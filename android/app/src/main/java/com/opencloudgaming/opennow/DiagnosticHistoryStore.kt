package com.opencloudgaming.opennow

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

internal data class PreviousDiagnosticSnapshot(
    val capturedAtEpochMs: Long,
    val text: String,
)

/**
 * Keeps the latest bounded diagnostic export from the current process so the next app run can
 * attach it. Snapshots are compressed because HTTP response diagnostics are intentionally rich,
 * and writing an uncompressed copy every few seconds would create unnecessary storage traffic on
 * lower-end Android TV hardware.
 */
internal class DiagnosticHistoryStore(
    directory: File,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    private val historyDirectory = File(directory, DIRECTORY_NAME)
    private val currentFile = File(historyDirectory, CURRENT_FILE_NAME)
    private val previousFile = File(historyDirectory, PREVIOUS_FILE_NAME)

    /**
     * Promotes the last process snapshot exactly once during Application startup. If the previous
     * process died before creating a usable snapshot, the older previous snapshot is preserved.
     */
    @Synchronized
    fun beginAppRun() {
        historyDirectory.mkdirs()
        recoverInterruptedReplacement(currentFile)
        recoverInterruptedReplacement(previousFile)
        val current = readSnapshot(currentFile)
        if (current == null) {
            currentFile.delete()
            return
        }

        val stagedPrevious = File(historyDirectory, "$PREVIOUS_FILE_NAME.stage")
        stagedPrevious.delete()
        currentFile.copyTo(stagedPrevious, overwrite = true)
        replaceFile(stagedPrevious, previousFile)
        currentFile.delete()
    }

    @Synchronized
    fun saveCurrent(text: String) {
        historyDirectory.mkdirs()
        recoverInterruptedReplacement(currentFile)
        val stagedCurrent = File(historyDirectory, "$CURRENT_FILE_NAME.stage")
        stagedCurrent.delete()
        val bounded = boundDiagnosticSnapshot(text)
        GZIPOutputStream(stagedCurrent.outputStream().buffered()).use { compressed ->
            OutputStreamWriter(compressed, Charsets.UTF_8).use { writer ->
                writer.append(nowEpochMs().toString())
                writer.append('\n')
                writer.append(bounded)
            }
        }
        replaceFile(stagedCurrent, currentFile)
    }

    @Synchronized
    fun previousSnapshot(): PreviousDiagnosticSnapshot? {
        recoverInterruptedReplacement(previousFile)
        return readSnapshot(previousFile)
    }

    private fun readSnapshot(file: File): PreviousDiagnosticSnapshot? {
        if (!file.isFile || file.length() <= 0L) return null
        return runCatching {
            GZIPInputStream(file.inputStream().buffered()).use { compressed ->
                BufferedReader(InputStreamReader(compressed, Charsets.UTF_8)).use { reader ->
                    val capturedAt = reader.readLine()?.toLongOrNull() ?: return null
                    val text = reader.readText().trimEnd()
                    if (text.isBlank()) return null
                    PreviousDiagnosticSnapshot(capturedAtEpochMs = capturedAt, text = text)
                }
            }
        }.getOrNull()
    }

    private fun recoverInterruptedReplacement(target: File) {
        val backup = File(historyDirectory, "${target.name}.backup")
        if (!target.exists() && backup.isFile) {
            backup.renameTo(target)
        } else if (target.exists()) {
            backup.delete()
        }
    }

    private fun replaceFile(staged: File, target: File) {
        val backup = File(historyDirectory, "${target.name}.backup")
        backup.delete()
        val hadTarget = target.isFile
        if (hadTarget && !target.renameTo(backup)) {
            staged.delete()
            error("Could not stage existing diagnostic history")
        }
        if (!staged.renameTo(target)) {
            if (hadTarget) backup.renameTo(target)
            staged.delete()
            error("Could not save diagnostic history")
        }
        backup.delete()
    }

    private companion object {
        const val DIRECTORY_NAME = "diagnostic-history"
        const val CURRENT_FILE_NAME = "current.txt.gz"
        const val PREVIOUS_FILE_NAME = "previous.txt.gz"
    }
}

internal fun boundDiagnosticSnapshot(
    text: String,
    maxCharacters: Int = 1_500_000,
): String {
    require(maxCharacters >= 256)
    if (text.length <= maxCharacters) return text
    val marker = "\n... persisted diagnostic snapshot truncated ${text.length - maxCharacters} characters ...\n"
    val available = (maxCharacters - marker.length).coerceAtLeast(2)
    val headLength = available / 2
    val tailLength = available - headLength
    return text.take(headLength) + marker + text.takeLast(tailLength)
}

internal fun appendPreviousDiagnosticSnapshot(
    current: String,
    previous: PreviousDiagnosticSnapshot?,
): String {
    if (previous == null) return current
    return buildString(current.length + previous.text.length + 160) {
        append(current.trimEnd())
        appendLine()
        appendLine()
        appendLine("previousAppRun.diagnostics:")
        appendLine("previousAppRun.capturedAtEpochMs=${previous.capturedAtEpochMs}")
        appendLine("----- BEGIN PREVIOUS APP RUN -----")
        appendLine(previous.text.trimEnd())
        append("----- END PREVIOUS APP RUN -----")
    }
}
