package com.xbdlib.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeechNumberExtractorTest {
    @Test fun stripsSeparatorsAndLabels() {
        assertEquals("13800138000", SpeechNumberExtractor.extract("号码：138 0013-8000")?.phone)
    }

    @Test fun formatsBothExtensionLengthsWithHyphen() {
        assertEquals("13800138000-123", SpeechNumberExtractor.extract("13800138000 转 123")?.value)
        val result = SpeechNumberExtractor.extract("13800138000#1234")
        assertEquals("13800138000-1234", result?.virtualNumber)
        assertEquals("1234", result?.extension)
    }

    @Test fun rejectsEntireInputWithWrongLengthRatherThanExtractingASubstring() {
        for (count in listOf(0, 10, 12, 13, 16, 22)) {
            assertNull("length $count", SpeechNumberExtractor.extract("1".repeat(count)))
        }
    }

    @Test fun phoneOnlyRejectsVirtualNumbers() {
        assertEquals("13800138000", SpeechNumberExtractor.extract("13800138000", false)?.value)
        assertNull(SpeechNumberExtractor.extract("13800138000-1234", false))
    }

    @Test fun acceptsAnyElevenNumericCharactersWithoutPrefixValidation() {
        assertEquals("01234567890", SpeechNumberExtractor.extract("01234567890")?.phone)
        assertEquals("１２３４５６７８９０１", SpeechNumberExtractor.extract("１２３４５６７８９０１")?.phone)
    }

    @Test fun preservesChineseNumericCharactersAsSwiftDoes() {
        assertEquals("一三八零零一三八零零零", SpeechNumberExtractor.extract("号码：一三八零零一三八零零零")?.phone)
        assertEquals("幺三八零零幺三八零零零-幺二三", SpeechNumberExtractor.extract("幺三八零零幺三八零零零转幺二三")?.value)
        assertEquals("一幺〇Ⅳ①", SpeechNumberExtractor.normalize("一幺〇Ⅳ①Ab#"))
    }

    @Test fun countsSupplementaryNumericCharactersWithoutSplittingSurrogatePairs() {
        val phone = "𝟙".repeat(11)
        assertEquals("$phone-𝟚𝟚𝟚", SpeechNumberExtractor.extract(phone + "𝟚".repeat(3))?.value)
    }

    @Test fun rejectsOrdinaryText() { assertNull(SpeechNumberExtractor.extract("今天下午三点开会")) }
}
