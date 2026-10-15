package com.xbdlib.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A self-contained Android panel. It can be put in XML or hosted from Compose with AndroidView.
 * The host still owns the runtime RECORD_AUDIO permission request.
 */
class SpeechRecognitionPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {
    private var appId: String? = null
    private var mode = SpeechMode.PHONE_OR_VIRTUAL
    private var autoStart = true
    private var theme = SpeechRecognitionTheme.BLUE
    private var resultFilter: ((String) -> String?)? = null
    private var controller: SpeechRecognitionController? = null
    private var dialog: SpeechRecognitionDialog? = null
    private var externalListener: SpeechRecognitionListener? = null

    private val modeTitle = TextView(context).apply { textSize = 16f; setTextColor(Color.DKGRAY) }
    private val status = TextView(context).apply { textSize = 13f; setTextColor(Color.GRAY) }
    private val result = TextView(context).apply { textSize = 20f; setTextColor(Color.BLACK); minLines = 2 }
    private val phoneOnlyMode = Button(context).apply { text = "手机号" }
    private val phoneMode = Button(context).apply { text = "手机号／虚拟号" }
    private val textMode = Button(context).apply { text = "普通文字" }
    private val action = Button(context).apply { text = "开始说话" }
    private val buttonDefaults = listOf(phoneOnlyMode, phoneMode, textMode, action)
        .associateWith { it.backgroundTintList to it.textColors }

    init {
        orientation = VERTICAL
        setPadding(20, 16, 20, 16)
        background = GradientDrawable().apply {
            setColor(Color.WHITE); cornerRadius = 24f; setStroke(1, 0xffdddddd.toInt())
        }
        addView(modeTitle, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(status, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = 6 })
        val modes = LinearLayout(context).apply { gravity = Gravity.CENTER; weightSum = 3f }
        modes.addView(phoneOnlyMode, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        modes.addView(phoneMode, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        modes.addView(textMode, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(modes)
        addView(result, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = 12; bottomMargin = 8 })
        addView(action, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        phoneOnlyMode.setOnClickListener { setMode(SpeechMode.PHONE_ONLY) }
        phoneMode.setOnClickListener { setMode(SpeechMode.PHONE_OR_VIRTUAL) }
        textMode.setOnClickListener { setMode(SpeechMode.FREE_TEXT) }
        action.setOnClickListener { showRecognitionDialog() }
        renderMode()
    }

    /** Set the App ID issued by iFlytek before binding the panel. */
    fun setAppId(appId: String) {
        require(appId.isNotBlank())
        if (this.appId != appId) { this.appId = appId; rebuildController() }
    }

    fun setMode(mode: SpeechMode) {
        if (this.mode == mode) return
        this.mode = mode
        rebuildController()
        renderMode()
    }

    fun setAutoStart(autoStart: Boolean) { this.autoStart = autoStart }

    /** Applies to panel controls and the current/future sheet, preserving recognition state. */
    fun setTheme(theme: SpeechRecognitionTheme) {
        this.theme = theme
        buttonDefaults.forEach { (button, original) ->
            if (theme == SpeechRecognitionTheme.BLUE) {
                button.backgroundTintList = original.first
                button.setTextColor(original.second)
            } else {
                button.backgroundTintList = ColorStateList.valueOf(theme.buttonBackgroundColor)
                button.setTextColor(theme.buttonTextColor)
            }
        }
        dialog?.setTheme(theme)
    }

    fun setResultFilter(filter: ((String) -> String?)?) {
        resultFilter = filter
        rebuildController()
    }

    fun setListener(listener: SpeechRecognitionListener?) { externalListener = listener }

    /** Prepares the controller. Call release() from the host's onDestroy/DisposableEffect. */
    fun bind(@Suppress("UNUSED_PARAMETER") owner: Any? = null) {
        rebuildController()
    }

    fun start() = showRecognitionDialog()
    fun startListening() = controller?.start()
    fun stop() { controller?.stop(); dialog?.dismiss(); dialog = null }

    fun release() {
        dialog?.dismiss()
        dialog = null
        controller?.release()
        controller = null
    }

    /** Shows the EasyIFly-style recording sheet. */
    fun showRecognitionDialog() {
        val current = controller
        if (current == null) {
            status.text = "请先设置讯飞 App ID"
            return
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.text = "请先授予麦克风权限"
            return
        }
        dialog?.dismiss()
        dialog = SpeechRecognitionDialog(context, current, mode, autoStart = autoStart, theme = theme).also { it.show() }
    }

    private fun rebuildController() {
        dialog?.dismiss()
        dialog = null
        controller?.release()
        val id = appId ?: return
        controller = SpeechRecognitionController(context, SpeechRecognitionConfig(id, mode, resultFilter = resultFilter), object : SpeechRecognitionListener {
            override fun onStateChanged(state: SpeechRecognitionState) {
                status.text = when (state) {
                    SpeechRecognitionState.LISTENING -> "正在聆听，请开始说话"
                    SpeechRecognitionState.PROCESSING -> "正在识别…"
                    SpeechRecognitionState.STOPPED -> "已停止"
                    SpeechRecognitionState.ERROR -> "识别失败"
                    SpeechRecognitionState.RELEASED -> "已释放"
                    SpeechRecognitionState.IDLE -> "准备就绪"
                }
                externalListener?.onStateChanged(state)
            }
            override fun onVolumeChanged(volume: Int) { externalListener?.onVolumeChanged(volume) }
            override fun onResult(value: SpeechRecognitionResult) {
                result.text = if (value.mode != SpeechMode.FREE_TEXT) {
                    value.phone?.let { number ->
                        buildString {
                            append("手机号：").append(number.phone ?: "未识别")
                            number.virtualNumber?.let { append("\n虚拟号：").append(it) }
                        }
                    } ?: value.text
                } else value.text
                externalListener?.onResult(value)
            }
            override fun onError(error: SpeechRecognitionError) {
                status.text = error.message
                externalListener?.onError(error)
            }
        })
    }

    private fun renderMode() {
        modeTitle.text = when (mode) {
            SpeechMode.PHONE_ONLY -> "语音录入手机号"
            SpeechMode.PHONE_OR_VIRTUAL -> "语音录入手机号／虚拟号"
            SpeechMode.FREE_TEXT -> "语音转文字"
        }
        result.text = "请点击开始，说出要录入的内容"
        phoneOnlyMode.alpha = if (mode == SpeechMode.PHONE_ONLY) 1f else .55f
        phoneMode.alpha = if (mode == SpeechMode.PHONE_OR_VIRTUAL) 1f else .55f
        textMode.alpha = if (mode == SpeechMode.FREE_TEXT) 1f else .55f
    }
}
