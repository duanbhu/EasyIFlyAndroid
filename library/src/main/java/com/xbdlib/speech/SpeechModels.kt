package com.xbdlib.speech

/** Built-in filters corresponding to EasyIFly's input helpers. */
enum class SpeechMode {
    /** Accepts 11 numeric characters or an 11-character number with a 3–4 character extension. */
    PHONE_OR_VIRTUAL,
    /** Returns the current SDK text segment without number filtering. */
    FREE_TEXT,
    /** Accepts exactly 11 numeric characters without validating the mobile prefix. */
    PHONE_ONLY,
}

data class SpeechRecognitionConfig(
    val appId: String,
    val mode: SpeechMode = SpeechMode.PHONE_OR_VIRTUAL,
    val stopOnResult: Boolean = true,
    val vadBosMillis: Int = 86_400_000,
    val vadEosMillis: Int = 86_400_000,
    val language: String = "zh_cn",
    val accent: String = "mandarin",
    val saveAudio: Boolean = false,
    val speechTimeoutMillis: Int = 86_400_000,
    val networkTimeoutMillis: Int = 20_000,
    /** Overrides the built-in filter. Null means retry; a non-null string is submitted. */
    val resultFilter: ((String) -> String?)? = null,
) {
    init {
        require(appId.isNotBlank()) { "讯飞 appId 不能为空" }
        require(vadBosMillis > 0)
        require(vadEosMillis >= 0)
        require(speechTimeoutMillis > 0)
        require(networkTimeoutMillis > 0)
    }
}

data class PhoneVirtualResult(
    val phone: String? = null,
    val virtualNumber: String? = null,
    val extension: String? = null,
) {
    val value: String? get() = virtualNumber ?: phone
}

data class SpeechRecognitionResult(
    val mode: SpeechMode,
    val text: String,
    val phone: PhoneVirtualResult? = null,
    val isFinal: Boolean = false,
)

data class SpeechRecognitionError(
    val code: Int? = null,
    val message: String,
    val cause: Throwable? = null,
)

enum class SpeechRecognitionState { IDLE, LISTENING, PROCESSING, STOPPED, RELEASED, ERROR }

interface SpeechRecognitionListener {
    fun onStateChanged(state: SpeechRecognitionState) = Unit
    fun onVolumeChanged(volume: Int) = Unit
    fun onResult(result: SpeechRecognitionResult) = Unit
    fun onError(error: SpeechRecognitionError) = Unit
}
