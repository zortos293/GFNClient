import { useCallback, useEffect, useRef, useState } from "react";
import type { RefObject } from "react";
import type { RecordingEntry } from "@shared/gfn";
import { fitThumbnailSize, selectRecordingMimeType } from "../components/stream/streamRuntimeHelpers";

interface UseStreamRecorderOptions {
  videoRef: RefObject<HTMLVideoElement | null>;
  audioRef: RefObject<HTMLAudioElement | null>;
  gameTitle: string;
  micTrack: MediaStreamTrack | null;
  recordingBitrateMbps: number | null;
}

export function useStreamRecorder({
  videoRef,
  audioRef,
  gameTitle,
  micTrack,
  recordingBitrateMbps,
}: UseStreamRecorderOptions) {
  const [isRecording, setIsRecording] = useState(false);
  const [recordings, setRecordings] = useState<RecordingEntry[]>([]);
  const [recordingDurationMs, setRecordingDurationMs] = useState(0);
  const [recordingError, setRecordingError] = useState<string | null>(null);
  const [usedMimeType, setUsedMimeType] = useState<string | null>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const recordingIdRef = useRef<string | null>(null);
  const recordingStartTimeRef = useRef(0);
  const recordingTimerRef = useRef<number | undefined>(undefined);
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
    setRecordingError(null);

    if (isRecording) {
      mediaRecorderRef.current?.stop();
      return;
    }

    if (!recordingApiAvailable) {
      setRecordingError("Recording API unavailable. Restart OpenNOW to enable recording.");
      return;
    }

    const video = videoRef.current;
    if (!video || !video.srcObject) {
      setRecordingError("Stream is not ready for recording yet.");
      return;
    }

    const stream = video.srcObject as MediaStream;
    const mimeType = selectRecordingMimeType((candidate) => MediaRecorder.isTypeSupported(candidate));
    setUsedMimeType(mimeType);

    const audioCtx = new AudioContext();
    audioCtxRef.current = audioCtx;
    const audioDest = audioCtx.createMediaStreamDestination();

    const audioElement = audioRef.current;
    const gameAudioStream = audioElement?.srcObject instanceof MediaStream ? audioElement.srcObject : null;
    if (gameAudioStream && gameAudioStream.getAudioTracks().length > 0) {
      audioCtx.createMediaStreamSource(gameAudioStream).connect(audioDest);
    }

    if (micTrack && micTrack.readyState === "live") {
      const micStream = new MediaStream([micTrack]);
      audioCtx.createMediaStreamSource(micStream).connect(audioDest);
    }

    const composed = new MediaStream([
      ...stream.getVideoTracks(),
      ...audioDest.stream.getAudioTracks(),
    ]);

    let recordingId: string;
    try {
      const result = await window.openNow.beginRecording({ mimeType });
      recordingId = result.recordingId;
    } catch (error) {
      console.error("[StreamView] Failed to begin recording:", error);
      audioCtx.close().catch(() => undefined);
      audioCtxRef.current = null;
      setRecordingError("Could not start recording.");
      return;
    }

    recordingIdRef.current = recordingId;
    thumbnailDataUrlRef.current = null;
    recordingStartTimeRef.current = Date.now();
    setRecordingDurationMs(0);
    setIsRecording(true);

    recordingTimerRef.current = window.setInterval(() => {
      setRecordingDurationMs(Date.now() - recordingStartTimeRef.current);
    }, 500);

    let isFirstChunk = true;
    const recorderOptions: MediaRecorderOptions = { mimeType };
    if (recordingBitrateMbps !== null) {
      recorderOptions.videoBitsPerSecond =
        Math.max(1, Math.min(200, Math.round(recordingBitrateMbps))) * 1_000_000;
    }
    const recorder = new MediaRecorder(composed, recorderOptions);

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

      void event.data.arrayBuffer().then((buffer) => {
        const id = recordingIdRef.current;
        if (!id) return;
        window.openNow.sendRecordingChunk({ recordingId: id, chunk: buffer }).catch((error: unknown) => {
          console.error("[StreamView] Failed to send recording chunk:", error);
        });
      });
    };

    recorder.onstop = () => {
      window.clearInterval(recordingTimerRef.current);
      recordingTimerRef.current = undefined;
      audioCtxRef.current?.close().catch(() => undefined);
      audioCtxRef.current = null;
      const id = recordingIdRef.current;
      recordingIdRef.current = null;
      setIsRecording(false);

      if (!id) return;

      const durationMs = Date.now() - recordingStartTimeRef.current;
      void window.openNow
        .finishRecording({
          recordingId: id,
          durationMs,
          gameTitle,
          thumbnailDataUrl: thumbnailDataUrlRef.current ?? undefined,
        })
        .then((entry) => {
          setRecordings((prev) => [entry, ...prev].slice(0, 20));
          thumbnailDataUrlRef.current = null;
        })
        .catch((error: unknown) => {
          console.error("[StreamView] Failed to finish recording:", error);
          setRecordingError("Recording could not be saved.");
        });
    };

    recorder.onerror = () => {
      window.clearInterval(recordingTimerRef.current);
      recordingTimerRef.current = undefined;
      audioCtxRef.current?.close().catch(() => undefined);
      audioCtxRef.current = null;
      const id = recordingIdRef.current;
      recordingIdRef.current = null;
      setIsRecording(false);
      thumbnailDataUrlRef.current = null;
      if (id) {
        window.openNow.abortRecording({ recordingId: id }).catch(() => undefined);
      }
      setRecordingError("Recording encountered an error.");
    };

    mediaRecorderRef.current = recorder;
    recorder.start(2000);
  }, [
    audioRef,
    gameTitle,
    isRecording,
    micTrack,
    recordingApiAvailable,
    recordingBitrateMbps,
    videoRef,
  ]);

  useEffect(() => {
    return () => {
      window.clearInterval(recordingTimerRef.current);
      const recorder = mediaRecorderRef.current;
      const id = recordingIdRef.current;
      if (recorder && recorder.state !== "inactive") {
        recorder.stop();
      }
      if (id) {
        window.openNow.abortRecording({ recordingId: id }).catch(() => undefined);
        recordingIdRef.current = null;
      }
      audioCtxRef.current?.close().catch(() => undefined);
      audioCtxRef.current = null;
    };
  }, []);

  return {
    isRecording,
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
