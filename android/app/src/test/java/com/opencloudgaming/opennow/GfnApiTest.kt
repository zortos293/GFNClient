package com.opencloudgaming.opennow

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GfnApiTest {
    @Test
    fun catalogArtworkUsesGameBoxArtForMobileAndTvCards() {
        val artwork = catalogCardArtwork(
            keyArt = "key-art",
            gameBoxArt = "game-box-art",
            heroImage = "hero-image",
            tvBanner = "tv-banner",
        )

        assertEquals("game-box-art", artwork.mobileImageUrl)
        assertEquals("game-box-art", artwork.tvImageUrl)
    }

    @Test
    fun catalogArtworkDoesNotFallBackToLandscapeArtOnMobile() {
        val artwork = catalogCardArtwork(
            keyArt = "key-art",
            gameBoxArt = null,
            heroImage = "hero-image",
            tvBanner = "tv-banner",
        )

        assertNull(artwork.mobileImageUrl)
        assertEquals("key-art", artwork.tvImageUrl)
    }

    @Test
    fun catalogScreenshotsPreserveDistinctNonBlankImages() {
        val images = buildJsonObject {
            putJsonArray("SCREENSHOTS") {
                add(JsonPrimitive(" screenshot-one "))
                add(JsonPrimitive(""))
                add(JsonPrimitive("screenshot-two"))
                add(JsonPrimitive("screenshot-one"))
            }
        }

        assertEquals(
            listOf("screenshot-one", "screenshot-two"),
            catalogScreenshotUrls(images),
        )
    }

    @Test
    fun catalogDescriptionSupportsBrowseAndMetadataFieldNames() {
        val browseApp = buildJsonObject {
            put("shortDescription", JsonPrimitive("Browse description"))
        }
        val metadataApp = buildJsonObject {
            put("description", JsonPrimitive("Metadata description"))
            put("shortDescription", JsonPrimitive("Fallback description"))
        }

        assertEquals("Browse description", catalogGameDescription(browseApp))
        assertEquals("Metadata description", catalogGameDescription(metadataApp))
    }

    @Test
    fun canonicalizesOldGamesGraphQlHost() {
        val url = canonicalizeGfnRequestUrl(
            "https://games.geforcenow.com/graphql?requestType=panels%2FMainV2".toHttpUrl(),
        )

        assertEquals("https", url.scheme)
        assertEquals("games.geforce.com", url.host)
        assertEquals("/graphql", url.encodedPath)
        assertEquals("panels/MainV2", url.queryParameter("requestType"))
    }

    @Test
    fun leavesCanonicalGamesGraphQlHostUnchanged() {
        val source = "https://games.geforce.com/graphql".toHttpUrl()

        assertEquals(source, canonicalizeGfnRequestUrl(source))
    }

    @Test
    fun claimRequestWithoutSettingsDoesNotRenegotiateMonitorSettings() {
        val body = buildMinimalClaimRequestBody(appId = "123", deviceId = "device")
        val sessionRequestData = body.getValue("sessionRequestData").jsonObject
        val metadata = sessionRequestData.getValue("metaData").jsonArray

        assertEquals(2, body.getValue("action").jsonPrimitive.int)
        assertEquals(123, sessionRequestData.getValue("appId").jsonPrimitive.int)
        assertFalse(sessionRequestData.containsKey("clientRequestMonitorSettings"))
        assertFalse(sessionRequestData.containsKey("requestedStreamingFeatures"))
        assertTrue(metadata.none { item ->
            item.jsonObject["key"]?.jsonPrimitive?.contentOrNull == "clientPhysicalResolution"
        })
    }

    @Test
    fun claimRequestCarriesCommonResolutionAspectAndCodecMatrix() {
        val cases = listOf(
            Triple("1280x720", "16:9", 1280 to 720),
            Triple("1920x1080", "16:9", 1920 to 1080),
            Triple("1920x1200", "16:10", 1920 to 1200),
            Triple("1024x768", "4:3", 1024 to 768),
            Triple("1680x720", "21:9", 1680 to 720),
            Triple("2560x1080", "21:9", 2560 to 1080),
        )

        for ((resolution, aspectRatio, pixels) in cases) {
            for (codec in VideoCodec.entries) {
                val settings = StreamSettings(
                    resolution = resolution,
                    aspectRatio = aspectRatio,
                    fps = 60,
                    maxBitrateMbps = 75,
                    codec = codec,
                    colorQuality = if (codec == VideoCodec.H264) ColorQuality.EightBit420 else ColorQuality.TenBit420,
                )
                val body = buildMinimalClaimRequestBody(appId = "123", deviceId = "device", settings = settings)
                val sessionRequestData = body.getValue("sessionRequestData").jsonObject
                val metadata = sessionRequestData.getValue("metaData").jsonArray
                val monitor = sessionRequestData.getValue("clientRequestMonitorSettings").jsonArray.single().jsonObject
                val features = sessionRequestData.getValue("requestedStreamingFeatures").jsonObject
                val signature = metadata.firstNotNullOfOrNull { item ->
                    item.jsonObject.takeIf {
                        it["key"]?.jsonPrimitive?.contentOrNull == OPENNOW_STREAM_SETTINGS_METADATA_KEY
                    }?.get("value")?.jsonPrimitive?.contentOrNull
                }
                val physicalResolution = metadata.firstNotNullOfOrNull { item ->
                    item.jsonObject.takeIf {
                        it["key"]?.jsonPrimitive?.contentOrNull == "clientPhysicalResolution"
                    }?.get("value")?.jsonPrimitive?.contentOrNull
                }?.let { OpenNowJson.parseToJsonElement(it).jsonObject }

                assertEquals("$resolution $codec signature", streamSettingsSessionSignature(settings), signature)
                assertEquals("$resolution $codec width", pixels.first, monitor.getValue("widthInPixels").jsonPrimitive.int)
                assertEquals("$resolution $codec height", pixels.second, monitor.getValue("heightInPixels").jsonPrimitive.int)
                assertEquals("$resolution $codec fps", 60, monitor.getValue("framesPerSecond").jsonPrimitive.int)
                assertEquals("$resolution $codec bit depth", if (codec == VideoCodec.H264) 0 else 10, features.getValue("bitDepth").jsonPrimitive.int)
                assertEquals(false, features.getValue("reflex").jsonPrimitive.boolean)
                assertEquals(pixels.first, physicalResolution?.getValue("horizontalPixels")?.jsonPrimitive?.int)
                assertEquals(pixels.second, physicalResolution?.getValue("verticalPixels")?.jsonPrimitive?.int)
            }
        }
    }

    @Test
    fun ultrawideMetadataKeepsPhysicalDisplaySeparateFromStreamResolution() {
        val settings = StreamSettings(
            resolution = "1680x720",
            aspectRatio = "21:9",
            fps = 60,
            codec = VideoCodec.H264,
            colorQuality = ColorQuality.EightBit420,
        )

        val body = buildMinimalClaimRequestBody(
            appId = "123",
            deviceId = "device",
            settings = settings,
            physicalDisplayResolution = 1920 to 1080,
        )
        val sessionRequestData = body.getValue("sessionRequestData").jsonObject
        val monitor = sessionRequestData
            .getValue("clientRequestMonitorSettings").jsonArray
            .single().jsonObject
        val physicalResolution = sessionRequestData
            .getValue("metaData").jsonArray
            .firstNotNullOf { item ->
                item.jsonObject.takeIf {
                    it["key"]?.jsonPrimitive?.contentOrNull == "clientPhysicalResolution"
                }?.get("value")?.jsonPrimitive?.contentOrNull
            }
            .let { OpenNowJson.parseToJsonElement(it).jsonObject }

        assertEquals(1680, monitor.getValue("widthInPixels").jsonPrimitive.int)
        assertEquals(720, monitor.getValue("heightInPixels").jsonPrimitive.int)
        assertEquals(0, monitor.getValue("dpi").jsonPrimitive.int)
        assertFalse(monitor.containsKey("monitorId"))
        assertFalse(monitor.containsKey("positionX"))
        assertFalse(monitor.containsKey("positionY"))
        assertEquals(JsonNull, monitor.getValue("displayData"))
        assertEquals(JsonNull, monitor.getValue("hdr10PlusGamingData"))
        assertEquals("browser", sessionRequestData.getValue("clientPlatformName").jsonPrimitive.content)
        assertEquals(2, sessionRequestData.getValue("appLaunchMode").jsonPrimitive.int)
        assertEquals(false, sessionRequestData.getValue("enablePersistingInGameSettings").jsonPrimitive.boolean)
        assertEquals(1920, physicalResolution.getValue("horizontalPixels").jsonPrimitive.int)
        assertEquals(1080, physicalResolution.getValue("verticalPixels").jsonPrimitive.int)
    }

    @Test
    fun claimRequestExplicitlyMarksSdrColorMetadata() {
        val settings = StreamSettings(
            resolution = "1920x1080",
            codec = VideoCodec.H265,
            colorQuality = ColorQuality.EightBit420,
            hdrEnabled = false,
        )

        val sessionRequestData = buildMinimalClaimRequestBody("123", "device", settings)
            .getValue("sessionRequestData").jsonObject
        val monitor = sessionRequestData
            .getValue("clientRequestMonitorSettings").jsonArray.single().jsonObject
        val features = sessionRequestData.getValue("requestedStreamingFeatures").jsonObject

        assertEquals(0, monitor.getValue("sdrHdrMode").jsonPrimitive.int)
        assertEquals(JsonNull, monitor.getValue("displayData"))
        assertEquals(JsonNull, monitor.getValue("hdr10PlusGamingData"))
        assertEquals(0, features.getValue("bitDepth").jsonPrimitive.int)
        assertEquals(false, features.getValue("trueHdr").jsonPrimitive.boolean)
        assertEquals(2, features.getValue("sdrColorSpace").jsonPrimitive.int)
        assertEquals(0, features.getValue("hdrColorSpace").jsonPrimitive.int)
        assertEquals(0, sessionRequestData.getValue("sdrHdrMode").jsonPrimitive.int)
        assertEquals(JsonNull, sessionRequestData.getValue("clientDisplayHdrCapabilities"))
    }

    @Test
    fun claimRequestExplicitlyMarksHdrColorMetadata() {
        val settings = StreamSettings(
            resolution = "1920x1080",
            codec = VideoCodec.H265,
            colorQuality = ColorQuality.TenBit420,
            hdrEnabled = true,
        )

        val sessionRequestData = buildMinimalClaimRequestBody("123", "device", settings)
            .getValue("sessionRequestData").jsonObject
        val monitor = sessionRequestData
            .getValue("clientRequestMonitorSettings").jsonArray.single().jsonObject
        val features = sessionRequestData.getValue("requestedStreamingFeatures").jsonObject

        assertEquals(1, monitor.getValue("sdrHdrMode").jsonPrimitive.int)
        assertEquals(1000, monitor.getValue("displayData").jsonObject
            .getValue("desiredContentMaxLuminance").jsonPrimitive.int)
        assertEquals(true, features.getValue("trueHdr").jsonPrimitive.boolean)
        assertEquals(10, features.getValue("bitDepth").jsonPrimitive.int)
        assertEquals(2, features.getValue("sdrColorSpace").jsonPrimitive.int)
        assertEquals(4, features.getValue("hdrColorSpace").jsonPrimitive.int)
        assertEquals(1, sessionRequestData.getValue("sdrHdrMode").jsonPrimitive.int)
        assertTrue(sessionRequestData.getValue("clientDisplayHdrCapabilities") is kotlinx.serialization.json.JsonObject)
    }

    @Test
    fun claimRequestCarriesRequested120FpsMonitorSetting() {
        val settings = StreamSettings(
            resolution = "1920x1080",
            aspectRatio = "16:9",
            fps = 120,
            maxBitrateMbps = 75,
            codec = VideoCodec.H264,
            colorQuality = ColorQuality.EightBit420,
        )

        val body = buildMinimalClaimRequestBody(appId = "123", deviceId = "device", settings = settings)
        val monitor = body
            .getValue("sessionRequestData").jsonObject
            .getValue("clientRequestMonitorSettings").jsonArray
            .single().jsonObject

        assertEquals(120, monitor.getValue("framesPerSecond").jsonPrimitive.int)
    }

    @Test
    fun claimRequestCarriesRequested360FpsMonitorSetting() {
        val settings = StreamSettings(
            resolution = "1920x1080",
            aspectRatio = "16:9",
            fps = 360,
            maxBitrateMbps = 75,
            codec = VideoCodec.AV1,
            colorQuality = ColorQuality.EightBit420,
        )

        val body = buildMinimalClaimRequestBody(appId = "123", deviceId = "device", settings = settings)
        val monitor = body
            .getValue("sessionRequestData").jsonObject
            .getValue("clientRequestMonitorSettings").jsonArray
            .single().jsonObject

        assertEquals(360, monitor.getValue("framesPerSecond").jsonPrimitive.int)
    }

    @Test
    fun claimRequestDoesNotAdvertiseAv1Chroma444() {
        val settings = StreamSettings(
            resolution = "1920x1080",
            aspectRatio = "16:9",
            fps = 60,
            codec = VideoCodec.AV1,
            colorQuality = ColorQuality.EightBit444,
        )

        val body = buildMinimalClaimRequestBody(appId = "123", deviceId = "device", settings = settings)
        val sessionRequestData = body.getValue("sessionRequestData").jsonObject
        val features = sessionRequestData.getValue("requestedStreamingFeatures").jsonObject
        val signature = sessionRequestData.getValue("metaData").jsonArray.firstNotNullOfOrNull { item ->
            item.jsonObject.takeIf {
                it["key"]?.jsonPrimitive?.contentOrNull == OPENNOW_STREAM_SETTINGS_METADATA_KEY
            }?.get("value")?.jsonPrimitive?.contentOrNull
        }

        assertEquals(0, features.getValue("chromaFormat").jsonPrimitive.int)
        assertTrue(signature?.contains("color=EightBit420") == true)
    }

    @Test
    fun activeSessionMonitorSettingsPreferActualTopLevelMonitor() {
        val session = OpenNowJson.parseToJsonElement(
            """
            {
              "sessionRequestData": {
                "clientRequestMonitorSettings": [
                  { "widthInPixels": 1680, "heightInPixels": 720, "framesPerSecond": 60 }
                ]
              },
              "monitorSettings": [
                { "widthInPixels": 1366, "heightInPixels": 768, "framesPerSecond": 60 }
              ]
            }
            """.trimIndent(),
        ).jsonObject
        val monitor = requireNotNull(activeSessionMonitorSettings(session))

        assertEquals(1366, monitor.getValue("widthInPixels").jsonPrimitive.int)
        assertEquals(768, monitor.getValue("heightInPixels").jsonPrimitive.int)
    }

    @Test
    fun activeSessionMonitorSettingsFallsBackToTopLevelMonitor() {
        val session = OpenNowJson.parseToJsonElement(
            """
            {
              "monitorSettings": [
                { "widthInPixels": 1366, "heightInPixels": 768, "framesPerSecond": 60 }
              ]
            }
            """.trimIndent(),
        ).jsonObject
        val monitor = requireNotNull(activeSessionMonitorSettings(session))

        assertEquals(1366, monitor.getValue("widthInPixels").jsonPrimitive.int)
        assertEquals(768, monitor.getValue("heightInPixels").jsonPrimitive.int)
    }

    @Test
    fun activeSessionSettingsSignatureReadsSessionRequestMetadata() {
        val settings = StreamSettings(resolution = "1680x720", aspectRatio = "21:9", fps = 60, maxBitrateMbps = 150, codec = VideoCodec.H265)
        val signature = streamSettingsSessionSignature(settings)
        val session = OpenNowJson.parseToJsonElement(
            """
            {
              "sessionRequestData": {
                "metaData": [
                  { "key": "$OPENNOW_STREAM_SETTINGS_METADATA_KEY", "value": "$signature" }
                ]
              }
            }
            """.trimIndent(),
        ).jsonObject

        assertEquals(signature, activeSessionSettingsSignature(session))
    }

    @Test
    fun providerLaunchBaseUsesSingleAdvertisedAllianceRegion() {
        val base = providerLaunchBaseUrl(
            providerBase = "https://prod.yes.geforcenow.nvidiagrid.net/",
            regions = listOf(StreamRegion("MY YES", "https://my-yes.yes.geforcenow.nvidiagrid.net")),
        )

        assertEquals("https://my-yes.yes.geforcenow.nvidiagrid.net", base)
    }

    @Test
    fun providerLaunchBaseDoesNotGuessWhenProviderHasMultipleRegions() {
        val base = providerLaunchBaseUrl(
            providerBase = "https://prod.example.geforcenow.nvidiagrid.net/",
            regions = listOf(
                StreamRegion("A", "https://a.example.geforcenow.nvidiagrid.net"),
                StreamRegion("B", "https://b.example.geforcenow.nvidiagrid.net"),
            ),
        )

        assertEquals("https://prod.example.geforcenow.nvidiagrid.net", base)
    }

    @Test
    fun providerLaunchBaseDoesNotRewriteCloudmatchRoot() {
        val base = providerLaunchBaseUrl(
            providerBase = "https://prod.cloudmatchbeta.nvidiagrid.net/",
            regions = listOf(StreamRegion("NP-AMS-06", "https://np-ams-06.cloudmatchbeta.nvidiagrid.net")),
        )

        assertEquals("https://prod.cloudmatchbeta.nvidiagrid.net", base)
    }

    @Test
    fun usableSessionHostRejectsPlaceholderAllianceHosts() {
        assertNull(usableSessionHost(".yes.geforcenow.nvidiagrid.net"))
        assertNull(usableSessionHost("bad..host"))
        assertEquals("183-78-14-238.yes.geforcenow.nvidiagrid.net", usableSessionHost("183-78-14-238.yes.geforcenow.nvidiagrid.net"))
    }

    @Test
    fun diagnosticLogPayloadRedactsSensitiveJsonFields() {
        val exported = sanitizeDiagnosticLogPayload(
            """
            {
              "session": {
                "sessionId": "session-123",
                "queuePosition": 4,
                "iceServerConfiguration": {
                  "iceServers": [
                    {
                      "urls": ["turn:example.invalid"],
                      "username": "ice-user",
                      "credential": "ice-secret"
                    }
                  ]
                }
              },
              "accessToken": "token-value",
              "email": "player@example.invalid"
            }
            """.trimIndent(),
        )

        assertTrue(exported.contains("\"sessionId\": \"session-123\""))
        assertTrue(exported.contains("\"queuePosition\": 4"))
        assertFalse(exported.contains("ice-secret"))
        assertFalse(exported.contains("token-value"))
        assertFalse(exported.contains("player@example.invalid"))
        assertTrue(exported.contains("\"credential\": \"[redacted]\""))
        assertTrue(exported.contains("\"accessToken\": \"[redacted]\""))
    }

    @Test
    fun diagnosticLogPayloadRedactsDeviceLoginAndDeviceIds() {
        val exported = sanitizeDiagnosticLogPayload(
            """
            {
              "device_code": "device-secret",
              "user_code": "ABCD-EFGH",
              "verification_uri_complete": "https://login.example/activate?user_code=ABCD-EFGH",
              "deviceHashId": "stable-device-id",
              "statusCode": 1
            }
            """.trimIndent(),
        )

        assertFalse(exported.contains("device-secret"))
        assertFalse(exported.contains("ABCD-EFGH"))
        assertFalse(exported.contains("stable-device-id"))
        assertTrue(exported.contains("\"device_code\": \"[redacted]\""))
        assertTrue(exported.contains("\"user_code\": \"[redacted]\""))
        assertTrue(exported.contains("\"deviceHashId\": \"[redacted]\""))
        assertTrue(exported.contains("\"statusCode\": 1"))
    }

    @Test
    fun diagnosticUrlRedactsSensitiveQueryParameters() {
        val exported = redactDiagnosticUrl("https://login.example/token?code=abc123&device_id=device-1&requestType=session")

        assertFalse(exported.contains("abc123"))
        assertFalse(exported.contains("device-1"))
        assertTrue(exported.contains("code=%5Bredacted%5D"))
        assertTrue(exported.contains("device_id=%5Bredacted%5D"))
        assertTrue(exported.contains("requestType=session"))
    }

    @Test
    fun diagnosticLogPayloadRedactsFormEncodedAuthFields() {
        val exported = sanitizeDiagnosticLogPayload(
            "grant_type=client_token&client_token=client-secret&client_id=public-client&sub=user-123",
        )

        assertFalse(exported.contains("client-secret"))
        assertFalse(exported.contains("user-123"))
        assertTrue(exported.contains("client_token=[redacted]"))
        assertTrue(exported.contains("sub=[redacted]"))
        assertTrue(exported.contains("client_id=public-client"))
    }

}
