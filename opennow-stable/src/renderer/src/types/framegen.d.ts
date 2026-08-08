declare module "framegen" {
  export interface RT {
    prepPair(previous: GPUTexture, current: GPUTexture): void;
    runT(time: number, output: GPUTexture): void;
    destroy(): void;
  }

  export interface CreateRTOptions {
    w: number;
    h: number;
    weightsBin: ArrayBuffer;
    weightsManifest: Record<string, { offset: number; shape: number[] }>;
    textureInput?: boolean;
    textureOutput?: boolean;
  }

  export function createRT(device: GPUDevice, options: CreateRTOptions): Promise<RT>;
}
