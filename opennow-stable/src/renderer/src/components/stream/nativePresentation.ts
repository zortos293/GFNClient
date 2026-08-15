export interface NativePresentationState {
  nativeRendererActive: boolean;
  nativeStreamingEnabled: boolean;
  connecting: boolean;
  externalRenderer: boolean;
}

export function usesNativeInternalSurface(state: NativePresentationState): boolean {
  return !state.externalRenderer
    && (
      state.nativeRendererActive
      || (state.nativeStreamingEnabled && state.connecting)
    );
}
