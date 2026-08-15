declare module "framegen" {
  export interface ConvTune {
    coc: number;
    slab: number;
    sg?: boolean;
    wgx?: number;
    wgy?: number;
    w4?: boolean;
    v2?: boolean;
    s2?: { coc: number; w4: boolean; ms?: number };
    ms?: number;
  }

  export interface CreateRTOptions {
    w: number;
    h: number;
    weightsBin: ArrayBuffer;
    weightsManifest: Record<string, { offset: number; shape: number[] }>;
    convTune?: ConvTune | null;
    textureInput?: boolean;
    textureOutput?: boolean;
    staticGuard?: boolean;
    sparseRefine?: boolean;
    refineThr?: number;
  }

  export interface RT {
    run(rgbaA: Uint8Array, rgbaB: Uint8Array, t?: number): Promise<Uint8Array>;
    runMulti(
      a: Uint8Array | GPUTexture,
      b: Uint8Array | GPUTexture,
      ts: number[],
      outTexs?: GPUTexture[],
    ): Promise<Uint8Array[] | null>;
    prepPair(a: GPUTexture, b: GPUTexture): void;
    runT(t: number, outTex: GPUTexture): void;
    profile(rgbaA: Uint8Array, rgbaB: Uint8Array): Promise<string>;
    profileT(
      a: GPUTexture,
      b: GPUTexture,
      t?: number,
      outTex?: GPUTexture | null,
    ): Promise<string>;
    destroy(): void;
    readonly w: number;
    readonly h: number;
  }

  export function createRT(device: GPUDevice, opts: CreateRTOptions): Promise<RT>;
  export function tuneConvRB(
    device: GPUDevice,
    shape: { ci: number; co: number; w16: number; h16: number; s2ci?: number },
  ): Promise<ConvTune>;
}
