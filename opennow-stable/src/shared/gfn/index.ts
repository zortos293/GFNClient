/**
 * GeForce NOW (GFN) shared contracts.
 *
 * Platform-specific DTOs, helpers, and the OpenNow IPC API surface used by
 * main, preload, and renderer. Prefer importing from focused submodules when
 * adding new code; `@shared/gfn` remains the stable barrel export.
 */
export * from "./stream";
export * from "./keyboard";
export * from "./nativeStreamer";
export * from "./videoShader";
export * from "./settings";
export * from "./entitlements";
export * from "./subscription";
export * from "./auth";
export * from "./thankYou";
export * from "./catalog";
export * from "./session";
export * from "./signaling";
export * from "./updater";
export * from "./api";
export * from "./media";
export * from "./printedWaste";
export * from "./endpoints";
