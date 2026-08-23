package io.github.stpavliuk.codex.idecontext.ipc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

class IpcPathPermissionsTest {
    @Test
    fun `creates an owner-only IPC directory`() {
        assumeFalse(System.getProperty("os.name").startsWith("Windows", true))
        val directory = Files.createTempDirectory("codex-ipc-parent").resolve("ipc")

        IpcPathPermissions.prepareDirectory(directory)

        assertEquals(
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
            ),
            Files.getPosixFilePermissions(directory),
        )
    }

    @Test
    fun `rejects an IPC directory writable by other users`() {
        assumeFalse(System.getProperty("os.name").startsWith("Windows", true))
        val directory = Files.createTempDirectory("codex-ipc-test")
        Files.setPosixFilePermissions(
            directory,
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_WRITE,
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            IpcPathPermissions.validateDirectory(directory)
        }
    }
}
