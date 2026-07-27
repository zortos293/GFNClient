import {
  GAMEPAD_MAX_CONTROLLERS,
  captureTimestampUs,
  mapGamepadButtons,
  normalizeToInt16,
  normalizeToUint8,
  readGamepadAxes,
  type GamepadInput,
  type InputEncoder,
} from "../inputProtocol";
import {
  evaluateControllerOverlayShortcutGate,
  type ControllerOverlayChordState,
} from "./controllerOverlayGate";

interface DualRumbleEffectOptions {
  startDelay: 0;
  duration: number;
  weakMagnitude: number;
  strongMagnitude: number;
}

interface GamepadHapticActuatorLike {
  readonly type?: string;
  playEffect(effectType: "dual-rumble", options: DualRumbleEffectOptions): Promise<unknown>;
}

interface LegacyGamepadHapticActuatorLike {
  pulse(value: number, duration: number): Promise<unknown>;
}

type GamepadWithOptionalHaptics = Gamepad & {
  readonly vibrationActuator?: GamepadHapticActuatorLike | null;
  readonly hapticActuators?: readonly (LegacyGamepadHapticActuatorLike | null | undefined)[] | null;
};

interface GamepadRumbleApi {
  playEffectActuator: GamepadHapticActuatorLike | null;
  pulseActuator: LegacyGamepadHapticActuatorLike | null;
}

interface ConnectedRumbleGamepad {
  index: number;
  gamepad: Gamepad;
  api: GamepadRumbleApi | null;
}

interface GamepadControllerDependencies {
  inputEncoder: InputEncoder;
  isInputReady: () => boolean;
  isInputPaused: () => boolean;
  isNativeInputActive: () => boolean;
  isNativeElectronInputBridge: () => boolean;
  isReliableChannelOpen: () => boolean;
  canSendPartiallyReliableGamepad: (controllerId: number) => boolean;
  sendPartiallyReliable: (payload: Uint8Array) => void;
  sendReliable: (payload: Uint8Array) => void;
  onControllerMetaPress?: (event: { controllerId: number; gamepad: Gamepad }) => void;
  onConnectedGamepadsChanged: (count: number, emit: boolean) => void;
  log: (message: string) => void;
}

function isXboxLikeGamepad(gamepad: Gamepad): boolean {
  return /xbox|xinput/i.test(gamepad.id);
}

function getGamepadRumbleApi(gamepad: Gamepad): GamepadRumbleApi | null {
  const hapticGamepad = gamepad as GamepadWithOptionalHaptics;
  const playEffectActuator = hapticGamepad.vibrationActuator;
  const pulseActuator = hapticGamepad.hapticActuators?.[0];
  const api: GamepadRumbleApi = {
    playEffectActuator: playEffectActuator && typeof playEffectActuator.playEffect === "function"
      ? playEffectActuator
      : null,
    pulseActuator: pulseActuator && typeof pulseActuator.pulse === "function"
      ? pulseActuator
      : null,
  };
  return api.playEffectActuator || api.pulseActuator ? api : null;
}

function clampRumbleMagnitude(value: number): number {
  if (!Number.isFinite(value)) {
    return 0;
  }
  return Math.max(0, Math.min(1, value));
}

function timestampUs(): bigint {
  return captureTimestampUs();
}

export function selectGamepadPollIntervalMs(params: {
  inputReady: boolean;
  visible: boolean;
  connectedCount: number;
  inputBlocked: boolean;
}): number {
  if (!params.inputReady || !params.visible || params.connectedCount === 0) {
    return 100;
  }
  return params.inputBlocked ? 16 : 4;
}

export function shouldSendGamepadPacket(
  stateChanged: boolean,
  elapsedSinceLastSendMs: number,
): boolean {
  return stateChanged || elapsedSinceLastSendMs >= 100;
}

export class GamepadController {
  private pollTimer: number | null = null;
  private gamepadBitmap = 0;
  private lastGamepadSendMs = 0;
  private gamepadSendCount = 0;
  private readonly connectedGamepads = new Set<number>();
  private readonly gamepadMetaPressed = new Map<number, boolean>();
  private readonly gamepadOverlayChordStates = new Map<number, ControllerOverlayChordState>();
  private readonly previousGamepadStates = new Map<number, GamepadInput>();
  private readonly lastRumbleWeak: number[] = [0, 0, 0, 0];
  private readonly lastRumbleStrong: number[] = [0, 0, 0, 0];
  private readonly lastRumbleEffectAtMs: number[] = [0, 0, 0, 0];
  private readonly hapticsSupportLogged: boolean[] = [false, false, false, false];
  private readonly fallbackHapticsSupportLogged: boolean[] = [false, false, false, false];
  private lastHapticsWarningAtMs = 0;
  private hapticsAdvertised = false;

  private static readonly RUMBLE_EFFECT_MS = 500;
  private static readonly RUMBLE_THROTTLE_MS = 500;
  private static readonly HAPTICS_LOG_INTERVAL_MS = 5000;

  constructor(private readonly dependencies: GamepadControllerDependencies) {}

  stop(): void {
    if (this.pollTimer !== null) {
      window.clearTimeout(this.pollTimer);
      this.pollTimer = null;
    }
    this.stopAllGamepadRumble();
    this.updateHapticsAdvertisement(false);
  }

  reset(): void {
    this.stop();
    this.connectedGamepads.clear();
    this.gamepadMetaPressed.clear();
    this.gamepadOverlayChordStates.clear();
    this.previousGamepadStates.clear();
    this.gamepadSendCount = 0;
    this.lastGamepadSendMs = 0;
    this.gamepadBitmap = 0;
    this.hapticsAdvertised = false;
    this.dependencies.inputEncoder.resetGamepadSequences();
    this.dependencies.onConnectedGamepadsChanged(0, false);
  }

  resetProtocolState(): void {
    this.previousGamepadStates.clear();
    this.lastGamepadSendMs = 0;
    this.dependencies.inputEncoder.resetGamepadSequences();
  }

  refreshHapticsAdvertisement(): void {
    this.updateHapticsAdvertisement(this.hasConnectedHapticGamepad());
  }

  stopHaptics(): void {
    this.stopAllGamepadRumble();
    this.updateHapticsAdvertisement(false);
  }

  start(): void {
    if (this.pollTimer !== null) {
      window.clearTimeout(this.pollTimer);
    }

    this.dependencies.log("Gamepad polling started (adaptive)");
    this.scheduleGamepadPolling();
  }

  private scheduleGamepadPolling(): void {
    if (this.pollTimer !== null) {
      window.clearTimeout(this.pollTimer);
    }

    const nextDelay = this.getGamepadPollIntervalMs();
    this.pollTimer = window.setTimeout(() => {
      this.pollTimer = null;
      if (!this.dependencies.isInputReady()) {
        this.scheduleGamepadPolling();
        return;
      }
      this.pollGamepads();
      this.scheduleGamepadPolling();
    }, nextDelay);
  }

  private isStreamInputBlocked(): boolean {
    const sidebarOpen = typeof document !== "undefined" && document.body?.dataset?.sidebarOpen === "1";
    return this.dependencies.isInputPaused() || sidebarOpen;
  }

  private getGamepadPollIntervalMs(): number {
    return selectGamepadPollIntervalMs({
      inputReady: this.dependencies.isInputReady(),
      visible: document.visibilityState === "visible",
      connectedCount: this.connectedGamepads.size,
      inputBlocked: this.isStreamInputBlocked(),
    });
  }

  private shouldPollGamepads(): boolean {
    return this.dependencies.isInputReady()
      && document.visibilityState === "visible";
  }

  private updateGamepadBitmap(controllerId: number, gamepad: Gamepad): void {
    const connectedBit = 1 << controllerId;
    const xboxBit = 1 << (controllerId + 8);
    this.gamepadBitmap |= connectedBit;
    if (isXboxLikeGamepad(gamepad)) {
      this.gamepadBitmap |= xboxBit;
    } else {
      this.gamepadBitmap &= ~xboxBit;
    }
  }

  private clearGamepadBitmap(controllerId: number): void {
    this.gamepadBitmap &= ~(1 << controllerId);
    this.gamepadBitmap &= ~(1 << (controllerId + 8));
  }

  private pollGamepads(): void {
    if (!this.shouldPollGamepads()) return;
    const streamInputBlocked = this.isStreamInputBlocked();
    const gamepads = navigator.getGamepads();
    if (!gamepads) {
      return;
    }

    let connectedCount = 0;
    const nowMs = performance.now();

    for (let i = 0; i < Math.min(gamepads.length, GAMEPAD_MAX_CONTROLLERS); i++) {
      const gamepad = gamepads[i];

      if (gamepad && gamepad.connected) {
        connectedCount++;
        this.updateGamepadBitmap(i, gamepad);
        const overlayShortcutGate = evaluateControllerOverlayShortcutGate(
          gamepad,
          this.gamepadOverlayChordStates.get(i) ?? null,
          nowMs,
        );
        if (overlayShortcutGate.nextState) {
          this.gamepadOverlayChordStates.set(i, overlayShortcutGate.nextState);
        } else {
          this.gamepadOverlayChordStates.delete(i);
        }
        const overlayShortcutPressed = overlayShortcutGate.overlayPressed;
        const prevOverlayShortcutPressed = this.gamepadMetaPressed.get(i) ?? false;
        if (overlayShortcutPressed && !prevOverlayShortcutPressed) {
          try {
            this.dependencies.onControllerMetaPress?.({ controllerId: i, gamepad });
          } catch {
            // Host callbacks must never break stream input polling.
          }
        }
        this.gamepadMetaPressed.set(i, overlayShortcutPressed);

        // Track connected gamepads and update bitmap
        if (!this.connectedGamepads.has(i)) {
          this.connectedGamepads.add(i);
          this.dependencies.log(`Gamepad ${i} connected: ${gamepad.id}`);
          this.dependencies.log(`  Buttons: ${gamepad.buttons.length}, Axes: ${gamepad.axes.length}, Mapping: ${gamepad.mapping}`);
          this.dependencies.log(`  Bitmap now: 0x${this.gamepadBitmap.toString(16)}`);
          this.dependencies.onConnectedGamepadsChanged(this.connectedGamepads.size, true);
        }

        // Read and encode gamepad state.
        // Skip when blocked, overlay chord preempts, or external native window owns pads.
        // Internal native mode still forwards gamepads through the Electron bridge.
        if (
          streamInputBlocked
          || (this.dependencies.isNativeInputActive() && !this.dependencies.isNativeElectronInputBridge())
          || overlayShortcutGate.preemptInput
        ) {
          continue;
        }
        const gamepadInput = this.readGamepadState(gamepad, i);
        const stateChanged = this.hasGamepadStateChanged(i, gamepadInput);

        // Send if state changed OR as a keepalive to maintain server controller presence
        // Games detect active input device by receiving packets; if we stop sending,
        // the game falls back to showing keyboard/mouse prompts.
        const needsSend = shouldSendGamepadPacket(
          stateChanged,
          nowMs - this.lastGamepadSendMs,
        );

        if (needsSend) {
          const usePR = this.dependencies.canSendPartiallyReliableGamepad(i);
          const bytes = this.dependencies.inputEncoder.encodeGamepadState(gamepadInput, this.gamepadBitmap, usePR);
          if (usePR) {
            this.dependencies.sendPartiallyReliable(bytes);
          } else {
            this.dependencies.sendReliable(bytes);
          }
          this.lastGamepadSendMs = nowMs;

          if (stateChanged) {
            this.previousGamepadStates.set(i, { ...gamepadInput });
          }

          // Log first N gamepad sends for debugging
          if (stateChanged) {
            this.gamepadSendCount++;
            if (this.gamepadSendCount <= 20) {
              this.dependencies.log(`Gamepad send #${this.gamepadSendCount}: pad=${i} btns=0x${gamepadInput.buttons.toString(16)} lt=${gamepadInput.leftTrigger} rt=${gamepadInput.rightTrigger} lx=${gamepadInput.leftStickX} ly=${gamepadInput.leftStickY} rx=${gamepadInput.rightStickX} ry=${gamepadInput.rightStickY} bytes=${bytes.length}`);
            }
          }
        }
      } else if (this.connectedGamepads.has(i)) {
        // Gamepad disconnected — clear bit from bitmap
        this.stopGamepadRumble(i, gamepad ?? undefined);
        this.connectedGamepads.delete(i);
        this.gamepadMetaPressed.delete(i);
        this.gamepadOverlayChordStates.delete(i);
        this.previousGamepadStates.delete(i);
        this.clearGamepadBitmap(i);
        this.dependencies.log(`Gamepad ${i} disconnected, bitmap now: 0x${this.gamepadBitmap.toString(16)}`);
        this.dependencies.onConnectedGamepadsChanged(this.connectedGamepads.size, true);

        // Send state with updated bitmap (gamepad bit cleared = disconnected)
        const disconnectState: GamepadInput = {
          controllerId: i,
          buttons: 0,
          leftTrigger: 0,
          rightTrigger: 0,
          leftStickX: 0,
          leftStickY: 0,
          rightStickX: 0,
          rightStickY: 0,
          connected: false,
          timestampUs: timestampUs(),
        };
        const usePR = this.dependencies.canSendPartiallyReliableGamepad(i);
        const bytes = this.dependencies.inputEncoder.encodeGamepadState(disconnectState, this.gamepadBitmap, usePR);
        if (usePR) {
          this.dependencies.sendPartiallyReliable(bytes);
        } else {
          this.dependencies.sendReliable(bytes);
        }
      }
    }

    this.dependencies.onConnectedGamepadsChanged(connectedCount, false);
    this.updateHapticsAdvertisement(this.hasConnectedHapticGamepad());
  }

  private readGamepadState(gamepad: Gamepad, controllerId: number): GamepadInput {
    const buttons = mapGamepadButtons(gamepad);
    const axes = readGamepadAxes(gamepad);

    return {
      controllerId,
      buttons,
      leftTrigger: normalizeToUint8(axes.leftTrigger),
      rightTrigger: normalizeToUint8(axes.rightTrigger),
      leftStickX: normalizeToInt16(axes.leftStickX),
      leftStickY: normalizeToInt16(axes.leftStickY),
      rightStickX: normalizeToInt16(axes.rightStickX),
      rightStickY: normalizeToInt16(axes.rightStickY),
      connected: true,
      timestampUs: timestampUs(),
    };
  }

  private hasGamepadStateChanged(controllerId: number, newState: GamepadInput): boolean {
    const prevState = this.previousGamepadStates.get(controllerId);
    if (!prevState) {
      return true;
    }

    return (
      prevState.buttons !== newState.buttons ||
      prevState.leftTrigger !== newState.leftTrigger ||
      prevState.rightTrigger !== newState.rightTrigger ||
      prevState.leftStickX !== newState.leftStickX ||
      prevState.leftStickY !== newState.leftStickY ||
      prevState.rightStickX !== newState.rightStickX ||
      prevState.rightStickY !== newState.rightStickY
    );
  }

  readonly onGamepadConnected = (event: GamepadEvent): void => {
    this.dependencies.log(`Gamepad connected event: ${event.gamepad.id}`);
    // The polling loop will detect and handle the new gamepad
  };

  readonly onGamepadDisconnected = (event: GamepadEvent): void => {
    this.dependencies.log(`Gamepad disconnected event: ${event.gamepad.id}`);
    this.stopGamepadRumble(event.gamepad.index, event.gamepad);
    // The polling loop will detect and handle the disconnection
  };

  private logHapticsWarning(message: string): void {
    const nowMs = performance.now();
    if (nowMs - this.lastHapticsWarningAtMs < GamepadController.HAPTICS_LOG_INTERVAL_MS) {
      return;
    }
    this.lastHapticsWarningAtMs = nowMs;
    this.dependencies.log(message);
  }

  private getConnectedRumbleGamepads(): ConnectedRumbleGamepad[] {
    const gamepads = navigator.getGamepads();
    if (!gamepads) {
      return [];
    }

    const connected: ConnectedRumbleGamepad[] = [];
    for (let i = 0; i < Math.min(gamepads.length, GAMEPAD_MAX_CONTROLLERS); i++) {
      const gamepad = gamepads[i];
      if (gamepad?.connected) {
        connected.push({ index: i, gamepad, api: getGamepadRumbleApi(gamepad) });
      }
    }
    return connected;
  }

  private hasConnectedHapticGamepad(): boolean {
    const gamepads = navigator.getGamepads();
    if (!gamepads) {
      return false;
    }

    for (let i = 0; i < Math.min(gamepads.length, GAMEPAD_MAX_CONTROLLERS); i++) {
      const gamepad = gamepads[i];
      if (gamepad?.connected && getGamepadRumbleApi(gamepad)) {
        return true;
      }
    }
    return false;
  }

  private updateHapticsAdvertisement(enabled: boolean): void {
    if (!this.dependencies.isInputReady() || !this.dependencies.isReliableChannelOpen() || this.hapticsAdvertised === enabled) {
      return;
    }

    this.dependencies.sendReliable(this.dependencies.inputEncoder.encodeHapticsEnabled(enabled));
    this.hapticsAdvertised = enabled;
    this.dependencies.log(`Gamepad haptics advertised: ${enabled ? "enabled" : "disabled"}`);
  }

  private findConnectedGamepad(controllerId: number): ConnectedRumbleGamepad | null {
    const connected = this.getConnectedRumbleGamepads();
    if (connected.length === 0) {
      this.logHapticsWarning(`Input haptics: no haptic-capable gamepad for controller ${controllerId} (connected=0)`);
      return null;
    }

    const exact = controllerId >= 0 && controllerId < GAMEPAD_MAX_CONTROLLERS
      ? connected.find((candidate) => candidate.index === controllerId)
      : undefined;
    if (exact?.api) {
      return exact;
    }

    const hapticConnected = connected.filter((candidate) => candidate.api);
    const indexedFallback = controllerId >= 0 && controllerId < GAMEPAD_MAX_CONTROLLERS
      ? hapticConnected[controllerId]
      : undefined;
    if (indexedFallback) {
      return indexedFallback;
    }

    if (hapticConnected.length === 1) {
      return hapticConnected[0];
    }

    this.logHapticsWarning(
      `Input haptics: no haptic-capable gamepad for controller ${controllerId} (connected=${connected.length})`,
    );
    return null;
  }

  private applyRumbleApi(api: GamepadRumbleApi, index: number, weakMagnitude: number, strongMagnitude: number, isStop: boolean): void {
    const duration = isStop ? 0 : GamepadController.RUMBLE_EFFECT_MS;
    let usedPlayEffect = false;
    if (api.playEffectActuator) {
      usedPlayEffect = true;
      void api.playEffectActuator.playEffect("dual-rumble", {
        startDelay: 0,
        duration,
        weakMagnitude: isStop ? 0 : weakMagnitude,
        strongMagnitude: isStop ? 0 : strongMagnitude,
      }).catch(() => {});
    }

    if (api.pulseActuator && (isStop || !usedPlayEffect)) {
      if (!isStop && !this.fallbackHapticsSupportLogged[index]) {
        this.fallbackHapticsSupportLogged[index] = true;
        this.dependencies.log(`Gamepad ${index} fallback pulse haptics available`);
      }
      void api.pulseActuator.pulse(isStop ? 0 : Math.max(weakMagnitude, strongMagnitude), duration).catch(() => {});
    }
  }

  private applyGamepadRumble(controllerId: number, weakMagnitude16: number, strongMagnitude16: number): void {
    const target = this.findConnectedGamepad(controllerId);
    if (!target) {
      return;
    }
    if (!target.api) {
      return;
    }

    const index = target.index;
    if (target.api.playEffectActuator && !this.hapticsSupportLogged[index]) {
      this.hapticsSupportLogged[index] = true;
      this.dependencies.log(`Gamepad ${index} dual-rumble haptics available`);
    }

    const weakMagnitude = clampRumbleMagnitude(weakMagnitude16 / 65535);
    const strongMagnitude = clampRumbleMagnitude(strongMagnitude16 / 65535);
    const isStop = weakMagnitude === 0 && strongMagnitude === 0;
    const nowMs = performance.now();
    this.lastRumbleWeak[index] = weakMagnitude;
    this.lastRumbleStrong[index] = strongMagnitude;

    if (
      !isStop
      && this.lastRumbleEffectAtMs[index] !== 0
      && nowMs - this.lastRumbleEffectAtMs[index] <= GamepadController.RUMBLE_THROTTLE_MS
    ) {
      return;
    }

    this.lastRumbleEffectAtMs[index] = isStop ? 0 : nowMs;
    this.applyRumbleApi(target.api, index, weakMagnitude, strongMagnitude, isStop);
  }

  private stopGamepadRumble(controllerId: number, gamepad?: Gamepad): void {
    if (controllerId < 0 || controllerId >= GAMEPAD_MAX_CONTROLLERS) {
      return;
    }
    if (gamepad) {
      const api = getGamepadRumbleApi(gamepad);
      if (api) {
        this.applyRumbleApi(api, controllerId, 0, 0, true);
      }
    } else {
      this.applyGamepadRumble(controllerId, 0, 0);
    }
    this.lastRumbleWeak[controllerId] = 0;
    this.lastRumbleStrong[controllerId] = 0;
    this.lastRumbleEffectAtMs[controllerId] = 0;
    this.hapticsSupportLogged[controllerId] = false;
    this.fallbackHapticsSupportLogged[controllerId] = false;
  }

  private stopAllGamepadRumble(): void {
    for (const target of this.getConnectedRumbleGamepads()) {
      if (target.api) {
        this.applyRumbleApi(target.api, target.index, 0, 0, true);
      }
    }
    for (let i = 0; i < this.lastRumbleWeak.length; i++) {
      this.lastRumbleWeak[i] = 0;
      this.lastRumbleStrong[i] = 0;
      this.lastRumbleEffectAtMs[i] = 0;
      this.hapticsSupportLogged[i] = false;
      this.fallbackHapticsSupportLogged[i] = false;
    }
    this.lastHapticsWarningAtMs = 0;
  }

  private parseLegacyHapticPacket(view: DataView, offset: number): boolean {
    if (offset < 0 || offset + 10 > view.byteLength) {
      this.logHapticsWarning(`Input haptics: malformed legacy packet (${view.byteLength - offset} bytes)`);
      return false;
    }

    const kind = view.getUint16(offset, true);
    if (kind !== 1) {
      if (kind !== 0) {
        this.logHapticsWarning(`Input haptics: unknown legacy kind ${kind}`);
      }
      return false;
    }

    const length = view.getUint16(offset + 2, true);
    if (length < 6) {
      return false;
    }

    const controllerId = view.getUint16(offset + 4, true);
    const weakMagnitude = view.getUint16(offset + 6, true);
    const strongMagnitude = view.getUint16(offset + 8, true);
    this.applyGamepadRumble(controllerId, weakMagnitude, strongMagnitude);
    return true;
  }

  private parseOcHapticPacket(view: DataView, offset: number): boolean {
    if (offset < 0 || offset + 9 > view.byteLength) {
      this.logHapticsWarning(`Input haptics: malformed Oc packet (${view.byteLength - offset} bytes)`);
      return false;
    }

    const controllerByte = view.getUint8(offset);
    if (controllerByte < 6 || controllerByte >= 10) {
      this.logHapticsWarning(`Input haptics: unknown Oc controller byte ${controllerByte}`);
      return false;
    }

    const reportKind = view.getUint8(offset + 3);
    const flags = view.getUint8(offset + 4);
    if (reportKind !== 5 || (flags & ~1) !== 0) {
      this.logHapticsWarning(`Input haptics: unsupported Oc report kind=${reportKind} flags=0x${flags.toString(16)}`);
      return false;
    }

    const controllerId = controllerByte - 6;
    const weakMagnitude = view.getUint8(offset + 7) << 8;
    const strongMagnitude = view.getUint8(offset + 8) << 8;
    this.applyGamepadRumble(controllerId, weakMagnitude, strongMagnitude);
    return true;
  }

  private parseInputSubMessage(view: DataView, offset: number): boolean {
    if (offset < 0 || offset + 4 > view.byteLength) {
      this.logHapticsWarning(`Input haptics: malformed sub-message (${view.byteLength - offset} bytes)`);
      return false;
    }

    const type = view.getUint32(offset, true);
    if (type === 267) {
      return this.parseLegacyHapticPacket(view, offset + 4);
    }
    if (type === 17) {
      return this.parseOcHapticPacket(view, offset + 4);
    }

    this.logHapticsWarning(`Input haptics: unknown sub-message type ${type}`);
    return false;
  }

  handleHapticsMessage(bytes: Uint8Array): void {
    if (bytes.length < 2) {
      return;
    }

    const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    const firstWord = view.getUint16(0, true);
    if (firstWord === 267) {
      this.parseLegacyHapticPacket(view, 2);
      return;
    }

    const wrapperType = firstWord & 0xff;
    switch (wrapperType) {
      case 34:
        this.parseInputSubMessage(view, 1);
        return;
      case 32:
      case 33:
      case 35:
      case 36:
      case 255:
        return;
      default:
        this.parseLegacyHapticPacket(view, 0);
    }
  }

}
