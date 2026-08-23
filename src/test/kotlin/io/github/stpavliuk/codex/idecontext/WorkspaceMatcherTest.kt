package io.github.stpavliuk.codex.idecontext

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class WorkspaceMatcherTest {
    @Test
    fun `matches the project root and its descendants`() {
        val project = Files.createTempDirectory("codex-project").toRealPath()

        assertTrue(WorkspaceMatcher.contains(project.toString(), project.toString()))
        assertTrue(WorkspaceMatcher.contains(project.toString(), project.resolve("module").toString()))
        assertFalse(WorkspaceMatcher.contains(project.resolve("module").toString(), project.toString()))
    }

    @Test
    fun `matches workspace parent for IntelliJ metadata roots`() {
        val workspace = Files.createTempDirectory("codex-workspace").toRealPath()

        listOf(".idea", ".ijwb").forEach { metadataDirectory ->
            val projectRoot = Files.createDirectory(workspace.resolve(metadataDirectory))

            assertTrue(WorkspaceMatcher.contains(projectRoot.toString(), workspace.toString()))
            assertTrue(
                WorkspaceMatcher.contains(
                    projectRoot.toString(),
                    workspace.resolve("module").toString(),
                ),
            )
        }
    }

    @Test
    fun `rejects unrelated and missing roots`() {
        val project = Files.createTempDirectory("codex-project").toRealPath()
        val unrelated = Files.createTempDirectory("other-project").toRealPath()

        assertFalse(WorkspaceMatcher.contains(project.toString(), unrelated.toString()))
        assertFalse(WorkspaceMatcher.contains(null, project.toString()))
        assertFalse(WorkspaceMatcher.contains(project.toString(), ""))
    }
}
