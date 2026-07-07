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
#endif
