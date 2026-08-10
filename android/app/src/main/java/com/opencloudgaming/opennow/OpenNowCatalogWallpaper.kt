package com.opencloudgaming.opennow

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.opencloudgaming.opennow.ui.theme.OpenNowPalette

internal sealed interface CatalogWallpaperSelection {
    data class BuiltIn(val preset: CatalogBackgroundPreset) : CatalogWallpaperSelection
    data class Custom(val source: String) : CatalogWallpaperSelection
}

internal fun catalogWallpaperSelection(
    preset: CatalogBackgroundPreset,
    customSource: String?,
): CatalogWallpaperSelection =
    customSource
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let(CatalogWallpaperSelection::Custom)
        ?: CatalogWallpaperSelection.BuiltIn(preset)

internal fun shouldShowCatalogWallpaper(settings: AppSettings): Boolean =
    settings.nerdCatalogBackground

@Composable
internal fun CatalogWallpaperBackdrop(
    settings: AppSettings,
    tvProfile: Boolean,
    width: Dp,
    height: Dp,
) {
    val showBackdrop = shouldShowCatalogWallpaper(settings)
    if (!showBackdrop) {
        return
    }
    val wallpaper = catalogWallpaperSelection(
        preset = settings.catalogBackgroundPreset,
        customSource = settings.nerdCatalogBackgroundUri,
    )
    val scrimAlpha = when {
        tvProfile -> 0.48f
        width > height -> 0.28f
        else -> 0.36f
    }
    Box(Modifier.fillMaxSize().clipToBounds()) {
        when (wallpaper) {
            is CatalogWallpaperSelection.BuiltIn -> {
                CatalogBuiltInWallpaperBackdrop(wallpaper.preset, Modifier.matchParentSize())
            }
            is CatalogWallpaperSelection.Custom -> {
                val fallbackPainter = painterResource(settings.catalogBackgroundPreset.drawableRes)
                AsyncImage(
                    model = imageDataForSource(wallpaper.source),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = fallbackPainter,
                    error = fallbackPainter,
                    fallback = fallbackPainter,
                )
            }
        }
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = scrimAlpha)),
        )
    }
}

private val CatalogBackgroundPreset.drawableRes: Int
    get() = when (this) {
        CatalogBackgroundPreset.ColorfulAbstract -> R.drawable.catalog_colorful_abstract_background
        CatalogBackgroundPreset.Original -> R.drawable.catalog_default_background
        CatalogBackgroundPreset.AbsoluteCinema -> R.drawable.catalog_absolute_cinema_background
    }

@Composable
private fun CatalogBuiltInWallpaperBackdrop(
    preset: CatalogBackgroundPreset,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(preset.drawableRes),
        contentDescription = null,
        modifier = modifier.background(OpenNowPalette.WallpaperBackdrop),
        contentScale = ContentScale.Crop,
    )
}
