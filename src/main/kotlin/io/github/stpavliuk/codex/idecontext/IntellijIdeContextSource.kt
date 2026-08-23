package io.github.stpavliuk.codex.idecontext

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class IntellijIdeContextSource : IdeContextSource {
    override fun canHandle(workspaceRoot: String): Boolean =
        ProjectManager.getInstance().openProjects.any { project ->
            !project.isDisposed && WorkspaceMatcher.contains(project, workspaceRoot)
        }

    override fun collect(workspaceRoot: String): JsonObject? {
        val application = ApplicationManager.getApplication()

        if (application.isDispatchThread) {
            return matchingProject(workspaceRoot)?.let(::contextFor)
        }

        val collect = FutureTask<JsonObject?> {
            matchingProject(workspaceRoot)?.let(::contextFor)
        }

        application.invokeLater(collect)

        return try {
            collect.get(COLLECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (error: TimeoutException) {
            collect.cancel(false)
            throw IdeContextCollectionTimeoutException(error)
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        }
    }

    private fun matchingProject(workspaceRoot: String): Project? =
        ProjectManager.getInstance().openProjects.firstOrNull { project ->
            !project.isDisposed && WorkspaceMatcher.contains(project, workspaceRoot)
        }

    private fun contextFor(project: Project): JsonObject = JsonObject().apply {
        val fileEditorManager = FileEditorManager.getInstance(project)

        fileEditorManager.selectedTextEditor?.let { editor ->
            activeFile(project, editor)?.let { add("activeFile", it) }
        }

        add("openTabs", openTabs(project, fileEditorManager.openFiles))
    }

    private fun activeFile(project: Project, editor: Editor): JsonObject? {
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return null

        val primaryCaret = editor.caretModel.primaryCaret
        val selections = JsonArray()

        editor.caretModel.allCarets.forEach { caret ->
            selections.add(range(editor.document, caret.selectionStart, caret.selectionEnd))
        }

        return fileDescriptor(project, file).apply {
            add("selection", range(editor.document, primaryCaret.selectionStart, primaryCaret.selectionEnd))
            addProperty(
                "activeSelectionContent",
                selectedText(editor.document, primaryCaret.selectionStart, primaryCaret.selectionEnd),
            )
            add("selections", selections)
        }
    }

    private fun openTabs(project: Project, files: Array<VirtualFile>): JsonArray = JsonArray().apply {
        files.take(MAX_OPEN_TABS).forEach { file -> add(fileDescriptor(project, file)) }
    }

    private fun fileDescriptor(project: Project, file: VirtualFile): JsonObject = JsonObject().apply {
        addProperty("label", file.name)
        addProperty("path", displayPath(project, file))
        addProperty("fsPath", file.path)
    }

    private fun displayPath(project: Project, file: VirtualFile): String {
        val projectPath = project.basePath?.let(Path::of)?.toAbsolutePath()?.normalize()
        val filePath = runCatching { Path.of(file.path).toAbsolutePath().normalize() }.getOrNull()

        if (projectPath == null || filePath == null || !filePath.startsWith(projectPath)) {
            return file.path
        }

        return projectPath.relativize(filePath).toString().replace('\\', '/')
    }

    private fun range(document: Document, startOffset: Int, endOffset: Int): JsonObject =
        JsonObject().apply {
            add("start", position(document, startOffset))
            add("end", position(document, endOffset))
        }

    private fun position(document: Document, rawOffset: Int): JsonObject {
        val offset = rawOffset.coerceIn(0, document.textLength)
        val line = document.getLineNumber(offset)
        return JsonObject().apply {
            addProperty("line", line)
            addProperty("character", offset - document.getLineStartOffset(line))
        }
    }

    private fun selectedText(document: Document, rawStart: Int, rawEnd: Int): String {
        val start = rawStart.coerceIn(0, document.textLength)
        val end = rawEnd.coerceIn(start, document.textLength)
        if (start == end) {
            return ""
        }
        return document.getText(TextRange(start, end)).take(MAX_SELECTION_CHARS)
    }

    private companion object {
        const val MAX_SELECTION_CHARS = 40_000
        const val MAX_OPEN_TABS = 100
        const val COLLECTION_TIMEOUT_SECONDS = 4L
    }
}
