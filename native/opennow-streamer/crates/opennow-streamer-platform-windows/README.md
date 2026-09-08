# OpenNOW Windows media backend

This isolated crate provides the Windows media end of the native streamer. It accepts complete
H.264, HEVC/H.265, or AV1 access units and interleaved `f32` PCM that has already been decoded from
Opus.

The backend uses only Windows system APIs:

- Media Foundation hardware MFTs for H.264/HEVC/AV1 decode into NV12 or P010. Hardware selection
  accepts only adapter-matched hardware transforms. A separate software mode accepts only
  synchronous/asynchronous software MFTs that are D3D11-aware and publish D3D11-backed output;
  the two modes are never silently mixed.
- A D3D11 video processor and a two-buffer flip-model DXGI swap chain for NV12 conversion, aspect-correct scaling, and presentation. The swap chain can target a caller-owned HWND, or the backend can create and own a child or top-level HWND.
- WASAPI shared-mode rendering for interleaved IEEE-float PCM. Windows performs endpoint format conversion when the active mix format differs.

`WindowsBackend::probe_for` creates a hidden presentation path using either D3D11 or a D3D12-backed
D3D11-on-12 device, independently probes adapter-matched hardware transforms and registered
software transforms for H.264, HEVC and AV1, and starts a 48 kHz stereo WASAPI client. A codec
capability is reported only when its selected decoder class and the complete presentation/audio
operation succeed. `WindowsBackend::probe` remains the D3D11 compatibility entry point.
Non-Windows builds expose the same typed API but report the backend as unavailable.

`CapabilityProbe.h265_hdr`, `av1_hdr`, and `supports_hdr(codec)` report HDR separately from
SDR codec support. HDR requires the probe device to support P010 hardware decode, an activatable
hardware MFT configured for actual P010/PQ output, and the embedded video processor's exact
P010/PQ-to-RGB10A2/PQ conversion. These checks use the default adapter selected by `probe_for`;
the Qt-adopted device is checked independently when its decoder and converter are created.
The capability probe does not establish that a monitor or the Qt swapchain supports HDR.

## Embedded HDR color contract

`VideoFormat` carries explicit transfer function, primaries, matrix, range, and chroma siting.
Callers supply the negotiated defaults, including `VideoChromaSiting::Left` for GFN. Actual
Media Foundation output metadata overrides these defaults; unsupported metadata and HDR-to-SDR
decoder downgrades fail rather than being mislabeled. HDR requires ten-bit BT.2020 output;
ten-bit SDR remains SDR. Color-only changes invalidate converter resources and texture slots.

The embedded renderer publishes `D3d11RecordedFrame` with `texture_format: Rgb10A2` and
`color_space: Pq2020` for HDR. PQ uses the matching LEFT or TOPLEFT input colorspace. HLG can
convert to PQ only for TOPLEFT siting and explicit driver support; HLG with LEFT siting and
full-range PQ fail because no exact DXGI input colorspace is available. SDR uses BT.709 RGB,
with RGBA8 for eight-bit input and RGB10A2 for ten-bit input.
The standalone HWND presenter explicitly rejects HDR; HDR presentation belongs to the embedded
Qt path, which must preserve the recorded texture's color interpretation through scan-out.
## Embedded Qt SDR conversion

The embedded frame producer converts NV12/AYUV to RGBA8 and P010/Y410 to RGB10A2,
preserving ten-bit precision until Qt's final SDR composition pass. RGB10A2 is an SDR
intermediate, not an HDR or ten-bit scan-out guarantee. Both full- and limited-range
BT.709 decoder output are converted to full-range BT.709 RGB.

Embedded conversion requires `ID3D11VideoContext1` for explicit color-space selection and
`ID3D11VideoProcessorEnumerator1` to validate the input/output format and color-space pair.
Unsupported conversions fail explicitly; the producer does not silently replace RGB10A2
with RGBA8 or rely on legacy driver color defaults. Format, range, and extent changes
invalidate the processor, cached input views, and frame slots before conversion resumes.

Media Foundation may temporarily negotiate a lower-precision output type before reading
the first sequence header. That startup compatibility does not permit downgraded frames:
each decoded surface must match its output media type and preserve at least the negotiated
bit depth and chroma before entering the decoded queue.

## HEVC availability in Qt

The embedded Qt stream view uses the same Media Foundation decoder configuration as the
Windows capability probe. HEVC input types explicitly carry the profile matching the negotiated
bit depth and chroma before the decoder selects its output type. Hardware profile checks and
D3D11 surface requirements still apply; setting a profile does not enable an unsupported GPU.

Windows must have a registered, activatable HEVC Media Foundation decoder, such as Microsoft's
HEVC Video Extensions, in addition to a compatible GPU and driver. The app does not bundle a
separate Windows HEVC decoder. An installed extension alone does not prove that decoder
activation or D3D11 configuration succeeds.

When Qt disables H.265, `%APPDATA%\OpenNOW\diagnostics\native-streamer.log` records
`Windows hardware decoder probe failed` with `codec=H.265` and the decoder error, even if H.264
remains available. `OPENNOW_DATA_DIR` overrides the data directory. Configuration failures include
the MFT name and activation, D3D manager, or input-type stage where applicable.

## Integration API

```rust,no_run
use std::num::NonZeroU32;
use opennow_streamer_platform_windows::{
    AudioFormat, BackendConfig, Bounds, OwnedWindow, SurfaceTarget, VideoCodec, VideoFormat,
    VideoChromaFormat, VideoChromaSiting, VideoPixelFormat, VideoTransferFunction,
    VideoColorPrimaries, VideoColorMatrix, WindowsBackend,
};

let backend = WindowsBackend::start(BackendConfig {
    video: VideoFormat {
        codec: VideoCodec::H264,
        width: 1920,
        height: 1080,
        frame_rate_numerator: NonZeroU32::new(120).unwrap(),
        frame_rate_denominator: NonZeroU32::new(1).unwrap(),
        average_bitrate: 75_000_000,
        pixel_format: VideoPixelFormat::Nv12,
        chroma_format: VideoChromaFormat::Cs420,
        chroma_siting: VideoChromaSiting::Left,
        full_range: false,
        transfer_function: VideoTransferFunction::Sdr,
        color_primaries: VideoColorPrimaries::Bt709,
        color_matrix: VideoColorMatrix::Bt709,
    },
    audio: AudioFormat {
        sample_rate: 48_000,
        channels: 2,
    },
    surface: SurfaceTarget::Owned(OwnedWindow {
        parent: None,
        bounds: Bounds {
            x: 0,
            y: 0,
            width: 1920,
            height: 1080,
        },
        visible: true,
    }),
    video_queue_capacity: 3,
    audio_queue_capacity: 8,
})?;
# Ok::<(), Box<dyn std::error::Error>>(())
```

`submit_video` and `submit_audio` never wait for the media thread. When a configured queue is full, the oldest packet is discarded and the method returns `PushOutcome::DroppedOldest`. The WASAPI staging buffer is independently capped at two endpoint buffers. Events use a bounded drop-oldest queue as well, so a stalled event consumer cannot create unbounded memory growth.

### Existing and parented surfaces

`SurfaceTarget::Existing` is only for a dedicated HWND that the caller reserves for D3D presentation. Do not pass the shell's interactive top-level HWND as an existing surface because the swap chain and window input policy would then share that interactive window.

An embedded shell integration must use `SurfaceTarget::Owned` with the shell HWND in `OwnedWindow::parent`. The backend creates a child with `WS_EX_NOACTIVATE | WS_EX_TRANSPARENT`, returns `HTTRANSPARENT` from `WM_NCHITTEST`, and rejects mouse activation. The child therefore cannot take focus or consume pointer input from the shell. `OwnedWindow::bounds` are parent-client coordinates relative to the shell. Calling `set_surface` with the same parent updates those bounds and `visible` state in place with `SWP_NOACTIVATE`; changing the parent recreates the owned child and its swap chain.

Video and audio reconfiguration flush their respective queues. D3D11 or WASAPI failures move the backend to `Recovering`, clear stale packets, and retry for five seconds. Successful D3D recovery emits `KeyFrameRequired`; exhausted recovery emits a fatal device-loss event. `stop` closes both queues and joins the media thread, including when recovery is in progress.

## Checks

The crate is a member of the native streamer workspace and can also be checked directly:

```sh
cargo test --manifest-path native/opennow-streamer/crates/opennow-streamer-platform-windows/Cargo.toml
cargo check --manifest-path native/opennow-streamer/crates/opennow-streamer-platform-windows/Cargo.toml --all-targets --target x86_64-pc-windows-msvc
cargo check --manifest-path native/opennow-streamer/crates/opennow-streamer-platform-windows/Cargo.toml --all-targets --target aarch64-pc-windows-msvc
```

The cross-target checks validate the Win32 bindings for x64 and ARM64. Hardware decode, presentation, audio output, device removal, and endpoint switching still require tests on real Windows hardware.
