// XInput button flags (matching Windows XINPUT_GAMEPAD_* constants)
export const GAMEPAD_DPAD_UP = 0x0001;
export const GAMEPAD_DPAD_DOWN = 0x0002;
export const GAMEPAD_DPAD_LEFT = 0x0004;
export const GAMEPAD_DPAD_RIGHT = 0x0008;
export const GAMEPAD_START = 0x0010;
export const GAMEPAD_BACK = 0x0020;
export const GAMEPAD_LS = 0x0040; // Left stick click (L3)
export const GAMEPAD_RS = 0x0080; // Right stick click (R3)
export const GAMEPAD_LB = 0x0100; // Left bumper
export const GAMEPAD_RB = 0x0200; // Right bumper
export const GAMEPAD_GUIDE = 0x0400; // Xbox/Guide button
export const GAMEPAD_A = 0x1000;
export const GAMEPAD_B = 0x2000;
export const GAMEPAD_X = 0x4000;
export const GAMEPAD_Y = 0x8000;

// Axis indices for gamepad
export const GAMEPAD_AXIS_LX = 0; // Left stick X
export const GAMEPAD_AXIS_LY = 1; // Left stick Y
export const GAMEPAD_AXIS_RX = 2; // Right stick X
export const GAMEPAD_AXIS_RY = 3; // Right stick Y
export const GAMEPAD_AXIS_LT = 4; // Left trigger
export const GAMEPAD_AXIS_RT = 5; // Right trigger

// Gamepad constants
export const GAMEPAD_MAX_CONTROLLERS = 4;
export const GAMEPAD_PACKET_SIZE = 38;
export const GAMEPAD_DEADZONE = 0.15; // 15% radial deadzone

export interface GamepadInput {
  controllerId: number; // 0-3
  buttons: number; // 16-bit button flags
  leftTrigger: number; // 0-255
  rightTrigger: number; // 0-255
  leftStickX: number; // -32768 to 32767
  leftStickY: number; // -32768 to 32767 (inverted in XInput)
  rightStickX: number; // -32768 to 32767
  rightStickY: number; // -32768 to 32767 (inverted in XInput)
  connected: boolean; // true = connected, false = disconnected
  timestampUs: bigint;
}

/**
 * Apply radial deadzone to analog stick values.
 * Uses a circular deadzone where values inside the threshold are zeroed.
 * @param x X-axis value (-1.0 to 1.0)
 * @param y Y-axis value (-1.0 to 1.0)
 * @param deadzone Deadzone threshold (0.0 to 1.0), default 15%
 * @returns Adjusted {x, y} values
 */
export function applyDeadzone(
  x: number,
  y: number,
  deadzone: number = GAMEPAD_DEADZONE
): { x: number; y: number } {
  // Calculate magnitude (distance from center)
  const magnitude = Math.sqrt(x * x + y * y);

  // If inside deadzone, return zero
  if (magnitude < deadzone) {
    return { x: 0, y: 0 };
  }

  // Normalize and rescale to full range
  const normalizedX = x / magnitude;
  const normalizedY = y / magnitude;

  // Scale from deadzone edge to 1.0
  const scaledMagnitude = (magnitude - deadzone) / (1.0 - deadzone);
  const clampedMagnitude = Math.min(1.0, scaledMagnitude);

  return {
    x: normalizedX * clampedMagnitude,
    y: normalizedY * clampedMagnitude,
  };
}

/**
 * Convert a normalized axis value (-1.0 to 1.0) to signed 16-bit integer.
 * @param value Normalized value (-1.0 to 1.0)
 * @returns Signed 16-bit integer (-32768 to 32767)
 */
export function normalizeToInt16(value: number): number {
  return Math.max(-32768, Math.min(32767, Math.round(value * 32767)));
}

/**
 * Convert a normalized trigger value (0.0 to 1.0) to unsigned 8-bit integer.
 * @param value Normalized value (0.0 to 1.0)
 * @returns Unsigned 8-bit integer (0 to 255)
 */
export function normalizeToUint8(value: number): number {
  return Math.max(0, Math.min(255, Math.round(value * 255)));
}

/**
 * Map Standard Gamepad API buttons to XInput button flags.
 * Standard Gamepad: https://w3c.github.io/gamepad/#remapping
 * 
 * Uses button.value (not button.pressed) to match the official GFN client's NA() function.
 * button.value is a float 0.0-1.0; any non-zero value counts as pressed.
 * This catches partial analog button presses that button.pressed might miss.
 */
export function mapGamepadButtons(gamepad: Gamepad): number {
  let buttons = 0;
  const b = gamepad.buttons;

  // Standard Gamepad mapping to XInput (matches official client's NA() exactly)
  // Face buttons
  if (b[0]?.value) buttons |= GAMEPAD_A;          // Bottom (A/Cross)
  if (b[1]?.value) buttons |= GAMEPAD_B;          // Right (B/Circle)
  if (b[2]?.value) buttons |= GAMEPAD_X;          // Left (X/Square)
  if (b[3]?.value) buttons |= GAMEPAD_Y;          // Top (Y/Triangle)
  
  // Bumpers
  if (b[4]?.value) buttons |= GAMEPAD_LB;         // Left Bumper
  if (b[5]?.value) buttons |= GAMEPAD_RB;         // Right Bumper
  
  // buttons[6] and [7] are LT/RT as buttons — we use analog trigger values instead
  
  // Center buttons
  if (b[8]?.value) buttons |= GAMEPAD_BACK;       // Back/Select
  if (b[9]?.value) buttons |= GAMEPAD_START;      // Start
  
  // Stick clicks (L3/R3)
  if (b[10]?.value) buttons |= GAMEPAD_LS;        // L3 (Left Stick click)
  if (b[11]?.value) buttons |= GAMEPAD_RS;        // R3 (Right Stick click)
  
  // D-Pad
  if (b[12]?.value) buttons |= GAMEPAD_DPAD_UP;
  if (b[13]?.value) buttons |= GAMEPAD_DPAD_DOWN;
  if (b[14]?.value) buttons |= GAMEPAD_DPAD_LEFT;
  if (b[15]?.value) buttons |= GAMEPAD_DPAD_RIGHT;
  
  // Guide button
  if (b[16]?.value) buttons |= GAMEPAD_GUIDE;     // Guide (Center/Xbox)

  return buttons;
}

/**
 * Read analog axes from Standard Gamepad API and apply deadzone.
 * @param gamepad The Gamepad object from navigator.getGamepads()
 * @returns Object with left/right stick and trigger values
 */
export function readGamepadAxes(gamepad: Gamepad): {
  leftStickX: number;
  leftStickY: number;
  rightStickX: number;
  rightStickY: number;
  leftTrigger: number;
  rightTrigger: number;
} {
  // Left stick (axes 0, 1)
  const lx = gamepad.axes[0] ?? 0;
  const ly = gamepad.axes[1] ?? 0;
  const leftStick = applyDeadzone(lx, ly);

  // Right stick (axes 2, 3)
  const rx = gamepad.axes[2] ?? 0;
  const ry = gamepad.axes[3] ?? 0;
  const rightStick = applyDeadzone(rx, ry);

  // Triggers - can be buttons (6, 7) or axes (4, 5) depending on browser
  let leftTrigger = 0;
  let rightTrigger = 0;

  if (gamepad.buttons[6]) {
    leftTrigger = gamepad.buttons[6].value;
  } else if (gamepad.axes[4] !== undefined && gamepad.axes[4] > 0) {
    leftTrigger = gamepad.axes[4];
  }

  if (gamepad.buttons[7]) {
    rightTrigger = gamepad.buttons[7].value;
  } else if (gamepad.axes[5] !== undefined && gamepad.axes[5] > 0) {
    rightTrigger = gamepad.axes[5];
  }

  return {
    leftStickX: leftStick.x,
    leftStickY: -leftStick.y, // Invert Y to match XInput convention
    rightStickX: rightStick.x,
    rightStickY: -rightStick.y, // Invert Y to match XInput convention
    leftTrigger,
    rightTrigger,
  };
}
