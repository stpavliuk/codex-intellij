package io.github.stpavliuk.codex.idecontext.ipc

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object IpcFrameCodec {
    private const val MAX_FRAME_BYTES = 256 * 1024 * 1024

    fun read(input: InputStream): JsonObject? {
        val header = ByteArray(Int.SIZE_BYTES)
        val headerBytes = readFully(input, header, allowInitialEof = true)
        if (headerBytes == 0) {
            return null
        }

        val length = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).int
        require(length in 0..MAX_FRAME_BYTES) { "IPC frame length is invalid: $length" }

        val payload = ByteArray(length)
        readFully(input, payload, allowInitialEof = false)

        val element = JsonParser.parseString(payload.toString(Charsets.UTF_8))
        require(element.isJsonObject) { "IPC frame must contain a JSON object" }

        return element.asJsonObject
    }

    fun write(output: OutputStream, message: JsonObject) {
        val payload = message.toString().toByteArray(Charsets.UTF_8)
        require(payload.size <= MAX_FRAME_BYTES) { "IPC frame is too large" }

        val header = ByteBuffer.allocate(Int.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(payload.size)
            .array()

        output.write(header)
        output.write(payload)
        output.flush()
    }

    private fun readFully(input: InputStream, buffer: ByteArray, allowInitialEof: Boolean): Int {
        var offset = 0

        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) {
                if (allowInitialEof && offset == 0) {
                    return 0
                }
                throw EOFException("Unexpected end of IPC frame")
            }
            offset += read
        }

        return offset
    }
}
