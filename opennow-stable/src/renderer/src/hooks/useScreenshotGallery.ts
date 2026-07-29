import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { RefObject } from "react";
import type { ScreenshotEntry } from "@shared/gfn";

interface UseScreenshotGalleryOptions {
  videoRef: RefObject<HTMLVideoElement | null>;
  gameTitle: string;
}

export function useScreenshotGallery({
  videoRef,
  gameTitle,
}: UseScreenshotGalleryOptions) {
  const [screenshots, setScreenshots] = useState<ScreenshotEntry[]>([]);
  const [isSavingScreenshot, setIsSavingScreenshot] = useState(false);
  const [galleryError, setGalleryError] = useState<string | null>(null);
  const [selectedScreenshotId, setSelectedScreenshotId] = useState<string | null>(null);
  const galleryStripRef = useRef<HTMLDivElement | null>(null);
  const screenshotApiAvailable =
    typeof window.openNow?.saveScreenshot === "function" &&
    typeof window.openNow?.listScreenshots === "function" &&
    typeof window.openNow?.deleteScreenshot === "function" &&
    typeof window.openNow?.saveScreenshotAs === "function";

  const selectedScreenshot = useMemo(() => {
    if (!selectedScreenshotId) return null;
    return screenshots.find((item) => item.id === selectedScreenshotId) ?? null;
  }, [screenshots, selectedScreenshotId]);

  const refreshScreenshots = useCallback(async () => {
    setGalleryError(null);
    if (!screenshotApiAvailable) {
      setGalleryError("Screenshot API unavailable. Restart OpenNOW to enable gallery.");
      return;
    }
    try {
      const items = await window.openNow.listScreenshots();
      setScreenshots(items);
    } catch (error) {
      console.error("[StreamView] Failed to load screenshots:", error);
      setGalleryError("Unable to load screenshot gallery.");
    }
  }, [screenshotApiAvailable]);

  const captureScreenshot = useCallback(async () => {
    setGalleryError(null);
    if (!screenshotApiAvailable) {
      setGalleryError("Screenshot API unavailable. Restart OpenNOW to enable capture.");
      return;
    }
    if (isSavingScreenshot) {
      return;
    }

    const video = videoRef.current;
    if (!video || video.videoWidth <= 0 || video.videoHeight <= 0) {
      setGalleryError("Stream is not ready for screenshots yet.");
      return;
    }

    setIsSavingScreenshot(true);
    try {
      const canvas = document.createElement("canvas");
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      const context = canvas.getContext("2d");
      if (!context) {
        throw new Error("Could not acquire 2D context");
      }

      context.drawImage(video, 0, 0, canvas.width, canvas.height);
      const dataUrl = canvas.toDataURL("image/png");
      const saved = await window.openNow.saveScreenshot({ dataUrl, gameTitle });
      setScreenshots((prev) => [saved, ...prev.filter((item) => item.id !== saved.id)].slice(0, 60));
    } catch (error) {
      console.error("[StreamView] Failed to capture screenshot:", error);
      setGalleryError("Screenshot failed. Try again.");
    } finally {
      setIsSavingScreenshot(false);
    }
  }, [gameTitle, isSavingScreenshot, screenshotApiAvailable, videoRef]);

  const scrollGallery = useCallback((direction: "left" | "right") => {
    const strip = galleryStripRef.current;
    if (!strip) return;
    const delta = Math.max(180, Math.round(strip.clientWidth * 0.7));
    strip.scrollBy({ left: direction === "left" ? -delta : delta, behavior: "smooth" });
  }, []);

  const deleteSelectedScreenshot = useCallback(async () => {
    setGalleryError(null);
    if (!screenshotApiAvailable) {
      setGalleryError("Screenshot API unavailable. Restart OpenNOW to enable gallery.");
      return;
    }
    if (!selectedScreenshot) return;

    try {
      await window.openNow.deleteScreenshot({ id: selectedScreenshot.id });
      setScreenshots((prev) => prev.filter((item) => item.id !== selectedScreenshot.id));
      setSelectedScreenshotId(null);
    } catch (error) {
      console.error("[StreamView] Failed to delete screenshot:", error);
      setGalleryError("Unable to delete screenshot.");
    }
  }, [screenshotApiAvailable, selectedScreenshot]);

  const saveSelectedScreenshotAs = useCallback(async () => {
    setGalleryError(null);
    if (!screenshotApiAvailable) {
      setGalleryError("Screenshot API unavailable. Restart OpenNOW to enable gallery.");
      return;
    }
    if (!selectedScreenshot) return;

    try {
      await window.openNow.saveScreenshotAs({ id: selectedScreenshot.id });
    } catch (error) {
      console.error("[StreamView] Failed to save screenshot as:", error);
      setGalleryError("Unable to save screenshot.");
    }
  }, [screenshotApiAvailable, selectedScreenshot]);

  useEffect(() => {
    if (!selectedScreenshotId) return;
    if (!screenshots.some((item) => item.id === selectedScreenshotId)) {
      setSelectedScreenshotId(null);
    }
  }, [screenshots, selectedScreenshotId]);

  return {
    screenshots,
    isSavingScreenshot,
    galleryError,
    selectedScreenshot,
    selectedScreenshotId,
    setSelectedScreenshotId,
    galleryStripRef,
    screenshotApiAvailable,
    refreshScreenshots,
    captureScreenshot,
    scrollGallery,
    deleteSelectedScreenshot,
    saveSelectedScreenshotAs,
  };
}
