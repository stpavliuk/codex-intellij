package io.github.stpavliuk.codex.idecontext

import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.Project
import java.nio.file.Path

internal object WorkspaceMatcher {
    fun contains(project: Project, requestedRoot: String): Boolean {
        val projectRoots = project.getBaseDirectories()
            .map { it.path }
            .ifEmpty { listOfNotNull(project.basePath) }

        return contains(projectRoots, requestedRoot)
    }

    internal fun contains(projectRoots: Iterable<String?>, requestedRoot: String): Boolean {
        if (requestedRoot.isBlank()) {
            return false
        }

        val requested = normalized(requestedRoot) ?: return false

        return projectRoots.any { projectRoot ->
            val project = projectRoot
                ?.takeUnless(String::isBlank)
                ?.let(::normalized)
                ?: return@any false

            requested == project || requested.startsWith(project)
        }
    }

    private fun normalized(path: String): Path? =
        runCatching { Path.of(path).toAbsolutePath().normalize() }.getOrNull()
}
