package com.opencloudgaming.opennow

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogBackgroundTest {
    @Test
    fun `blank custom source selects only the bundled default`() {
        assertEquals(CatalogWallpaperSelection.Default, catalogWallpaperSelection(null))
        assertEquals(CatalogWallpaperSelection.Default, catalogWallpaperSelection("   "))
    }

    @Test
    fun `custom source replaces the bundled default`() {
        assertEquals(
            CatalogWallpaperSelection.Custom("file:///data/user/0/opennow/files/custom"),
            catalogWallpaperSelection("  file:///data/user/0/opennow/files/custom  "),
        )
    }

    @Test
    fun `managed background cleanup cannot escape app files directory`() {
        val filesDir = Files.createTempDirectory("catalog-background-test").toFile()
        val outsideDir = Files.createTempDirectory("catalog-background-outside").toFile()
        try {
            assertTrue(
                isManagedCatalogBackgroundImageFile(
                    filesDir,
                    File(filesDir, CATALOG_BACKGROUND_IMAGE_FILE_PREFIX),
                ),
            )
            assertTrue(
                isManagedCatalogBackgroundImageFile(
                    filesDir,
                    File(filesDir, "$CATALOG_BACKGROUND_IMAGE_FILE_PREFIX-unique"),
                ),
            )
            assertFalse(
                isManagedCatalogBackgroundImageFile(
                    filesDir,
                    File(filesDir, "unrelated-image"),
                ),
            )
            assertFalse(
                isManagedCatalogBackgroundImageFile(
                    filesDir,
                    File(outsideDir, "$CATALOG_BACKGROUND_IMAGE_FILE_PREFIX-unique"),
                ),
            )
        } finally {
            filesDir.deleteRecursively()
            outsideDir.deleteRecursively()
        }
    }
}
