package io.github.stpavliuk.codex.idecontext.ipc

import com.google.gson.JsonObject
import io.github.stpavliuk.codex.idecontext.IdeContextCollectionTimeoutException
import io.github.stpavliuk.codex.idecontext.IdeContextSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class IpcRouterTest {
    @Test
    fun `routes an IDE context request to a registered provider`() {
        val context = JsonObject().apply { add("openTabs", com.google.gson.JsonArray()) }
        val response = routeRequest(object : IdeContextSource {
            override fun canHandle(workspaceRoot: String): Boolean = workspaceRoot == "/repo"

            override fun collect(workspaceRoot: String): JsonObject = context
        })

        assertEquals("success", response.get("resultType").asString)
        assertEquals(context, response.getAsJsonObject("result").getAsJsonObject("ideContext"))
    }

    @Test
    fun `returns request timeout when editor context collection times out`() {
        val response = routeRequest(object : IdeContextSource {
            override fun canHandle(workspaceRoot: String): Boolean = true

            override fun collect(workspaceRoot: String): JsonObject {
                throw IdeContextCollectionTimeoutException(TimeoutException())
            }
        })

        assertEquals("error", response.get("resultType").asString)
        assertEquals("request-timeout", response.get("error").asString)
    }

    private fun routeRequest(contextSource: IdeContextSource): JsonObject {
        assumeFalse(System.getProperty("os.name").startsWith("Windows", true))
        val directory = Files.createTempDirectory("codex-ipc-test")
        val socketPath = directory.resolve("ipc.sock")
        val executor = Executors.newCachedThreadPool()
        val router = IpcRouter(socketPath, executor)
        val provider = IpcProviderClient(socketPath, contextSource)

        try {
            router.start()
            executor.submit { provider.run() }
            assertTrue(provider.awaitInitialized(2, TimeUnit.SECONDS))

            SocketChannel.open(StandardProtocolFamily.UNIX).use { channel ->
                channel.connect(UnixDomainSocketAddress.of(socketPath))
                val input = Channels.newInputStream(channel)
                val output = Channels.newOutputStream(channel)
                val requestId = UUID.randomUUID().toString()
                IpcFrameCodec.write(output, JsonObject().apply {
                    addProperty("type", "request")
                    addProperty("requestId", requestId)
                    addProperty("sourceClientId", "codex-tui")
                    addProperty("version", 0)
                    addProperty("method", "ide-context")
                    add("params", JsonObject().apply { addProperty("workspaceRoot", "/repo") })
                })

                val response = IpcFrameCodec.read(input)!!
                assertEquals(requestId, response.get("requestId").asString)
                return response
            }
        } finally {
            provider.close()
            router.close()
            executor.shutdownNow()
        }
    }
}
