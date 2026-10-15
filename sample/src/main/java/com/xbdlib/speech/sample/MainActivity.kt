package com.xbdlib.speech.sample

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import com.xbdlib.speech.SpeechRecognitionPanel
import com.xbdlib.speech.SpeechRecognitionListener
import com.xbdlib.speech.SpeechRecognitionState
import com.xbdlib.speech.SpeechRecognitionResult
import com.xbdlib.speech.SpeechRecognitionError
import com.xbdlib.speech.SpeechRecognitionTheme

class MainActivity : Activity() {
    private lateinit var panel: SpeechRecognitionPanel
    private var redTheme = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        redTheme = savedInstanceState?.getBoolean("red_theme")
            ?: (intent.getStringExtra("speech_theme") == "red")
        panel = SpeechRecognitionPanel(this).apply {
            setTheme(if (redTheme) SpeechRecognitionTheme.RED else SpeechRecognitionTheme.BLUE)
            setAutoStart(intent.getBooleanExtra("auto_start", true))
            // Record lifecycle and error codes for device acceptance, without recognized text.
            setListener(object : SpeechRecognitionListener {
                override fun onStateChanged(state: SpeechRecognitionState) {
                    Log.i("SpeechSample", "state=$state")
                }
                override fun onResult(result: SpeechRecognitionResult) {
                    Log.i("SpeechSample", "result mode=${result.mode} final=${result.isFinal}")
                }
                override fun onError(error: SpeechRecognitionError) {
                    Log.e("SpeechSample", "error code=${error.code} message=${error.message}")
                }
            })
            setAppId(BuildConfig.IFLYTEK_APP_ID)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val themes = LinearLayout(this)
        val blue = Button(this).apply { text = "蓝色主题"; setTextColor(SpeechRecognitionTheme.BLUE.primaryColor) }
        val red = Button(this).apply { text = "红色主题"; setTextColor(SpeechRecognitionTheme.RED.primaryColor) }
        fun selectTheme(useRed: Boolean) {
            redTheme = useRed
            panel.setTheme(if (useRed) SpeechRecognitionTheme.RED else SpeechRecognitionTheme.BLUE)
            blue.alpha = if (useRed) .55f else 1f
            red.alpha = if (useRed) 1f else .55f
        }
        blue.setOnClickListener { selectTheme(false) }
        red.setOnClickListener { selectTheme(true) }
        themes.addView(blue, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        themes.addView(red, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(themes)
        root.addView(panel)
        setContentView(root)
        selectTheme(redTheme)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 7)
        } else if (intent.getBooleanExtra("show_dialog", false)) {
            panel.post { panel.start() }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("red_theme", redTheme)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        panel.release()
        super.onDestroy()
    }
}
