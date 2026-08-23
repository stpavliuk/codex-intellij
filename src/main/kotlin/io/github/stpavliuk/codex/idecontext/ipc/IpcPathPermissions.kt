package io.github.stpavliuk.codex.idecontext.ipc

import com.intellij.openapi.diagnostic.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

internal object IpcPathPermissions {
    private val logger = Logger.getInstance(IpcPathPermissions::class.java)

    fun prepareDirectory(path: Path) {
        try {
            Files.createDirectories(
                path,
                PosixFilePermissions.asFileAttribute(DIRECTORY_PERMISSIONS),
            )
            Files.setPosixFilePermissions(path, DIRECTORY_PERMISSIONS)
            validateDirectory(path)
        } catch (error: Exception) {
            logger.warn("Could not secure Codex IPC directory: $path", error)
            throw error
        }
    }

    fun secureSocket(path: Path) {
        try {
            Files.setPosixFilePermissions(path, SOCKET_PERMISSIONS)
            val attributes = Files.readAttributes(path, PosixFileAttributes::class.java)

            require(attributes.owner() == currentUserOwner()) {
                "Codex IPC socket is not owned by the current user: $path"
            }
            require(attributes.permissions().none(OTHER_USER_WRITE_PERMISSIONS::contains)) {
                "Codex IPC socket is writable by other users: $path"
            }
        } catch (error: Exception) {
            logger.warn("Could not secure Codex IPC socket: $path", error)

            throw error
        }
    }

    internal fun validateDirectory(path: Path) {
        val attributes = Files.readAttributes(path, PosixFileAttributes::class.java)
        require(attributes.isDirectory) { "Codex IPC path is not a directory: $path" }
        require(attributes.owner() == currentUserOwner()) {
            "Codex IPC directory is not owned by the current user: $path"
        }
        require(attributes.permissions().none(OTHER_USER_WRITE_PERMISSIONS::contains)) {
            "Codex IPC directory is writable by other users: $path"
        }
    }

    private fun currentUserOwner() = Files.getOwner(Path.of(System.getProperty("user.home")))

    private val DIRECTORY_PERMISSIONS = setOf(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE,
    )

    private val SOCKET_PERMISSIONS = setOf(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
    )

    private val OTHER_USER_WRITE_PERMISSIONS = setOf(
        PosixFilePermission.GROUP_WRITE,
        PosixFilePermission.OTHERS_WRITE,
    )
}
