package com.xbdlib.speech

import org.json.JSONObject

internal object IflyResultParser {
    /** A malformed response must be retried, never submitted as raw JSON. */
    fun parse(resultJson: String): String? = runCatching {
        val words = JSONObject(resultJson).optJSONArray("ws") ?: return null
        buildString {
            for (index in 0 until words.length()) {
                val candidates = words.optJSONObject(index)?.optJSONArray("cw") ?: continue
                for (candidate in 0 until candidates.length()) {
                    append(candidates.optJSONObject(candidate)?.optString("w").orEmpty())
                }
            }
        }
    }.getOrNull()
}
