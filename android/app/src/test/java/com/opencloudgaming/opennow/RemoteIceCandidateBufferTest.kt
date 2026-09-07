package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.webrtc.IceCandidate

class RemoteIceCandidateBufferTest {
    @Test
    fun candidatesWaitForRemoteDescriptionAndThenArriveInOrder() {
        val buffer = RemoteIceCandidateBuffer()
        val delivered = mutableListOf<IceCandidate>()
        val candidates = (0..2).map(::candidate)

        buffer.receive(candidates[0]) { delivered += it }
        buffer.receive(candidates[1]) { delivered += it }
        assertTrue(delivered.isEmpty())

        buffer.onRemoteDescriptionSet { delivered += it }
        buffer.receive(candidates[2]) { delivered += it }
        assertEquals(candidates, delivered)

        buffer.onRemoteDescriptionSet { delivered += it }
        assertEquals(candidates, delivered)
    }

    @Test
    fun overflowRetainsTheNewest120CandidatesInOrder() {
        val buffer = RemoteIceCandidateBuffer()
        val delivered = mutableListOf<IceCandidate>()
        val candidates = (0..120).map(::candidate)

        candidates.forEach { buffer.receive(it) { delivered += it } }
        assertTrue(delivered.isEmpty())
        buffer.onRemoteDescriptionSet { delivered += it }
        assertEquals(candidates.drop(1), delivered)
    }

    @Test
    fun closingBeforeRemoteDescriptionDiscardsTheOldPeersCandidates() {
        val buffer = RemoteIceCandidateBuffer()
        val delivered = mutableListOf<IceCandidate>()
        buffer.receive(candidate(0)) { delivered += it }
        buffer.clear()

        val nextCandidate = candidate(1)
        buffer.receive(nextCandidate) { delivered += it }
        assertTrue(delivered.isEmpty())
        buffer.onRemoteDescriptionSet { delivered += it }
        assertEquals(listOf(nextCandidate), delivered)
    }

    @Test
    fun closingAReadyPeerMakesTheNextPeerWaitForItsOwnDescription() {
        val buffer = RemoteIceCandidateBuffer()
        val delivered = mutableListOf<IceCandidate>()
        buffer.onRemoteDescriptionSet { delivered += it }
        buffer.clear()

        val nextCandidate = candidate(1)
        buffer.receive(nextCandidate) { delivered += it }
        assertTrue(delivered.isEmpty())
        buffer.onRemoteDescriptionSet { delivered += it }
        assertEquals(listOf(nextCandidate), delivered)
    }

    private fun candidate(index: Int) = IceCandidate("0", 0, "candidate:$index")
}
