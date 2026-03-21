package com.forbidad4tieba.hook

import org.json.JSONObject

data class HookSymbols(
    val homeTabClass: String? = null,
    val homeTabRebuildMethod: String? = null,
    val homeTabListField: String? = null,
    val settingsClass: String? = null,
    val settingsInitMethod: String? = null,
    val settingsContainerField: String? = null,
    val source: String = "unsupported",
    val createdAt: Long = 0L,
) {
    fun toJson(): String {
        return JSONObject()
            .put("homeTabClass", homeTabClass)
            .put("homeTabRebuildMethod", homeTabRebuildMethod)
            .put("homeTabListField", homeTabListField)
            .put("settingsClass", settingsClass)
            .put("settingsInitMethod", settingsInitMethod)
            .put("settingsContainerField", settingsContainerField)
            .put("source", source)
            .put("createdAt", createdAt)
            .toString()
    }

    companion object {
        fun fromJson(json: String?): HookSymbols? {
            if (json.isNullOrBlank()) return null
            return try {
                val obj = JSONObject(json)
                HookSymbols(
                    homeTabClass = obj.optStringOrNull("homeTabClass"),
                    homeTabRebuildMethod = obj.optStringOrNull("homeTabRebuildMethod"),
                    homeTabListField = obj.optStringOrNull("homeTabListField"),
                    settingsClass = obj.optStringOrNull("settingsClass"),
                    settingsInitMethod = obj.optStringOrNull("settingsInitMethod"),
                    settingsContainerField = obj.optStringOrNull("settingsContainerField"),
                    source = obj.optString("source", "unsupported"),
                    createdAt = obj.optLong("createdAt", 0L),
                )
            } catch (_: Throwable) {
                null
            }
        }

        private fun JSONObject.optStringOrNull(key: String): String? {
            if (isNull(key)) return null
            return optString(key).takeIf { it.isNotBlank() }
        }
    }
}
