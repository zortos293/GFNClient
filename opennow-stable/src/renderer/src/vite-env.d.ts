/// <reference types="vite/client" />
/// <reference types="@webgpu/types" />

import type { OpenNowApi } from "@shared/gfn";

declare global {
  interface Window {
    openNow: OpenNowApi;
  }
}

export {};
