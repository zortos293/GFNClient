export class RecordingChunkQueue {
  private tail: Promise<void> = Promise.resolve();
  private firstError: unknown = null;

  constructor(
    private readonly send: (buffer: ArrayBuffer) => Promise<void>,
  ) {}

  enqueue(blob: Blob): void {
    this.tail = this.tail
      .then(async () => {
        const buffer = await blob.arrayBuffer();
        await this.send(buffer);
      })
      .catch((error: unknown) => {
        this.firstError ??= error;
      });
  }

  async flush(): Promise<void> {
    await this.tail;
    if (this.firstError) {
      throw this.firstError;
    }
  }
}

export async function finishRecordingAfterQueuedChunks<T>(
  queue: RecordingChunkQueue,
  finish: () => Promise<T>,
): Promise<T> {
  await queue.flush();
  return finish();
}
