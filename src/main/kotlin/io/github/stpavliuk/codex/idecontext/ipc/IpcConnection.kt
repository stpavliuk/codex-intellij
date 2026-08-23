package io.github.stpavliuk.codex.idecontext.ipc

import com.google.gson.JsonObject
import java.io.Closeable
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.util.concurrent.atomic.AtomicBoolean

internal class IpcConnection(private val channel: SocketChannel) : Closeable {
    private val input = Channels.newInputStream(channel)
    private val output = Channels.newOutputStream(channel)
    private val closed = AtomicBoolean()

    fun read(): JsonObject? = IpcFrameCodec.read(input)

    @Synchronized
    fun send(message: JsonObject) {
        check(!closed.get()) { "IPC connection is closed" }
        IpcFrameCodec.write(output, message)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            channel.close()
        }
    }
}
