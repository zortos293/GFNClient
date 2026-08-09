import { useCallback, useEffect, useRef, useState } from "react";
import type { RefObject } from "react";
import {
  normalizeRecordingBitrateMbps,
  type RecordingEntry,
  type RecordingFps,
  type RecordingResolution,
} from "@shared/gfn";
import { fitThumbnailSize, selectRecordingMimeType } from "../components/stream/streamRuntimeHelpers";
import {
  createRecordingVideoCapture,
  type RecordingVideoCapture,
} from "../lib/recordingCapture";
import { RecordingChunkQueue } from "../lib/recordingChunkQueue";

export type RecordingStatus = "idle" | "starting" | "recording" | "stopping" | "error";

interface UseStreamRecorderOptions {
  videoRef: RefObject<HTMLVideoElement | null>;
  audioRef: RefObject<HTMLAudioElement | null>;
  gameTitle: string;
  micTrack: MediaStreamTrack | null;
  recordingBitrateMbps: number | null;
  recordingResolution: RecordingResolution;
  recordingFps: RecordingFps;
}

export function useStreamRecorder({
  videoRef,
  audioRef,
  gameTitle,
  micTrack,
  recordingBitrateMbps,
  recordingResolution,
  recordingFps,
}: UseStreamRecorderOptions) {
  const [recordingStatus, setRecordingStatus] = useState<RecordingStatus>("idle");
  const [recordings, setRecordings] = useState<RecordingEntry[]>([]);
  const [recordingDurationMs, setRecordingDurationMs] = useState(0);
  const [recordingError, setRecordingError] = useState<string | null>(null);
  const [usedMimeType, setUsedMimeType] = useState<string | null>(null);
  const mountedRef = useRef(true);
  const recordingStatusRef = useRef<RecordingStatus>("idle");
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const recordingIdRef = useRef<string | null>(null);
  const recordingStartTimeRef = useRef(0);
  const recordingTimerRef = useRef<number | undefined>(undefined);
  const recordingCaptureRef = useRef<RecordingVideoCapture | null>(null);
  const ownedAudioTracksRef = useRef<MediaStreamTrack[]>([]);
  const chunkQueueRef = useRef<RecordingChunkQueue | null>(null);
  const thumbnailDataUrlRef = useRef<string | null>(null);
  const recCarouselRef = useRef<HTMLDivElement | null>(null);
  const audioCtxRef = useRef<AudioContext | null>(null);
  const recordingApiAvailable =
    typeof window.openNow?.beginRecording === "function" &&
    typeof window.openNow?.sendRecordingChunk === "function" &&
    typeof window.openNow?.finishRecording === "function" &&
    typeof window.openNow?.abortRecording === "function" &&
    typeof window.openNow?.listRecordings === "function" &&
    typeof window.openNow?.deleteRecording === "function";

  const updateRecordingStatus = useCallback((status: RecordingStatus): void => {
    recordingStatusRef.current = status;
    if (mountedRef.current) {
      setRecordingStatus(status);
    }
  }, []);

  const cleanupRecordingResources = useCallback((): void => {
    window.clearInterval(recordingTimerRef.current);
    recordingTimerRef.current = undefined;
    recordingCaptureRef.current?.dispose();
    recordingCaptureRef.current = null;
    ownedAudioTracksRef.current.forEach((track) => {
      if (track.readyState !== "ended") {
        track.stop();
      }
    });
    ownedAudioTracksRef.current = [];
    audioCtxRef.current?.close().catch(() => undefined);
    audioCtxRef.current = null;
  }, []);

  const refreshRecordings = useCallback(async () => {
    setRecordingError(null);
    if (!recordingApiAvailable) return;
    try {
      const items = await window.openNow.listRecordings();
      setRecordings(items);
    } catch (error) {
      console.error("[StreamView] Failed to load recordings:", error);
      setRecordingError("Unable to load recordings.");
    }
  }, [recordingApiAvailable]);

  const deleteRecording = useCallback(async (id: string) => {
    setRecordingError(null);
    if (!recordingApiAvailable) return;
    try {
      await window.openNow.deleteRecording({ id });
      setRecordings((prev) => prev.filter((recording) => recording.id !== id));
    } catch (error) {
      console.error("[StreamView] Failed to delete recording:", error);
      setRecordingError("Unable to delete recording.");
    }
  }, [recordingApiAvailable]);

  const scrollRecordings = useCallback((direction: "left" | "right") => {
    const strip = recCarouselRef.current;
    if (!strip) return;
    strip.scrollBy({ left: direction === "left" ? -200 : 200, behavior: "smooth" });
  }, []);

  const toggleRecording = useCallback(async () => {
    const currentStatus = recordingStatusRef.current;
    if (currentStatus === "starting" || currentStatus === "stopping") {
      return;
    }

    if (currentStatus === "recording") {
      const recorder = mediaRecorderRef.current;
      if (!recorder || recorder.state === "inactive") {
        if (recorder) {
          recorder.ondataavailable = null;
          recorder.onstop = null;
          recorder.onerror = null;
        }
        mediaRecorderRef.current = null;
        const id = recordingIdRef.current;
        recordingIdRef.current = null;
        chunkQueueRef.current = null;
        cleanupRecordingResources();
        if (id) {
          await window.openNow.abortRecording({ recordingId: id }).catch(() => undefined);
        }
        updateRecordingStatus("error");
        setRecordingError("Recording stopped unexpectedly.");
        return;
      }
      updateRecordingStatus("stopping");
      try {
        recorder.stop();
      } catch (error) {
        console.error("[StreamView] Failed to stop recording:", error);
        recorder.onerror?.(new ErrorEvent("error", { error }));
      }
      return;
    }

    setRecordingError(null);
    updateRecordingStatus("starting");

    if (!recordingApiAvailable) {
      setRecordingError("Recording API unavailable. Restart OpenNOW to enable recording.");
      updateRecordingStatus("error");
      return;
    }

    const video = videoRef.current;
    if (!video || !(video.srcObject instanceof MediaStream)) {
      setRecordingError("Stream is not ready for recording yet.");
      updateRecordingStatus("error");
      return;
    }

    let recorder: MediaRecorder | null = null;
    let recordingId: string | null = null;
    try {
      const mimeType = selectRecordingMimeType((candidate) => MediaRecorder.isTypeSupported(candidate));
      const audioCtx = new AudioContext();
      audioCtxRef.current = audioCtx;
      const audioDest = audioCtx.createMediaStreamDestination();
      ownedAudioTracksRef.current = audioDest.stream.getAudioTracks();

      const audioElement = audioRef.current;
      const gameAudioStream = audioElement?.srcObject instanceof MediaStream ? audioElement.srcObject : null;
      if (gameAudioStream && gameAudioStream.getAudioTracks().length > 0) {
        audioCtx.createMediaStreamSource(gameAudioStream).connect(audioDest);
      }

      if (micTrack && micTrack.readyState === "live") {
        const micStream = new MediaStream([micTrack]);
        audioCtx.createMediaStreamSource(micStream).connect(audioDest);
      }

      const capture = createRecordingVideoCapture(video, recordingResolution, recordingFps);
      if (!capture) {
        throw new Error("Recording canvas could not be initialized");
      }
      recordingCaptureRef.current = capture;

      const composed = new MediaStream([
        capture.track,
        ...audioDest.stream.getAudioTracks(),
      ]);
      const recorderOptions: MediaRecorderOptions = { mimeType };
      const normalizedBitrate = normalizeRecordingBitrateMbps(recordingBitrateMbps);
      if (normalizedBitrate !== null) {
        recorderOptions.videoBitsPerSecond = normalizedBitrate * 1_000_000;
      }
      recorder = new MediaRecorder(composed, recorderOptions);

      const result = await window.openNow.beginRecording({ mimeType });
      recordingId = result.recordingId;
      if (!mountedRef.current) {
        await window.openNow.abortRecording({ recordingId });
        cleanupRecordingResources();
        return;
      }

      let terminalEventHandled = false;
      recordingIdRef.current = recordingId;
      thumbnailDataUrlRef.current = null;
      chunkQueueRef.current = new RecordingChunkQueue(async (buffer) => {
        await window.openNow.sendRecordingChunk({ recordingId: recordingId!, chunk: buffer });
      });

      const settleRecording = async (save: boolean, errorMessage?: string): Promise<void> => {
        if (terminalEventHandled) return;
        terminalEventHandled = true;
        recorder!.ondataavailable = null;
        recorder!.onstop = null;
        recorder!.onerror = null;
        if (!save && recorder!.state !== "inactive") {
          try {
            recorder!.stop();
          } catch {}
        }
        if (mediaRecorderRef.current === recorder) {
          mediaRecorderRef.current = null;
        }
        recordingIdRef.current = null;
        cleanupRecordingResources();

        if (!save) {
          chunkQueueRef.current = null;
          thumbnailDataUrlRef.current = null;
          await window.openNow.abortRecording({ recordingId: recordingId! }).catch(() => undefined);
          if (mountedRef.current) {
            setRecordingError(errorMessage ?? "Recording encountered an error.");
            updateRecordingStatus("error");
          }
          return;
        }

        try {
          await chunkQueueRef.current?.flush();
          const entry = await window.openNow.finishRecording({
            recordingId: recordingId!,
            durationMs: Date.now() - recordingStartTimeRef.current,
            gameTitle,
            thumbnailDataUrl: thumbnailDataUrlRef.current ?? undefined,
          });
          chunkQueueRef.current = null;
          thumbnailDataUrlRef.current = null;
          if (mountedRef.current) {
            setRecordings((prev) => [entry, ...prev].slice(0, 20));
            setRecordingDurationMs(0);
            updateRecordingStatus("idle");
          }
        } catch (error) {
          console.error("[StreamView] Failed to finish recording:", error);
          chunkQueueRef.current = null;
          thumbnailDataUrlRef.current = null;
          await window.openNow.abortRecording({ recordingId: recordingId! }).catch(() => undefined);
          if (mountedRef.current) {
            setRecordingError("Recording could not be saved.");
            updateRecordingStatus("error");
          }
        }
      };

      let isFirstChunk = true;
      recorder.ondataavailable = (event: BlobEvent) => {
        if (!event.data || event.data.size === 0) return;

        if (isFirstChunk) {
          isFirstChunk = false;
          const currentVideo = videoRef.current;
          if (currentVideo && currentVideo.videoWidth > 0 && currentVideo.videoHeight > 0) {
            const { width, height } = fitThumbnailSize(
              currentVideo.videoWidth,
              currentVideo.videoHeight,
            );
            const canvas = document.createElement("canvas");
            canvas.width = width;
            canvas.height = height;
            const context = canvas.getContext("2d");
            if (context) {
              context.drawImage(currentVideo, 0, 0, width, height);
              thumbnailDataUrlRef.current = canvas.toDataURL("image/jpeg", 0.72);
            }
          }
        }

        chunkQueueRef.current?.enqueue(event.data);
      };

      recorder.onstop = () => {
        updateRecordingStatus("stopping");
        void settleRecording(true);
      };
      recorder.onerror = () => {
        void settleRecording(false);
      };

      mediaRecorderRef.current = recorder;
      recordingStartTimeRef.current = Date.now();
      recorder.start(5000);
      setUsedMimeType(mimeType);
      setRecordingDurationMs(0);
      updateRecordingStatus("recording");
      recordingTimerRef.current = window.setInterval(() => {
        if (mountedRef.current) {
          setRecordingDurationMs(Date.now() - recordingStartTimeRef.current);
        }
      }, 500);
    } catch (error) {
      console.error("[StreamView] Failed to begin recording:", error);
      if (recorder) {
        recorder.ondataavailable = null;
        recorder.onstop = null;
        recorder.onerror = null;
        if (recorder.state !== "inactive") {
          try {
            recorder.stop();
          } catch {}
        }
      }
      mediaRecorderRef.current = null;
      recordingIdRef.current = null;
      chunkQueueRef.current = null;
      cleanupRecordingResources();
      if (recordingId) {
        await window.openNow.abortRecording({ recordingId }).catch(() => undefined);
      }
      if (mountedRef.current) {
        setRecordingError("Could not start recording.");
        updateRecordingStatus("error");
      }
    }
  }, [
    audioRef,
    cleanupRecordingResources,
    gameTitle,
    micTrack,
    recordingApiAvailable,
    recordingBitrateMbps,
    recordingFps,
    recordingResolution,
    updateRecordingStatus,
    videoRef,
  ]);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      const recorder = mediaRecorderRef.current;
      mediaRecorderRef.current = null;
      if (recorder) {
        recorder.ondataavailable = null;
        recorder.onstop = null;
        recorder.onerror = null;
        if (recorder.state !== "inactive") {
          try {
            recorder.stop();
          } catch {}
        }
      }
      const id = recordingIdRef.current;
      recordingIdRef.current = null;
      chunkQueueRef.current = null;
      cleanupRecordingResources();
      if (id) {
        window.openNow.abortRecording({ recordingId: id }).catch(() => undefined);
      }
    };
  }, [cleanupRecordingResources]);

  return {
    isRecording: recordingStatus === "recording" || recordingStatus === "stopping",
    recordingStatus,
    recordings,
    recordingDurationMs,
    recordingError,
    usedMimeType,
    recCarouselRef,
    recordingApiAvailable,
    refreshRecordings,
    deleteRecording,
    scrollRecordings,
    toggleRecording,
  };
}
