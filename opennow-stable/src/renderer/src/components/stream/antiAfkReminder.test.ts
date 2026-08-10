/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import {
  RecurringReminderScheduler,
  shouldScheduleAntiAfkReminder,
  type ReminderTimerApi,
} from "./antiAfkReminder";

interface ScheduledTimer {
  callback: () => void;
  dueAt: number;
  intervalMs: number | null;
}

class FakeTimers implements ReminderTimerApi {
  private now = 0;
  private nextId = 1;
  private readonly timers = new Map<number, ScheduledTimer>();

  setTimeout(callback: () => void, delayMs: number): number {
    return this.add(callback, delayMs, null);
  }

  clearTimeout(timer: number): void {
    this.timers.delete(timer);
  }

  setInterval(callback: () => void, delayMs: number): number {
    return this.add(callback, delayMs, delayMs);
  }

  clearInterval(timer: number): void {
    this.timers.delete(timer);
  }

  advanceBy(durationMs: number): void {
    const target = this.now + durationMs;
    while (true) {
      const next = [...this.timers.entries()]
        .filter(([, timer]) => timer.dueAt <= target)
        .sort((left, right) => left[1].dueAt - right[1].dueAt || left[0] - right[0])[0];
      if (!next) {
        break;
      }

      const [id, timer] = next;
      this.now = timer.dueAt;
      if (timer.intervalMs === null) {
        this.timers.delete(id);
      } else {
        timer.dueAt += timer.intervalMs;
      }
      timer.callback();
    }
    this.now = target;
  }

  count(): number {
    return this.timers.size;
  }

  private add(callback: () => void, delayMs: number, intervalMs: number | null): number {
    const id = this.nextId++;
    this.timers.set(id, {
      callback,
      dueAt: this.now + delayMs,
      intervalMs,
    });
    return id;
  }
}

test("schedules reminders when Anti-AFK is active and the persistent indicator is hidden", () => {
  assert.equal(shouldScheduleAntiAfkReminder({
    antiAfkEnabled: true,
    isStreaming: true,
    isConnecting: false,
    showPersistentIndicator: false,
    intervalMs: 15 * 60 * 1000,
  }), true);
});

test("does not schedule inactive, disconnected, redundant, or disabled reminders", () => {
  const activeConditions = {
    antiAfkEnabled: true,
    isStreaming: true,
    isConnecting: false,
    showPersistentIndicator: false,
    intervalMs: 15 * 60 * 1000,
  };

  assert.equal(shouldScheduleAntiAfkReminder({ ...activeConditions, antiAfkEnabled: false }), false);
  assert.equal(shouldScheduleAntiAfkReminder({ ...activeConditions, isStreaming: false }), false);
  assert.equal(shouldScheduleAntiAfkReminder({ ...activeConditions, isConnecting: true }), false);
  assert.equal(shouldScheduleAntiAfkReminder({ ...activeConditions, showPersistentIndicator: true }), false);
  assert.equal(shouldScheduleAntiAfkReminder({ ...activeConditions, intervalMs: 0 }), false);
});

test("shows each reminder after the interval and hides it after the configured duration", () => {
  const timers = new FakeTimers();
  const visibility: boolean[] = [];
  const scheduler = new RecurringReminderScheduler(timers, (visible) => visibility.push(visible));

  scheduler.start(true, 15 * 60 * 1000, 5 * 1000);
  timers.advanceBy(15 * 60 * 1000 - 1);
  assert.deepEqual(visibility, []);

  timers.advanceBy(1);
  assert.deepEqual(visibility, [true]);

  timers.advanceBy(5 * 1000 - 1);
  assert.deepEqual(visibility, [true]);

  timers.advanceBy(1);
  assert.deepEqual(visibility, [true, false]);

  timers.advanceBy(15 * 60 * 1000 - 5 * 1000);
  assert.deepEqual(visibility, [true, false, true]);
});

test("stopping the scheduler hides an active reminder and clears all timers", () => {
  const timers = new FakeTimers();
  const visibility: boolean[] = [];
  const scheduler = new RecurringReminderScheduler(timers, (visible) => visibility.push(visible));

  scheduler.start(true, 1000, 5000);
  timers.advanceBy(1000);
  scheduler.stop();

  assert.deepEqual(visibility, [true, false]);
  assert.equal(timers.count(), 0);

  timers.advanceBy(20_000);
  assert.deepEqual(visibility, [true, false]);
});

test("restarting replaces existing timers instead of creating duplicate reminders", () => {
  const timers = new FakeTimers();
  const visibility: boolean[] = [];
  const scheduler = new RecurringReminderScheduler(timers, (visible) => visibility.push(visible));

  scheduler.start(true, 1000, 100);
  scheduler.start(true, 1000, 100);
  assert.equal(timers.count(), 1);

  timers.advanceBy(1000);
  assert.deepEqual(visibility, [true]);
  assert.equal(timers.count(), 2);

  timers.advanceBy(100);
  assert.deepEqual(visibility, [true, false]);
});

test("disabled scheduling creates no timers or visibility changes", () => {
  const timers = new FakeTimers();
  const visibility: boolean[] = [];
  const scheduler = new RecurringReminderScheduler(timers, (visible) => visibility.push(visible));

  scheduler.start(false, 1000, 100);
  scheduler.start(true, 0, 100);

  assert.equal(timers.count(), 0);
  timers.advanceBy(20_000);
  assert.deepEqual(visibility, []);
});
