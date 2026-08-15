export interface AntiAfkReminderConditions {
  antiAfkEnabled: boolean;
  isStreaming: boolean;
  isConnecting: boolean;
  showPersistentIndicator: boolean;
  intervalMs: number;
}

export function shouldScheduleAntiAfkReminder({
  antiAfkEnabled,
  isStreaming,
  isConnecting,
  showPersistentIndicator,
  intervalMs,
}: AntiAfkReminderConditions): boolean {
  return (
    antiAfkEnabled
    && isStreaming
    && !isConnecting
    && !showPersistentIndicator
    && Number.isFinite(intervalMs)
    && intervalMs > 0
  );
}

export interface ReminderTimerApi {
  setTimeout(callback: () => void, delayMs: number): number;
  clearTimeout(timer: number): void;
  setInterval(callback: () => void, delayMs: number): number;
  clearInterval(timer: number): void;
}

export class RecurringReminderScheduler {
  private intervalTimer: number | null = null;
  private hideTimer: number | null = null;
  private visible = false;

  constructor(
    private readonly timers: ReminderTimerApi,
    private readonly onVisibilityChange: (visible: boolean) => void,
  ) {}

  start(enabled: boolean, intervalMs: number, durationMs: number): void {
    this.stop();

    if (
      !enabled
      || !Number.isFinite(intervalMs)
      || intervalMs <= 0
      || !Number.isFinite(durationMs)
      || durationMs <= 0
    ) {
      return;
    }

    const normalizedIntervalMs = Math.max(1, Math.floor(intervalMs));
    const normalizedDurationMs = Math.max(1, Math.floor(durationMs));
    this.intervalTimer = this.timers.setInterval(() => {
      this.showFor(normalizedDurationMs);
    }, normalizedIntervalMs);
  }

  stop(): void {
    if (this.intervalTimer !== null) {
      this.timers.clearInterval(this.intervalTimer);
      this.intervalTimer = null;
    }
    if (this.hideTimer !== null) {
      this.timers.clearTimeout(this.hideTimer);
      this.hideTimer = null;
    }
    this.setVisible(false);
  }

  private showFor(durationMs: number): void {
    this.setVisible(true);
    if (this.hideTimer !== null) {
      this.timers.clearTimeout(this.hideTimer);
    }
    this.hideTimer = this.timers.setTimeout(() => {
      this.hideTimer = null;
      this.setVisible(false);
    }, durationMs);
  }

  private setVisible(visible: boolean): void {
    if (this.visible === visible) {
      return;
    }
    this.visible = visible;
    this.onVisibilityChange(visible);
  }
}
