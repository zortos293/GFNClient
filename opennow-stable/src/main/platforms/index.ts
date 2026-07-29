/**
 * Main-process cloud platform registry.
 *
 * Today only GeForce NOW is wired. New providers should live under
 * `platforms/<id>/` and be registered here so IPC/session code can resolve
 * the active adapter without hard-coding GFN imports throughout the app.
 */

export { DEFAULT_CLOUD_PLATFORM_ID, type CloudPlatformId } from "@shared/platforms";

export * as gfn from "./gfn";
