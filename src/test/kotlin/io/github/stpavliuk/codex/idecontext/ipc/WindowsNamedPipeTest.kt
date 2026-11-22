package io.github.stpavliuk.codex.idecontext.ipc

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.IOException
import java.nio.file.Path
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.TimeoutException

class WindowsNamedPipeTest {
    @Test
    fun `closing an idle pipe cancels a pending framed read`() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows", true))
        val name = "codex-ipc-test-${UUID.randomUUID()}"
        val script = javaClass.getResource("/windows-pipe-server.ps1")!!.readText()
            .replace("PIPE_NAME", name)
        val encodedScript = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        val server = ProcessBuilder(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encodedScript,
        ).redirectErrorStream(true).start()
        val executor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "windows-pipe-test").apply { isDaemon = true }
        }
        var connection: IpcConnection? = null

        try {
            val serverOutput = server.inputStream.bufferedReader()
            assertEquals("READY", executor.submit<String> { serverOutput.readLine() }.get(15, SECONDS))
            val connected = IpcConnection(WindowsNamedPipe.open(Path.of("\\\\.\\pipe\\$name")))
            connection = connected

            // The server echoes a complete frame using fragmented writes, then
            // remains connected and sends nothing until the test tears it down.
            val message = JsonObject().apply { addProperty("selection", "hello \uD83D\uDC4B") }
            connected.send(message)
            assertEquals(message, executor.submit<JsonObject?> { connected.read() }.get(5, SECONDS))
            assertEquals("IDLE", executor.submit<String> { serverOutput.readLine() }.get(5, SECONDS))

            val reading = CountDownLatch(1)
            val pendingRead = executor.submit<JsonObject?> {
                reading.countDown()
                connected.read()
            }
            assertTrue(reading.await(5, SECONDS))
            assertThrows(TimeoutException::class.java) { pendingRead.get(200, MILLISECONDS) }

            executor.submit { connected.close() }.get(2, SECONDS)
            assertTrue("Closing the client must not require stopping the server", server.isAlive)
            val failure = assertThrows(ExecutionException::class.java) { pendingRead.get(2, SECONDS) }
            assertTrue(failure.cause is IOException)
            connected.close()
        } finally {
            // Stop the server first so a broken client close cannot hang test cleanup.
            server.destroyForcibly()
            server.waitFor(5, SECONDS)
            connection?.close()
            executor.shutdownNow()
        }
    }
}
