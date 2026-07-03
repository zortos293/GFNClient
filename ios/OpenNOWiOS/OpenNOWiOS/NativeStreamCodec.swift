import Foundation
import CoreMedia
import VideoToolbox

#if canImport(WebRTC) && os(iOS)
import WebRTC
#endif

struct NativeStreamCodecCapability: Equatable {
    let codec: NativeStreamVideoCodec
    let videoToolboxHardwareDecode: Bool
    let webRTCSupported: Bool
    let webRTCProfileSummary: [String]

    var launchSafe: Bool {
        switch codec {
        case .h264:
            return webRTCSupported
        case .h265, .av1:
            return videoToolboxHardwareDecode && webRTCSupported
        }
    }
}

struct NativeStreamCodecReport: Equatable {
    let capabilities: [NativeStreamCodecCapability]

    func capability(for codec: NativeStreamVideoCodec) -> NativeStreamCodecCapability? {
        capabilities.first { $0.codec == codec }
    }

    func launchSafeCodec(preferred: String) -> NativeStreamVideoCodec {
        if let requested = NativeStreamVideoCodec.normalized(preferred),
           capability(for: requested)?.launchSafe == true {
            return requested
        }
        return [.h265, .av1, .h264].first { capability(for: $0)?.launchSafe == true } ?? .h264
    }

    var summary: String {
        capabilities.map { capability in
            let safe = capability.launchSafe ? "safe" : "unsafe"
            let vt = capability.videoToolboxHardwareDecode ? "VT=hw" : "VT=no-hw"
            let rtc = capability.webRTCSupported ? "WebRTC=yes" : "WebRTC=no"
            return "\(capability.codec.rawValue):\(safe):\(vt):\(rtc)"
        }.joined(separator: " ")
    }
}

enum NativeStreamCodecProbe {
    static func report() -> NativeStreamCodecReport {
        let supportedWebRTCCodecs = webRTCSupportedCodecs()
        let capabilities = NativeStreamVideoCodec.allCases.map { codec in
            NativeStreamCodecCapability(
                codec: codec,
                videoToolboxHardwareDecode: videoToolboxHardwareDecodeSupported(for: codec),
                webRTCSupported: supportedWebRTCCodecs[codec]?.isEmpty == false,
                webRTCProfileSummary: supportedWebRTCCodecs[codec] ?? []
            )
        }
        return NativeStreamCodecReport(capabilities: capabilities)
    }

    private static func videoToolboxHardwareDecodeSupported(for codec: NativeStreamVideoCodec) -> Bool {
        switch codec {
        case .h264:
            return VTIsHardwareDecodeSupported(kCMVideoCodecType_H264)
        case .h265:
            return VTIsHardwareDecodeSupported(kCMVideoCodecType_HEVC)
        case .av1:
            if #available(iOS 16.0, tvOS 16.0, *) {
                return VTIsHardwareDecodeSupported(kCMVideoCodecType_AV1)
            }
            return false
        }
    }

    private static func webRTCSupportedCodecs() -> [NativeStreamVideoCodec: [String]] {
        #if canImport(WebRTC) && os(iOS)
        let factory = RTCDefaultVideoDecoderFactory()
        return factory.supportedCodecs().reduce(into: [NativeStreamVideoCodec: [String]]()) { partial, info in
            guard let codec = NativeStreamVideoCodec.normalized(info.name) else { return }
            let params = info.parameters
                .map { "\($0.key)=\($0.value)" }
                .sorted()
                .joined(separator: " ")
            let summary = params.isEmpty ? codec.rawValue : "\(codec.rawValue) \(params)"
            partial[codec, default: []].append(summary)
        }
        #else
        return [.h264: ["H264"]]
        #endif
    }
}

enum NativeStreamSelfTest {
    static func run() {
        var failures: [String] = []
        let report = NativeStreamCodecProbe.report()
        print("[STREAMER-SELFTEST] codec report \(report.summary)")
        for codec in NativeStreamVideoCodec.allCases {
            guard let capability = report.capability(for: codec) else {
                failures.append("missing codec capability \(codec.rawValue)")
                continue
            }
            print("[STREAMER-SELFTEST] \(codec.rawValue) profiles \(capability.webRTCProfileSummary.joined(separator: ", "))")
        }

        let encoder = NativeStreamInputEncoder()
        encoder.setProtocolVersion(3)
        let key = encoder.encodeKeyDown(mapping: .init(virtualKey: 0x41, scanCode: 0x001e), modifiers: 0)
        assertSelfTest(key.count == 28 && key.first == 0x23 && key[9] == 0x22, "keyboard packet v3 wrapper", failures: &failures)
        let mouse = encoder.encodeMouseMove(dx: 12, dy: -7)
        assertSelfTest(mouse.count == 34 && mouse.first == 0x23 && mouse[9] == 0x21, "mouse packet v3 wrapper", failures: &failures)
        let gamepad = encoder.encodeGamepadState(
            .init(
                controllerId: 0,
                buttons: 0x1000,
                leftTrigger: 0,
                rightTrigger: 255,
                leftStickX: 0,
                leftStickY: 0,
                rightStickX: 0,
                rightStickY: 0,
                connected: true
            ),
            bitmap: 0x0101,
            partiallyReliable: true
        )
        assertSelfTest(gamepad.count == 54 && gamepad[9] == 0x26 && gamepad[13] == 0x21, "gamepad PR wrapper", failures: &failures)

        let sampleOffer = """
        v=0
        o=- 0 0 IN IP4 0.0.0.0
        s=-
        t=0 0
        a=ri.version:3
        a=ri.partialReliableThresholdMs:45
        a=ri.enablePartiallyReliableTransferGamepad:0x0f
        m=video 9 UDP/TLS/RTP/SAVPF 96 97 98 99
        c=IN IP4 0.0.0.0
        a=rtpmap:96 H264/90000
        a=rtpmap:97 rtx/90000
        a=fmtp:97 apt=96
        a=rtpmap:98 H265/90000
        a=fmtp:98 profile-id=1
        a=rtpmap:99 rtx/90000
        a=fmtp:99 apt=98
        a=candidate:1 1 udp 2130706431 0.0.0.0 49003 typ host
        a=ice-ufrag:offerufrag
        a=ice-pwd:offerpwd
        a=fingerprint:sha-256 AA:BB
        """
        let fixed = NativeStreamSDP.fixServerIP(in: sampleOffer, serverIP: "1-2-3-4.foo")
        assertSelfTest(fixed.contains("a=candidate:1 1 udp 2130706431 1.2.3.4 49003 typ host") && fixed.contains("c=IN IP4 0.0.0.0"), "candidate IP rewrite", failures: &failures)
        assertSelfTest(NativeStreamSDP.parseInputProtocolVersion(from: sampleOffer) == 3, "input protocol parse", failures: &failures)
        assertSelfTest(NativeStreamSDP.parsePartialReliableThresholdMs(from: sampleOffer) == 45, "partial reliable parse", failures: &failures)
        let preferred = NativeStreamSDP.preferCodec(in: sampleOffer, codec: .h265, preferTenBit: false)
        assertSelfTest(preferred.contains("m=video 9 UDP/TLS/RTP/SAVPF 98 99"), "codec preference rewrite", failures: &failures)

        if failures.isEmpty {
            print("[STREAMER-SELFTEST] PASS")
        } else {
            print("[STREAMER-SELFTEST] FAIL \(failures.joined(separator: "; "))")
        }
    }

    private static func assertSelfTest(_ condition: Bool, _ message: String, failures: inout [String]) {
        if condition {
            print("[STREAMER-SELFTEST] ok \(message)")
        } else {
            failures.append(message)
        }
    }
}
