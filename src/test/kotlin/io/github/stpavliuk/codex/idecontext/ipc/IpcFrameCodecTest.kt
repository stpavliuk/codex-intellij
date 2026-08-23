package io.github.stpavliuk.codex.idecontext.ipc

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class IpcFrameCodecTest {
    @Test
    fun `round trips a framed message`() {
        val message = JsonObject().apply {
            addProperty("type", "request")
            addProperty("method", "ide-context")
        }
        val bytes = ByteArrayOutputStream().also { IpcFrameCodec.write(it, message) }.toByteArray()

        assertEquals(message, IpcFrameCodec.read(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `returns null at a clean end of stream`() {
        assertNull(IpcFrameCodec.read(ByteArrayInputStream(byteArrayOf())))
    }
}
