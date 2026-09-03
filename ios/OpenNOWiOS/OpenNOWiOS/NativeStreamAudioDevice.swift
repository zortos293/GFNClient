import AVFoundation
import Foundation

#if os(iOS) && canImport(WebRTC)
@preconcurrency import WebRTC

final class NativeStreamMutedAudioDevice: NSObject, RTCAudioDevice {
    private weak var delegate: RTCAudioDeviceDelegate?
    private var initialized = false
    private var playoutInitialized = false
    private var recordingInitialized = false
    private var playing = false
    private var recording = false

    var deviceInputSampleRate: Double { 48_000 }
    var inputIOBufferDuration: TimeInterval { 0.01 }
    var inputNumberOfChannels: Int { 1 }
    var inputLatency: TimeInterval { 0 }
    var deviceOutputSampleRate: Double { 48_000 }
    var outputIOBufferDuration: TimeInterval { 0.01 }
    var outputNumberOfChannels: Int { 2 }
    var outputLatency: TimeInterval { 0 }
    var isInitialized: Bool { initialized }
    var isPlayoutInitialized: Bool { playoutInitialized }
    var isPlaying: Bool { playing }
    var isRecordingInitialized: Bool { recordingInitialized }
    var isRecording: Bool { recording }

    func initialize(with delegate: RTCAudioDeviceDelegate) -> Bool {
        self.delegate = delegate
        initialized = true
        return true
    }

    func terminateDevice() -> Bool {
        recording = false
        playing = false
        recordingInitialized = false
        playoutInitialized = false
        initialized = false
        delegate = nil
        return true
    }

    func initializePlayout() -> Bool {
        playoutInitialized = true
        return true
    }

    func startPlayout() -> Bool {
        playoutInitialized = true
        playing = true
        return true
    }

    func stopPlayout() -> Bool {
        playing = false
        return true
    }

    func initializeRecording() -> Bool {
        recordingInitialized = true
        return true
    }

    func startRecording() -> Bool {
        recordingInitialized = true
        recording = true
        return true
    }

    func stopRecording() -> Bool {
        recording = false
        return true
    }
}

/// Output-only WebRTC audio for game streaming.
///
/// WebRTC's built-in iOS device is a VoiceProcessingIO unit and therefore makes iOS treat remote
/// game audio like a call even when the app never publishes a microphone track. This device pulls
/// the same decoded PCM from WebRTC into an `AVAudioEngine` source node while owning a normal
/// playback-category audio session. It never opens an input node or asks for microphone access.
final class NativeStreamPlaybackAudioDevice: NSObject, RTCAudioDevice {
    // Retain the callback bridge for the full custom-device lifecycle, matching WebRTC's reference
    // implementation. The output render block cannot pull decoded PCM after this bridge is gone.
    private var delegate: RTCAudioDeviceDelegate?
    private var engine: AVAudioEngine?
    private var sourceNode: AVAudioSourceNode?
    private var initialized = false
    private var playoutInitialized = false
    private var recordingInitialized = false
    private var playoutRequested = false
    private var recordingRequested = false
    private var playbackEnabled: Bool
    private var outputSampleRate = 48_000.0
    private var outputChannelCount = 2
    private let onEvent: @Sendable (String) -> Void

    init(
        playbackEnabled: Bool,
        onEvent: @escaping @Sendable (String) -> Void = { _ in }
    ) {
        self.playbackEnabled = playbackEnabled
        self.onEvent = onEvent
        super.init()
    }

    var deviceInputSampleRate: Double { 48_000 }
    var inputIOBufferDuration: TimeInterval { 0.01 }
    var inputNumberOfChannels: Int { 1 }
    var inputLatency: TimeInterval { 0 }
    var deviceOutputSampleRate: Double { outputSampleRate }
    var outputIOBufferDuration: TimeInterval {
        let duration = AVAudioSession.sharedInstance().ioBufferDuration
        return duration > 0 ? duration : 0.01
    }
    var outputNumberOfChannels: Int { outputChannelCount }
    var outputLatency: TimeInterval { AVAudioSession.sharedInstance().outputLatency }
    var isInitialized: Bool { initialized }
    var isPlayoutInitialized: Bool { playoutInitialized }
    var isPlaying: Bool { playoutRequested }
    var isRecordingInitialized: Bool { recordingInitialized }
    var isRecording: Bool { recordingRequested }

    func initialize(with delegate: RTCAudioDeviceDelegate) -> Bool {
        guard self.delegate == nil else { return false }
        self.delegate = delegate
        initialized = true
        onEvent("Media audio device initialized")
        return true
    }

    func terminateDevice() -> Bool {
        stopEngine(deactivateSession: true)
        recordingRequested = false
        playoutRequested = false
        recordingInitialized = false
        playoutInitialized = false
        initialized = false
        delegate = nil
        return true
    }

    func initializePlayout() -> Bool {
        guard initialized else { return false }
        playoutInitialized = true
        return true
    }

    func startPlayout() -> Bool {
        guard initialized else { return false }
        playoutInitialized = true
        playoutRequested = true
        guard playbackEnabled else { return true }
        let started = startEngine()
        onEvent("Media audio playout requested started=\(started)")
        return started
    }

    func stopPlayout() -> Bool {
        playoutRequested = false
        stopEngine(deactivateSession: true)
        return true
    }

    // OpenNOW never publishes an upstream audio track. Satisfy the ADM lifecycle without opening
    // AVAudioEngine's input node, so an unexpected WebRTC recording request remains silent.
    func initializeRecording() -> Bool {
        recordingInitialized = true
        return true
    }

    func startRecording() -> Bool {
        recordingInitialized = true
        recordingRequested = true
        return true
    }

    func stopRecording() -> Bool {
        recordingRequested = false
        return true
    }

    /// May be called from the main actor. Marshal engine changes onto WebRTC's ADM thread once the
    /// device has been initialized so the render callback and lifecycle cannot race each other.
    func setPlaybackEnabled(_ enabled: Bool) {
        guard let delegate else {
            playbackEnabled = enabled
            return
        }
        delegate.dispatchAsync { [weak self] in
            guard let self else { return }
            self.playbackEnabled = enabled
            if enabled, self.playoutRequested {
                _ = self.startEngine()
            } else if !enabled {
                self.stopEngine(deactivateSession: true)
            }
        }
    }

    func recoverAudioOutput() {
        guard let delegate else { return }
        delegate.dispatchAsync { [weak self, weak delegate] in
            guard let self, let delegate else { return }
            delegate.notifyAudioOutputInterrupted()
            self.stopEngine(deactivateSession: false)
            if self.playbackEnabled, self.playoutRequested {
                _ = self.startEngine()
            }
        }
    }

    private func startEngine() -> Bool {
        if engine?.isRunning == true { return true }
        guard let delegate else { return false }

        stopEngine(deactivateSession: false)
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(
                NativeStreamAudioSessionPolicy.category(enableMic: false),
                mode: NativeStreamAudioSessionPolicy.mode(enableMic: false),
                options: NativeStreamAudioSessionPolicy.options(enableMic: false)
            )
            try? session.setPreferredSampleRate(48_000)
            try? session.setPreferredIOBufferDuration(0.01)
            if session.maximumOutputNumberOfChannels >= 2 {
                try? session.setPreferredOutputNumberOfChannels(2)
            }
            try session.setActive(true)
        } catch {
            onEvent("Media audio session failed: \(error.localizedDescription)")
            return false
        }

        let engine = AVAudioEngine()
        let hardwareFormat = engine.outputNode.outputFormat(forBus: 0)
        guard hardwareFormat.sampleRate > 0, hardwareFormat.channelCount > 0 else {
            onEvent("Media audio output has no active hardware format")
            return false
        }
        outputSampleRate = hardwareFormat.sampleRate
        outputChannelCount = Int(hardwareFormat.channelCount)
        guard let renderFormat = AVAudioFormat(
            commonFormat: .pcmFormatInt16,
            sampleRate: outputSampleRate,
            channels: AVAudioChannelCount(outputChannelCount),
            interleaved: true
        ) else {
            onEvent("Media audio output could not create WebRTC PCM format")
            return false
        }

        let sourceNode = AVAudioSourceNode(format: renderFormat) {
            [weak delegate] isSilence, timestamp, frameCount, outputData in
            guard let delegate else {
                let buffers = UnsafeMutableAudioBufferListPointer(outputData)
                for buffer in buffers {
                    guard let data = buffer.mData else { continue }
                    memset(data, 0, Int(buffer.mDataByteSize))
                }
                isSilence.pointee = true
                return noErr
            }
            var actionFlags = AudioUnitRenderActionFlags()
            let status = delegate.getPlayoutData(
                &actionFlags,
                timestamp,
                0,
                frameCount,
                outputData
            )
            isSilence.pointee = ObjCBool(
                actionFlags.contains(.unitRenderAction_OutputIsSilence)
            )
            return status
        }
        engine.attach(sourceNode)
        // AVAudioEngine's output graph must use the route's native format. The source node still
        // asks WebRTC for interleaved Int16 PCM, while the engine performs the conversion into the
        // hardware format. Connecting Int16 directly to the mixer can fail to start, or start with
        // silence, on routes whose mixer is float/non-interleaved (including common speakers and
        // Bluetooth outputs).
        engine.connect(engine.mainMixerNode, to: engine.outputNode, format: hardwareFormat)
        engine.connect(sourceNode, to: engine.mainMixerNode, format: hardwareFormat)
        engine.prepare()
        // The device properties now reflect the active route. WebRTC must see those values before
        // the source node begins requesting PCM, especially when A2DP changes 48 kHz stereo to a
        // different hardware rate.
        delegate.notifyAudioOutputParametersChange()
        do {
            try engine.start()
        } catch {
            engine.detach(sourceNode)
            onEvent("Media audio engine failed: \(error.localizedDescription)")
            return false
        }
        self.engine = engine
        self.sourceNode = sourceNode
        onEvent(
            "Media audio output started sampleRate=\(Int(outputSampleRate)) "
                + "channels=\(outputChannelCount) route=\(session.currentRoute.outputs.map(\.portType.rawValue).joined(separator: ","))"
        )
        return true
    }

    private func stopEngine(deactivateSession: Bool) {
        engine?.stop()
        if let engine, let sourceNode {
            engine.disconnectNodeOutput(sourceNode)
            engine.detach(sourceNode)
        }
        sourceNode = nil
        engine = nil
        guard deactivateSession else { return }
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}
#endif
