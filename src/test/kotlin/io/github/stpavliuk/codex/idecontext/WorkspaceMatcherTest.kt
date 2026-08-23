package io.github.stpavliuk.codex.idecontext

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class WorkspaceMatcherTest {
    @Test
    fun `matches the project root and its descendants`() {
        val project = Files.createTempDirectory("codex-project").toRealPath()

        assertTrue(WorkspaceMatcher.contains(listOf(project.toString()), project.toString()))
        assertTrue(WorkspaceMatcher.contains(listOf(project.toString()), project.resolve("module").toString()))
        assertFalse(WorkspaceMatcher.contains(listOf(project.resolve("module").toString()), project.toString()))
    }

    @Test
    fun `matches any detected project root`() {
        val workspace = Files.createTempDirectory("codex-workspace").toRealPath()
        val firstRoot = Files.createDirectory(workspace.resolve("first"))
        val secondRoot = Files.createDirectory(workspace.resolve("second"))

        assertTrue(
            WorkspaceMatcher.contains(
                listOf(firstRoot.toString(), secondRoot.toString()),
                secondRoot.resolve("module").toString(),
            ),
        )
    }

    @Test
    fun `rejects unrelated and missing roots`() {
        val project = Files.createTempDirectory("codex-project").toRealPath()
        val unrelated = Files.createTempDirectory("other-project").toRealPath()

        assertFalse(WorkspaceMatcher.contains(listOf(project.toString()), unrelated.toString()))
        assertFalse(WorkspaceMatcher.contains(emptyList(), project.toString()))
        assertFalse(WorkspaceMatcher.contains(listOf(null, ""), project.toString()))
        assertFalse(WorkspaceMatcher.contains(listOf(project.toString()), ""))
    }
}
