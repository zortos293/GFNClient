# Framegen third-party notice

OpenNOW optionally uses [Framegen](https://github.com/MONZikWasTaken/Framegen)
for experimental client-side neural frame interpolation on the embedded WebRTC
stream path.

| Component | License |
| --- | --- |
| Runtime (`framegen` npm: JS + WGSL) | MIT |
| Model weights (`rt_v7s.*`) | Non-commercial research / personal use only — see [WEIGHTS_LICENSE.md](./WEIGHTS_LICENSE.md) |

Weights are copied into `src/renderer/public/framegen-weights/` at install time
by `scripts/copy-framegen-weights.mjs`. If the copy is missing at runtime, the
pipeline falls back to the pinned jsDelivr npm CDN mirror.
