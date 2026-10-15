package com.xbdlib.speech

/** Colors are Android ARGB ints. Each part can be overridden independently with copy(). */
data class SpeechRecognitionTheme(
    val primaryColor: Int,
    val titleBackgroundColor: Int = primaryColor,
    val titleTextColor: Int = 0xffffffff.toInt(),
    val waveformColor: Int = primaryColor,
    val microphoneColor: Int = primaryColor,
    val buttonBackgroundColor: Int = 0xfff5f5f5.toInt(),
    val buttonTextColor: Int = primaryColor,
    val backgroundColor: Int = 0xffffffff.toInt(),
) {
    companion object {
        /** Preserves the original blue sheet's exact colors. */
        @JvmField val BLUE = SpeechRecognitionTheme(
            primaryColor = 0xff007aff.toInt(),
            waveformColor = 0xff91aaf7.toInt(),
            microphoneColor = 0xff6e8ee7.toInt(),
            buttonBackgroundColor = 0xfff0f2ff.toInt(),
        )

        @JvmField val RED = SpeechRecognitionTheme(
            primaryColor = 0xffff3b30.toInt(),
            titleBackgroundColor = 0xfffff5e6.toInt(),
            titleTextColor = 0xff000000.toInt(),
            waveformColor = 0xffff8a80.toInt(),
            buttonBackgroundColor = 0xffffefed.toInt(),
        )
    }
}
