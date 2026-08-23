package io.github.stpavliuk.codex.idecontext

import com.google.gson.JsonObject

internal interface IdeContextSource {
    fun canHandle(workspaceRoot: String): Boolean

    fun collect(workspaceRoot: String): JsonObject?
}

internal class IdeContextCollectionTimeoutException(cause: Throwable) :
    RuntimeException("Timed out collecting IntelliJ editor context", cause)
