package com.opencloudgaming.opennow

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.opencloudgaming.opennow.ui.controls.ControlRow
import com.opencloudgaming.opennow.ui.controls.ControlRowLabels
import com.opencloudgaming.opennow.ui.controls.controlRowStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

internal data class LocalAppEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable,
)

internal fun normalizeLocalAppPackageNames(packageNames: List<String>): List<String> =
    packageNames.map(String::trim).filter(String::isNotEmpty).distinct()

@Composable
internal fun LocalAppsShelf(
    packageNames: List<String>,
    onAddPackage: (String) -> Unit,
    onRemovePackage: (String) -> Unit,
    focusRequester: FocusRequester? = null,
    topFocusRequester: FocusRequester? = null,
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val normalizedPackages = remember(packageNames) { normalizeLocalAppPackageNames(packageNames) }
    val apps = remember(normalizedPackages) {
        normalizedPackages.mapNotNull { packageName ->
            runCatching {
                val info = packageManager.getApplicationInfo(packageName, 0)
                LocalAppEntry(
                    packageName = packageName,
                    label = packageManager.getApplicationLabel(info).toString(),
                    icon = packageManager.getApplicationIcon(info),
                )
            }.getOrNull()
        }
    }
    var pickerOpen by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.library_local_apps),
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "add-local-app") {
                AddLocalAppTile(
                    onClick = { pickerOpen = true },
                    focusRequester = focusRequester,
                    topFocusRequester = topFocusRequester,
                )
            }
            items(apps, key = { it.packageName }) { app ->
                LocalAppTile(
                    app = app,
                    onLaunch = {
                        if (!launchLocalApp(context, app.packageName)) {
                            Toast.makeText(context, R.string.library_local_app_unavailable, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRemove = { onRemovePackage(app.packageName) },
                    topFocusRequester = topFocusRequester,
                )
            }
        }
    }

    if (pickerOpen) {
        LocalAppPickerDialog(
            existingPackages = normalizedPackages.toSet(),
            onDismiss = { pickerOpen = false },
            onSelect = { packageName ->
                onAddPackage(packageName)
                pickerOpen = false
            },
        )
    }
}

/** Prefer the TV entry point on televisions, with the regular launcher activity as fallback. */
internal fun launchLocalApp(context: Context, packageName: String): Boolean {
    val packageManager = context.packageManager
    val launchIntent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
        ?: packageManager.getLaunchIntentForPackage(packageName)
        ?: return false
    return try {
        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

@Composable
private fun LocalAppPickerDialog(
    existingPackages: Set<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var apps by remember { mutableStateOf<List<LocalAppEntry>>(emptyList()) }
    val firstAppFocusRequester = remember { FocusRequester() }
    BackHandler(onBack = onDismiss)
    LaunchedEffect(existingPackages) {
        loading = true
        apps = withContext(Dispatchers.IO) {
            queryLaunchableLocalApps(context.packageManager, context.packageName)
                .filterNot { it.packageName in existingPackages }
        }
        loading = false
    }
    LaunchedEffect(loading, apps) {
        if (!loading && apps.isNotEmpty()) {
            delay(80)
            runCatching { firstAppFocusRequester.requestFocus() }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.library_choose_local_app),
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            when {
                loading -> Row(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.library_local_apps_loading), color = TextMuted)
                }
                apps.isEmpty() -> Text(stringResource(R.string.library_no_launchable_apps), color = TextMuted)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        LocalAppPickerRow(
                            app = app,
                            focusRequester = firstAppFocusRequester.takeIf { app == apps.first() },
                            onSelect = { onSelect(app.packageName) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        containerColor = Panel,
    )
}

@Composable
private fun LocalAppPickerRow(
    app: LocalAppEntry,
    focusRequester: FocusRequester?,
    onSelect: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val bitmap = remember(app.packageName, app.icon) { app.icon.toBitmap().asImageBitmap() }
    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .border(2.dp, if (focused) Color.White else Color.White.copy(alpha = 0.2f), shape)
            .clip(shape)
            .clickable(onClick = onSelect)
            .onPreviewKeyEvent { event ->
                if (isTvActivateKey(event)) {
                    onSelect()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Fit,
        )
        Text(
            app.label,
            color = TextPrimary,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun queryLaunchableLocalApps(packageManager: PackageManager, ownPackage: String): List<LocalAppEntry> {
    val intents = listOf(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
    )
    return intents
        .flatMap { intent -> packageManager.queryLaunchableActivities(intent) }
        .distinctBy { it.activityInfo.packageName }
        .filterNot { it.activityInfo.packageName == ownPackage }
        .map { info ->
            LocalAppEntry(
                packageName = info.activityInfo.packageName,
                label = info.loadLabel(packageManager).toString(),
                icon = info.loadIcon(packageManager),
            )
        }
        .sortedBy { it.label.lowercase(Locale.getDefault()) }
}

@Suppress("DEPRECATION")
private fun PackageManager.queryLaunchableActivities(intent: Intent): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
    } else {
        queryIntentActivities(intent, PackageManager.MATCH_ALL)
    }

@Composable
private fun AddLocalAppTile(
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    topFocusRequester: FocusRequester?,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val haptics = LocalHapticFeedback.current
    val activate = {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onClick()
    }
    Box(Modifier.width(104.dp).height(126.dp)) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .then(
                    topFocusRequester?.let { top ->
                        Modifier.focusProperties { up = top }
                    } ?: Modifier,
                )
                .onFocusChanged { focused = it.isFocused }
                .border(3.dp, if (focused) LocalActiveSelectionColor.current else Color.White.copy(alpha = 0.56f), shape)
                .semantics { role = Role.Button }
                .clickable(onClick = activate)
                .onPreviewKeyEvent { event ->
                    if (isTvActivateKey(event)) {
                        activate()
                        true
                    } else {
                        false
                    }
                }
                .focusable(),
            shape = shape,
            color = PanelAlt.copy(alpha = 0.86f),
        ) {
            Column(
                Modifier.padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("+", color = LocalActiveSelectionColor.current, style = MaterialTheme.typography.displaySmall)
                Text(
                    stringResource(R.string.library_add_local_app),
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        }
        ControllerFocusFrame(
            visible = focused && LocalActiveSelectionEnabled.current,
            cornerRadius = 16.dp,
            tint = LocalActiveSelectionColor.current,
            secondaryTint = LocalActiveSelectionSecondaryColor.current,
        )
    }
}

@Composable
private fun LocalAppTile(
    app: LocalAppEntry,
    onLaunch: () -> Unit,
    onRemove: () -> Unit,
    topFocusRequester: FocusRequester?,
) {
    var tileFocused by remember { mutableStateOf(false) }
    var containsFocus by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val bitmap = remember(app.packageName, app.icon) { app.icon.toBitmap().asImageBitmap() }
    val haptics = LocalHapticFeedback.current
    val launch = {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onLaunch()
    }
    Box(Modifier.width(104.dp).height(126.dp)) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    topFocusRequester?.let { top ->
                        Modifier.focusProperties { up = top }
                    } ?: Modifier,
                )
                .onFocusChanged {
                    tileFocused = it.isFocused
                    containsFocus = it.hasFocus
                }
                .border(3.dp, if (containsFocus) LocalActiveSelectionColor.current else Color.White.copy(alpha = 0.44f), shape)
                .semantics { role = Role.Button }
                .clickable(onClick = launch)
                .onPreviewKeyEvent { event ->
                    if (tileFocused && isTvActivateKey(event)) {
                        launch()
                        true
                    } else {
                        false
                    }
                }
                .focusable(),
            shape = shape,
            color = PanelAlt.copy(alpha = 0.9f),
        ) {
            Box(Modifier.padding(9.dp)) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(13.dp)),
                        contentScale = ContentScale.Fit,
                    )
                    Text(
                        app.label,
                        color = TextPrimary,
                        fontWeight = if (containsFocus) FontWeight.ExtraBold else FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_clear),
                        contentDescription = stringResource(R.string.library_remove_local_app, app.label),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        ControllerFocusFrame(
            visible = containsFocus && LocalActiveSelectionEnabled.current,
            cornerRadius = 16.dp,
            tint = LocalActiveSelectionColor.current,
            secondaryTint = LocalActiveSelectionSecondaryColor.current,
        )
    }
}

@Composable
internal fun rememberDefaultLauncherControl(): DefaultLauncherControl {
    val context = LocalContext.current
    var isDefault by remember { mutableStateOf(isOpenNowDefaultLauncher(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = isOpenNowDefaultLauncher(context)
    }
    return DefaultLauncherControl(
        isDefault = isDefault,
        request = {
            val intent = defaultLauncherRequestIntent(context, isDefault)
            runCatching { launcher.launch(intent) }
                .onFailure {
                    runCatching { launcher.launch(Intent(Settings.ACTION_SETTINGS)) }
                }
            Unit
        },
    )
}

internal data class DefaultLauncherControl(
    val isDefault: Boolean,
    val request: () -> Unit,
)

@Composable
internal fun DefaultLauncherSetting() {
    val control = rememberDefaultLauncherControl()
    ControlRow(onClick = control.request) {
        ControlRowLabels(
            label = stringResource(
                if (control.isDefault) R.string.settings_default_launcher_selected
                else R.string.settings_default_launcher,
            ),
            value = null,
            expandedDescription = stringResource(R.string.settings_default_launcher_desc),
            enabled = true,
            style = controlRowStyle(),
        )
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(
                if (control.isDefault) R.string.settings_default_launcher_manage
                else R.string.settings_default_launcher_action,
            ),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

internal fun isOpenNowDefaultLauncher(context: Context): Boolean {
    val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    return context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo
        ?.packageName == context.packageName
}

internal fun defaultLauncherRequestIntent(context: Context, alreadyDefault: Boolean): Intent {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !alreadyDefault) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true &&
            !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        ) {
            return roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        }
    }
    return Intent(Settings.ACTION_HOME_SETTINGS)
}
