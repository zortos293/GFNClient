import { app } from "electron";
import { randomUUID } from "node:crypto";
import { tmpdir } from "node:os";
import { spawn, type ChildProcessWithoutNullStreams } from "node:child_process";

import {
  createUnsupportedNativeStreamerStatus,
  isNativeStreamerSupportedPlatform,
  NATIVE_STREAMER_WINDOWS_ONLY_MESSAGE,
  type IceCandidatePayload,
  type KeyframeRequest,
  type MainToRendererSignalingEvent,
  type NativeStreamerBackendPreference,
  type NativeStreamerFeatureMode,
  type NativeVideoBackendPreference,
  type NativeStreamerStatus,
  type NativeGstreamerRuntimeStatus,
  type NativeRenderSurface,
  type NativeStreamerSessionContext,
  type SendAnswerRequest,
} from "@shared/gfn";
import {
  NATIVE_STREAMER_PROTOCOL_VERSION,
  type NativeStreamerCapabilities,
  type NativeStreamerCommand,
  type NativeStreamerEvent,
  type NativeStreamerInputPacket,
  type NativeStreamerMessage,
  type NativeStreamerResponse,
} from "@shared/nativeStreamer";
import type { NativeStreamerShortcutBindings } from "@shared/gfn";
import {
  createNativeStreamerDetectionFailureStatus,
  createNativeStreamerStatus,
  formatError,
} from "./capabilities";
import { resolveNativeStreamerExecutableCandidates } from "./executableDiscovery";
import {
  isNativeStreamerEvent,
  isNativeStreamerResponse,
  type NativeStreamerCommandInput,
} from "./protocol";
import { createNativeStreamerRuntimeEnvironment } from "./runtime";
import { NativeSurfaceUpdateQueue } from "./surfaceUpdateQueue";

interface NativeStreamerCallbacks {
  sendAnswer(payload: SendAnswerRequest): Promise<void>;
  sendIceCandidate(candidate: IceCandidatePayload): Promise<void>;
  requestKeyframe(payload: KeyframeRequest): Promise<void>;
  emit(event: MainToRendererSignalingEvent): void;
}

interface NativeStreamerManagerOptions extends NativeStreamerCallbacks {
  mainDir: string;
  getBackendPreference(): NativeStreamerBackendPreference;
  getVideoBackendPreference(): NativeVideoBackendPreference;
  getExecutablePathOverride(): string;
  getCloudGsyncMode(): NativeStreamerFeatureMode;
  getD3dFullscreenMode(): NativeStreamerFeatureMode;
  getExternalRendererEnabled(): boolean;
}

interface PendingRequest {
  resolve(message: NativeStreamerResponse): void;
  reject(error: Error): void;
  timeout: NodeJS.Timeout;
}

const HELLO_TIMEOUT_MS = 10000;
const BUNDLED_GSTREAMER_HELLO_TIMEOUT_MS = process.platform === "win32" ? 120000 : 30000;
const CONTROL_TIMEOUT_MS = 8000;
const SESSION_START_TIMEOUT_MS = process.platform === "win32" ? 90000 : 45000;
const SURFACE_UPDATE_TIMEOUT_MS = 15000;
const OFFER_TIMEOUT_MS = 20000;
const STOP_TIMEOUT_MS = 1200;
const MAX_INPUT_STDIN_BUFFER_BYTES = 64 * 1024;
const MIN_NATIVE_BITRATE_KBPS = 5_000;
const MAX_NATIVE_BITRATE_KBPS = 150_000;

function normalizeBitrateKbps(value: number): number {
  if (!Number.isFinite(value)) {
    return MIN_NATIVE_BITRATE_KBPS;
  }

  return Math.min(
    MAX_NATIVE_BITRATE_KBPS,
    Math.max(MIN_NATIVE_BITRATE_KBPS, Math.round(value)),
  );
}

export class NativeStreamerManager {
  private child: ChildProcessWithoutNullStreams | null = null;
  private startupPromise: Promise<void> | null = null;
  private stdoutBuffer = "";
  private stderrTail: string[] = [];
  private gstreamerRuntime: NativeGstreamerRuntimeStatus | null = null;
  private pending = new Map<string, PendingRequest>();
  private capabilities: NativeStreamerCapabilities | null = null;
  private activeSessionId: string | null = null;
  private inputBackpressureWarned = false;
  private answerInFlight = false;
  private queuedLocalIce: IceCandidatePayload[] = [];
  private queuedRemoteIceSessionId: string | null = null;
  private queuedRemoteIce: IceCandidatePayload[] = [];
  private readonly surfaceUpdates: NativeSurfaceUpdateQueue;

  constructor(private readonly options: NativeStreamerManagerOptions) {
    this.surfaceUpdates = new NativeSurfaceUpdateQueue(
      (surface) => this.request({ type: "surface", surface }, SURFACE_UPDATE_TIMEOUT_MS).then(() => undefined),
      (error) => console.warn("[NativeStreamer] Failed to update native render surface:", error),
    );
  }

  isRunning(): boolean {
    return this.child !== null;
  }

  hasActiveSession(): boolean {
    return this.activeSessionId !== null;
  }

  async prepareForSession(context: NativeStreamerSessionContext): Promise<void> {
    if (this.activeSessionId && this.activeSessionId !== context.session.sessionId) {
      await this.stop("new native streamer session");
    }
    this.prepareRemoteIceQueue(context.session.sessionId);

    await this.ensureProcess();

    if (this.activeSessionId === context.session.sessionId) {
      return;
    }

    if (context.settings.enableCloudGsync) {
      console.log(
        "[NativeStreamer] Cloud G-Sync/VRR mode resolved for this session; preserving unthrottled low-latency present behavior.",
      );
    }

    await this.request({
      type: "start",
      context,
    }, SESSION_START_TIMEOUT_MS);
    this.activeSessionId = context.session.sessionId;
    await this.flushQueuedRemoteIce(context.session.sessionId);
  }

  async handleOffer(sdp: string, context: NativeStreamerSessionContext): Promise<void> {
    const negotiatedProfile = context.session.negotiatedStreamProfile;
    console.log(
      "[NativeStreamer] Session context:",
      JSON.stringify({
        sessionId: context.session.sessionId,
        requestedResolution: context.settings.resolution,
        requestedFps: context.settings.fps,
        requestedCodec: context.settings.codec,
        negotiatedResolution: negotiatedProfile?.resolution,
        negotiatedFps: negotiatedProfile?.fps,
        negotiatedCodec: negotiatedProfile?.codec ?? context.settings.codec,
        requestedStreamingFeatures: context.session.requestedStreamingFeatures,
        finalizedStreamingFeatures: context.session.finalizedStreamingFeatures,
      }),
    );

    await this.prepareForSession(context);

    if (!this.capabilities?.supportsOfferAnswer) {
      console.warn(
        `[NativeStreamer] Backend "${this.capabilities?.backend ?? "unknown"}" reports offer/answer is not ready; forwarding offer for validation/fallback.`,
      );
    }

    this.answerInFlight = true;
    this.queuedLocalIce = [];

    try {
      const response = await this.request({
        type: "offer",
        sdp,
        context,
      }, OFFER_TIMEOUT_MS);

      if (response.type !== "answer") {
        throw new Error(`Native streamer returned ${response.type} instead of answer.`);
      }

      await this.options.sendAnswer(response.answer);
      this.answerInFlight = false;
      await this.flushQueuedLocalIce();
    } catch (error) {
      this.answerInFlight = false;
      this.queuedLocalIce = [];
      throw error;
    }

    this.options.emit({
      type: "log",
      message: "Native streamer accepted the WebRTC offer; waiting for decoded media.",
    });
  }

  async probeStatus(): Promise<NativeStreamerStatus> {
    if (!isNativeStreamerSupportedPlatform(process.platform)) {
      return createUnsupportedNativeStreamerStatus();
    }

    try {
      await this.ensureProcess();
      return createNativeStreamerStatus(
        this.capabilities,
        this.gstreamerRuntime,
        this.options.getVideoBackendPreference(),
        process.platform,
      );
    } catch (error) {
      return createNativeStreamerDetectionFailureStatus(
        error,
        this.gstreamerRuntime,
        process.platform,
      );
    }
  }

  async addRemoteIce(candidate: IceCandidatePayload, context: NativeStreamerSessionContext): Promise<void> {
    const sessionId = context.session.sessionId;
    if (!this.child || this.activeSessionId !== sessionId) {
      this.queueRemoteIce(sessionId, candidate);
      return;
    }

    await this.sendRemoteIce(candidate);
  }

  drainQueuedRemoteIce(sessionId: string): IceCandidatePayload[] {
    if (this.queuedRemoteIceSessionId !== sessionId) {
      return [];
    }

    const queued = this.queuedRemoteIce;
    this.queuedRemoteIceSessionId = null;
    this.queuedRemoteIce = [];
    return queued;
  }

  sendInput(input: NativeStreamerInputPacket): void {
    const child = this.child;
    if (
      !child
      || child.killed
      || !child.stdin.writable
      || !this.activeSessionId
      || !this.capabilities?.supportsInput
    ) {
      return;
    }

    if (child.stdin.writableLength > MAX_INPUT_STDIN_BUFFER_BYTES) {
      if (!this.inputBackpressureWarned) {
        this.inputBackpressureWarned = true;
        console.warn("[NativeStreamer] Dropping native input while streamer stdin is backpressured.");
      }
      return;
    }

    const payload = {
      id: randomUUID(),
      type: "input",
      input,
    } satisfies NativeStreamerCommand;

    const flushed = child.stdin.write(`${JSON.stringify(payload)}\n`, "utf8", (error) => {
      if (error && !this.inputBackpressureWarned) {
        this.inputBackpressureWarned = true;
        console.warn("[NativeStreamer] Failed to write native input:", error);
      }
    });

    if (!flushed && !this.inputBackpressureWarned) {
      this.inputBackpressureWarned = true;
      console.warn("[NativeStreamer] Native input writer reported backpressure; input will be dropped until it drains.");
      child.stdin.once("drain", () => {
        this.inputBackpressureWarned = false;
      });
    } else if (flushed) {
      this.inputBackpressureWarned = false;
    }
  }

  updateSurface(surface: NativeRenderSurface): void {
    this.surfaceUpdates.update(surface);
  }

  updateBitrateLimit(maxBitrateKbps: number): void {
    if (!this.child || !this.activeSessionId) {
      return;
    }

    void this.request({
      type: "bitrate",
      maxBitrateKbps: normalizeBitrateKbps(maxBitrateKbps),
    }, CONTROL_TIMEOUT_MS).catch((error) => {
      console.warn("[NativeStreamer] Failed to update native bitrate limit:", error);
    });
  }

  setInputPaused(paused: boolean): void {
    if (!this.child || !this.activeSessionId) {
      return;
    }

    void this.request({
      type: "input-paused",
      paused,
    }, CONTROL_TIMEOUT_MS).catch((error) => {
      console.warn("[NativeStreamer] Failed to update native input pause state:", error);
    });
  }

  updateShortcuts(shortcuts: NativeStreamerShortcutBindings): void {
    if (!this.child || !this.activeSessionId) {
      return;
    }

    void this.request({
      type: "update-shortcuts",
      shortcuts,
    }, CONTROL_TIMEOUT_MS).catch((error) => {
      console.warn("[NativeStreamer] Failed to update native shortcut bindings:", error);
    });
  }

  async stop(reason = "stopped"): Promise<void> {
    const child = this.child;
    this.activeSessionId = null;
    this.capabilities = null;
    this.surfaceUpdates.markNotReady();
    this.clearQueuedRemoteIce();

    if (!child) {
      return;
    }

    try {
      await this.request({ type: "stop", reason }, STOP_TIMEOUT_MS);
    } catch (error) {
      console.warn("[NativeStreamer] Stop request failed:", error);
    } finally {
      this.terminateProcess();
    }
  }

  dispose(reason = "disposed"): void {
    this.activeSessionId = null;
    this.capabilities = null;
    this.surfaceUpdates.markNotReady();
    this.clearQueuedRemoteIce();
    this.rejectPending(new Error(`Native streamer ${reason}.`));
    this.terminateProcess();
  }

  private async ensureProcess(): Promise<void> {
    if (!isNativeStreamerSupportedPlatform(process.platform)) {
      throw new Error(NATIVE_STREAMER_WINDOWS_ONLY_MESSAGE);
    }

    if (this.child && this.capabilities) {
      return;
    }

    if (this.startupPromise) {
      await this.startupPromise;
      return;
    }

    if (this.child && !this.capabilities) {
      console.warn("[NativeStreamer] Restarting native streamer after an incomplete startup handshake.");
      this.rejectPending(new Error("Native streamer startup handshake did not complete."));
      this.terminateProcess();
      this.stdoutBuffer = "";
      this.stderrTail = [];
    }

    const startupPromise = (async () => {
      const backendPreference = this.options.getBackendPreference();
      let lastError: Error | null = null;

      for (const executablePath of resolveNativeStreamerExecutableCandidates({
        platform: process.platform,
        arch: process.arch,
        resourcesPath: process.resourcesPath,
        appPath: app.getAppPath(),
        mainDir: this.options.mainDir,
        isPackaged: app.isPackaged,
        envExecutablePath: process.env.OPENNOW_NATIVE_STREAMER,
        getConfiguredPath: () => this.options.getExecutablePathOverride(),
        cacheContext: {
          appVersion: app.getVersion(),
          isPackaged: app.isPackaged,
          platform: process.platform,
          resourcesPath: process.resourcesPath,
          tempDirectory: tmpdir(),
          userDataPath: app.getPath("userData"),
        },
      })) {
        try {
          await this.startProcess(executablePath, backendPreference);
          return;
        } catch (error) {
          lastError = error instanceof Error ? error : new Error(String(error));
          console.warn(
            `[NativeStreamer] Failed to initialize ${executablePath}: ${formatError(lastError)}`,
          );
          this.rejectPending(lastError);
          this.terminateProcess();
          this.stdoutBuffer = "";
          this.stderrTail = [];
          this.capabilities = null;
        }
      }

      throw lastError ?? new Error("Native streamer could not be initialized from any candidate path.");
    })();

    this.startupPromise = startupPromise;
    try {
      await startupPromise;
    } finally {
      if (this.startupPromise === startupPromise) {
        this.startupPromise = null;
      }
    }
  }

  private async startProcess(
    executablePath: string,
    backendPreference: NativeStreamerBackendPreference,
  ): Promise<void> {
    console.log("[NativeStreamer] Starting:", executablePath);
    console.log("[NativeStreamer] Backend preference:", backendPreference);
    const videoBackendPreference = this.options.getVideoBackendPreference();
    console.log("[NativeStreamer] Video backend preference:", videoBackendPreference);

    const { env: childEnv, runtimeStatus } = createNativeStreamerRuntimeEnvironment({
      executablePath,
      baseEnv: process.env,
      platform: process.platform,
      arch: process.arch,
      userDataPath: app.getPath("userData"),
      protocolVersion: NATIVE_STREAMER_PROTOCOL_VERSION,
      backendPreference,
      videoBackendPreference,
      externalRendererEnabled: process.platform === "win32"
        ? this.options.getExternalRendererEnabled()
        : false,
      cloudGsyncMode: this.options.getCloudGsyncMode(),
      d3dFullscreenMode: this.options.getD3dFullscreenMode(),
    });
    this.gstreamerRuntime = runtimeStatus;
    if (runtimeStatus.bundled) {
      console.log("[NativeStreamer] Using bundled GStreamer runtime:", runtimeStatus.path);
    } else {
      console.log("[NativeStreamer]", runtimeStatus.message);
    }

    const child = spawn(executablePath, [], {
      stdio: "pipe",
      // The default native path lets the GStreamer video sink create its own
      // render window. Hiding the child process also hides that sink window on
      // Windows, which leaves the Electron input placeholder black.
      windowsHide: false,
      env: childEnv,
    });

    this.child = child;
    this.stdoutBuffer = "";
    this.stderrTail = [];

    child.stdout.setEncoding("utf8");
    child.stdout.on("data", (chunk: string) => this.handleStdout(chunk));
    child.stderr.setEncoding("utf8");
    child.stderr.on("data", (chunk: string) => {
      for (const line of chunk.split(/\r?\n/)) {
        if (line.trim()) {
          this.appendStderr(line);
          console.warn(`[NativeStreamer] ${line}`);
        }
      }
    });

    child.once("error", (error) => {
      this.options.emit({ type: "error", message: `Native streamer failed to start: ${formatError(error)}` });
      this.handleProcessExit(`spawn error: ${formatError(error)}`);
    });

    child.once("exit", (code, signal) => {
      const reason = signal ? `signal ${signal}` : `exit code ${code ?? "unknown"}`;
      this.handleProcessExit(reason);
    });

    const helloTimeoutMs = runtimeStatus.bundled ? BUNDLED_GSTREAMER_HELLO_TIMEOUT_MS : HELLO_TIMEOUT_MS;
    const response = await this.request({
      type: "hello",
      protocolVersion: NATIVE_STREAMER_PROTOCOL_VERSION,
    }, helloTimeoutMs);

    if (response.type !== "ready") {
      throw new Error(`Native streamer returned ${response.type} instead of ready.`);
    }

    this.capabilities = response.capabilities;
    console.log("[NativeStreamer] Capabilities:", response.capabilities);
    if (response.capabilities.protocolVersion !== NATIVE_STREAMER_PROTOCOL_VERSION) {
      throw new Error(
        `Native streamer reported protocolVersion=${response.capabilities.protocolVersion}, expected ${NATIVE_STREAMER_PROTOCOL_VERSION}.`,
      );
    }
    this.assertBackendPreference(response.capabilities, backendPreference);
    await this.surfaceUpdates.markReady();
  }

  private assertBackendPreference(
    capabilities: NativeStreamerCapabilities,
    backendPreference: NativeStreamerBackendPreference,
  ): void {
    if (backendPreference === "auto" || capabilities.backend === backendPreference) {
      return;
    }

    const reason = capabilities.fallbackReason ? ` ${capabilities.fallbackReason}` : "";
    throw new Error(
      `Native streamer backend "${backendPreference}" is unavailable; process selected "${capabilities.backend}".${reason}`,
    );
  }

  private request(input: NativeStreamerCommandInput, timeoutMs: number): Promise<NativeStreamerResponse> {
    const child = this.child;
    if (!child || child.killed || !child.stdin.writable) {
      return Promise.reject(new Error("Native streamer process is not running."));
    }

    const id = randomUUID();
    const payload = { ...input, id } as NativeStreamerCommand;

    return new Promise<NativeStreamerResponse>((resolveRequest, rejectRequest) => {
      const timeout = setTimeout(() => {
        this.pending.delete(id);
        rejectRequest(new Error(`Native streamer request "${input.type}" timed out after ${timeoutMs}ms.${this.formatStderrTail()}`));
      }, timeoutMs);
      timeout.unref?.();

      this.pending.set(id, {
        resolve: (message) => {
          clearTimeout(timeout);
          resolveRequest(message);
        },
        reject: (error) => {
          clearTimeout(timeout);
          rejectRequest(error);
        },
        timeout,
      });

      child.stdin.write(`${JSON.stringify(payload)}\n`, "utf8", (error) => {
        if (!error) {
          return;
        }
        const pending = this.pending.get(id);
        if (pending) {
          this.pending.delete(id);
          pending.reject(error);
        }
      });
    });
  }

  private handleStdout(chunk: string): void {
    this.stdoutBuffer += chunk;
    const lines = this.stdoutBuffer.split(/\r?\n/);
    this.stdoutBuffer = lines.pop() ?? "";

    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed) {
        continue;
      }
      this.handleLine(trimmed);
    }
  }

  private handleLine(line: string): void {
    let message: NativeStreamerMessage;
    try {
      message = JSON.parse(line) as NativeStreamerMessage;
    } catch {
      console.log(`[NativeStreamer] ${line}`);
      return;
    }

    if (isNativeStreamerResponse(message)) {
      this.handleResponse(message);
      return;
    }

    if (isNativeStreamerEvent(message)) {
      this.handleEvent(message);
    }
  }

  private handleResponse(message: NativeStreamerResponse): void {
    const pending = this.pending.get(message.id);
    if (!pending) {
      console.warn("[NativeStreamer] Ignoring response for unknown request:", message.id);
      return;
    }

    this.pending.delete(message.id);
    if (message.type === "error") {
      pending.reject(new Error(message.code ? `${message.code}: ${message.message}` : message.message));
      return;
    }

    pending.resolve(message);
  }

  private handleEvent(message: NativeStreamerEvent): void {
    if (message.type === "log") {
      const text = `[NativeStreamer] ${message.message}`;
      if (message.level === "error") {
        console.error(text);
      } else if (message.level === "warn") {
        console.warn(text);
      } else {
        console.log(text);
      }
      this.options.emit({ type: "log", message: text });
      return;
    }

    if (message.type === "local-ice") {
      if (this.answerInFlight) {
        this.queuedLocalIce.push(message.candidate);
        return;
      }

      this.forwardLocalIce(message.candidate);
      return;
    }

    if (message.type === "input-ready") {
      console.log(`[NativeStreamer] Input protocol ready: v${message.protocolVersion}`);
      this.options.emit({ type: "native-input-ready", protocolVersion: message.protocolVersion });
      return;
    }

    if (message.type === "shortcut") {
      this.options.emit({ type: "native-shortcut", action: message.action });
      return;
    }

    if (message.type === "clipboard-paste") {
      this.options.emit({ type: "native-clipboard-paste" });
      return;
    }

    if (message.type === "input-capture-changed") {
      this.options.emit({ type: "native-input-capture-changed", captured: message.captured });
      return;
    }

    if (message.type === "video-stall") {
      const formatAge = (value: number | undefined): string => value === undefined ? "n/a" : `${value}ms`;
      const stats = [
        `stall=${message.stallMs}ms`,
        `stage=${message.likelyStage ?? "unknown"}`,
        `encoded=${(message.encodedKbps ?? 0).toFixed(0)}kbps`,
        `decoded=${message.decodedFps.toFixed(1)}fps`,
        `sink=${message.sinkFps.toFixed(1)}fps`,
        `requestedFps=${message.requestedFps ?? "n/a"}`,
        `capsFramerate=${message.capsFramerate ?? "n/a"}`,
        `queueMode=${message.queueMode ?? "unknown"}`,
        `partialFlushes=${message.partialFlushCount ?? 0}`,
        `completeFlushes=${message.completeFlushCount ?? 0}`,
        `lastTransition=${message.lastTransitionType ?? "none"}`,
        `ages=encoded:${formatAge(message.encodedAgeMs)} decoded:${formatAge(message.decodedAgeMs)} sink:${formatAge(message.sinkAgeMs)}`,
        `rendered=${message.sinkRendered ?? "n/a"}`,
        `dropped=${message.sinkDropped ?? "n/a"}`,
        `memory=${message.memoryMode ?? "unknown"}`,
        `zeroCopy=${message.zeroCopy ?? "unknown"}`,
        `zeroCopyD3D11=${message.zeroCopyD3D11}`,
        `zeroCopyD3D12=${message.zeroCopyD3D12}`,
      ].join(" ");
      console.warn(`[NativeStreamer] Video stall recovery attempt ${message.recoveryAttempt}: ${stats}`);
      this.options.emit({
        type: "log",
        message: `[NativeStreamer] Video stall recovery attempt ${message.recoveryAttempt}: ${stats}`,
      });
      void this.options.requestKeyframe({
        reason: "native-video-stall",
        backlogFrames: 0,
        attempt: message.recoveryAttempt,
      }).catch((error) => {
        console.warn("[NativeStreamer] Failed to request video keyframe after stall:", error);
      });
      return;
    }

    if (message.type === "video-transition") {
      const transition = message.transition;
      const summary = transition.summary ?? `${transition.transitionType} @ ${transition.atMs}ms`;
      console.warn(`[NativeStreamer] Video transition: ${summary}`);
      this.options.emit({
        type: "native-stream-transition",
        transition,
      });
      this.options.emit({
        type: "log",
        message: `[NativeStreamer] Video transition: ${summary}`,
      });
      return;
    }

    if (message.type === "stats") {
      this.options.emit({
        type: "native-stream-stats",
        stats: message.stats,
      });
      return;
    }

    if (message.type === "status") {
      console.log(`[NativeStreamer] Status: ${message.status}${message.message ? ` (${message.message})` : ""}`);
      if (message.status === "streaming") {
        this.options.emit({ type: "native-stream-started", message: message.message });
      } else if (message.status === "stopped") {
        this.options.emit({ type: "native-stream-stopped", reason: message.message });
      }
      return;
    }

    if (message.type === "error") {
      this.options.emit({ type: "error", message: `Native streamer error: ${message.message}` });
    }
  }

  private handleProcessExit(reason: string): void {
    if (!this.child) {
      return;
    }

    const tail = this.formatStderrTail();
    const hadActiveSession = this.activeSessionId !== null;
    const stoppedReason = `process ended (${reason})`;
    console.warn(`[NativeStreamer] Process ended (${reason})${tail}`);
    this.child = null;
    this.stdoutBuffer = "";
    this.stderrTail = [];
    this.activeSessionId = null;
    this.capabilities = null;
    this.surfaceUpdates.markNotReady();
    this.clearQueuedRemoteIce();
    this.rejectPending(new Error(`Native streamer process ended (${reason}).${tail}`));

    if (hadActiveSession) {
      this.options.emit({ type: "native-stream-stopped", reason: stoppedReason });
      this.options.emit({ type: "error", message: `Native streamer ${stoppedReason}.${tail}` });
    }
  }

  private appendStderr(line: string): void {
    this.stderrTail.push(line);
    if (this.stderrTail.length > 12) this.stderrTail.shift();
  }

  private formatStderrTail(): string {
    return this.stderrTail.length > 0 ? ` Recent stderr: ${this.stderrTail.join(" | ")}` : "";
  }

  private rejectPending(error: Error): void {
    for (const [id, pending] of this.pending.entries()) {
      clearTimeout(pending.timeout);
      pending.reject(error);
      this.pending.delete(id);
    }
  }

  private async flushQueuedLocalIce(): Promise<void> {
    const queued = this.queuedLocalIce;
    this.queuedLocalIce = [];

    for (const candidate of queued) {
      await this.forwardLocalIce(candidate);
    }
  }

  private prepareRemoteIceQueue(sessionId: string): void {
    if (this.queuedRemoteIceSessionId !== null && this.queuedRemoteIceSessionId !== sessionId) {
      this.clearQueuedRemoteIce();
    }
    this.queuedRemoteIceSessionId = sessionId;
  }

  private queueRemoteIce(sessionId: string, candidate: IceCandidatePayload): void {
    this.prepareRemoteIceQueue(sessionId);
    this.queuedRemoteIce.push(candidate);
  }

  private clearQueuedRemoteIce(): void {
    this.queuedRemoteIceSessionId = null;
    this.queuedRemoteIce = [];
  }

  private async flushQueuedRemoteIce(sessionId: string): Promise<void> {
    const queued = this.drainQueuedRemoteIce(sessionId);
    for (const candidate of queued) {
      await this.sendRemoteIce(candidate);
    }
  }

  private async sendRemoteIce(candidate: IceCandidatePayload): Promise<void> {
    await this.request({
      type: "remote-ice",
      candidate,
    }, CONTROL_TIMEOUT_MS);
  }

  private async forwardLocalIce(candidate: IceCandidatePayload): Promise<void> {
    try {
      await this.options.sendIceCandidate(candidate);
    } catch (error) {
      console.warn("[NativeStreamer] Failed to forward local ICE candidate:", error);
    }
  }

  private terminateProcess(): void {
    this.surfaceUpdates.markNotReady();
    const child = this.child;
    if (!child) {
      return;
    }

    this.child = null;
    try {
      child.kill();
    } catch (error) {
      console.warn("[NativeStreamer] Failed to terminate process:", error);
    }
  }
}
