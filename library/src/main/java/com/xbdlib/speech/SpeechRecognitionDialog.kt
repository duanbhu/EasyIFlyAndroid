package com.xbdlib.speech

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** EasyIFly-style sheet: submit the first matching result and dismiss automatically. */
class SpeechRecognitionDialog @JvmOverloads constructor(
    context: Context,
    private val controller: SpeechRecognitionController,
    private val mode: SpeechMode,
    private val listener: SpeechRecognitionListener? = null,
    private val autoStart: Boolean = true,
    private val title: String? = null,
    private val onDismiss: (() -> Unit)? = null,
    private var theme: SpeechRecognitionTheme = SpeechRecognitionTheme.BLUE,
) : Dialog(context) {
    private lateinit var waveform: SpeechWaveformView
    private lateinit var status: TextView
    private lateinit var content: LinearLayout
    private lateinit var microphone: SpeechMicView
    private lateinit var endButton: Button
    private var previousListener: SpeechRecognitionListener? = null
    private var targetListener: SpeechRecognitionListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(createContent())
        setCanceledOnTouchOutside(true)
        setOnDismissListener { onDismiss?.invoke() }
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = .4f; gravity = Gravity.BOTTOM; y = dp(20) }
        }
    }

    override fun onStart() {
        super.onStart()
        window?.setLayout(context.resources.displayMetrics.widthPixels - dp(64), WindowManager.LayoutParams.WRAP_CONTENT)
        previousListener = controller.currentListener
        targetListener = listener ?: previousListener
        controller.updateListener(dialogListener)
        // Swift starts its fixed frame animation even when autoStart is false.
        waveform.start()
        if (autoStart) startListening()
    }

    fun startListening() = controller.start()

    /** Updates the visible sheet immediately without restarting recognition. */
    fun setTheme(theme: SpeechRecognitionTheme) {
        this.theme = theme
        if (::content.isInitialized) applyTheme()
    }

    fun stopListening() {
        controller.stop()
        if (::waveform.isInitialized) waveform.stop()
    }

    override fun onStop() {
        stopListening()
        previousListener?.let { controller.updateListener(it) }
        previousListener = null
        targetListener = null
        super.onStop()
    }

    private val dialogListener = object : SpeechRecognitionListener {
        override fun onStateChanged(state: SpeechRecognitionState) { targetListener?.onStateChanged(state) }
        override fun onVolumeChanged(volume: Int) { targetListener?.onVolumeChanged(volume) }
        override fun onResult(result: SpeechRecognitionResult) {
            // Exactly one delivery: there is no second confirmation callback.
            try { targetListener?.onResult(result) } finally { dismiss() }
        }
        override fun onError(error: SpeechRecognitionError) {
            status.text = error.message
            waveform.stop()
            targetListener?.onError(error)
        }
    }

    private fun createContent(): View {
        content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(15).toFloat() }
            clipToOutline = true
        }
        status = TextView(context).apply {
            text = title ?: if (mode == SpeechMode.FREE_TEXT) "请语音输入" else "请语音输入手机号"
            setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(0, 122, 255))
        }
        content.addView(status, LinearLayout.LayoutParams(-1, dp(48)))
        val visual = FrameLayout(context)
        waveform = SpeechWaveformView(context)
        visual.addView(waveform, FrameLayout.LayoutParams(dp(244), dp(47), Gravity.CENTER))
        microphone = SpeechMicView(context)
        visual.addView(microphone, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER))
        content.addView(visual, LinearLayout.LayoutParams(-1, dp(92)))
        endButton = Button(context).apply {
            text = "结束录入"; textSize = 16f; setTextColor(Color.rgb(0, 122, 255)); isAllCaps = false
            background = GradientDrawable().apply { setColor(Color.rgb(240, 242, 255)) }
            setOnClickListener { dismiss() }
        }
        content.addView(endButton, LinearLayout.LayoutParams(-1, dp(55)))
        applyTheme()
        return content
    }

    private fun applyTheme() {
        (content.background as GradientDrawable).setColor(theme.backgroundColor)
        status.setBackgroundColor(theme.titleBackgroundColor)
        status.setTextColor(theme.titleTextColor)
        waveform.color = theme.waveformColor
        microphone.color = theme.microphoneColor
        endButton.setTextColor(theme.buttonTextColor)
        (endButton.background as GradientDrawable).setColor(theme.buttonBackgroundColor)
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}

internal class SpeechWaveformView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(145, 170, 247); strokeWidth = 2f }
    var color: Int
        get() = paint.color
        set(value) { paint.color = value; invalidate() }
    private val bars = floatArrayOf(.24f, .42f, .62f, .35f, .75f, .48f, .28f, .58f, .82f, .36f)
    private var running = false
    private var frame = 0
    private val ticker = object : Runnable {
        override fun run() {
            if (running) { frame = (frame + 1) % 5; invalidate(); postDelayed(this, 350) }
        }
    }
    fun start() { if (!running) { running = true; post(ticker) } }
    fun stop() { running = false; removeCallbacks(ticker); invalidate() }
    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val center = height / 2f
        val gap = width * 90f / 244 / bars.size
        val inner = width * 32f / 244
        for (side in 0..1) for (i in bars.indices) {
            val x = width / 2f + (if (side == 0) -1 else 1) * (inner + i * gap)
            val h = height * bars[(i + frame) % bars.size]
            canvas.drawLine(x, center - h / 2, x, center + h / 2, paint)
        }
    }
}

internal class SpeechMicView(context: Context) : View(context) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(110, 142, 231); style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density * 2; strokeCap = Paint.Cap.ROUND
    }
    var color: Int
        get() = line.color
        set(value) { line.color = value; invalidate() }
    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val unit = minOf(width, height) / 44f
        canvas.drawRoundRect(RectF(cx - 6 * unit, cy - 18 * unit, cx + 6 * unit, cy + 4 * unit), 6 * unit, 6 * unit, line)
        canvas.drawArc(RectF(cx - 12 * unit, cy - 8 * unit, cx + 12 * unit, cy + 12 * unit), 0f, 180f, false, line)
        canvas.drawLine(cx, cy + 12 * unit, cx, cy + 19 * unit, line)
        canvas.drawLine(cx - 7 * unit, cy + 19 * unit, cx + 7 * unit, cy + 19 * unit, line)
    }
}
