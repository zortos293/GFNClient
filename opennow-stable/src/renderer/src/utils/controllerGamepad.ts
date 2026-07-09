export const controllerButton = {
  south: 1 << 0,
  east: 1 << 1,
  west: 1 << 2,
  north: 1 << 3,
  leftShoulder: 1 << 4,
  rightShoulder: 1 << 5,
  up: 1 << 6,
  down: 1 << 7,
  left: 1 << 8,
  right: 1 << 9,
  menu: 1 << 10,
} as const;

export function readControllerGamepadButtons(pad: Gamepad | undefined): number {
  if (!pad) return 0;
  const buttons = pad.buttons ?? [];
  const axes = pad.axes ?? [];
  let result = 0;
  if (buttons[0]?.pressed) result |= controllerButton.south;
  if (buttons[1]?.pressed) result |= controllerButton.east;
  if (buttons[2]?.pressed) result |= controllerButton.west;
  if (buttons[3]?.pressed) result |= controllerButton.north;
  if (buttons[4]?.pressed) result |= controllerButton.leftShoulder;
  if (buttons[5]?.pressed) result |= controllerButton.rightShoulder;
  if (buttons[12]?.pressed || (axes[1] ?? 0) < -0.65) result |= controllerButton.up;
  if (buttons[13]?.pressed || (axes[1] ?? 0) > 0.65) result |= controllerButton.down;
  if (buttons[14]?.pressed || (axes[0] ?? 0) < -0.65) result |= controllerButton.left;
  if (buttons[15]?.pressed || (axes[0] ?? 0) > 0.65) result |= controllerButton.right;
  if (buttons[9]?.pressed || buttons[16]?.pressed) result |= controllerButton.menu;
  return result;
}
