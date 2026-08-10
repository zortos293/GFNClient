import type { RenderProcessGoneDetails } from "electron";

export function shouldReportRendererTermination(
  _reason: RenderProcessGoneDetails["reason"],
  appShutdownRequested: boolean,
): boolean {
  return !appShutdownRequested;
}
