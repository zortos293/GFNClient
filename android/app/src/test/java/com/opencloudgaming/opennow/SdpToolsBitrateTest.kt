package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the live bitrate ceiling update: replacing b=AS in the video section of the local SDP
 * without touching audio, idempotently, preserving line endings and section boundaries.
 */
class SdpToolsBitrateTest {

    private fun videoBitrate(sdp: String): Int? =
        Regex("m=video[\\s\\S]*?b=AS:(\\d+)").find(sdp)?.groupValues?.get(1)?.toInt()

    private fun audioBitrate(sdp: String): Int? =
        Regex("m=audio[\\s\\S]*?b=AS:(\\d+)").find(sdp)?.groupValues?.get(1)?.toInt()

    @Test
    fun replacesVideoBitrateWithoutTouchingAudio() {
        val sdp = "v=0\r\n" +
            "m=audio 9 UDP/TLS/RTP/SAVPF 111\r\n" +
            "b=AS:128\r\n" +
            "a=fmtp:111 minptime=10\r\n" +
            "m=video 9 UDP/TLS/RTP/SAVPF 96\r\n" +
            "b=AS:75000\r\n" +
            "a=rtpmap:96 VP8\r\n"
        val out = SdpTools.replaceVideoBitrateInSdp(sdp, 50000)
        assertEquals(50000, videoBitrate(out))
        assertEquals(128, audioBitrate(out))
    }

    @Test
    fun isIdempotentAcrossRepeatedCalls() {
        val sdp = "v=0\n" +
            "m=video 9 UDP/TLS/RTP/SAVPF 96\n" +
            "b=AS:75000\n" +
            "m=audio 9 UDP/TLS/RTP/SAVPF 111\n" +
            "b=AS:128\n"
        val once = SdpTools.replaceVideoBitrateInSdp(sdp, 40000)
        val twice = SdpTools.replaceVideoBitrateInSdp(once, 40000)
        assertEquals(once, twice)
    }

    @Test
    fun replacesOnlyTheFirstVideoBitrateLine() {
        val sdp = "v=0\n" +
            "m=video 9 UDP/TLS/RTP/SAVPF 96\n" +
            "b=AS:75000\n" +
            "b=AS:99999\n"
        val out = SdpTools.replaceVideoBitrateInSdp(sdp, 30000)
        assertEquals("v=0\nm=video 9 UDP/TLS/RTP/SAVPF 96\nb=AS:30000\nb=AS:99999\n", out)
    }

    @Test
    fun preservesLineEndings() {
        val sdp = "v=0\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\nb=AS:75000\r\n"
        assertTrue(SdpTools.replaceVideoBitrateInSdp(sdp, 60000).contains("\r\n"))
    }

    @Test
    fun leavesSdpWithoutVideoBitrateUntouched() {
        val sdp = "v=0\nm=video 9 UDP/TLS/RTP/SAVPF 96\n"
        assertEquals(sdp, SdpTools.replaceVideoBitrateInSdp(sdp, 60000))
    }

    @Test
    fun updatesEveryVideoSectionButNeverAudio() {
        val sdp = "v=0\n" +
            "m=video 9 UDP/TLS/RTP/SAVPF 96\n" +
            "b=AS:75000\n" +
            "m=audio 9 UDP/TLS/RTP/SAVPF 111\n" +
            "b=AS:128\n" +
            "m=video 9 UDP/TLS/RTP/SAVPF 97\n" +
            "b=AS:90000\n"
        val out = SdpTools.replaceVideoBitrateInSdp(sdp, 20000)
        // Every video section's b=AS is replaced (the flag resets per m= section), audio is untouched.
        assertEquals("v=0\n" +
            "m=video 9 UDP/TLS/RTP/SAVPF 96\n" +
            "b=AS:20000\n" +
            "m=audio 9 UDP/TLS/RTP/SAVPF 111\n" +
            "b=AS:128\n" +
            "m=video 9 UDP/TLS/RTP/SAVPF 97\n" +
            "b=AS:20000\n", out)
    }
}
