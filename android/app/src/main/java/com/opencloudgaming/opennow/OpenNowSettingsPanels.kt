package com.opencloudgaming.opennow

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import android.os.PowerManager
import android.os.BatteryManager
import android.os.Build
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Spacer
import kotlinx.coroutines.delay

@Composable
internal fun AppDataSettingsPanel(viewModel: OpenNowViewModel) {
    var clearCacheConfirmOpen by remember { mutableStateOf(false) }
    var resetSettingsConfirmOpen by remember { mutableStateOf(false) }
    if (clearCacheConfirmOpen) {
        AlertDialog(
            onDismissRequest = { clearCacheConfirmOpen = false },
            title = { Text("Clear game cache?") },
            text = { Text("Cached store, library, and search results will be removed. Your account and settings stay unchanged.") },
            confirmButton = {
                Button(
                    onClick = {
                        clearCacheConfirmOpen = false
                        viewModel.clearCatalogCache()
                    },
                ) {
                    Text("Clear cache")
                }
            },
            dismissButton = {
                TextButton(onClick = { clearCacheConfirmOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    if (resetSettingsConfirmOpen) {
        AlertDialog(
            onDismissRequest = { resetSettingsConfirmOpen = false },
            title = { Text("Reset settings and app data?") },
            text = { Text("Accounts, settings, cached games, tutorial state, and local app files will be removed. OpenNOW will relaunch like a fresh install.") },
            confirmButton = {
                Button(
                    onClick = {
                        resetSettingsConfirmOpen = false
                        viewModel.resetSettings()
                    },
                ) {
                    Text("Reset and relaunch")
                }
            },
            dismissButton = {
                TextButton(onClick = { resetSettingsConfirmOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Reset tutorial only makes the stream guide appear again. Reset settings is destructive: it clears local app data and relaunches OpenNOW.",
            color = SettingsTextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { clearCacheConfirmOpen = true }, modifier = Modifier.weight(1f)) {
                    Text("Clear cache", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = viewModel::resetStreamTutorial, modifier = Modifier.weight(1f)) {
                    Text("Reset tutorial", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            OutlinedButton(onClick = { resetSettingsConfirmOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Reset settings", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun AndroidUpdatePanel(state: OpenNowUiState, viewModel: OpenNowViewModel) {
    val update = state.androidUpdate
    if (!update.apkUpdatesAllowed) {
        AndroidUpdateUnavailablePanel(update)
        return
    }
    val updateCheckingDisabled = !state.settings.autoCheckForUpdates
    val checkBlockedByStream = state.isAndroidUpdateCheckBlockedByStream()
    val showCheckPauseMessage = checkBlockedByStream && when (update.status) {
        AndroidUpdateStatus.Available,
        AndroidUpdateStatus.Downloading,
        AndroidUpdateStatus.Downloaded -> false
        else -> true
    }
    val statusMessage = when {
        updateCheckingDisabled -> "Automatic checks are off."
        showCheckPauseMessage -> "Checks pause while streaming."
        else -> updateStatusSubtitle(update)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (update.status in updateAvailableStatuses) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        updateStatusTitle(update),
                        color = SettingsText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        statusMessage,
                        color = SettingsTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                UpdateStatusBadge(update.status)
            }
            UpdateVersionSummary(update)
            if (update.status == AndroidUpdateStatus.Downloading) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    update.progress?.let { progress ->
                        Text(
                            formatAndroidUpdateProgress(progress),
                            color = SettingsTextMuted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            UpdateReleaseNotes(update.releaseNotes)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = viewModel::checkAndroidUpdate,
                    enabled = update.canCheck && !checkBlockedByStream && !updateCheckingDisabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (update.status == AndroidUpdateStatus.Checking) "Checking..." else "Check", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                when {
                    update.status == AndroidUpdateStatus.Available -> {
                        Button(
                            onClick = viewModel::downloadAndroidUpdate,
                            enabled = update.canDownload,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Download", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    update.status == AndroidUpdateStatus.Downloaded -> {
                        Button(
                            onClick = viewModel::installAndroidUpdate,
                            enabled = update.canInstall,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Install", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AndroidUpdateUnavailablePanel(update: AndroidUpdateState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        if (update.installSource.isGooglePlay) "Updates managed by Google Play" else "APK updates unavailable",
                        color = SettingsText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        update.message,
                        color = SettingsTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                ) {
                    Text(
                        if (update.installSource.isGooglePlay) "PLAY" else "LOCKED",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.52f),
            ) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    UpdateInfoValue("Current", update.currentVersionName.ifBlank { "Installed" }, Modifier.weight(1f))
                    UpdateInfoValue("Source", update.installSource.displayName, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun UpdateStatusBadge(status: AndroidUpdateStatus) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = updateMessageColor(status).copy(alpha = 0.16f),
    ) {
        Text(
            updateStatusBadgeText(status),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = updateMessageColor(status),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

private val updateAvailableStatuses = setOf(
    AndroidUpdateStatus.Available,
    AndroidUpdateStatus.Downloading,
    AndroidUpdateStatus.Downloaded,
)

@Composable
private fun UpdateVersionSummary(update: AndroidUpdateState) {
    val checked = update.lastCheckedAt?.let { checkedAt ->
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(checkedAt))
    }
    val availableVersion = formatAvailableUpdateVersion(update)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.52f),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                UpdateInfoValue("Current", update.currentVersionName.ifBlank { "Installed" }, Modifier.weight(1f))
                availableVersion?.let {
                    UpdateInfoValue("Available", it, Modifier.weight(1f))
                }
            }
            checked?.let {
                Text(
                    "Last checked $it",
                    color = SettingsTextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun UpdateInfoValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            color = SettingsTextMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            color = SettingsText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun UpdateReleaseNotes(notes: String?) {
    val releaseNotes = notes?.trim()?.takeIf { it.isNotBlank() } ?: return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.52f),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Release notes",
                color = SettingsText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                releaseNotes,
                color = SettingsTextMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatAvailableUpdateVersion(update: AndroidUpdateState): String? {
    val pieces = listOfNotNull(
        update.availableVersionName?.let { "v$it" },
        update.availableVersionCode?.let { "build $it" },
    )
    return pieces.takeIf { it.isNotEmpty() }?.joinToString(" ")
}

private fun updateStatusTitle(update: AndroidUpdateState): String =
    when (update.status) {
        AndroidUpdateStatus.Available -> "Update available"
        AndroidUpdateStatus.Downloading -> "Downloading update"
        AndroidUpdateStatus.Downloaded -> "Ready to install"
        AndroidUpdateStatus.NotAvailable -> "OpenNOW is up to date"
        AndroidUpdateStatus.Checking -> "Checking for updates"
        AndroidUpdateStatus.Error -> "Update check failed"
        AndroidUpdateStatus.Idle -> "App updates"
    }

private fun updateStatusSubtitle(update: AndroidUpdateState): String =
    when (update.status) {
        AndroidUpdateStatus.Available -> update.availableVersionName?.let { "Version $it is available." } ?: "A new build is available."
        AndroidUpdateStatus.Downloading -> "Keep OpenNOW open while the APK downloads."
        AndroidUpdateStatus.Downloaded -> update.availableVersionName?.let { "Version $it has been downloaded." } ?: "The update has been downloaded."
        AndroidUpdateStatus.NotAvailable -> update.message
        AndroidUpdateStatus.Checking -> "Contacting the update source."
        AndroidUpdateStatus.Error -> update.message
        AndroidUpdateStatus.Idle -> update.message
    }

private fun updateStatusBadgeText(status: AndroidUpdateStatus): String =
    when (status) {
        AndroidUpdateStatus.Available -> "NEW"
        AndroidUpdateStatus.Downloading -> "DOWNLOADING"
        AndroidUpdateStatus.Downloaded -> "READY"
        AndroidUpdateStatus.NotAvailable -> "CURRENT"
        AndroidUpdateStatus.Checking -> "CHECKING"
        AndroidUpdateStatus.Error -> "ERROR"
        AndroidUpdateStatus.Idle -> "IDLE"
    }

@Composable
private fun updateMessageColor(status: AndroidUpdateStatus): Color =
    when (status) {
        AndroidUpdateStatus.Available,
        AndroidUpdateStatus.Downloaded,
        AndroidUpdateStatus.NotAvailable -> MaterialTheme.colorScheme.primary
        AndroidUpdateStatus.Error -> Color(0xffff9f9f)
        else -> SettingsTextMuted
    }

private fun formatAndroidUpdateProgress(progress: AndroidUpdateProgress): String {
    val bytes = progress.totalBytes?.let { total ->
        "${formatUpdateBytes(progress.transferredBytes)} / ${formatUpdateBytes(total)}"
    } ?: formatUpdateBytes(progress.transferredBytes)
    return progress.percent?.let { "$it% - $bytes" } ?: bytes
}

private fun formatUpdateBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes.toDouble() / 1024.0
    var unit = units.first()
    for (index in 1 until units.size) {
        if (value < 1024.0) break
        value /= 1024.0
        unit = units[index]
    }
    return "%.1f %s".format(Locale.US, value, unit)
}

@Composable
internal fun AccountSettingsPanel(state: OpenNowUiState, viewModel: OpenNowViewModel) {
    val currentSession = state.authSession
    val currentUserId = currentSession?.user?.userId
    val context = LocalContext.current
    var addAccountPromptOpen by remember { mutableStateOf(false) }
    val addAccountProviders = remember(state.providers, state.selectedProvider) {
        accountProviderOptions(state.providers, state.selectedProvider)
    }
    if (addAccountPromptOpen) {
        AddAccountProviderDialog(
            providers = addAccountProviders,
            selectedProvider = state.selectedProvider,
            onProviderSelected = { provider ->
                addAccountPromptOpen = false
                viewModel.selectProvider(provider)
                viewModel.login(provider)
            },
            onDismiss = { addAccountPromptOpen = false },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.savedAccounts.ifEmpty {
            state.authSession?.toSavedAccount()?.let { listOf(it) } ?: emptyList()
        }.forEach { account ->
            val selected = account.userId == currentUserId
            val membershipTier = if (selected) {
                state.subscriptionInfo?.membershipTier?.takeIf { it.isNotBlank() }
                    ?: currentSession.user.membershipTier.takeIf { it.isNotBlank() }
                    ?: account.membershipTier
            } else {
                account.membershipTier
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.76f),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(account.displayName.ifBlank { "NVIDIA Account" }, color = SettingsText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(account.email?.takeIf { it.isNotBlank() }, account.providerCode, membershipTier).joinToString(" - "),
                            color = SettingsTextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (selected) {
                        Text("Active", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    } else {
                        OutlinedButton(onClick = { viewModel.switchAccount(account.userId) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                            Text("Switch")
                        }
                    }
                }
            }
        }
        AndroidUpdateNoticeRow(
            update = state.androidUpdate,
            dismissedKey = state.dismissedAndroidUpdateNoticeKey,
            onOpenUpdates = viewModel::openAndroidUpdateSettings,
            onDismiss = viewModel::dismissAndroidUpdateNotice,
        )
        state.deviceLoginPrompt?.let { prompt ->
            DeviceLoginPanel(
                prompt = prompt,
                phase = state.launchPhase,
                onCancel = viewModel::cancelLogin,
                modifier = Modifier.fillMaxWidth(),
                qrMaxSize = 240.dp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { addAccountPromptOpen = true }, modifier = Modifier.weight(1f)) { Text("Add account") }
            OutlinedButton(onClick = viewModel::logout, modifier = Modifier.weight(1f)) { Text("Sign out") }
        }
        OutlinedButton(onClick = viewModel::logoutAll, modifier = Modifier.fillMaxWidth()) { Text("Sign out all accounts") }
        AccountPlayTimeStatsPanel(
            subscriptionInfo = state.subscriptionInfo,
            fallbackMembershipTier = state.authSession?.user?.membershipTier,
        )
        StorageAddonPanel(
            storageAddon = state.subscriptionInfo?.storageAddon,
            openExternal = { url ->
                if (!openExternalUrl(context, url)) {
                    Toast.makeText(context, "No browser available", Toast.LENGTH_SHORT).show()
                }
            },
        )
        AccountConnectorsPanel(
            connectors = state.accountConnectors,
            loading = state.loadingAccountConnectors,
            actionStore = state.connectorActionStore,
            onRefresh = viewModel::refreshAccountConnectors,
            onConnect = { connector ->
                viewModel.connectAccountConnector(connector.store) { url ->
                    if (!openExternalUrl(context, url)) {
                        Toast.makeText(context, "No browser available", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDisconnect = { connector ->
                viewModel.disconnectAccountConnector(connector.store)
            },
            openExternal = { url ->
                if (!openExternalUrl(context, url)) {
                    Toast.makeText(context, "No browser available", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

@Composable
private fun AddAccountProviderDialog(
    providers: List<LoginProvider>,
    selectedProvider: LoginProvider,
    onProviderSelected: (LoginProvider) -> Unit,
    onDismiss: () -> Unit,
) {
    var providerChoice by remember(providers, selectedProvider) {
        mutableStateOf(providers.preferredProvider(selectedProvider))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose provider") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Select the GeForce NOW provider to use for the new account.",
                    color = SettingsTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                providers.forEach { provider ->
                    ProviderChoiceRow(
                        provider = provider,
                        selected = provider.sameProvider(providerChoice),
                        onClick = { providerChoice = provider },
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onProviderSelected(providerChoice) }) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun ProviderChoiceRow(provider: LoginProvider, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.76f)
        },
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    provider.displayName,
                    color = SettingsText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    provider.code,
                    color = SettingsTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Text("Selected", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AccountPlayTimeStatsPanel(subscriptionInfo: SubscriptionInfo?, fallbackMembershipTier: String?) {
    val sessionLimit = smartSessionLimitFor(subscriptionInfo, fallbackMembershipTier)
    val monthlyLimit = monthlyHourLimitFor(subscriptionInfo, fallbackMembershipTier)
    val monthlyRemaining = monthlyHoursRemainingFor(subscriptionInfo, fallbackMembershipTier)
    val usedHours = subscriptionInfo?.usedHours?.takeIf { it > 0.0 }
    val progressFraction = if (monthlyLimit != null && monthlyLimit > 0.0) {
        ((usedHours ?: 0.0) / monthlyLimit).toFloat().coerceIn(0f, 1f)
    } else {
        null
    }
    val freePlan = sessionLimit.mode == SessionTimerMode.Countdown
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.76f),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Play time stats", color = SettingsText, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                UsageMetricTile(
                    label = "Session",
                    value = "${sessionLimit.limitHours}h",
                    detail = when (sessionLimit.mode) {
                        SessionTimerMode.Countdown -> "countdown"
                        SessionTimerMode.Stopwatch -> "stopwatch"
                    },
                    modifier = Modifier.weight(1f),
                )
                UsageMetricTile(
                    label = "Monthly left",
                    value = monthlyRemaining?.let(::formatPlayTimeHours) ?: "--",
                    detail = monthlyLimit?.let { "of ${formatPlayTimeHours(it)}" } ?: if (freePlan) "paid plans" else "refresh account",
                    modifier = Modifier.weight(1f),
                )
            }
            if (progressFraction != null && monthlyLimit != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "${formatPlayTimeHours(usedHours ?: 0.0)} used",
                            color = SettingsTextMuted,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${formatPlayTimePercent(progressFraction)} of ${formatPlayTimeHours(monthlyLimit)}",
                            color = SettingsTextMuted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                            .semantics {
                                contentDescription = "Monthly play time ${formatPlayTimePercent(progressFraction)} used"
                                progressBarRangeInfo = ProgressBarRangeInfo(progressFraction, 0f..1f)
                            },
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progressFraction)
                                .height(4.dp)
                                .background(
                                    when {
                                        progressFraction >= 0.9f -> Color(0xffff8a65)
                                        progressFraction >= 0.75f -> Color(0xffffc266)
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                ),
                        )
                    }
                }
            } else {
                Text(
                    if (freePlan) {
                        "Paid plans show monthly play-time usage here when NVIDIA reports it."
                    } else {
                        "Refresh Account settings after sign-in to load monthly play-time usage."
                    },
                    color = SettingsTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun UsageMetricTile(label: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.52f),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = SettingsTextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, color = SettingsText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = SettingsTextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun AndroidUpdateNoticeRow(
    update: AndroidUpdateState,
    dismissedKey: String?,
    onOpenUpdates: () -> Unit,
    onDismiss: () -> Unit,
) {
    val noticeKey = update.visibleNoticeKey(dismissedKey) ?: return
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpenUpdates),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
    ) {
        Row(
            Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            UpdateStatusBadge(update.status)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(accountUpdateTitle(update), color = SettingsText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(accountUpdateSubtitle(update), color = SettingsTextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (update.status == AndroidUpdateStatus.Downloading) {
                CircularUpdateProgress(update.progress)
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.semantics { contentDescription = "Dismiss update ${noticeKey.takeLast(12)}" },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_clear),
                    contentDescription = null,
                    tint = SettingsTextMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun CircularUpdateProgress(progress: AndroidUpdateProgress?) {
    val label = progress?.let(::formatAndroidUpdateProgress) ?: "Downloading"
    Text(
        label,
        color = SettingsTextMuted,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun accountUpdateTitle(update: AndroidUpdateState): String =
    when (update.status) {
        AndroidUpdateStatus.Downloaded -> "Update ready"
        AndroidUpdateStatus.Downloading -> "Downloading OpenNOW"
        else -> "OpenNOW update available"
    }

private fun accountUpdateSubtitle(update: AndroidUpdateState): String =
    update.availableVersionName?.let { "Version $it is ready for this device." }
        ?: update.message

@Composable
private fun StorageAddonPanel(storageAddon: StorageAddon?, openExternal: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.76f),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cloud storage", color = SettingsText, fontWeight = FontWeight.SemiBold)
            if (storageAddon == null) {
                Text("No persistent storage add-on is active for this account.", color = SettingsTextMuted, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { openExternal(GFN_ADD_STORAGE_URL) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Add storage", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                val used = storageAddon.usedGb
                val total = storageAddon.sizeGb
                val usageFraction = storageUsageFraction(used, total)
                Text(
                    listOfNotNull(
                        total?.let { "Total ${formatStorageGb(it)}" },
                        used?.let { "Used ${formatStorageGb(it)}" },
                        if (used != null && total != null) "Available ${formatStorageGb((total - used).coerceAtLeast(0.0))}" else null,
                    ).joinToString(" - "),
                    color = SettingsTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (usageFraction != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Storage usage",
                                color = SettingsText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${formatStoragePercent(usageFraction)} used",
                                color = SettingsTextMuted,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        val usageColor = when {
                            usageFraction >= 0.9f -> Color(0xffff8a65)
                            usageFraction >= 0.75f -> Color(0xffffc266)
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                .semantics {
                                    contentDescription = "Cloud storage ${formatStoragePercent(usageFraction)} used"
                                    progressBarRangeInfo = ProgressBarRangeInfo(usageFraction, 0f..1f)
                                },
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(usageFraction.coerceIn(0f, 1f))
                                    .height(4.dp)
                                    .background(usageColor),
                            )
                        }
                    }
                }
                storageAddon.regionName?.takeIf { it.isNotBlank() }?.let { region ->
                    Text("Location: $region", color = SettingsTextMuted, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { openExternal(GFN_STORAGE_MANAGEMENT_URL) }, modifier = Modifier.weight(1f)) {
                        Text("Manage", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(onClick = { openExternal(GFN_STORAGE_RESET_URL) }, modifier = Modifier.weight(1f)) {
                        Text("Reset", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                OutlinedButton(onClick = { openExternal(GFN_STORAGE_MANAGEMENT_URL) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Change storage location", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun AccountConnectorsPanel(
    connectors: List<AccountConnector>,
    loading: Boolean,
    actionStore: String?,
    onRefresh: () -> Unit,
    onConnect: (AccountConnector) -> Unit,
    onDisconnect: (AccountConnector) -> Unit,
    openExternal: (String) -> Unit,
) {
    var disconnecting by remember { mutableStateOf<AccountConnector?>(null) }
    disconnecting?.let { connector ->
        AlertDialog(
            onDismissRequest = { disconnecting = null },
            title = { Text("Disconnect ${connector.label}?") },
            text = { Text("This removes the linked ${connector.label} account from GeForce NOW. You can connect it again later.") },
            confirmButton = {
                Button(
                    onClick = {
                        disconnecting = null
                        onDisconnect(connector)
                    },
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { disconnecting = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.76f),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Game store connections", color = SettingsText, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onRefresh, enabled = !loading) {
                    Text(if (loading) "Refreshing..." else "Refresh")
                }
            }
            if (connectors.isEmpty()) {
                Text(
                    if (loading) "Loading connected stores..." else "Connect Steam, Epic, Xbox, and other supported stores to sync your GeForce NOW library.",
                    color = SettingsTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                connectors.take(6).forEach { connector ->
                    ConnectorRow(
                        connector = connector,
                        busy = actionStore == connector.store,
                        onConnect = { onConnect(connector) },
                        onDisconnect = { disconnecting = connector },
                    )
                }
            }
            OutlinedButton(onClick = { openExternal(GFN_ACCOUNT_HELP_URL) }, modifier = Modifier.fillMaxWidth()) {
                Text("Connection help", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ConnectorRow(
    connector: AccountConnector,
    busy: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val actionEnabled = !busy && (connector.isLinked || connector.supported)
    val badge = launcherBadgeForStoreKey(splitGameStoreKeys(connector.store).firstOrNull())
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = actionEnabled) {
                if (connector.isLinked) onDisconnect() else onConnect()
            },
    ) {
        ConnectorStoreIcon(badge)
        Column(Modifier.weight(1f)) {
            Text(connector.label, color = SettingsText, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(connectorStatusText(connector), color = SettingsTextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (connector.isLinked) {
            OutlinedButton(onClick = onDisconnect, enabled = !busy, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                Text(if (busy) "Removing..." else "Disconnect")
            }
        } else {
            Button(onClick = onConnect, enabled = connector.supported && !busy, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                Text(if (busy) "Opening..." else "Connect")
            }
        }
    }
}

@Composable
internal fun ConnectorStoreIcon(badge: LauncherBadge) {
    Surface(
        modifier = Modifier
            .size(34.dp)
            .semantics { contentDescription = "${badge.name} store" },
        shape = RoundedCornerShape(10.dp),
        color = badge.background.copy(alpha = 0.88f),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                painter = painterResource(badge.iconRes),
                contentDescription = null,
                tint = badge.foreground,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

private fun AuthSession.toSavedAccount(): SavedAccount =
    SavedAccount(
        userId = user.userId,
        displayName = user.displayName,
        email = user.email,
        avatarUrl = user.avatarUrl,
        membershipTier = user.membershipTier,
        providerCode = provider.code,
    )

private fun accountProviderOptions(providers: List<LoginProvider>, selectedProvider: LoginProvider): List<LoginProvider> =
    (providers + selectedProvider)
        .distinctBy { it.providerIdentityKey() }
        .ifEmpty { listOf(selectedProvider) }

private fun List<LoginProvider>.preferredProvider(provider: LoginProvider): LoginProvider =
    firstOrNull { it.sameProvider(provider) }
        ?: firstOrNull()
        ?: provider

private fun LoginProvider.sameProvider(other: LoginProvider): Boolean =
    providerIdentityKey() == other.providerIdentityKey()

private fun LoginProvider.providerIdentityKey(): String =
    idpId.ifBlank { code }.lowercase(Locale.US)

private const val GFN_STORAGE_MANAGEMENT_URL = "https://gfn.link/cloudstorage"
private const val GFN_STORAGE_RESET_URL = "https://gfn.link/resetstorage"
private const val GFN_ADD_STORAGE_URL = "https://gfn.link/addstorage"
private const val GFN_ACCOUNT_HELP_URL = "https://gfn.link/5399"
private const val OPENNOW_GITHUB_URL = "https://github.com/OpenCloudGaming/OpenNOW"

private data class DeveloperCredit(
    val name: String,
    val githubUrl: String,
)

private val DEVELOPER_CREDITS = listOf(
    DeveloperCredit("Kiefer", "https://github.com/Kief5555"),
    DeveloperCredit("Zortos", "https://github.com/zortos293"),
)

private fun formatStorageGb(value: Double): String =
    if (value % 1.0 == 0.0) "${value.toInt()} GB" else "%.1f GB".format(Locale.US, value)

private fun storageUsageFraction(usedGb: Double?, totalGb: Double?): Float? {
    if (usedGb == null || totalGb == null || totalGb <= 0.0) return null
    return (usedGb / totalGb).coerceIn(0.0, 1.0).toFloat()
}

private fun formatStoragePercent(fraction: Float): String =
    "${(fraction * 100).roundToInt().coerceIn(0, 100)}%"

private fun formatPlayTimeHours(value: Double): String =
    if (value >= 10.0 || value % 1.0 == 0.0) {
        "${value.roundToInt()}h"
    } else {
        "%.1fh".format(Locale.US, value)
    }

private fun formatPlayTimePercent(fraction: Float): String =
    "${(fraction * 100).roundToInt().coerceIn(0, 100)}%"

private fun connectorStatusText(connector: AccountConnector): String {
    if (!connector.isLinked) return if (connector.required) "Required for some games" else "Available to connect"
    val identity = connector.userDisplayName?.takeIf { it.isNotBlank() }
        ?: connector.userIdentifier?.takeIf { it.isNotBlank() }
    val sync = when {
        connector.syncedGameCount != null -> "${connector.syncedGameCount} synced games"
        !connector.syncState.isNullOrBlank() -> connector.syncState.replace('_', ' ').lowercase(Locale.US)
            .replaceFirstChar { it.titlecase(Locale.US) }
        else -> null
    }
    return listOfNotNull(identity, sync).joinToString(" - ").ifBlank { "Connected" }
}

@Composable
internal fun CodecDiagnosticsPanel(report: RuntimeCodecReport?) {
    if (report == null) {
        Text(stringResource(R.string.settings_codec_diagnostics_unavailable), color = SettingsTextMuted)
        return
    }
    val clipboard = LocalClipboardManager.current
    var copied by remember(report) { mutableStateOf(false) }
    val safeDecoders = report.capabilities.count { it.streamingRealtimeSafe() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = {
                clipboard.setText(AnnotatedString(formatCodecDiagnosticReport(report)))
                copied = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (copied) {
                    stringResource(R.string.settings_codec_diagnostics_copied)
                } else {
                    stringResource(R.string.settings_codec_diagnostics_copy)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            CodecSummaryChip("${safeDecoders}/${report.capabilities.size}", "real-time decoders")
            CodecSummaryChip(if (report.lowPowerGpuProfile) "Low power" else "Standard", "device profile")
            CodecSummaryChip(if (report.androidTvProfile) "TV" else "Mobile", "shell")
        }
        report.capabilities.forEach { capability ->
            CodecCapabilityRow(capability)
        }
        Text(
            report.nativeRuntimeSummary.replace("{", "").replace("}", "").replace("\"", ""),
            color = SettingsTextMuted,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatCodecDiagnosticReport(report: RuntimeCodecReport): String = buildString {
    appendLine("OpenNOW Android codec diagnostics")
    appendLine("nativeRuntimeSummary=${report.nativeRuntimeSummary}")
    appendLine("androidTvProfile=${report.androidTvProfile}")
    appendLine("lowPowerGpuProfile=${report.lowPowerGpuProfile}")
    report.capabilities.forEach { capability ->
        appendLine()
        appendLine("codec=${capability.codec}")
        appendLine("decoderAvailable=${capability.decoderAvailable}")
        appendLine("decoderName=${capability.decoderName ?: "none"}")
        appendLine("hardwareDecoder=${capability.hardwareDecoder}")
        appendLine("realtimeSafe=${capability.realtimeSafe}")
        appendLine("nativeDecoderAvailable=${capability.nativeDecoderAvailable ?: "unknown"}")
        appendLine("webRtcDecoderAvailable=${capability.webRtcDecoderAvailable ?: "unknown"}")
        appendLine("webRtcDecoderName=${capability.webRtcDecoderName ?: "none"}")
        appendLine("webRtcHardwareDecoderAvailable=${capability.webRtcHardwareDecoderAvailable ?: "unknown"}")
        appendLine("webRtcProfiles=${capability.webRtcCodecProfiles.joinToString(", ").ifBlank { "none" }}")
        appendLine("encoderAvailable=${capability.encoderAvailable}")
        appendLine("encoderName=${capability.encoderName ?: "none"}")
        appendLine("hardwareEncoder=${capability.hardwareEncoder}")
    }
}

@Composable
private fun RowScope.CodecSummaryChip(value: String, label: String) {
    Surface(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.76f),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(value, color = SettingsText, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, color = SettingsTextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CodecCapabilityRow(capability: CodecCapability) {
    val streamingReady = capability.streamingDecoderAvailable()
    val healthy = capability.streamingRealtimeSafe()
    val status = when {
        healthy -> "Ready"
        streamingReady -> "WebRTC ready"
        capability.decoderAvailable -> "Platform only"
        else -> "Unavailable"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.76f),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(capability.codec.name, color = SettingsText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    status,
                    color = if (healthy) MaterialTheme.colorScheme.primary else Color(0xffffc266),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                "WebRTC: ${capability.streamingDecoderName() ?: "none"}",
                color = SettingsTextMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Hardware decode ${yesNo(capability.streamingHardwareDecoderAvailable())} - native ${capability.nativeDecoderAvailable ?: "unknown"} - platform ${capability.decoderName ?: "none"}",
                color = SettingsTextMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun yesNo(value: Boolean): String = if (value) "yes" else "no"

internal val StreamStatsStyle.label: String
    get() = when (this) {
        StreamStatsStyle.Compact -> "Single line"
        StreamStatsStyle.Detailed -> "Multiline"
    }

internal fun StreamStatsStyle.next(): StreamStatsStyle =
    when (this) {
        StreamStatsStyle.Compact -> StreamStatsStyle.Detailed
        StreamStatsStyle.Detailed -> StreamStatsStyle.Compact
    }

internal val StreamStatsPosition.label: String
    get() = when (this) {
        StreamStatsPosition.Left -> "Left"
        StreamStatsPosition.Center -> "Center"
        StreamStatsPosition.Right -> "Right"
    }

internal fun StreamStatsPosition.next(): StreamStatsPosition =
    when (this) {
        StreamStatsPosition.Left -> StreamStatsPosition.Center
        StreamStatsPosition.Center -> StreamStatsPosition.Right
        StreamStatsPosition.Right -> StreamStatsPosition.Left
    }

@Composable
internal fun AppVersionPanel() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SettingsPanelAlt)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("OpenNOW Android", color = SettingsText, fontWeight = FontWeight.SemiBold)
            Text("Version ${BuildConfig.VERSION_NAME}", color = SettingsTextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Text("Build ${BuildConfig.VERSION_CODE}", color = SettingsTextMuted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun OpenNowGitHubPanel() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SettingsPanelAlt)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("OpenNOW Repository", color = SettingsText, fontWeight = FontWeight.SemiBold)
            Text("OpenCloudGaming/OpenNOW", color = SettingsTextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(onClick = { openExternalUrlOrCopy(context, clipboard, OPENNOW_GITHUB_URL, "GitHub link copied") }) {
            Text("GitHub", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun DeveloperPanel() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DEVELOPER_CREDITS.forEach { developer ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SettingsPanelAlt)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    UrlImage("${developer.githubUrl}.png?size=160", Modifier.fillMaxSize().clip(CircleShape))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(developer.name, color = SettingsText, fontWeight = FontWeight.SemiBold)
                    Text("Developer", color = SettingsTextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = { openExternalUrlOrCopy(context, clipboard, developer.githubUrl, "GitHub link copied") }) {
                    Text("GitHub", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun ThanksPanel() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Text(
        stringResource(R.string.settings_thanks_body),
        color = SettingsTextMuted,
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SettingsPanelAlt)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.settings_thanks_darkevilpt), color = SettingsText, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.settings_thanks_darkevilpt_note), color = SettingsTextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
    Button(
        onClick = {
            openExternalUrlOrCopy(context, clipboard, DONATE_URL, context.getString(R.string.settings_donate_link_copied))
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.settings_donate), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun openExternalUrlOrCopy(
    context: android.content.Context,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    url: String,
    copiedMessage: String,
) {
    if (!openExternalUrl(context, url)) {
        clipboard.setText(AnnotatedString(url))
        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
    }
}

@Composable
internal fun DebugLogsPanel(state: OpenNowUiState, viewModel: OpenNowViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var pendingLogText by remember { mutableStateOf("") }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(pendingLogText.toByteArray(Charsets.UTF_8))
            } ?: error("Could not open log file")
        }.onSuccess {
            saved = true
            saveError = null
        }.onFailure { error ->
            saveError = error.message ?: "Could not save logs"
        }
    }
    Text(
        "Exports launch state, queue state, stream updates, recovery events, settings, codec capabilities, and recent sanitized CloudMatch JSON responses.",
        color = SettingsTextMuted,
    )
    if (state.androidTvProfile) {
        Button(
            onClick = viewModel::uploadDiagnosticShare,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Upload logs and show QR", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            "Sensitive values are removed before an unlisted, temporary paste is created. Scan the QR code with your phone to share it.",
            color = SettingsTextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(viewModel.sanitizedDebugLogText()))
                    copied = true
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(if (copied) "Copied logs" else "Copy logs", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = {
                    pendingLogText = viewModel.sanitizedDebugLogText()
                    saved = false
                    saveError = null
                    saveLauncher.launch(viewModel.debugLogFileName())
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(if (saved) "Exported" else "Export logs", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    state.error?.let { error ->
        OutlinedButton(
            onClick = {
                clipboard.setText(AnnotatedString(error))
                copied = true
            },
        ) {
            Text("Copy error")
        }
    }
    saveError?.let {
        Text(it, color = Color(0xffff9f9f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun rememberDeviceHasBattery(): Boolean {
    val appContext = LocalContext.current.applicationContext
    return remember(appContext) { deviceHasBattery(appContext) }
}

internal fun shouldShowBatteryOptimization(explicitBatteryPresent: Boolean?): Boolean =
    explicitBatteryPresent != false

private fun deviceHasBattery(context: Context): Boolean {
    val batteryStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            Context.RECEIVER_NOT_EXPORTED,
        )
    } else {
        @Suppress("DEPRECATION")
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }
    val explicitBatteryPresent = batteryStatus
        ?.takeIf { it.hasExtra(BatteryManager.EXTRA_PRESENT) }
        ?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true)
    return shouldShowBatteryOptimization(explicitBatteryPresent)
}

@Composable
internal fun BatteryOptimizationPanel() {
    val context = LocalContext.current
    var isIgnoring by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        while (true) {
            isIgnoring = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            delay(1000L)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Android battery optimization restricts the app's background activity, which can cause connection timeouts or pause GFN queue progress when the app is minimized.",
            style = MaterialTheme.typography.bodyMedium,
            color = SettingsTextMuted
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Background activity",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (isIgnoring) "Unlimited (Allowed in background)" else "Optimized (May timeout in background)",
                    color = if (isIgnoring) Color(0xff81c784) else Color(0xffffb74d),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!isIgnoring) {
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            } catch (_: Exception) {}
                        }
                    }
                ) {
                    Text("Allow")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        } catch (_: Exception) {}
                    }
                ) {
                    Text("Settings")
                }
            }
        }
    }
}
