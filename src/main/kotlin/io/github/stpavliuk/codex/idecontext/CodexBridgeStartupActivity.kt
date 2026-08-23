package io.github.stpavliuk.codex.idecontext

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

internal class CodexBridgeStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        service<CodexIpcBridgeService>().start()
    }
}
