package com.xbdlib.speech

/** EasyIFly-compatible filtering: keep numeric characters and check the total length. */
object SpeechNumberExtractor {
    // Unicode numeric ideographs have letter categories, but Swift isNumber accepts them.
    // Keep them unchanged: this is a character filter, not Chinese-to-Arabic conversion.
    private const val numericIdeographs = "㐅㒃㠪㭍一七万三两九二五亖京亿什仟仨伍佰俩倆億兆兩八六十千卄卅卌叁参參叄四壱壹幺廾廿弌弍弎弐拐拾捌柒洞漆玖百皕秭肆萬貮貳贰鈎钩阡陆陌陸零參拾兩零六陸什𠀁𠁤𠃢𠄡𠤪𠦃𠦌𠦜𠫪𠫽𠬙𢎐𢦘𣬛𦉭廾"

    @JvmOverloads
    fun extract(text: String, allowVirtual: Boolean = true): PhoneVirtualResult? {
        val digits = normalize(text)
        val count = digits.codePointCount(0, digits.length)
        return when {
            count == 11 -> PhoneVirtualResult(phone = digits)
            allowVirtual && count in 14..15 -> {
                val boundary = digits.offsetByCodePoints(0, 11)
                val phone = digits.substring(0, boundary)
                val extension = digits.substring(boundary)
                PhoneVirtualResult(phone, "$phone-$extension", extension)
            }
            else -> null
        }
    }

    fun normalize(text: String): String = buildString {
        var index = 0
        while (index < text.length) {
            val point = text.codePointAt(index)
            val character = String(Character.toChars(point))
            val numeric = when (Character.getType(point)) {
                Character.DECIMAL_DIGIT_NUMBER.toInt(),
                Character.LETTER_NUMBER.toInt(),
                Character.OTHER_NUMBER.toInt() -> true
                else -> numericIdeographs.contains(character)
            }
            if (numeric) append(character)
            index += Character.charCount(point)
        }
    }
}
