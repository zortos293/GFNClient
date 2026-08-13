package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidDeviceDiagnosticsTest {
    @Test
    fun debugSummaryContainsUsefulNonUniqueSupportContext() {
        val summary = snapshot().debugSummary()

        assertTrue(summary.contains("manufacturer=NVIDIA"))
        assertTrue(summary.contains("model=SHIELD_Android_TV"))
        assertTrue(summary.contains("formFactor=tv"))
        assertTrue(summary.contains("release=11"))
        assertTrue(summary.contains("sdk=30"))
        assertTrue(summary.contains("targetSdk=36"))
        assertTrue(summary.contains("securityPatch=2025-04-05"))
        assertTrue(summary.contains("abis=arm64-v8a|armeabi-v7a"))
        assertTrue(summary.contains("runtimeBits=64"))
        assertTrue(summary.contains("memoryMiB=3072"))
        assertTrue(summary.contains("pixels=3840x2160"))
        assertFalse(summary.contains("serial"))
        assertFalse(summary.contains("fingerprint"))
        assertFalse(summary.contains("androidId"))
    }

    @Test
    fun classifiesTvTabletAndPhoneFormFactors() {
        assertEquals("tv", androidDeviceFormFactor(androidTv = true, smallestScreenWidthDp = 320))
        assertEquals("tablet", androidDeviceFormFactor(androidTv = false, smallestScreenWidthDp = 600))
        assertEquals("phone", androidDeviceFormFactor(androidTv = false, smallestScreenWidthDp = 599))
    }

    private fun snapshot() = AndroidDeviceDiagnosticsSnapshot(
        manufacturer = "NVIDIA",
        brand = "NVIDIA",
        model = "SHIELD_Android_TV",
        deviceCodename = "mdarcy",
        product = "mdarcy",
        hardware = "darcy",
        board = "darcy",
        androidRelease = "11",
        androidCodename = "REL",
        androidSdk = 30,
        targetSdk = 36,
        securityPatch = "2025-04-05",
        supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
        is64BitRuntime = true,
        processorCount = 8,
        totalMemoryMiB = 3_072,
        lowRamDevice = false,
        displayWidthPixels = 3840,
        displayHeightPixels = 2160,
        densityDpi = 320,
        smallestScreenWidthDp = 960,
        formFactor = "tv",
        emulator = false,
    )
}
