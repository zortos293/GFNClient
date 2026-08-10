package com.opencloudgaming.opennow

import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import java.util.Locale
import com.opencloudgaming.opennow.ui.theme.OpenNowPalette
import com.opencloudgaming.opennow.ui.theme.OpenNowRadius
import com.opencloudgaming.opennow.ui.theme.OpenNowSpacing
import com.opencloudgaming.opennow.ui.theme.numeric
import com.opencloudgaming.opennow.ui.theme.tint

@Composable
internal fun SessionReportDialog(
    report: SessionReport,
    onDismiss: (dontShowAgain: Boolean) -> Unit,
    onReportBug: (dontShowAgain: Boolean) -> Unit,
) {
    // Four tones for a 0-100 score was more colour than information, and AccentLime vs
    // AccentDefault is indistinguishable at the 0.12 alpha this fills with.
    val scoreColor = when (report.rating) {
        SessionReportRating.Excellent, SessionReportRating.Good -> OpenNowPalette.StatusGood
        SessionReportRating.Fair -> OpenNowPalette.StatusFair
        SessionReportRating.Poor -> OpenNowPalette.StatusPoor
    }
    val configuration = LocalConfiguration.current
    val landscapeLayout = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var dontShowAgain by rememberSaveable(report.gameTitle, report.durationSeconds) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onDismiss(dontShowAgain) },
        modifier = if (landscapeLayout) {
            Modifier.widthIn(max = 960.dp).fillMaxWidth(0.94f)
        } else {
            Modifier
        },
        properties = DialogProperties(usePlatformDefaultWidth = !landscapeLayout),
        title = { Text("Session report") },
        text = {
            if (landscapeLayout) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = (configuration.screenHeightDp * 0.66f).dp),
                    horizontalArrangement = Arrangement.spacedBy(OpenNowSpacing.lg),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        SessionReportSummary(report, scoreColor)
                        SessionReportConnection(report)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        SessionReportOutcome(report) { onReportBug(dontShowAgain) }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 510.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SessionReportSummary(report, scoreColor)
                    SessionReportConnection(report)
                    SessionReportOutcome(report) { onReportBug(dontShowAgain) }
                }
            }
        },
        dismissButton = {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(OpenNowRadius.sm))
                    .clickable { dontShowAgain = !dontShowAgain },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = dontShowAgain,
                    onCheckedChange = { dontShowAgain = it },
                )
                Text(
                    stringResource(R.string.session_report_dont_show_again),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { Button(onClick = { onDismiss(dontShowAgain) }) { Text("Done") } },
    )
}

@Composable
private fun SessionReportSummary(report: SessionReport, scoreColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            color = scoreColor.copy(alpha = 0.12f),
            shape = RoundedCornerShape(OpenNowRadius.lg + 2.dp),
            border = BorderStroke(1.dp, scoreColor.copy(alpha = 0.38f)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(OpenNowSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(report.gameTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(
                            R.string.session_report_subtitle,
                            formatSessionTimerDuration(report.durationSeconds),
                        ),
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        stringResource(R.string.session_report_score, report.score),
                        color = scoreColor,
                        style = MaterialTheme.typography.headlineMedium.numeric(),
                    )
                    Text(report.rating.label, color = scoreColor, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (report.limitedData) {
            Text(
                "This was a short session, so the score may vary more than usual.",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SessionReportConnection(report: SessionReport) {
    Text(stringResource(R.string.session_report_connection), style = MaterialTheme.typography.titleSmall)
    SessionReportMetricGrid(
        listOf(
            SessionReportMetricData(
                label = stringResource(R.string.session_report_metric_latency),
                value = report.averagePingMs?.let { stringResource(R.string.session_report_ms_avg, it) },
                detail = report.peakPingMs?.let { stringResource(R.string.session_report_ms_peak, it) },
                quality = report.averagePingMs?.let(StreamQuality::latency),
            ),
            SessionReportMetricData(
                label = stringResource(R.string.session_report_metric_speed),
                value = formatRuntimeBitrate(report.averageBitrateKbps),
                detail = report.peakBitrateKbps?.let {
                    stringResource(R.string.session_report_peak, formatRuntimeBitrate(it))
                },
            ),
            SessionReportMetricData(
                label = stringResource(R.string.session_report_metric_loss),
                value = report.packetLossPct?.let { "%.2f%%".format(Locale.US, it) },
                detail = report.packetLossPct?.let {
                    stringResource(
                        if (it <= 0.5) R.string.session_report_loss_stable
                        else R.string.session_report_loss_affects,
                    )
                },
                quality = report.packetLossPct?.let(StreamQuality::packetLoss),
            ),
            SessionReportMetricData(
                label = stringResource(R.string.session_report_metric_jitter),
                value = report.averageJitterMs?.let { "%.1f ms".format(Locale.US, it) },
                detail = stringResource(R.string.session_report_jitter_detail),
                quality = report.averageJitterMs?.let(StreamQuality::jitter),
            ),
            SessionReportMetricData(
                label = stringResource(R.string.session_report_metric_fps),
                value = report.averageFps?.let { "%.1f / %d".format(Locale.US, it, report.targetFps) },
                detail = stringResource(R.string.session_report_fps_detail),
                quality = report.averageFps?.let { StreamQuality.frameRate(it, report.targetFps) },
            ),
            SessionReportMetricData(
                label = stringResource(R.string.session_report_metric_decode),
                value = report.averageDecodeMs?.let { "%.1f ms".format(Locale.US, it) },
                detail = stringResource(R.string.session_report_decode_detail),
                quality = report.averageDecodeMs?.let { StreamQuality.decode(it, report.targetFps) },
            ),
        ),
    )
    val networkLabel = when (report.networkKind) {
        AndroidNetworkKind.Wifi -> report.wifiBand.label
        else -> report.networkKind.label
    }
    Text(
        buildString {
            append("Network: $networkLabel")
            report.estimatedLinkDownstreamKbps?.let {
                append(" • Android link estimate ${formatRuntimeBitrate(it)}")
            }
        },
        color = TextMuted,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun SessionReportOutcome(report: SessionReport, onReportBug: () -> Unit) {
    Text("Delivered profile", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Text(
        buildString {
            append(formatRuntimeResolution(report.deliveredResolution ?: report.requestedResolution))
            append(" • ")
            append(report.deliveredCodec ?: report.requestedCodec.name)
            if (
                normalizeSessionReportResolution(report.deliveredResolution) !=
                normalizeSessionReportResolution(report.requestedResolution) ||
                report.deliveredCodec?.contains(report.requestedCodec.name, ignoreCase = true) == false
            ) {
                append(" (requested ${formatRuntimeResolution(report.requestedResolution)} • ${report.requestedCodec.name})")
            }
        },
        color = TextPrimary,
        style = MaterialTheme.typography.bodyMedium,
    )
    if (report.downgrades.isNotEmpty()) {
        Text("Why the profile changed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        report.downgrades.forEach { finding -> SessionReportFindingRow(finding) }
    }
    Text("What to do next", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    report.recommendations.forEach { finding -> SessionReportFindingRow(finding) }
    TextButton(
        onClick = onReportBug,
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
    ) {
        Text("Experienced a bug? ", color = TextMuted)
        Text(
            "Report it",
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        )
    }
}

@Composable
internal fun CompletedSessionBugReportDialog(
    submission: BugReportSubmissionState,
    versionCheck: AndroidBugReportVersionCheckState,
    update: AndroidUpdateState,
    onSubmit: (String, String) -> Unit,
    onReset: () -> Unit,
    onVersionCheck: () -> Unit,
    onOpenUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val landscapeLayout = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var consentChecked by rememberSaveable { mutableStateOf(false) }
    var confirmationOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(update.installSource.isGooglePlay) {
        if (update.installSource.isGooglePlay) onVersionCheck()
    }

    AlertDialog(
        onDismissRequest = {
            if (!submission.uploading) onDismiss()
        },
        modifier = if (landscapeLayout) {
            Modifier.widthIn(max = 960.dp).fillMaxWidth(0.94f)
        } else {
            Modifier
        },
        properties = DialogProperties(usePlatformDefaultWidth = !landscapeLayout),
        title = { Text(stringResource(R.string.bug_report_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(
                        max = if (landscapeLayout) {
                            (configuration.screenHeightDp * 0.68f).dp
                        } else {
                            620.dp
                        },
                    )
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    submission.submitted -> {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Green)
                        Text("Bug report sent", color = Green, fontWeight = FontWeight.Bold)
                        submission.reference?.let { reference ->
                            Text("Reference: $reference", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    !androidBugReportsAllowed(update, versionCheck) -> BugReportVersionGateCard(
                        update = update,
                        versionCheck = versionCheck,
                        onRetry = onVersionCheck,
                        onOpenUpdate = onOpenUpdate,
                    )
                    else -> {
                        Text(
                            "Describe the bug in English. Session diagnostics are attached.",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(
                            value = title,
                            onValueChange = { value ->
                                title = value
                                if (submission.error != null) onReset()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !submission.uploading,
                            singleLine = true,
                            label = { Text("Issue title") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { value ->
                                description = value
                                if (submission.error != null) onReset()
                            },
                            modifier = Modifier.fillMaxWidth().height(128.dp),
                            enabled = !submission.uploading,
                            minLines = 4,
                            maxLines = 7,
                            label = { Text("What happened?") },
                            supportingText = {
                                Text("${description.trim().length} / $ANDROID_BUG_REPORT_MIN_DESCRIPTION_CHARS")
                            },
                            isError = description.isNotEmpty() &&
                                description.trim().length < ANDROID_BUG_REPORT_MIN_DESCRIPTION_CHARS,
                        )
                        BugReportDataDisclosure(includeTypedTextWarning = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = !submission.uploading) {
                                    consentChecked = !consentChecked
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = consentChecked,
                                onCheckedChange = { consentChecked = it },
                                enabled = !submission.uploading,
                            )
                            Text(
                                "I consent to send this report.",
                                color = TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        submission.error?.let { error ->
                            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                submission.submitted -> Button(onClick = onDismiss) { Text("Done") }
                submission.uploading -> Button(enabled = false, onClick = {}) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Sending…")
                }
                androidBugReportsAllowed(update, versionCheck) -> Button(
                    onClick = { confirmationOpen = true },
                    enabled = title.isNotBlank() &&
                        description.trim().length >= ANDROID_BUG_REPORT_MIN_DESCRIPTION_CHARS &&
                        consentChecked,
                ) {
                    Text("Review & send")
                }
            }
        },
        dismissButton = {
            if (!submission.submitted) {
                TextButton(onClick = onDismiss, enabled = !submission.uploading) {
                    Text(stringResource(R.string.action_close))
                }
            }
        },
    )

    if (confirmationOpen) {
        AlertDialog(
            onDismissRequest = { confirmationOpen = false },
            title = { Text("Send bug report?") },
            text = { Text("Send this report and the attached redacted diagnostics to PrintedWaste API?") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmationOpen = false
                        onSubmit(title, description)
                    },
                ) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmationOpen = false }) {
                    Text("Back")
                }
            },
        )
    }
}

private data class SessionReportMetricData(
    val label: String,
    /** Null when the metric was never measured. */
    val value: String?,
    val detail: String?,
    val quality: StreamQualityLevel? = null,
)

/**
 * Six cards in an even two- or three-column grid.
 *
 * They used to be a FlowRow of fixed 136dp cards, which left a ragged right edge at every width
 * and, because `value` was unbounded while `detail` was capped at one line, let cards in the same
 * row end up different heights.
 */
@Composable
private fun SessionReportMetricGrid(metrics: List<SessionReportMetricData>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 520.dp) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(OpenNowSpacing.sm)) {
            metrics.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(OpenNowSpacing.sm)) {
                    row.forEach { metric -> SessionReportMetric(metric, Modifier.weight(1f)) }
                    // Six items divide evenly into 2 and 3, so this is defensive only.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SessionReportMetric(metric: SessionReportMetricData, modifier: Modifier = Modifier) {
    val notMeasured = stringResource(R.string.session_report_not_measured)
    Surface(
        modifier = modifier,
        color = PanelAlt,
        shape = RoundedCornerShape(OpenNowRadius.md),
    ) {
        // A fixed three-line structure keeps every card the same height without an intrinsics
        // pass, which would be a second measure inside an already-scrolling dialog.
        Column(Modifier.padding(horizontal = OpenNowSpacing.md, vertical = 10.dp)) {
            Text(metric.label, color = TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Text(
                metric.value ?: notMeasured,
                color = metric.quality?.tint() ?: TextPrimary,
                style = MaterialTheme.typography.bodyMedium.numeric(),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Rendered even when absent so the line box is still reserved.
            Text(
                metric.detail.orEmpty(),
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall.numeric(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SessionReportFindingRow(finding: SessionReportFinding) {
    val titleColor = if (finding.kind == SessionReportFindingKind.Warning) OpenNowPalette.StatusFair else Green
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(finding.title, color = titleColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(finding.detail, color = TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

private fun normalizeSessionReportResolution(value: String?): Pair<Int, Int>? =
    value?.let(::parseResolutionPixelsOrNull)

@Composable
internal fun DiagnosticShareDialog(
    state: OpenNowUiState,
    onUpload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val share = state.diagnosticShare
    if (!share.awaitingConsent && !share.uploading && share.pasteUrl == null) return
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(share.clipboardSummary, state.androidTvProfile) {
        if (!state.androidTvProfile) {
            share.clipboardSummary?.let { clipboard.setText(AnnotatedString(it)) }
        }
    }
    when {
        share.uploading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Preparing diagnostics") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text("Removing sensitive values and creating a temporary paste…")
                }
            },
            confirmButton = {},
        )
        share.pasteUrl != null -> {
            val qrCode = remember(share.pasteUrl) { QrCode.encodeText(share.pasteUrl) }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(if (state.androidTvProfile) "Scan diagnostics link" else "Diagnostics copied") },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.androidTvProfile) {
                            if (qrCode != null) {
                                QrCodeView(qrCode, Modifier.size(240.dp))
                                Text("Scan this QR code on your phone. The sanitized paste expires within 24 hours.")
                            } else {
                                Text("Could not create the QR code. Close this dialog and try again.")
                            }
                        } else {
                            Text("Device, account type, stream profile, current status, and the temporary paste URL were copied to the clipboard.")
                            Text(
                                share.pasteUrl,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                },
                confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
            )
        }
        else -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Create temporary diagnostics paste?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("OpenNOW will remove tokens, account identifiers, email addresses, session IDs, and network addresses before uploading.")
                    Text("The randomized link is unlisted but not encrypted, and the paste service deletes uploads within 24 hours.", color = TextMuted)
                    share.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { Button(onClick = onUpload) { Text(if (share.error == null) "Sanitize and upload" else "Retry") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun AnalyticsConsentDialog(
    onAllow: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("Share diagnostics?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Share anonymous diagnostics to help us find patterns in bugs, crashes, and performance problems. Sensitive data is removed, and we do not sell your data.",
                )
                Text(
                    "If sharing is off during a crash, we may not have enough information to investigate your report. It is off by default and can be changed in Privacy settings.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(onClick = onAllow) {
                Text("Share analytics")
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text("Keep off")
            }
        },
    )
}

@Composable
internal fun AndroidUpdatePromptDialog(
    update: AndroidUpdateState,
    onPrimary: () -> Unit,
    onDetails: () -> Unit,
    onDismiss: () -> Unit,
) {
    val version = update.availableVersionName?.let { "Version $it" }
        ?: update.availableVersionCode?.let { "Build $it" }
        ?: "A new build"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (update.status == AndroidUpdateStatus.Downloaded) "Update ready" else "OpenNOW update available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (update.status == AndroidUpdateStatus.Downloaded) {
                        "$version is downloaded and ready to install."
                    } else if (update.installSource.isGooglePlay) {
                        "You are on build ${update.currentVersionCode}. Google Play has ${version.lowercase()}."
                    } else {
                        "$version is available for this device."
                    },
                )
                update.releaseNotes?.trim()?.takeIf { it.isNotBlank() }?.let { notes ->
                    Text(
                        notes,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onPrimary) {
                Text(
                    when {
                        update.status == AndroidUpdateStatus.Downloaded -> "Install"
                        update.installSource.isGooglePlay -> "Update"
                        else -> "Download"
                    },
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDetails) {
                    Text("Details")
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}

@Composable
private fun LoadingScreen(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OpenNowMark(72.dp)
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(text, color = TextMuted)
        }
    }
}
