package com.opencloudgaming.opennow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal const val ANDROID_BUG_REPORT_ENDPOINT =
    "https://api.printedwaste.com/releases/opennow/bug-reports"
internal const val ANDROID_BUG_REPORT_MAX_FILES = 5
internal const val ANDROID_BUG_REPORT_MAX_FILE_BYTES = 10L * 1024L * 1024L

internal data class AndroidBugReportAttachment(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
)

internal data class AndroidBugReport(
    val title: String,
    val description: String,
    val versionName: String,
    val versionCode: String,
    val metadata: String,
    val files: List<AndroidBugReportAttachment>,
)

internal data class AndroidBugReportReceipt(
    val reference: String?,
)

internal fun buildAndroidBugReportMetadata(
    logFileName: String,
): String = buildJsonObject {
    put("source", "settings-advanced-debug-logs")
    put("attachment", logFileName)
}.toString()

internal fun buildAndroidBugReportRequest(
    report: AndroidBugReport,
    endpoint: String = ANDROID_BUG_REPORT_ENDPOINT,
): Request {
    val title = report.title.trim()
    val description = report.description.trim()
    require(title.isNotEmpty()) { "Enter a short issue title" }
    require(description.isNotEmpty()) { "Describe what happened" }
    require(report.versionName.isNotBlank()) { "App version is unavailable" }
    require(report.versionCode.isNotBlank()) { "App build is unavailable" }
    require(report.files.size <= ANDROID_BUG_REPORT_MAX_FILES) {
        "Bug reports support up to $ANDROID_BUG_REPORT_MAX_FILES files"
    }
    runCatching { OpenNowJson.parseToJsonElement(report.metadata).jsonObject }
        .getOrElse { throw IllegalArgumentException("Bug report metadata must be a JSON object", it) }

    val multipart = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("title", title)
        .addFormDataPart("description", description)
        .addFormDataPart("versionName", report.versionName)
        .addFormDataPart("versionCode", report.versionCode)
        .addFormDataPart("platform", "android")
        .addFormDataPart("metadata", report.metadata)

    report.files.forEach { attachment ->
        require(attachment.fileName.isNotBlank()) { "Bug report files must have a name" }
        require(attachment.bytes.size.toLong() <= ANDROID_BUG_REPORT_MAX_FILE_BYTES) {
            "${attachment.fileName} is larger than 10 MiB"
        }
        val mediaType = attachment.contentType.toMediaType()
        multipart.addFormDataPart(
            "files",
            attachment.fileName,
            attachment.bytes.toRequestBody(mediaType),
        )
    }

    return Request.Builder()
        .url(endpoint)
        .header("Accept", "application/json")
        .post(multipart.build())
        .build()
}

internal suspend fun uploadAndroidBugReport(
    http: OkHttpClient,
    report: AndroidBugReport,
): AndroidBugReportReceipt = withContext(Dispatchers.IO) {
    http.newCall(buildAndroidBugReportRequest(report)).execute().use { response ->
        val body = response.body.string().take(MAX_BUG_REPORT_RESPONSE_CHARS)
        if (!response.isSuccessful) {
            val detail = body
                .lineSequence()
                .joinToString(" ") { it.trim() }
                .take(320)
                .takeIf(String::isNotBlank)
            error(
                buildString {
                    append("Bug report upload failed (HTTP ${response.code})")
                    detail?.let { append(": $it") }
                },
            )
        }
        AndroidBugReportReceipt(reference = parseAndroidBugReportReference(body))
    }
}

internal fun parseAndroidBugReportReference(body: String): String? = runCatching {
    val json = OpenNowJson.parseToJsonElement(body).jsonObject
    listOf("id", "reportId", "bugReportId")
        .firstNotNullOfOrNull { key -> json[key]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) }
}.getOrNull()

private const val MAX_BUG_REPORT_RESPONSE_CHARS = 64 * 1024
