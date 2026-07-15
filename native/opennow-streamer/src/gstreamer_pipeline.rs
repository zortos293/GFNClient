use crate::gstreamer_backend::send_log;
use crate::gstreamer_config::{
    automatic_present_max_fps, requested_video_backend, use_external_renderer_window,
    use_internal_renderer, vrr_present_max_fps, zero_copy_requested, EXTERNAL_RENDERER_ENV,
    NATIVE_D3D_FULLSCREEN_ENV, NATIVE_PRESENT_MAX_FPS_ENV, NATIVE_VIDEO_API_ENV,
    NATIVE_VIDEO_BACKEND_ENV, PRESENT_LIMITER_AUTO_SENTINEL, PRESENT_LIMITER_VRR_SENTINEL,
};
#[cfg(target_os = "windows")]
use crate::gstreamer_input::NativeWindowInputBridge;
use crate::gstreamer_input::{
    create_input_data_channels, wire_remote_data_channels, GstreamerInputChannels,
    GstreamerInputState,
};
use crate::gstreamer_liveness::{
    install_present_limiter, watch_audio_activity, watch_first_sink_buffer,
    watch_rtp_video_bitrate, watch_video_caps_transitions, watch_video_decoded_rate,
    watch_video_sink_caps_transitions, watch_video_sink_rate, VideoLivenessMonitor,
};
use crate::gstreamer_platform::{
    arm_internal_child_input, primary_display_refresh_hz, release_native_input_capture,
    start_external_renderer_window_guard, update_external_renderer_surface,
};
use crate::gstreamer_transitions::DEFAULT_VIDEO_QUEUE_DEPTH;
use crate::internal_renderer::InternalRenderer;
use crate::nvst_video::{
    annexb_appsrc_caps, spawn_nvst_udp_receive, NvstVideoReceiveHandle,
};
use crate::protocol::{
    Event, IceCandidatePayload, IceServer, NativeRenderSurface, NativeStreamerSessionContext,
    NativeVideoBackendCapability, NativeVideoCodecCapability, NvstVideoSession,
};
use crate::sdp::IceCredentials;
use gst::glib;
use gst::prelude::*;
use gstreamer as gst;
use gstreamer_sdp as gst_sdp;
use gstreamer_webrtc as gst_webrtc;
use std::collections::HashSet;
use std::ffi::CString;
use std::sync::atomic::{AtomicBool, AtomicU32, Ordering};
use std::sync::mpsc::Sender;
use std::sync::{Arc, Mutex};
use std::thread;

const WEBRTC_LATENCY_MS: u32 = 2;
const DEFAULT_GFN_STUN_SERVER: &str = "stun://stun2.l.google.com:19302";
const VIDEO_COMPRESSED_QUEUE_MAX_BUFFERS: u32 = 6;
pub(crate) const VIDEO_QUEUE_MAX_BUFFERS: u32 = DEFAULT_VIDEO_QUEUE_DEPTH;
const AUDIO_QUEUE_MAX_BUFFERS: u32 = 2;

// gstreamer-rs exposes the generic ICE transport but not the NICE stream that
// owns remote credentials. GFN uses UUID ICE passwords, so we need the actual
// NICE stream after GStreamer's SDP parser validates a sanitized copy.
#[repr(C)]
struct GstWebRTCNiceTransportCompat {
    parent: gst_webrtc::ffi::GstWebRTCICETransport,
    stream: *mut gst_webrtc::ffi::GstWebRTCICEStream,
    _priv: glib::ffi::gpointer,
}

#[derive(Debug, Clone, Copy)]
struct ActualNiceIceStream {
    ptr: *mut gst_webrtc::ffi::GstWebRTCICEStream,
    stream_id: u32,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum DecodedMediaKind {
    Audio,
    Video,
    Unknown,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum RtpVideoChainRole {
    Depayloader,
    Parser,
    PreDecodeQueue,
    Decoder,
    PostDecodeRateSetter,
    PostDecodeConverter,
    PostDecodeCapsFilter,
    StatsOverlay,
    PostDecodeQueue,
    Sink,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum RtpVideoApi {
    D3D11,
    D3D12,
    VideoToolbox,
    Vaapi,
    V4L2,
    Vulkan,
    Software,
}

impl RtpVideoApi {
    fn label(self) -> &'static str {
        match self {
            Self::D3D11 => "D3D11",
            Self::D3D12 => "D3D12",
            Self::VideoToolbox => "VideoToolbox",
            Self::Vaapi => "VAAPI",
            Self::V4L2 => "V4L2",
            Self::Vulkan => "Vulkan",
            Self::Software => "software",
        }
    }

    fn capability_id(self) -> &'static str {
        match self {
            Self::D3D11 => "d3d11",
            Self::D3D12 => "d3d12",
            Self::VideoToolbox => "videotoolbox",
            Self::Vaapi => "vaapi",
            Self::V4L2 => "v4l2",
            Self::Vulkan => "vulkan",
            Self::Software => "software",
        }
    }

    fn platform(self) -> &'static str {
        match self {
            Self::D3D11 | Self::D3D12 => "windows",
            Self::VideoToolbox => "macos",
            Self::Vaapi | Self::V4L2 => "linux",
            Self::Vulkan if current_platform_label() == "windows" => "windows",
            Self::Vulkan => "linux",
            Self::Software => "cross-platform",
        }
    }

    fn memory_caps(self) -> Option<&'static str> {
        match self {
            // D3D decoders and sinks can negotiate GPU memory directly. Keep
            // the capsfilter opt-in so startup does not fail when a live RTP
            // stream's raw caps are still settling.
            Self::D3D11 => zero_copy_requested().then_some("video/x-raw(memory:D3D11Memory)"),
            Self::D3D12 => zero_copy_requested().then_some("video/x-raw(memory:D3D12Memory)"),
            Self::VideoToolbox => zero_copy_requested().then_some("video/x-raw(memory:GLMemory)"),
            Self::Vaapi => zero_copy_requested().then_some("video/x-raw(memory:VAMemory)"),
            // Linux: keep Vulkan images in-GPU. Windows uses a DXVA→upload hybrid
            // (vulkanh264dec currently SIGSEGVs on NVIDIA Windows), so skip a hard
            // VulkanImage capsfilter on that path.
            Self::Vulkan if cfg!(target_os = "windows") => None,
            Self::Vulkan => Some("video/x-raw(memory:VulkanImage)"),
            _ => None,
        }
    }

    fn post_decode_converter_factory(self) -> Option<&'static str> {
        match self {
            Self::D3D11 | Self::D3D12 => None,
            // Windows Vulkan present chain inserts download/convert/upload explicitly.
            Self::Vulkan if cfg!(target_os = "windows") => None,
            Self::Vulkan => Some("vulkancolorconvert"),
            Self::VideoToolbox | Self::Vaapi if zero_copy_requested() => None,
            // Non-D3D hardware decoders are not guaranteed to negotiate directly with every
            // platform sink. Keep these paths reliable with an explicit raw-video conversion stage.
            Self::VideoToolbox | Self::Vaapi | Self::V4L2 | Self::Software => Some("videoconvert"),
        }
    }

    fn stats_overlay_factory(self) -> Option<&'static str> {
        match self {
            Self::D3D11 | Self::D3D12 => Some("dwritetextoverlay"),
            _ => None,
        }
    }

    fn sink_factory(self) -> &'static str {
        match self {
            Self::D3D11 => "d3d11videosink",
            Self::D3D12 => "d3d12videosink",
            Self::VideoToolbox => "glimagesink",
            Self::Vaapi => "glimagesink",
            Self::V4L2 => "glimagesink",
            Self::Vulkan => "vulkansink",
            Self::Software => "autovideosink",
        }
    }

    fn decoder_factory(self, codec: &str) -> Option<&'static str> {
        match (self, codec) {
            (Self::D3D11, "H265" | "HEVC") => Some("d3d11h265dec"),
            (Self::D3D11, "H264") => Some("d3d11h264dec"),
            (Self::D3D11, "AV1") => Some("d3d11av1dec"),
            (Self::D3D12, "H265" | "HEVC") => Some("d3d12h265dec"),
            (Self::D3D12, "H264") => Some("d3d12h264dec"),
            (Self::D3D12, "AV1") => Some("d3d12av1dec"),
            (Self::VideoToolbox, "H265" | "HEVC" | "H264") => Some("vtdec_hw"),
            (Self::Vaapi, "H265" | "HEVC") => Some("vah265dec"),
            (Self::Vaapi, "H264") => Some("vah264dec"),
            (Self::Vaapi, "AV1") => Some("vaav1dec"),
            (Self::V4L2, "H265" | "HEVC") => Some("v4l2slh265dec"),
            (Self::V4L2, "H264") => Some("v4l2slh264dec"),
            // vulkanh264dec/vulkanh265dec SIGSEGV on current NVIDIA Windows drivers;
            // use DXVA decode (prefer D3D12) and either D3D present (Internal) or
            // upload into Vulkan (External).
            (Self::Vulkan, "H265" | "HEVC") if cfg!(target_os = "windows") => Some("d3d12h265dec"),
            (Self::Vulkan, "H264") if cfg!(target_os = "windows") => Some("d3d12h264dec"),
            (Self::Vulkan, "H265" | "HEVC") => Some("vulkanh265dec"),
            (Self::Vulkan, "H264") => Some("vulkanh264dec"),
            (Self::Software, "H265" | "HEVC") => Some("avdec_h265"),
            (Self::Software, "H264") => Some("avdec_h264"),
            (Self::Software, "AV1") => Some("avdec_av1"),
            _ => None,
        }
    }

    fn fallback_decoder_factories(self, codec: &str) -> &'static [&'static str] {
        match (self, codec) {
            (Self::Vaapi, "H265" | "HEVC") => &["vaapih265dec"],
            (Self::Vaapi, "H264") => &["vaapih264dec"],
            (Self::Vaapi, "AV1") => &["vaapiav1dec"],
            (Self::V4L2, "H265" | "HEVC") => &["v4l2h265dec"],
            (Self::V4L2, "H264") => &["v4l2h264dec"],
            (Self::VideoToolbox, "H265" | "HEVC" | "H264") => &["vtdec"],
            (Self::Vulkan, "H265" | "HEVC") if cfg!(target_os = "windows") => {
                &["d3d11h265dec", "nvh265dec"]
            }
            (Self::Vulkan, "H264") if cfg!(target_os = "windows") => {
                &["d3d11h264dec", "nvh264dec"]
            }
            _ => &[],
        }
    }

    fn sink_fallback_factories(self) -> &'static [&'static str] {
        match self {
            Self::VideoToolbox => &["osxvideosink", "autovideosink"],
            // Prefer X11-capable sinks first: Internal Linux embeds via GstVideoOverlay
            // into an X11 child. waylandsink cannot paint into that handle.
            Self::Vaapi | Self::V4L2 => {
                &["ximagesink", "xvimagesink", "glimagesink", "waylandsink", "autovideosink"]
            }
            Self::Software => &["ximagesink", "xvimagesink", "glimagesink", "waylandsink"],
            _ => &[],
        }
    }

    /// Sinks that can bind to the Internal X11 child via GstVideoOverlay.
    fn internal_x11_sink_candidates(self) -> &'static [&'static str] {
        match self {
            Self::Vaapi | Self::V4L2 | Self::Software | Self::Vulkan => {
                &["glimagesink", "ximagesink", "xvimagesink"]
            }
            _ => &[],
        }
    }

    fn is_gpu_path(self) -> bool {
        !matches!(self, Self::Software)
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct RtpVideoChainSpec {
    pub(crate) factory: &'static str,
    pub(crate) role: RtpVideoChainRole,
    pub(crate) caps: Option<String>,
}

impl RtpVideoChainSpec {
    fn new(factory: &'static str, role: RtpVideoChainRole) -> Self {
        Self {
            factory,
            role,
            caps: None,
        }
    }

    fn with_caps(factory: &'static str, role: RtpVideoChainRole, caps: impl Into<String>) -> Self {
        Self {
            factory,
            role,
            caps: Some(caps.into()),
        }
    }
}

#[derive(Clone, Debug)]
pub(crate) struct GstreamerRenderState {
    surface: Arc<Mutex<Option<NativeRenderSurface>>>,
    video_sink: Arc<Mutex<Option<gst::Element>>>,
    internal_renderer: Arc<InternalRenderer>,
    external_renderer_logged: Arc<AtomicBool>,
    internal_renderer_logged: Arc<AtomicBool>,
    external_window_guard_started: Arc<AtomicBool>,
    external_window_guard_stop: Arc<AtomicBool>,
}

impl Default for GstreamerRenderState {
    fn default() -> Self {
        Self {
            surface: Arc::new(Mutex::new(None)),
            video_sink: Arc::new(Mutex::new(None)),
            internal_renderer: Arc::new(InternalRenderer::new()),
            external_renderer_logged: Arc::new(AtomicBool::new(false)),
            internal_renderer_logged: Arc::new(AtomicBool::new(false)),
            external_window_guard_started: Arc::new(AtomicBool::new(false)),
            external_window_guard_stop: Arc::new(AtomicBool::new(false)),
        }
    }
}

impl GstreamerRenderState {
    fn set_surface(&self, surface: NativeRenderSurface, event_sender: &Option<Sender<Event>>) {
        if let Ok(mut current) = self.surface.lock() {
            *current = Some(surface);
        }
        self.apply(event_sender);
    }

    fn set_video_sink(&self, sink: gst::Element, event_sender: &Option<Sender<Event>>) {
        if let Ok(mut current) = self.video_sink.lock() {
            *current = Some(sink.clone());
        }
        if use_internal_renderer() {
            if let Err(message) = self.internal_renderer.set_video_sink(sink) {
                send_log(event_sender, "warn", message);
            }
        }
        self.apply(event_sender);
    }

    fn apply(&self, event_sender: &Option<Sender<Event>>) {
        if use_external_renderer_window() {
            let sink_ready = self.video_sink.lock().ok().and_then(|sink| sink.clone());
            let Some(_sink) = sink_ready else {
                return;
            };

            if let Some(surface) = self.surface.lock().ok().and_then(|surface| surface.clone()) {
                update_external_renderer_surface(&surface);
            }
            if !self
                .external_window_guard_started
                .swap(true, Ordering::SeqCst)
            {
                self.external_window_guard_stop
                    .store(false, Ordering::SeqCst);
                start_external_renderer_window_guard(
                    event_sender.clone(),
                    self.external_window_guard_stop.clone(),
                );
            }
            if !self.external_renderer_logged.swap(true, Ordering::SeqCst) {
                send_log(
                    event_sender,
                    "info",
                    format!(
                        "Using external native GStreamer renderer window; set {EXTERNAL_RENDERER_ENV}=0 for the internal child-surface renderer."
                    ),
                );
            }
            return;
        }

        let sink_ready = self.video_sink.lock().ok().and_then(|sink| sink.clone());
        let Some(_sink) = sink_ready else {
            return;
        };

        let surface = self.surface.lock().ok().and_then(|surface| surface.clone());
        let Some(surface) = surface else {
            return;
        };

        if !self.internal_renderer_logged.swap(true, Ordering::SeqCst) {
            send_log(
                event_sender,
                "info",
                format!(
                    "Using internal native child-surface renderer; set {EXTERNAL_RENDERER_ENV}=1 for the floating GStreamer window."
                ),
            );
        }

        if let Err(message) = self.internal_renderer.apply_surface(&surface) {
            send_log(event_sender, "warn", message);
        }

        // Keep ClipCursor / capture rect aligned with the StreamView hole, and
        // (re)arm RawInput if the child HWND was recreated on parent change.
        #[cfg(target_os = "windows")]
        {
            update_external_renderer_surface(&surface);
            let hwnd = self.internal_renderer.child_handle();
            if hwnd != 0 {
                let _ = arm_internal_child_input(hwnd);
            }
        }
    }

    fn stop_external_renderer_window_guard(&self) {
        self.external_window_guard_stop
            .store(true, Ordering::SeqCst);
        self.external_window_guard_started
            .store(false, Ordering::SeqCst);
    }

    fn destroy_internal_renderer(&self) {
        self.internal_renderer.destroy();
        self.internal_renderer_logged
            .store(false, Ordering::SeqCst);
    }
}

#[derive(Debug)]
pub(crate) struct GstreamerPipeline {
    pub(crate) pipeline: gst::Pipeline,
    pub(crate) webrtc: gst::Element,
    input_state: GstreamerInputState,
    input_channels: Option<GstreamerInputChannels>,
    #[cfg(target_os = "windows")]
    native_window_input_bridge: Option<NativeWindowInputBridge>,
    render_state: GstreamerRenderState,
    present_max_fps: Arc<AtomicU32>,
    d3d_fullscreen_sink: Arc<AtomicBool>,
    /// When true, WebRTC RTP video pads are ignored (classic NVST UDP owns video).
    skip_webrtc_video: Arc<AtomicBool>,
    nvst_receive: Option<NvstVideoReceiveHandle>,
    video_liveness: VideoLivenessMonitor,
    event_sender: Option<Sender<Event>>,
    pub(crate) original_remote_ice_credentials: Option<IceCredentials>,
}

impl GstreamerPipeline {
    pub(crate) fn build(
        event_sender: Option<Sender<Event>>,
        ice_servers: &[IceServer],
    ) -> Result<Self, String> {
        init_gstreamer()?;

        let pipeline = gst::Pipeline::new();
        let webrtc = gst::ElementFactory::make("webrtcbin")
            .name("opennow-webrtcbin")
            .property_from_str("bundle-policy", "max-bundle")
            .build()
            .map_err(|error| format!("Failed to create webrtcbin: {error}"))?;
        configure_webrtc_low_latency(&webrtc);
        let stun_server = resolve_gstreamer_stun_server(ice_servers);
        webrtc.set_property("stun-server", &stun_server);
        send_log(
            &event_sender,
            "info",
            format!("Configured GStreamer ICE with STUN server {stun_server}."),
        );

        let input_state = GstreamerInputState::default();
        let render_state = GstreamerRenderState::default();
        let video_liveness = VideoLivenessMonitor::default();
        wire_local_ice_events(&webrtc, event_sender.clone())?;
        wire_webrtc_state_events(&webrtc, event_sender.clone());
        wire_remote_data_channels(&webrtc, event_sender.clone());
        start_gstreamer_bus_diagnostics(
            &pipeline,
            event_sender.clone(),
            video_liveness.stop_flag(),
            video_liveness.clone(),
        );
        let present_max_fps = Arc::new(AtomicU32::new(0));
        let d3d_fullscreen_sink = Arc::new(AtomicBool::new(false));
        let skip_webrtc_video = Arc::new(AtomicBool::new(false));
        wire_incoming_media_sink(
            &pipeline,
            &webrtc,
            event_sender.clone(),
            render_state.clone(),
            present_max_fps.clone(),
            d3d_fullscreen_sink.clone(),
            skip_webrtc_video.clone(),
            video_liveness.clone(),
        );

        pipeline
            .add(&webrtc)
            .map_err(|error| format!("Failed to add webrtcbin to pipeline: {error}"))?;
        pipeline
            .set_state(gst::State::Ready)
            .map_err(|error| format!("Failed to set GStreamer pipeline to Ready: {error:?}"))?;

        Ok(Self {
            pipeline,
            webrtc,
            input_state,
            input_channels: None,
            #[cfg(target_os = "windows")]
            native_window_input_bridge: None,
            render_state,
            present_max_fps,
            d3d_fullscreen_sink,
            skip_webrtc_video,
            nvst_receive: None,
            video_liveness,
            event_sender,
            original_remote_ice_credentials: None,
        })
    }

    pub(crate) fn parse_offer_sdp(sdp: &str) -> Result<gst_sdp::SDPMessage, String> {
        init_gstreamer()?;
        gst_sdp::SDPMessage::parse_buffer(sdp.as_bytes())
            .map_err(|error| format!("GStreamer rejected the remote SDP offer: {error:?}"))
    }

    pub(crate) fn webrtc_name(&self) -> String {
        self.webrtc.name().to_string()
    }

    pub(crate) fn set_present_max_fps(&self, fps: u32) {
        self.present_max_fps.store(fps, Ordering::SeqCst);
    }

    pub(crate) fn set_d3d_fullscreen_sink(&self, enabled: bool) {
        self.d3d_fullscreen_sink.store(enabled, Ordering::SeqCst);
    }

    pub(crate) fn configure_stats(
        &self,
        context: &NativeStreamerSessionContext,
        target_bitrate_kbps: u32,
    ) {
        self.video_liveness.configure(context, target_bitrate_kbps);
    }

    /// Attach classic NVST UDP video: appsrc → parse → decoder → sink, plus UDP recv thread.
    /// Keeps webrtcbin for SCTP input; ignores WebRTC RTP video pads.
    pub(crate) fn attach_nvst_video(
        &mut self,
        session: NvstVideoSession,
        fallback_codec: &str,
        requested_fps: Option<u32>,
        d3d_fullscreen_sink: bool,
    ) -> Result<(), String> {
        if self.nvst_receive.is_some() {
            return Ok(());
        }

        let codec = session
            .codec
            .as_deref()
            .filter(|c| !c.is_empty())
            .unwrap_or(fallback_codec);
        let codec_upper = codec.to_ascii_uppercase();
        let encoding = match codec_upper.as_str() {
            "H264" => "H264",
            "H265" | "HEVC" => "H265",
            other => {
                return Err(format!(
                    "NVST classic UDP video scaffold supports H264/H265, got {other}"
                ));
            }
        };

        self.skip_webrtc_video.store(true, Ordering::SeqCst);

        let (video_api, mut specs) = rtp_video_chain_specs(encoding, requested_fps).ok_or_else(|| {
            format!(
                "NVST Annex-B decode chain unavailable for {encoding}; install GStreamer plugins or set {NATIVE_VIDEO_BACKEND_ENV}=software."
            )
        })?;
        // Drop RTP depayloader — appsrc feeds assembled Annex-B AUs.
        specs.retain(|spec| spec.role != RtpVideoChainRole::Depayloader);
        if specs
            .first()
            .is_none_or(|spec| spec.role != RtpVideoChainRole::Parser)
        {
            return Err(format!(
                "NVST video chain for {encoding} is missing a parser after depayloader removal."
            ));
        }

        let caps_str = annexb_appsrc_caps(encoding);
        let appsrc = gst::ElementFactory::make("appsrc")
            .name("nvst-annexb")
            .build()
            .map_err(|error| format!("Failed to create nvst-annexb appsrc: {error}"))?;
        let caps = caps_str
            .parse::<gst::Caps>()
            .map_err(|error| format!("Invalid NVST appsrc caps: {error}"))?;
        appsrc.set_property("caps", &caps);
        set_property_if_supported(&appsrc, "is-live", true);
        set_property_from_str_if_supported(&appsrc, "format", "time");
        set_property_if_supported(&appsrc, "block", false);
        set_property_if_supported(&appsrc, "max-bytes", 0u64);
        set_property_from_str_if_supported(&appsrc, "stream-type", "stream");

        let streaming_reported = Arc::new(AtomicBool::new(false));
        let mut elements: Vec<gst::Element> = Vec::with_capacity(specs.len() + 1);

        let result = (|| -> Result<(), String> {
            send_log(
                &self.event_sender,
                "info",
                format!(
                    "Attaching NVST classic UDP video ({encoding}) via appsrc Annex-B → {}; {}",
                    video_api.label(),
                    format_video_chain_selection(encoding, video_api, &specs)
                ),
            );

            let configured_present_max_fps = self.present_max_fps.load(Ordering::SeqCst);
            let effective = effective_present_max_fps(
                configured_present_max_fps,
                requested_fps,
                video_api,
                primary_display_refresh_hz(),
            );
            self.present_max_fps.store(effective, Ordering::SeqCst);

            self.pipeline
                .add(&appsrc)
                .map_err(|error| format!("Failed to add NVST appsrc: {error}"))?;
            elements.push(appsrc.clone());

            for spec in &specs {
                let element = make_element(spec.factory)?;
                configure_rtp_video_chain_element(
                    &element,
                    spec.clone(),
                    video_api,
                    d3d_fullscreen_sink,
                );
                if spec.role == RtpVideoChainRole::StatsOverlay {
                    self.video_liveness.set_stats_overlay(Some(element.clone()));
                }
                self.pipeline.add(&element).map_err(|error| {
                    format!(
                        "Failed to add {} for NVST {encoding} video chain: {error}",
                        spec.factory
                    )
                })?;
                elements.push(element);
            }

            for pair in elements.windows(2) {
                pair[0].link(&pair[1]).map_err(|error| {
                    format!(
                        "Failed to link {} -> {} for NVST {encoding}: {error:?}",
                        element_factory_name(&pair[0]),
                        element_factory_name(&pair[1])
                    )
                })?;
            }

            let sink = elements
                .last()
                .ok_or_else(|| format!("NVST {encoding} video chain has no sink."))?;
            if let Some(post_decode_queue) =
                specs
                    .iter()
                    .zip(elements.iter().skip(1))
                    .find_map(|(spec, element)| {
                        (spec.role == RtpVideoChainRole::PostDecodeQueue).then_some(element)
                    })
            {
                self.video_liveness
                    .set_post_decode_queue(post_decode_queue.clone());
                watch_video_decoded_rate(
                    post_decode_queue,
                    &self.event_sender,
                    Some(self.video_liveness.clone()),
                );
            }
            if let Some(pre_decode_queue) =
                specs
                    .iter()
                    .zip(elements.iter().skip(1))
                    .find_map(|(spec, element)| {
                        (spec.role == RtpVideoChainRole::PreDecodeQueue).then_some(element)
                    })
            {
                self.video_liveness
                    .set_pre_decode_queue(pre_decode_queue.clone());
            }
            if let Some(parser) = specs.iter().zip(elements.iter().skip(1)).find_map(
                |(spec, element)| (spec.role == RtpVideoChainRole::Parser).then_some(element),
            ) {
                watch_video_caps_transitions(
                    parser,
                    "parser",
                    &self.event_sender,
                    self.video_liveness.clone(),
                );
            }
            if let Some(decoder) = specs.iter().zip(elements.iter().skip(1)).find_map(
                |(spec, element)| (spec.role == RtpVideoChainRole::Decoder).then_some(element),
            ) {
                self.video_liveness.set_decoder(decoder.clone());
                watch_video_caps_transitions(
                    decoder,
                    "decoder",
                    &self.event_sender,
                    self.video_liveness.clone(),
                );
            }

            self.render_state
                .set_video_sink(sink.clone(), &self.event_sender);
            install_present_limiter(
                sink,
                self.present_max_fps.clone(),
                &self.event_sender,
                Some(self.video_liveness.clone()),
            );
            watch_video_sink_caps_transitions(
                sink,
                &self.event_sender,
                Some(self.video_liveness.clone()),
            );
            watch_first_sink_buffer(sink, "video", &self.event_sender, &streaming_reported);
            watch_video_sink_rate(
                sink,
                &self.event_sender,
                Some(self.video_liveness.clone()),
            );

            for element in &elements {
                element.sync_state_with_parent().map_err(|error| {
                    format!("Failed to sync NVST {encoding} video-chain element state: {error}")
                })?;
            }

            self.video_liveness.update_hardware_acceleration(format!(
                "GStreamer {} (NVST UDP)",
                video_api.label()
            ));
            self.video_liveness.start(
                self.pipeline.clone(),
                sink.clone(),
                self.event_sender.clone(),
            );

            let handle = spawn_nvst_udp_receive(
                session,
                appsrc,
                self.event_sender.clone(),
            )?;
            self.nvst_receive = Some(handle);

            // Ensure pipeline can run the appsrc branch even before WebRTC offer.
            let _ = self.pipeline.set_state(gst::State::Playing);

            Ok(())
        })();

        if result.is_err() {
            self.skip_webrtc_video.store(false, Ordering::SeqCst);
            for element in &elements {
                let _ = element.set_state(gst::State::Null);
                let _ = self.pipeline.remove(element);
            }
        }

        result
    }

    fn ensure_input_data_channels(
        &mut self,
        partial_reliable_threshold_ms: u32,
    ) -> Result<(), String> {
        if self.input_channels.is_some() {
            return Ok(());
        }

        self.input_state.reset();
        let channels = create_input_data_channels(
            &self.webrtc,
            self.input_state.clone(),
            self.event_sender.clone(),
            partial_reliable_threshold_ms,
        )?;
        let _ = channels.labels();
        self.input_channels = Some(channels);
        self.ensure_native_window_input_bridge();
        Ok(())
    }

    #[cfg(target_os = "windows")]
    fn ensure_native_window_input_bridge(&mut self) {
        // Win32 RawInput: floating external window OR internal child HWND.
        // Electron click-through across a topmost D3D sibling is unreliable.
        if self.native_window_input_bridge.is_some() {
            return;
        }
        let Some(input_channels) = self.input_channels.clone() else {
            return;
        };

        self.native_window_input_bridge = Some(NativeWindowInputBridge::start(
            self.input_state.clone(),
            input_channels,
            self.event_sender.clone(),
        ));
        if use_internal_renderer() {
            let hwnd = self.render_state.internal_renderer.child_handle();
            if hwnd != 0 && arm_internal_child_input(hwnd) {
                send_log(
                    &self.event_sender,
                    "info",
                    "Armed RawInput capture on the internal child HWND.".to_owned(),
                );
            }
        }
    }

    #[cfg(not(target_os = "windows"))]
    fn ensure_native_window_input_bridge(&mut self) {
        if use_internal_renderer() {
            return;
        }
        send_log(
            &self.event_sender,
            "warn",
            format!(
                "Native OS-level input capture is not implemented for {}; Electron input forwarding remains active.",
                std::env::consts::OS
            ),
        );
    }

    pub(crate) fn negotiate_answer(
        &mut self,
        offer_sdp: gst_sdp::SDPMessage,
        original_remote_credentials: Option<&IceCredentials>,
        partial_reliable_threshold_ms: u32,
    ) -> Result<String, String> {
        let offer =
            gst_webrtc::WebRTCSessionDescription::new(gst_webrtc::WebRTCSDPType::Offer, offer_sdp);
        self.pipeline
            .set_state(gst::State::Playing)
            .map_err(|error| {
                format!("Failed to set GStreamer pipeline to Playing before negotiation: {error:?}")
            })?;
        self.set_description("set-remote-description", &offer)?;
        if let Some(credentials) = original_remote_credentials {
            self.original_remote_ice_credentials = Some(credentials.clone());
            self.try_restore_original_remote_ice_credentials("after remote description")?;
        }
        self.ensure_input_data_channels(partial_reliable_threshold_ms)?;
        let answer = self.create_answer()?;
        let answer_sdp = answer
            .sdp()
            .as_text()
            .map_err(|error| format!("Failed to serialize GStreamer answer SDP: {error}"))?;
        self.set_description("set-local-description", &answer)?;
        self.try_restore_original_remote_ice_credentials("after local description")?;
        Ok(answer_sdp)
    }

    pub(crate) fn try_restore_original_remote_ice_credentials(
        &mut self,
        stage: &str,
    ) -> Result<bool, String> {
        let Some(credentials) = self.original_remote_ice_credentials.clone() else {
            return Ok(false);
        };

        if credentials.ufrag.is_empty() || credentials.pwd.is_empty() {
            return Err(
                "Cannot restore original remote ICE credentials: offer credentials are empty."
                    .to_owned(),
            );
        }

        let Some(ice_agent) = self
            .webrtc
            .property::<Option<gst_webrtc::WebRTCICE>>("ice-agent")
        else {
            return Err(
                "Cannot restore original remote ICE credentials: webrtcbin has no ICE agent."
                    .to_owned(),
            );
        };
        let ice_agent_ptr = ice_agent.as_ptr() as *mut gst_webrtc::ffi::GstWebRTCICE;
        let ufrag = CString::new(credentials.ufrag.as_str())
            .map_err(|_| "Cannot restore original remote ICE credentials: ufrag contains NUL.")?;
        let pwd = CString::new(credentials.pwd.as_str())
            .map_err(|_| "Cannot restore original remote ICE credentials: pwd contains NUL.")?;

        let streams = self.negotiated_nice_streams();
        if streams.is_empty() {
            send_log(
                &self.event_sender,
                "warn",
                format!(
                    "GStreamer has not exposed actual NICE ICE streams {stage}; deferring GFN remote ICE credential restoration."
                ),
            );
            return Ok(false);
        }

        let mut restored = 0usize;
        let stream_ids = streams
            .iter()
            .map(|stream| stream.stream_id)
            .collect::<Vec<_>>();
        for stream in &streams {
            let accepted = unsafe {
                gst_webrtc::ffi::gst_webrtc_ice_set_remote_credentials(
                    ice_agent_ptr,
                    stream.ptr,
                    ufrag.as_ptr(),
                    pwd.as_ptr(),
                ) != glib::ffi::GFALSE
            };
            if accepted {
                restored += 1;
            } else {
                send_log(
                    &self.event_sender,
                    "warn",
                    format!(
                        "GStreamer ICE agent rejected original remote credentials for actual stream {}.",
                        stream.stream_id
                    ),
                );
            }
        }

        if restored == 0 {
            send_log(
                &self.event_sender,
                "warn",
                format!(
                    "GStreamer rejected original GFN remote ICE credentials on all actual streams {stage}; ICE may fail."
                ),
            );
            return Ok(false);
        }

        send_log(
            &self.event_sender,
            "info",
            format!(
                "Restored original GFN remote ICE credentials on {restored}/{} actual GStreamer NICE ICE stream(s) {stage}; streamIds={stream_ids:?}.",
                streams.len()
            ),
        );
        Ok(true)
    }

    fn negotiated_nice_streams(&self) -> Vec<ActualNiceIceStream> {
        let mut streams = Vec::new();
        let mut seen_stream_pointers = HashSet::new();
        let mut seen_transport_summaries = Vec::new();
        for index in 0..8 {
            let transceiver = self
                .webrtc
                .emit_by_name::<Option<gst_webrtc::WebRTCRTPTransceiver>>(
                    "get-transceiver",
                    &[&(index as i32)],
                );
            let Some(transceiver) = transceiver else {
                continue;
            };

            if let Some(receiver) = transceiver.receiver() {
                if let Some(transport) = receiver.transport() {
                    self.collect_nice_stream_from_dtls_transport(
                        &transport,
                        index,
                        "receiver",
                        &mut streams,
                        &mut seen_stream_pointers,
                        &mut seen_transport_summaries,
                    );
                }
            }
            if let Some(sender) = transceiver.sender() {
                if let Some(transport) = sender.transport() {
                    self.collect_nice_stream_from_dtls_transport(
                        &transport,
                        index,
                        "sender",
                        &mut streams,
                        &mut seen_stream_pointers,
                        &mut seen_transport_summaries,
                    );
                }
            }
        }

        if !seen_transport_summaries.is_empty() {
            send_log(
                &self.event_sender,
                "debug",
                format!(
                    "GStreamer negotiated ICE transports: {}.",
                    seen_transport_summaries.join(", ")
                ),
            );
        }
        streams
    }

    fn collect_nice_stream_from_dtls_transport(
        &self,
        dtls_transport: &gst_webrtc::WebRTCDTLSTransport,
        transceiver_index: u32,
        direction: &str,
        streams: &mut Vec<ActualNiceIceStream>,
        seen_stream_pointers: &mut HashSet<usize>,
        seen_transport_summaries: &mut Vec<String>,
    ) {
        let session_id = dtls_transport.session_id();
        let Some(ice_transport) = dtls_transport.transport() else {
            seen_transport_summaries.push(format!(
                "transceiver {transceiver_index} {direction} dtlsSession={session_id} iceTransport=none"
            ));
            return;
        };

        let transport_type = ice_transport.type_().name().to_owned();
        let component = ice_transport.component();
        let state = ice_transport.state();
        let Some(stream) = nice_stream_from_ice_transport(&ice_transport) else {
            seen_transport_summaries.push(format!(
                "transceiver {transceiver_index} {direction} dtlsSession={session_id} iceTransportType={transport_type} component={component:?} state={state:?} stream=none"
            ));
            return;
        };

        seen_transport_summaries.push(format!(
            "transceiver {transceiver_index} {direction} dtlsSession={session_id} iceTransportType={transport_type} component={component:?} state={state:?} streamId={}",
            stream.stream_id
        ));

        let stream_pointer = stream.ptr as usize;
        if seen_stream_pointers.insert(stream_pointer) {
            streams.push(stream);
        }
    }

    pub(crate) fn set_description(
        &self,
        signal_name: &'static str,
        description: &gst_webrtc::WebRTCSessionDescription,
    ) -> Result<(), String> {
        let promise = gst::Promise::new();
        self.webrtc
            .emit_by_name::<()>(signal_name, &[description, &promise]);
        wait_for_promise(&promise, signal_name)
    }

    fn create_answer(&self) -> Result<gst_webrtc::WebRTCSessionDescription, String> {
        let promise = gst::Promise::new();
        self.webrtc
            .emit_by_name::<()>("create-answer", &[&None::<gst::Structure>, &promise]);
        wait_for_promise(&promise, "create-answer")?;
        let reply = promise
            .get_reply()
            .ok_or_else(|| "GStreamer create-answer resolved without a reply.".to_owned())?;
        reply
            .get::<gst_webrtc::WebRTCSessionDescription>("answer")
            .map_err(|error| {
                format!(
                    "GStreamer create-answer reply did not contain an answer: {error}; reply={}",
                    describe_structure(reply)
                )
            })
    }

    pub(crate) fn add_remote_ice(&mut self, candidate: &IceCandidatePayload) -> Result<(), String> {
        if candidate.candidate.trim().is_empty() {
            return Err("Remote ICE candidate is empty.".to_owned());
        }
        self.try_restore_original_remote_ice_credentials("before adding remote ICE candidate")?;
        let sdp_m_line_index = candidate.sdp_m_line_index.unwrap_or(0);
        self.webrtc.emit_by_name::<()>(
            "add-ice-candidate",
            &[&sdp_m_line_index, &candidate.candidate],
        );
        Ok(())
    }

    pub(crate) fn send_input_packet(&self, payload: &[u8], partially_reliable: bool) -> bool {
        if !self.input_state.ready.load(Ordering::SeqCst)
            || self.input_state.paused.load(Ordering::SeqCst)
        {
            return false;
        }

        let Some(input_channels) = &self.input_channels else {
            return false;
        };

        input_channels.send_packet(payload, partially_reliable)
    }

    pub(crate) fn set_input_paused(&self, paused: bool) {
        self.input_state.paused.store(paused, Ordering::SeqCst);
        if paused {
            release_native_input_capture();
        }
    }

    pub(crate) fn update_render_surface(&self, surface: NativeRenderSurface) {
        self.video_liveness
            .set_stats_overlay_visible(surface.visible && surface.show_stats);
        self.render_state.set_surface(surface, &self.event_sender);
    }

    pub(crate) fn stop(mut self) -> Result<(), String> {
        if let Some(handle) = self.nvst_receive.take() {
            handle.stop();
        }
        self.skip_webrtc_video.store(false, Ordering::SeqCst);
        self.video_liveness.set_stats_overlay_visible(false);
        self.render_state.stop_external_renderer_window_guard();
        self.render_state.destroy_internal_renderer();
        #[cfg(target_os = "windows")]
        if let Some(mut bridge) = self.native_window_input_bridge.take() {
            bridge.stop();
        }
        self.input_state.stop_heartbeat();
        self.video_liveness.stop();
        self.pipeline
            .set_state(gst::State::Null)
            .map(|_| ())
            .map_err(|error| format!("Failed to stop GStreamer pipeline: {error:?}"))
    }
}

pub(crate) fn resolve_gstreamer_stun_server(ice_servers: &[IceServer]) -> String {
    ice_servers
        .iter()
        .flat_map(|server| server.urls.iter())
        .find_map(|url| {
            let url = url.trim();
            if url.starts_with("stun://") {
                Some(url.to_owned())
            } else {
                url.strip_prefix("stun:")
                    .map(|endpoint| format!("stun://{endpoint}"))
            }
        })
        .unwrap_or_else(|| DEFAULT_GFN_STUN_SERVER.to_owned())
}

fn nice_stream_from_ice_transport(
    transport: &gst_webrtc::WebRTCICETransport,
) -> Option<ActualNiceIceStream> {
    if transport.type_().name() != "GstWebRTCNiceTransport" {
        return None;
    }

    unsafe {
        let transport_ptr = transport.as_ptr() as *mut GstWebRTCNiceTransportCompat;
        if transport_ptr.is_null() {
            return None;
        }

        let stream_ptr = (*transport_ptr).stream;
        if stream_ptr.is_null() {
            return None;
        }

        Some(ActualNiceIceStream {
            ptr: stream_ptr,
            stream_id: (*stream_ptr).stream_id,
        })
    }
}

pub(crate) fn init_gstreamer() -> Result<(), String> {
    gst::init().map_err(|error| format!("Failed to initialize GStreamer: {error}"))
}

pub(crate) fn set_property_if_supported<T: Into<glib::Value>>(
    element: &gst::Element,
    name: &str,
    value: T,
) {
    if let Some(property) = element.find_property(name) {
        if !property.flags().contains(glib::ParamFlags::WRITABLE) {
            return;
        }

        let value = value.into();
        let value_type = value.type_();
        let property_type = property.value_type();
        if value_type == property_type || value_type.is_a(property_type) {
            element.set_property_from_value(name, &value);
        }
    }
}

pub(crate) fn set_property_from_str_if_supported(element: &gst::Element, name: &str, value: &str) {
    if element.find_property(name).is_some() {
        element.set_property_from_str(name, value);
    }
}

pub(crate) fn configure_webrtc_low_latency(webrtc: &gst::Element) {
    set_property_if_supported(webrtc, "latency", WEBRTC_LATENCY_MS);
}

pub(crate) fn configure_queue_for_low_latency(element: &gst::Element, media_label: &str) {
    let max_buffers = if media_label == "video" {
        VIDEO_QUEUE_MAX_BUFFERS
    } else {
        AUDIO_QUEUE_MAX_BUFFERS
    };

    configure_queue(element, max_buffers, true);
}

pub(crate) fn configure_queue(element: &gst::Element, max_buffers: u32, leaky_downstream: bool) {
    set_property_if_supported(element, "max-size-buffers", max_buffers);
    set_property_if_supported(element, "max-size-bytes", 0u32);
    set_property_if_supported(element, "max-size-time", 0u64);
    if leaky_downstream {
        set_property_from_str_if_supported(element, "leaky", "downstream");
    } else {
        set_property_from_str_if_supported(element, "leaky", "no");
    }
}

pub(crate) fn configure_sink_for_low_latency(element: &gst::Element) {
    // GFN-aligned present: never clock-sync or QoS-throttle the sink. Latency
    // comes from decode + a depth-1 leaky post-decode queue + optional present
    // limiter, not from GstBaseSink pacing.
    set_property_if_supported(element, "sync", false);
    set_property_if_supported(element, "async", false);
    set_property_if_supported(element, "qos", false);
    set_property_if_supported(element, "max-lateness", -1i64);
    set_property_if_supported(element, "processing-deadline", 0u64);
    set_property_if_supported(element, "render-delay", 0u64);
    set_property_if_supported(element, "throttle-time", 0u64);
    set_property_if_supported(element, "enable-last-sample", false);
    set_property_if_supported(element, "show-preroll-frame", false);
    set_property_if_supported(element, "redraw-on-update", true);
    set_property_if_supported(element, "force-aspect-ratio", true);
}

/// Configure d3d11/d3d12videosink for low-latency Internal/External present.
///
/// GStreamer docs: `fullscreen` is ignored unless `fullscreen-toggle-mode`
/// includes `property`. Internal always keeps exclusive fullscreen off (caller
/// passes `d3d_fullscreen_sink=false`); External + Cloud G-Sync may enable it.
pub(crate) fn configure_d3d_video_sink(element: &gst::Element, d3d_fullscreen_sink: bool) {
    configure_sink_for_low_latency(element);
    // d3d12 only: attaching the swapchain directly to an external HWND can turn
    // a present stall into upstream decode backpressure on the child-surface path.
    set_property_if_supported(element, "direct-swapchain", false);
    set_property_if_supported(element, "error-on-closed", false);
    // RawInput owns mouse/keyboard; do not let the sink emit GstNavigation events.
    set_property_if_supported(element, "enable-navigation-events", false);
    set_property_if_supported(element, "fullscreen-on-alt-enter", false);
    if d3d_fullscreen_sink {
        set_property_from_str_if_supported(element, "fullscreen-toggle-mode", "property");
        set_property_if_supported(element, "fullscreen", true);
    } else {
        set_property_from_str_if_supported(element, "fullscreen-toggle-mode", "none");
        set_property_if_supported(element, "fullscreen", false);
    }
}

pub(crate) fn configure_stats_overlay_element(element: &gst::Element) {
    set_property_if_supported(element, "visible", false);
    set_property_if_supported(element, "text", "");
    set_property_if_supported(element, "auto-resize", true);
    set_property_if_supported(element, "layout-x", 0.018f64);
    set_property_if_supported(element, "layout-y", 0.018f64);
    set_property_if_supported(element, "layout-width", 0.55f64);
    set_property_if_supported(element, "layout-height", 0.18f64);
    set_property_if_supported(element, "font-family", "Cascadia Mono");
    set_property_if_supported(element, "font-size", 18f32);
    set_property_from_str_if_supported(element, "text-alignment", "leading");
    set_property_from_str_if_supported(element, "paragraph-alignment", "near");
    set_property_if_supported(element, "foreground-color", 0xF2FF_FFFFu32);
    set_property_if_supported(element, "outline-color", 0xD000_0000u32);
}

pub(crate) fn wait_for_promise(promise: &gst::Promise, operation: &str) -> Result<(), String> {
    match promise.wait() {
        gst::PromiseResult::Replied => {
            if let Some(reply) = promise.get_reply() {
                if reply.has_field("error") {
                    return Err(format!(
                        "GStreamer promise returned an error during {operation}: {}",
                        describe_structure(reply)
                    ));
                }
            }
            Ok(())
        }
        gst::PromiseResult::Interrupted => {
            Err(format!("GStreamer promise interrupted during {operation}."))
        }
        gst::PromiseResult::Expired => {
            Err(format!("GStreamer promise expired during {operation}."))
        }
        gst::PromiseResult::Pending => Err(format!(
            "GStreamer promise still pending during {operation}."
        )),
        other => Err(format!(
            "GStreamer promise failed during {operation}: {other:?}"
        )),
    }
}

pub(crate) fn describe_structure(structure: &gst::StructureRef) -> String {
    let fields = structure
        .iter()
        .map(|(name, value)| {
            let rendered = value
                .get::<&glib::Error>()
                .map(|error| format!("{error:?}"))
                .unwrap_or_else(|_| format!("{value:?}"));
            format!("{}={rendered}", name.as_str())
        })
        .collect::<Vec<_>>();

    format!("{} {{{}}}", structure.name().as_str(), fields.join(", "))
}

fn wire_local_ice_events(
    webrtc: &gst::Element,
    event_sender: Option<Sender<Event>>,
) -> Result<(), String> {
    let Some(event_sender) = event_sender else {
        return Ok(());
    };

    webrtc.connect("on-ice-candidate", false, move |values| {
        let sdp_m_line_index = values.get(1).and_then(glib_value_to_u32).unwrap_or(0);
        let candidate = values
            .get(2)
            .and_then(|value| value.get::<String>().ok())
            .unwrap_or_default();

        if !candidate.trim().is_empty() {
            let _ = event_sender.send(Event::LocalIce {
                candidate: IceCandidatePayload {
                    candidate,
                    sdp_mid: Some(sdp_m_line_index.to_string()),
                    sdp_m_line_index: Some(sdp_m_line_index),
                    username_fragment: None,
                },
            });
        }

        None
    });
    Ok(())
}

fn glib_value_to_u32(value: &glib::Value) -> Option<u32> {
    let value_type = value.type_();
    if value_type == u32::static_type() {
        return value.get::<u32>().ok();
    }
    if value_type == i32::static_type() {
        return value
            .get::<i32>()
            .ok()
            .and_then(|value| u32::try_from(value).ok());
    }
    if value_type == u64::static_type() {
        return value
            .get::<u64>()
            .ok()
            .and_then(|value| u32::try_from(value).ok());
    }
    if value_type == i64::static_type() {
        return value
            .get::<i64>()
            .ok()
            .and_then(|value| u32::try_from(value).ok());
    }
    None
}

fn wire_webrtc_state_events(webrtc: &gst::Element, event_sender: Option<Sender<Event>>) {
    wire_webrtc_property_event(
        webrtc,
        event_sender.clone(),
        "ice-connection-state",
        "ICE connection state",
    );
    wire_webrtc_property_event(
        webrtc,
        event_sender.clone(),
        "ice-gathering-state",
        "ICE gathering state",
    );
    wire_webrtc_property_event(
        webrtc,
        event_sender,
        "connection-state",
        "peer connection state",
    );
}

fn wire_webrtc_property_event(
    webrtc: &gst::Element,
    event_sender: Option<Sender<Event>>,
    property_name: &'static str,
    label: &'static str,
) {
    if event_sender.is_none() || webrtc.find_property(property_name).is_none() {
        return;
    }

    webrtc.connect_notify(Some(property_name), move |element, _| {
        let value = element.property_value(property_name);
        send_log(
            &event_sender,
            "debug",
            format!("GStreamer WebRTC {label}: {value:?}."),
        );
    });
}

fn start_gstreamer_bus_diagnostics(
    pipeline: &gst::Pipeline,
    event_sender: Option<Sender<Event>>,
    stop: Arc<AtomicBool>,
    video_liveness: VideoLivenessMonitor,
) {
    let Some(bus) = pipeline.bus() else {
        send_log(
            &event_sender,
            "warn",
            "GStreamer pipeline has no bus; native diagnostics will be limited.".to_owned(),
        );
        return;
    };

    thread::spawn(move || {
        while !stop.load(Ordering::SeqCst) {
            let Some(message) = bus.timed_pop_filtered(
                gst::ClockTime::from_mseconds(250),
                &[
                    gst::MessageType::Error,
                    gst::MessageType::Warning,
                    gst::MessageType::Qos,
                    gst::MessageType::Latency,
                    gst::MessageType::StateChanged,
                    gst::MessageType::Eos,
                ],
            ) else {
                continue;
            };

            match message.view() {
                gst::MessageView::Error(error) => send_log(
                    &event_sender,
                    "error",
                    format!(
                        "GStreamer bus error from {}: {}; debug={:?}.",
                        message_src_name(&message),
                        error.error(),
                        error.debug()
                    ),
                ),
                gst::MessageView::Warning(warning) => send_log(
                    &event_sender,
                    "warn",
                    format!(
                        "GStreamer bus warning from {}: {}; debug={:?}.",
                        message_src_name(&message),
                        warning.error(),
                        warning.debug()
                    ),
                ),
                gst::MessageView::Qos(_) => send_log(
                    &event_sender,
                    "debug",
                    format!(
                        "GStreamer bus QoS from {}: {}.",
                        message_src_name(&message),
                        message_structure_summary(&message)
                    ),
                ),
                gst::MessageView::Latency(_) => send_log(
                    &event_sender,
                    "debug",
                    format!(
                        "GStreamer bus latency update from {}.",
                        message_src_name(&message)
                    ),
                ),
                gst::MessageView::StateChanged(state) => {
                    if message
                        .src()
                        .and_then(|src| src.clone().downcast::<gst::Pipeline>().ok())
                        .is_some()
                    {
                        send_log(
                            &event_sender,
                            "debug",
                            format!(
                                "GStreamer pipeline state changed: {:?} -> {:?} pending {:?}.",
                                state.old(),
                                state.current(),
                                state.pending()
                            ),
                        );
                        video_liveness.record_transition(
                            "pipeline-state-change",
                            "pipeline",
                            Some(format!("{:?}", state.old())),
                            Some(format!("{:?}", state.current())),
                            None,
                            None,
                            None,
                            None,
                            &event_sender,
                        );
                    }
                }
                gst::MessageView::Eos(_) => send_log(
                    &event_sender,
                    "warn",
                    format!("GStreamer bus EOS from {}.", message_src_name(&message)),
                ),
                _ => {}
            }
        }
    });
}

fn message_src_name(message: &gst::Message) -> String {
    message
        .src()
        .map(|src| src.path_string().to_string())
        .unwrap_or_else(|| "unknown".to_owned())
}

fn message_structure_summary(message: &gst::Message) -> String {
    message
        .structure()
        .map(|structure| structure.to_string())
        .unwrap_or_else(|| "no structure".to_owned())
}

fn wire_incoming_media_sink(
    pipeline: &gst::Pipeline,
    webrtc: &gst::Element,
    event_sender: Option<Sender<Event>>,
    render_state: GstreamerRenderState,
    present_max_fps: Arc<AtomicU32>,
    d3d_fullscreen_sink: Arc<AtomicBool>,
    skip_webrtc_video: Arc<AtomicBool>,
    video_liveness: VideoLivenessMonitor,
) {
    let pipeline = pipeline.downgrade();
    let streaming_reported = Arc::new(AtomicBool::new(false));
    webrtc.connect_pad_added(move |_webrtc, src_pad| {
        let Some(pipeline) = pipeline.upgrade() else {
            return;
        };
        let event_sender = event_sender.clone();

        if !is_rtp_pad(src_pad) {
            send_log(
                &event_sender,
                "debug",
                format!(
                    "Ignoring non-RTP WebRTC pad with caps {:?}.",
                    pad_caps_name(src_pad)
                ),
            );
            return;
        }

        if let Some(encoding) = rtp_video_encoding(src_pad) {
            if skip_webrtc_video.load(Ordering::SeqCst) {
                send_log(
                    &event_sender,
                    "info",
                    format!(
                        "Ignoring WebRTC RTP video pad ({encoding}); NVST classic UDP owns video."
                    ),
                );
                if let Err(error) =
                    link_decoded_media_to_fakesink(&pipeline, src_pad, "ignored webrtc video")
                {
                    send_log(&event_sender, "debug", error);
                }
                return;
            }
            match link_rtp_video_pad(
                &pipeline,
                src_pad,
                &encoding,
                &render_state,
                &event_sender,
                &streaming_reported,
                present_max_fps.clone(),
                d3d_fullscreen_sink.load(Ordering::SeqCst),
                video_liveness.clone(),
            ) {
                Ok(()) => return,
                Err(error) => send_log(
                    &event_sender,
                    "warn",
                    format!("{error}; falling back to decodebin."),
                ),
            }
        }

        let decodebin = match make_element("decodebin") {
            Ok(decodebin) => decodebin,
            Err(error) => {
                send_log(&event_sender, "warn", error);
                return;
            }
        };

        let decode_pipeline = pipeline.downgrade();
        let decode_sender = event_sender.clone();
        let decode_render_state = render_state.clone();
        let decode_streaming_reported = streaming_reported.clone();
        let decode_video_liveness = video_liveness.clone();
        decodebin.connect_pad_added(move |_decodebin, decoded_pad| {
            let Some(pipeline) = decode_pipeline.upgrade() else {
                return;
            };
            let media_kind = decoded_media_kind(decoded_pad);
            if let Err(error) = link_decoded_media_pad(
                &pipeline,
                decoded_pad,
                &decode_render_state,
                &decode_sender,
                &decode_streaming_reported,
                &decode_video_liveness,
            ) {
                send_log(&decode_sender, "warn", error);
                if let Err(fallback_error) =
                    link_decoded_media_to_fakesink(&pipeline, decoded_pad, "decoded media fallback")
                {
                    send_log(&decode_sender, "warn", fallback_error);
                }
                return;
            }

            send_log(
                &decode_sender,
                "info",
                format!(
                    "Linked decoded {} stream to native sink chain.",
                    media_kind.label()
                ),
            );
        });

        if let Err(error) = pipeline.add(&decodebin) {
            send_log(
                &event_sender,
                "warn",
                format!("Failed to add decodebin: {error}"),
            );
            return;
        }
        if let Err(error) = decodebin.sync_state_with_parent() {
            send_log(
                &event_sender,
                "warn",
                format!("Failed to sync decodebin state: {error}"),
            );
            return;
        }

        let Some(sink_pad) = decodebin.static_pad("sink") else {
            send_log(
                &event_sender,
                "warn",
                "decodebin has no sink pad.".to_owned(),
            );
            return;
        };
        if let Err(error) = src_pad.link(&sink_pad) {
            send_log(
                &event_sender,
                "warn",
                format!("Failed to link WebRTC RTP pad to decodebin: {error:?}"),
            );
        } else if rtp_video_encoding(src_pad).is_some() {
            video_liveness.set_rtp_video_src_pad(src_pad);
        }
    });
}

impl DecodedMediaKind {
    fn label(self) -> &'static str {
        match self {
            Self::Audio => "audio",
            Self::Video => "video",
            Self::Unknown => "unknown",
        }
    }
}

fn is_rtp_pad(pad: &gst::Pad) -> bool {
    pad_caps_name(pad)
        .as_deref()
        .is_some_and(|name| name == "application/x-rtp")
}

fn pad_caps_name(pad: &gst::Pad) -> Option<String> {
    let caps = pad.current_caps().unwrap_or_else(|| pad.query_caps(None));
    caps.structure(0)
        .map(|structure| structure.name().to_string())
}

fn decoded_media_kind(pad: &gst::Pad) -> DecodedMediaKind {
    match pad_caps_name(pad).as_deref() {
        Some(name) if name.starts_with("video/") => DecodedMediaKind::Video,
        Some(name) if name.starts_with("audio/") => DecodedMediaKind::Audio,
        _ => DecodedMediaKind::Unknown,
    }
}

fn rtp_video_encoding(pad: &gst::Pad) -> Option<String> {
    let caps = pad.current_caps().unwrap_or_else(|| pad.query_caps(None));
    let structure = caps.structure(0)?;
    if structure.name() != "application/x-rtp" {
        return None;
    }

    let media = structure.get::<String>("media").ok()?;
    if media != "video" {
        return None;
    }

    structure
        .get::<String>("encoding-name")
        .ok()
        .map(|encoding| encoding.to_ascii_uppercase())
}

fn rtp_video_depayloader_factory(codec: &str) -> Option<&'static str> {
    match codec {
        "H265" | "HEVC" => Some("rtph265depay"),
        "H264" => Some("rtph264depay"),
        "AV1" => Some("rtpav1depay"),
        _ => None,
    }
}

fn rtp_video_parser_factory(codec: &str) -> Option<&'static str> {
    match codec {
        "H265" | "HEVC" => Some("h265parse"),
        "H264" => Some("h264parse"),
        "AV1" => Some("av1parse"),
        _ => None,
    }
}

pub(crate) fn rtp_video_chain_definition(
    encoding: &str,
    video_api: RtpVideoApi,
) -> Option<Vec<RtpVideoChainSpec>> {
    let codec = encoding.to_ascii_uppercase();

    #[cfg(target_os = "windows")]
    if video_api == RtpVideoApi::Vulkan {
        return windows_vulkan_present_chain_definition(codec.as_str());
    }

    let mut specs = vec![
        RtpVideoChainSpec::new(
            rtp_video_depayloader_factory(codec.as_str())?,
            RtpVideoChainRole::Depayloader,
        ),
        RtpVideoChainSpec::new(
            rtp_video_parser_factory(codec.as_str())?,
            RtpVideoChainRole::Parser,
        ),
        RtpVideoChainSpec::new("queue", RtpVideoChainRole::PreDecodeQueue),
        RtpVideoChainSpec::new(
            video_api.decoder_factory(codec.as_str())?,
            RtpVideoChainRole::Decoder,
        ),
    ];

    if let Some(memory_caps) = video_api.memory_caps() {
        specs.push(RtpVideoChainSpec::with_caps(
            "capsfilter",
            RtpVideoChainRole::PostDecodeCapsFilter,
            memory_caps,
        ));
    }
    if let Some(converter) = video_api.post_decode_converter_factory() {
        specs.push(RtpVideoChainSpec::new(
            converter,
            RtpVideoChainRole::PostDecodeConverter,
        ));
    }
    if let Some(overlay) = video_api.stats_overlay_factory() {
        specs.push(RtpVideoChainSpec::new(
            overlay,
            RtpVideoChainRole::StatsOverlay,
        ));
    }
    specs.push(RtpVideoChainSpec::new(
        "queue",
        RtpVideoChainRole::PostDecodeQueue,
    ));
    specs.push(RtpVideoChainSpec::new(
        video_api.sink_factory(),
        RtpVideoChainRole::Sink,
    ));

    Some(specs)
}

/// Windows Vulkan path.
///
/// Electron Internal hole-punch only composites DXGI swapchains on the child HWND.
/// A Win32 Vulkan surface on that HWND (or a GSTVULKAN child of it) presents black
/// even though vulkansink reports rendered frames. So:
/// - Internal: DXVA decode + `d3d12videosink` (D3D11 fallback; visible in Electron)
/// - External: DXVA decode + convert/upload + `vulkansink` (true Vulkan present)
///
/// Native `vulkanh264dec` currently access-violates under NVIDIA Windows drivers.
#[cfg(target_os = "windows")]
fn windows_vulkan_present_chain_definition(codec: &str) -> Option<Vec<RtpVideoChainSpec>> {
    if use_internal_renderer() {
        windows_vulkan_internal_present_chain_definition(codec)
    } else {
        windows_vulkan_external_present_chain_definition(codec)
    }
}

/// Internal Electron path: DXVA + D3D12 present (D3D11 fallback; DXGI hole-punch).
#[cfg(target_os = "windows")]
fn windows_vulkan_internal_present_chain_definition(codec: &str) -> Option<Vec<RtpVideoChainSpec>> {
    let decoder = RtpVideoApi::Vulkan.decoder_factory(codec)?;
    let prefer_d3d12 = decoder.starts_with("d3d12");
    let sink = if prefer_d3d12 {
        "d3d12videosink"
    } else {
        "d3d11videosink"
    };
    let memory_api = if prefer_d3d12 {
        RtpVideoApi::D3D12
    } else {
        RtpVideoApi::D3D11
    };
    let mut specs = vec![
        RtpVideoChainSpec::new(
            rtp_video_depayloader_factory(codec)?,
            RtpVideoChainRole::Depayloader,
        ),
        RtpVideoChainSpec::new(rtp_video_parser_factory(codec)?, RtpVideoChainRole::Parser),
        RtpVideoChainSpec::new("queue", RtpVideoChainRole::PreDecodeQueue),
        RtpVideoChainSpec::new(decoder, RtpVideoChainRole::Decoder),
    ];
    if let Some(memory_caps) = memory_api.memory_caps() {
        specs.push(RtpVideoChainSpec::with_caps(
            "capsfilter",
            RtpVideoChainRole::PostDecodeCapsFilter,
            memory_caps,
        ));
    }
    specs.push(RtpVideoChainSpec::new(
        "dwritetextoverlay",
        RtpVideoChainRole::StatsOverlay,
    ));
    specs.push(RtpVideoChainSpec::new(
        "queue",
        RtpVideoChainRole::PostDecodeQueue,
    ));
    specs.push(RtpVideoChainSpec::new(sink, RtpVideoChainRole::Sink));
    Some(specs)
}

/// External / capability path: DXVA + vulkanupload + vulkansink.
#[cfg(target_os = "windows")]
fn windows_vulkan_external_present_chain_definition(codec: &str) -> Option<Vec<RtpVideoChainSpec>> {
    let decoder = RtpVideoApi::Vulkan.decoder_factory(codec)?;
    Some(vec![
        RtpVideoChainSpec::new(
            rtp_video_depayloader_factory(codec)?,
            RtpVideoChainRole::Depayloader,
        ),
        RtpVideoChainSpec::new(rtp_video_parser_factory(codec)?, RtpVideoChainRole::Parser),
        RtpVideoChainSpec::new("queue", RtpVideoChainRole::PreDecodeQueue),
        RtpVideoChainSpec::new(decoder, RtpVideoChainRole::Decoder),
        // Composite diagnostics while frames are still in the DXVA/D3D path.
        // dwritetextoverlay cannot consume VulkanImage memory after upload.
        RtpVideoChainSpec::new("dwritetextoverlay", RtpVideoChainRole::StatsOverlay),
        RtpVideoChainSpec::new("d3d11download", RtpVideoChainRole::PostDecodeConverter),
        RtpVideoChainSpec::new("videoconvert", RtpVideoChainRole::PostDecodeConverter),
        RtpVideoChainSpec::with_caps(
            "capsfilter",
            RtpVideoChainRole::PostDecodeCapsFilter,
            "video/x-raw,format=RGBA",
        ),
        RtpVideoChainSpec::new("vulkanupload", RtpVideoChainRole::PostDecodeConverter),
        RtpVideoChainSpec::new("queue", RtpVideoChainRole::PostDecodeQueue),
        RtpVideoChainSpec::new(RtpVideoApi::Vulkan.sink_factory(), RtpVideoChainRole::Sink),
    ])
}

fn preferred_rtp_video_apis(requested_fps: Option<u32>) -> Vec<RtpVideoApi> {
    let requested = requested_video_backend();
    preferred_rtp_video_apis_for(requested.as_str(), requested_fps)
}

pub(crate) fn preferred_rtp_video_apis_for(
    requested: &str,
    requested_fps: Option<u32>,
) -> Vec<RtpVideoApi> {
    match requested {
        "d3d11" => vec![RtpVideoApi::D3D11],
        "d3d12" => vec![RtpVideoApi::D3D12],
        "videotoolbox" | "vt" => vec![RtpVideoApi::VideoToolbox],
        "vaapi" | "va" => vec![RtpVideoApi::Vaapi],
        "v4l2" | "v4l2stateless" => vec![RtpVideoApi::V4L2],
        "vulkan" | "vk" => vec![RtpVideoApi::Vulkan],
        "software" | "sw" => vec![RtpVideoApi::Software],
        _ => default_rtp_video_api_priority(requested_fps),
    }
}

pub(crate) fn effective_present_max_fps(
    configured_present_max_fps: u32,
    requested_fps: Option<u32>,
    video_api: RtpVideoApi,
    display_hz: Option<u32>,
) -> u32 {
    if configured_present_max_fps == PRESENT_LIMITER_VRR_SENTINEL {
        if !matches!(video_api, RtpVideoApi::D3D11 | RtpVideoApi::D3D12) {
            return 0;
        }
        return requested_fps
            .filter(|fps| *fps > 0)
            .map(|fps| vrr_present_max_fps(fps, display_hz))
            .unwrap_or(0);
    }

    if configured_present_max_fps != PRESENT_LIMITER_AUTO_SENTINEL {
        return configured_present_max_fps;
    }

    // D3D11/D3D12 present (and Internal Vulkan→D3D) need the auto limiter so
    // stream fps above display Hz does not stall the DXGI present path.
    if !matches!(video_api, RtpVideoApi::D3D11 | RtpVideoApi::D3D12)
        && !(cfg!(target_os = "windows")
            && video_api == RtpVideoApi::Vulkan
            && use_internal_renderer())
    {
        return 0;
    }

    requested_fps
        .filter(|fps| *fps > 0)
        .map(|fps| automatic_present_max_fps(fps, display_hz))
        .unwrap_or(0)
}

pub(crate) fn default_rtp_video_api_priority(requested_fps: Option<u32>) -> Vec<RtpVideoApi> {
    #[cfg(target_os = "windows")]
    {
        if should_prefer_d3d12_for_high_fps(requested_fps) {
            return vec![
                RtpVideoApi::D3D12,
                RtpVideoApi::D3D11,
                RtpVideoApi::Software,
            ];
        }
        vec![
            RtpVideoApi::D3D11,
            RtpVideoApi::D3D12,
            RtpVideoApi::Software,
        ]
    }
    #[cfg(target_os = "macos")]
    {
        let _ = requested_fps;
        vec![RtpVideoApi::VideoToolbox, RtpVideoApi::Software]
    }
    #[cfg(all(target_os = "linux", target_arch = "aarch64"))]
    {
        let _ = requested_fps;
        vec![
            RtpVideoApi::V4L2,
            RtpVideoApi::Vaapi,
            RtpVideoApi::Vulkan,
            RtpVideoApi::Software,
        ]
    }
    #[cfg(all(target_os = "linux", not(target_arch = "aarch64")))]
    {
        let _ = requested_fps;
        vec![
            RtpVideoApi::Vaapi,
            RtpVideoApi::Vulkan,
            RtpVideoApi::V4L2,
            RtpVideoApi::Software,
        ]
    }
    #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
    {
        let _ = requested_fps;
        vec![RtpVideoApi::Software]
    }
}

fn should_prefer_d3d12_for_high_fps(requested_fps: Option<u32>) -> bool {
    requested_fps.is_some_and(|fps| fps >= 200)
}

fn rtp_video_chain_specs(
    encoding: &str,
    requested_fps: Option<u32>,
) -> Option<(RtpVideoApi, Vec<RtpVideoChainSpec>)> {
    preferred_rtp_video_apis(requested_fps)
        .into_iter()
        .find_map(|video_api| {
            let codec = encoding.to_ascii_uppercase();
            let decoder = select_decoder_factory(video_api, codec.as_str())?;
            let sink = select_sink_factory(video_api)?;
            let mut specs = rtp_video_chain_definition(encoding, video_api)?;
            for spec in &mut specs {
                if spec.role == RtpVideoChainRole::Decoder {
                    spec.factory = decoder;
                } else if spec.role == RtpVideoChainRole::Sink {
                    spec.factory = sink;
                }
            }
            align_windows_vulkan_download_factory(&mut specs, decoder);
            align_windows_vulkan_internal_present(&mut specs, decoder);
            insert_requested_fps_capssetter(&mut specs, requested_fps);
            specs.retain(|spec| {
                spec.role != RtpVideoChainRole::StatsOverlay
                    || gst::ElementFactory::find(spec.factory).is_some()
            });
            required_video_chain_elements_available(&specs).then_some((video_api, specs))
        })
}

fn align_windows_vulkan_download_factory(specs: &mut Vec<RtpVideoChainSpec>, decoder: &str) {
    #[cfg(target_os = "windows")]
    {
        let download = if decoder.starts_with("d3d12") {
            Some("d3d12download")
        } else if decoder.starts_with("d3d11") {
            Some("d3d11download")
        } else if decoder.starts_with("nv") {
            // NVDEC Windows outputs system memory in our bundle; skip D3D download.
            None
        } else {
            return;
        };

        match download {
            Some(factory) => {
                if let Some(spec) = specs.iter_mut().find(|spec| {
                    spec.role == RtpVideoChainRole::PostDecodeConverter
                        && (spec.factory == "d3d11download" || spec.factory == "d3d12download")
                }) {
                    spec.factory = factory;
                }
            }
            None => {
                specs.retain(|spec| {
                    !(spec.role == RtpVideoChainRole::PostDecodeConverter
                        && (spec.factory == "d3d11download" || spec.factory == "d3d12download"))
                });
            }
        }
    }
    #[cfg(not(target_os = "windows"))]
    {
        let _ = (specs, decoder);
    }
}

/// Keep Internal Vulkan→D3D present matched to the selected DXVA decoder family.
#[cfg(target_os = "windows")]
fn align_windows_vulkan_internal_present(specs: &mut Vec<RtpVideoChainSpec>, decoder: &str) {
    if !use_internal_renderer() {
        return;
    }
    let has_d3d_present = specs.iter().any(|spec| {
        spec.role == RtpVideoChainRole::Sink
            && (spec.factory == "d3d11videosink" || spec.factory == "d3d12videosink")
    });
    if !has_d3d_present {
        return;
    }

    let (sink, memory_caps) = if decoder.starts_with("d3d12")
        && gst::ElementFactory::find("d3d12videosink").is_some()
    {
        ("d3d12videosink", RtpVideoApi::D3D12.memory_caps())
    } else if decoder.starts_with("d3d11")
        && gst::ElementFactory::find("d3d11videosink").is_some()
    {
        ("d3d11videosink", RtpVideoApi::D3D11.memory_caps())
    } else {
        return;
    };

    if let Some(spec) = specs
        .iter_mut()
        .find(|spec| spec.role == RtpVideoChainRole::Sink)
    {
        spec.factory = sink;
    }
    if let Some(spec) = specs
        .iter_mut()
        .find(|spec| spec.role == RtpVideoChainRole::PostDecodeCapsFilter)
    {
        if let Some(caps) = memory_caps {
            spec.caps = Some(caps.to_owned());
        }
    }
}

#[cfg(not(target_os = "windows"))]
fn align_windows_vulkan_internal_present(_specs: &mut Vec<RtpVideoChainSpec>, _decoder: &str) {}

fn insert_requested_fps_capssetter(specs: &mut Vec<RtpVideoChainSpec>, requested_fps: Option<u32>) {
    let Some(fps) = requested_fps.filter(|fps| *fps > 0) else {
        return;
    };
    if gst::ElementFactory::find("capssetter").is_none() {
        return;
    }
    // Windows Vulkan hybrid / D3D present: forcing plain video/x-raw onto a D3D
    // memory pad breaks caps negotiation.
    if specs.iter().any(|spec| {
        matches!(
            spec.factory,
            "d3d11download"
                | "d3d12download"
                | "vulkanupload"
                | "d3d11videosink"
                | "d3d12videosink"
                | "d3d11h264dec"
                | "d3d11h265dec"
                | "d3d11av1dec"
                | "d3d12h264dec"
                | "d3d12h265dec"
                | "d3d12av1dec"
        )
    }) {
        return;
    }
    let Some(decoder_index) = specs
        .iter()
        .position(|spec| spec.role == RtpVideoChainRole::Decoder)
    else {
        return;
    };

    specs.insert(
        decoder_index + 1,
        RtpVideoChainSpec::with_caps(
            "capssetter",
            RtpVideoChainRole::PostDecodeRateSetter,
            format!("video/x-raw,framerate=(fraction){fps}/1"),
        ),
    );
}

fn select_decoder_factory(video_api: RtpVideoApi, codec: &str) -> Option<&'static str> {
    let primary = video_api.decoder_factory(codec)?;
    std::iter::once(primary)
        .chain(video_api.fallback_decoder_factories(codec).iter().copied())
        .find(|factory| gst::ElementFactory::find(factory).is_some())
}

fn select_sink_factory(video_api: RtpVideoApi) -> Option<&'static str> {
    // Internal Linux: never pick waylandsink for the X11 child overlay path.
    #[cfg(target_os = "linux")]
    if use_internal_renderer() {
        let internal = video_api.internal_x11_sink_candidates();
        if let Some(factory) = internal
            .iter()
            .copied()
            .find(|factory| gst::ElementFactory::find(factory).is_some())
        {
            return Some(factory);
        }
    }

    // Internal Windows + Vulkan: Electron hole-punch cannot composite Win32 Vulkan
    // swapchains; present with D3D12 (D3D11 fallback) VideoOverlay instead.
    #[cfg(target_os = "windows")]
    if use_internal_renderer() && video_api == RtpVideoApi::Vulkan {
        return ["d3d12videosink", "d3d11videosink"]
            .into_iter()
            .find(|factory| gst::ElementFactory::find(factory).is_some());
    }

    select_capability_sink_factory(video_api)
}

/// Sink advertised in capabilities / used when not overriding for Internal present.
fn select_capability_sink_factory(video_api: RtpVideoApi) -> Option<&'static str> {
    std::iter::once(video_api.sink_factory())
        .chain(video_api.sink_fallback_factories().iter().copied())
        .find(|factory| gst::ElementFactory::find(factory).is_some())
}

fn required_video_chain_elements_available(specs: &[RtpVideoChainSpec]) -> bool {
    specs
        .iter()
        .all(|spec| gst::ElementFactory::find(spec.factory).is_some())
}

fn all_rtp_video_apis() -> &'static [RtpVideoApi] {
    &[
        RtpVideoApi::D3D12,
        RtpVideoApi::D3D11,
        RtpVideoApi::VideoToolbox,
        RtpVideoApi::Vaapi,
        RtpVideoApi::V4L2,
        RtpVideoApi::Vulkan,
        RtpVideoApi::Software,
    ]
}

fn all_video_codec_labels() -> &'static [&'static str] {
    &["H264", "H265", "AV1"]
}

pub(crate) fn current_platform_label() -> &'static str {
    #[cfg(target_os = "windows")]
    {
        "windows"
    }
    #[cfg(target_os = "macos")]
    {
        "macos"
    }
    #[cfg(target_os = "linux")]
    {
        "linux"
    }
    #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
    {
        "other"
    }
}

fn backend_runs_on_current_platform(video_api: RtpVideoApi) -> bool {
    backend_runs_on_platform(video_api, current_platform_label())
}

pub(crate) fn backend_runs_on_platform(video_api: RtpVideoApi, platform: &str) -> bool {
    match video_api {
        RtpVideoApi::D3D11 | RtpVideoApi::D3D12 => platform == "windows",
        RtpVideoApi::VideoToolbox => platform == "macos",
        RtpVideoApi::Vaapi | RtpVideoApi::V4L2 => platform == "linux",
        RtpVideoApi::Vulkan => matches!(platform, "windows" | "linux"),
        RtpVideoApi::Software => true,
    }
}

pub(crate) fn native_video_backend_capabilities() -> Vec<NativeVideoBackendCapability> {
    all_rtp_video_apis()
        .iter()
        .copied()
        .map(native_video_backend_capability)
        .collect()
}

fn native_video_backend_capability(video_api: RtpVideoApi) -> NativeVideoBackendCapability {
    let platform_supported = backend_runs_on_current_platform(video_api);
    // Advertise the true API sink (vulkansink). Internal Windows Vulkan may present
    // via d3d12/d3d11videosink at session time for Electron hole-punch compatibility.
    let sink_factory = platform_supported
        .then(|| select_capability_sink_factory(video_api))
        .flatten();
    let codecs = all_video_codec_labels()
        .iter()
        .map(|codec| {
            native_video_codec_capability(video_api, codec, platform_supported, sink_factory)
        })
        .collect::<Vec<_>>();
    let available =
        platform_supported && sink_factory.is_some() && codecs.iter().any(|codec| codec.available);
    let reason = if !platform_supported {
        Some(format!(
            "{} is a {} backend and does not run on {}.",
            video_api.label(),
            video_api.platform(),
            current_platform_label()
        ))
    } else if sink_factory.is_none() {
        Some(format!(
            "{} sink is unavailable; install the platform GStreamer video sink plugins.",
            video_api.label()
        ))
    } else if !available {
        Some(format!(
            "{} decoders are unavailable for H.264, H.265, and AV1.",
            video_api.label()
        ))
    } else {
        None
    };

    NativeVideoBackendCapability {
        backend: video_api.capability_id().to_owned(),
        platform: video_api.platform().to_owned(),
        codecs,
        zero_copy_modes: zero_copy_modes_for_backend(video_api),
        sink: sink_factory.map(str::to_owned),
        available,
        reason,
    }
}

fn native_video_codec_capability(
    video_api: RtpVideoApi,
    codec: &str,
    platform_supported: bool,
    sink: Option<&'static str>,
) -> NativeVideoCodecCapability {
    let depayloader = rtp_video_depayloader_factory(codec);
    let parser = rtp_video_parser_factory(codec);
    let decoder = platform_supported
        .then(|| select_decoder_factory(video_api, codec))
        .flatten();
    // Capability checks the External Vulkan present chain so vulkansink/vulkanupload
    // must be present even when Internal sessions present via D3D11.
    let definition = {
        #[cfg(target_os = "windows")]
        {
            if video_api == RtpVideoApi::Vulkan {
                windows_vulkan_external_present_chain_definition(codec)
            } else {
                rtp_video_chain_definition(codec, video_api)
            }
        }
        #[cfg(not(target_os = "windows"))]
        {
            rtp_video_chain_definition(codec, video_api)
        }
    };
    let available = platform_supported
        && sink.is_some()
        && decoder.is_some()
        && depayloader.is_some_and(|factory| gst::ElementFactory::find(factory).is_some())
        && parser.is_some_and(|factory| gst::ElementFactory::find(factory).is_some())
        && definition.is_some_and(|mut specs| {
            for spec in &mut specs {
                if spec.role == RtpVideoChainRole::Decoder {
                    if let Some(decoder) = decoder {
                        spec.factory = decoder;
                    }
                } else if spec.role == RtpVideoChainRole::Sink {
                    if let Some(sink) = sink {
                        spec.factory = sink;
                    }
                }
            }
            specs.retain(|spec| {
                spec.role != RtpVideoChainRole::StatsOverlay
                    || gst::ElementFactory::find(spec.factory).is_some()
            });
            required_video_chain_elements_available(&specs)
        });

    let reason = if !platform_supported {
        Some("Backend is not available on this platform.".to_owned())
    } else if depayloader.is_none() || parser.is_none() {
        Some("RTP depayloader or parser is not mapped for this codec.".to_owned())
    } else if decoder.is_none() {
        Some(format!(
            "{} decoder for {codec} is not installed.",
            video_api.label()
        ))
    } else if sink.is_none() {
        Some(format!(
            "{} video sink is not installed.",
            video_api.label()
        ))
    } else if !available {
        Some("Required GStreamer elements are not all available.".to_owned())
    } else {
        None
    };

    NativeVideoCodecCapability {
        codec: codec.to_ascii_lowercase(),
        available,
        decoder: decoder.map(str::to_owned),
        parser: parser.map(str::to_owned),
        depayloader: depayloader.map(str::to_owned),
        reason,
    }
}

fn zero_copy_modes_for_backend(video_api: RtpVideoApi) -> Vec<String> {
    match video_api {
        RtpVideoApi::D3D11 => vec!["D3D11Memory".to_owned()],
        RtpVideoApi::D3D12 => vec!["D3D12Memory".to_owned()],
        RtpVideoApi::VideoToolbox => vec!["GLMemory".to_owned()],
        RtpVideoApi::Vaapi => vec!["VAMemory".to_owned()],
        // Linux keeps decoded frames as VulkanImage. Windows uses DXVA→upload,
        // so there is no end-to-end VulkanImage zero-copy path yet.
        RtpVideoApi::Vulkan if cfg!(target_os = "windows") => Vec::new(),
        RtpVideoApi::Vulkan => vec!["VulkanImage".to_owned()],
        RtpVideoApi::V4L2 | RtpVideoApi::Software => Vec::new(),
    }
}

fn configure_rtp_video_chain_element(
    element: &gst::Element,
    spec: RtpVideoChainSpec,
    video_api: RtpVideoApi,
    d3d_fullscreen_sink: bool,
) {
    match spec.role {
        RtpVideoChainRole::Depayloader => {
            set_property_if_supported(element, "request-keyframe", true);
            // Hard-waiting after packet loss can freeze the visible frame while RTP is still flowing.
            set_property_if_supported(element, "wait-for-keyframe", false);
        }
        RtpVideoChainRole::Parser => {
            set_property_if_supported(element, "disable-passthrough", true);
            set_property_if_supported(element, "config-interval", -1i32);
        }
        RtpVideoChainRole::PreDecodeQueue => {
            configure_queue(element, VIDEO_COMPRESSED_QUEUE_MAX_BUFFERS, false);
        }
        RtpVideoChainRole::Decoder => {
            set_property_if_supported(element, "automatic-request-sync-points", true);
            set_property_if_supported(element, "discard-corrupted-frames", true);
            set_property_if_supported(element, "min-force-key-unit-interval", 100_000_000u64);
            set_property_if_supported(element, "qos", false);
        }
        RtpVideoChainRole::PostDecodeRateSetter => {
            if let Some(caps) = spec
                .caps
                .as_deref()
                .and_then(|caps| caps.parse::<gst::Caps>().ok())
            {
                element.set_property("caps", &caps);
            }
            set_property_if_supported(element, "join", true);
            set_property_if_supported(element, "replace", false);
            set_property_if_supported(element, "qos", false);
        }
        RtpVideoChainRole::PostDecodeCapsFilter => {
            if let Some(caps) = spec
                .caps
                .as_deref()
                .and_then(|caps| caps.parse::<gst::Caps>().ok())
            {
                element.set_property("caps", &caps);
            }
        }
        RtpVideoChainRole::PostDecodeConverter => {
            set_property_if_supported(element, "qos", false);
        }
        RtpVideoChainRole::StatsOverlay => {
            configure_stats_overlay_element(element);
        }
        RtpVideoChainRole::PostDecodeQueue => {
            configure_queue_for_low_latency(element, "video");
        }
        RtpVideoChainRole::Sink => {
            if matches!(video_api, RtpVideoApi::D3D11 | RtpVideoApi::D3D12) {
                configure_d3d_video_sink(element, d3d_fullscreen_sink);
            } else {
                configure_sink_for_low_latency(element);
            }
        }
    }
}

fn link_rtp_video_pad(
    pipeline: &gst::Pipeline,
    src_pad: &gst::Pad,
    encoding: &str,
    render_state: &GstreamerRenderState,
    event_sender: &Option<Sender<Event>>,
    streaming_reported: &Arc<AtomicBool>,
    present_max_fps: Arc<AtomicU32>,
    d3d_fullscreen_sink: bool,
    video_liveness: VideoLivenessMonitor,
) -> Result<(), String> {
    if src_pad.is_linked() {
        return Ok(());
    }

    let requested_fps = video_liveness.requested_fps();
    let (video_api, specs) = rtp_video_chain_specs(encoding, requested_fps).ok_or_else(|| {
        format!(
            "Explicit low-latency decode chain is unavailable for RTP {encoding}; install the platform GStreamer plugin packages or set {NATIVE_VIDEO_BACKEND_ENV}=software to force software decode."
        )
    })?;
    video_liveness.update_hardware_acceleration(format!("GStreamer {}", video_api.label()));
    video_liveness.set_stats_overlay(None);
    let mut elements = Vec::with_capacity(specs.len());

    let result = (|| -> Result<(), String> {
        send_log(
            event_sender,
            "info",
            format_video_chain_selection(encoding, video_api, &specs),
        );
        if video_api == RtpVideoApi::D3D12 {
            send_log(
                event_sender,
                "info",
                format_d3d12_selection_summary(requested_fps),
            );
        }
        let configured_present_max_fps = present_max_fps.load(Ordering::SeqCst);
        let effective_present_max_fps = effective_present_max_fps(
            configured_present_max_fps,
            requested_fps,
            video_api,
            primary_display_refresh_hz(),
        );
        present_max_fps.store(effective_present_max_fps, Ordering::SeqCst);
        if effective_present_max_fps > 0 {
            let reason = if configured_present_max_fps == PRESENT_LIMITER_AUTO_SENTINEL {
                "auto-enabled for the D3D present path to prevent display-rate present backpressure"
                    .to_owned()
            } else if configured_present_max_fps == PRESENT_LIMITER_VRR_SENTINEL {
                "kept below the display refresh ceiling for VRR".to_owned()
            } else {
                format!("configured by {NATIVE_PRESENT_MAX_FPS_ENV}")
            };
            send_log(
                event_sender,
                "info",
                format!(
                    "Native present limiter enabled at {effective_present_max_fps} fps for {} video path; reason: {reason}.",
                    video_api.label()
                ),
            );
        }
        if d3d_fullscreen_sink {
            send_log(
                event_sender,
                "info",
                format!(
                    "Native D3D sink fullscreen presentation enabled for Cloud G-Sync/VRR; set {NATIVE_D3D_FULLSCREEN_ENV}=0 to disable."
                ),
            );
        }
        for spec in &specs {
            let element = make_element(spec.factory)?;
            configure_rtp_video_chain_element(
                &element,
                spec.clone(),
                video_api,
                d3d_fullscreen_sink,
            );
            if spec.role == RtpVideoChainRole::StatsOverlay {
                video_liveness.set_stats_overlay(Some(element.clone()));
            }
            pipeline.add(&element).map_err(|error| {
                format!(
                    "Failed to add {} for RTP {encoding} video chain: {error}",
                    spec.factory
                )
            })?;
            elements.push(element);
        }

        for pair in elements.windows(2) {
            pair[0].link(&pair[1]).map_err(|error| {
                format!(
                    "Failed to link {} -> {} for RTP {encoding} video chain: {error:?}",
                    element_factory_name(&pair[0]),
                    element_factory_name(&pair[1])
                )
            })?;
        }

        let first = elements
            .first()
            .ok_or_else(|| format!("No elements created for RTP {encoding} video chain."))?;
        let Some(first_sink_pad) = first.static_pad("sink") else {
            return Err(format!(
                "First RTP {encoding} video-chain element has no sink pad."
            ));
        };
        let sink = elements
            .last()
            .ok_or_else(|| format!("RTP {encoding} video chain has no sink element."))?;
        if let Some(post_decode_queue) =
            specs
                .iter()
                .zip(elements.iter())
                .find_map(|(spec, element)| {
                    (spec.role == RtpVideoChainRole::PostDecodeQueue).then_some(element)
                })
        {
            video_liveness.set_post_decode_queue(post_decode_queue.clone());
            watch_video_decoded_rate(
                post_decode_queue,
                event_sender,
                Some(video_liveness.clone()),
            );
        }
        if let Some(pre_decode_queue) =
            specs
                .iter()
                .zip(elements.iter())
                .find_map(|(spec, element)| {
                    (spec.role == RtpVideoChainRole::PreDecodeQueue).then_some(element)
                })
        {
            video_liveness.set_pre_decode_queue(pre_decode_queue.clone());
        }
        if let Some(parser) = specs
            .iter()
            .zip(elements.iter())
            .find_map(|(spec, element)| (spec.role == RtpVideoChainRole::Parser).then_some(element))
        {
            watch_video_caps_transitions(parser, "parser", event_sender, video_liveness.clone());
        }
        if let Some(decoder) = specs
            .iter()
            .zip(elements.iter())
            .find_map(|(spec, element)| {
                (spec.role == RtpVideoChainRole::Decoder).then_some(element)
            })
        {
            video_liveness.set_decoder(decoder.clone());
            watch_video_caps_transitions(decoder, "decoder", event_sender, video_liveness.clone());
        }
        render_state.set_video_sink(sink.clone(), event_sender);
        install_present_limiter(
            sink,
            present_max_fps,
            event_sender,
            Some(video_liveness.clone()),
        );
        watch_video_sink_caps_transitions(sink, event_sender, Some(video_liveness.clone()));
        watch_first_sink_buffer(sink, "video", event_sender, streaming_reported);
        watch_video_sink_rate(sink, event_sender, Some(video_liveness.clone()));

        for element in &elements {
            element.sync_state_with_parent().map_err(|error| {
                format!("Failed to sync RTP {encoding} video-chain element state: {error}")
            })?;
        }
        src_pad
            .link(&first_sink_pad)
            .map_err(|error| format!("Failed to link RTP {encoding} video pad: {error:?}"))?;
        video_liveness.set_rtp_video_src_pad(src_pad);
        watch_rtp_video_bitrate(src_pad, video_liveness.clone(), event_sender);
        video_liveness.start(pipeline.clone(), sink.clone(), event_sender.clone());

        Ok(())
    })();

    if result.is_err() {
        for element in &elements {
            let _ = element.set_state(gst::State::Null);
            let _ = pipeline.remove(element);
        }
    }

    result?;
    send_log(
        event_sender,
        "info",
        format!(
            "Linked RTP {encoding} video through explicit low-latency {} decode chain.",
            video_api.label()
        ),
    );
    Ok(())
}

pub(crate) fn format_video_chain_selection(
    encoding: &str,
    video_api: RtpVideoApi,
    specs: &[RtpVideoChainSpec],
) -> String {
    let decoder = specs
        .iter()
        .find(|spec| spec.role == RtpVideoChainRole::Decoder)
        .map(|spec| spec.factory)
        .unwrap_or("unknown");
    let sink = specs
        .iter()
        .find(|spec| spec.role == RtpVideoChainRole::Sink)
        .map(|spec| spec.factory)
        .unwrap_or("unknown");
    let converter = specs
        .iter()
        .filter(|spec| spec.role == RtpVideoChainRole::PostDecodeConverter)
        .map(|spec| spec.factory)
        .collect::<Vec<_>>()
        .join("+");
    let converter = if converter.is_empty() {
        "none".to_owned()
    } else {
        converter
    };
    let memory = specs
        .iter()
        .find(|spec| spec.role == RtpVideoChainRole::PostDecodeCapsFilter)
        .and_then(|spec| spec.caps.as_deref())
        .unwrap_or(if video_api.is_gpu_path() {
            "auto-negotiated"
        } else {
            "system-memory"
        });
    let acceleration = if video_api.is_gpu_path() {
        "hardware"
    } else {
        "software"
    };
    let path_note = if cfg!(target_os = "windows") && video_api == RtpVideoApi::Vulkan {
        if sink == "d3d12videosink" {
            " (DXVA decode + D3D12 present; Electron cannot composite Win32 vulkansink — use External for true Vulkan present)"
        } else if sink == "d3d11videosink" {
            " (DXVA decode + D3D11 present; Electron cannot composite Win32 vulkansink — use External for true Vulkan present)"
        } else {
            " (DXVA decode + Vulkan present; native vulkanh264dec is unstable on Windows)"
        }
    } else {
        ""
    };
    format!(
        "Selected native {acceleration} video path for RTP {encoding}: backend={}, decoder={decoder}, converter={converter}, renderer={sink}, memory={memory}{path_note}.",
        video_api.label()
    )
}

fn format_d3d12_selection_summary(requested_fps: Option<u32>) -> String {
    let backend_env = std::env::var(NATIVE_VIDEO_BACKEND_ENV).ok();
    let api_env = std::env::var(NATIVE_VIDEO_API_ENV).ok();
    let reason = if backend_env
        .as_deref()
        .is_some_and(|value| value.eq_ignore_ascii_case("d3d12"))
    {
        format!("forced by {NATIVE_VIDEO_BACKEND_ENV}=d3d12")
    } else if api_env
        .as_deref()
        .is_some_and(|value| value.eq_ignore_ascii_case("d3d12"))
    {
        format!("forced by {NATIVE_VIDEO_API_ENV}=d3d12")
    } else if should_prefer_d3d12_for_high_fps(requested_fps) {
        format!(
            "auto-selected for {} fps stream to avoid D3D11 display-rate present backpressure",
            requested_fps
                .map(|fps| fps.to_string())
                .unwrap_or_else(|| "high-FPS".to_owned())
        )
    } else {
        "D3D11 was unavailable/probe failed".to_owned()
    };

    format!(
        "Native D3D12 video path selected; reason: {reason}. env {NATIVE_VIDEO_BACKEND_ENV}={backend_env:?}, {NATIVE_VIDEO_API_ENV}={api_env:?}. If D3D12 stalls on a specific driver, force {NATIVE_VIDEO_BACKEND_ENV}=d3d11."
    )
}

fn element_factory_name(element: &gst::Element) -> String {
    element
        .factory()
        .map(|factory| factory.name().to_string())
        .unwrap_or_else(|| element.name().to_string())
}

fn link_decoded_media_pad(
    pipeline: &gst::Pipeline,
    src_pad: &gst::Pad,
    render_state: &GstreamerRenderState,
    event_sender: &Option<Sender<Event>>,
    streaming_reported: &Arc<AtomicBool>,
    video_liveness: &VideoLivenessMonitor,
) -> Result<(), String> {
    if src_pad.is_linked() {
        return Ok(());
    }

    match decoded_media_kind(src_pad) {
        DecodedMediaKind::Video => link_media_chain(
            pipeline,
            src_pad,
            &video_sink_factories(),
            "video",
            Some(render_state),
            event_sender,
            streaming_reported,
            Some(video_liveness),
        ),
        DecodedMediaKind::Audio => link_media_chain(
            pipeline,
            src_pad,
            &[
                ("queue", None),
                ("audioconvert", None),
                ("audioresample", None),
                ("autoaudiosink", Some(false)),
            ],
            "audio",
            None,
            event_sender,
            streaming_reported,
            None,
        ),
        DecodedMediaKind::Unknown => Err(format!(
            "Unsupported decoded media caps {:?}; routing to fallback sink.",
            pad_caps_name(src_pad)
        )),
    }
}

fn video_sink_factories() -> Vec<(&'static str, Option<bool>)> {
    #[cfg(target_os = "windows")]
    {
        let d3d_sink = ["d3d12videosink", "d3d11videosink"]
            .into_iter()
            .find(|factory| gst::ElementFactory::find(factory).is_some());
        if let Some(sink) = d3d_sink {
            let mut factories = vec![("queue", None)];
            if gst::ElementFactory::find("dwritetextoverlay").is_some() {
                factories.push(("dwritetextoverlay", None));
            }
            factories.push((sink, Some(false)));
            return factories;
        }
    }

    let mut factories = vec![("queue", None), ("videoconvert", None)];
    if gst::ElementFactory::find("dwritetextoverlay").is_some() {
        factories.push(("dwritetextoverlay", None));
    }
    factories.push(("autovideosink", Some(false)));
    factories
}

fn link_media_chain(
    pipeline: &gst::Pipeline,
    src_pad: &gst::Pad,
    factories: &[(&str, Option<bool>)],
    media_label: &str,
    render_state: Option<&GstreamerRenderState>,
    event_sender: &Option<Sender<Event>>,
    streaming_reported: &Arc<AtomicBool>,
    video_liveness: Option<&VideoLivenessMonitor>,
) -> Result<(), String> {
    if media_label == "video" {
        if let Some(video_liveness) = video_liveness {
            video_liveness.set_stats_overlay(None);
        }
    }

    let mut elements = Vec::with_capacity(factories.len());
    for (factory, sync_property) in factories {
        let factory = *factory;
        let element = make_element(factory)?;
        if factory == "queue" {
            configure_queue_for_low_latency(&element, media_label);
        }
        if factory == "dwritetextoverlay" {
            configure_stats_overlay_element(&element);
            if media_label == "video" {
                if let Some(video_liveness) = video_liveness {
                    video_liveness.set_stats_overlay(Some(element.clone()));
                }
            }
        }
        if sync_property.is_some() || factory.ends_with("sink") {
            if factory == "d3d11videosink" || factory == "d3d12videosink" {
                // Fallback decodebin path: never exclusive-fullscreen (Internal default).
                configure_d3d_video_sink(&element, false);
            } else {
                configure_sink_for_low_latency(&element);
            }
        }
        pipeline
            .add(&element)
            .map_err(|error| format!("Failed to add {factory} for {media_label}: {error}"))?;
        elements.push(element);
    }

    for pair in elements.windows(2) {
        pair[0].link(&pair[1]).map_err(|error| {
            format!(
                "Failed to link {} -> {} for {media_label}: {error:?}",
                pair[0]
                    .factory()
                    .map(|factory| factory.name())
                    .unwrap_or_default(),
                pair[1]
                    .factory()
                    .map(|factory| factory.name())
                    .unwrap_or_default()
            )
        })?;
    }

    let first = elements
        .first()
        .ok_or_else(|| format!("No elements created for {media_label} sink chain."))?;
    let Some(first_sink_pad) = first.static_pad("sink") else {
        return Err(format!(
            "First {media_label} sink-chain element has no sink pad."
        ));
    };
    src_pad
        .link(&first_sink_pad)
        .map_err(|error| format!("Failed to link decoded {media_label} pad: {error:?}"))?;

    if let Some(sink) = elements.last() {
        if media_label == "video" {
            if let Some(render_state) = render_state {
                render_state.set_video_sink(sink.clone(), event_sender);
            }
        }
        watch_first_sink_buffer(sink, media_label, event_sender, streaming_reported);
        if media_label == "audio" {
            if let Some(video_liveness) = video_liveness {
                watch_audio_activity(sink, video_liveness);
            }
        }
        if media_label == "video" {
            if let Some(video_liveness) = video_liveness {
                watch_video_sink_rate(sink, event_sender, Some(video_liveness.clone()));
                video_liveness.start(pipeline.clone(), sink.clone(), event_sender.clone());
            }
        }
    }

    for element in &elements {
        element.sync_state_with_parent().map_err(|error| {
            format!("Failed to sync {media_label} sink-chain element state: {error}")
        })?;
    }

    Ok(())
}

fn link_decoded_media_to_fakesink(
    pipeline: &gst::Pipeline,
    src_pad: &gst::Pad,
    label: &str,
) -> Result<(), String> {
    if src_pad.is_linked() {
        return Ok(());
    }

    let sink = gst::ElementFactory::make("fakesink")
        .property("sync", false)
        .property("async", false)
        .build()
        .map_err(|error| format!("Failed to create {label}: {error}"))?;
    configure_sink_for_low_latency(&sink);
    pipeline
        .add(&sink)
        .map_err(|error| format!("Failed to add {label}: {error}"))?;
    sink.sync_state_with_parent()
        .map_err(|error| format!("Failed to sync {label} state: {error}"))?;

    let Some(sink_pad) = sink.static_pad("sink") else {
        return Err(format!("{label} has no sink pad."));
    };
    src_pad
        .link(&sink_pad)
        .map(|_| ())
        .map_err(|error| format!("Failed to link {label}: {error:?}"))
}

fn make_element(factory: &str) -> Result<gst::Element, String> {
    gst::ElementFactory::make(factory)
        .build()
        .map_err(|error| format!("Failed to create GStreamer element {factory}: {error}"))
}
