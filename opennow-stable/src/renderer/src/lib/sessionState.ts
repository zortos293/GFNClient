import {
  isGfnSessionInQueue,
  isSessionReadyForConnectStatus,
  type GameInfo,
  type SessionInfo,
} from "@shared/gfn";

import type { LaunchErrorState, StreamLoadingStatus, StreamStatus } from "./appTypes";

type TranslateFunction = typeof import("../i18n").t;

const GFN_SESSION_LIMIT_EXCEEDED_CODE = 3237093643;
const GFN_INSUFFICIENT_PLAYABILITY_CODE = 3237093718;
const GFN_USER_STORAGE_NOT_AVAILABLE_CODE = 3237093721;
const GFN_STORAGE_NOT_AVAILABLE_CODE = 3237093722;

export function isSessionReadyForConnect(status: number): boolean {
  return isSessionReadyForConnectStatus(status);
}

export function isSessionInQueue(session: SessionInfo): boolean {
  return isGfnSessionInQueue(session);
}

export function isSessionLimitError(error: unknown): boolean {
  if (error && typeof error === "object" && "gfnErrorCode" in error) {
    const candidate = error.gfnErrorCode;
    if (typeof candidate === "number") {
      return candidate === GFN_SESSION_LIMIT_EXCEEDED_CODE;
    }
  }
  if (error instanceof Error) {
    const msg = error.message.toUpperCase();
    return msg.includes("SESSION LIMIT") || msg.includes("DUPLICATE SESSION");
  }
  return false;
}

export function isInsufficientPlayabilityError(error: unknown): boolean {
  if (error && typeof error === "object" && "gfnErrorCode" in error) {
    const candidate = error.gfnErrorCode;
    if (typeof candidate === "number") {
      return candidate === GFN_INSUFFICIENT_PLAYABILITY_CODE;
    }
  }
  if (error instanceof Error) {
    return error.message.toUpperCase().includes("INSUFFICIENT_PLAYABILITY");
  }
  return false;
}

export function isPersistentStorageUnavailableError(error: unknown): boolean {
  if (error && typeof error === "object") {
    if ("gfnErrorCode" in error) {
      const candidate = error.gfnErrorCode;
      if (candidate === GFN_USER_STORAGE_NOT_AVAILABLE_CODE || candidate === GFN_STORAGE_NOT_AVAILABLE_CODE) {
        return true;
      }
    }
    if ("statusCode" in error) {
      const statusCode = error.statusCode;
      if (statusCode === 89 || statusCode === 90) {
        return true;
      }
    }
  }
  if (error instanceof Error) {
    const msg = error.message.toUpperCase();
    return msg.includes("USER_STORAGE_NOT_AVAILABLE") || msg.includes("GFN_STORAGE_NOT_AVAILABLE");
  }
  return false;
}

export function toLoadingStatus(status: StreamStatus): StreamLoadingStatus {
  switch (status) {
    case "queue":
    case "setup":
    case "starting":
    case "connecting":
      return status;
    default:
      return "queue";
  }
}

export function isStreamVideoReady(
  status: StreamStatus,
  diagnosticsReady: boolean,
  videoElementHasFrame: boolean,
): boolean {
  return status === "streaming" && (diagnosticsReady || videoElementHasFrame);
}

export function toCodeLabel(code: number | undefined): string | undefined {
  if (code === undefined) return undefined;
  if (code === GFN_SESSION_LIMIT_EXCEEDED_CODE) return `SessionLimitExceeded (${code})`;
  if (code === GFN_INSUFFICIENT_PLAYABILITY_CODE) return `SessionInsufficientPlayabilityLevel (${code})`;
  if (code === GFN_USER_STORAGE_NOT_AVAILABLE_CODE) return `UserStorageNotAvailable (${code})`;
  if (code === GFN_STORAGE_NOT_AVAILABLE_CODE) return `GfnStorageNotAvailable (${code})`;
  return `GFN Error ${code}`;
}

export function extractLaunchErrorCode(error: unknown): number | undefined {
  if (error && typeof error === "object") {
    if ("gfnErrorCode" in error) {
      const directCode = error.gfnErrorCode;
      if (typeof directCode === "number") return directCode;
    }
    if ("statusCode" in error) {
      const statusCode = error.statusCode;
      if (typeof statusCode === "number" && statusCode > 0 && statusCode < 255) {
        return 3237093632 + statusCode;
      }
    }
  }
  if (error instanceof Error) {
    const match = error.message.match(/\b(3237\d{6,})\b/);
    if (match) {
      const code = Number(match[1]);
      if (Number.isFinite(code)) return code;
    }
  }
  return undefined;
}

function firstText(value: string | string[] | undefined): string {
  if (typeof value === "string") return value.trim();
  if (Array.isArray(value)) {
    return value.find((entry) => typeof entry === "string" && entry.trim().length > 0)?.trim() ?? "";
  }
  return "";
}

function formatCatalogSkuString(template: string, sku: string): string {
  return template.replace(/\{\{\s*SKU\s*\}\}|\{\s*SKU\s*\}/g, sku).trim();
}

function toInsufficientPlayabilityState(
  t: TranslateFunction,
  stage: StreamLoadingStatus,
  code: number | undefined,
  game?: Pick<GameInfo, "title" | "membershipTierLabel" | "catalogSkuStrings"> | null,
): LaunchErrorState {
  const catalogHeader = firstText(game?.catalogSkuStrings?.SKU_BASED_UNPLAYABLE_DIALOG_HEADER);
  const catalogBody = firstText(game?.catalogSkuStrings?.SKU_BASED_UNPLAYABLE_DIALOG_BODY_UPGRADE);
  const tier = game?.membershipTierLabel?.trim();
  const title = catalogHeader || t("errors.insufficientPlayabilityTitle");
  const description = catalogBody
    ? formatCatalogSkuString(catalogBody, catalogHeader || tier || t("errors.insufficientPlayabilityTitle"))
    : tier
      ? t("errors.insufficientPlayabilityTierDescription", { tier })
      : t("errors.insufficientPlayabilityDescription");

  return {
    stage,
    title,
    description,
    codeLabel: toCodeLabel(code),
  };
}

export function toLaunchErrorState(
  t: TranslateFunction,
  error: unknown,
  stage: StreamLoadingStatus,
  game?: Pick<GameInfo, "title" | "membershipTierLabel" | "catalogSkuStrings"> | null,
): LaunchErrorState {
  const unknownMessage = t("errors.launchUnknown");

  const titleFromError =
    error && typeof error === "object" && "title" in error && typeof error.title === "string"
      ? error.title.trim()
      : "";
  const descriptionFromError =
    error && typeof error === "object" && "description" in error && typeof error.description === "string"
      ? error.description.trim()
      : "";
  const statusDescription =
    error && typeof error === "object" && "statusDescription" in error && typeof error.statusDescription === "string"
      ? error.statusDescription.trim()
      : "";
  const messageFromError = error instanceof Error ? error.message.trim() : "";
  const combined = `${statusDescription} ${messageFromError}`.toUpperCase();
  const code = extractLaunchErrorCode(error);

  if (isInsufficientPlayabilityError(error) || combined.includes("INSUFFICIENT_PLAYABILITY")) {
    return toInsufficientPlayabilityState(t, stage, code, game);
  }

  if (
    isPersistentStorageUnavailableError(error) ||
    combined.includes("USER_STORAGE_NOT_AVAILABLE") ||
    combined.includes("GFN_STORAGE_NOT_AVAILABLE")
  ) {
    return {
      stage,
      title: t("errors.userStorageUnavailableTitle"),
      description: t("errors.userStorageUnavailableDescription"),
      codeLabel: toCodeLabel(code),
      action: "persistent-storage-settings",
      actionLabel: t("errors.userStorageUnavailableAction"),
    };
  }

  if (
    isSessionLimitError(error) ||
    combined.includes("SESSION_LIMIT") ||
    combined.includes("DUPLICATE SESSION")
  ) {
    return {
      stage,
      title: t("errors.duplicateSessionTitle"),
      description: t("errors.duplicateSessionDescription"),
      codeLabel: toCodeLabel(code),
    };
  }

  return {
    stage,
    title: titleFromError || t("errors.launchFailedTitle"),
    description: descriptionFromError || messageFromError || statusDescription || unknownMessage,
    codeLabel: toCodeLabel(code),
  };
}

export function streamStatusToLoadingStage(status: StreamStatus): StreamLoadingStatus {
  if (status === "queue" || status === "setup" || status === "starting" || status === "connecting") {
    return status;
  }
  return "connecting";
}
