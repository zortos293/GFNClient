export const GFN_CLIPBOARD_MESSAGE_TYPE = "PASTE";
export const GFN_CLIPBOARD_CLIENT_RECIPIENT = "UIPlugin";
export const GFN_CLIPBOARD_SERVER_RECIPIENT = "GFN_CLIENT_UI";

export const CLIPBOARD_CLIENT_ADDED_DATA = "CLIENT_ADDED_DATA";
export const CLIPBOARD_CLIENT_REMOVED_DATA = "CLIENT_REMOVED_DATA";
export const CLIPBOARD_CLIENT_DATA_RESPONSE = "CLIENT_DATA_RESPONSE";
export const CLIPBOARD_SERVER_DATA_REQUEST = "SERVER_DATA_REQUEST";

export type ClipboardPasteDataType =
  | typeof CLIPBOARD_CLIENT_ADDED_DATA
  | typeof CLIPBOARD_CLIENT_REMOVED_DATA
  | typeof CLIPBOARD_CLIENT_DATA_RESPONSE
  | typeof CLIPBOARD_SERVER_DATA_REQUEST;

export interface ClipboardTraceContextEntry {
  key: string;
  value: string;
}

export interface ClipboardTracingData {
  requestId?: string;
  traceId?: string;
  traceContext?: ClipboardTraceContextEntry[];
}

export interface ClipboardPastePayload {
  messageType: typeof GFN_CLIPBOARD_MESSAGE_TYPE;
  pasteData?: {
    type?: ClipboardPasteDataType;
    data?: string;
  };
  tracingData?: ClipboardTracingData;
}

export interface GfnCustomMessage {
  messageType: string;
  messageRecipient: string;
  data?: string;
}

export interface GfnControlCustomMessageEnvelope {
  customMessage: string;
}

const TRACE_ID_BYTES = 16;

export function clipboardUtf8Size(text: string): number {
  return new Blob([text]).size;
}

export function validateClipboardText(text: string, maxBytes: number): string | null {
  if (!text || maxBytes <= 0 || clipboardUtf8Size(text) > maxBytes) {
    return null;
  }
  return text;
}

export function createClipboardTraceId(): string {
  const bytes = new Uint8Array(TRACE_ID_BYTES);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
}

export function buildClipboardControlMessage(
  pasteType: ClipboardPasteDataType,
  options: {
    text?: string | null;
    tracingData?: ClipboardTracingData;
  } = {},
): GfnControlCustomMessageEnvelope {
  const pastePayload: ClipboardPastePayload = {
    messageType: GFN_CLIPBOARD_MESSAGE_TYPE,
    pasteData: { type: pasteType },
    tracingData: {
      traceId: options.tracingData?.traceId ?? createClipboardTraceId(),
      traceContext: options.tracingData?.traceContext ?? [],
      ...(options.tracingData?.requestId ? { requestId: options.tracingData.requestId } : {}),
    },
  };

  if (pasteType === CLIPBOARD_CLIENT_DATA_RESPONSE && options.text) {
    pastePayload.pasteData!.data = options.text;
  }

  const customMessage: GfnCustomMessage = {
    messageType: GFN_CLIPBOARD_MESSAGE_TYPE,
    messageRecipient: GFN_CLIPBOARD_CLIENT_RECIPIENT,
    data: JSON.stringify(pastePayload),
  };

  return { customMessage: JSON.stringify(customMessage) };
}

function parseJsonObject(value: unknown): Record<string, unknown> | null {
  if (typeof value !== "string") {
    return null;
  }
  try {
    const parsed = JSON.parse(value) as unknown;
    return parsed && typeof parsed === "object" && !Array.isArray(parsed)
      ? parsed as Record<string, unknown>
      : null;
  } catch {
    return null;
  }
}

export function parseClipboardControlMessage(value: unknown): ClipboardPastePayload | null {
  const outer = value && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;
  const customMessage = parseJsonObject(outer?.customMessage);
  if (!customMessage) {
    return null;
  }

  if (
    customMessage.messageType !== GFN_CLIPBOARD_MESSAGE_TYPE
    || customMessage.messageRecipient !== GFN_CLIPBOARD_SERVER_RECIPIENT
  ) {
    return null;
  }

  const payload = parseJsonObject(customMessage.data);
  if (!payload || payload.messageType !== GFN_CLIPBOARD_MESSAGE_TYPE) {
    return null;
  }

  return payload as unknown as ClipboardPastePayload;
}

export function isClipboardServerDataRequest(payload: ClipboardPastePayload | null): boolean {
  return payload?.pasteData?.type === CLIPBOARD_SERVER_DATA_REQUEST;
}
