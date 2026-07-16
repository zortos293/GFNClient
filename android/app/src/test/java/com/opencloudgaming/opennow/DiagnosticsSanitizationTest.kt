package com.opencloudgaming.opennow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsSanitizationTest {
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
