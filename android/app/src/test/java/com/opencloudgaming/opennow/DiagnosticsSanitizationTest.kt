package com.opencloudgaming.opennow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsSanitizationTest {
    @Test
    fun exportKeepsNonUniqueDeviceAndAndroidSupportContext() {
        val raw = """
            device.identity manufacturer=NVIDIA brand=NVIDIA model=SHIELD_Android_TV codename=mdarcy product=mdarcy formFactor=tv emulator=false
            android.os release=11 codename=REL sdk=30 targetSdk=36 securityPatch=2025-04-05
            device.hardware hardware=darcy board=darcy abis=arm64-v8a|armeabi-v7a runtimeBits=64 processors=8 memoryMiB=3072 lowRam=false
            device.display pixels=3840x2160 densityDpi=320 smallestWidthDp=960
        """.trimIndent()

        val sanitized = sanitizeDiagnosticExport(raw)

        assertTrue(sanitized.contains("model=SHIELD_Android_TV"))
        assertTrue(sanitized.contains("sdk=30"))
        assertTrue(sanitized.contains("securityPatch=2025-04-05"))
        assertTrue(sanitized.contains("abis=arm64-v8a|armeabi-v7a"))
        assertTrue(sanitized.contains("pixels=3840x2160"))
    }

    @Test
    fun exportRemovesNamesTokensIdsAndNetworkAddresses() {
        val raw = """
            user=Jane Example tier=FREE provider=NVIDIA
            sessionId=01234567-89ab-4cde-8fab-0123456789ab sessionStatus=READY serverIp=192.168.10.42
            Authorization: Bearer secret.jwt.value
            {"displayName":"Jane Example","email":"jane@example.com","deviceId":"device-secret"}
            ipv6=2001:db8::1234
        """.trimIndent()

        val sanitized = sanitizeDiagnosticExport(raw)

        listOf(
            "Jane Example",
            "jane@example.com",
            "secret.jwt.value",
            "01234567-89ab-4cde-8fab-0123456789ab",
            "192.168.10.42",
            "2001:db8::1234",
            "device-secret",
        ).forEach { sensitive -> assertFalse("Leaked $sensitive in $sanitized", sanitized.contains(sensitive)) }
        assertTrue(sanitized.contains("tier=FREE"))
        assertTrue(sanitized.contains("provider=NVIDIA"))
        assertTrue(sanitized.contains("[redacted]"))
    }
}
