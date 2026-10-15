package com.xbdlib.speech

import org.junit.Assert.*
import org.junit.Test

class SpeechRecognitionSessionTest {
    private class Harness(config: SpeechRecognitionConfig = SpeechRecognitionConfig("test")) {
        val starts = mutableListOf<Long>()
        val pending = mutableListOf<() -> Unit>()
        val states = mutableListOf<SpeechRecognitionState>()
        val results = mutableListOf<SpeechRecognitionResult>()
        val errors = mutableListOf<SpeechRecognitionError>()
        var stops = 0
        var cancels = 0
        var startCode = 0
        val session = SpeechRecognitionSession(config,
            startEngine = { starts.add(it); startCode },
            stopEngine = { stops++ }, cancelEngine = { cancels++ },
            scheduleRetry = { pending.add(it) },
            onState = { states.add(it) }, onResult = { results.add(it) }, onError = { errors.add(it) },
        )
        val attempt get() = starts.last()
        fun result(text: String?, isLast: Boolean = true) = session.result(attempt, text, isLast)
        fun retry() { pending.removeAt(0).invoke() }
    }

    @Test fun matchingIntermediateResultStopsAndSubmitsOnce() {
        val h = Harness()
        h.session.start()
        val original = h.attempt
        h.result("13800138000", false)
        h.session.result(original, "13800138000-1234", true)
        assertEquals(1, h.results.size)
        assertEquals("13800138000", h.results.single().text)
        assertFalse(h.results.single().isFinal)
        assertFalse(h.session.isListening)
        assertEquals(1, h.stops)
        assertTrue(h.pending.isEmpty())
    }

    @Test fun rejectedResultRetriesAndIgnoresStaleCallbacks() {
        val h = Harness()
        h.session.start()
        val old = h.attempt
        h.result("错误号码")
        h.session.result(old, "13800138000", true)
        h.session.error(old, SpeechRecognitionError(message = "cancelled"))
        h.retry()
        h.result("13800138000-1234")
        assertEquals(2, h.starts.size)
        assertEquals("13800138000-1234", h.results.single().phone?.value)
        assertTrue(h.errors.isEmpty())
    }

    @Test fun segmentsAreNotAccumulated() {
        val h = Harness()
        h.session.start()
        h.result("1380013", false)
        h.retry()
        h.result("8000", true)
        assertTrue(h.results.isEmpty())
        assertEquals(1, h.pending.size)
    }

    @Test fun stopCancelsPendingRetry() {
        val h = Harness()
        h.session.start()
        h.result(null)
        h.session.stop()
        h.retry()
        assertEquals(1, h.starts.size)
        assertFalse(h.session.isListening)
    }

    @Test fun releaseCancelsPendingRetryAndIgnoresLateResults() {
        val h = Harness()
        h.session.start()
        h.result("138")
        val old = h.attempt
        h.session.release()
        h.retry()
        h.session.result(old, "13800138000", true)
        h.session.state(old, SpeechRecognitionState.LISTENING)
        assertEquals(1, h.starts.size)
        assertTrue(h.results.isEmpty())
        assertEquals(SpeechRecognitionState.RELEASED, h.states.last())
    }

    @Test fun explicitRestartInvalidatesPreviousAttemptAndQueuedRetry() {
        val h = Harness()
        h.session.start()
        val old = h.attempt
        h.result("138")
        h.session.start()
        h.retry()
        h.session.result(old, "13800138000", true)
        h.result("13900139000")
        assertEquals(2, h.starts.size)
        assertEquals("13900139000", h.results.single().text)
    }

    @Test fun phoneOnlyRejectsExtensionsUntilAnElevenCharacterResult() {
        val h = Harness(SpeechRecognitionConfig("test", SpeechMode.PHONE_ONLY))
        h.session.start()
        h.result("138001380001234")
        h.retry()
        h.result("01234567890")
        assertEquals("01234567890", h.results.single().phone?.value)
    }

    @Test fun freeTextReturnsFirstSegmentUnfiltered() {
        val h = Harness(SpeechRecognitionConfig("test", SpeechMode.FREE_TEXT))
        h.session.start()
        h.result("今天下午三点开会", false)
        assertEquals("今天下午三点开会", h.results.single().text)
        assertNull(h.results.single().phone)
    }

    @Test fun customFilterCanRejectAndTransform() {
        val h = Harness(SpeechRecognitionConfig("test", SpeechMode.FREE_TEXT,
            resultFilter = { if (it.startsWith("地址")) it.removePrefix("地址：") else null }))
        h.session.start()
        h.result("重说一次")
        h.retry()
        h.result("地址：上海")
        assertEquals("上海", h.results.single().text)
    }

    @Test fun malformedResponseRetriesEvenInFreeTextMode() {
        val h = Harness(SpeechRecognitionConfig("test", SpeechMode.FREE_TEXT))
        h.session.start()
        h.result(null)
        assertTrue(h.results.isEmpty())
        h.retry()
        assertEquals(2, h.starts.size)
    }

    @Test fun sdkErrorIsReportedOnceWithoutAutomaticRetry() {
        val h = Harness()
        h.session.start()
        val attempt = h.attempt
        h.session.error(attempt, SpeechRecognitionError(101, "network"))
        h.session.error(attempt, SpeechRecognitionError(101, "network"))
        h.result("13800138000")
        assertEquals(101, h.errors.single().code)
        assertTrue(h.results.isEmpty())
        assertTrue(h.pending.isEmpty())
        assertFalse(h.session.isListening)
    }

    @Test fun startFailureEndsSession() {
        val h = Harness()
        h.startCode = 101
        h.session.start()
        assertEquals(101, h.errors.single().code)
        assertEquals(SpeechRecognitionState.ERROR, h.states.last())
        assertFalse(h.session.isListening)
    }

    @Test fun explicitContinuousOptionRestartsAfterValidResult() {
        val h = Harness(SpeechRecognitionConfig("test", stopOnResult = false))
        h.session.start()
        h.result("13800138000")
        h.retry()
        assertEquals(1, h.results.size)
        assertEquals(2, h.starts.size)
        assertTrue(h.session.isListening)
    }
}
