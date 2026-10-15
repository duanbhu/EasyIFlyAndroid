import com.xbdlib.speech.SpeechMode
import com.xbdlib.speech.SpeechNumberExtractor
import com.xbdlib.speech.SpeechRecognitionConfig
import com.xbdlib.speech.SpeechRecognitionTheme

fun speechKotlin18Consumer() = SpeechRecognitionConfig("app-id", SpeechMode.PHONE_OR_VIRTUAL)
fun speechPhone(text: String) = SpeechNumberExtractor.extract(text)?.value
fun speechPhoneOnly(text: String) = SpeechNumberExtractor.extract(text, allowVirtual = false)?.value
fun speechCustomFilter() = SpeechRecognitionConfig(
    "app-id", SpeechMode.FREE_TEXT,
    resultFilter = { text -> text.takeIf { it.isNotBlank() } },
)
fun speechRedTheme() = SpeechRecognitionTheme.RED
fun speechCustomTheme() = SpeechRecognitionTheme(primaryColor = 0xff008577.toInt())
