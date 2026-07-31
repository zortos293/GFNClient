package com.opencloudgaming.opennow

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BugReportsTest {
    @Test
    fun buildsPrintedWasteMultipartReportWithRedactedLogAttachment() {
        val request = buildAndroidBugReportRequest(
            AndroidBugReport(
                title = " Stream froze ",
                description = " Video stopped after reconnecting and remained frozen until I restarted the session. ",
                versionName = "0.9.0",
                versionCode = "45",
                metadata = """{"device":"Pixel 9","sessionId":"[redacted]"}""",
                files = listOf(
                    AndroidBugReportAttachment(
                        fileName = "opennow.log",
                        contentType = "text/plain; charset=utf-8",
                        bytes = "sessionId=[redacted]".toByteArray(),
                    ),
                ),
            ),
        )

        val buffer = Buffer()
        requireNotNull(request.body).writeTo(buffer)
        val multipart = buffer.readUtf8()

        assertEquals(ANDROID_BUG_REPORT_ENDPOINT, request.url.toString())
        assertEquals("POST", request.method)
        assertTrue(multipart.contains("name=\"title\"\r\n\r\nStream froze"))
        assertTrue(
            multipart.contains(
                "name=\"description\"\r\n\r\nVideo stopped after reconnecting and remained frozen until I restarted the session.",
            ),
        )
        assertTrue(multipart.contains("name=\"versionName\"\r\n\r\n0.9.0"))
        assertTrue(multipart.contains("name=\"versionCode\"\r\n\r\n45"))
        assertTrue(multipart.contains("name=\"platform\"\r\n\r\nandroid"))
        assertTrue(multipart.contains("name=\"files\"; filename=\"opennow.log\""))
        assertTrue(multipart.contains("sessionId=[redacted]"))
    }

    @Test
    fun metadataOnlyIdentifiesTheAdvancedDebugLogExport() {
        val fileName = "opennow-android-logs-20260718-123456.txt"
        val metadata = buildAndroidBugReportMetadata(fileName)

        assertTrue(metadata.contains("\"source\":\"settings-advanced-debug-logs\""))
        assertTrue(metadata.contains("\"attachment\":\"$fileName\""))
        assertFalse(metadata.contains("device"))
        assertFalse(metadata.contains("sessionId"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMoreThanFiveFiles() {
        val files = (1..6).map { index ->
            AndroidBugReportAttachment("$index.log", "text/plain", byteArrayOf())
        }
        buildAndroidBugReportRequest(
            AndroidBugReport(
                "Title",
                "The stream stopped decoding video after a reconnect and did not recover.",
                "0.9.0",
                "45",
                "{}",
                files,
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsFilesLargerThanTenMib() {
        buildAndroidBugReportRequest(
            AndroidBugReport(
                "Title",
                "The stream stopped decoding video after a reconnect and did not recover.",
                "0.9.0",
                "45",
                "{}",
                listOf(
                    AndroidBugReportAttachment(
                        "too-large.log",
                        "text/plain",
                        ByteArray(ANDROID_BUG_REPORT_MAX_FILE_BYTES.toInt() + 1),
                    ),
                ),
            ),
        )
    }

    @Test
    fun parsesServerReferenceWhenPresent() {
        assertEquals("report-123", parseAndroidBugReportReference("""{"id":"report-123"}"""))
        assertEquals(null, parseAndroidBugReportReference("""{"ok":true}"""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDescriptionsShorterThanFiftyCharacters() {
        buildAndroidBugReportRequest(
            AndroidBugReport(
                title = "Lag",
                description = "It lagged.",
                versionName = "1.0.5",
                versionCode = "60",
                metadata = "{}",
                files = emptyList(),
            ),
        )
    }

    @Test
    fun playStoreReportsRequireAFreshCurrentVersionCheck() {
        val playUpdate = AndroidUpdateState(
            installSource = AndroidAppInstallSource(setOf(GOOGLE_PLAY_STORE_PACKAGE)),
            status = AndroidUpdateStatus.NotAvailable,
        )

        assertFalse(
            androidBugReportsAllowed(
                playUpdate,
                AndroidBugReportVersionCheckState(AndroidBugReportVersionCheckStatus.NotChecked),
            ),
        )
        assertTrue(
            androidBugReportsAllowed(
                playUpdate,
                AndroidBugReportVersionCheckState(AndroidBugReportVersionCheckStatus.Current),
            ),
        )
        assertFalse(
            androidBugReportsAllowed(
                playUpdate.copy(status = AndroidUpdateStatus.Available),
                AndroidBugReportVersionCheckState(AndroidBugReportVersionCheckStatus.Current),
            ),
        )
    }

    @Test
    fun sideloadReportsDoNotDependOnGooglePlayVerification() {
        val sideloadUpdate = AndroidUpdateState(
            installSource = AndroidAppInstallSource(emptySet()),
            status = AndroidUpdateStatus.Idle,
        )

        assertTrue(
            androidBugReportsAllowed(
                sideloadUpdate,
                AndroidBugReportVersionCheckState(AndroidBugReportVersionCheckStatus.NotChecked),
            ),
        )
    }
}
