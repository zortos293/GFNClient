export interface GlobalShortcutRegistrar {
  register(accelerator: string, callback: () => void): boolean;
  unregister(accelerator: string): void;
}

const STREAM_ESCAPE_ACCELERATOR = "Escape";

export class StreamEscapeShortcutController {
  private registered = false;

  constructor(
    private readonly registrar: GlobalShortcutRegistrar,
    private readonly onEscape: () => void,
  ) {}

  setCaptureActive(active: boolean): boolean {
    if (!active) {
      this.dispose();
      return false;
    }

    if (!this.registered) {
      this.registered = this.registrar.register(
        STREAM_ESCAPE_ACCELERATOR,
        this.onEscape,
      );
    }
    return this.registered;
  }

  dispose(): void {
    if (!this.registered) return;
    this.registrar.unregister(STREAM_ESCAPE_ACCELERATOR);
    this.registered = false;
  }
}
