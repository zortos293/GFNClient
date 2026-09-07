# HDR on Windows and Linux

HDR is an opt-in streaming mode in the Qt/native client. It is separate from the existing color-quality setting: a 10-bit SDR stream is still SDR. Enable HDR in the operating system first, then enable **HDR** in OpenNOW's stream settings before starting a new session. The remote game and GeForce NOW service must also allow HDR.

## Supported paths

| Platform | Decode and conversion | Window output |
| --- | --- | --- |
| Windows | Media Foundation hardware HEVC/AV1, P010, and a D3D11 video processor that supports PQ BT.2020 to PQ BT.2020 RGB10A2 conversion. | Linear extended sRGB (scRGB) is preferred; HDR10 is available when the window supports it. |
| Linux | An attached Vulkan Video device supporting the negotiated 10-bit profile, or FFmpeg VAAPI with a verified HEVC Main10/AV1 10-bit profile and supported P010 DMA-BUF import. | A Vulkan surface exposing scRGB or HDR10 through the compositor and driver. An HDR monitor alone does not establish surface support. |

H.264, software decoding, CUDA's CPU-download presentation path, and standalone native presenter windows do not support HDR. The application does not enable HDR on an SDR-only desktop or assume that X11 supports it. Linux DMA-BUF layouts requiring disjoint multi-object image import are rejected rather than copied to the CPU.

Frame generation is disabled for HDR sources because the current interpolation path is designed for SDR. Native frames retain their original cadence and HDR precision; enabling HDR does not insert SDR-generated frames into the stream.

HDR requests use 10-bit 4:2:0. Auto prefers HEVC and falls back to AV1 only when the runtime reports the required hardware support. Explicit incompatible codecs or unavailable output produce an actionable error before allocating a new session. An explicit server SDR response remains SDR; a saved HDR preference does not override the accepted session format.

## Color and ownership contract

CloudMatch receives `sdrHdrMode=1`, `trueHdr=true`, and requested-content luminance defaults of 1000 nits maximum, 400 nits frame average, and zero minimum. These are requested content characteristics, not measurements of the physical display. The accepted HDR mode is carried through resume, stream preparation, and NVST's dynamic-range and bit-depth settings.

Decoder metadata carries transfer function, primaries, matrix, range, and supported chroma location. Explicit metadata takes precedence over negotiated defaults; unspecified fields use those defaults. Unsupported combinations and precision-losing paths fail explicitly. Pixel depth alone never selects an HDR transfer function.

FFI ABI 6 publishes the texture format and encoded RGB color space separately. Windows publishes PQ BT.2020 in RGB10A2; Linux preserves PQ/HLG BT.2020 in RGBA16F. Qt performs the transfer-function and gamut conversion for the actual output. HDR/SDR changes retain per-frame metadata and bounded GPU resource ownership. Build and deploy the Qt shell and native streamer together; an older ABI is rejected.

The existing `StreamVideoItem`, stream transport, and input owner remain active under menus, statistics, and exit confirmation. Losing HDR output uses explicit SDR tone mapping instead of reinterpreting PQ values as SDR. No CPU video readback is introduced for HDR presentation.

SDR chrome is converted to the output's linear white level before blending. scRGB uses direct scene composition. HDR10-only outputs require one bounded, window-sized RGBA16F GPU composition target and a final PQ-encoding pass, so translucent chrome blends in linear light rather than averaging PQ code values. This adds GPU bandwidth only on the HDR10 output path; it does not create another window or decoder.

## Hardware acceptance

Automated shader and native tests cannot prove an HDR monitor's optical output. Test the packaged build, rather than mixing a new shell with an older native runtime.

1. Enable HDR in the OS and confirm that OpenNOW reports an available HDR output. Start an HDR-capable game with HDR enabled, Auto codec, and the normal native backend. Verify that in-game HDR controls become available and highlights extend beyond SDR white without lifting black levels or washing out colors.
2. Repeat with explicit HEVC and AV1 where the GPU supports each codec. Confirm that an H.264 or software override produces an error rather than a falsely labeled HDR stream. Disable HDR and confirm that ordinary 8-bit and 10-bit SDR sessions retain their previous appearance.
3. Test windowed and fullscreen modes, resizing, and DPI changes. Open and close F3 statistics, the stream menu, and exit confirmation. Check text brightness, translucent edges, black backgrounds, pointer confinement, gameplay key routing, and uninterrupted audio/video.
4. Move the active stream between HDR and SDR displays and toggle OS HDR. Verify deliberate SDR tone mapping on an SDR output, recovery when HDR returns, and no session restart. Repeat with overlays open.
5. End the session and start another with the opposite HDR setting, then exercise reconnect/resume. Verify that the new or accepted session's color mode wins over stale frame textures and saved preferences.
6. On Linux, test Vulkan Video and VAAPI separately when available. Check diagnostics for explicit import/decoder failures; audio continuing alone does not prove video health.

When reporting a failure, include the OS/compositor, GPU and driver, display model, HDR setting, codec/backend, window mode, and whether the failure occurred during startup, decoding, presentation, or a display transition. Do not share account tokens or session credentials.

## Reference

The implementation was informed by [OpenNOW-Mac at 88a09bd](https://github.com/OpenCloudGaming/OpenNOW-Mac/tree/88a09bd), particularly its CloudMatch HDR requests, decoded color attachments, and precision-preserving Metal output. Its native renderer was not copied into Qt. Qt's [HDR swapchain formats](https://doc.qt.io/qt-6.8/qrhiswapchain.html) and [HDR white-level contract](https://doc.qt.io/qt-6.8/qrhiswapchainhdrinfo.html) define the output conversions.
