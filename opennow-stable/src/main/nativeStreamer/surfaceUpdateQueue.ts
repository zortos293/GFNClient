import type { NativeRenderSurface } from "@shared/gfn";

type SurfaceSender = (surface: NativeRenderSurface) => Promise<void>;
type SurfaceErrorHandler = (error: unknown) => void;

/**
 * Delivers only the newest native render surface after the child handshake is
 * complete. Revisions make updates that arrive during an in-flight request
 * observable instead of relying on object identity or a lossy queued flag.
 */
export class NativeSurfaceUpdateQueue {
  private latestSurface: NativeRenderSurface | null = null;
  private latestRevision = 0;
  private deliveredRevision = 0;
  private processGeneration = 0;
  private ready = false;
  private inFlight = false;

  constructor(
    private readonly send: SurfaceSender,
    private readonly onError: SurfaceErrorHandler,
  ) {}

  update(surface: NativeRenderSurface): void {
    this.latestSurface = surface;
    this.latestRevision += 1;
    void this.flush();
  }

  markNotReady(): void {
    this.ready = false;
    this.processGeneration += 1;

    // A replacement process must receive the current surface even when it was
    // already delivered successfully to the process that just stopped.
    if (this.latestSurface) {
      this.deliveredRevision = Math.min(this.deliveredRevision, this.latestRevision - 1);
    }
  }

  markReady(): Promise<void> {
    this.ready = true;
    return this.flush();
  }

  private async flush(): Promise<void> {
    if (
      this.inFlight
      || !this.ready
      || !this.latestSurface
      || this.deliveredRevision >= this.latestRevision
    ) {
      return;
    }

    this.inFlight = true;
    let attemptedRevision = -1;
    let attemptedGeneration = -1;

    try {
      while (
        this.ready
        && this.latestSurface
        && this.deliveredRevision < this.latestRevision
      ) {
        const surface = this.latestSurface;
        attemptedRevision = this.latestRevision;
        attemptedGeneration = this.processGeneration;

        try {
          await this.send(surface);
        } catch (error) {
          this.onError(error);
          break;
        }

        if (this.ready && attemptedGeneration === this.processGeneration) {
          this.deliveredRevision = attemptedRevision;
        }
      }
    } finally {
      this.inFlight = false;

      // Retry only when something changed during the failed/in-flight send.
      // A stable failure waits for the next surface update instead of spinning.
      if (
        this.ready
        && this.latestSurface
        && this.deliveredRevision < this.latestRevision
        && (
          attemptedRevision !== this.latestRevision
          || attemptedGeneration !== this.processGeneration
        )
      ) {
        void this.flush();
      }
    }
  }
}
