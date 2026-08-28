package com.opencloudgaming.opennow

import android.os.SystemClock
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val OPENNOW_DEBUG_LOG_TAG = "OpenNOWDebug"

private const val DIAGNOSTIC_PAYLOAD_BODY_LIMIT = 20_000
private const val HTTP_DIAGNOSTIC_LIMIT = 80
private const val HTTP_DIAGNOSTIC_BODY_LIMIT = 4_000
private const val HTTP_DIAGNOSTIC_MAX_REQUEST_CAPTURE_BYTES = 48_000L

private val DIAGNOSTIC_SENSITIVE_TEXT_PATTERN = Regex(
    """(?i)\b(authorization|access[_-]?token|id[_-]?token|refresh[_-]?token|client[_-]?token|device[_-]?code|user[_-]?code|verification[_-]?uri[_-]?complete|credential|password|secret|cookie|code|sub)(\s*[=:]\s*)([^\s,;&]+)""",
)
private val DIAGNOSTIC_BEARER_PATTERN = Regex("""(?i)\bBearer\s+[A-Za-z0-9._~+/=-]+""")
private val DIAGNOSTIC_JSON_IDENTITY_PATTERN = Regex(
    """(?i)([\"']?(?:email|user(?:[_-]?id|[_-]?name)?|display[_-]?name|account[_-]?id|profile[_-]?id|session[_-]?id|server[_-]?ip|device[_-]?id|device[_-]?name|ip[_-]?address)[\"']?\s*:\s*)(\"(?:\\.|[^\"])*\"|'(?:\\.|[^'])*'|[^,}\r\n]+)""",
)
private val DIAGNOSTIC_LINE_IDENTITY_PATTERN = Regex(
    """(?im)\b(email|user|user[_-]?id|user[_-]?name|display[_-]?name|account|account[_-]?id|profile[_-]?id|session|session[_-]?id|server|server[_-]?ip|device|device[_-]?id|device[_-]?name|ip[_-]?address)(\s*[=:]\s*)(.*?)(?=\s+[a-z][a-z0-9_.-]*\s*[=:]|$)""",
)
private val DIAGNOSTIC_EMAIL_PATTERN = Regex(
    """\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b""",
    RegexOption.IGNORE_CASE,
)
private val DIAGNOSTIC_IPV4_PATTERN = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
private val DIAGNOSTIC_IPV6_FULL_PATTERN = Regex(
    """(?i)(?<![A-F0-9:])(?:[A-F0-9]{1,4}:){2,7}[A-F0-9]{1,4}(?![A-F0-9:])""",
)
private val DIAGNOSTIC_IPV6_COMPRESSED_PATTERN = Regex(
    """(?i)(?<![A-F0-9:])(?:(?:[A-F0-9]{1,4}:){1,7}:(?:[A-F0-9]{1,4}(?::[A-F0-9]{1,4}){0,6})?|::(?:[A-F0-9]{1,4}(?::[A-F0-9]{1,4}){0,6})?)(?![A-F0-9:])""",
)
private val DIAGNOSTIC_UUID_PATTERN = Regex(
    """\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}\b""",
)

private val DebugPayloadJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
    encodeDefaults = true
}

internal object OpenNowHttpDiagnostics {
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun record(
        request: Request,
        requestBody: String,
        statusCode: Int?,
        responseBody: String,
        elapsedMs: Long,
        error: Throwable? = null,
    ) {
        val status = statusCode?.toString() ?: "ERR:${error?.javaClass?.simpleName ?: "unknown"}"
        val requestBytes = request.body?.safeContentLength()?.takeIf { it >= 0 }?.toString() ?: "none"
        val responseBytes = if (responseBody.isBlank() && error != null) "none" else responseBody.length.toString()
        val requestPreview = requestBody.takeIf { it.isNotBlank() }?.let(::singleLineDiagnosticPreview)
        val responsePreview = responseBody.takeIf { it.isNotBlank() }?.let(::singleLineDiagnosticPreview)
        val errorMessage = error?.let { "${it.javaClass.simpleName}: ${it.message.orEmpty()}".take(320) }
        val line = buildString {
            append(SystemClock.elapsedRealtime())
            append(' ')
            append(request.method)
            append(' ')
            append(redactDiagnosticUrl(request.url.toString()))
            append(" -> http=")
            append(status)
            append(" elapsedMs=")
            append(elapsedMs)
            append(" reqBytes=")
            append(requestBytes)
            append(" respBytes=")
            append(responseBytes)
            if (!requestPreview.isNullOrBlank()) {
                append(" request=")
                append(requestPreview)
            }
            if (!responsePreview.isNullOrBlank()) {
                append(" response=")
                append(responsePreview)
            }
            if (!errorMessage.isNullOrBlank()) {
                append(" error=")
                append(errorMessage)
            }
        }
        lines.addLast(line)
        while (lines.size > HTTP_DIAGNOSTIC_LIMIT) {
            lines.removeFirst()
        }
        Log.d(OPENNOW_DEBUG_LOG_TAG, "http: $line")
    }

    fun captureRequestBody(request: Request): String {
        val body = request.body ?: return ""
        val contentLength = body.safeContentLength()
        if (contentLength > HTTP_DIAGNOSTIC_MAX_REQUEST_CAPTURE_BYTES) {
            return "(request body omitted ${contentLength}B)"
        }
        if (body.isDuplex()) return "(duplex request body omitted)"
        if (body.isOneShot()) return "(one-shot request body omitted)"
        return runCatching {
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8()
        }.getOrElse { error ->
            "(request body unavailable ${error.javaClass.simpleName})"
        }
    }

    @Synchronized
    fun snapshot(): String =
        if (lines.isEmpty()) {
            "network.diagnostics=empty"
        } else {
            buildString {
                appendLine("network.diagnostics:")
                lines.forEachIndexed { index, line ->
                    appendLine("network.${index + 1} $line")
                }
            }.trimEnd()
        }

}

internal fun sanitizeDiagnosticLogPayload(
    raw: String,
    limit: Int = DIAGNOSTIC_PAYLOAD_BODY_LIMIT,
): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return "(empty)"
    val formatted = runCatching {
        val sanitized = redactDiagnosticJsonElement(OpenNowJson.parseToJsonElement(trimmed))
        DebugPayloadJson.encodeToString(JsonElement.serializer(), sanitized)
    }.getOrElse {
        redactDiagnosticText(trimmed)
    }
    val redacted = redactDiagnosticText(formatted)
    return if (redacted.length <= limit) {
        redacted
    } else {
        redacted.take(limit) + "\n... truncated ${redacted.length - limit} chars ..."
    }
}

internal fun redactDiagnosticUrl(raw: String): String {
    val parsed = raw.toHttpUrlOrNull() ?: return redactDiagnosticText(raw)
    val redactedNames = (0 until parsed.querySize)
        .map { parsed.queryParameterName(it) }
        .filter(::shouldRedactDiagnosticKey)
        .distinct()
    if (redactedNames.isEmpty()) return raw
    val builder = parsed.newBuilder()
    redactedNames.forEach { name -> builder.setQueryParameter(name, "[redacted]") }
    return builder.build().toString()
}

private fun redactDiagnosticJsonElement(element: JsonElement, keyHint: String? = null): JsonElement =
    when {
        keyHint != null && shouldRedactDiagnosticKey(keyHint) -> JsonPrimitive("[redacted]")
        element is JsonObject -> JsonObject(element.mapValues { (key, value) -> redactDiagnosticJsonElement(value, key) })
        element is JsonArray -> JsonArray(element.map { redactDiagnosticJsonElement(it) })
        else -> element
    }

private fun shouldRedactDiagnosticKey(key: String): Boolean {
    val normalized = key.lowercase(Locale.US).filter(Char::isLetterOrDigit)
    return normalized.contains("authorization") ||
        normalized.contains("token") ||
        normalized.contains("credential") ||
        normalized.contains("password") ||
        normalized.contains("secret") ||
        normalized.contains("cookie") ||
        normalized == "code" ||
        normalized == "devicecode" ||
        normalized == "usercode" ||
        normalized == "verificationuricomplete" ||
        normalized == "deviceid" ||
        normalized == "devicehashid" ||
        normalized == "sub" ||
        normalized == "email" ||
        normalized == "userid"
}

private fun redactDiagnosticText(text: String): String {
    return DIAGNOSTIC_SENSITIVE_TEXT_PATTERN.replace(text) { match ->
        "${match.groupValues[1]}${match.groupValues[2]}[redacted]"
    }
}

internal fun sanitizeDiagnosticExport(raw: String): String {
    var sanitized = DIAGNOSTIC_BEARER_PATTERN.replace(raw, "Bearer [redacted]")
    sanitized = redactDiagnosticText(sanitized)
    sanitized = DIAGNOSTIC_JSON_IDENTITY_PATTERN.replace(sanitized) { match ->
        "${match.groupValues[1]}\"[redacted]\""
    }
    sanitized = DIAGNOSTIC_LINE_IDENTITY_PATTERN.replace(sanitized) { match ->
        "${match.groupValues[1]}${match.groupValues[2]}[redacted]"
    }
    sanitized = DIAGNOSTIC_EMAIL_PATTERN.replace(sanitized, "[redacted-email]")
    sanitized = DIAGNOSTIC_IPV4_PATTERN.replace(sanitized, "[redacted-ip]")
    sanitized = DIAGNOSTIC_IPV6_FULL_PATTERN.replace(sanitized, "[redacted-ip]")
    sanitized = DIAGNOSTIC_IPV6_COMPRESSED_PATTERN.replace(sanitized, "[redacted-ip]")
    sanitized = DIAGNOSTIC_UUID_PATTERN.replace(sanitized, "[redacted-id]")
    return sanitized
}

private const val ANDROID_DIAGNOSTIC_PASTE_URL =
    "https://paste.rtech.support/upload/opennow-android-diagnostics.txt"
private const val ANDROID_DIAGNOSTIC_PASTE_EXPIRY_SECONDS = 86_400

internal suspend fun uploadAndroidDiagnosticPaste(
    http: OkHttpClient,
    sanitizedText: String,
): String = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url(ANDROID_DIAGNOSTIC_PASTE_URL)
        .header("Accept", "application/json")
        .header("Linx-Randomize", "yes")
        .header("Linx-Expiry", ANDROID_DIAGNOSTIC_PASTE_EXPIRY_SECONDS.toString())
        .put(sanitizedText.toRequestBody("text/plain; charset=utf-8".toMediaType()))
        .build()
    http.newCall(request).execute().use { response ->
        val body = response.body.string().trim()
        if (!response.isSuccessful) {
            error("Diagnostics upload failed (HTTP ${response.code})")
        }
        val jsonUrl = runCatching {
            OpenNowJson.parseToJsonElement(body).jsonObject["url"]?.jsonPrimitive?.content
        }.getOrNull()
        (jsonUrl ?: body.lineSequence().firstOrNull { it.startsWith("https://") })
            ?.trim()
            ?.takeIf { it.startsWith("https://paste.rtech.support/") }
            ?: error("Diagnostics upload returned no paste URL")
    }
}

private fun singleLineDiagnosticPreview(raw: String): String {
    // Parsing and pretty-printing multi-hundred-kilobyte catalog responses used to run
    // before the preview was truncated. Keep diagnostics bounded before any JSON work.
    val bounded = if (raw.length > HTTP_DIAGNOSTIC_BODY_LIMIT * 2) {
        raw.take(HTTP_DIAGNOSTIC_BODY_LIMIT * 2) +
            "\n... omitted ${raw.length - (HTTP_DIAGNOSTIC_BODY_LIMIT * 2)} chars before formatting ..."
    } else {
        raw
    }
    return redactDiagnosticText(sanitizeDiagnosticLogPayload(bounded, HTTP_DIAGNOSTIC_BODY_LIMIT))
        .lineSequence()
        .joinToString(" ") { it.trim() }
        .take(HTTP_DIAGNOSTIC_BODY_LIMIT)
}

private fun okhttp3.RequestBody.safeContentLength(): Long =
    runCatching { contentLength() }.getOrDefault(-1L)
