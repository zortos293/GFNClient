import SwiftUI
import VideoToolbox

/// What this device can actually decode, and why a codec choice may not have been honoured.
///
/// The probe already existed and fed the launch resolver silently. Showing it turns "AV1 keeps
/// switching to H.265 and I don't know why" into a screen that answers the question — and gives a
/// bug report something concrete to quote.
struct CodecDiagnosticsView: View {
    @Environment(\.openNowAccent) private var accent

    private let report = NativeStreamCodecProbe.report()

    var body: some View {
        Form {
            ForEach(NativeStreamVideoCodec.allCases, id: \.self) { codec in
                if let capability = report.capability(for: codec) {
                    Section {
                        verdictRow(capability)
                        checkRow(
                            "Hardware decoder",
                            passed: capability.videoToolboxHardwareDecode,
                            detail: capability.videoToolboxHardwareDecode
                                ? "VideoToolbox reports hardware support"
                                : "No hardware path — software decoding is too slow to stream"
                        )
                        checkRow(
                            "WebRTC support",
                            passed: capability.webRTCSupported,
                            detail: capability.webRTCSupported
                                ? "The decoder factory offers this codec"
                                : "The bundled WebRTC build does not offer this codec"
                        )
                        if !capability.webRTCProfileSummary.isEmpty {
                            LabeledContent("Profiles") {
                                Text(capability.webRTCProfileSummary.joined(separator: ", "))
                                    .font(.footnote.monospaced())
                                    .multilineTextAlignment(.trailing)
                            }
                        }
                    } header: {
                        Text(codec.rawValue.uppercased())
                    } footer: {
                        Text(explanation(for: capability))
                    }
                }
            }

            Section {
                LabeledContent("Device", value: OpenNOWPlatform.modelIdentifier)
                LabeledContent("Chosen for auto", value: report.launchSafeCodec(preferred: "Auto").rawValue.uppercased())
                Button {
                    #if canImport(UIKit)
                    UIPasteboard.general.string = report.summary
                    #endif
                } label: {
                    Label("Copy Probe Summary", systemImage: "doc.on.doc")
                }
            } header: {
                Text("Result")
            } footer: {
                Text("H.264 only needs WebRTC support because every device that runs iOS 16 decodes it in hardware. H.265 and AV1 need both checks to pass, because a software fallback would drop frames rather than degrade gracefully.")
            }
        }
        .navigationTitle("Decoders")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func verdictRow(_ capability: NativeStreamCodecCapability) -> some View {
        HStack {
            Text("Usable for streaming")
            Spacer()
            Label(
                capability.launchSafe ? "Yes" : "No",
                systemImage: capability.launchSafe ? "checkmark.circle.fill" : "xmark.circle.fill"
            )
            .labelStyle(.titleAndIcon)
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(capability.launchSafe ? accent.color : OpenNOWPalette.statusPoor)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(capability.codec.rawValue.uppercased()) usable for streaming")
        .accessibilityValue(capability.launchSafe ? "Yes" : "No")
    }

    private func checkRow(_ title: String, passed: Bool, detail: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: OpenNOWSpacing.md) {
            Image(systemName: passed ? "checkmark.circle.fill" : "xmark.circle.fill")
                .font(.footnote)
                .foregroundStyle(passed ? accent.color : OpenNOWPalette.textMuted)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                Text(detail)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(title): \(passed ? "passed" : "failed"). \(detail)")
    }

    private func explanation(for capability: NativeStreamCodecCapability) -> String {
        if capability.launchSafe {
            return "Selecting \(capability.codec.rawValue.uppercased()) in Settings will be honoured."
        }
        if !capability.webRTCSupported {
            return "Selecting \(capability.codec.rawValue.uppercased()) falls back to H.264 at launch."
        }
        return "\(capability.codec.rawValue.uppercased()) is offered by WebRTC but has no hardware decoder here, so it falls back to H.264 rather than dropping frames."
    }
}
