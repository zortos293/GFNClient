import type { NativeStreamerSessionContext } from "@shared/gfn";

import {
  negotiateNvstRtspSession,
  NvstRtspNegotiationError,
  type NvstRtspProbeInput,
  type NvstRtspSession,
} from "./probe";

export type GfnNvstUnavailableCode =
  | "missing-rtsps-endpoints"
  | "negotiation-failed"
  | "preparation-superseded";

export class GfnNvstUnavailableError extends Error {
  constructor(
    readonly code: GfnNvstUnavailableCode,
    message: string,
    options?: ErrorOptions,
  ) {
    super(message, options);
    this.name = "GfnNvstUnavailableError";
  }
}

export interface GfnNvstRtspOwner {
  prepare(context: NativeStreamerSessionContext): Promise<NativeStreamerSessionContext>;
  release(reason: string): Promise<void>;
}

export interface GfnNvstRtspSessionOwnerDependencies {
  negotiate(input: NvstRtspProbeInput): Promise<NvstRtspSession>;
  onLog?(message: string): void;
}

interface OwnedSession {
  sessionId: string;
  rtsp: NvstRtspSession;
}

function withoutNvstVideo(
  context: NativeStreamerSessionContext,
): NativeStreamerSessionContext {
  const { nvstVideo: _nvstVideo, ...rest } = context;
  return rest;
}

export class GfnNvstRtspSessionOwner implements GfnNvstRtspOwner {
  private active: OwnedSession | null = null;
  private revision = 0;
  private operation: Promise<void> = Promise.resolve();

  constructor(
    private readonly dependencies: GfnNvstRtspSessionOwnerDependencies = {
      negotiate: negotiateNvstRtspSession,
    },
  ) {}

  prepare(context: NativeStreamerSessionContext): Promise<NativeStreamerSessionContext> {
    const revision = ++this.revision;
    return this.enqueue(async () => {
      if (revision !== this.revision) {
        throw new GfnNvstUnavailableError(
          "preparation-superseded",
          "NVST preparation was superseded before it started",
        );
      }

      if (context.settings.transportMode !== "nvst") {
        await this.releaseActive("native transport is not NVST");
        return withoutNvstVideo(context);
      }

      const sessionId = context.session.sessionId;
      if (this.active?.sessionId === sessionId) {
        return {
          ...withoutNvstVideo(context),
          nvstVideo: this.active.rtsp.videoSession,
        };
      }

      await this.releaseActive(`replaced by GFN session ${sessionId}`);

      const rtspsEndpoints = context.session.rtspsEndpoints ?? [];
      if (rtspsEndpoints.length === 0) {
        const message = `GFN session ${sessionId} did not provide RTSPS endpoints for explicit NVST mode`;
        this.log(message);
        throw new GfnNvstUnavailableError("missing-rtsps-endpoints", message);
      }

      let rtsp: NvstRtspSession;
      try {
        rtsp = await this.dependencies.negotiate({
          sessionId,
          rtspsEndpoints,
          resolution: context.settings.resolution,
          fps: context.settings.fps,
          codec: context.session.negotiatedStreamProfile?.codec ?? context.settings.codec,
          onLog: this.dependencies.onLog,
        });
      } catch (error) {
        const detail = error instanceof NvstRtspNegotiationError
          ? `${error.code}: ${error.message}`
          : error instanceof Error
            ? error.message
            : String(error);
        const message = `NVST is unavailable for GFN session ${sessionId}: ${detail}`;
        this.log(message);
        throw new GfnNvstUnavailableError("negotiation-failed", message, {
          cause: error,
        });
      }

      if (revision !== this.revision) {
        await rtsp.release("superseded NVST preparation");
        throw new GfnNvstUnavailableError(
          "preparation-superseded",
          "NVST preparation was superseded before native startup",
        );
      }

      this.active = { sessionId, rtsp };
      this.log(`Retaining NVST RTSPS control session for GFN session ${sessionId}`);
      return {
        ...withoutNvstVideo(context),
        nvstVideo: rtsp.videoSession,
      };
    });
  }

  release(reason: string): Promise<void> {
    ++this.revision;
    return this.enqueue(() => this.releaseActive(reason));
  }

  private enqueue<T>(operation: () => Promise<T>): Promise<T> {
    const result = this.operation.then(operation, operation);
    this.operation = result.then(
      () => undefined,
      () => undefined,
    );
    return result;
  }

  private async releaseActive(reason: string): Promise<void> {
    const active = this.active;
    this.active = null;
    if (!active) {
      return;
    }

    this.log(`Releasing NVST RTSPS control session for GFN session ${active.sessionId} (${reason})`);
    try {
      await active.rtsp.release(reason);
    } catch (error) {
      this.log(
        `Failed to release NVST RTSPS control session for GFN session ${active.sessionId}: ${error instanceof Error ? error.message : String(error)}`,
      );
    }
  }

  private log(message: string): void {
    console.log(`[GfnNvstRtspOwner] ${message}`);
    this.dependencies.onLog?.(message);
  }
}
