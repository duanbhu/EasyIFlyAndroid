package com.xbdlib.speech

/** Session policy independent of Android and the SDK for lifecycle verification. */
internal class SpeechRecognitionSession(
    private val config: SpeechRecognitionConfig,
    private val startEngine: (Long) -> Int,
    private val stopEngine: () -> Unit,
    private val cancelEngine: () -> Unit,
    private val scheduleRetry: (() -> Unit) -> Unit,
    private val onState: (SpeechRecognitionState) -> Unit,
    private val onResult: (SpeechRecognitionResult) -> Unit,
    private val onError: (SpeechRecognitionError) -> Unit,
) {
    private var attempt = 0L
    private var released = false
    var isListening = false
        private set

    fun start() {
        check(!released) { "语音控制器已释放" }
        ++attempt
        cancelEngine()
        isListening = true
        startAttempt()
    }

    private fun startAttempt() {
        val current = ++attempt
        val code = startEngine(current)
        if (!accepts(current)) return
        if (code == 0) onState(SpeechRecognitionState.LISTENING)
        else error(current, SpeechRecognitionError(code, "开始语音识别失败($code)"))
    }

    fun accepts(current: Long): Boolean = !released && isListening && attempt == current

    fun state(current: Long, state: SpeechRecognitionState) {
        if (accepts(current)) onState(state)
    }

    fun result(current: Long, raw: String?, isLast: Boolean) {
        if (!accepts(current)) return
        val filtered = raw?.let { text ->
            val filter = config.resultFilter
            if (filter != null) filter(text) else when (config.mode) {
                SpeechMode.PHONE_ONLY -> SpeechNumberExtractor.extract(text, false)?.value
                SpeechMode.PHONE_OR_VIRTUAL -> SpeechNumberExtractor.extract(text)?.value
                SpeechMode.FREE_TEXT -> text
            }
        }
        // User filters may stop/release the session themselves.
        if (!accepts(current)) return
        if (filtered == null) {
            retry()
            return
        }
        val number = when (config.mode) {
            SpeechMode.PHONE_ONLY -> SpeechNumberExtractor.extract(filtered, false)
            SpeechMode.PHONE_OR_VIRTUAL -> SpeechNumberExtractor.extract(filtered)
            SpeechMode.FREE_TEXT -> null
        }
        // The first matching segment is accepted even when isLast is false, as in Swift.
        if (config.stopOnResult) stop() else retry()
        onResult(SpeechRecognitionResult(config.mode, filtered, number, isLast))
    }

    private fun retry() {
        val pending = ++attempt
        cancelEngine()
        scheduleRetry {
            if (accepts(pending)) startAttempt()
        }
    }

    fun error(current: Long, error: SpeechRecognitionError) {
        if (!accepts(current)) return
        isListening = false
        ++attempt
        cancelEngine()
        onState(SpeechRecognitionState.ERROR)
        onError(error)
    }

    fun stop() {
        if (released || !isListening) return
        isListening = false
        ++attempt
        stopEngine()
        onState(SpeechRecognitionState.STOPPED)
    }

    fun release() {
        if (released) return
        released = true
        isListening = false
        ++attempt
        cancelEngine()
        onState(SpeechRecognitionState.RELEASED)
    }
}
