package com.xbdlib.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.iflytek.cloud.ErrorCode
import com.iflytek.cloud.RecognizerListener
import com.iflytek.cloud.RecognizerResult
import com.iflytek.cloud.SpeechConstant
import com.iflytek.cloud.SpeechError
import com.iflytek.cloud.SpeechUtility
import java.io.Closeable
import java.io.File

/**
 * Kotlin facade over the bundled iFlytek engine.
 * It has no Activity, Fragment, or Compose requirement and can also be driven by the built-in panel.
 */
class SpeechRecognitionController(
    context: Context,
    private val config: SpeechRecognitionConfig,
    listener: SpeechRecognitionListener = object : SpeechRecognitionListener {},
) : Closeable {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: com.iflytek.cloud.SpeechRecognizer? = null
    private var released = false
    private var callback = listener
    internal val currentListener: SpeechRecognitionListener get() = callback
    private val session: SpeechRecognitionSession = SpeechRecognitionSession(
        config = config,
        startEngine = { attempt -> recognizer!!.startListening(listenerFor(attempt)) },
        stopEngine = { recognizer?.stopListening(); Unit },
        cancelEngine = { recognizer?.cancel(); Unit },
        scheduleRetry = { retry -> main.post { retry() }; Unit },
        onState = { callback.onStateChanged(it) },
        onResult = { callback.onResult(it) },
        onError = { callback.onError(it) },
    )

    init { checkMain(); initSdk() }

    fun updateListener(listener: SpeechRecognitionListener) { checkMain(); callback = listener }

    fun start() {
        checkMain(); check(!released) { "语音控制器已释放" }
        if (appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            emitError(SpeechRecognitionError(message = "需要 RECORD_AUDIO 权限")); return
        }
        val current = recognizer
        if (current == null) {
            emitError(SpeechRecognitionError(message = "讯飞识别器初始化失败")); return
        }
        session.start()
    }

    fun stop() { checkMain(); session.stop() }

    fun isListening(): Boolean = session.isListening

    override fun close() = release()

    fun release() {
        checkMain()
        if (released) return
        released = true
        session.release()
        recognizer?.destroy()
        recognizer = null
    }

    private fun initSdk() {
        SpeechUtility.createUtility(appContext, "${SpeechConstant.APPID}=${config.appId},${SpeechConstant.FORCE_LOGIN}=true")
        recognizer = com.iflytek.cloud.SpeechRecognizer.createRecognizer(appContext) { code ->
            if (code != ErrorCode.SUCCESS) main.post {
                if (!released) emitError(SpeechRecognitionError(code = code, message = "讯飞 SDK 初始化失败($code)"))
            }
        }
        recognizer?.apply {
            setParameter(SpeechConstant.PARAMS, null)
            setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD)
            setParameter(SpeechConstant.DOMAIN, "iat")
            setParameter(SpeechConstant.KEY_SPEECH_TIMEOUT, config.speechTimeoutMillis.toString())
            setParameter(SpeechConstant.NET_TIMEOUT, config.networkTimeoutMillis.toString())
            setParameter(SpeechConstant.SAMPLE_RATE, "16000")
            setParameter(SpeechConstant.RESULT_TYPE, "json")
            setParameter(SpeechConstant.LANGUAGE, config.language)
            setParameter(SpeechConstant.ACCENT, config.accent)
            setParameter(SpeechConstant.VAD_BOS, config.vadBosMillis.toString())
            setParameter(SpeechConstant.VAD_EOS, config.vadEosMillis.toString())
            setParameter(SpeechConstant.ASR_PTT, "0")
            if (config.saveAudio) {
                val directory = File(appContext.filesDir, "speech")
                directory.mkdirs()
                setParameter(SpeechConstant.AUDIO_FORMAT, "wav")
                setParameter(SpeechConstant.ASR_AUDIO_PATH, File(directory, "iat.wav").absolutePath)
            }
        }
    }

    private fun listenerFor(attempt: Long): RecognizerListener = object : RecognizerListener {
        override fun onVolumeChanged(volume: Int, data: ByteArray?) { main.post {
            if (session.accepts(attempt)) callback.onVolumeChanged(volume)
        } }
        override fun onBeginOfSpeech() { main.post { session.state(attempt, SpeechRecognitionState.LISTENING) } }
        override fun onEndOfSpeech() { main.post { session.state(attempt, SpeechRecognitionState.PROCESSING) } }
        override fun onError(error: SpeechError) { main.post {
            session.error(attempt, SpeechRecognitionError(error.errorCode, error.getPlainDescription(false)))
        } }
        override fun onEvent(eventType: Int, arg1: Int, arg2: Int, obj: Bundle?) = Unit
        override fun onResult(result: RecognizerResult, isLast: Boolean) { main.post {
            session.result(attempt, IflyResultParser.parse(result.resultString.orEmpty()), isLast)
        } }
    }

    private fun emitState(state: SpeechRecognitionState) = callback.onStateChanged(state)
    private fun emitError(error: SpeechRecognitionError) { emitState(SpeechRecognitionState.ERROR); callback.onError(error) }
    private fun checkMain() = check(Looper.myLooper() == Looper.getMainLooper()) { "请在主线程调用语音控制器" }
}
