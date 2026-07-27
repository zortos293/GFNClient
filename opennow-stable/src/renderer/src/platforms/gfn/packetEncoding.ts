import type { GamepadInput } from "./gamepadMapping";
import { GAMEPAD_MAX_CONTROLLERS, GAMEPAD_PACKET_SIZE } from "./gamepadMapping";
import type { KeyboardPayload } from "./keyboardMapping";
import { sendTimestampUs, writeSessionTimestamp } from "./inputSessionClock";

export const INPUT_HEARTBEAT = 2;
export const INPUT_KEY_DOWN = 3;
export const INPUT_KEY_UP = 4;
/** Lock-key state sync (Caps/Num/Scroll), matches official GFN Cc()/Ic() type 19. */
export const INPUT_LOCK_KEYS_SYNC = 19;
export const INPUT_MOUSE_ABS = 5;
export const INPUT_MOUSE_REL = 7;
export const INPUT_MOUSE_BUTTON_DOWN = 8;
export const INPUT_MOUSE_BUTTON_UP = 9;
export const INPUT_MOUSE_WHEEL = 10;
export const INPUT_GAMEPAD = 12;
export const INPUT_HAPTICS_ENABLED = 13;
export const INPUT_TEXT = 23;

const TEXT_INPUT_CHUNK_MAX_BYTES = 1016;
const TEXT_INPUT_HEADER_BYTES = 5;

export const WRAPPER_VERSION_MARKER = 0x23;
export const WRAPPER_SINGLE_INPUT = 0x22;
const WRAPPER_VERSION_HEADER_BYTES = 9;
const WRAPPER_SINGLE_BODY_OFFSET = WRAPPER_VERSION_HEADER_BYTES + 1;

/** Rewrite the protocol v3 `[0x23][timestamp]` header to the send-time session clock. */
export function restampProtocolV3OuterTimestamp(packet: Uint8Array, timestampUs: bigint): boolean {
  if (packet.length < WRAPPER_VERSION_HEADER_BYTES || packet[0] !== WRAPPER_VERSION_MARKER) {
    return false;
  }
  writeSessionTimestamp(new DataView(packet.buffer, packet.byteOffset, packet.byteLength), 1, timestampUs);
  return true;
}

/**
 * Coalesce protocol v3 single-input packets into one datachannel payload.
 * Official GFN batches multiple `[0x22][body]` frames under one `[0x23][timestamp]`
 * header stamped with the send-time session clock (`ed()`).
 */
export function combineSingleInputPackets(
  payloads: readonly Uint8Array[],
  sendTimestampUsValue: bigint,
): Uint8Array | null {
  if (payloads.length === 0) {
    return null;
  }
  if (payloads.length === 1) {
    const packet = payloads[0].slice();
    restampProtocolV3OuterTimestamp(packet, sendTimestampUsValue);
    return packet;
  }

  const combinedBodies: number[] = [];
  for (const payload of payloads) {
    if (
      payload.length >= WRAPPER_SINGLE_BODY_OFFSET
      && payload[0] === WRAPPER_VERSION_MARKER
      && payload[WRAPPER_VERSION_HEADER_BYTES] === WRAPPER_SINGLE_INPUT
    ) {
      combinedBodies.push(WRAPPER_SINGLE_INPUT);
      combinedBodies.push(...payload.subarray(WRAPPER_SINGLE_BODY_OFFSET));
      continue;
    }
    return null;
  }

  const bytes = new Uint8Array(WRAPPER_VERSION_HEADER_BYTES + combinedBodies.length);
  const view = new DataView(bytes.buffer);
  bytes[0] = WRAPPER_VERSION_MARKER;
  writeSessionTimestamp(view, 1, sendTimestampUsValue);
  bytes.set(combinedBodies, WRAPPER_VERSION_HEADER_BYTES);
  return bytes;
}

/** Finalize reliable keyboard/button packets, coalescing and restamping v3 headers at send time. */
export function finalizeReliableSingleInputPackets(
  payloads: readonly Uint8Array[],
  sendTimestampUsValue: bigint,
): Uint8Array[] {
  if (payloads.length === 0) {
    return [];
  }

  const combined = combineSingleInputPackets(payloads, sendTimestampUsValue);
  if (combined) {
    return [combined];
  }

  return payloads.map((payload) => {
    const packet = payload.slice();
    restampProtocolV3OuterTimestamp(packet, sendTimestampUsValue);
    return packet;
  });
}

// Mouse button constants (1-based for GFN protocol)
// GFN uses: 1=Left, 2=Middle, 3=Right, 4=Back, 5=Forward
export const MOUSE_LEFT = 1;
export const MOUSE_MIDDLE = 2;
export const MOUSE_RIGHT = 3;
export const MOUSE_BACK = 4;
export const MOUSE_FORWARD = 5;

export const PARTIALLY_RELIABLE_GAMEPAD_MASK_ALL = (1 << GAMEPAD_MAX_CONTROLLERS) - 1;
export const PARTIALLY_RELIABLE_HID_DEVICE_MASK_ALL = 0xFFFFFFFF;

export interface MouseMovePayload {
  dx: number;
  dy: number;
  timestampUs: bigint;
}

/**
 * Absolute mouse position (input type 5). Coordinates are expressed inside a
 * client-defined extent (`width`/`height`) that the server uses to scale onto
 * the remote desktop, mirroring the official client's Hc() encoder.
 */
export interface MouseAbsolutePayload {
  x: number;
  y: number;
  width: number;
  height: number;
  timestampUs: bigint;
}

export interface MouseButtonPayload {
  button: number;
  timestampUs: bigint;
}

export interface MouseWheelPayload {
  delta: number;
  timestampUs: bigint;
}

export function partiallyReliableHidMaskForInputType(inputType: number): number {
  if (!Number.isInteger(inputType) || inputType < 0 || inputType > 31) {
    return 0;
  }
  return 1 << inputType;
}

export function isPartiallyReliableHidTransferEligible(inputType: number): boolean {
  return inputType === INPUT_MOUSE_REL || inputType === INPUT_MOUSE_ABS;
}

/**
 * Write an 8-byte big-endian session-relative timestamp into a DataView.
 * Outer v3 headers are restamped again at send time via restampProtocolV3OuterTimestamp().
 */
function writeTimestamp(view: DataView, offset: number): void {
  writeSessionTimestamp(view, offset, sendTimestampUs());
}

/**
 * Protocol v3+ wrapper for SINGLE non-mouse events (keyboard, mouse button, wheel).
 * Format: [0x23][8B timestamp][0x22][payload]
 *
 * 0x23 = outer timestamp wrapper (added by yc() in official client)
 * 0x22 = single-event sub-message marker (added by Ec() allocator in official client)
 *
 * For protocol v1-v2, returns the raw payload unchanged.
 */
function wrapSingleEvent(payload: Uint8Array, protocolVersion: number): Uint8Array {
  if (protocolVersion <= 2) {
    return payload;
  }
  // [0x23][8B timestamp][0x22][payload]
  const wrapped = new Uint8Array(9 + 1 + payload.length);
  const view = new DataView(wrapped.buffer);
  wrapped[0] = 0x23;
  writeTimestamp(view, 1);
  wrapped[9] = 0x22;  // single-event sub-message marker
  wrapped.set(payload, 10);
  return wrapped;
}

/**
 * Protocol v3+ wrapper for MOUSE MOVE events.
 * Format: [0x23][8B timestamp][0x21][2B event-length][payload]
 *
 * 0x23 = outer timestamp wrapper
 * 0x21 = mouse/cursor event marker (used by Tc() coalescer in official client)
 * 2B   = payload length (BE uint16) — official client's Wa() with no endian param = BE
 *
 * For protocol v1-v2, returns the raw payload unchanged.
 */
function wrapMouseMoveEvent(payload: Uint8Array, protocolVersion: number): Uint8Array {
  if (protocolVersion <= 2) {
    return payload;
  }
  // [0x23][8B timestamp][0x21][2B length][payload]
  const wrapped = new Uint8Array(9 + 1 + 2 + payload.length);
  const view = new DataView(wrapped.buffer);
  wrapped[0] = 0x23;
  writeTimestamp(view, 1);
  wrapped[9] = 0x21;  // mouse/cursor event marker
  view.setUint16(10, payload.length, false);  // event length (BE, matches official setUint16)
  wrapped.set(payload, 12);
  return wrapped;
}

/**
 * Protocol v3+ wrapper for GAMEPAD events on the RELIABLE channel.
 * Format: [0x23][8B timestamp][0x21][2B size BE][payload]
 *
 * Official GFN client's ul() with m=false writes [0x21][2B size] then yc() prepends [0x23][8B ts].
 * Gamepad goes through the same batching system as other events.
 *
 * For protocol v1-v2, returns the raw payload unchanged.
 */
function wrapGamepadReliable(payload: Uint8Array, protocolVersion: number): Uint8Array {
  if (protocolVersion <= 2) {
    return payload;
  }
  // [0x23][8B timestamp][0x21][2B size][payload]
  const wrapped = new Uint8Array(9 + 1 + 2 + payload.length);
  const view = new DataView(wrapped.buffer);
  wrapped[0] = 0x23;
  writeTimestamp(view, 1);
  wrapped[9] = 0x21;  // batched event marker (m=false path in ul())
  view.setUint16(10, payload.length, false);  // size (BE, Wa() with no endian param)
  wrapped.set(payload, 12);
  return wrapped;
}

/**
 * Protocol v3+ wrapper for GAMEPAD events on the PARTIALLY RELIABLE channel.
 * Format: [0x23][8B timestamp][0x26][1B gamepadIdx][2B seqNum BE][0x21][2B size BE][payload]
 *
 * Official GFN client's ul() adds [0x26][idx][seq] header when gamepad index is specified
 * (partially reliable path), then [0x21][2B size], then yc() prepends [0x23][8B ts].
 *
 * 0x26 = 38 decimal, PR sequence header byte (written by Va(38) in ul())
 *
 * For protocol v1-v2, returns the raw payload unchanged.
 */
function wrapGamepadPartiallyReliable(
  payload: Uint8Array,
  protocolVersion: number,
  gamepadIndex: number,
  sequenceNumber: number,
): Uint8Array {
  if (protocolVersion <= 2) {
    return payload;
  }
  // [0x23][8B ts][0x26][1B idx][2B seq][0x21][2B size][payload]
  const wrapped = new Uint8Array(9 + 1 + 1 + 2 + 1 + 2 + payload.length);
  const view = new DataView(wrapped.buffer);
  wrapped[0] = 0x23;
  writeTimestamp(view, 1);
  wrapped[9] = 0x26;  // PR sequence header (decimal 38, written by Va(38))
  wrapped[10] = gamepadIndex & 0xFF;  // gamepad index byte
  view.setUint16(11, sequenceNumber, false);  // sequence number (BE, Wa() with no endian param)
  wrapped[13] = 0x21;  // batched event marker
  view.setUint16(14, payload.length, false);  // size (BE)
  wrapped.set(payload, 16);
  return wrapped;
}

export class InputEncoder {
  private protocolVersion = 2;
  // Per-gamepad sequence numbers for partially reliable channel framing.
  // Official GFN client tracks this per-gamepad-index via this.tc Map.
  private gamepadSequence: Map<number, number> = new Map();

  setProtocolVersion(version: number): void {
    this.protocolVersion = version;
  }

  /** Get and increment the sequence number for a gamepad on the PR channel.
   *  Wraps at 65536 (uint16 range), matching official client's cl() function. */
  getNextGamepadSequence(gamepadIndex: number): number {
    const current = this.gamepadSequence.get(gamepadIndex) ?? 1;
    this.gamepadSequence.set(gamepadIndex, (current + 1) % 65536);
    return current;
  }

  resetGamepadSequences(): void {
    this.gamepadSequence.clear();
  }

  encodeLockKeysSync(state: number): Uint8Array {
    const bytes = new Uint8Array(5);
    const view = new DataView(bytes.buffer);
    view.setUint32(0, INPUT_LOCK_KEYS_SYNC, true);
    view.setUint8(4, state & 0xff);
    return wrapSingleEvent(bytes, this.protocolVersion);
  }

  encodeHeartbeat(): Uint8Array {
    // Heartbeat is sent RAW — no v3 wrapper.
    // Official GFN client's Jc() sends [u32 LE = 2] directly, no 0x23/0x22 prefix.
    const payload = new Uint8Array(4);
    const view = new DataView(payload.buffer);
    view.setUint32(0, INPUT_HEARTBEAT, true);
    return payload;
  }

  encodeKeyDown(payload: KeyboardPayload): Uint8Array {
    return this.encodeKey(INPUT_KEY_DOWN, payload);
  }

  encodeKeyUp(payload: KeyboardPayload): Uint8Array {
    return this.encodeKey(INPUT_KEY_UP, payload);
  }

  encodeMouseMove(payload: MouseMovePayload): Uint8Array {
    const bytes = new Uint8Array(22);
    const view = new DataView(bytes.buffer);
    // [type 4B LE][dx 2B BE][dy 2B BE][reserved 6B BE][timestamp 8B BE]
    view.setUint32(0, INPUT_MOUSE_REL, true);        // type: LE
    view.setInt16(4, payload.dx, false);              // dx: BE
    view.setInt16(6, payload.dy, false);              // dy: BE
    view.setUint16(8, 0, false);                      // reserved: BE
    view.setUint32(10, 0, false);                     // reserved: BE
    view.setBigUint64(14, payload.timestampUs, false); // timestamp: BE
    return wrapMouseMoveEvent(bytes, this.protocolVersion);
  }

  encodeMouseAbsolute(payload: MouseAbsolutePayload): Uint8Array {
    const bytes = new Uint8Array(26);
    const view = new DataView(bytes.buffer);
    // Official client Hc() with absolute flag (opcode 5, 26 bytes):
    // [type 4B LE][x 2B BE][y 2B BE][reserved 2B BE][width 2B BE][height 2B BE][reserved 4B BE][timestamp 8B BE]
    view.setUint32(0, INPUT_MOUSE_ABS, true);             // type: LE
    view.setUint16(4, clampU16(payload.x), false);         // x: BE
    view.setUint16(6, clampU16(payload.y), false);         // y: BE
    view.setUint16(8, 0, false);                           // reserved: BE
    view.setUint16(10, clampU16(payload.width), false);    // extent width: BE
    view.setUint16(12, clampU16(payload.height), false);   // extent height: BE
    view.setUint32(14, 0, false);                          // reserved: BE
    view.setBigUint64(18, payload.timestampUs, false);     // timestamp: BE
    return wrapMouseMoveEvent(bytes, this.protocolVersion);
  }

  encodeMouseButtonDown(payload: MouseButtonPayload): Uint8Array {
    return this.encodeMouseButton(INPUT_MOUSE_BUTTON_DOWN, payload);
  }

  encodeMouseButtonUp(payload: MouseButtonPayload): Uint8Array {
    return this.encodeMouseButton(INPUT_MOUSE_BUTTON_UP, payload);
  }

  encodeMouseWheel(payload: MouseWheelPayload): Uint8Array {
    const bytes = new Uint8Array(22);
    const view = new DataView(bytes.buffer);
    // [type 4B LE][horiz 2B BE][vert 2B BE][reserved 6B BE][timestamp 8B BE]
    view.setUint32(0, INPUT_MOUSE_WHEEL, true);        // type: LE
    view.setInt16(4, 0, false);                         // horizontal: BE
    view.setInt16(6, payload.delta, false);              // vertical: BE
    view.setUint16(8, 0, false);                         // reserved: BE
    view.setUint32(10, 0, false);                        // reserved: BE
    view.setBigUint64(14, payload.timestampUs, false);   // timestamp: BE
    return wrapSingleEvent(bytes, this.protocolVersion);
  }

  encodeHapticsEnabled(enabled: boolean): Uint8Array {
    const bytes = new Uint8Array(6);
    const view = new DataView(bytes.buffer);
    view.setUint32(0, INPUT_HAPTICS_ENABLED, true);
    view.setUint16(4, enabled ? 1 : 0, false);
    return wrapSingleEvent(bytes, this.protocolVersion);
  }

  encodeTextInput(text: string): Uint8Array[] {
    const utf8 = new TextEncoder().encode(text);
    const chunks: Uint8Array[] = [];

    for (let offset = 0; offset < utf8.byteLength;) {
      const chunkLength = textInputChunkLength(utf8, offset);
      if (chunkLength <= 0) {
        break;
      }

      const bytes = new Uint8Array(TEXT_INPUT_HEADER_BYTES + chunkLength);
      const view = new DataView(bytes.buffer);
      bytes[0] = 0x22;
      view.setUint32(1, INPUT_TEXT, true);
      bytes.set(utf8.subarray(offset, offset + chunkLength), TEXT_INPUT_HEADER_BYTES);
      chunks.push(bytes);
      offset += chunkLength;
    }

    return chunks;
  }

  encodeGamepadState(payload: GamepadInput, bitmap: number, usePartiallyReliable: boolean): Uint8Array {
    const bytes = new Uint8Array(GAMEPAD_PACKET_SIZE);
    const view = new DataView(bytes.buffer);

    // Match official GFN client's gl() function exactly (vendor_beautified.js line 13469-13470):
    // gl(i, u, m, w, P, L, $=0, ae=0) where:
    //   i=DataView, u=base offset (0), m=gamepad index, w=buttons,
    //   P=triggers, L=axes[4], $=timestamp, ae=bitmap
    
    // Offset 0x00: Type (u32 LE) - event type 12
    view.setUint32(0, INPUT_GAMEPAD, true);
    
    // Offset 0x04: Payload size (u16 LE) = 26
    view.setUint16(4, 26, true);
    
    // Offset 0x06: Gamepad index (u16 LE)
    view.setUint16(6, payload.controllerId & 0x03, true);
    
    // Offset 0x08: Bitmap (u16 LE) — official this.nu bitmask.
    // Bit i = gamepad i connected; bit (i+8) = Xbox/xinput style device.
    // The high bit likely advertises the XInput/haptics-capable variant.
    view.setUint16(8, bitmap, true);
    
    // Offset 0x0A: Inner payload size (u16 LE) = 20
    view.setUint16(10, 20, true);
    
    // Offset 0x0C: Button flags (u16 LE) - XInput format
    view.setUint16(12, payload.buttons, true);
    
    // Offset 0x0E: Packed triggers (u16 LE: low byte=LT, high byte=RT)
    const packedTriggers = (payload.leftTrigger & 0xFF) | ((payload.rightTrigger & 0xFF) << 8);
    view.setUint16(14, packedTriggers, true);
    
    // Offset 0x10: Left stick X (i16 LE)
    view.setInt16(16, payload.leftStickX, true);
    
    // Offset 0x12: Left stick Y (i16 LE)
    view.setInt16(18, payload.leftStickY, true);
    
    // Offset 0x14: Right stick X (i16 LE)
    view.setInt16(20, payload.rightStickX, true);
    
    // Offset 0x16: Right stick Y (i16 LE)
    view.setInt16(22, payload.rightStickY, true);
    
    // Offset 0x18: Reserved (u16 LE) = 0
    view.setUint16(24, 0, true);
    
    // Offset 0x1A: Magic constant (u16 LE) = 85 (0x55)
    view.setUint16(26, 85, true);
    
    // Offset 0x1C: Reserved (u16 LE) = 0
    view.setUint16(28, 0, true);
    
    // Offset 0x1E: Timestamp (u64 LE)
    view.setBigUint64(30, payload.timestampUs, true);

    // Gamepad packets ARE wrapped in protocol v3+ — the official client's yc() function
    // applies the 0x23 wrapper for ALL channels (the v2+ check does NOT exclude PR).
    // The batching system also adds 0x21 inner framing.
    if (usePartiallyReliable) {
      // PR channel: [0x23][8B ts][0x26][1B idx][2B seq][0x21][2B size][38B payload]
      const seq = this.getNextGamepadSequence(payload.controllerId);
      return wrapGamepadPartiallyReliable(bytes, this.protocolVersion, payload.controllerId, seq);
    }
    // Reliable channel: [0x23][8B ts][0x21][2B size][38B payload]
    return wrapGamepadReliable(bytes, this.protocolVersion);
  }

  private encodeKey(type: number, payload: KeyboardPayload): Uint8Array {
    const bytes = new Uint8Array(18);
    const view = new DataView(bytes.buffer);
    // [type 4B LE][keycode 2B BE][modifiers 2B BE][scancode 2B BE][timestamp 8B BE]
    view.setUint32(0, type, true);                       // type: LE
    view.setUint16(4, payload.keycode, false);            // keycode: BE
    view.setUint16(6, payload.modifiers, false);          // modifiers: BE
    view.setUint16(8, payload.scancode, false);           // scancode: BE
    view.setBigUint64(10, payload.timestampUs, false);    // timestamp: BE
    return wrapSingleEvent(bytes, this.protocolVersion);
  }

  private encodeMouseButton(type: number, payload: MouseButtonPayload): Uint8Array {
    const bytes = new Uint8Array(18);
    const view = new DataView(bytes.buffer);
    // [type 4B LE][button 1B][pad 1B][reserved 4B BE][timestamp 8B BE]
    view.setUint32(0, type, true);                       // type: LE
    view.setUint8(4, payload.button);
    view.setUint8(5, 0);
    view.setUint32(6, 0, false);                          // reserved: BE
    view.setBigUint64(10, payload.timestampUs, false);    // timestamp: BE
    return wrapSingleEvent(bytes, this.protocolVersion);
  }
}

function clampU16(value: number): number {
  return Math.max(0, Math.min(65535, Math.round(value)));
}

function textInputChunkLength(bytes: Uint8Array, offset: number): number {
  const remaining = bytes.byteLength - offset;
  if (remaining <= TEXT_INPUT_CHUNK_MAX_BYTES) {
    return remaining;
  }

  let end = offset + TEXT_INPUT_CHUNK_MAX_BYTES;
  for (let attempt = 0; attempt < 4; attempt++) {
    if ((bytes[end] & 0xc0) !== 0x80) {
      return end - offset;
    }
    end--;
  }

  return 0;
}

/**
 * Convert browser mouse button (0-based) to GFN protocol (1-based).
 * Browser: 0=Left, 1=Middle, 2=Right, 3=Back, 4=Forward
 * GFN:     1=Left, 2=Middle, 3=Right, 4=Back, 5=Forward
 */
export function toMouseButton(button: number): number {
  // Convert 0-based browser button to 1-based GFN button
  return button + 1;
}
