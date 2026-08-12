package com.opencloudgaming.opennow

import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal const val ANDROID_BUG_REPORT_MIN_MEANINGFUL_CHARS = 50
internal const val ANDROID_BUG_REPORT_MIN_WORDS = 8
internal const val ANDROID_BUG_REPORT_MIN_UNIQUE_WORDS = 6
internal const val ANDROID_BUG_REPORT_MIN_ENGLISH_CONFIDENCE = 0.50f

internal data class AndroidBugReportLanguageCandidate(
    val languageTag: String,
    val confidence: Float,
)

internal data class AndroidBugReportLanguageCheck(
    val languageTag: String,
    val confidence: Float,
)

internal fun androidBugReportMeaningfulCharacterCount(description: String): Int =
    description.count(Char::isLetterOrDigit)

internal fun androidBugReportDescriptionError(description: String): String? {
    val meaningfulCharacters = androidBugReportMeaningfulCharacterCount(description)
    if (meaningfulCharacters < ANDROID_BUG_REPORT_MIN_MEANINGFUL_CHARS) {
        return "Describe what happened using at least $ANDROID_BUG_REPORT_MIN_MEANINGFUL_CHARS letters or numbers"
    }
    val words = BUG_REPORT_WORD.findAll(description)
        .map { match -> match.value.lowercase(Locale.ROOT) }
        .toList()
    if (words.size < ANDROID_BUG_REPORT_MIN_WORDS || words.toSet().size < ANDROID_BUG_REPORT_MIN_UNIQUE_WORDS) {
        return "Use complete English sentences that explain the steps, result, and expected behavior"
    }
    if (description.contains(BUG_REPORT_REPEATED_CHARACTER) || words.areRepeatedPadding()) {
        return "Remove repeated or random text and describe the real problem in English"
    }
    return null
}

internal fun androidBugReportTitleError(title: String): String? {
    if (title.isBlank()) return "Enter a short issue title"
    if (title.any { it.isLetter() && it.code > ASCII_END }) {
        return "Write the issue title in English"
    }
    if (title.contains(BUG_REPORT_REPEATED_CHARACTER)) {
        return "Remove repeated or random text from the issue title"
    }
    return null
}

internal fun androidBugReportLanguageError(
    candidates: List<AndroidBugReportLanguageCandidate>,
): String? {
    val strongest = candidates.maxByOrNull(AndroidBugReportLanguageCandidate::confidence)
    val language = strongest?.languageTag
        ?.substringBefore('-')
        ?.substringBefore('_')
        ?.lowercase(Locale.ROOT)
    return if (
        language == "en" &&
        strongest.confidence >= ANDROID_BUG_REPORT_MIN_ENGLISH_CONFIDENCE
    ) {
        null
    } else {
        "Write the title and description in clear English; non-English or unrecognizable text cannot be sent"
    }
}

internal suspend fun identifyAndroidBugReportLanguage(
    title: String,
    description: String,
): AndroidBugReportLanguageCheck {
    val identifier = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder()
            .setConfidenceThreshold(MIN_LANGUAGE_CANDIDATE_CONFIDENCE)
            .build(),
    )
    return try {
        val candidates = identifier.identifyPossibleLanguages("${title.trim()}\n${description.trim()}")
            .awaitResult()
            .map { candidate ->
                AndroidBugReportLanguageCandidate(
                    languageTag = candidate.languageTag,
                    confidence = candidate.confidence,
                )
            }
        androidBugReportLanguageError(candidates)?.let { message ->
            throw IllegalArgumentException(message)
        }
        val strongest = candidates.maxByOrNull(AndroidBugReportLanguageCandidate::confidence)
            ?: throw IllegalArgumentException("OpenNOW could not verify that this report is written in English")
        AndroidBugReportLanguageCheck(
            languageTag = strongest.languageTag,
            confidence = strongest.confidence,
        )
    } finally {
        identifier.close()
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        if (continuation.isActive) continuation.resume(result)
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWithException(error)
    }
    addOnCanceledListener {
        continuation.cancel()
    }
}

private fun List<String>.areRepeatedPadding(): Boolean {
    if (isEmpty()) return false
    val mostFrequentWord = groupingBy { word -> word }.eachCount().maxOf(Map.Entry<String, Int>::value)
    if (mostFrequentWord > size / 2) return true
    for (period in 1..size / 2) {
        if (size % period == 0 && indices.all { index -> this[index] == this[index % period] }) {
            return true
        }
    }
    return false
}

private const val ASCII_END = 0x7f
private const val MIN_LANGUAGE_CANDIDATE_CONFIDENCE = 0.01f
private val BUG_REPORT_WORD = Regex("[\\p{L}\\p{N}]+(?:['’-][\\p{L}\\p{N}]+)*")
private val BUG_REPORT_REPEATED_CHARACTER = Regex("([^\\s])\\1{5,}", RegexOption.IGNORE_CASE)
