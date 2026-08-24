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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.opencloudgaming.opennow.ui.controls.ControlRow
import com.opencloudgaming.opennow.ui.controls.ControlRowLabels
import com.opencloudgaming.opennow.ui.controls.controlRowStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal data class LocalAppEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable,
)

internal fun normalizeLocalAppPackageNames(packageNames: List<String>): List<String> =
    packageNames.map(String::trim).filter(String::isNotEmpty).distinct()

/**
 * Icon-sized so the shelf reads as a different kind of thing from the poster grid below it.
 *
 * A cloud game is a 628x888 key art poster; an Android app is a launcher icon with no artwork
 * behind it. Blowing that icon up to poster size made every tile look like a mis-cropped game, so
 * the tile is sized to the icon instead and the shelf sits visibly above the catalogue rather than
 * pretending to be the first row of it.
 */
private val LOCAL_APP_ICON_SIZE = 56.dp
private val LOCAL_APP_TILE_SIZE = 72.dp
private val LOCAL_APP_TILE_WIDTH = 78.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LocalAppsShelf(
    packageNames: List<String>,
    collapsed: Boolean,
    onCollapsedChange: (Boolean) -> Unit,
    onAddPackage: (String) -> Unit,
    onRemovePackage: (String) -> Unit,
    horizontalPadding: Dp,
    headerFocusRequester: FocusRequester? = null,
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
    var pendingRemoval by remember { mutableStateOf<LocalAppEntry?>(null) }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LocalAppsSectionHeader(
            collapsed = collapsed,
            appCount = apps.size,
            onToggle = { onCollapsedChange(!collapsed) },
            focusRequester = headerFocusRequester,
            topFocusRequester = topFocusRequester,
            modifier = Modifier.padding(horizontal = horizontalPadding),
        )
        AnimatedVisibility(visible = !collapsed) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = horizontalPadding),
            ) {
                item(key = "add-local-app") {
                    AddLocalAppTile(
                        onClick = { pickerOpen = true },
                        focusRequester = focusRequester,
                        topFocusRequester = headerFocusRequester ?: topFocusRequester,
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
                        onRemove = { pendingRemoval = app },
                        topFocusRequester = headerFocusRequester ?: topFocusRequester,
                    )
                }
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

    pendingRemoval?.let { app ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(R.string.library_remove_local_app, app.label), color = TextPrimary) },
            text = { Text(stringResource(R.string.library_remove_local_app_body), color = TextMuted) },
            confirmButton = {
                TextButton(onClick = {
                    onRemovePackage(app.packageName)
                    pendingRemoval = null
                }) { Text(stringResource(R.string.library_remove_local_app_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text(stringResource(R.string.action_cancel)) }
            },
            containerColor = Panel,
        )
    }
}

/** The whole header is the fold control, so it stays a single focus stop on a controller. */
@Composable
private fun LocalAppsSectionHeader(
    collapsed: Boolean,
    appCount: Int,
    onToggle: () -> Unit,
    focusRequester: FocusRequester?,
    topFocusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val haptics = LocalOpenNowHaptics.current
    val shape = RoundedCornerShape(10.dp)
    val toggle = {
        haptics?.play(HapticCue.Activate)
        onToggle()
    }
    // One chevron drawable, rotated: right when folded, down when open.
    val chevronRotation by animateFloatAsState(
        targetValue = if (collapsed) 0f else 90f,
        label = "local-apps-chevron",
    )
    val description = stringResource(
        if (collapsed) R.string.library_local_apps_show else R.string.library_local_apps_hide,
        appCount,
    )
    Row(
        modifier
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .then(
                topFocusRequester?.let { top -> Modifier.focusProperties { up = top } } ?: Modifier,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusMoveHaptics()
            .clip(shape)
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .clickable(onClick = toggle)
            .onPreviewKeyEvent { event ->
                if (isTvActivateKey(event)) {
                    toggle()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .border(2.dp, if (focused) LocalSelectionTintColor.current else Color.Transparent, shape)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = if (focused) Color.White else TextMuted,
            modifier = Modifier.size(18.dp).rotate(chevronRotation),
        )
        Text(
            stringResource(R.string.library_local_apps),
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
        if (appCount > 0) {
            Text(
                appCount.toString(),
                color = TextMuted,
                style = MaterialTheme.typography.labelMedium,
            )
        }
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
            .border(
                2.dp,
                cinemaBorderColor(LocalAbsoluteCinemaEffects.current, LocalActiveSelectionColor.current),
                shape,
            )
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
    val shape = RoundedCornerShape(18.dp)
    val haptics = LocalOpenNowHaptics.current
    val activate = {
        haptics?.play(HapticCue.Activate)
        onClick()
    }
    LocalAppTileFrame(label = stringResource(R.string.library_add_local_app), focused = focused) {
        // The focus frame is a sibling of the clipped tile, not a child: inside it, the glow would
        // be cut off at the tile's own corners.
        Box(Modifier.size(LOCAL_APP_TILE_SIZE)) {
            Box(
                Modifier
                    .matchParentSize()
                    .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                    .then(
                        topFocusRequester?.let { top -> Modifier.focusProperties { up = top } } ?: Modifier,
                    )
                    .onFocusChanged { focused = it.isFocused }
                    .focusMoveHaptics()
                    .clip(shape)
                    .background(PanelAlt.copy(alpha = 0.86f))
                    .border(
                        2.dp,
                        cinemaBorderColor(LocalAbsoluteCinemaEffects.current, LocalActiveSelectionColor.current),
                        shape,
                    )
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
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "+",
                    color = LocalSelectionTintColor.current,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            ControllerFocusFrame(
                visible = focused && LocalActiveSelectionEnabled.current,
                cornerRadius = 18.dp,
                tint = LocalActiveSelectionColor.current,
                secondaryTint = LocalActiveSelectionSecondaryColor.current,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LocalAppTile(
    app: LocalAppEntry,
    onLaunch: () -> Unit,
    onRemove: () -> Unit,
    topFocusRequester: FocusRequester?,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    val bitmap = remember(app.packageName, app.icon) { app.icon.toBitmap().asImageBitmap() }
    val haptics = LocalOpenNowHaptics.current
    val launch = {
        haptics?.play(HapticCue.Activate)
        onLaunch()
    }
    val remove = {
        haptics?.play(HapticCue.Boundary)
        onRemove()
    }
    LocalAppTileFrame(label = app.label, focused = focused) {
        Box(Modifier.size(LOCAL_APP_TILE_SIZE)) {
            Box(
                Modifier
                    .matchParentSize()
                    .then(
                        topFocusRequester?.let { top -> Modifier.focusProperties { up = top } } ?: Modifier,
                    )
                    .onFocusChanged { focused = it.isFocused }
                    .focusMoveHaptics()
                    .clip(shape)
                    .background(PanelAlt.copy(alpha = 0.72f))
                    .border(
                        2.dp,
                        cinemaBorderColor(LocalAbsoluteCinemaEffects.current, LocalActiveSelectionColor.current),
                        shape,
                    )
                    .semantics { role = Role.Button }
                    // Long-press removes, matching how a launcher un-pins an icon, and keeps the
                    // tile free of a delete affordance that would crowd an icon this size.
                    .combinedClickable(
                        onClick = launch,
                        onLongClick = remove,
                        onLongClickLabel = stringResource(R.string.library_remove_local_app, app.label),
                    )
                    .onPreviewKeyEvent { event ->
                        when {
                            !focused -> false
                            isTvActivateKey(event) -> {
                                launch()
                                true
                            }
                            // Y removes, the same button the catalogue cards use for their
                            // secondary action, so a controller never needs the long-press.
                            event.type == KeyEventType.KeyUp && event.key == Key.ButtonY -> {
                                remove()
                                true
                            }
                            else -> false
                        }
                    }
                    .focusable(),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.size(LOCAL_APP_ICON_SIZE).clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Fit,
                )
            }
            ControllerFocusFrame(
                visible = focused && LocalActiveSelectionEnabled.current,
                cornerRadius = 18.dp,
                tint = LocalActiveSelectionColor.current,
                secondaryTint = LocalActiveSelectionSecondaryColor.current,
            )
        }
    }
}

/** Caption below the icon rather than inside it, so the icon keeps its full square. */
@Composable
private fun LocalAppTileFrame(
    label: String,
    focused: Boolean,
    tile: @Composable () -> Unit,
) {
    Column(
        Modifier.width(LOCAL_APP_TILE_WIDTH),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tile()
        Text(
            label,
            color = if (focused) TextPrimary else TextMuted,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
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
