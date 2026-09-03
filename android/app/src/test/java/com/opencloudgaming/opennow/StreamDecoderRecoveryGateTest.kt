package com.opencloudgaming.opennow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamDecoderRecoveryGateTest {
    @Test
    fun sustainedAdvancedCodecOverloadRequestsOneRecovery() {
        val gate = StreamDecoderRecoveryGate(badSamplesBeforeRecovery = 3)
        val overloaded = StreamRuntimeStats(receivedFps = 60, decodedFps = 34, decodeMs = 38.0)

        assertFalse(gate.observe(overloaded, requestedFps = 60, advancedCodecActive = true, recoveryEligible = true))
        assertFalse(gate.observe(overloaded, requestedFps = 60, advancedCodecActive = true, recoveryEligible = true))
        assertTrue(gate.observe(overloaded, requestedFps = 60, advancedCodecActive = true, recoveryEligible = true))
        assertFalse(gate.observe(overloaded, requestedFps = 60, advancedCodecActive = true, recoveryEligible = true))
    }

    @Test
    fun networkLimitedInputDoesNotBlameDecoder() {
        val gate = StreamDecoderRecoveryGate(badSamplesBeforeRecovery = 2)
        val networkLimited = StreamRuntimeStats(receivedFps = 18, decodedFps = 18, decodeMs = 40.0)

        repeat(4) {
            assertFalse(gate.observe(networkLimited, requestedFps = 60, advancedCodecActive = true, recoveryEligible = true))
        }
    }

    @Test
    fun rendererDetachmentAndH264NeverTriggerRecovery() {
        val gate = StreamDecoderRecoveryGate(badSamplesBeforeRecovery = 2)
        val overloaded = StreamRuntimeStats(receivedFps = 60, decodedFps = 30, decodeMs = 40.0)

        repeat(3) {
            assertFalse(gate.observe(overloaded, requestedFps = 60, advancedCodecActive = true, recoveryEligible = false))
            assertFalse(gate.observe(overloaded, requestedFps = 60, advancedCodecActive = false, recoveryEligible = true))
        }
    }

    @Test
    fun healthySampleBreaksTheEvidenceChain() {
        val gate = StreamDecoderRecoveryGate(badSamplesBeforeRecovery = 2)
        val overloaded = StreamRuntimeStats(receivedFps = 60, decodedFps = 34, decodeMs = 38.0)
        val healthy = StreamRuntimeStats(receivedFps = 60, decodedFps = 59, decodeMs = 14.0)

        assertFalse(gate.observe(overloaded, requestedFps = 60, advancedCodecActive = true, recoveryEligible = true))
        assertFalse(gate.observe(healthy, requestedFps = 60, advancedCodecActive = true, recoveryEligible = true))
        assertFalse(gate.observe(overloaded, requestedFps = 60, advancedCodecActive = true, recoveryEligible = true))
    }
}
