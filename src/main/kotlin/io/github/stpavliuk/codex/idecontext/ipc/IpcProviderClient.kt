package io.github.stpavliuk.codex.idecontext.ipc

import com.google.gson.JsonObject
import io.github.stpavliuk.codex.idecontext.IdeContextCollectionTimeoutException
import io.github.stpavliuk.codex.idecontext.IdeContextSource
import org.jetbrains.annotations.TestOnly
import java.io.Closeable
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.SocketChannel
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class IpcProviderClient(
    private val socketPath: Path,
    private val contextSource: IdeContextSource,
) : Closeable {
    private val connection = AtomicReference<IpcConnection?>()
    private val initialized = CountDownLatch(1)
    private val closed = AtomicBoolean()

    fun run() {
        val channel = SocketChannel.open(StandardProtocolFamily.UNIX)
        channel.connect(UnixDomainSocketAddress.of(socketPath))
        val connected = IpcConnection(channel)
        connection.set(connected)
        try {
            connected.send(initializeRequest())
            while (true) {
                val message = connected.read() ?: break
                handle(connected, message)
            }
        } catch (error: Exception) {
            if (!closed.get()) {
                throw error
            }
        } finally {
            connection.compareAndSet(connected, null)
            connected.close()
        }
    }

    @TestOnly
    fun awaitInitialized(timeout: Long, unit: TimeUnit): Boolean = initialized.await(timeout, unit)

    private fun handle(connection: IpcConnection, message: JsonObject) {
        when (IpcMessages.string(message, "type")) {
            "response" -> {
                if (IpcMessages.string(message, "method") == "initialize" &&
                    IpcMessages.string(message, "resultType") == "success"
                ) {
                    initialized.countDown()
                }
            }

            "client-discovery-request" -> handleDiscovery(connection, message)
            "request" -> handleRequest(connection, message)
        }
    }

    private fun handleDiscovery(connection: IpcConnection, message: JsonObject) {
        val requestId = IpcMessages.string(message, "requestId") ?: return
        val request = message.getAsJsonObject("request")
        val canHandle = request != null && requestVersion(request) == IDE_CONTEXT_VERSION &&
                IpcMessages.string(request, "method") == IDE_CONTEXT_METHOD &&
                workspaceRoot(request)?.let(contextSource::canHandle) == true
        connection.send(JsonObject().apply {
            addProperty("type", "client-discovery-response")
            addProperty("requestId", requestId)
            add("response", JsonObject().apply { addProperty("canHandle", canHandle) })
        })
    }

    private fun handleRequest(connection: IpcConnection, request: JsonObject) {
        val requestId = IpcMessages.string(request, "requestId") ?: return

        if (requestVersion(request) != IDE_CONTEXT_VERSION) {
            connection.send(
                IpcMessages.response(requestId, "error", error = "request-version-mismatch"),
            )
            return
        }

        if (IpcMessages.string(request, "method") != IDE_CONTEXT_METHOD) {
            connection.send(
                IpcMessages.response(requestId, "error", error = "no-handler-for-request"),
            )
            return
        }

        val workspaceRoot = workspaceRoot(request)
        val context = try {
            workspaceRoot?.let(contextSource::collect)
        } catch (_: IdeContextCollectionTimeoutException) {
            connection.send(IpcMessages.response(requestId, "error", error = "request-timeout"))
            return
        }

        if (context == null) {
            connection.send(IpcMessages.response(requestId, "error", error = "no-client-found"))
            return
        }

        connection.send(
            IpcMessages.response(
                requestId = requestId,
                resultType = "success",
                method = IDE_CONTEXT_METHOD,
                result = JsonObject().apply { add("ideContext", context) },
            ),
        )
    }

    private fun initializeRequest(): JsonObject = JsonObject().apply {
        addProperty("type", "request")
        addProperty("requestId", UUID.randomUUID().toString())
        addProperty("sourceClientId", "initializing-client")
        addProperty("version", 0)
        addProperty("method", "initialize")
        add("params", JsonObject().apply { addProperty("clientType", "intellij") })
    }

    private fun requestVersion(request: JsonObject): Int = request.get("version")?.asInt ?: 0

    private fun workspaceRoot(request: JsonObject): String? =
        request.getAsJsonObject("params")?.get("workspaceRoot")?.asString

    override fun close() {
        closed.set(true)
        connection.getAndSet(null)?.close()
    }

    private companion object {
        const val IDE_CONTEXT_METHOD = "ide-context"
        const val IDE_CONTEXT_VERSION = 0
    }
}
