package io.github.stpavliuk.codex.idecontext

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import io.github.stpavliuk.codex.idecontext.ipc.IpcPathPermissions
import io.github.stpavliuk.codex.idecontext.ipc.IpcProviderClient
import io.github.stpavliuk.codex.idecontext.ipc.IpcRouter
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.APP)
internal class CodexIpcBridgeService : Disposable {

    private val logger = Logger.getInstance(CodexIpcBridgeService::class.java)
    private val started = AtomicBoolean()
    private val disposed = AtomicBoolean()
    private val workers = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "codex-ide-context-ipc").apply { isDaemon = true }
    }
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "codex-ide-context-reconnect").apply { isDaemon = true }
    }
    private val contextSource = IntellijIdeContextSource()
    @Volatile private var router: IpcRouter? = null
    @Volatile private var provider: IpcProviderClient? = null

    fun start() {
        if (!isUnix() || !started.compareAndSet(false, true)) {
            return
        }

        scheduler.execute(::connectProvider)
    }

    private fun connectProvider() {
        if (disposed.get()) {
            return
        }
        val socketPath = socketPath()
        try {
            ensureRouter(socketPath)
            val client = IpcProviderClient(socketPath, contextSource)

            provider = client
            client.run()
        } catch (error: Exception) {
            if (!disposed.get()) {
                logger.warn("Codex IDE context connection failed; retrying", error)
            }
        } finally {
            provider = null
            if (!disposed.get()) {
                scheduler.schedule(::connectProvider, 1, TimeUnit.SECONDS)
            }
        }
    }

    @Synchronized
    private fun ensureRouter(socketPath: Path) {
        if (canConnect(socketPath)) {
            return
        }
        router?.close()
        router = null

        val parent = socketPath.parent
        IpcPathPermissions.prepareDirectory(parent)
        deleteStaleSocket(socketPath)

        val localRouter = IpcRouter(socketPath, workers)
        try {
            localRouter.start()
            router = localRouter
            logger.info("Started Codex IPC router at $socketPath")
        } catch (error: Exception) {
            localRouter.close()
            if (!canConnect(socketPath)) {
                throw error
            }
        }
    }

    private fun canConnect(socketPath: Path): Boolean = runCatching {
        SocketChannel.open(StandardProtocolFamily.UNIX).use { channel ->
            channel.connect(UnixDomainSocketAddress.of(socketPath))
        }

        true
    }.getOrDefault(false)

    private fun deleteStaleSocket(socketPath: Path) {
        if (!Files.exists(socketPath)) {
            return
        }

        val attributes = Files.readAttributes(socketPath, BasicFileAttributes::class.java)
        require(attributes.isOther) { "Refusing to replace non-socket path: $socketPath" }
        Files.delete(socketPath)
    }

    private fun socketPath(): Path {
        val configuredHome = System.getenv("CODEX_HOME")?.takeIf(String::isNotBlank)
        val codexHome = configuredHome?.let(Path::of)
            ?: Path.of(System.getProperty("user.home"), ".codex")
        return codexHome.resolve("ipc").resolve("ipc.sock")
    }

    private fun isUnix(): Boolean = !System.getProperty("os.name").startsWith("Windows", true)

    override fun dispose() {
        disposed.set(true)
        provider?.close()
        router?.close()
        scheduler.shutdownNow()
        workers.shutdownNow()
    }
}
