import { useCallback, useEffect, useRef, useState, type RefObject } from "react";
import { ZORTOS_GITHUB_SPONSORS_URL } from "@shared/communityProxy";
import { t } from "../../../i18n";
import { extractRemoteInvokeErrorMessage } from "../settingsFormatters";
import type { SettingsChangeHandler } from "./streamSettingsTypes";

interface UseCommunityProxyProvisioningOptions {
  handleChange: SettingsChangeHandler;
  onBlockingOverlayChange?: (blocking: boolean) => void;
}

interface CommunityProxyProvisioningState {
  closePrompt: () => void;
  confirmPrompt: () => Promise<void>;
  continueRef: RefObject<HTMLButtonElement | null>;
  error: string | null;
  handlePromptExit: () => void;
  openPrompt: () => void;
  openSponsors: () => void;
  promptOpen: boolean;
  provisioning: boolean;
}

export function useCommunityProxyProvisioning({
  handleChange,
  onBlockingOverlayChange,
}: UseCommunityProxyProvisioningOptions): CommunityProxyProvisioningState {
  const [promptOpen, setPromptOpen] = useState(false);
  const [provisioning, setProvisioning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const continueRef = useRef<HTMLButtonElement | null>(null);

  useEffect(() => {
    if (promptOpen) {
      onBlockingOverlayChange?.(true);
    }
  }, [onBlockingOverlayChange, promptOpen]);

  useEffect(() => () => onBlockingOverlayChange?.(false), [onBlockingOverlayChange]);

  const openPrompt = useCallback((): void => {
    setError(null);
    setProvisioning(false);
    setPromptOpen(true);
  }, []);

  const closePrompt = useCallback((): void => {
    if (provisioning) return;
    setPromptOpen(false);
  }, [provisioning]);

  const handlePromptExit = useCallback((): void => {
    setError(null);
    onBlockingOverlayChange?.(false);
  }, [onBlockingOverlayChange]);

  const openSponsors = useCallback((): void => {
    void window.openNow.openExternalUrl(ZORTOS_GITHUB_SPONSORS_URL).catch((openError) => {
      console.error("[Settings] Failed to open GitHub Sponsors:", openError);
    });
  }, []);

  const confirmPrompt = useCallback(async (): Promise<void> => {
    if (provisioning) return;

    setProvisioning(true);
    setError(null);
    try {
      const result = await window.openNow.provisionZortosCommunityProxy();
      handleChange("sessionProxyUrl", result.proxyUrl);
      handleChange("sessionProxyEnabled", true);
      setProvisioning(false);
      closePrompt();
    } catch (provisionError) {
      console.error("[Settings] Failed to provision Zortos community proxy:", provisionError);
      setError(extractRemoteInvokeErrorMessage(
        provisionError,
        t("settings.video.zortosCommunityProxy.provisionFailed"),
      ));
    } finally {
      setProvisioning(false);
    }
  }, [closePrompt, handleChange, provisioning]);

  return {
    closePrompt,
    confirmPrompt,
    continueRef,
    error,
    handlePromptExit,
    openPrompt,
    openSponsors,
    promptOpen,
    provisioning,
  };
}

