import XCTest
import UIKit
@testable import OpenNOWiOS

final class OpenNOWiOSParityTests: XCTestCase {
    func testAccountSnapshotRoundTripsSubscriptionStorageAndConnections() throws {
        let storage = StorageAddon(
            type: "STORAGE",
            sizeGb: 200,
            usedGb: 75,
            regionName: "Malaysia",
            regionCode: "MY-KUL",
            status: "OK",
            subType: "PERMANENT_STORAGE",
            autoPayEnabled: true
        )
        let snapshot = CachedAccountSnapshot(
            schemaVersion: 1,
            cachedAt: 123,
            membershipTier: "ULTIMATE",
            subscription: SubscriptionSnapshot(
                membershipTier: "ULTIMATE",
                subscriptionType: "PAID",
                subscriptionSubType: "UNLIMITED",
                isGamePlayAllowed: true,
                isUnlimited: true,
                remainingHours: 80,
                totalHours: 100,
                storageAddon: storage
            ),
            accountConnectors: [
                AccountConnector(
                    store: "STEAM",
                    label: "Steam",
                    supported: true,
                    required: false,
                    userDisplayName: "Player",
                    userIdentifier: "steam-user",
                    expiresInSeconds: nil,
                    syncedGameCount: 40,
                    syncState: "DONE",
                    syncDate: nil
                )
            ],
            availableRegions: [StreamRegion(name: "Malaysia", url: "https://my.example/")],
            vpcId: "MY-YES"
        )

        let decoded = try JSONDecoder().decode(
            CachedAccountSnapshot.self,
            from: JSONEncoder().encode(snapshot)
        )

        XCTAssertEqual(decoded, snapshot)
        XCTAssertEqual(decoded.subscription?.storageAddon?.usedGb, 75)
        XCTAssertEqual(decoded.accountConnectors.first?.store, "STEAM")
    }

    func testAccountErrorsPreferParsedServerMessageOverRawJSON() {
        let error = NSError(
            domain: "OpenNOW.Auth",
            code: 401,
            userInfo: [
                NSLocalizedDescriptionKey: #"{"errors":[{"errorMessage":"This saved account needs a fresh sign-in."}]}"#
            ]
        )

        XCTAssertEqual(
            OpenNOWErrorPresenter.message(for: error, fallback: "Sign-in failed."),
            "This saved account needs a fresh sign-in."
        )
    }

    func testAccountErrorsHumanizeGFNStatusCodes() {
        let error = NSError(
            domain: "OpenNOW.Account",
            code: 1,
            userInfo: [NSLocalizedDescriptionKey: #"{"requestStatus":{"statusDescription":"AUTHENTICATION_REQUIRED_STATUS"}}"#]
        )

        XCTAssertEqual(
            OpenNOWErrorPresenter.message(for: error, fallback: "Refresh failed."),
            "Authentication Required"
        )
    }

    func testAccountTimeoutErrorIsActionable() {
        let error = NSError(domain: NSURLErrorDomain, code: NSURLErrorTimedOut)

        XCTAssertEqual(
            OpenNOWErrorPresenter.message(for: error, fallback: "Refresh failed."),
            "The request timed out. Check your connection and try again."
        )
    }

    func testPlaceholderSDPEndpointsUseSignalingEndpointWithoutMediaMetadata() {
        let offer = remoteOffer(address: "0.0.0.0", port: 47_998)

        let fixed = NativeStreamSDP.fixServerIP(
            in: offer,
            serverIP: "66-22-131-132.cloudmatchbeta.nvidiagrid.net"
        )

        XCTAssertTrue(fixed.contains("c=IN IP4 0.0.0.0"))
        XCTAssertTrue(fixed.contains("a=candidate:1 1 udp 2122260223 66.22.131.132 47998 typ host"))
    }

    func testPrivateSDPEndpointsUseCloudMatchMediaEndpoint() {
        let offer = remoteOffer(address: "10.0.175.0", port: 47_998)

        let fixed = NativeStreamSDP.fixServerEndpoint(
            in: offer,
            serverIP: "183-78-14-231.yes.geforcenow.nvidiagrid.net",
            mediaIP: "183-78-14-231.yes.geforcenow.nvidiagrid.net",
            mediaPort: 14_317
        )

        XCTAssertTrue(fixed.contains("c=IN IP4 10.0.175.0"))
        XCTAssertTrue(fixed.contains("a=candidate:1 1 udp 2122260223 183-78-14-231.yes.geforcenow.nvidiagrid.net 14317 typ host"))
    }

    func testPrivateSDPEndpointsStayAdvertisedWithoutMediaMetadata() {
        let offer = remoteOffer(address: "10.0.175.0", port: 47_998)

        let fixed = NativeStreamSDP.fixServerIP(
            in: offer,
            serverIP: "183-78-14-231.yes.geforcenow.nvidiagrid.net"
        )

        XCTAssertEqual(fixed, offer)
    }

    func testCarrierGradeNATSDPEndpointsUseCloudMatchMediaEndpoint() {
        let offer = remoteOffer(address: "100.96.10.4", port: 47_998)

        let fixed = NativeStreamSDP.fixServerEndpoint(
            in: offer,
            serverIP: "183-78-14-231.yes.geforcenow.nvidiagrid.net",
            mediaIP: "183.78.14.231",
            mediaPort: 19_353
        )

        XCTAssertTrue(fixed.contains("c=IN IP4 100.96.10.4"))
        XCTAssertTrue(fixed.contains("a=candidate:1 1 udp 2122260223 183.78.14.231 19353 typ host"))
    }

    func testPublicSDPEndpointsUseExplicitCloudMatchMediaEndpoint() {
        let offer = remoteOffer(address: "203.0.113.10", port: 47_998)

        let fixed = NativeStreamSDP.fixServerEndpoint(
            in: offer,
            serverIP: "183-78-14-231.yes.geforcenow.nvidiagrid.net",
            mediaIP: "183.78.14.231",
            mediaPort: 19_353
        )

        XCTAssertTrue(fixed.contains("c=IN IP4 203.0.113.10"))
        XCTAssertTrue(fixed.contains("a=candidate:1 1 udp 2122260223 183.78.14.231 19353 typ host"))
    }

    func testTrickledRemoteCandidateUsesCloudMatchMediaEndpoint() {
        let candidate = "candidate:2 1 udp 2122260223 100.96.10.4 47998 typ host generation 0"

        let fixed = NativeStreamSDP.rewriteIceCandidateEndpoint(
            candidate,
            mediaIP: "183.78.14.231",
            mediaPort: 19_353
        )

        XCTAssertEqual(
            fixed,
            "candidate:2 1 udp 2122260223 183.78.14.231 19353 typ host generation 0"
        )
    }

    func testExplicitMediaEndpointDoesNotDependOnSignalingHostnameShape() {
        let offer = remoteOffer(address: "100.96.10.4", port: 47_998)

        let fixed = NativeStreamSDP.fixServerEndpoint(
            in: offer,
            serverIP: "npa-yes-kul-01.yes.geforcenow.nvidiagrid.net",
            mediaIP: "183.78.14.231",
            mediaPort: 19_353
        )

        XCTAssertTrue(fixed.contains("a=candidate:1 1 udp 2122260223 183.78.14.231 19353 typ host"))
    }

    func testAndroidInputHandshakeIsAppliedAndPrimesReliableChannel() {
        let bridge = NativeStreamInputBridge()
        let sink = RecordingNativeStreamInputSink()
        bridge.sink = sink

        XCTAssertEqual(bridge.handleServerHandshake(Data([0x0e, 0x02, 0x03, 0x00])), 3)
        XCTAssertGreaterThanOrEqual(sink.reliablePackets.count, 2)
        XCTAssertEqual(sink.reliablePackets.first, Data([0x02, 0x00, 0x00, 0x00]))

        let packetCount = sink.reliablePackets.count
        XCTAssertNil(bridge.handleServerHandshake(Data([0xff, 0x00])))
        XCTAssertEqual(sink.reliablePackets.count, packetCount)
    }

    private func remoteOffer(address: String, port: Int) -> String {
        """
        v=0
        c=IN IP4 \(address)
        m=video \(port) UDP/TLS/RTP/SAVPF 96
        a=candidate:1 1 udp 2122260223 \(address) \(port) typ host generation 0
        a=rtpmap:96 H264/90000
        """
    }

    func testNvstRequestMatchesAndroidStartupAndPacingContract() {
        var settings = AppSettings.default
        settings.preferredCodec = "H264"
        let profile = StreamVideoProfile(width: 1_280, height: 720, fps: 60, maxBitrateKbps: 13_000)
        let nvst = NativeStreamSDP.buildNvstSDP(
            offerSDP: "a=ri.partialReliableThresholdMs:30",
            localAnswerSDP: "a=ice-ufrag:u\na=ice-pwd:p\na=fingerprint:sha-256 AA:BB",
            profile: profile,
            settings: settings,
            codec: .h264
        )

        XCTAssertTrue(nvst.contains("a=vqos.adjustStreamingFpsDuringOutOfFocus:0"))
        XCTAssertTrue(nvst.contains("a=packetPacing.numGroups:5"))
        XCTAssertTrue(nvst.contains("a=video.initialBitrateKbps:9100"))
        XCTAssertTrue(nvst.contains("a=video.initialPeakBitrateKbps:13000"))
        XCTAssertTrue(nvst.contains("a=vqos.bw.minimumBitrateKbps:5000"))
        XCTAssertFalse(nvst.contains("a=vqos.drc.minRequiredBitrateCheckEnabled"))
        XCTAssertFalse(nvst.contains("a=vqos.bllFec.enable"))
    }

    func testStreamPresetsMatchAndroidValuesAndRespectAppleTierFPSCaps() {
        var base = AppSettings.default
        base.preferredAspectRatio = "16:9"

        let low = StreamSettingsResolver.settings(base, applying: .lowDataSaver, membershipTier: "FREE")
        XCTAssertEqual(low.streamPreset, .lowDataSaver)
        XCTAssertEqual(low.preferredResolution, "1366x768")
        XCTAssertEqual(low.preferredFPS, 30)
        XCTAssertEqual(low.maxBitrateMbps, 12)
        XCTAssertEqual(low.preferredQuality, "Data Saver")

        let medium = StreamSettingsResolver.settings(base, applying: .medium, membershipTier: "PERFORMANCE")
        XCTAssertEqual(medium.streamPreset, .medium)
        XCTAssertEqual(medium.preferredResolution, "1920x1080")
        XCTAssertEqual(medium.preferredFPS, 60)
        XCTAssertEqual(medium.maxBitrateMbps, 35)
        XCTAssertEqual(medium.preferredQuality, "Balanced")

        let freeHigh = StreamSettingsResolver.settings(base, applying: .high, membershipTier: "FREE")
        XCTAssertEqual(freeHigh.preferredResolution, "1920x1080")
        XCTAssertEqual(freeHigh.preferredFPS, 60)

        let ultimateHigh = StreamSettingsResolver.settings(base, applying: .high, membershipTier: "ULTIMATE")
        XCTAssertEqual(ultimateHigh.streamPreset, .high)
        XCTAssertEqual(ultimateHigh.preferredResolution, "2560x1440")
        XCTAssertEqual(ultimateHigh.preferredFPS, 120)
        XCTAssertEqual(ultimateHigh.maxBitrateMbps, 75)
        XCTAssertEqual(ultimateHigh.preferredQuality, "Quality")

        var excessiveFPS = base
        excessiveFPS.preferredResolution = "1920x1080"
        excessiveFPS.preferredFPS = 360
        let cappedProfile = deterministicProfile(for: excessiveFPS, membershipTier: "ULTIMATE")
        XCTAssertEqual(cappedProfile.fps, 120)
    }

    func testTwentyByNineResolutionCatalogIncludesEveryAndroidChoice() {
        let choices = StreamSettingsResolver.choices(forAspectRatio: "20:9")
        XCTAssertEqual(
            choices.map(\.value),
            ["1600x720", "2400x1080", "3200x1440", "4800x2160"]
        )
        XCTAssertEqual(
            choices.map(\.requiredPlan),
            [.free, .priority, .priority, .ultimate]
        )
    }

    func testArbitraryCustomResolutionIsPreservedWithinTierAndRejectedAboveTier() {
        XCTAssertTrue(
            StreamSettingsResolver.customResolutionIsAvailable(
                width: 1_800,
                height: 1_000,
                membershipTier: "FREE"
            )
        )

        var withinTier = AppSettings.default
        withinTier.preferredAspectRatio = "16:9"
        withinTier.preferredResolution = "1800x1000"
        let preserved = deterministicProfile(for: withinTier, membershipTier: "FREE")
        XCTAssertEqual(preserved.resolutionString, "1800x1000")

        XCTAssertFalse(
            StreamSettingsResolver.customResolutionIsAvailable(
                width: 2_200,
                height: 1_200,
                membershipTier: "FREE"
            )
        )

        var aboveTier = withinTier
        aboveTier.preferredResolution = "2200x1200"
        let rejected = deterministicProfile(for: aboveTier, membershipTier: "FREE")
        XCTAssertEqual(rejected.resolutionString, "1920x1080")
    }

    func testLegacySettingsDecodeWithSafeDefaultsAndMigration() throws {
        let legacyJSON = Data(
            """
            {
              "preferredRegion": "US East",
              "preferredResolution": "1920x1080",
              "preferredFPS": 60,
              "preferredQuality": "Balanced",
              "preferredCodec": "Auto",
              "maxBitrateMbps": 0,
              "keyboardLayout": "en-US",
              "gameLanguage": "en_US",
              "enableL4S": false,
              "enableCloudGsync": false,
              "keepMicEnabled": false,
              "showStatsOverlay": true,
              "hideServerSelector": false,
              "queueLiveActivitiesEnabled": true,
              "selectedProviderIdpId": "legacy-provider",
              "fortnitePrefersNativeTouch": true,
              "favoriteGameIds": ["game-1"]
            }
            """.utf8
        )

        let settings = try JSONDecoder().decode(AppSettings.self, from: legacyJSON)

        XCTAssertEqual(settings.preferredRegion, "")
        XCTAssertEqual(settings.preferredAspectRatio, "16:9")
        XCTAssertEqual(settings.streamPreset, .custom)
        XCTAssertFalse(settings.sessionProxyEnabled)
        XCTAssertEqual(settings.sessionProxyUrl, "")
        XCTAssertFalse(settings.streamSharpeningEnabled)
        XCTAssertEqual(settings.streamSharpeningAmount, 0.25)
        XCTAssertEqual(settings.mouseSensitivity, 1)
        XCTAssertEqual(settings.mouseAcceleration, 1)
        XCTAssertTrue(settings.fingerMouseEnabled)
        XCTAssertTrue(settings.phoneRumbleFallback)
        XCTAssertEqual(settings.launchPage, .store)
        XCTAssertEqual(settings.posterSizeScale, 1)
        XCTAssertTrue(settings.compactGameCards)
        XCTAssertTrue(settings.showGameStoreLabels)
        XCTAssertTrue(settings.sessionCounterEnabled)
        XCTAssertFalse(settings.nerdMode)
        XCTAssertFalse(settings.catalogWallpaperEnabled)
        XCTAssertNil(settings.catalogWallpaperFilename)
        XCTAssertFalse(settings.streamTutorialCompleted)
        XCTAssertFalse(settings.controllerTouchPromptDismissed)
        XCTAssertEqual(settings.streamerPreferences, .default)
        XCTAssertEqual(settings.defaultGameVariantIds, [:])
        XCTAssertEqual(settings.favoriteGameIds, ["game-1"])

        let roundTrip = try JSONDecoder().decode(
            AppSettings.self,
            from: JSONEncoder().encode(settings)
        )
        XCTAssertEqual(roundTrip, settings)
    }

    func testSafeVideoFallbackCapsExpensiveAndUnsupportedSettings() {
        var settings = AppSettings.default
        settings.preferredAspectRatio = "16:9"
        settings.preferredResolution = "5120x2880"
        settings.preferredFPS = 120
        settings.maxBitrateMbps = 100
        settings.preferredCodec = "AV1"
        settings.preferredColorQuality = StreamColorQuality.tenBit444.rawValue
        settings.hdrEnabled = true
        settings.enableCloudGsync = true

        let fallback = settings.safeVideoFallback()

        XCTAssertEqual(fallback.preferredResolution, "1920x1080")
        XCTAssertEqual(fallback.preferredAspectRatio, "16:9")
        XCTAssertEqual(fallback.preferredFPS, 60)
        XCTAssertEqual(fallback.maxBitrateMbps, 75)
        XCTAssertEqual(fallback.preferredCodec, "H264")
        XCTAssertEqual(fallback.preferredColorQuality, StreamColorQuality.eightBit420.rawValue)
        XCTAssertFalse(fallback.hdrEnabled)
        XCTAssertFalse(fallback.enableCloudGsync)
    }

    func testInternalSessionProfileRejectionRetriesOnlyWithAChangedSafeProfile() {
        let rejection = NSError(
            domain: "OpenNOW.Session",
            code: 400,
            userInfo: [NSLocalizedDescriptionKey: #"{"requestStatus":{"statusCode":4,"statusDescription":"INTERNAL_ERROR_STATUS 8A8C0000"}}"#]
        )
        var unsafeSettings = AppSettings.default
        unsafeSettings.preferredCodec = "AV1"
        unsafeSettings.preferredColorQuality = StreamColorQuality.tenBit444.rawValue
        unsafeSettings.enableCloudGsync = true

        XCTAssertTrue(
            SessionLaunchRecoveryPolicy.shouldRetryWithSafeVideoProfile(
                error: rejection,
                settings: unsafeSettings
            )
        )
        XCTAssertFalse(
            SessionLaunchRecoveryPolicy.shouldRetryWithSafeVideoProfile(
                error: rejection,
                settings: unsafeSettings.safeVideoFallback()
            )
        )
        XCTAssertFalse(
            SessionLaunchRecoveryPolicy.shouldRetryWithSafeVideoProfile(
                error: NSError(domain: "OpenNOW.Session", code: 500),
                settings: unsafeSettings
            )
        )
    }

    func testCloudGameDecodesLegacyCachedPayloadWithoutNewCatalogFields() throws {
        let legacyJSON = Data(
            """
            {
              "id": "legacy-game",
              "title": "Legacy Game",
              "genre": "Action",
              "platform": "GeForce NOW",
              "icon": "gamecontroller.fill",
              "launchOptions": []
            }
            """.utf8
        )

        let game = try JSONDecoder().decode(CloudGame.self, from: legacyJSON)

        XCTAssertEqual(game.id, "legacy-game")
        XCTAssertEqual(game.title, "Legacy Game")
        XCTAssertNil(game.catalogSectionId)
        XCTAssertNil(game.catalogSectionTitle)
        XCTAssertNil(game.contentRatings)
    }

    func testCurrentGFNContentRatingObjectParsesForGameDetails() throws {
        let data = Data(
            #"""
            {
              "contentRatings": {
                "type": "ESRB",
                "categoryKey": "T",
                "contentDescriptorKeys": ["VIOLENCE", "STRONG_LANGUAGE"],
                "interactiveElementKeys": ["USERS_INTERACT", "IN_GAME_PURCHASES"]
              }
            }
            """#.utf8
        )
        let app = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])

        XCTAssertEqual(
            GFNContentRatingParser.labels(from: app["contentRatings"]),
            ["ESRB T", "Violence", "Strong Language", "Users Interact", "In Game Purchases"]
        )
    }

    func testLegacyRatingArraysRemainSupportedAndDeduplicated() throws {
        let data = Data(
            #"""
            {
              "contentRatings": [
                "PEGI 12",
                {"displayName": "USK 12"},
                "PEGI 12"
              ]
            }
            """#.utf8
        )
        let app = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])

        XCTAssertEqual(
            GFNContentRatingParser.labels(from: app["contentRatings"]),
            ["PEGI 12", "USK 12"]
        )
    }

    func testCatalogFallbackPreservesDescriptionsWithoutRestoringStaleLaunchers() throws {
        let cached = try JSONDecoder().decode(
            CloudGame.self,
            from: Data(
                #"""
                {
                  "id": "game",
                  "title": "Game",
                  "genre": "Action",
                  "platform": "Steam",
                  "icon": "gamecontroller.fill",
                  "launchAppId": "123",
                  "launchOptions": [{"storefront":"Steam","appId":"123"}],
                  "summary": "Cached description",
                  "stores": ["Steam"],
                  "playType": "ACCOUNT_LINKED",
                  "membershipTierLabel": "ULTIMATE",
                  "contentRatings": ["ESRB T"]
                }
                """#.utf8
            )
        )
        let fetched = try JSONDecoder().decode(
            CloudGame.self,
            from: Data(
                #"""
                {
                  "id": "game",
                  "title": "Game",
                  "genre": "Action",
                  "platform": "GeForce NOW",
                  "icon": "gamecontroller.fill",
                  "launchOptions": []
                }
                """#.utf8
            )
        )

        let merged = fetched.fillingMissingMetadata(from: cached)

        XCTAssertEqual(merged.summary, "Cached description")
        XCTAssertEqual(merged.contentRatings, ["ESRB T"])
        XCTAssertNil(merged.launchAppId)
        XCTAssertTrue(merged.launchOptions.isEmpty)
        XCTAssertNil(merged.stores)
        XCTAssertNil(merged.playType)
        XCTAssertNil(merged.membershipTierLabel)
    }

    func testPrintedWasteAutoRoutingMatchesAndroidLatencyWeighting() throws {
        let lowLatency = PrintedWasteZone(
            id: "low-latency",
            region: "US",
            queuePosition: 100,
            etaMs: nil,
            zoneUrl: "https://low-latency.example",
            pingMs: 10,
            isMeasuring: false,
            regionSuffix: "west"
        )
        let shortQueue = PrintedWasteZone(
            id: "short-queue",
            region: "US",
            queuePosition: 1,
            etaMs: nil,
            zoneUrl: "https://short-queue.example",
            pingMs: 50,
            isMeasuring: false,
            regionSuffix: "east"
        )
        let unpinged = PrintedWasteZone(
            id: "unpinged",
            region: "US",
            queuePosition: 0,
            etaMs: nil,
            zoneUrl: "https://unpinged.example",
            pingMs: nil,
            isMeasuring: true,
            regionSuffix: "unknown"
        )

        XCTAssertEqual(
            recommendedPrintedWasteZone(in: [lowLatency, shortQueue, unpinged])?.id,
            lowLatency.id
        )
    }

    func testBlockedBombayZoneCannotBeSelectedByIDURLOrAutomaticRouting() {
        XCTAssertTrue(StreamZonePolicy.isBlocked("np-bom-01"))
        XCTAssertTrue(StreamZonePolicy.isBlocked("https://np-bom-01.cloudmatchbeta.nvidiagrid.net/"))
        XCTAssertTrue(StreamZonePolicy.isBlocked("NP-BOM-01.CLOUDMATCHBETA.NVIDIAGRID.NET"))
        XCTAssertFalse(StreamZonePolicy.isBlocked("np-bom-02"))

        let blocked = PrintedWasteZone(
            id: "NP-BOM-01",
            region: "IN",
            queuePosition: 1,
            etaMs: nil,
            zoneUrl: "https://np-bom-01.cloudmatchbeta.nvidiagrid.net/",
            pingMs: 1,
            isMeasuring: false,
            regionSuffix: "bom-01"
        )
        let allowed = PrintedWasteZone(
            id: "NP-AMS-02",
            region: "EU",
            queuePosition: 30,
            etaMs: nil,
            zoneUrl: "https://np-ams-02.cloudmatchbeta.nvidiagrid.net/",
            pingMs: 40,
            isMeasuring: false,
            regionSuffix: "ams-02"
        )

        XCTAssertEqual(recommendedPrintedWasteZone(in: [blocked, allowed])?.id, allowed.id)
    }

    func testAndroidBitrateAndLanguageChoicesRemainAvailable() {
        XCTAssertEqual(StreamSettingsResolver.bitrateOptionsMbps, [0] + Array(1...150))
        XCTAssertTrue(StreamSettingsResolver.keyboardLayoutOptions.contains { $0.value == "zh-TW" })
        XCTAssertTrue(StreamSettingsResolver.keyboardLayoutOptions.contains { $0.value == "ru-RU" })
        for language in ["th_TH", "vi_VN", "id_ID", "uk_UA", "nl_NL", "no_NO"] {
            XCTAssertTrue(
                StreamSettingsResolver.gameLanguageOptions.contains { $0.value == language },
                "Missing game language \(language)"
            )
        }
    }

    func testSessionLimitsUseAndroidDurationsAcrossMembershipAliases() {
        for alias in ["FREE", "free-tier", nil] as [String?] {
            XCTAssertEqual(
                streamSessionLimit(for: alias),
                StreamSessionLimit(tierLabel: "Free", limitHours: 1, mode: .countdown)
            )
        }

        for alias in ["PRIORITY", "PERFORMANCE", "PREMIUM", "FOUNDERS"] {
            XCTAssertEqual(
                streamSessionLimit(for: alias),
                StreamSessionLimit(tierLabel: "Performance", limitHours: 6, mode: .stopwatch),
                "Incorrect session limit for \(alias)"
            )
        }

        for alias in ["ULTIMATE", "RTX 3080"] {
            XCTAssertEqual(
                streamSessionLimit(for: alias),
                StreamSessionLimit(tierLabel: "Ultimate", limitHours: 8, mode: .stopwatch),
                "Incorrect session limit for \(alias)"
            )
        }
    }

    func testSessionWarningTrackerArmsWithoutWarningOnFirstSample() {
        var tracker = StreamSessionWarningTracker()

        XCTAssertNil(tracker.nextWarning(remainingSeconds: 30 * 60))
        XCTAssertEqual(tracker.previousRemainingSeconds, 30 * 60)
        XCTAssertTrue(tracker.warnedThresholds.isEmpty)
    }

    func testSessionWarningTrackerChoosesMostUrgentSkippedThreshold() {
        var tracker = StreamSessionWarningTracker()

        XCTAssertNil(tracker.nextWarning(remainingSeconds: 10 * 60 + 1))
        XCTAssertEqual(tracker.nextWarning(remainingSeconds: 5 * 60 - 1), 5 * 60)
        XCTAssertEqual(tracker.warnedThresholds, [5 * 60])
    }

    func testSessionWarningTrackerWarnsEachThresholdOnlyOnce() {
        var tracker = StreamSessionWarningTracker()

        XCTAssertNil(tracker.nextWarning(remainingSeconds: 10 * 60 + 1))
        XCTAssertEqual(tracker.nextWarning(remainingSeconds: 10 * 60), 10 * 60)
        XCTAssertNil(tracker.nextWarning(remainingSeconds: 10 * 60 + 1))
        XCTAssertNil(tracker.nextWarning(remainingSeconds: 10 * 60))
        XCTAssertEqual(tracker.warnedThresholds, [10 * 60])
    }

    func testUnicodeInputPacketsPreserveScalarBoundariesAndCharacterLimit() throws {
        let encoder = NativeStreamInputEncoder()
        let text = String(repeating: "a", count: 1_015) + "😀B"
        let batch = encoder.encodeUnicodeText(text)

        XCTAssertEqual(batch.characterCount, text.count)
        XCTAssertEqual(batch.packets.count, 2)
        var reconstructed = ""
        for packet in batch.packets {
            let bytes = [UInt8](packet)
            XCTAssertEqual(Array(bytes.prefix(5)), [0x22, 0x17, 0, 0, 0])
            XCTAssertLessThanOrEqual(bytes.count - 5, 1_016)
            reconstructed += try XCTUnwrap(String(data: packet.dropFirst(5), encoding: .utf8))
        }
        XCTAssertEqual(reconstructed, text)

        let limited = encoder.encodeUnicodeText(String(repeating: "x", count: 4_100))
        XCTAssertEqual(limited.characterCount, 4_096)
        XCTAssertEqual(limited.packets.reduce(0) { $0 + max(0, $1.count - 5) }, 4_096)
    }

    func testVirtualControllerMergesIntoPrimaryPhysicalControllerLikeAndroid() {
        let physical = NativeStreamGamepadState(
            controllerId: 2,
            buttons: NativeStreamVirtualGamepadButton.a.rawValue,
            leftTrigger: 40,
            rightTrigger: 100,
            leftStickX: 100,
            leftStickY: 200,
            rightStickX: 300,
            rightStickY: 400,
            connected: true
        )
        let virtual = NativeStreamVirtualGamepadState(
            buttons: NativeStreamVirtualGamepadButton.b.rawValue,
            leftTrigger: 80,
            rightTrigger: 20,
            leftStickX: -1_000,
            leftStickY: 1_000,
            rightStickX: -2_000,
            rightStickY: 2_000,
            leftStickActive: true,
            rightStickActive: false
        )

        let merged = NativeStreamGamepadMixer.merging(physical: physical, virtual: virtual)

        XCTAssertEqual(merged.controllerId, 2)
        XCTAssertEqual(
            merged.buttons,
            NativeStreamVirtualGamepadButton.a.rawValue | NativeStreamVirtualGamepadButton.b.rawValue
        )
        XCTAssertEqual(merged.leftTrigger, 80)
        XCTAssertEqual(merged.rightTrigger, 100)
        XCTAssertEqual(merged.leftStickX, -1_000)
        XCTAssertEqual(merged.leftStickY, 1_000)
        XCTAssertEqual(merged.rightStickX, 300)
        XCTAssertEqual(merged.rightStickY, 400)
        XCTAssertTrue(merged.connected)
    }

    func testFortniteUsesItsPersistedMobileTouchLayoutOnlyWhenEnabled() {
        var settings = AppSettings.default
        XCTAssertEqual(
            streamTouchLayoutProfile(gameTitle: "Fortnite Festival", settings: settings),
            "fortnite-mobile"
        )
        XCTAssertEqual(settings.touchLayout(for: "fortnite-mobile"), .fortniteMobile)

        settings.fortnitePrefersNativeTouch = false
        XCTAssertEqual(
            streamTouchLayoutProfile(gameTitle: "FORTNITE", settings: settings),
            "default"
        )
        XCTAssertEqual(
            streamTouchLayoutProfile(gameTitle: "Aimlabs", settings: AppSettings.default),
            "default"
        )
    }

    func testContentRatingMetadataUsesOrderedDeduplicatedUnion() {
        XCTAssertEqual(
            GFNContentRatingParser.merging(
                ["ESRB T", "Violence"],
                ["ESRB T", "Users Interact"]
            ),
            ["ESRB T", "Violence", "Users Interact"]
        )
    }

    func testContentRatingsUseCatalogStyleAgeBadgesWithoutLegacySeventeenGate() {
        XCTAssertEqual(GFNContentRatingParser.ageBadge(from: ["ESRB T", "Violence"]), "12+")
        XCTAssertEqual(GFNContentRatingParser.ageBadge(from: ["PEGI 18"]), "18+")
        XCTAssertEqual(GFNContentRatingParser.ageBadge(from: ["USK 6"]), "6+")
        XCTAssertNil(GFNContentRatingParser.ageBadge(from: ["Violence", "Users Interact"]))
    }

    func testArtworkRolesMatchAndroidCatalogDetailsAndQueuePriority() throws {
        let game = try JSONDecoder().decode(
            CloudGame.self,
            from: Data(
                #"{"id":"game","title":"Game","genre":"Action","platform":"Steam","icon":"gamecontroller.fill","imageUrl":"legacy","boxArtUrl":"box","heroImageUrl":"hero","tvBannerUrl":"tv","launchOptions":[]}"#.utf8
            )
        )

        XCTAssertEqual(game.catalogArtworkUrl, "box")
        XCTAssertEqual(game.detailsArtworkUrl, "hero")
        XCTAssertEqual(game.queueArtworkUrl, "tv")
    }

    func testPrintedWasteEqualScoresPreferLowerPingLikeAndroid() {
        let highPing = PrintedWasteZone(
            id: "high-ping",
            region: "US",
            queuePosition: 1,
            etaMs: nil,
            zoneUrl: "https://high-ping.example",
            pingMs: 2,
            isMeasuring: false,
            regionSuffix: "east"
        )
        let lowPing = PrintedWasteZone(
            id: "low-ping",
            region: "US",
            queuePosition: 4,
            etaMs: nil,
            zoneUrl: "https://low-ping.example",
            pingMs: 1,
            isMeasuring: false,
            regionSuffix: "west"
        )
        let normalizationAnchor = PrintedWasteZone(
            id: "anchor",
            region: "US",
            queuePosition: 4,
            etaMs: nil,
            zoneUrl: "https://anchor.example",
            pingMs: 4,
            isMeasuring: false,
            regionSuffix: "central"
        )

        XCTAssertEqual(
            recommendedPrintedWasteZone(in: [highPing, lowPing, normalizationAnchor])?.id,
            lowPing.id
        )
    }

    func testStableCatalogIdentityDeduplicatesLauncherVariants() throws {
        let first = try JSONDecoder().decode(
            CloudGame.self,
            from: Data(
                #"{"id":"shared-app:steam","title":"Game","genre":"Action","platform":"Steam","icon":"gamecontroller.fill","launchOptions":[],"uuid":"shared-app"}"#.utf8
            )
        )
        let second = try JSONDecoder().decode(
            CloudGame.self,
            from: Data(
                #"{"id":"shared-app:epic","title":"Game","genre":"Action","platform":"Epic","icon":"gamecontroller.fill","launchOptions":[],"uuid":"SHARED-APP"}"#.utf8
            )
        )

        XCTAssertEqual(catalogStableGameKey(first), catalogStableGameKey(second))
    }

    func testDiagnosticsStrictlyRedactsCredentialsAndIdentifiers() throws {
        let payload = #"{"requestStatus":{"statusCode":500,"statusDescription":"internal_server_error"},"access_token":"secret-access-token","refresh_token":"secret-refresh-token","email":"person@example.com","sessionId":"c49ec342-4c25-4e0a-9416-9c82e2f53233","serverIp":"203.0.113.42"}"#
        let redacted = DiagnosticsSanitizer.redactedBody(
            Data(payload.utf8),
            headers: ["Content-Type": "application/json"]
        )

        XCTAssertTrue(redacted.contains("internal_server_error"))
        XCTAssertTrue(redacted.contains("500"))
        XCTAssertFalse(redacted.contains("secret-access-token"))
        XCTAssertFalse(redacted.contains("secret-refresh-token"))
        XCTAssertFalse(redacted.contains("person@example.com"))
        XCTAssertFalse(redacted.contains("c49ec342-4c25-4e0a-9416-9c82e2f53233"))
        XCTAssertFalse(redacted.contains("203.0.113.42"))
        XCTAssertFalse(
            DiagnosticsSanitizer.sanitize(#"lastError={"access_token":"short-secret"}"#)
                .contains("short-secret")
        )
    }

    func testDiagnosticsHeadersKeepUsefulMetadataWithoutSecrets() {
        let redacted = DiagnosticsSanitizer.redactedHeaders([
            "Authorization": "GFNJWT super-secret-token",
            "x-device-id": "device-12345",
            "Content-Type": "application/json",
            "x-request-id": "request-67890"
        ])

        XCTAssertTrue(redacted.contains("Content-Type: application/json"))
        XCTAssertFalse(redacted.contains("super-secret-token"))
        XCTAssertFalse(redacted.contains("device-12345"))
        XCTAssertFalse(redacted.contains("request-67890"))
        XCTAssertTrue(redacted.contains("[ID:"))
    }

    private func deterministicProfile(
        for settings: AppSettings,
        membershipTier: String
    ) -> StreamVideoProfile {
        StreamSettingsResolver.profile(
            for: settings,
            nativeBounds: CGRect(x: 0, y: 0, width: 1_179, height: 2_556),
            nativeScale: 3,
            userInterfaceIdiom: .phone,
            membershipTier: membershipTier
        )
    }
}

private final class RecordingNativeStreamInputSink: NativeStreamInputSink {
    var reliablePackets: [Data] = []
    var partiallyReliablePackets: [Data] = []
    var logMessages: [String] = []

    func sendReliableInput(_ data: Data) {
        reliablePackets.append(data)
    }

    func sendPartiallyReliableInput(_ data: Data) {
        partiallyReliablePackets.append(data)
    }

    func logInputEvent(_ message: String) {
        logMessages.append(message)
    }
}
