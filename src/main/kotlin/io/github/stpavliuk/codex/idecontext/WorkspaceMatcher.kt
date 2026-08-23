package io.github.stpavliuk.codex.idecontext

import java.nio.file.Path

internal object WorkspaceMatcher {
    fun contains(projectRoot: String?, requestedRoot: String): Boolean {
        if (projectRoot.isNullOrBlank() || requestedRoot.isBlank()) {
            return false
        }

        val project = normalized(projectRoot)?.workspaceRoot() ?: return false
        val requested = normalized(requestedRoot) ?: return false

        return requested == project || requested.startsWith(project)
    }

    private fun Path.workspaceRoot(): Path =
        if (fileName?.toString() in INTELLIJ_METADATA_DIRECTORIES) {
            parent ?: this
        } else {
            this
        }

    private fun normalized(path: String): Path? =
        runCatching { Path.of(path).toAbsolutePath().normalize() }.getOrNull()

    private val INTELLIJ_METADATA_DIRECTORIES = setOf(".idea", ".ijwb")
}
