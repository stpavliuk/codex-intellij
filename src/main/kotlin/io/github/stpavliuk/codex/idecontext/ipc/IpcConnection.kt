package io.github.stpavliuk.codex.idecontext.ipc

import com.google.gson.JsonObject
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.util.concurrent.atomic.AtomicBoolean

internal class IpcConnection(
    private val input: InputStream,
    private val output: OutputStream,
    private val closeTransport: () -> Unit,
) : Closeable {
    constructor(channel: SocketChannel) : this(
        Channels.newInputStream(channel),
        Channels.newOutputStream(channel),
        channel::close,
    )
    private val closed = AtomicBoolean()

    fun read(): JsonObject? = IpcFrameCodec.read(input)

    @Synchronized
    fun send(message: JsonObject) {
        check(!closed.get()) { "IPC connection is closed" }
        IpcFrameCodec.write(output, message)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            closeTransport()
        }
    }
}
