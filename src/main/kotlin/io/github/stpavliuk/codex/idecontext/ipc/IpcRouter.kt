package io.github.stpavliuk.codex.idecontext.ipc

import com.google.gson.JsonObject
import java.io.Closeable
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.ServerSocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class IpcRouter(
    private val socketPath: Path,
    private val executor: ExecutorService,
) : Closeable {
    private val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
    private val bound = AtomicBoolean()
    private val closed = AtomicBoolean()
    private val connections = ConcurrentHashMap.newKeySet<IpcConnection>()
    private val clients = ConcurrentHashMap<IpcConnection, Client>()
    private val clientsById = ConcurrentHashMap<String, IpcConnection>()
    private val discoveries = ConcurrentHashMap<String, PendingDiscovery>()
    private val routes = ConcurrentHashMap<String, PendingRoute>()

    fun start() {
        server.bind(UnixDomainSocketAddress.of(socketPath))
        bound.set(true)
        IpcPathPermissions.secureSocket(socketPath)
        executor.execute(::acceptLoop)
    }

    private fun acceptLoop() {
        while (!closed.get()) {
            val channel = runCatching { server.accept() }.getOrNull() ?: break
            val connection = IpcConnection(channel)
            connections.add(connection)
            executor.execute { readLoop(connection) }
        }
    }

    private fun readLoop(connection: IpcConnection) {
        try {
            while (!closed.get()) {
                val message = connection.read() ?: break
                handle(connection, message)
            }
        } finally {
            disconnect(connection)
        }
    }

    private fun handle(connection: IpcConnection, message: JsonObject) {
        when (IpcMessages.string(message, "type")) {
            "request" -> handleRequest(connection, message)
            "response" -> handleResponse(message)
            "broadcast" -> forwardBroadcast(connection, message)
            "client-discovery-response" -> handleDiscoveryResponse(message)
        }
    }

    private fun handleRequest(connection: IpcConnection, request: JsonObject) {
        if (IpcMessages.string(request, "method") == "initialize") {
            register(connection, request)
        } else {
            executor.execute { route(connection, request) }
        }
    }

    private fun register(connection: IpcConnection, request: JsonObject) {
        val requestId = IpcMessages.string(request, "requestId") ?: return
        val existing = clients[connection]
        val client = existing ?: Client(
            id = UUID.randomUUID().toString(),
            type = request.getAsJsonObject("params")?.get("clientType")?.asString ?: "unknown",
        ).also {
            clients[connection] = it
            clientsById[it.id] = connection
        }
        connection.send(
            IpcMessages.response(
                requestId = requestId,
                resultType = "success",
                method = "initialize",
                result = JsonObject().apply { addProperty("clientId", client.id) },
            ),
        )
    }

    private fun route(source: IpcConnection, request: JsonObject) {
        val requestId = IpcMessages.string(request, "requestId") ?: return
        val targetId = IpcMessages.string(request, "targetClientId")
        val candidates = if (targetId != null) {
            listOfNotNull(clientsById[targetId]).filterNot { it === source }
        } else {
            clients.keys.filterNot { it === source }
        }

        val probes = candidates.mapNotNull { candidate ->
            runCatching { probe(candidate, request) }.getOrNull()
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
        var target: IpcConnection? = null
        while (System.nanoTime() < deadline && probes.any { !it.future.isDone }) {
            target = probes.firstOrNull { it.future.getNow(false) }?.connection
            if (target != null) {
                break
            }
            Thread.sleep(10)
        }
        target = target ?: probes.firstOrNull { it.future.getNow(false) }?.connection
        probes.forEach { discoveries.remove(it.id) }

        if (target == null) {
            source.send(
                IpcMessages.response(
                    requestId = requestId,
                    resultType = "error",
                    error = "no-client-found",
                ),
            )
            return
        }

        routes[requestId] = PendingRoute(source, target)
        val forwarded = request.deepCopy()
        forwarded.addProperty(
            "sourceClientId",
            clients[source]?.id ?: IpcMessages.string(request, "sourceClientId").orEmpty(),
        )
        with(target) {
            forwarded.addProperty(
                "sourceClientId",
                clients[source]?.id ?: IpcMessages.string(request, "sourceClientId").orEmpty(),
            )
            send(forwarded)
        }
    }

    private fun probe(connection: IpcConnection, request: JsonObject): Probe {
        val id = UUID.randomUUID().toString()
        val future = CompletableFuture<Boolean>()
        discoveries[id] = PendingDiscovery(connection, future)
        connection.send(JsonObject().apply {
            addProperty("type", "client-discovery-request")
            addProperty("requestId", id)
            add("request", request.deepCopy())
        })
        return Probe(id, connection, future)
    }

    private fun handleDiscoveryResponse(message: JsonObject) {
        val requestId = IpcMessages.string(message, "requestId") ?: return
        val pending = discoveries.remove(requestId) ?: return
        val canHandle = message.getAsJsonObject("response")?.get("canHandle")?.asBoolean == true
        pending.future.complete(canHandle)
    }

    private fun handleResponse(message: JsonObject) {
        val requestId = IpcMessages.string(message, "requestId") ?: return
        routes.remove(requestId)?.source?.send(message)
    }

    private fun forwardBroadcast(source: IpcConnection, message: JsonObject) {
        val forwarded = message.deepCopy().apply {
            addProperty(
                "sourceClientId",
                clients[source]?.id ?: IpcMessages.string(message, "sourceClientId").orEmpty(),
            )
        }
        connections.filterNot { it === source }.forEach { connection ->
            runCatching { connection.send(forwarded) }
        }
    }

    private fun disconnect(connection: IpcConnection) {
        connections.remove(connection)
        clients.remove(connection)?.let { clientsById.remove(it.id) }
        discoveries.entries.removeIf { (_, pending) ->
            if (pending.connection === connection) {
                pending.future.complete(false)
                true
            } else {
                false
            }
        }
        routes.entries.removeIf { (requestId, route) ->
            if (route.target === connection) {
                runCatching {
                    route.source.send(
                        IpcMessages.response(requestId, "error", error = "client-disconnected"),
                    )
                }
                true
            } else {
                route.source === connection
            }
        }
        connection.close()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        server.close()
        connections.toList().forEach(IpcConnection::close)
        connections.clear()
        if (bound.get()) {
            Files.deleteIfExists(socketPath)
        }
    }

    private data class Client(val id: String, val type: String)
    private data class PendingDiscovery(
        val connection: IpcConnection,
        val future: CompletableFuture<Boolean>,
    )

    private data class PendingRoute(val source: IpcConnection, val target: IpcConnection)
    private data class Probe(
        val id: String,
        val connection: IpcConnection,
        val future: CompletableFuture<Boolean>,
    )

}
