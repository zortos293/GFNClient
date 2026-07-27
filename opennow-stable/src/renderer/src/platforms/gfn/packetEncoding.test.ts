/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import { GAMEPAD_PACKET_SIZE } from "./gamepadMapping";
import {
  INPUT_GAMEPAD,
  INPUT_KEY_DOWN,
  INPUT_MOUSE_REL,
  InputEncoder,
  combineSingleInputPackets,
  finalizeReliableSingleInputPackets,
  restampProtocolV3OuterTimestamp,
} from "./packetEncoding";

function view(bytes: Uint8Array): DataView {
  return new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
}

test("preserves raw v2 and wrapped v3 packet layouts", () => {
  const encoder = new InputEncoder();
  const key = { keycode: 0x41, modifiers: 0x02, scancode: 0, timestampUs: 0x0102030405060708n };
  const raw = encoder.encodeKeyDown(key);

  assert.equal(raw.byteLength, 18);
  assert.equal(view(raw).getUint32(0, true), INPUT_KEY_DOWN);
  assert.equal(view(raw).getBigUint64(10, false), key.timestampUs);

  encoder.setProtocolVersion(3);
  const mouse = encoder.encodeMouseMove({ dx: -12, dy: 34, timestampUs: 99n });
  assert.equal(mouse[0], 0x23);
  assert.equal(mouse[9], 0x21);
  assert.equal(view(mouse).getUint16(10, false), 22);
  assert.equal(view(mouse).getUint32(12, true), INPUT_MOUSE_REL);
  assert.equal(view(mouse).getInt16(16, false), -12);
});

test("encodes exact reliable and partially reliable gamepad framing", () => {
  const encoder = new InputEncoder();
  encoder.setProtocolVersion(3);
  const state = {
    controllerId: 2,
    buttons: 0x1000,
    leftTrigger: 10,
    rightTrigger: 20,
    leftStickX: -100,
    leftStickY: 200,
    rightStickX: -300,
    rightStickY: 400,
    connected: true,
    timestampUs: 0x0102030405060708n,
  };

  const reliable = encoder.encodeGamepadState(state, 0x0104, false);
  assert.equal(reliable[9], 0x21);
  assert.equal(view(reliable).getUint16(10, false), GAMEPAD_PACKET_SIZE);
  assert.equal(view(reliable).getUint32(12, true), INPUT_GAMEPAD);

  const partiallyReliable = encoder.encodeGamepadState(state, 0x0104, true);
  assert.equal(partiallyReliable[9], 0x26);
  assert.equal(partiallyReliable[10], 2);
  assert.equal(view(partiallyReliable).getUint16(11, false), 1);
  assert.equal(partiallyReliable[13], 0x21);
});

test("restamps and coalesces reliable v3 single-input packets", () => {
  const encoder = new InputEncoder();
  encoder.setProtocolVersion(3);
  const first = encoder.encodeKeyDown({ keycode: 0x41, scancode: 0, modifiers: 0, timestampUs: 1n });
  const second = encoder.encodeKeyDown({ keycode: 0x42, scancode: 0, modifiers: 0, timestampUs: 2n });

  assert.equal(restampProtocolV3OuterTimestamp(first, 50n), true);
  assert.equal(view(first).getBigUint64(1, false), 50n);

  const combined = combineSingleInputPackets([first, second], 77n);
  assert.ok(combined);
  assert.equal(view(combined).getBigUint64(1, false), 77n);
  assert.equal(combined[9], 0x22);
  assert.equal(combined[28], 0x22);

  const finalized = finalizeReliableSingleInputPackets([first, second], 99n);
  assert.equal(finalized.length, 1);
  assert.equal(view(finalized[0]).getBigUint64(1, false), 99n);
});
