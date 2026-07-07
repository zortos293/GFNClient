import Foundation
import CoreGraphics

#if canImport(GameController)
import GameController
#endif

#if canImport(CoreHaptics)
import CoreHaptics
#endif

protocol NativeStreamInputSink: AnyObject {
    func sendReliableInput(_ data: Data)
    func sendPartiallyReliableInput(_ data: Data)
    func logInputEvent(_ message: String)
}

struct NativeStreamKeyboardMapping: Equatable {
    let virtualKey: UInt16
    let scanCode: UInt16
}

struct NativeStreamGamepadState: Equatable {
    let controllerId: Int
    let buttons: UInt16
    let leftTrigger: UInt8
    let rightTrigger: UInt8
    let leftStickX: Int16
    let leftStickY: Int16
    let rightStickX: Int16
    let rightStickY: Int16
    let connected: Bool
}

final class NativeStreamInputEncoder {
    private enum EventType {
        static let heartbeat: UInt32 = 2
        static let keyDown: UInt32 = 3
        static let keyUp: UInt32 = 4
        static let mouseRelative: UInt32 = 7
        static let mouseButtonDown: UInt32 = 8
        static let mouseButtonUp: UInt32 = 9
        static let mouseWheel: UInt32 = 10
        static let gamepad: UInt32 = 12
        static let hapticsEnabled: UInt32 = 13
    }

    private var protocolVersion = NativeStreamSDP.defaultInputProtocolVersion
    private var gamepadSequences: [Int: UInt16] = [:]

    func setProtocolVersion(_ version: Int) {
        protocolVersion = max(1, min(255, version))
    }

    func resetGamepadSequences() {
        gamepadSequences.removeAll(keepingCapacity: true)
    }

    func encodeHeartbeat() -> Data {
        var bytes = [UInt8](repeating: 0, count: 4)
        Self.writeUInt32LE(EventType.heartbeat, to: &bytes, at: 0)
        return Data(bytes)
    }

    func encodeKeyDown(mapping: NativeStreamKeyboardMapping, modifiers: UInt16) -> Data {
        encodeKey(type: EventType.keyDown, mapping: mapping, modifiers: modifiers)
    }

    func encodeKeyUp(mapping: NativeStreamKeyboardMapping, modifiers: UInt16) -> Data {
        encodeKey(type: EventType.keyUp, mapping: mapping, modifiers: modifiers)
    }

    func encodeMouseMove(dx: Int, dy: Int) -> Data {
        var bytes = [UInt8](repeating: 0, count: 22)
        Self.writeUInt32LE(EventType.mouseRelative, to: &bytes, at: 0)
        Self.writeInt16BE(Self.clampInt16(dx), to: &bytes, at: 4)
        Self.writeInt16BE(Self.clampInt16(dy), to: &bytes, at: 6)
        Self.writeUInt16BE(0, to: &bytes, at: 8)
        Self.writeUInt32BE(0, to: &bytes, at: 10)
        Self.writeUInt64BE(Self.timestampUs(), to: &bytes, at: 14)
        return wrapMouseMove(Data(bytes))
    }

    func encodeMouseButton(button: Int, pressed: Bool) -> Data {
        var bytes = [UInt8](repeating: 0, count: 18)
        Self.writeUInt32LE(pressed ? EventType.mouseButtonDown : EventType.mouseButtonUp, to: &bytes, at: 0)
        bytes[4] = UInt8(min(max(button, 1), 5))
        bytes[5] = 0
        Self.writeUInt32BE(0, to: &bytes, at: 6)
        Self.writeUInt64BE(Self.timestampUs(), to: &bytes, at: 10)
        return wrapSingle(Data(bytes))
    }

    func encodeMouseWheel(delta: Int) -> Data {
        var bytes = [UInt8](repeating: 0, count: 22)
        Self.writeUInt32LE(EventType.mouseWheel, to: &bytes, at: 0)
        Self.writeInt16BE(0, to: &bytes, at: 4)
        Self.writeInt16BE(Self.clampInt16(delta), to: &bytes, at: 6)
        Self.writeUInt16BE(0, to: &bytes, at: 8)
        Self.writeUInt32BE(0, to: &bytes, at: 10)
        Self.writeUInt64BE(Self.timestampUs(), to: &bytes, at: 14)
        return wrapSingle(Data(bytes))
    }

    func encodeHapticsEnabled(_ enabled: Bool) -> Data {
        var bytes = [UInt8](repeating: 0, count: 6)
        Self.writeUInt32LE(EventType.hapticsEnabled, to: &bytes, at: 0)
        Self.writeUInt16BE(enabled ? 1 : 0, to: &bytes, at: 4)
        return wrapSingle(Data(bytes))
    }

    func encodeGamepadState(_ state: NativeStreamGamepadState, bitmap: UInt16, partiallyReliable: Bool) -> Data {
        var bytes = [UInt8](repeating: 0, count: 38)
        Self.writeUInt32LE(EventType.gamepad, to: &bytes, at: 0)
        Self.writeUInt16LE(26, to: &bytes, at: 4)
        Self.writeUInt16LE(UInt16(state.controllerId & 0x03), to: &bytes, at: 6)
        Self.writeUInt16LE(bitmap, to: &bytes, at: 8)
        Self.writeUInt16LE(20, to: &bytes, at: 10)
        Self.writeUInt16LE(state.buttons, to: &bytes, at: 12)
        Self.writeUInt16LE(UInt16(state.leftTrigger) | (UInt16(state.rightTrigger) << 8), to: &bytes, at: 14)
        Self.writeInt16LE(state.leftStickX, to: &bytes, at: 16)
        Self.writeInt16LE(state.leftStickY, to: &bytes, at: 18)
        Self.writeInt16LE(state.rightStickX, to: &bytes, at: 20)
        Self.writeInt16LE(state.rightStickY, to: &bytes, at: 22)
        Self.writeUInt16LE(0, to: &bytes, at: 24)
        Self.writeUInt16LE(85, to: &bytes, at: 26)
        Self.writeUInt16LE(0, to: &bytes, at: 28)
        Self.writeUInt64LE(Self.timestampUs(), to: &bytes, at: 30)
        return partiallyReliable
            ? wrapGamepadPartiallyReliable(Data(bytes), controllerId: state.controllerId)
            : wrapGamepadReliable(Data(bytes))
    }

    private func encodeKey(type: UInt32, mapping: NativeStreamKeyboardMapping, modifiers: UInt16) -> Data {
        var bytes = [UInt8](repeating: 0, count: 18)
        Self.writeUInt32LE(type, to: &bytes, at: 0)
        Self.writeUInt16BE(mapping.virtualKey, to: &bytes, at: 4)
        Self.writeUInt16BE(modifiers, to: &bytes, at: 6)
        Self.writeUInt16BE(mapping.scanCode, to: &bytes, at: 8)
        Self.writeUInt64BE(Self.timestampUs(), to: &bytes, at: 10)
        return wrapSingle(Data(bytes))
    }

    private func wrapSingle(_ payload: Data) -> Data {
        guard protocolVersion > 2 else { return payload }
        var bytes = [UInt8](repeating: 0, count: 10 + payload.count)
        bytes[0] = 0x23
        Self.writeUInt64BE(Self.timestampUs(), to: &bytes, at: 1)
        bytes[9] = 0x22
        bytes.replaceSubrange(10..<(10 + payload.count), with: payload)
        return Data(bytes)
    }

    private func wrapMouseMove(_ payload: Data) -> Data {
        guard protocolVersion > 2 else { return payload }
        var bytes = [UInt8](repeating: 0, count: 12 + payload.count)
        bytes[0] = 0x23
        Self.writeUInt64BE(Self.timestampUs(), to: &bytes, at: 1)
        bytes[9] = 0x21
        Self.writeUInt16BE(UInt16(payload.count), to: &bytes, at: 10)
        bytes.replaceSubrange(12..<(12 + payload.count), with: payload)
        return Data(bytes)
    }

    private func wrapGamepadReliable(_ payload: Data) -> Data {
        guard protocolVersion > 2 else { return payload }
        var bytes = [UInt8](repeating: 0, count: 12 + payload.count)
        bytes[0] = 0x23
        Self.writeUInt64BE(Self.timestampUs(), to: &bytes, at: 1)
        bytes[9] = 0x21
        Self.writeUInt16BE(UInt16(payload.count), to: &bytes, at: 10)
        bytes.replaceSubrange(12..<(12 + payload.count), with: payload)
        return Data(bytes)
    }

    private func wrapGamepadPartiallyReliable(_ payload: Data, controllerId: Int) -> Data {
        guard protocolVersion > 2 else { return payload }
        let index = controllerId & 0x03
        let sequence = gamepadSequences[index] ?? 1
        gamepadSequences[index] = sequence &+ 1
        var bytes = [UInt8](repeating: 0, count: 16 + payload.count)
        bytes[0] = 0x23
        Self.writeUInt64BE(Self.timestampUs(), to: &bytes, at: 1)
        bytes[9] = 0x26
        bytes[10] = UInt8(index)
        Self.writeUInt16BE(sequence, to: &bytes, at: 11)
        bytes[13] = 0x21
        Self.writeUInt16BE(UInt16(payload.count), to: &bytes, at: 14)
        bytes.replaceSubrange(16..<(16 + payload.count), with: payload)
        return Data(bytes)
    }

    private static func timestampUs() -> UInt64 {
        UInt64((ProcessInfo.processInfo.systemUptime * 1_000_000).rounded())
    }

    private static func clampInt16(_ value: Int) -> Int16 {
        Int16(min(max(value, Int(Int16.min)), Int(Int16.max)))
    }

    private static func writeUInt16LE(_ value: UInt16, to bytes: inout [UInt8], at offset: Int) {
        bytes[offset] = UInt8(value & 0xff)
        bytes[offset + 1] = UInt8((value >> 8) & 0xff)
    }

    private static func writeUInt16BE(_ value: UInt16, to bytes: inout [UInt8], at offset: Int) {
        bytes[offset] = UInt8((value >> 8) & 0xff)
        bytes[offset + 1] = UInt8(value & 0xff)
    }

    private static func writeInt16LE(_ value: Int16, to bytes: inout [UInt8], at offset: Int) {
        writeUInt16LE(UInt16(bitPattern: value), to: &bytes, at: offset)
    }

    private static func writeInt16BE(_ value: Int16, to bytes: inout [UInt8], at offset: Int) {
        writeUInt16BE(UInt16(bitPattern: value), to: &bytes, at: offset)
    }

    private static func writeUInt32LE(_ value: UInt32, to bytes: inout [UInt8], at offset: Int) {
        bytes[offset] = UInt8(value & 0xff)
        bytes[offset + 1] = UInt8((value >> 8) & 0xff)
        bytes[offset + 2] = UInt8((value >> 16) & 0xff)
        bytes[offset + 3] = UInt8((value >> 24) & 0xff)
    }

    private static func writeUInt32BE(_ value: UInt32, to bytes: inout [UInt8], at offset: Int) {
        bytes[offset] = UInt8((value >> 24) & 0xff)
        bytes[offset + 1] = UInt8((value >> 16) & 0xff)
        bytes[offset + 2] = UInt8((value >> 8) & 0xff)
        bytes[offset + 3] = UInt8(value & 0xff)
    }

    private static func writeUInt64LE(_ value: UInt64, to bytes: inout [UInt8], at offset: Int) {
        for index in 0..<8 {
            bytes[offset + index] = UInt8((value >> UInt64(index * 8)) & 0xff)
        }
    }

    private static func writeUInt64BE(_ value: UInt64, to bytes: inout [UInt8], at offset: Int) {
        for index in 0..<8 {
            let shift = UInt64((7 - index) * 8)
            bytes[offset + index] = UInt8((value >> shift) & 0xff)
        }
    }
}

#if canImport(GameController)
enum NativeStreamKeyboardMapper {
    static func mapping(for keyCode: GCKeyCode) -> NativeStreamKeyboardMapping? {
        mappingByKey[keyCode]
    }

    static func modifiers(for keyboardInput: GCKeyboardInput, changedKey: GCKeyCode, pressed: Bool) -> UInt16 {
        var flags: UInt16 = 0
        if isPressed(.leftShift, or: .rightShift, in: keyboardInput, changedKey: changedKey, pressed: pressed) { flags |= 0x01 }
        if isPressed(.leftControl, or: .rightControl, in: keyboardInput, changedKey: changedKey, pressed: pressed) { flags |= 0x02 }
        if isPressed(.leftAlt, or: .rightAlt, in: keyboardInput, changedKey: changedKey, pressed: pressed) { flags |= 0x04 }
        if isPressed(.leftGUI, or: .rightGUI, in: keyboardInput, changedKey: changedKey, pressed: pressed) { flags |= 0x08 }
        if isKeyPressed(.capsLock, in: keyboardInput, changedKey: changedKey, pressed: pressed) { flags |= 0x10 }
        if isKeyPressed(.keypadNumLock, in: keyboardInput, changedKey: changedKey, pressed: pressed) { flags |= 0x20 }
        return flags
    }

    private static func isPressed(
        _ lhs: GCKeyCode,
        or rhs: GCKeyCode,
        in keyboardInput: GCKeyboardInput,
        changedKey: GCKeyCode,
        pressed: Bool
    ) -> Bool {
        isKeyPressed(lhs, in: keyboardInput, changedKey: changedKey, pressed: pressed)
            || isKeyPressed(rhs, in: keyboardInput, changedKey: changedKey, pressed: pressed)
    }

    private static func isKeyPressed(
        _ keyCode: GCKeyCode,
        in keyboardInput: GCKeyboardInput,
        changedKey: GCKeyCode,
        pressed: Bool
    ) -> Bool {
        changedKey == keyCode ? pressed : (keyboardInput.button(forKeyCode: keyCode)?.isPressed == true)
    }

    private static let mappingByKey: [GCKeyCode: NativeStreamKeyboardMapping] = [
        .keyA: .init(virtualKey: 0x41, scanCode: 0x001e),
        .keyB: .init(virtualKey: 0x42, scanCode: 0x0030),
        .keyC: .init(virtualKey: 0x43, scanCode: 0x002e),
        .keyD: .init(virtualKey: 0x44, scanCode: 0x0020),
        .keyE: .init(virtualKey: 0x45, scanCode: 0x0012),
        .keyF: .init(virtualKey: 0x46, scanCode: 0x0021),
        .keyG: .init(virtualKey: 0x47, scanCode: 0x0022),
        .keyH: .init(virtualKey: 0x48, scanCode: 0x0023),
        .keyI: .init(virtualKey: 0x49, scanCode: 0x0017),
        .keyJ: .init(virtualKey: 0x4a, scanCode: 0x0024),
        .keyK: .init(virtualKey: 0x4b, scanCode: 0x0025),
        .keyL: .init(virtualKey: 0x4c, scanCode: 0x0026),
        .keyM: .init(virtualKey: 0x4d, scanCode: 0x0032),
        .keyN: .init(virtualKey: 0x4e, scanCode: 0x0031),
        .keyO: .init(virtualKey: 0x4f, scanCode: 0x0018),
        .keyP: .init(virtualKey: 0x50, scanCode: 0x0019),
        .keyQ: .init(virtualKey: 0x51, scanCode: 0x0010),
        .keyR: .init(virtualKey: 0x52, scanCode: 0x0013),
        .keyS: .init(virtualKey: 0x53, scanCode: 0x001f),
        .keyT: .init(virtualKey: 0x54, scanCode: 0x0014),
        .keyU: .init(virtualKey: 0x55, scanCode: 0x0016),
        .keyV: .init(virtualKey: 0x56, scanCode: 0x002f),
        .keyW: .init(virtualKey: 0x57, scanCode: 0x0011),
        .keyX: .init(virtualKey: 0x58, scanCode: 0x002d),
        .keyY: .init(virtualKey: 0x59, scanCode: 0x0015),
        .keyZ: .init(virtualKey: 0x5a, scanCode: 0x002c),
        .one: .init(virtualKey: 0x31, scanCode: 0x0002),
        .two: .init(virtualKey: 0x32, scanCode: 0x0003),
        .three: .init(virtualKey: 0x33, scanCode: 0x0004),
        .four: .init(virtualKey: 0x34, scanCode: 0x0005),
        .five: .init(virtualKey: 0x35, scanCode: 0x0006),
        .six: .init(virtualKey: 0x36, scanCode: 0x0007),
        .seven: .init(virtualKey: 0x37, scanCode: 0x0008),
        .eight: .init(virtualKey: 0x38, scanCode: 0x0009),
        .nine: .init(virtualKey: 0x39, scanCode: 0x000a),
        .zero: .init(virtualKey: 0x30, scanCode: 0x000b),
        .returnOrEnter: .init(virtualKey: 0x0d, scanCode: 0x001c),
        .escape: .init(virtualKey: 0x1b, scanCode: 0x0001),
        .deleteOrBackspace: .init(virtualKey: 0x08, scanCode: 0x000e),
        .tab: .init(virtualKey: 0x09, scanCode: 0x000f),
        .spacebar: .init(virtualKey: 0x20, scanCode: 0x0039),
        .hyphen: .init(virtualKey: 0xbd, scanCode: 0x000c),
        .equalSign: .init(virtualKey: 0xbb, scanCode: 0x000d),
        .openBracket: .init(virtualKey: 0xdb, scanCode: 0x001a),
        .closeBracket: .init(virtualKey: 0xdd, scanCode: 0x001b),
        .backslash: .init(virtualKey: 0xdc, scanCode: 0x002b),
        .semicolon: .init(virtualKey: 0xba, scanCode: 0x0027),
        .quote: .init(virtualKey: 0xde, scanCode: 0x0028),
        .graveAccentAndTilde: .init(virtualKey: 0xc0, scanCode: 0x0029),
        .comma: .init(virtualKey: 0xbc, scanCode: 0x0033),
        .period: .init(virtualKey: 0xbe, scanCode: 0x0034),
        .slash: .init(virtualKey: 0xbf, scanCode: 0x0035),
        .capsLock: .init(virtualKey: 0x14, scanCode: 0x003a),
        .F1: .init(virtualKey: 0x70, scanCode: 0x003b),
        .F2: .init(virtualKey: 0x71, scanCode: 0x003c),
        .F3: .init(virtualKey: 0x72, scanCode: 0x003d),
        .F4: .init(virtualKey: 0x73, scanCode: 0x003e),
        .F5: .init(virtualKey: 0x74, scanCode: 0x003f),
        .F6: .init(virtualKey: 0x75, scanCode: 0x0040),
        .F7: .init(virtualKey: 0x76, scanCode: 0x0041),
        .F8: .init(virtualKey: 0x77, scanCode: 0x0042),
        .F9: .init(virtualKey: 0x78, scanCode: 0x0043),
        .F10: .init(virtualKey: 0x79, scanCode: 0x0044),
        .F11: .init(virtualKey: 0x7a, scanCode: 0x0057),
        .F12: .init(virtualKey: 0x7b, scanCode: 0x0058),
        .printScreen: .init(virtualKey: 0x2c, scanCode: 0xe037),
        .scrollLock: .init(virtualKey: 0x91, scanCode: 0x0046),
        .pause: .init(virtualKey: 0x13, scanCode: 0x0045),
        .insert: .init(virtualKey: 0x2d, scanCode: 0xe052),
        .home: .init(virtualKey: 0x24, scanCode: 0xe047),
        .pageUp: .init(virtualKey: 0x21, scanCode: 0xe049),
        .deleteForward: .init(virtualKey: 0x2e, scanCode: 0xe053),
        .end: .init(virtualKey: 0x23, scanCode: 0xe04f),
        .pageDown: .init(virtualKey: 0x22, scanCode: 0xe051),
        .rightArrow: .init(virtualKey: 0x27, scanCode: 0xe04d),
        .leftArrow: .init(virtualKey: 0x25, scanCode: 0xe04b),
        .downArrow: .init(virtualKey: 0x28, scanCode: 0xe050),
        .upArrow: .init(virtualKey: 0x26, scanCode: 0xe048),
        .leftControl: .init(virtualKey: 0xa2, scanCode: 0x001d),
        .leftShift: .init(virtualKey: 0xa0, scanCode: 0x002a),
        .leftAlt: .init(virtualKey: 0xa4, scanCode: 0x0038),
        .leftGUI: .init(virtualKey: 0x5b, scanCode: 0xe05b),
        .rightControl: .init(virtualKey: 0xa3, scanCode: 0xe01d),
        .rightShift: .init(virtualKey: 0xa1, scanCode: 0x0036),
        .rightAlt: .init(virtualKey: 0xa5, scanCode: 0xe038),
        .rightGUI: .init(virtualKey: 0x5c, scanCode: 0xe05c),
        .keypadEnter: .init(virtualKey: 0x0d, scanCode: 0xe01c),
        .application: .init(virtualKey: 0x5d, scanCode: 0xe05d)
    ]
}

final class NativeStreamInputBridge {
    weak var sink: NativeStreamInputSink?

    private let encoder = NativeStreamInputEncoder()
    private var keyboard: GCKeyboard?
    private var mice: [GCMouse] = []
    private var controllers: [GCController] = []
    private var lastGamepadStates: [Int: NativeStreamGamepadState] = [:]
    private var gamepadKeepaliveTimer: Timer?
    private var heartbeatTimer: Timer?
    private var mouseAccumulator = CGPoint.zero
    private var mouseFlushScheduled = false
    private var partiallyReliableGamepadMask = UInt16(NativeStreamSDP.partiallyReliableGamepadMaskAll)
    #if canImport(CoreHaptics)
    private var hapticEngines: [ObjectIdentifier: CHHapticEngine] = [:]
    #endif

    func configure(protocolVersion: Int, partiallyReliableGamepadMask: Int) {
        encoder.setProtocolVersion(protocolVersion)
        self.partiallyReliableGamepadMask = UInt16(partiallyReliableGamepadMask & 0xffff)
    }

    func attach() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(refreshDevices),
            name: NSNotification.Name.GCKeyboardDidConnect,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(refreshDevices),
            name: NSNotification.Name.GCKeyboardDidDisconnect,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(refreshDevices),
            name: NSNotification.Name.GCMouseDidConnect,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(refreshDevices),
            name: NSNotification.Name.GCMouseDidDisconnect,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(refreshDevices),
            name: NSNotification.Name.GCControllerDidConnect,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(refreshDevices),
            name: NSNotification.Name.GCControllerDidDisconnect,
            object: nil
        )
        refreshDevices()
        startTimers()
    }

    func detach() {
        NotificationCenter.default.removeObserver(self)
        keyboard?.keyboardInput?.keyChangedHandler = nil
        mice.forEach { mouse in
            guard let input = mouse.mouseInput else { return }
            input.mouseMovedHandler = nil
            input.leftButton.pressedChangedHandler = nil
            input.rightButton?.pressedChangedHandler = nil
            input.middleButton?.pressedChangedHandler = nil
            input.scroll.valueChangedHandler = nil
        }
        controllers.forEach { $0.extendedGamepad?.valueChangedHandler = nil }
        keyboard = nil
        mice = []
        controllers = []
        lastGamepadStates.removeAll(keepingCapacity: true)
        heartbeatTimer?.invalidate()
        heartbeatTimer = nil
        gamepadKeepaliveTimer?.invalidate()
        gamepadKeepaliveTimer = nil
        stopAllRumble()
    }

    func sendTouchMouseMove(dx: CGFloat, dy: CGFloat) {
        let scale: CGFloat = 1.35
        let x = Int((dx * scale).rounded())
        let y = Int((dy * scale).rounded())
        guard x != 0 || y != 0 else { return }
        sink?.sendPartiallyReliableInput(encoder.encodeMouseMove(dx: x, dy: y))
    }

    func sendMouseButton(_ button: Int, pressed: Bool) {
        sink?.sendReliableInput(encoder.encodeMouseButton(button: button, pressed: pressed))
    }

    func sendKey(mapping: NativeStreamKeyboardMapping, pressed: Bool, modifiers: UInt16) {
        let data = pressed
            ? encoder.encodeKeyDown(mapping: mapping, modifiers: modifiers)
            : encoder.encodeKeyUp(mapping: mapping, modifiers: modifiers)
        sink?.sendReliableInput(data)
    }

    func sendMouseWheel(delta: Int) {
        sink?.sendReliableInput(encoder.encodeMouseWheel(delta: delta))
    }

    func advertiseHaptics() {
        let available = controllers.contains { $0.haptics != nil }
        sink?.sendReliableInput(encoder.encodeHapticsEnabled(available))
    }

    func applyRumble(controllerId: Int, weakMagnitude: Int, strongMagnitude: Int) {
        #if canImport(CoreHaptics)
        let hapticControllers = controllers.filter { $0.haptics != nil }
        let selected: GCController? = {
            if controllerId >= 0, controllerId < controllers.count, controllers[controllerId].haptics != nil {
                return controllers[controllerId]
            }
            if controllerId >= 0, controllerId < hapticControllers.count {
                return hapticControllers[controllerId]
            }
            return hapticControllers.count == 1 ? hapticControllers[0] : nil
        }()
        guard let controller = selected else { return }
        let weak = min(max(Float(weakMagnitude) / 65_535.0, 0), 1)
        let strong = min(max(Float(strongMagnitude) / 65_535.0, 0), 1)
        let intensity = max(weak, strong)
        guard intensity > 0 else {
            stopRumble(for: controller)
            return
        }
        do {
            let engine = try hapticEngine(for: controller)
            let sharpness = min(max((strong * 0.75) + (weak * 0.25), 0), 1)
            let event = CHHapticEvent(
                eventType: .hapticContinuous,
                parameters: [
                    CHHapticEventParameter(parameterID: .hapticIntensity, value: intensity),
                    CHHapticEventParameter(parameterID: .hapticSharpness, value: sharpness)
                ],
                relativeTime: 0,
                duration: 0.18
            )
            let pattern = try CHHapticPattern(events: [event], parameters: [])
            try engine.start()
            try engine.makePlayer(with: pattern).start(atTime: CHHapticTimeImmediate)
        } catch {
            sink?.logInputEvent("Controller rumble failed: \(error.localizedDescription)")
        }
        #endif
    }

    @objc private func refreshDevices() {
        attachKeyboard()
        attachMice()
        attachControllers()
        advertiseHaptics()
    }

    private func startTimers() {
        heartbeatTimer?.invalidate()
        heartbeatTimer = Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { [weak self] _ in
            guard let self else { return }
            self.sink?.sendReliableInput(self.encoder.encodeHeartbeat())
        }
        gamepadKeepaliveTimer?.invalidate()
        gamepadKeepaliveTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            self?.sendCurrentGamepads(force: true)
        }
    }

    private func attachKeyboard() {
        let nextKeyboard = GCKeyboard.coalesced
        guard nextKeyboard !== keyboard else { return }
        keyboard?.keyboardInput?.keyChangedHandler = nil
        keyboard = nextKeyboard
        keyboard?.keyboardInput?.keyChangedHandler = { [weak self] keyboardInput, _, keyCode, pressed in
            guard let self, let mapping = NativeStreamKeyboardMapper.mapping(for: keyCode) else { return }
            let modifiers = NativeStreamKeyboardMapper.modifiers(
                for: keyboardInput,
                changedKey: keyCode,
                pressed: pressed
            )
            let data = pressed
                ? self.encoder.encodeKeyDown(mapping: mapping, modifiers: modifiers)
                : self.encoder.encodeKeyUp(mapping: mapping, modifiers: modifiers)
            self.sink?.sendReliableInput(data)
        }
    }

    private func attachMice() {
        mice.forEach { mouse in
            guard let input = mouse.mouseInput else { return }
            input.mouseMovedHandler = nil
            input.leftButton.pressedChangedHandler = nil
            input.rightButton?.pressedChangedHandler = nil
            input.middleButton?.pressedChangedHandler = nil
            input.scroll.valueChangedHandler = nil
        }
        mice = GCMouse.mice()
        for mouse in mice {
            guard let input = mouse.mouseInput else { continue }
            input.mouseMovedHandler = { [weak self] _, deltaX, deltaY in
                self?.coalesceMouse(dx: CGFloat(deltaX), dy: CGFloat(deltaY))
            }
            input.leftButton.pressedChangedHandler = { [weak self] _, _, pressed in
                self?.sendMouseButton(1, pressed: pressed)
            }
            input.middleButton?.pressedChangedHandler = { [weak self] _, _, pressed in
                self?.sendMouseButton(2, pressed: pressed)
            }
            input.rightButton?.pressedChangedHandler = { [weak self] _, _, pressed in
                self?.sendMouseButton(3, pressed: pressed)
            }
            input.scroll.valueChangedHandler = { [weak self] _, _, yValue in
                let delta = Int((CGFloat(yValue) * 120).rounded())
                guard delta != 0 else { return }
                self?.sendMouseWheel(delta: delta)
            }
            input.auxiliaryButtons?.enumerated().forEach { offset, button in
                button.pressedChangedHandler = { [weak self] _, _, pressed in
                    self?.sendMouseButton(4 + offset, pressed: pressed)
                }
            }
        }
    }

    private func coalesceMouse(dx: CGFloat, dy: CGFloat) {
        mouseAccumulator.x += dx
        mouseAccumulator.y += dy
        guard !mouseFlushScheduled else { return }
        mouseFlushScheduled = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.006) { [weak self] in
            guard let self else { return }
            let dx = Int(self.mouseAccumulator.x.rounded())
            let dy = Int(self.mouseAccumulator.y.rounded())
            self.mouseAccumulator = .zero
            self.mouseFlushScheduled = false
            guard dx != 0 || dy != 0 else { return }
            self.sink?.sendPartiallyReliableInput(self.encoder.encodeMouseMove(dx: dx, dy: dy))
        }
    }

    private func attachControllers() {
        controllers.forEach { $0.extendedGamepad?.valueChangedHandler = nil }
        controllers = GCController.controllers().filter { $0.extendedGamepad != nil }
        for (index, controller) in controllers.enumerated() {
            controller.extendedGamepad?.valueChangedHandler = { [weak self] _, _ in
                self?.sendGamepad(index: index, force: false)
            }
        }
        sendCurrentGamepads(force: true)
    }

    private func sendCurrentGamepads(force: Bool) {
        for index in controllers.indices {
            sendGamepad(index: index, force: force)
        }
        if controllers.isEmpty, !lastGamepadStates.isEmpty {
            for id in lastGamepadStates.keys {
                let disconnected = NativeStreamGamepadState(
                    controllerId: id,
                    buttons: 0,
                    leftTrigger: 0,
                    rightTrigger: 0,
                    leftStickX: 0,
                    leftStickY: 0,
                    rightStickX: 0,
                    rightStickY: 0,
                    connected: false
                )
                sendGamepadState(disconnected, force: true)
            }
            lastGamepadStates.removeAll(keepingCapacity: true)
        }
    }

    private func sendGamepad(index: Int, force: Bool) {
        guard index < controllers.count, let gamepad = controllers[index].extendedGamepad else { return }
        var buttons: UInt16 = 0
        if gamepad.dpad.up.isPressed { buttons |= 0x0001 }
        if gamepad.dpad.down.isPressed { buttons |= 0x0002 }
        if gamepad.dpad.left.isPressed { buttons |= 0x0004 }
        if gamepad.dpad.right.isPressed { buttons |= 0x0008 }
        if gamepad.buttonMenu.isPressed { buttons |= 0x0010 }
        if gamepad.buttonOptions?.isPressed == true { buttons |= 0x0020 }
        if gamepad.leftThumbstickButton?.isPressed == true { buttons |= 0x0040 }
        if gamepad.rightThumbstickButton?.isPressed == true { buttons |= 0x0080 }
        if gamepad.leftShoulder.isPressed { buttons |= 0x0100 }
        if gamepad.rightShoulder.isPressed { buttons |= 0x0200 }
        if gamepad.buttonHome?.isPressed == true { buttons |= 0x0400 }
        if gamepad.buttonA.isPressed { buttons |= 0x1000 }
        if gamepad.buttonB.isPressed { buttons |= 0x2000 }
        if gamepad.buttonX.isPressed { buttons |= 0x4000 }
        if gamepad.buttonY.isPressed { buttons |= 0x8000 }

        let state = NativeStreamGamepadState(
            controllerId: index,
            buttons: buttons,
            leftTrigger: uint8(gamepad.leftTrigger.value),
            rightTrigger: uint8(gamepad.rightTrigger.value),
            leftStickX: int16Axis(gamepad.leftThumbstick.xAxis.value),
            leftStickY: int16Axis(gamepad.leftThumbstick.yAxis.value),
            rightStickX: int16Axis(gamepad.rightThumbstick.xAxis.value),
            rightStickY: int16Axis(gamepad.rightThumbstick.yAxis.value),
            connected: true
        )
        sendGamepadState(state, force: force)
    }

    private func sendGamepadState(_ state: NativeStreamGamepadState, force: Bool) {
        guard force || lastGamepadStates[state.controllerId] != state else { return }
        lastGamepadStates[state.controllerId] = state
        var bitmap: UInt16 = 0
        for index in controllers.indices where index < 4 {
            bitmap |= UInt16(1 << index)
            bitmap |= UInt16(1 << (index + 8))
        }
        if !state.connected {
            bitmap &= ~UInt16(1 << state.controllerId)
        }
        let usePartiallyReliable = (partiallyReliableGamepadMask & UInt16(1 << (state.controllerId & 0x03))) != 0
        let data = encoder.encodeGamepadState(state, bitmap: bitmap, partiallyReliable: usePartiallyReliable)
        if usePartiallyReliable {
            sink?.sendPartiallyReliableInput(data)
        } else {
            sink?.sendReliableInput(data)
        }
    }

    private func uint8(_ value: Float) -> UInt8 {
        UInt8(min(max(Int((value * 255).rounded()), 0), 255))
    }

    private func int16Axis(_ value: Float) -> Int16 {
        let clamped = min(max(value, -1), 1)
        let scaled = Int((clamped >= 0 ? clamped * 32767 : clamped * 32768).rounded())
        return Int16(min(max(scaled, Int(Int16.min)), Int(Int16.max)))
    }

    #if canImport(CoreHaptics)
    private func hapticEngine(for controller: GCController) throws -> CHHapticEngine {
        let key = ObjectIdentifier(controller)
        if let engine = hapticEngines[key] {
            return engine
        }
        guard let haptics = controller.haptics else {
            throw NSError(domain: "OpenNOW.NativeStreamer.Haptics", code: 1)
        }
        let locality: GCHapticsLocality = haptics.supportedLocalities.contains(.all) ? .all : .default
        guard let engine = haptics.createEngine(withLocality: locality) else {
            throw NSError(domain: "OpenNOW.NativeStreamer.Haptics", code: 2)
        }
        engine.stoppedHandler = { [weak self, weak controller] _ in
            guard let controller else { return }
            self?.hapticEngines.removeValue(forKey: ObjectIdentifier(controller))
        }
        hapticEngines[key] = engine
        return engine
    }

    private func stopRumble(for controller: GCController) {
        let key = ObjectIdentifier(controller)
        hapticEngines[key]?.stop(completionHandler: nil)
        hapticEngines.removeValue(forKey: key)
    }
    #endif

    private func stopAllRumble() {
        #if canImport(CoreHaptics)
        hapticEngines.values.forEach { $0.stop(completionHandler: nil) }
        hapticEngines.removeAll(keepingCapacity: true)
        #endif
    }
}
#else
final class NativeStreamInputBridge {
    weak var sink: NativeStreamInputSink?
    func configure(protocolVersion: Int, partiallyReliableGamepadMask: Int) {}
    func attach() {}
    func detach() {}
    func sendTouchMouseMove(dx: CGFloat, dy: CGFloat) {}
    func sendMouseButton(_ button: Int, pressed: Bool) {}
    func sendKey(mapping: NativeStreamKeyboardMapping, pressed: Bool, modifiers: UInt16) {}
    func sendMouseWheel(delta: Int) {}
    func advertiseHaptics() {}
    func applyRumble(controllerId: Int, weakMagnitude: Int, strongMagnitude: Int) {}
}
#endif
