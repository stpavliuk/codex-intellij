package io.github.stpavliuk.codex.idecontext.ipc

import java.io.IOException
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.ByteChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future

internal class WindowsNamedPipe private constructor(
    private val channel: AsynchronousFileChannel,
) : ByteChannel {
    // Windows opens this channel with FILE_FLAG_OVERLAPPED. Closing it cancels
    // pending I/O, including a read from an idle server. RandomAccessFile's
    // synchronous FileChannel waits for the read before it closes the handle.
    // Named pipes ignore the file position supplied to overlapped operations.
    override fun read(destination: ByteBuffer): Int = await(channel.read(destination, 0))

    override fun write(source: ByteBuffer): Int = await(channel.write(source, 0))

    override fun isOpen(): Boolean = channel.isOpen

    override fun close() = channel.close()

    private fun await(operation: Future<Int>): Int = try {
        operation.get()
    } catch (error: ExecutionException) {
        throw (error.cause as? IOException ?: IOException("Named pipe I/O failed", error.cause))
    } catch (error: InterruptedException) {
        Thread.currentThread().interrupt()
        throw InterruptedIOException("Named pipe I/O interrupted").apply { initCause(error) }
    }

    companion object {
        fun open(path: Path = Path.of("\\\\.\\pipe\\codex-ipc")): WindowsNamedPipe =
            WindowsNamedPipe(AsynchronousFileChannel.open(path, READ, WRITE))
    }
}
