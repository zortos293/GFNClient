import Foundation

// MARK: - Quality ladders
//
// Ported verbatim from `SessionReport.kt` so a session scored on an iPhone and the same session
// scored on a Pixel produce the same number. The in-stream HUD reads the same ladders, which is
// what stops the overlay calling a session bad while the report that follows calls it Good.

enum StreamQualityLadder {

    static func level(forScore score: Int) -> StreamQualityLevel {
        switch score {
        case 85...: return .good
        case 55..<85: return .fair
        default: return .poor
        }
    }

    static func latencyScore(_ value: Int) -> Int {
        switch value {
        case ...30: return 100
        case ...50: return 92
        case ...80: return 80
        case ...120: return 60
        case ...180: return 35
        default: return 10
        }
    }

    static func packetLossScore(_ value: Double) -> Int {
        switch value {
        case ...0.1: return 100
        case ...0.5: return 90
        case ...1.0: return 75
        case ...2.0: return 55
        case ...5.0: return 25
        default: return 5
        }
    }

    static func jitterScore(_ value: Double) -> Int {
        switch value {
        case ...5.0: return 100
        case ...10.0: return 90
        case ...20.0: return 70
        case ...30.0: return 50
        case ...50.0: return 25
        default: return 5
        }
    }

    static func frameRateScore(_ value: Double, targetFps: Int) -> Int {
        let ratio = value / Double(max(targetFps, 1))
        switch ratio {
        case 0.98...: return 100
        case 0.95..<0.98: return 95
        case 0.90..<0.95: return 82
        case 0.80..<0.90: return 60
        case 0.65..<0.80: return 35
        default: return 10
        }
    }

    static func decodeScore(_ value: Double, targetFps: Int, actualFps: Double? = nil) -> Int {
        let frameBudgetMs = 1_000.0 / Double(max(targetFps, 1))
        let ratio = value / frameBudgetMs
        let latency: Int
        switch ratio {
        case ...0.50: latency = 100
        case ...0.75: latency = 90
        case ...1.00: latency = 75
        case ...1.50: latency = 45
        default: latency = 15
        }
        // Decode time is input-to-output latency, not serial decoder throughput. Hardware decoders
        // pipeline frames, so latency can exceed one display interval while cadence stays healthy.
        guard let actualFps else { return latency }
        let throughputRatio = actualFps / Double(max(targetFps, 1))
        switch throughputRatio {
        case 0.98...: return max(latency, 75)
        case 0.90..<0.98: return max(latency, 55)
        default: return latency
        }
    }
}

/// Per-metric readings, derived from the same ladders the session score is built from.
enum StreamQuality {
    static func latency(_ ms: Int) -> StreamQualityLevel {
        StreamQualityLadder.level(forScore: StreamQualityLadder.latencyScore(ms))
    }

    static func packetLoss(_ percent: Double) -> StreamQualityLevel {
        StreamQualityLadder.level(forScore: StreamQualityLadder.packetLossScore(percent))
    }

    static func jitter(_ ms: Double) -> StreamQualityLevel {
        StreamQualityLadder.level(forScore: StreamQualityLadder.jitterScore(ms))
    }

    static func frameRate(_ fps: Double, targetFps: Int) -> StreamQualityLevel {
        StreamQualityLadder.level(forScore: StreamQualityLadder.frameRateScore(fps, targetFps: targetFps))
    }

    static func decode(_ ms: Double, targetFps: Int, actualFps: Double? = nil) -> StreamQualityLevel {
        StreamQualityLadder.level(
            forScore: StreamQualityLadder.decodeScore(ms, targetFps: targetFps, actualFps: actualFps)
        )
    }

    /// Round-trip time to a candidate server, used by the server picker before a session exists.
    static func serverPing(_ ms: Int) -> StreamQualityLevel { latency(ms) }
}

// MARK: - Report model

enum SessionReportRating: String, Equatable {
    case excellent
    case good
    case fair
    case poor

    var label: String {
        switch self {
        case .excellent: return "Excellent"
        case .good: return "Good"
        case .fair: return "Fair"
        case .poor: return "Needs work"
        }
    }

    static func forScore(_ score: Int) -> SessionReportRating {
        switch score {
        case 90...: return .excellent
        case 75..<90: return .good
        case 60..<75: return .fair
        default: return .poor
        }
    }

    var qualityLevel: StreamQualityLevel {
        switch self {
        case .excellent, .good: return .good
        case .fair: return .fair
        case .poor: return .poor
        }
    }
}

enum SessionReportFindingKind: Equatable {
    case info
    case warning
}

struct SessionReportFinding: Identifiable, Equatable {
    let title: String
    let detail: String
    var kind: SessionReportFindingKind = .info

    var id: String { title }
}

enum SessionNetworkKind: String, Equatable {
    case wifi
    case cellular
    case wired
    case unknown

    var label: String {
        switch self {
        case .wifi: return "Wi-Fi"
        case .cellular: return "Cellular"
        case .wired: return "Wired"
        case .unknown: return "Unknown"
        }
    }
}

/// A single measurement taken from the WebRTC stats report, roughly once a second.
struct StreamRuntimeSample: Equatable {
    var timestamp: TimeInterval
    var pingMs: Int?
    var bitrateKbps: Int?
    var jitterMs: Double?
    var fps: Int?
    var receivedFps: Int?
    var decodedFps: Int?
    var decodeMs: Double?
    var packetsLostDelta: Int?
    var packetsReceivedDelta: Int?
    var packetLossPercent: Double?
    var resolution: String?
    var codec: String?
    var networkKind: SessionNetworkKind = .unknown

    var carriesReportableValues: Bool {
        pingMs != nil
            || bitrateKbps != nil
            || fps != nil
            || receivedFps != nil
            || decodedFps != nil
            || decodeMs != nil
            || jitterMs != nil
            || packetLossPercent != nil
    }
}

/// One point on the report's latency chart. Kept small: a two-hour session at one sample a second
/// is 7,200 of these, and they are downsampled before they reach the view.
struct SessionReportTrendPoint: Identifiable, Equatable {
    let offsetSeconds: Int
    let pingMs: Int
    var id: Int { offsetSeconds }
}

struct SessionReport: Identifiable, Equatable {
    let id = UUID()
    let gameTitle: String
    let score: Int
    let rating: SessionReportRating
    let durationSeconds: Int
    let sampleCount: Int

    /// Fewer than ten samples is not enough to characterise a session. The view says so rather
    /// than presenting a confident-looking number built from three readings.
    let limitedData: Bool

    let averagePingMs: Int?
    let peakPingMs: Int?
    let averageBitrateKbps: Int?
    let peakBitrateKbps: Int?
    let packetLossPercent: Double?
    let peakPacketLossPercent: Double?
    let averageJitterMs: Double?
    let averageFps: Double?
    var averageReceivedFps: Double? = nil
    var averageDecodedFps: Double? = nil
    let lowestFps: Int?
    let targetFps: Int
    let averageDecodeMs: Double?
    let peakDecodeMs: Double?
    let requestedResolution: String
    let deliveredResolution: String?
    let requestedCodec: String
    let deliveredCodec: String?
    let networkKind: SessionNetworkKind
    let trend: [SessionReportTrendPoint]
    let downgrades: [SessionReportFinding]
    let recommendations: [SessionReportFinding]

    /// The chart needs enough points to say something true. Four points is a lie.
    var showsTrendChart: Bool { trend.count >= 8 }

    static func == (lhs: SessionReport, rhs: SessionReport) -> Bool { lhs.id == rhs.id }
}

// MARK: - Launch profile

/// What was asked for at each stage of the launch, so the report can name where a downgrade
/// actually happened rather than only reporting the end state.
struct StreamReportLaunchProfile: Equatable {
    let gameTitle: String
    /// What the user has saved in Settings.
    let selectedProfile: StreamVideoProfile
    /// After plan and entitlement limits.
    let eligibleProfile: StreamVideoProfile
    /// After the device codec probe — what was actually requested from CloudMatch.
    let initialProfile: StreamVideoProfile
    let requestedCodec: String
    let eligibleCodec: String
    let hdrRequested: Bool
}

// MARK: - Accumulator

/// Collects one session's telemetry and turns it into a report. Deliberately not an
/// `ObservableObject`: it is written to about once a second for hours and must never invalidate a
/// view. Ported from `StreamSessionReportAccumulator` on Android.
final class StreamSessionReportAccumulator {

    private static let minConfidentSamples = 10
    private static let maxRecommendations = 4
    private static let maxTrendPoints = 120

    private let launchProfile: StreamReportLaunchProfile
    private let startedAt: Date

    private var sampleCount = 0
    private var pingCount = 0
    private var pingTotal = 0
    private var peakPingMs: Int?
    private var bitrateCount = 0
    private var bitrateTotal = 0
    private var peakBitrateKbps: Int?
    private var jitterCount = 0
    private var jitterTotal = 0.0
    private var fpsCount = 0
    private var fpsTotal = 0
    private var lowestFps: Int?
    private var receivedFpsCount = 0
    private var receivedFpsTotal = 0
    private var decodedFpsCount = 0
    private var decodedFpsTotal = 0
    private var consecutiveDecoderOverloadSamples = 0
    private var decoderOverloadDetected = false
    private var decodeCount = 0
    private var decodeTotal = 0.0
    private var peakDecodeMs: Double?
    private var packetLossSampleCount = 0
    private var packetLossSampleTotal = 0.0
    private var peakPacketLossPercent: Double?
    private var packetsLost = 0
    private var packetsReceived = 0
    private var hasPacketDeltas = false
    private var lastResolution: String?
    private var lastCodec: String?
    private var networkKindCounts: [SessionNetworkKind: Int] = [:]
    private var trendSamples: [SessionReportTrendPoint] = []
    private var recoveryReason: String?
    private var finalProfile: StreamVideoProfile
    private var finalCodec: String

    init(launchProfile: StreamReportLaunchProfile, startedAt: Date = Date()) {
        self.launchProfile = launchProfile
        self.startedAt = startedAt
        self.finalProfile = launchProfile.initialProfile
        self.finalCodec = launchProfile.requestedCodec
    }

    func record(_ sample: StreamRuntimeSample) {
        if sample.carriesReportableValues { sampleCount += 1 }

        if let ping = sample.pingMs, ping >= 0 {
            pingCount += 1
            pingTotal += ping
            peakPingMs = max(peakPingMs ?? ping, ping)
            appendTrendPoint(pingMs: ping)
        }
        if let bitrate = sample.bitrateKbps, bitrate >= 0 {
            bitrateCount += 1
            bitrateTotal += bitrate
            peakBitrateKbps = max(peakBitrateKbps ?? bitrate, bitrate)
        }
        if let jitter = sample.jitterMs, jitter >= 0 {
            jitterCount += 1
            jitterTotal += jitter
        }
        if let fps = sample.fps, fps > 0 {
            fpsCount += 1
            fpsTotal += fps
            lowestFps = min(lowestFps ?? fps, fps)
        }
        if let receivedFps = sample.receivedFps, receivedFps > 0 {
            receivedFpsCount += 1
            receivedFpsTotal += receivedFps
        }
        if let decodedFps = sample.decodedFps, decodedFps > 0 {
            decodedFpsCount += 1
            decodedFpsTotal += decodedFps
        }
        if let decode = sample.decodeMs, decode >= 0 {
            decodeCount += 1
            decodeTotal += decode
            peakDecodeMs = max(peakDecodeMs ?? decode, decode)
        }
        consecutiveDecoderOverloadSamples = Self.isDecoderOverloadSample(
            sample,
            requestedFps: launchProfile.initialProfile.fps
        ) ? consecutiveDecoderOverloadSamples + 1 : 0
        if consecutiveDecoderOverloadSamples >= 3 {
            decoderOverloadDetected = true
        }

        // Deltas are authoritative when present: a cumulative loss ratio averaged over a session
        // is dominated by whatever happened in the first thirty seconds.
        if let lost = sample.packetsLostDelta, let received = sample.packetsReceivedDelta,
           lost >= 0, received >= 0 {
            hasPacketDeltas = true
            packetsLost += lost
            packetsReceived += received
            let total = lost + received
            if total > 0 {
                let windowLoss = Double(lost) / Double(total) * 100
                peakPacketLossPercent = max(peakPacketLossPercent ?? windowLoss, windowLoss)
            }
        } else if let loss = sample.packetLossPercent, loss >= 0 {
            packetLossSampleCount += 1
            packetLossSampleTotal += loss
            peakPacketLossPercent = max(peakPacketLossPercent ?? loss, loss)
        }

        if let resolution = sample.resolution, parsedResolution(resolution) != nil {
            lastResolution = resolution
        }
        if let codec = sample.codec, !codec.isEmpty {
            lastCodec = codec
        }
        networkKindCounts[sample.networkKind, default: 0] += 1
    }

    func recordRecovery(reason: String, profile: StreamVideoProfile, codec: String) {
        let trimmed = reason.trimmingCharacters(in: .whitespacesAndNewlines)
        recoveryReason = trimmed.isEmpty ? nil : trimmed
        finalProfile = profile
        finalCodec = codec
    }

    /// Keeps the chart bounded. Once the buffer is full, every other point is dropped and the
    /// sampling interval doubles — a decimation that preserves shape without unbounded memory.
    private var trendStride = 1
    private var trendCounter = 0

    private func appendTrendPoint(pingMs: Int) {
        trendCounter += 1
        guard trendCounter % trendStride == 0 else { return }
        let offset = Int(Date().timeIntervalSince(startedAt))
        trendSamples.append(SessionReportTrendPoint(offsetSeconds: offset, pingMs: pingMs))
        if trendSamples.count > Self.maxTrendPoints {
            trendSamples = trendSamples.enumerated().filter { $0.offset.isMultiple(of: 2) }.map(\.element)
            trendStride *= 2
        }
    }

    func finish(at finishedAt: Date = Date()) -> SessionReport? {
        guard sampleCount > 0 else { return nil }

        let averagePingMs = average(pingTotal, pingCount).map { Int($0.rounded()) }
        let averageBitrateKbps = average(bitrateTotal, bitrateCount).map { Int($0.rounded()) }
        let averageJitterMs = average(jitterTotal, jitterCount)
        let averageFps = average(fpsTotal, fpsCount)
        let averageReceivedFps = average(receivedFpsTotal, receivedFpsCount)
        let averageDecodedFps = average(decodedFpsTotal, decodedFpsCount)
        let averageDecodeMs = average(decodeTotal, decodeCount)

        let packetLossPercent: Double? = {
            if hasPacketDeltas, packetsLost + packetsReceived > 0 {
                return Double(packetsLost) / Double(packetsLost + packetsReceived) * 100
            }
            return average(packetLossSampleTotal, packetLossSampleCount)
        }()

        let networkKind = networkKindCounts.max { $0.value < $1.value }?.key ?? .unknown
        let targetFps = launchProfile.initialProfile.fps

        let score = Self.qualityScore(
            averagePingMs: averagePingMs,
            packetLossPercent: packetLossPercent,
            averageJitterMs: averageJitterMs,
            averageFps: averageFps,
            targetFps: targetFps,
            averageDecodeMs: averageDecodeMs
        )

        let deliveredResolution = lastResolution
        let deliveredCodec = lastCodec ?? finalCodec

        return SessionReport(
            gameTitle: launchProfile.gameTitle.isEmpty ? "Cloud session" : launchProfile.gameTitle,
            score: score,
            rating: .forScore(score),
            durationSeconds: max(0, Int(finishedAt.timeIntervalSince(startedAt))),
            sampleCount: sampleCount,
            limitedData: sampleCount < Self.minConfidentSamples,
            averagePingMs: averagePingMs,
            peakPingMs: peakPingMs,
            averageBitrateKbps: averageBitrateKbps,
            peakBitrateKbps: peakBitrateKbps,
            packetLossPercent: packetLossPercent,
            peakPacketLossPercent: peakPacketLossPercent,
            averageJitterMs: averageJitterMs,
            averageFps: averageFps,
            averageReceivedFps: averageReceivedFps,
            averageDecodedFps: averageDecodedFps,
            lowestFps: lowestFps,
            targetFps: targetFps,
            averageDecodeMs: averageDecodeMs,
            peakDecodeMs: peakDecodeMs,
            requestedResolution: launchProfile.initialProfile.resolutionString,
            deliveredResolution: deliveredResolution,
            requestedCodec: launchProfile.requestedCodec,
            deliveredCodec: deliveredCodec,
            networkKind: networkKind,
            trend: trendSamples,
            downgrades: Self.buildDowngrades(
                launchProfile: launchProfile,
                finalProfile: finalProfile,
                finalCodec: finalCodec,
                deliveredResolution: deliveredResolution,
                deliveredCodec: deliveredCodec,
                recoveryReason: recoveryReason
            ),
            recommendations: Self.buildRecommendations(
                averagePingMs: averagePingMs,
                packetLossPercent: packetLossPercent,
                averageJitterMs: averageJitterMs,
                averageFps: averageFps,
                averageReceivedFps: averageReceivedFps,
                averageDecodedFps: averageDecodedFps,
                averageDecodeMs: averageDecodeMs,
                decoderOverloadDetected: decoderOverloadDetected,
                targetFps: targetFps,
                targetBitrateKbps: launchProfile.initialProfile.maxBitrateKbps,
                averageBitrateKbps: averageBitrateKbps,
                networkKind: networkKind
            )
        )
    }

    // MARK: Scoring

    /// Weighted across whichever metrics were actually captured. Latency and loss dominate because
    /// they are what a player feels; decode time is a diagnostic, not an experience.
    static func qualityScore(
        averagePingMs: Int?,
        packetLossPercent: Double?,
        averageJitterMs: Double?,
        averageFps: Double?,
        targetFps: Int,
        averageDecodeMs: Double?
    ) -> Int {
        var weightedTotal = 0.0
        var availableWeight = 0

        func add(_ score: Int, weight: Int) {
            weightedTotal += Double(score * weight)
            availableWeight += weight
        }

        if let averagePingMs { add(StreamQualityLadder.latencyScore(averagePingMs), weight: 35) }
        if let packetLossPercent { add(StreamQualityLadder.packetLossScore(packetLossPercent), weight: 30) }
        if let averageJitterMs { add(StreamQualityLadder.jitterScore(averageJitterMs), weight: 15) }
        if let averageFps { add(StreamQualityLadder.frameRateScore(averageFps, targetFps: targetFps), weight: 15) }
        if let averageDecodeMs {
            add(
                StreamQualityLadder.decodeScore(averageDecodeMs, targetFps: targetFps, actualFps: averageFps),
                weight: 5
            )
        }

        guard availableWeight > 0 else { return 50 }
        return min(max(Int((weightedTotal / Double(availableWeight)).rounded()), 0), 100)
    }

    // MARK: Findings

    static func buildDowngrades(
        launchProfile: StreamReportLaunchProfile,
        finalProfile: StreamVideoProfile,
        finalCodec: String,
        deliveredResolution: String?,
        deliveredCodec: String?,
        recoveryReason: String?
    ) -> [SessionReportFinding] {
        var findings: [SessionReportFinding] = []

        if launchProfile.selectedProfile != launchProfile.eligibleProfile {
            findings.append(
                SessionReportFinding(
                    title: "Limited by your plan",
                    detail: "Your saved \(summary(launchProfile.selectedProfile)) profile was reduced to "
                        + "\(summary(launchProfile.eligibleProfile)) before launch, because your membership does not include it.",
                    kind: .warning
                )
            )
        }

        if launchProfile.requestedCodec != launchProfile.eligibleCodec {
            findings.append(
                SessionReportFinding(
                    title: "Codec changed for this device",
                    detail: "\(launchProfile.requestedCodec) was replaced with \(launchProfile.eligibleCodec) "
                        + "so this device could decode the stream in hardware.",
                    kind: .warning
                )
            )
        }

        if launchProfile.eligibleProfile != launchProfile.initialProfile {
            findings.append(
                SessionReportFinding(
                    title: "Adjusted for this device",
                    detail: "The decoder probe changed \(summary(launchProfile.eligibleProfile)) to "
                        + "\(summary(launchProfile.initialProfile)) to stay inside this device's limits.",
                    kind: .warning
                )
            )
        }

        var reportedRecovery = false
        if recoveryReason != nil || finalProfile != launchProfile.initialProfile {
            if finalProfile == launchProfile.initialProfile,
               finalCodec.caseInsensitiveCompare(launchProfile.requestedCodec) == .orderedSame {
                var detail = "OpenNOW rebuilt the local media transport while preserving your selected "
                    + "\(summary(finalProfile)) profile and cloud session"
                if let reason = recoveryReason {
                    detail += ". Reason: \(reason.trimmingCharacters(in: CharacterSet(charactersIn: ".")))."
                } else {
                    detail += "."
                }
                findings.append(SessionReportFinding(title: "Recovered locally", detail: detail, kind: .warning))
            } else {
                var detail = "OpenNOW changed the live stream from \(summary(launchProfile.initialProfile)) "
                    + "to \(summary(finalProfile)) to keep the session connected"
                if let reason = recoveryReason {
                    detail += ". Reason: \(reason.trimmingCharacters(in: CharacterSet(charactersIn: ".")))."
                } else {
                    detail += "."
                }
                findings.append(SessionReportFinding(title: "Recovered mid-session", detail: detail, kind: .warning))
            }
            reportedRecovery = finalProfile.resolutionString != launchProfile.initialProfile.resolutionString
        }

        let requestedResolution = launchProfile.initialProfile.resolutionString
        if let delivered = deliveredResolution.flatMap(normalizedResolutionLabel),
           delivered != requestedResolution,
           !reportedRecovery {
            findings.append(
                SessionReportFinding(
                    title: "Delivered a different resolution",
                    detail: "The stream arrived at \(delivered) instead of the requested \(requestedResolution). "
                        + "That reflects what the cloud server or the game chose at runtime — your saved setting has not changed.",
                    kind: .warning
                )
            )
        }

        if let delivered = deliveredCodec?.uppercased(),
           !delivered.contains(finalCodec.uppercased()),
           recoveryReason == nil {
            findings.append(
                SessionReportFinding(
                    title: "Delivered a different codec",
                    detail: "The stream negotiated \(delivered) rather than the requested \(finalCodec).",
                    kind: .warning
                )
            )
        }

        return findings
    }

    static func buildRecommendations(
        averagePingMs: Int?,
        packetLossPercent: Double?,
        averageJitterMs: Double?,
        averageFps: Double?,
        averageReceivedFps: Double? = nil,
        averageDecodedFps: Double? = nil,
        averageDecodeMs: Double?,
        decoderOverloadDetected: Bool = false,
        targetFps: Int,
        targetBitrateKbps: Int,
        averageBitrateKbps: Int?,
        networkKind: SessionNetworkKind
    ) -> [SessionReportFinding] {
        var findings: [SessionReportFinding] = []

        let averageDecoderDeficit = averageReceivedFps.map { received in
            received >= Double(targetFps) * 0.85
                && averageDecodedFps.map { $0 <= received * 0.80 } == true
        } == true
        let frameBudgetMs = 1_000.0 / Double(max(targetFps, 1))
        if decoderOverloadDetected
            || averageDecoderDeficit
            || (averageFps.map { $0 < Double(targetFps) * 0.85 } == true
                && (averageDecodeMs ?? 0) > frameBudgetMs * 0.85) {
            var detail = "OpenNOW detected a sustained local decoder bottleneck"
            if let averageReceivedFps, let averageDecodedFps {
                detail += String(
                    format: ": the stream delivered %.1f FPS while the decoder produced %.1f FPS",
                    averageReceivedFps,
                    averageDecodedFps
                )
            }
            detail += ". Try a lower resolution or frame rate, or select H.264."
            findings.append(
                SessionReportFinding(title: "Decoder could not keep up", detail: detail, kind: .warning)
            )
        }

        if networkKind == .cellular {
            findings.append(
                SessionReportFinding(
                    title: "Try Wi-Fi if you can",
                    detail: "Cellular latency and capacity swing with signal and tower load. A 5 GHz or 6 GHz "
                        + "Wi-Fi network is usually steadier for cloud gaming.",
                    kind: .warning
                )
            )
        }

        if let loss = packetLossPercent, loss > 1.0 {
            findings.append(
                SessionReportFinding(
                    title: "Reduce packet loss",
                    detail: String(format: "Loss averaged %.1f%%. Above 1%% you get blur, stutter and recovery "
                                   + "events. Pause other uploads, move closer to the router, or use a wired adapter.", loss),
                    kind: .warning
                )
            )
        }

        if (averagePingMs ?? 0) > 80 || (averageJitterMs ?? 0) > 20 {
            findings.append(
                SessionReportFinding(
                    title: "Steady the latency",
                    detail: "Pick the closest server, turn off any VPN, and pause background downloads. "
                        + "Consistent latency matters more than raw download speed.",
                    kind: .warning
                )
            )
        }

        if let averageBitrateKbps, targetBitrateKbps > 0,
           averageBitrateKbps < targetBitrateKbps * 6 / 10 {
            findings.append(
                SessionReportFinding(
                    title: "Lower the bitrate cap",
                    detail: String(format: "The stream averaged %.1f Mb/s against a %.1f Mb/s cap, so the "
                                   + "connection could not keep up. Setting the cap nearer what it delivers gives a steadier picture.",
                                   Double(averageBitrateKbps) / 1_000,
                                   Double(targetBitrateKbps) / 1_000),
                    kind: .warning
                )
            )
        }

        if findings.isEmpty {
            findings.append(
                SessionReportFinding(
                    title: "Connection looked healthy",
                    detail: "Nothing crossed the thresholds worth flagging. Keep the same server and network "
                        + "for sessions like this one."
                )
            )
        }

        return Array(findings.prefix(maxRecommendations))
    }

    private static func isDecoderOverloadSample(
        _ sample: StreamRuntimeSample,
        requestedFps: Int
    ) -> Bool {
        guard let receivedFps = sample.receivedFps,
              let decodedFps = sample.decodedFps,
              let decodeMs = sample.decodeMs else { return false }
        let frameBudgetMs = 1_000.0 / Double(max(requestedFps, 1))
        return Double(receivedFps) >= Double(requestedFps) * 0.85
            && Double(decodedFps) <= Double(receivedFps) * 0.80
            && decodeMs >= frameBudgetMs * 1.10
    }

    // MARK: Helpers

    private func average(_ total: Int, _ count: Int) -> Double? {
        count > 0 ? Double(total) / Double(count) : nil
    }

    private func average(_ total: Double, _ count: Int) -> Double? {
        count > 0 ? total / Double(count) : nil
    }

    private func parsedResolution(_ value: String) -> (width: Int, height: Int)? {
        Self.parsedResolution(value)
    }

    static func parsedResolution(_ value: String) -> (width: Int, height: Int)? {
        let parts = value.split(separator: "x", maxSplits: 1).map(String.init)
        guard parts.count == 2, let width = Int(parts[0]), let height = Int(parts[1]),
              width > 0, height > 0 else { return nil }
        return (width, height)
    }

    static func normalizedResolutionLabel(_ value: String) -> String? {
        parsedResolution(value).map { "\($0.width)x\($0.height)" } ?? value
    }

    static func summary(_ profile: StreamVideoProfile) -> String {
        let mbps = Double(profile.maxBitrateKbps) / 1_000
        return String(format: "%dx%d at %d fps, %.0f Mb/s", profile.width, profile.height, profile.fps, mbps)
    }
}
