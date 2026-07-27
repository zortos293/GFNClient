import type {
  NativeStreamerCommand,
  NativeStreamerEvent,
  NativeStreamerMessage,
  NativeStreamerResponse,
} from "@shared/nativeStreamer";

export type NativeStreamerCommandInput = NativeStreamerCommand extends infer T
  ? T extends NativeStreamerCommand
    ? Omit<T, "id">
    : never
  : never;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

export function isNativeStreamerResponse(
  message: NativeStreamerMessage,
): message is NativeStreamerResponse {
  return isRecord(message) && typeof (message as Record<string, unknown>)["id"] === "string";
}

export function isNativeStreamerEvent(
  message: NativeStreamerMessage,
): message is NativeStreamerEvent {
  return isRecord(message) && typeof (message as Record<string, unknown>)["id"] !== "string";
}
