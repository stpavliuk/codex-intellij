package io.github.stpavliuk.codex.idecontext.ipc

import com.google.gson.JsonObject

internal object IpcMessages {

    fun response(
        requestId: String,
        resultType: String,
        method: String? = null,
        result: JsonObject? = null,
        error: String? = null,
    ): JsonObject = JsonObject().apply {
        addProperty("type", "response")
        addProperty("requestId", requestId)
        addProperty("resultType", resultType)

        method?.let { addProperty("method", it) }
        result?.let { add("result", it) }
        error?.let { addProperty("error", it) }
    }

    fun string(message: JsonObject, name: String): String? =
        message.get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
}
