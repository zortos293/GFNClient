package com.opencloudgaming.opennow

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaRecorder
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Build
import android.os.CombinedVibration
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.View
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.TlsVersion
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.GlShader
import org.webrtc.GlUtil
import org.webrtc.HardwareVideoDecoderFactory
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.Predicate
import org.webrtc.RTCStats
import org.webrtc.RTCStatsCollectorCallback
import org.webrtc.RendererCommon
import org.webrtc.RtpCapabilities
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCodecInfo
import org.webrtc.VideoDecoder
import org.webrtc.VideoDecoderFactory
import org.webrtc.VideoTrack
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object NativeCodecProbe {
    init {
        runCatching { System.loadLibrary("opennow_native") }
    }

    external fun nativeRuntimeSummary(): String
    external fun nativeDecoderAvailable(mimeType: String): Boolean
}

private object WebRtcRuntime {
    @Volatile
    private var initialized = false

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions(),
            )
            initialized = true
        }
    }
}

object CodecProbe {
    private data class DecoderLimits(
        val maxSupportedWidth: Int?,
        val maxSupportedHeight: Int?,
    )

    fun report(context: Context): RuntimeCodecReport {
        WebRtcRuntime.ensureInitialized(context)
        val isTv = isAndroidTvProfile(context)
        val renderer = listOf(Build.HARDWARE, Build.BOARD, Build.DEVICE, Build.MODEL, Build.MANUFACTURER)
            .joinToString(" ")
            .lowercase(Locale.US)
        val memoryInfo = ActivityManager.MemoryInfo()
        val totalMemoryBytes = runCatching {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
                ?.getMemoryInfo(memoryInfo)
            memoryInfo.totalMem.takeIf { it > 0L }
        }.getOrNull()
        val is64BitRuntime = android.os.Process.is64Bit()
        val constrainedRuntime = isConstrainedStreamingRuntime(
            androidTvProfile = isTv,
            is64BitRuntime = is64BitRuntime,
            totalMemoryBytes = totalMemoryBytes,
        )
        val lowPower = isLowPowerStreamingProfile(
            androidTvProfile = isTv,
            renderer = renderer,
            totalMemoryBytes = totalMemoryBytes,
            is64BitRuntime = is64BitRuntime,
        )
        val webRtcDecoders = probeWebRtcDecoders()
        val capabilities = VideoCodec.entries.map { codec ->
            val mime = codec.mimeType()
            val decoders = codecInfos(mime, encoder = false)
            val encoders = codecInfos(mime, encoder = true)
            val webRtc = webRtcDecoders[codec]
            val nativeDecoderAvailable = runCatching { NativeCodecProbe.nativeDecoderAvailable(mime) }.getOrNull()
            val preferredDecoder = decoders.firstOrNull(::isHardwareCodec) ?: decoders.firstOrNull()
            val preferredEncoder = encoders.firstOrNull(::isHardwareCodec) ?: encoders.firstOrNull()
            val decoderLimits = decoderLimits(mime, decoders)
            CodecCapability(
                codec = codec,
                decoderAvailable = decoders.isNotEmpty(),
                encoderAvailable = encoders.isNotEmpty(),
                hardwareDecoder = decoders.any(::isHardwareCodec),
                hardwareEncoder = encoders.any(::isHardwareCodec),
                decoderName = preferredDecoder?.name,
                encoderName = preferredEncoder?.name,
                realtimeSafe = decoders.any { isRealtimeSafeDecoder(codec, it) },
                nativeDecoderAvailable = nativeDecoderAvailable,
                webRtcDecoderAvailable = webRtc?.decoderAvailable,
                webRtcHardwareDecoderAvailable = webRtc?.hardwareDecoderAvailable,
                webRtcDecoderName = webRtc?.decoderName,
                webRtcCodecProfiles = webRtc?.profiles.orEmpty(),
                maxSupportedWidth = decoderLimits.maxSupportedWidth,
                maxSupportedHeight = decoderLimits.maxSupportedHeight,
            )
        }
        return RuntimeCodecReport(
            capabilities = capabilities,
            nativeRuntimeSummary = runCatching { NativeCodecProbe.nativeRuntimeSummary() }.getOrElse { "{\"nativeLibrary\":\"unavailable\"}" },
            androidTvProfile = isTv,
            lowPowerGpuProfile = lowPower,
            constrainedRuntimeProfile = constrainedRuntime,
        ).also { report ->
            NativeInputDiagnostics.add(
                "codec probe device=${Build.MANUFACTURER}/${Build.MODEL} hardware=${Build.HARDWARE} tv=$isTv lowPower=$lowPower " +
                    "constrained=$constrainedRuntime runtimeBits=${if (is64BitRuntime) 64 else 32} " +
                    "memoryMiB=${totalMemoryBytes?.div(BYTES_PER_MEBIBYTE) ?: 0L}",
            )
            report.capabilities.forEach { capability ->
                NativeInputDiagnostics.add(
                    "codec probe codec=${capability.codec} platform=${capability.decoderName ?: "none"} " +
                        "platformHw=${capability.hardwareDecoder} native=${capability.nativeDecoderAvailable} " +
                        "webrtc=${capability.webRtcDecoderName ?: "none"} webrtcHw=${capability.webRtcHardwareDecoderAvailable} " +
                    "profiles=${capability.webRtcCodecProfiles.joinToString("|").ifBlank { "none" }} " +
                    "max=${capability.maxSupportedWidth ?: 0}x${capability.maxSupportedHeight ?: 0} " +
                    "launch=${capability.streamingDecoderUsableForLaunch()}",
                )
            }
        }
    }

    private fun decoderLimits(mime: String, decoders: List<MediaCodecInfo>): DecoderLimits {
        val candidates = decoders.filter(::isHardwareCodec).ifEmpty { decoders }
        if (candidates.isEmpty()) return DecoderLimits(null, null)
        val knownResolutions = streamAspectRatioOptions()
            .flatMap(::streamResolutionOptionsForAspect)
            .distinct()
        val supportedPixels = mutableListOf<Pair<Int, Int>>()
        for (resolution in knownResolutions) {
            val (width, height) = parseResolutionPixelsOrNull(resolution) ?: continue
            val sizeSupported = candidates.any { decoder ->
                runCatching {
                    decoder.getCapabilitiesForType(mime).videoCapabilities?.isSizeSupported(width, height) == true
                }.getOrDefault(false)
            }
            if (sizeSupported) supportedPixels += width to height
        }
        return DecoderLimits(
            maxSupportedWidth = supportedPixels.maxOfOrNull { it.first },
            maxSupportedHeight = supportedPixels.maxOfOrNull { it.second },
        )
    }

    private fun codecInfos(mime: String, encoder: Boolean): List<MediaCodecInfo> {
        val list = if (Build.VERSION.SDK_INT >= 21) {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.toList()
        } else {
            emptyList()
        }
        return list.filter { info ->
            info.isEncoder == encoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
        }
    }

    private data class WebRtcCodecProbe(
        val decoderAvailable: Boolean,
        val hardwareDecoderAvailable: Boolean,
        val decoderName: String?,
        val profiles: List<String>,
    )

    private fun probeWebRtcDecoders(): Map<VideoCodec, WebRtcCodecProbe> {
        val eglBase = runCatching { EglBase.create() }.getOrNull() ?: return emptyMap()
        return try {
            val streamingFactory = OpenNowVideoDecoderFactory(eglBase.eglBaseContext)
            val hardwareFactory = openNowHardwareVideoDecoderFactory(eglBase.eglBaseContext)
            val streamingSupported = streamingFactory.supportedCodecsByVideoCodec()
            val hardwareSupported = hardwareFactory.supportedCodecsByVideoCodec()
            VideoCodec.entries.associateWith { codec ->
                val defaultInfos = streamingSupported[codec].orEmpty()
                val hardwareInfos = hardwareSupported[codec].orEmpty()
                val decoderName = streamingFactory.firstDecoderName(defaultInfos)
                WebRtcCodecProbe(
                    decoderAvailable = decoderName != null,
                    hardwareDecoderAvailable = hardwareFactory.firstDecoderName(hardwareInfos) != null,
                    decoderName = decoderName,
                    profiles = defaultInfos.map(::formatWebRtcCodecInfo).distinct(),
                )
            }
        } catch (_: Throwable) {
            emptyMap()
        } finally {
            eglBase.release()
        }
    }

    private fun VideoDecoderFactory.supportedCodecsByVideoCodec(): Map<VideoCodec, List<VideoCodecInfo>> =
        getSupportedCodecs()
            .groupBy { info -> info.name.toVideoCodec() }
            .mapNotNull { (codec, infos) -> codec?.let { it to infos } }
            .toMap()

    private fun VideoDecoderFactory.firstDecoderName(infos: List<VideoCodecInfo>): String? {
        for (info in infos) {
            val decoder = runCatching { createDecoder(info) }.getOrNull() ?: continue
            return try {
                decoder.getImplementationName()
            } finally {
                runCatching { decoder.release() }
            }
        }
        return null
    }

    private fun String.toVideoCodec(): VideoCodec? =
        when (uppercase(Locale.US)) {
            "AVC", "H264", "H.264" -> VideoCodec.H264
            "HEVC", "H265", "H.265" -> VideoCodec.H265
            "AV01", "AV1" -> VideoCodec.AV1
            else -> null
        }

    private fun formatWebRtcCodecInfo(info: VideoCodecInfo): String {
        val profile = info.params["profile-level-id"]
        val packetization = info.params["packetization-mode"]
        return listOfNotNull(info.name.toVideoCodec()?.name ?: info.name, profile?.let { "profile=$it" }, packetization?.let { "packet=$it" })
            .joinToString(" ")
    }

    private fun isHardwareCodec(info: MediaCodecInfo): Boolean {
        val name = info.name.lowercase(Locale.US)
        if (name.contains("google") || name.contains("sw") || name.contains("software")) return false
        return if (Build.VERSION.SDK_INT >= 29) {
            info.isHardwareAccelerated
        } else {
            true
        }
    }

    internal fun isOpenNowHardwareDecoderAllowed(info: MediaCodecInfo): Boolean {
        if (!isHardwareCodec(info)) return false
        val name = info.name.lowercase(Locale.US)
        if (name.contains("google") || name.contains("software") || name.contains("sw")) return false
        if (name.contains("exynos")) return false
        return true
    }

    private fun isRealtimeSafeDecoder(codec: VideoCodec, info: MediaCodecInfo): Boolean {
        if (!isHardwareCodec(info)) return false
        val name = info.name.lowercase(Locale.US)
        return when (codec) {
            VideoCodec.H264 -> true
            // Android WebRTC HEVC/AV1 decode is still device-fragile here. Exynos HEVC black-screens
            // and Google AV1 falls back to a laggy software path even when the codec list advertises it.
            VideoCodec.H265 -> !name.contains("exynos")
            VideoCodec.AV1 -> !name.contains("google")
        }
    }

    private fun VideoCodec.mimeType(): String =
        when (this) {
            VideoCodec.H264 -> "video/avc"
            VideoCodec.H265 -> "video/hevc"
            VideoCodec.AV1 -> "video/av01"
        }
}

internal fun isAndroidTvProfile(context: Context): Boolean =
    context.packageManager.hasSystemFeature("android.software.leanback") ||
        context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION

internal fun isLowPowerStreamingProfile(
    androidTvProfile: Boolean,
    renderer: String,
    totalMemoryBytes: Long?,
    is64BitRuntime: Boolean = true,
): Boolean {
    val normalizedRenderer = renderer.lowercase(Locale.US)
    val knownLowPowerGpu =
        normalizedRenderer.contains("powervr") ||
            normalizedRenderer.contains("ge8320") ||
            normalizedRenderer.contains("ge83")
    return knownLowPowerGpu || isConstrainedStreamingRuntime(
        androidTvProfile = androidTvProfile,
        is64BitRuntime = is64BitRuntime,
        totalMemoryBytes = totalMemoryBytes,
    )
}

internal fun isConstrainedStreamingRuntime(
    androidTvProfile: Boolean,
    is64BitRuntime: Boolean,
    totalMemoryBytes: Long?,
): Boolean {
    val constrainedTvMemory = androidTvProfile &&
        totalMemoryBytes != null &&
        totalMemoryBytes in 1..LOW_POWER_TV_MEMORY_LIMIT_BYTES
    return !is64BitRuntime || constrainedTvMemory
}

private fun openNowHardwareVideoDecoderFactory(sharedContext: EglBase.Context): VideoDecoderFactory =
    HardwareVideoDecoderFactory(
        sharedContext,
        Predicate<MediaCodecInfo> { info -> CodecProbe.isOpenNowHardwareDecoderAllowed(info) },
    )

private class OpenNowVideoDecoderFactory(
    sharedContext: EglBase.Context,
    private val nativeLowLatencyDecoderEnabled: Boolean = false,
    private val requestedFps: () -> Int = { 60 },
) : VideoDecoderFactory {
    private val defaultFactory = DefaultVideoDecoderFactory(sharedContext)
    private val hardwareFactory = openNowHardwareVideoDecoderFactory(sharedContext)

    override fun createDecoder(info: VideoCodecInfo): VideoDecoder? {
        val codec = info.name.toOpenNowVideoCodec()
        val hardwareDecoder = if (codec != null) hardwareFactory.createDecoder(info) else null
        val decoder = when (codec) {
            VideoCodec.H264 -> hardwareDecoder ?: defaultFactory.createDecoder(info)
            VideoCodec.H265,
            VideoCodec.AV1,
            -> hardwareDecoder
            null -> defaultFactory.createDecoder(info)
        }
        val exactRequestedFps = requestedFps().coerceAtLeast(1)
        val tuneDecoderPerformance = mediaCodecPerformanceTargetFps(exactRequestedFps) != null
        val tuneSelectedDecoder = shouldUseMediaCodecDecoderTuning(
            selectedDecoder = decoder,
            approvedHardwareDecoder = hardwareDecoder,
            requestedFps = exactRequestedFps,
            lowLatencyEnabled = nativeLowLatencyDecoderEnabled,
        )
        if (codec != null && hardwareDecoder != null) {
            NativeInputDiagnostics.add(
                "native MediaCodec decoder selected codec=${codec.name} " +
                    "implementation=${hardwareDecoder.getImplementationName()} requestedFps=$exactRequestedFps " +
                    "performanceTuning=$tuneDecoderPerformance lowLatency=$nativeLowLatencyDecoderEnabled",
            )
        } else if (codec != null && decoder != null && (nativeLowLatencyDecoderEnabled || tuneDecoderPerformance)) {
            NativeInputDiagnostics.add(
                "MediaCodec tuning skipped codec=${codec.name} decoder=${decoder.javaClass.name} " +
                    "reason=non-approved-hardware-decoder",
            )
        }
        return if (decoder != null && tuneSelectedDecoder) {
            LowLatencyVideoDecoder(
                delegate = decoder,
                requestedFps = exactRequestedFps,
                lowLatencyEnabled = nativeLowLatencyDecoderEnabled,
            )
        } else {
            decoder
        }
    }

    override fun getSupportedCodecs(): Array<VideoCodecInfo> {
        val defaultCodecs = defaultFactory.getSupportedCodecs()
            .filterNot { it.name.toOpenNowVideoCodec() in ADVANCED_STREAM_CODECS }
        val nativeAdvancedCodecs = hardwareFactory.getSupportedCodecs()
            .filter { it.name.toOpenNowVideoCodec() in ADVANCED_STREAM_CODECS }
        return (defaultCodecs + nativeAdvancedCodecs)
            .distinctBy { it.stableKey() }
            .toTypedArray()
    }

    private fun VideoCodecInfo.stableKey(): String =
        "${name.uppercase(Locale.US)}:${params.toSortedMap()}"

    private companion object {
        private val ADVANCED_STREAM_CODECS = setOf(VideoCodec.H265, VideoCodec.AV1)
    }
}

private fun String.toOpenNowVideoCodec(): VideoCodec? =
    when (uppercase(Locale.US)) {
        "AVC", "H264", "H.264" -> VideoCodec.H264
        "HEVC", "H265", "H.265" -> VideoCodec.H265
        "AV01", "AV1" -> VideoCodec.AV1
        else -> null
    }

private fun VideoCodec.webRtcCodecName(): String =
    when (this) {
        VideoCodec.H264 -> "H264"
        VideoCodec.H265 -> "H265"
        VideoCodec.AV1 -> "AV1"
    }

private fun RtpCapabilities.CodecCapability.openNowCodecName(): String? {
    val fromMime = mimeType
        ?.substringAfter("/", "")
        ?.takeIf { it.isNotBlank() }
        ?.toOpenNowVideoCodec()
        ?.webRtcCodecName()
    if (fromMime != null) return fromMime
    return name?.toOpenNowVideoCodec()?.webRtcCodecName() ?: name?.uppercase(Locale.US)
}

private fun RtpCapabilities.CodecCapability.codecParameterInt(name: String): Int? =
    parameters
        ?.entries
        ?.firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
        ?.toIntOrNull()

private fun RtpCapabilities.CodecCapability.h265ProfilePriority(preferTenBit: Boolean): Int {
    val profile = codecParameterInt("profile-id")
    return if (preferTenBit) {
        when (profile) {
            2 -> 0
            1 -> 1
            else -> 2
        }
    } else {
        when (profile) {
            1 -> 0
            null -> 1
            2 -> 2
            else -> 3
        }
    }
}

private fun RtpCapabilities.CodecCapability.preferenceKey(): String =
    "${openNowCodecName().orEmpty()}:${parameters.orEmpty().toSortedMap()}"

private fun StreamSettings.prefersTenBitVideo(): Boolean =
    hdrEnabled ||
        colorQuality == ColorQuality.TenBit420 ||
        colorQuality == ColorQuality.TenBit444

private val WEBRTC_AUXILIARY_VIDEO_CODECS = setOf("RTX", "RED", "ULPFEC", "FLEXFEC-03")

private fun streamDiagnosticId(value: String?): String {
    val cleaned = value.orEmpty().trim()
    if (cleaned.isBlank()) return "-"
    return if (cleaned.length <= 12) cleaned else "${cleaned.take(4)}...${cleaned.takeLast(6)}"
}

private fun signalingUrlForDiagnostics(url: String, sessionId: String): String =
    redactDiagnosticUrl(url).replace(sessionId, streamDiagnosticId(sessionId))

internal enum class SignalingFailureDisposition {
    RetryTransport,
    RecoverSession,
    SessionEnded,
}

internal fun signalingFailureDisposition(
    message: String,
    normalClosureMeansSessionEnded: Boolean = false,
): SignalingFailureDisposition = when {
    message.contains("http=410", ignoreCase = true) -> SignalingFailureDisposition.SessionEnded
    message.contains("http=404", ignoreCase = true) ||
        message.contains("Not Found", ignoreCase = true) -> SignalingFailureDisposition.RecoverSession
    normalClosureMeansSessionEnded && message.contains("code=1000", ignoreCase = true) ->
        SignalingFailureDisposition.SessionEnded
    else -> SignalingFailureDisposition.RetryTransport
}

internal fun shouldPreserveMediaAfterSignalingFailure(
    disposition: SignalingFailureDisposition,
    iceState: PeerConnection.IceConnectionState?,
): Boolean {
    if (disposition != SignalingFailureDisposition.RetryTransport) return false
    return when (iceState) {
        PeerConnection.IceConnectionState.CHECKING,
        PeerConnection.IceConnectionState.CONNECTED,
        PeerConnection.IceConnectionState.COMPLETED,
        -> true
        else -> false
    }
}

internal fun signalingHeartbeatReply(message: JsonObject): String? =
    if (message["hb"] != null) """{"hb":1}""" else null

private fun IceCandidate.diagnosticSummary(): String {
    val raw = sdp
    val protocol = Regex("""\s(udp|tcp)\s""", RegexOption.IGNORE_CASE)
        .find(raw)
        ?.value
        ?.trim()
        ?.lowercase(Locale.US)
        ?: "unknown"
    val type = Regex("""\styp\s+([a-z0-9]+)""", RegexOption.IGNORE_CASE)
        .find(raw)
        ?.groupValues
        ?.getOrNull(1)
        ?.lowercase(Locale.US)
        ?: "unknown"
    val address = Regex("""candidate:\S+\s+\d+\s+\S+\s+\d+\s+([^\s]+)\s+(\d+)""")
        .find(raw)
        ?.let { match -> "${match.groupValues[1]}:${match.groupValues[2]}" }
        ?: "unknown"
    return "mid=${sdpMid.orEmpty()} line=$sdpMLineIndex type=$type protocol=$protocol address=$address raw=${raw.take(240)}"
}

private fun sdpDiagnosticSummary(label: String, sdp: String): String {
    val lines = sdp.split(Regex("\\r?\\n")).filter { it.isNotBlank() }
    val media = lines.filter { it.startsWith("m=") }.joinToString("|").take(180)
    val candidateEndpoints = lines
        .filter { it.startsWith("a=candidate:") }
        .mapNotNull { line ->
            Regex("""a=candidate:\S+\s+\d+\s+\S+\s+\d+\s+([^\s]+)\s+(\d+)""")
                .find(line)
                ?.let { match -> "${match.groupValues[1]}:${match.groupValues[2]}" }
        }
        .distinct()
        .joinToString(limit = 6)
    val codecs = lines
        .filter { it.startsWith("a=rtpmap:") }
        .mapNotNull { line -> line.substringAfter(' ', "").substringBefore('/').takeIf { it.isNotBlank() } }
        .distinct()
        .take(12)
        .joinToString(",")
    val candidates = lines.count { it.startsWith("a=candidate:") }
    val hasIce = lines.any { it.startsWith("a=ice-ufrag:") } && lines.any { it.startsWith("a=ice-pwd:") }
    val hasFingerprint = lines.any { it.startsWith("a=fingerprint:") }
    return "$label lines=${lines.size} media=$media codecs=$codecs candidates=$candidates endpoints=$candidateEndpoints ice=$hasIce fingerprint=$hasFingerprint"
}

sealed interface SignalingEvent {
    data object Connected : SignalingEvent
    data class Disconnected(val reason: String) : SignalingEvent
    data class Offer(val sdp: String) : SignalingEvent
    data class RemoteIce(val candidate: IceCandidate) : SignalingEvent
    data class Error(val message: String) : SignalingEvent
    data class Log(val message: String) : SignalingEvent
}

class GfnSignalingClient(
    private val session: SessionInfo,
    private val settings: StreamSettings,
    private val http: OkHttpClient = defaultHttpClient(),
    private val onEvent: (SignalingEvent) -> Unit,
) {
    private val signalingHttp = signalingWebSocketHttpClient(http)
    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeatJob: Job? = null
    private var peerId = 0
    private var remotePeerId = 1
    private val peerName = "peer-${java.util.UUID.randomUUID().toString().replace("-", "").take(12)}"
    private var ackCounter = 0

    fun connect() {
        val url = buildSignInUrl()
        val host = url.removePrefix("wss://").substringBefore("/")
        onEvent(SignalingEvent.Log("Signaling connecting url=${signalingUrlForDiagnostics(url, session.sessionId)} session=${streamDiagnosticId(session.sessionId)}"))
        val request = Request.Builder()
            .url(url)
            .header("Sec-WebSocket-Protocol", "x-nv-sessionid.${session.sessionId}")
            .header("Host", host)
            .header("Origin", "https://play.geforcenow.com")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/131.0.0.0 Safari/537.36")
            .build()
        webSocket = signalingHttp.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    onEvent(
                        SignalingEvent.Log(
                            "Signaling open http=${response.code} tls=${response.handshake?.tlsVersion?.javaName ?: "unknown"} " +
                                "protocol=${response.header("Sec-WebSocket-Protocol").orEmpty().replace(session.sessionId, streamDiagnosticId(session.sessionId))}",
                        ),
                    )
                    sendPeerInfo()
                    heartbeatJob?.cancel()
                    heartbeatJob = scope.launch {
                        while (true) {
                            delay(5000)
                            sendJson("""{"hb":1}""")
                        }
                    }
                    onEvent(SignalingEvent.Connected)
                }

                override fun onMessage(webSocket: WebSocket, text: String) = handleMessage(text)
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handleMessage(bytes.utf8())

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    heartbeatJob?.cancel()
                    onEvent(SignalingEvent.Disconnected("socket closed code=$code reason=${reason.ifBlank { "none" }}"))
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    heartbeatJob?.cancel()
                    val responseText = response?.let { " http=${it.code} message=${it.message}" }.orEmpty()
                    onEvent(SignalingEvent.Error("${t.javaClass.simpleName}: ${t.message ?: "Signaling failed"}$responseText"))
                }
            },
        )
    }

    fun sendAnswer(sdp: String, nvstSdp: String?) {
        onEvent(SignalingEvent.Log(sdpDiagnosticSummary("Sending answer", sdp)))
        if (!nvstSdp.isNullOrBlank()) {
            onEvent(SignalingEvent.Log("Sending NVST SDP lines=${nvstSdp.lineSequence().count()} bytes=${nvstSdp.length}"))
        }
        val msg = buildJsonObject {
            put("type", "answer")
            put("sdp", sdp)
            if (nvstSdp != null) put("nvstSdp", nvstSdp)
        }.toString()
        sendPeerMessage(msg)
    }

    fun sendIceCandidate(candidate: IceCandidate) {
        if (candidate.sdp.contains(" tcp ", ignoreCase = true)) {
            onEvent(SignalingEvent.Log("Dropping TCP local ICE candidate ${candidate.diagnosticSummary()}"))
            return
        }
        onEvent(SignalingEvent.Log("Sending local ICE candidate ${candidate.diagnosticSummary()}"))
        val msg = buildJsonObject {
            put("candidate", candidate.sdp)
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
        }.toString()
        sendPeerMessage(msg)
    }

    fun requestKeyframe(reason: String, backlogFrames: Int, attempt: Int) {
        val msg = buildJsonObject {
            put("type", "request_keyframe")
            put("reason", reason)
            put("backlogFrames", backlogFrames)
            put("attempt", attempt)
        }.toString()
        sendPeerMessage(msg)
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        webSocket?.close(1000, "closed")
        webSocket = null
    }

    private fun buildSignInUrl(): String {
        val base = session.signalingUrl.ifBlank {
            val host = if (session.signalingServer.contains(":")) session.signalingServer else "${session.signalingServer}:443"
            "wss://$host/nvst/"
        }
        val normalized = base.replace("wss://", "").trimEnd('/')
        return "wss://$normalized/sign_in?peer_id=$peerName&version=2&peer_role=1&pairing_id=${session.sessionId}"
    }

    private fun handleMessage(text: String) {
        val parsed = runCatching { OpenNowJson.parseToJsonElement(text).jsonObject }.getOrNull()
        if (parsed == null) {
            onEvent(SignalingEvent.Log("Ignoring non-JSON signaling packet"))
            return
        }
        parsed["peer_info"]?.jsonObject?.let { info ->
            if (info["name"]?.jsonPrimitive?.contentOrNull == peerName) {
                peerId = info["id"]?.jsonPrimitive?.intOrNull ?: peerId
            }
        }
        parsed["ackid"]?.jsonPrimitive?.intOrNull?.let { ack ->
            val shouldAck = parsed["peer_info"]?.jsonObject?.get("id")?.jsonPrimitive?.intOrNull != peerId
            if (shouldAck) sendJson("""{"ack":$ack}""")
        }
        signalingHeartbeatReply(parsed)?.let { reply ->
            // Match the desktop client and acknowledge server-driven
            // heartbeats immediately. The periodic client heartbeat remains
            // a separate keepalive when the server does not initiate one.
            sendJson(reply)
            return
        }
        val peerMsg = parsed["peer_msg"]?.jsonObject ?: return
        remotePeerId = peerMsg["from"]?.jsonPrimitive?.intOrNull ?: remotePeerId
        val msg = peerMsg["msg"]?.jsonPrimitive?.contentOrNull ?: return
        val payload = runCatching { OpenNowJson.parseToJsonElement(msg).jsonObject }.getOrNull() ?: return
        when {
            payload["type"]?.jsonPrimitive?.contentOrNull == "offer" -> {
                val sdp = payload["sdp"]?.jsonPrimitive?.contentOrNull
                if (sdp != null) {
                    onEvent(SignalingEvent.Log(sdpDiagnosticSummary("Received offer", sdp)))
                    onEvent(SignalingEvent.Offer(sdp))
                }
            }
            payload["candidate"]?.jsonPrimitive?.contentOrNull != null -> {
                val candidate = IceCandidate(
                    payload["sdpMid"]?.jsonPrimitive?.contentOrNull,
                    payload["sdpMLineIndex"]?.jsonPrimitive?.intOrNull ?: 0,
                    payload["candidate"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                )
                onEvent(SignalingEvent.Log("Received remote ICE candidate ${candidate.diagnosticSummary()}"))
                onEvent(SignalingEvent.RemoteIce(candidate))
            }
        }
    }

    private fun sendPeerInfo() {
        val (width, height) = streamResolutionPixels(settings)
        onEvent(SignalingEvent.Log("Sending peer info resolution=${width}x$height peer=$peerName"))
        sendJson(
            """
            {"ackid":${nextAckId()},"peer_info":{"browser":"Chrome","browserVersion":"131","connected":true,"id":$peerId,"name":"$peerName","peerRole":0,"resolution":"${width}x$height","version":2}}
            """.trimIndent(),
        )
    }

    private fun sendPeerMessage(message: String) {
        val escaped = message.replace("\\", "\\\\").replace("\"", "\\\"")
        sendJson("""{"peer_msg":{"from":$peerId,"to":$remotePeerId,"msg":"$escaped"},"ackid":${nextAckId()}}""")
    }

    private fun sendJson(text: String) {
        webSocket?.send(text)
    }

    private fun nextAckId(): Int {
        ackCounter += 1
        return ackCounter
    }
}

private val SIGNALING_TLS_1_2 =
    ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
        .tlsVersions(TlsVersion.TLS_1_2)
        .build()

internal fun signalingWebSocketHttpClient(base: OkHttpClient): OkHttpClient =
    base.newBuilder()
        // GFN already has an application heartbeat. Avoid a second WebSocket
        // ping loop and Android TV's TLS 1.3/Conscrypt reader spin on this
        // long-lived signaling socket; media remains DTLS/WebRTC and unchanged.
        .pingInterval(0, TimeUnit.MILLISECONDS)
        .connectionSpecs(listOf(SIGNALING_TLS_1_2))
        .build()

object NativeStreamInputRouter {
    @Volatile
    private var client: NativeStreamClient? = null
    @Volatile
    private var androidTvProfile = false
    fun setAndroidTvProfile(enabled: Boolean) {
        androidTvProfile = enabled
    }
    @Volatile
    private var touchMouseEnabled = false
    @Volatile
    private var mouseDirectClick = false
    @Volatile
    private var stretchToFit = false
    @Volatile
    private var renderingAspectRatio = 0f
    @Volatile
    private var decodedStreamWidth = 0
    @Volatile
    private var decodedStreamHeight = 0
    @Volatile
    private var captureAllTouch = false
    @Volatile
    private var systemMenuHandler: (() -> Unit)? = null

    @Volatile
    private var systemBackHandler: (() -> Unit)? = null
    @Volatile
    private var streamUiActive = false
    @Volatile
    private var streamChromePassthroughBounds: TouchPassthroughBounds? = null
    @Volatile
    private var streamPanelPassthroughBounds: TouchPassthroughBounds? = null
    /**
     * Bounds for transient full-screen or anchored overlays, keyed so they cannot clobber each
     * other. The keyboard bar, the exit confirmation and the controls launcher can all be present
     * across the same stream, and a single shared slot meant whichever disposed last wiped the
     * others' rect and left them forwarding taps into the game.
     */
    @Volatile
    private var overlayPassthroughBounds: Map<String, TouchPassthroughBounds> = emptyMap()
    @Volatile
    private var touchControllerPassthroughBounds: Map<String, TouchPassthroughBounds> = emptyMap()
    @Volatile
    private var touchControllerVisible = false
    @Volatile
    private var uiTouchPassthroughActive = false
    private val nativeUiTouchPointerIds = mutableSetOf<Int>()
    private val touchMouseState = TouchMouseState()

    /**
     * When set, fingers are forwarded to the host as real touch instead of being turned into a
     * cursor. Mutually exclusive with the touch-mouse and direct-click paths by construction:
     * [dispatchTouch] takes this branch first and returns.
     */
    @Volatile
    private var nativeTouchEnabled = false
    private val touchSlots = TouchSlotAllocator()
    /**
     * Tracks the initial DOWN position per pointer ID for jitter guard in native touch mode.
     * Entries are added on DOWN and removed on UP/CANCEL.
     */
    private val nativeTouchDownPoints = mutableMapOf<Int, Pair<Float, Float>>()
    /** Native touch settings synced from [AndroidTouchSettings]. */
    @Volatile private var nativeTouchScrollScale: Float = 1.0f
    @Volatile private var nativeTouchJitterThresholdPx: Float = 0f

    fun attach(next: NativeStreamClient) {
        client = next
        // Never carry an in-progress drag position into a different host session.
        touchMouseState.forgetCursorPosition()
        decodedStreamWidth = 0
        decodedStreamHeight = 0
    }

    fun detach(next: NativeStreamClient) {
        if (client === next) {
            client = null
            touchMouseState.forgetCursorPosition()
            touchSlots.clear()
            decodedStreamWidth = 0
            decodedStreamHeight = 0
        }
    }

    /**
     * The system interrupted the touch session — entering PiP, or the activity going to background.
     * Releases any button we are holding so it cannot stick down on the host. The next direct tap
     * establishes its own origin, so lifecycle changes cannot leave a stale click offset behind.
     */
    fun releaseTouchMouseForLifecycle() {
        touchMouseState.reset(client)
        releaseAllNativeTouches()
    }

    fun setTouchMouseEnabled(enabled: Boolean) {
        touchMouseEnabled = enabled
        if (!enabled) {
            touchMouseState.reset(client)
        }
    }

    fun setMouseDirectClick(enabled: Boolean) {
        mouseDirectClick = enabled
        touchMouseState.reset(client)
    }

    fun setNativeTouchEnabled(enabled: Boolean) {
        if (nativeTouchEnabled == enabled) return
        nativeTouchEnabled = enabled
        // Leaving the mode mid-gesture would otherwise strand whatever fingers are down.
        releaseAllNativeTouches()
        nativeTouchDownPoints.clear()
        touchMouseState.reset(client)
    }

    fun setNativeTouchSettings(scrollScale: Float, jitterThresholdDp: Float) {
        val density = android.content.res.Resources.getSystem().displayMetrics.density
        nativeTouchScrollScale = scrollScale
        nativeTouchJitterThresholdPx = jitterThresholdDp * density
    }

    fun setStretchToFit(enabled: Boolean) {
        if (stretchToFit != enabled) {
            stretchToFit = enabled
            touchMouseState.reset(client)
        }
    }

    fun setRenderingAspectRatio(ratio: Float) {
        if (renderingAspectRatio != ratio) {
            renderingAspectRatio = ratio
            touchMouseState.reset(client)
        }
    }

    fun setDecodedStreamResolution(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            decodedStreamWidth = width
            decodedStreamHeight = height
        }
    }

    fun setCaptureAllTouch(enabled: Boolean) {
        captureAllTouch = enabled
    }

    fun setSystemMenuHandler(handler: (() -> Unit)?) {
        systemMenuHandler = handler
    }

    fun setSystemBackHandler(handler: (() -> Unit)?) {
        systemBackHandler = handler
    }

    fun dispatchSystemBack(): Boolean {
        val handler = systemBackHandler ?: return false
        handler()
        return true
    }


    fun setStreamUiActive(active: Boolean) {
        streamUiActive = active
    }

    fun normalizedStreamUiKeyCode(event: KeyEvent): Int? {
        if (!streamUiActive) return null
        return when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_SELECT -> KeyEvent.KEYCODE_BACK
            else -> null
        }
    }

    fun normalizedAppUiKeyCode(event: KeyEvent): Int? {
        return normalizedAppUiKeyCode(event.keyCode, streamUiActive)
    }

    fun normalizedAppUiKeyCode(keyCode: Int, streamUiActive: Boolean): Int? {
        if (streamUiActive) return null
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
            else -> null
        }
    }

    fun isControllerAppBackKey(event: KeyEvent): Boolean =
        isControllerAppBackKey(
            keyCode = event.keyCode,
            controllerSource = event.isControllerInputDevice(),
            streamUiActive = streamUiActive,
        )

    fun isControllerAppBackKey(keyCode: Int, controllerSource: Boolean, streamUiActive: Boolean): Boolean =
        !streamUiActive &&
            (keyCode == KeyEvent.KEYCODE_BUTTON_B ||
                (keyCode == KeyEvent.KEYCODE_BACK && controllerSource))

    fun setUiTouchPassthroughBounds(left: Int, top: Int, right: Int, bottom: Int) {
        streamChromePassthroughBounds = TouchPassthroughBounds(left, top, right, bottom)
    }

    fun clearUiTouchPassthroughBounds() {
        streamChromePassthroughBounds = null
        uiTouchPassthroughActive = false
        nativeUiTouchPointerIds.clear()
    }

    fun setOverlayTouchPassthroughBound(id: String, left: Int, top: Int, right: Int, bottom: Int) {
        overlayPassthroughBounds = overlayPassthroughBounds.toMutableMap().also {
            it[id] = TouchPassthroughBounds(left, top, right, bottom)
        }
    }

    fun clearOverlayTouchPassthroughBound(id: String) {
        if (id !in overlayPassthroughBounds) return
        overlayPassthroughBounds = overlayPassthroughBounds.toMutableMap().also { it.remove(id) }
        uiTouchPassthroughActive = false
        nativeUiTouchPointerIds.clear()
    }

    fun setStreamPanelTouchPassthroughBounds(left: Int, top: Int, right: Int, bottom: Int) {
        streamPanelPassthroughBounds = TouchPassthroughBounds(left, top, right, bottom)
    }

    fun clearStreamPanelTouchPassthroughBounds() {
        streamPanelPassthroughBounds = null
        uiTouchPassthroughActive = false
        nativeUiTouchPointerIds.clear()
    }

    fun setTouchControllerPassthroughBounds(left: Int, top: Int, right: Int, bottom: Int) {
        touchControllerPassthroughBounds = mapOf("default" to TouchPassthroughBounds(left, top, right, bottom))
    }

    fun setTouchControllerPassthroughBound(id: String, left: Int, top: Int, right: Int, bottom: Int) {
        touchControllerPassthroughBounds = touchControllerPassthroughBounds.toMutableMap().also {
            it[id] = TouchPassthroughBounds(left, top, right, bottom)
        }
    }

    fun clearTouchControllerPassthroughBound(id: String) {
        if (id !in touchControllerPassthroughBounds) return
        touchControllerPassthroughBounds = touchControllerPassthroughBounds.toMutableMap().also { it.remove(id) }
    }

    fun setTouchControllerVisible(visible: Boolean) {
        touchControllerVisible = visible
        if (!visible) {
            touchControllerPassthroughBounds = emptyMap()
            uiTouchPassthroughActive = false
            nativeUiTouchPointerIds.clear()
        }
    }

    fun clearTouchControllerPassthroughBounds() {
        touchControllerPassthroughBounds = emptyMap()
        touchControllerVisible = false
        uiTouchPassthroughActive = false
        nativeUiTouchPointerIds.clear()
    }

    fun cancelTouchMouse() {
        touchMouseState.reset(client)
    }

    fun isNativeUiTouchGestureActive(): Boolean =
        nativeUiTouchPointerIds.isNotEmpty()

    fun shouldForwardTouchBeforeViews(event: MotionEvent, width: Int, height: Int): Boolean {
        val isDirectClick = mouseDirectClick && event.isExternalMousePointerEvent()
        val isNativeTouch = nativeTouchEnabled && event.isFingerTouchEvent()
        if (
            client == null ||
            streamUiActive ||
            !(touchMouseEnabled || isDirectClick || isNativeTouch) ||
            !captureAllTouch ||
            width <= 0 ||
            height <= 0 ||
            !(event.isFingerTouchEvent() || isDirectClick)
        ) {
            return false
        }
        updateNativeUiTouchPointers(event, width, height)
        if (!eventHasStreamTouchPointer(event, width, height)) return false
        // The single-pointer restriction below belongs to the cursor paths, where only one finger
        // can drive the pointer. Native touch forwards every finger by definition, so a two-finger
        // gesture reaching this point is the normal case rather than something to hand to the views.
        if (isNativeTouch) return true
        return event.pointerCount == 1 || nativeUiTouchPointerIds.isNotEmpty()
    }

    fun shouldCaptureTouchBeforeViews(event: MotionEvent, width: Int, height: Int): Boolean =
        shouldForwardTouchBeforeViews(event, width, height) &&
            nativeUiTouchPointerIds.isEmpty()

    fun dispatchTouch(event: MotionEvent, width: Int, height: Int): Boolean {
        val current = client ?: return false
        if (streamUiActive) return false
        // Direct click supports both external mouse/touchpad events AND finger touch events,
        // as long as the user has enabled mouseDirectClick in settings.
        val isDirectClick = mouseDirectClick && (event.isExternalMousePointerEvent() || event.isFingerTouchEvent())
        if (!event.isFingerTouchEvent() && !isDirectClick) return false
        updateNativeUiTouchPointers(event, width, height)
        if (nativeTouchEnabled && event.isFingerTouchEvent() && width > 0 && height > 0) {
            return dispatchNativeTouch(event, current, width, height)
        }
        return touchMouseState.handle(
            event = event,
            enabled = (touchMouseEnabled || isDirectClick) && width > 0 && height > 0,
            client = current,
            ignoredPointerIds = nativeUiTouchPointerIds,
            directClick = mouseDirectClick,
            width = width,
            height = height,
            stretchToFit = stretchToFit,
            renderingAspectRatio = renderingAspectRatio,
            decodedStreamWidth = decodedStreamWidth,
            decodedStreamHeight = decodedStreamHeight,
        )
    }

    /**
     * Forwards every finger as native touch, so the host presents a digitizer and touch-aware games
     * switch to their own mobile UI. Unlike the cursor paths this keeps no per-gesture state of its
     * own — the only thing carried across events is the pointer-id to slot mapping.
     */
    private fun dispatchNativeTouch(
        event: MotionEvent,
        client: NativeStreamClient,
        width: Int,
        height: Int,
    ): Boolean {
        val phase = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> TouchPhase.DOWN
            MotionEvent.ACTION_MOVE -> TouchPhase.MOVE
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> TouchPhase.UP
            MotionEvent.ACTION_CANCEL -> TouchPhase.CANCEL
            else -> return false
        }

        // Android reports which pointer changed only for the down/up actions; a MOVE carries fresh
        // positions for every finger at once, and all of them belong in the batch.
        val indices = if (phase == TouchPhase.MOVE || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            0 until event.pointerCount
        } else {
            val index = if (event.actionMasked == MotionEvent.ACTION_DOWN) 0 else event.actionIndex
            index..index
        }

        val scrollScale = nativeTouchScrollScale.coerceIn(0.25f, 2.0f)
        val jitterThresholdPx = nativeTouchJitterThresholdPx.coerceAtLeast(0f)

        val pointers = indices.mapNotNull { index ->
            if (index !in 0 until event.pointerCount) return@mapNotNull null
            val pointerId = event.getPointerId(index)
            // Fingers on our own chrome belong to the overlay, not the game.
            if (pointerId in nativeUiTouchPointerIds) return@mapNotNull null

            val rawX = event.getX(index)
            val rawY = event.getY(index)

            when (phase) {
                TouchPhase.DOWN -> {
                    // Record the starting position for jitter guard.
                    nativeTouchDownPoints[pointerId] = rawX to rawY
                }
                TouchPhase.MOVE -> {
                    val down = nativeTouchDownPoints[pointerId]
                    if (down != null && jitterThresholdPx > 0f) {
                        val dx = rawX - down.first
                        val dy = rawY - down.second
                        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                        // Suppress MOVE events until the finger has moved far enough from its
                        // initial touch point. This eliminates sensor jitter being interpreted
                        // as a micro-swipe that triggers a double-click or unintended scroll.
                        if (distance < jitterThresholdPx) return@mapNotNull null
                    }
                }
                TouchPhase.UP, TouchPhase.CANCEL -> {
                    nativeTouchDownPoints.remove(pointerId)
                }
            }

            // Apply scroll-scale to MOVE positions by interpolating from the DOWN point.
            // This scales the apparent velocity of gesture without clamping coordinates.
            val scaledX: Float
            val scaledY: Float
            if (phase == TouchPhase.MOVE && scrollScale != 1.0f) {
                val down = nativeTouchDownPoints[pointerId]
                if (down != null) {
                    scaledX = down.first + (rawX - down.first) * scrollScale
                    scaledY = down.second + (rawY - down.second) * scrollScale
                } else {
                    scaledX = rawX
                    scaledY = rawY
                }
            } else {
                scaledX = rawX
                scaledY = rawY
            }

            TouchPointerSample(
                pointerId = pointerId,
                x = scaledX,
                y = scaledY,
                radiusX = event.getTouchMajor(index) / 2f,
                radiusY = event.getTouchMinor(index) / 2f,
            )
        }
        if (pointers.isEmpty()) return false

        val (streamWidth, streamHeight) = streamResolutionPixels(client.settings)
        val records = buildTouchBatch(
            allocator = touchSlots,
            phase = phase,
            pointers = pointers,
            viewWidth = width,
            viewHeight = height,
            streamWidth = streamWidth,
            streamHeight = streamHeight,
            stretchToFit = stretchToFit,
            renderingAspectRatio = renderingAspectRatio,
        )
        if (records.isEmpty()) return false
        return client.sendNativeTouch(records)
    }

    /**
     * Lifts every finger the host still believes is down. Called when touch is turned off or the
     * session is interrupted, because in neither case will the platform deliver the missing UP.
     */
    private fun releaseAllNativeTouches() {
        val current = client
        val pointerIds = touchSlots.activePointerIds()
        if (current != null && pointerIds.isNotEmpty()) {
            val records = pointerIds.mapNotNull { pointerId ->
                touchSlots.release(pointerId)?.let { slot ->
                    TouchRecord(slot = slot, phase = TouchPhase.CANCEL, x = 0, y = 0)
                }
            }
            if (records.isNotEmpty()) current.sendNativeTouch(records)
        }
        touchSlots.clear()
    }

    fun dispatchExternalMouseTouch(event: MotionEvent, width: Int, height: Int): Boolean {
        if (streamUiActive) return false
        if (!event.isExternalMousePointerEvent()) return false
        if (mouseDirectClick) return false // Handled in dispatchTouch instead
        if (shouldPassTouchToNativeUi(event, width, height)) return false
        return client?.dispatchMotion(event) == true
    }

    fun dispatchKey(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && event.isStreamSystemMenuKey()) {
            systemMenuHandler?.invoke()
            return systemMenuHandler != null
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && event.isStreamControlsShortcutKey()) {
            systemMenuHandler?.invoke()
            return systemMenuHandler != null
        }
        val streamExitShortcut = event.isStreamExitShortcutKey()
        if (
            androidTvProfile &&
            event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount == 0 &&
            (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_BUTTON_B)
        ) {
            NativeInputDiagnostics.add(
                "tv back key key=${event.keyCode} source=${event.source} device=${event.deviceId}:${event.device?.name.orEmpty()} " +
                    "controller=${event.isControllerInputDevice()} dpad=${event.isDpadSource()} " +
                    "route=${if (streamExitShortcut) "stream_overlay" else "cloud_input"}",
            )
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && streamExitShortcut) {
            return dispatchSystemBack()
        }
        if (event.action == KeyEvent.ACTION_UP && streamExitShortcut) {
            return systemBackHandler != null
        }
        if (streamUiActive) return false
        val current = client ?: return false
        if (event.shouldConsumeAsStreamKeyboard()) {
            current.dispatchKey(event)
            return true
        }
        return current.dispatchKey(event)
    }

    fun dispatchMotion(event: MotionEvent): Boolean {
        if (streamUiActive && event.isExternalMousePointerEvent()) {
            return false
        }
        if (streamUiActive && event.isNativeUiNavigationMotion()) {
            return false
        }
        return client?.dispatchMotion(event) == true
    }

    private fun KeyEvent.isStreamSystemMenuKey(): Boolean =
        shouldOpenStreamSystemMenuKey(
            keyCode = keyCode,
            controllerInputDevice = isControllerInputDevice(),
        )

    private fun KeyEvent.isStreamControlsShortcutKey(): Boolean =
        !streamUiActive &&
            !isControllerInputDevice() &&
            !isHardwareKeyboardSource() &&
            isDpadSource() &&
            (keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                keyCode == KeyEvent.KEYCODE_DPAD_CENTER)

    private fun KeyEvent.isStreamExitShortcutKey(): Boolean =
        shouldHandleStreamExitKey(
            keyCode = keyCode,
            controllerInputDevice = isControllerInputDevice(),
            hardwareKeyboardSource = isHardwareKeyboardSource(),
            androidTvProfile = androidTvProfile,
            dpadSource = isDpadSource(),
        )

    fun shouldOpenStreamSystemMenuKey(keyCode: Int, controllerInputDevice: Boolean): Boolean =
        keyCode == KeyEvent.KEYCODE_MENU && !controllerInputDevice

    fun shouldHandleStreamExitKey(
        keyCode: Int,
        controllerInputDevice: Boolean,
        hardwareKeyboardSource: Boolean,
        androidTvProfile: Boolean = false,
        dpadSource: Boolean = false,
    ): Boolean =
        (keyCode == KeyEvent.KEYCODE_BACK && !controllerInputDevice) ||
            (androidTvProfile &&
                dpadSource &&
                keyCode == KeyEvent.KEYCODE_BUTTON_B &&
                !controllerInputDevice) ||
            (keyCode == KeyEvent.KEYCODE_ESCAPE && !hardwareKeyboardSource)

    private fun KeyEvent.isControllerInputDevice(): Boolean =
        AndroidControllerInput.isControllerEvent(source, deviceId)

    private fun KeyEvent.isDpadSource(): Boolean =
        (source and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD

    private fun KeyEvent.isHardwareKeyboardSource(): Boolean =
        !isControllerInputDevice() &&
            ((source and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD ||
                InputDevice.getDevice(deviceId)?.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC)

    private fun KeyEvent.shouldConsumeAsStreamKeyboard(): Boolean =
        (action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_UP) &&
            !isControllerInputDevice() &&
            !isAndroidSystemKey() &&
            (isHardwareKeyboardSource() || keyCode.isTextEntryKeyCode())

    private fun KeyEvent.isAndroidSystemKey(): Boolean =
        keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_VOLUME_MUTE ||
            keyCode == KeyEvent.KEYCODE_POWER ||
            keyCode == KeyEvent.KEYCODE_HOME

    private fun Int.isKeyboardLikeKeyCode(): Boolean =
        this == KeyEvent.KEYCODE_ENTER ||
            this == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            this == KeyEvent.KEYCODE_ESCAPE ||
            this == KeyEvent.KEYCODE_DEL ||
            this == KeyEvent.KEYCODE_TAB ||
            this == KeyEvent.KEYCODE_SPACE ||
            this == KeyEvent.KEYCODE_DPAD_LEFT ||
            this == KeyEvent.KEYCODE_DPAD_UP ||
            this == KeyEvent.KEYCODE_DPAD_RIGHT ||
            this == KeyEvent.KEYCODE_DPAD_DOWN ||
            this == KeyEvent.KEYCODE_PAGE_UP ||
            this == KeyEvent.KEYCODE_PAGE_DOWN ||
            this == KeyEvent.KEYCODE_FORWARD_DEL ||
            this == KeyEvent.KEYCODE_INSERT ||
            this == KeyEvent.KEYCODE_MOVE_HOME ||
            this == KeyEvent.KEYCODE_MOVE_END ||
            this == KeyEvent.KEYCODE_SHIFT_LEFT ||
            this == KeyEvent.KEYCODE_SHIFT_RIGHT ||
            this == KeyEvent.KEYCODE_CTRL_LEFT ||
            this == KeyEvent.KEYCODE_CTRL_RIGHT ||
            this == KeyEvent.KEYCODE_ALT_LEFT ||
            this == KeyEvent.KEYCODE_ALT_RIGHT ||
            this == KeyEvent.KEYCODE_CAPS_LOCK ||
            this == KeyEvent.KEYCODE_NUM_LOCK ||
            this == KeyEvent.KEYCODE_SCROLL_LOCK ||
            this == KeyEvent.KEYCODE_MINUS ||
            this == KeyEvent.KEYCODE_EQUALS ||
            this == KeyEvent.KEYCODE_LEFT_BRACKET ||
            this == KeyEvent.KEYCODE_RIGHT_BRACKET ||
            this == KeyEvent.KEYCODE_BACKSLASH ||
            this == KeyEvent.KEYCODE_SEMICOLON ||
            this == KeyEvent.KEYCODE_APOSTROPHE ||
            this == KeyEvent.KEYCODE_COMMA ||
            this == KeyEvent.KEYCODE_PERIOD ||
            this == KeyEvent.KEYCODE_SLASH ||
            this == KeyEvent.KEYCODE_GRAVE ||
            this in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z ||
            this in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ||
            this in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 ||
            this in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12

    private fun Int.isTextEntryKeyCode(): Boolean =
        this == KeyEvent.KEYCODE_ENTER ||
            this == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            this == KeyEvent.KEYCODE_ESCAPE ||
            this == KeyEvent.KEYCODE_DEL ||
            this == KeyEvent.KEYCODE_TAB ||
            this == KeyEvent.KEYCODE_SPACE ||
            this == KeyEvent.KEYCODE_FORWARD_DEL ||
            this == KeyEvent.KEYCODE_SHIFT_LEFT ||
            this == KeyEvent.KEYCODE_SHIFT_RIGHT ||
            this == KeyEvent.KEYCODE_CTRL_LEFT ||
            this == KeyEvent.KEYCODE_CTRL_RIGHT ||
            this == KeyEvent.KEYCODE_ALT_LEFT ||
            this == KeyEvent.KEYCODE_ALT_RIGHT ||
            this == KeyEvent.KEYCODE_CAPS_LOCK ||
            this == KeyEvent.KEYCODE_MINUS ||
            this == KeyEvent.KEYCODE_EQUALS ||
            this == KeyEvent.KEYCODE_LEFT_BRACKET ||
            this == KeyEvent.KEYCODE_RIGHT_BRACKET ||
            this == KeyEvent.KEYCODE_BACKSLASH ||
            this == KeyEvent.KEYCODE_SEMICOLON ||
            this == KeyEvent.KEYCODE_APOSTROPHE ||
            this == KeyEvent.KEYCODE_COMMA ||
            this == KeyEvent.KEYCODE_PERIOD ||
            this == KeyEvent.KEYCODE_SLASH ||
            this == KeyEvent.KEYCODE_GRAVE ||
            this in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z ||
            this in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ||
            this in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 ||
            this in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12

    private fun MotionEvent.isNativeUiNavigationMotion(): Boolean =
        isFromSource(InputDevice.SOURCE_JOYSTICK) ||
            isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            AndroidControllerInput.isControllerEvent(source, deviceId)

    private fun MotionEvent.isFromSource(source: Int): Boolean = (this.source and source) == source

    private fun MotionEvent.isFingerTouchEvent(): Boolean =
        isFromSource(InputDevice.SOURCE_TOUCHSCREEN) &&
            !isFromSource(InputDevice.SOURCE_MOUSE) &&
            !isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE)

    private fun MotionEvent.isExternalMousePointerEvent(): Boolean {
        val controllerSource = isFromSource(InputDevice.SOURCE_JOYSTICK) || isFromSource(InputDevice.SOURCE_GAMEPAD)
        return isFromSource(InputDevice.SOURCE_MOUSE) ||
            isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE) ||
            (isFromSource(InputDevice.SOURCE_TOUCHPAD) && !controllerSource)
    }

    private fun shouldPassTouchToNativeUi(event: MotionEvent, width: Int, height: Int): Boolean {
        if (event.isFingerTouchEvent()) {
            updateNativeUiTouchPointers(event, width, height)
            return eventHasNativeUiTouchPointer(event, width, height) &&
                !eventHasStreamTouchPointer(event, width, height)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                uiTouchPassthroughActive =
                    pointerTouchesNativeUi(event, 0, width, height)
                return uiTouchPassthroughActive
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasActive = uiTouchPassthroughActive
                uiTouchPassthroughActive = false
                return wasActive
            }
            else -> if (uiTouchPassthroughActive) {
                return true
            }
        }
        return false
    }

    private fun updateNativeUiTouchPointers(event: MotionEvent, width: Int, height: Int) {
        if (!event.isFingerTouchEvent()) return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                nativeUiTouchPointerIds.clear()
                if (pointerTouchesNativeUi(event, 0, width, height)) {
                    nativeUiTouchPointerIds += event.getPointerId(0)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                if (index in 0 until event.pointerCount && pointerTouchesNativeUi(event, index, width, height)) {
                    nativeUiTouchPointerIds += event.getPointerId(index)
                }
            }
        }
        uiTouchPassthroughActive = nativeUiTouchPointerIds.isNotEmpty()
    }

    fun postDispatchTouch(event: MotionEvent) {
        if (!event.isFingerTouchEvent()) return
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                if (index in 0 until event.pointerCount) {
                    nativeUiTouchPointerIds.remove(event.getPointerId(index))
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                nativeUiTouchPointerIds.clear()
            }
        }
        uiTouchPassthroughActive = nativeUiTouchPointerIds.isNotEmpty()
    }

    private fun eventHasNativeUiTouchPointer(event: MotionEvent, width: Int, height: Int): Boolean =
        (0 until event.pointerCount).any { index ->
            isNativeUiTouchPointer(event, index, width, height)
        }

    private fun eventHasStreamTouchPointer(event: MotionEvent, width: Int, height: Int): Boolean =
        (0 until event.pointerCount).any { index ->
            !isNativeUiTouchPointer(event, index, width, height)
        }

    private fun isNativeUiTouchPointer(event: MotionEvent, index: Int, width: Int, height: Int): Boolean =
        event.getPointerId(index) in nativeUiTouchPointerIds ||
            pointerTouchesNativeUi(event, index, width, height)

    private fun pointerTouchesNativeUi(event: MotionEvent, index: Int, width: Int, height: Int): Boolean {
        if (index !in 0 until event.pointerCount) return false
        val x = event.getX(index)
        val y = event.getY(index)

        // Narrow edge exclusion zone: 12dp is enough to capture system back-swipe gestures while
        // still allowing game UI elements placed near the screen edges to be reached.
        val density = android.content.res.Resources.getSystem().displayMetrics.density
        val edgeWidthPx = 12f * density
        val isNearEdge = !androidTvProfile && width > 0 && (x < edgeWidthPx || x > width - edgeWidthPx)
        if (isNearEdge) return true

        return streamChromePassthroughBounds?.contains(x, y) == true ||
            streamPanelPassthroughBounds?.contains(x, y) == true ||
            overlayPassthroughBounds.values.any { it.contains(x, y) } ||
            touchControllerContains(x, y, width, height)
    }

    private fun touchControllerContains(x: Float, y: Float, width: Int, height: Int): Boolean {
        val bounds = touchControllerPassthroughBounds
        if (bounds.isNotEmpty()) return bounds.values.any { it.contains(x, y) }
        return touchControllerVisible &&
            width > 0 &&
            height > 0 &&
            y >= height * TOUCH_CONTROLLER_FALLBACK_TOP_RATIO
    }

    private data class TouchPassthroughBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        fun contains(x: Float, y: Float): Boolean =
            x >= left - EDGE_SLOP_PX &&
                x <= right + EDGE_SLOP_PX &&
                y >= top - EDGE_SLOP_PX &&
                y <= bottom + EDGE_SLOP_PX

        companion object {
            private const val EDGE_SLOP_PX = 24
        }
    }

    private const val TOUCH_CONTROLLER_FALLBACK_TOP_RATIO = 0.52f
}

enum class InputDataChannelRole {
    Reliable,
    PartiallyReliable,
    Other,
}

object InputDataChannelLabels {
    fun classify(label: String): InputDataChannelRole =
        when (label.lowercase(Locale.US)) {
            "input_channel_v1",
            "input_channel",
            -> InputDataChannelRole.Reliable
            "input_channel_partially_reliable",
            "input_channel_pr",
            -> InputDataChannelRole.PartiallyReliable
            else -> InputDataChannelRole.Other
        }
}

internal enum class AndroidControllerFamily {
    Google,
    Xbox,
    PlayStation,
    Nintendo,
    Generic,
}

internal object AndroidControllerInput {
    fun hasControllerSource(source: Int): Boolean =
        source.hasSource(InputDevice.SOURCE_GAMEPAD) ||
            source.hasSource(InputDevice.SOURCE_JOYSTICK)

    fun isControllerDevice(device: InputDevice?): Boolean =
        device != null && isControllerDevice(device.sources, device.name)

    fun isControllerDevice(source: Int, deviceName: String?): Boolean =
        hasControllerSource(source) ||
            (source.hasSource(InputDevice.SOURCE_DPAD) && isKnownControllerName(deviceName))

    fun isControllerEvent(source: Int, deviceId: Int): Boolean =
        hasControllerSource(source) ||
            isControllerDevice(InputDevice.getDevice(deviceId))

    fun isKnownControllerName(name: String?): Boolean {
        val normalized = name.orEmpty().lowercase(Locale.US)
        return normalized.contains("stadia controller") ||
            normalized == "stadia" ||
            normalized.contains("google stadia") ||
            normalized.contains("dualsense") ||
            normalized.contains("dualshock") ||
            normalized.contains("wireless controller") ||
            normalized.contains("xbox") ||
            normalized.contains("x-input") ||
            normalized.contains("xinput") ||
            normalized.contains("8bitdo") ||
            normalized.contains("gamesir") ||
            normalized.contains("backbone") ||
            normalized.contains("razer kishi") ||
            normalized.contains("switch pro") ||
            normalized.contains("gamepad")
    }

    fun controllerFamily(device: InputDevice?): AndroidControllerFamily? =
        device
            ?.takeIf(::isControllerDevice)
            ?.let { controllerFamily(it.name) }

    internal fun controllerFamily(name: String?): AndroidControllerFamily {
        val normalized = name.orEmpty().lowercase(Locale.US)
        return when {
            normalized.contains("stadia") ||
                normalized.contains("google") ||
                normalized.contains("chromecast") -> AndroidControllerFamily.Google
            normalized.contains("xbox") ||
                normalized.contains("x-input") ||
                normalized.contains("xinput") -> AndroidControllerFamily.Xbox
            normalized.contains("dualsense") ||
                normalized.contains("dualshock") ||
                normalized.contains("playstation") ||
                normalized == "wireless controller" -> AndroidControllerFamily.PlayStation
            normalized.contains("switch") || normalized.contains("nintendo") -> AndroidControllerFamily.Nintendo
            else -> AndroidControllerFamily.Generic
        }
    }

    fun isPrimaryActivationKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

    private fun Int.hasSource(source: Int): Boolean = (this and source) == source
}

internal data class AndroidControllerSlotAssignment(
    val slot: Int,
    val removedDevices: Map<Int, Int>,
)

internal object AndroidControllerSlotRegistry {
    fun retainConnected(
        controllerSlots: MutableMap<Int, Int>,
        connectedDeviceIds: Set<Int>,
    ): Map<Int, Int> {
        val removedDevices = controllerSlots.filterKeys { it !in connectedDeviceIds }
        removedDevices.keys.forEach(controllerSlots::remove)
        return removedDevices
    }

    fun assign(
        controllerSlots: MutableMap<Int, Int>,
        deviceId: Int,
        connectedDeviceIds: Set<Int>,
        maxControllers: Int,
    ): AndroidControllerSlotAssignment {
        val stableDeviceId = if (deviceId >= 0) deviceId else 0
        val removedDevices = retainConnected(
            controllerSlots = controllerSlots,
            connectedDeviceIds = connectedDeviceIds + stableDeviceId,
        )
        val existingSlot = controllerSlots[stableDeviceId]
        if (existingSlot != null) {
            return AndroidControllerSlotAssignment(existingSlot, removedDevices)
        }
        val usedSlots = controllerSlots.values.toSet()
        val slot = (0 until maxControllers).firstOrNull { it !in usedSlots } ?: 0
        controllerSlots[stableDeviceId] = slot
        return AndroidControllerSlotAssignment(slot, removedDevices)
    }
}

internal data class ControllerMouseDelta(
    val dx: Int,
    val dy: Int,
)

internal object AndroidControllerMouseAssist {
    fun mouseDelta(stickX: Float, stickY: Float): ControllerMouseDelta? {
        if (!stickX.isFinite() || !stickY.isFinite()) return null
        val x = stickX.coerceIn(-1f, 1f)
        val y = stickY.coerceIn(-1f, 1f)
        val magnitude = sqrt(x * x + y * y).coerceIn(0f, 1f)
        if (magnitude < 0.001f) return null
        val speed = CONTROLLER_MOUSE_BASE_DELTA_PX + CONTROLLER_MOUSE_ACCEL_DELTA_PX * magnitude * magnitude
        val dx = (x * speed).roundToInt()
        val dy = (y * speed).roundToInt()
        return if (dx != 0 || dy != 0) ControllerMouseDelta(dx, dy) else null
    }

    fun scrollNotches(stickY: Float, scrollSensitivity: Int, accumulator: Float): Pair<Int, Float> {
        if (!stickY.isFinite() || !accumulator.isFinite()) return Pair(0, 0f)
        val y = stickY.coerceIn(-1f, 1f)
        if (abs(y) < 0.1f) return Pair(0, accumulator)

        val sensitivity = scrollSensitivity.toFloat().coerceIn(10f, 100f)
        val factor = 6.0f / sensitivity
        val nextAccumulator = accumulator + -y * factor
        val notches = nextAccumulator.toInt()
        return Pair(notches, nextAccumulator - notches)
    }

    fun mouseButtonForGamepad(buttonMask: Int): Int? =
        when (buttonMask) {
            GamepadButtonMapping.A -> 1
            GamepadButtonMapping.B -> 3
            else -> null
        }

    fun mouseButtonForTrigger(left: Boolean): Int? = null

    private const val CONTROLLER_MOUSE_BASE_DELTA_PX = 7f
    private const val CONTROLLER_MOUSE_ACCEL_DELTA_PX = 34f
}

internal data class AndroidGamepadRawAxes(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val rz: Float = 0f,
    val rx: Float = 0f,
    val ry: Float = 0f,
    val hatX: Float = 0f,
    val hatY: Float = 0f,
)

internal data class AndroidGamepadAxisAvailability(
    val x: Boolean = true,
    val y: Boolean = true,
    val z: Boolean = true,
    val rz: Boolean = true,
    val rx: Boolean = true,
    val ry: Boolean = true,
    val hatX: Boolean = true,
    val hatY: Boolean = true,
) {
    fun hasLeftStickPair(): Boolean = x && y
    fun hasHatPair(): Boolean = hatX && hatY
}

internal data class AndroidGamepadResolvedAxes(
    val leftX: Float,
    val leftY: Float,
    val rightX: Float,
    val rightY: Float,
    val leftSource: String,
    val rightSource: String,
    val hatUsedAsLeftStick: Boolean,
)

internal object AndroidGamepadAxisMapping {
    fun resolve(raw: AndroidGamepadRawAxes, available: AndroidGamepadAxisAvailability = AndroidGamepadAxisAvailability()): AndroidGamepadResolvedAxes {
        val rightUsesZRz = axisPairActive(raw.z, raw.rz) || !axisPairActive(raw.rx, raw.ry)
        val rightX = if (rightUsesZRz) raw.z else raw.rx
        val rightY = if (rightUsesZRz) raw.rz else raw.ry
        val rightSource = if (rightUsesZRz) "z/rz" else "rx/ry"

        val useHatForLeft =
            !available.hasLeftStickPair() &&
                available.hasHatPair() &&
                axisPairActive(raw.hatX, raw.hatY)
        val leftX = if (useHatForLeft) raw.hatX else raw.x
        val leftY = if (useHatForLeft) raw.hatY else raw.y
        return AndroidGamepadResolvedAxes(
            leftX = leftX,
            leftY = leftY,
            rightX = rightX,
            rightY = rightY,
            leftSource = if (useHatForLeft) "hat" else "x/y",
            rightSource = rightSource,
            hatUsedAsLeftStick = useHatForLeft,
        )
    }

    private fun axisPairActive(x: Float, y: Float): Boolean =
        abs(x) > AXIS_NOISE || abs(y) > AXIS_NOISE

    private const val AXIS_NOISE = 0.001f
}

internal data class GamepadRumbleCommand(
    val controllerId: Int,
    val weakMagnitude: Int,
    val strongMagnitude: Int,
)

internal object HapticsPacketParser {
    fun parse(bytes: ByteArray): GamepadRumbleCommand? {
        if (bytes.size < 2) return null
        val view = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val firstWord = view.getShort(0).toInt() and 0xffff
        if (firstWord == LEGACY_HAPTIC_SUBMESSAGE_TYPE) {
            return parseLegacy(view, 2)
        }

        return when (firstWord and 0xff) {
            WRAPPER_SINGLE_EVENT -> parseSubMessage(view, 1)
            WRAPPER_BATCHED_EVENT,
            WRAPPER_LEGACY_INPUT,
            WRAPPER_TIMESTAMPED_SINGLE,
            WRAPPER_TIMESTAMPED_BATCHED,
            WRAPPER_RESERVED,
            -> null
            else -> parseLegacy(view, 0)
        }
    }

    private fun parseSubMessage(view: ByteBuffer, offset: Int): GamepadRumbleCommand? {
        if (offset < 0 || offset + 4 > view.limit()) return null
        val type = view.getInt(offset)
        return when (type) {
            LEGACY_HAPTIC_SUBMESSAGE_TYPE -> parseLegacy(view, offset + 4)
            OC_HAPTIC_SUBMESSAGE_TYPE -> parseOc(view, offset + 4)
            else -> null
        }
    }

    private fun parseLegacy(view: ByteBuffer, offset: Int): GamepadRumbleCommand? {
        if (offset < 0 || offset + 10 > view.limit()) return null
        val kind = view.getShort(offset).toInt() and 0xffff
        if (kind != 1) return null
        val length = view.getShort(offset + 2).toInt() and 0xffff
        if (length < 6) return null
        return GamepadRumbleCommand(
            controllerId = view.getShort(offset + 4).toInt() and 0xffff,
            weakMagnitude = view.getShort(offset + 6).toInt() and 0xffff,
            strongMagnitude = view.getShort(offset + 8).toInt() and 0xffff,
        )
    }

    private fun parseOc(view: ByteBuffer, offset: Int): GamepadRumbleCommand? {
        if (offset < 0 || offset + 9 > view.limit()) return null
        val controllerByte = view.get(offset).toInt() and 0xff
        if (controllerByte !in 6 until 10) return null
        val reportKind = view.get(offset + 3).toInt() and 0xff
        val flags = view.get(offset + 4).toInt() and 0xff
        if (reportKind != 5 || (flags and 0xfe) != 0) return null
        return GamepadRumbleCommand(
            controllerId = controllerByte - 6,
            weakMagnitude = (view.get(offset + 7).toInt() and 0xff) shl 8,
            strongMagnitude = (view.get(offset + 8).toInt() and 0xff) shl 8,
        )
    }

    private const val LEGACY_HAPTIC_SUBMESSAGE_TYPE = 267
    private const val OC_HAPTIC_SUBMESSAGE_TYPE = 17
    private const val WRAPPER_BATCHED_EVENT = 32
    private const val WRAPPER_LEGACY_INPUT = 33
    private const val WRAPPER_SINGLE_EVENT = 34
    private const val WRAPPER_TIMESTAMPED_SINGLE = 35
    private const val WRAPPER_TIMESTAMPED_BATCHED = 36
    private const val WRAPPER_RESERVED = 255
}

internal object GamepadButtonMapping {
    const val DPAD_UP = 0x0001
    const val DPAD_DOWN = 0x0002
    const val DPAD_LEFT = 0x0004
    const val DPAD_RIGHT = 0x0008
    const val START = 0x0010
    const val BACK = 0x0020
    const val LEFT_THUMB = 0x0040
    const val RIGHT_THUMB = 0x0080
    const val LEFT_SHOULDER = 0x0100
    const val RIGHT_SHOULDER = 0x0200
    const val GUIDE = 0x0400
    const val A = 0x1000
    const val B = 0x2000
    const val X = 0x4000
    const val Y = 0x8000

    fun maskForKeyCode(keyCode: Int, controllerActivation: Boolean = false): Int? = when (keyCode) {
        KeyEvent.KEYCODE_MENU -> if (controllerActivation) START else null
        KeyEvent.KEYCODE_BACK -> if (controllerActivation) BACK else null
        KeyEvent.KEYCODE_DPAD_UP -> DPAD_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> DPAD_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> DPAD_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> DPAD_RIGHT
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        -> if (controllerActivation) A else null
        KeyEvent.KEYCODE_BUTTON_START -> START
        KeyEvent.KEYCODE_BUTTON_SELECT -> BACK
        KeyEvent.KEYCODE_BUTTON_THUMBL -> LEFT_THUMB
        KeyEvent.KEYCODE_BUTTON_THUMBR -> RIGHT_THUMB
        KeyEvent.KEYCODE_BUTTON_L1 -> LEFT_SHOULDER
        KeyEvent.KEYCODE_BUTTON_R1 -> RIGHT_SHOULDER
        KeyEvent.KEYCODE_BUTTON_MODE -> GUIDE
        KeyEvent.KEYCODE_BUTTON_A -> A
        KeyEvent.KEYCODE_BUTTON_B -> B
        KeyEvent.KEYCODE_BUTTON_X -> X
        KeyEvent.KEYCODE_BUTTON_Y -> Y
        else -> null
    }

    fun isControllerButtonKeyCode(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_BUTTON_L2 ||
            keyCode == KeyEvent.KEYCODE_BUTTON_R2 ||
            keyCode in KeyEvent.KEYCODE_BUTTON_A..KeyEvent.KEYCODE_BUTTON_MODE
}

internal object SteamMenuChord {
    // Send only the Guide (Home) button. The GUIDE+A chord previously used caused unintended
    // A-button input during gameplay; a plain Guide press is sufficient to open Steam overlay.
    fun buttons(aPressed: Boolean): Int = GamepadButtonMapping.GUIDE
}

internal class SteamOverlayChordState {
    private var latched = false
    private var chordPressed = false

    fun update(rawButtons: Int): Boolean {
        val topButtons = rawButtons and TOP_BUTTONS
        val activated = !latched && topButtons == TOP_BUTTONS
        if (activated) {
            latched = true
            chordPressed = true
        } else if (latched && topButtons == 0) {
            latched = false
            chordPressed = false
        }
        return activated
    }

    fun effectiveButtons(rawButtons: Int): Int {
        val withoutTopButtons = if (latched) rawButtons and TOP_BUTTONS.inv() else rawButtons
        return if (chordPressed) withoutTopButtons or SteamMenuChord.buttons(aPressed = true) else withoutTopButtons
    }

    fun releaseChord(): Boolean {
        if (!chordPressed) return false
        chordPressed = false
        return true
    }

    fun reset() {
        latched = false
        chordPressed = false
    }

    private companion object {
        const val TOP_BUTTONS = 0x0030
    }
}

internal fun streamSharpnessShaderStrength(enabled: Boolean, amount: Float): Float =
    if (enabled) amount.coerceIn(0f, 1f) * STREAM_SHARPNESS_MAX_SHADER_STRENGTH else 0f

private const val STREAM_SHARPNESS_MAX_SHADER_STRENGTH = 0.28f

private class StreamSharpnessGlDrawer : RendererCommon.GlDrawer {
    @Volatile
    var amount: Float = 0f

    private val vertexBuffer: FloatBuffer = GlUtil.createFloatBuffer(
        floatArrayOf(
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f,
        ),
    )
    private val textureBuffer: FloatBuffer = GlUtil.createFloatBuffer(
        floatArrayOf(
            0f, 0f,
            1f, 0f,
            0f, 1f,
            1f, 1f,
        ),
    )

    private var oesProgram: SharpnessProgram? = null
    private var rgbProgram: SharpnessProgram? = null
    private var yuvProgram: SharpnessProgram? = null

    override fun drawOes(
        oesTextureId: Int,
        texMatrix: FloatArray,
        frameWidth: Int,
        frameHeight: Int,
        viewportX: Int,
        viewportY: Int,
        viewportWidth: Int,
        viewportHeight: Int,
    ) {
        val program = oesProgram ?: SharpnessProgram(SHARPEN_OES_FRAGMENT_SHADER, TextureMode.Oes).also { oesProgram = it }
        program.draw(
            textureIds = intArrayOf(oesTextureId),
            textureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            texMatrix = texMatrix,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            viewportX = viewportX,
            viewportY = viewportY,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            amount = amount,
            vertexBuffer = vertexBuffer,
            textureBuffer = textureBuffer,
        )
    }

    override fun drawRgb(
        textureId: Int,
        texMatrix: FloatArray,
        frameWidth: Int,
        frameHeight: Int,
        viewportX: Int,
        viewportY: Int,
        viewportWidth: Int,
        viewportHeight: Int,
    ) {
        val program = rgbProgram ?: SharpnessProgram(SHARPEN_RGB_FRAGMENT_SHADER, TextureMode.Rgb).also { rgbProgram = it }
        program.draw(
            textureIds = intArrayOf(textureId),
            textureTarget = GLES20.GL_TEXTURE_2D,
            texMatrix = texMatrix,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            viewportX = viewportX,
            viewportY = viewportY,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            amount = amount,
            vertexBuffer = vertexBuffer,
            textureBuffer = textureBuffer,
        )
    }

    override fun drawYuv(
        yuvTextures: IntArray,
        texMatrix: FloatArray,
        frameWidth: Int,
        frameHeight: Int,
        viewportX: Int,
        viewportY: Int,
        viewportWidth: Int,
        viewportHeight: Int,
    ) {
        val program = yuvProgram ?: SharpnessProgram(SHARPEN_YUV_FRAGMENT_SHADER, TextureMode.Yuv).also { yuvProgram = it }
        program.draw(
            textureIds = yuvTextures,
            textureTarget = GLES20.GL_TEXTURE_2D,
            texMatrix = texMatrix,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            viewportX = viewportX,
            viewportY = viewportY,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            amount = amount,
            vertexBuffer = vertexBuffer,
            textureBuffer = textureBuffer,
        )
    }

    override fun release() {
        oesProgram?.release()
        rgbProgram?.release()
        yuvProgram?.release()
        oesProgram = null
        rgbProgram = null
        yuvProgram = null
    }

    private class SharpnessProgram(fragmentShader: String, private val mode: TextureMode) {
        private val shader = GlShader(SHARPEN_VERTEX_SHADER, fragmentShader)
        private val texMatrixLocation = shader.getUniformLocation("tex_mat")
        private val sharpnessLocation = shader.getUniformLocation("sharpness")
        private val texelSizeLocation = shader.getUniformLocation("texel_size")
        private val textureLocations: IntArray = when (mode) {
            TextureMode.Oes,
            TextureMode.Rgb,
            -> intArrayOf(shader.getUniformLocation("tex"))
            TextureMode.Yuv -> intArrayOf(
                shader.getUniformLocation("y_tex"),
                shader.getUniformLocation("u_tex"),
                shader.getUniformLocation("v_tex"),
            )
        }

        fun draw(
            textureIds: IntArray,
            textureTarget: Int,
            texMatrix: FloatArray,
            frameWidth: Int,
            frameHeight: Int,
            viewportX: Int,
            viewportY: Int,
            viewportWidth: Int,
            viewportHeight: Int,
            amount: Float,
            vertexBuffer: FloatBuffer,
            textureBuffer: FloatBuffer,
        ) {
            shader.useProgram()
            GLES20.glViewport(viewportX, viewportY, viewportWidth, viewportHeight)
            shader.setVertexAttribArray("in_pos", 2, vertexBuffer)
            shader.setVertexAttribArray("in_tc", 2, textureBuffer)
            GLES20.glUniformMatrix4fv(texMatrixLocation, 1, false, texMatrix, 0)
            GLES20.glUniform1f(sharpnessLocation, amount.coerceIn(0f, STREAM_SHARPNESS_MAX_SHADER_STRENGTH))
            GLES20.glUniform2f(
                texelSizeLocation,
                1f / frameWidth.coerceAtLeast(1).toFloat(),
                1f / frameHeight.coerceAtLeast(1).toFloat(),
            )
            textureLocations.forEachIndexed { index, location ->
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + index)
                GLES20.glBindTexture(textureTarget, textureIds.getOrElse(index) { 0 })
                GLES20.glUniform1i(location, index)
            }
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            textureLocations.indices.forEach { index ->
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + index)
                GLES20.glBindTexture(textureTarget, 0)
            }
            GlUtil.checkNoGLES2Error("StreamSharpnessGlDrawer.draw")
        }

        fun release() {
            shader.release()
        }
    }

    private enum class TextureMode {
        Oes,
        Rgb,
        Yuv,
    }

    private companion object {
        private const val SHARPEN_VERTEX_SHADER = """
            attribute vec4 in_pos;
            attribute vec2 in_tc;
            uniform mat4 tex_mat;
            varying vec2 tc;

            void main() {
              gl_Position = in_pos;
              tc = (tex_mat * vec4(in_tc, 0.0, 1.0)).xy;
            }
        """

        private const val SHARPEN_BODY = """
            uniform float sharpness;
            uniform vec2 texel_size;
            varying vec2 tc;

            void main() {
              vec4 center = sampleColor(tc);
              if (sharpness <= 0.001) {
                gl_FragColor = center;
                return;
              }
              vec3 north = sampleColor(tc + vec2(0.0, -texel_size.y)).rgb;
              vec3 south = sampleColor(tc + vec2(0.0, texel_size.y)).rgb;
              vec3 west = sampleColor(tc + vec2(-texel_size.x, 0.0)).rgb;
              vec3 east = sampleColor(tc + vec2(texel_size.x, 0.0)).rgb;
              vec3 sharpened = center.rgb * (1.0 + 4.0 * sharpness) - (north + south + west + east) * sharpness;
              gl_FragColor = vec4(clamp(sharpened, 0.0, 1.0), center.a);
            }
        """

        private const val SHARPEN_OES_FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES tex;
            vec4 sampleColor(vec2 pos) {
              return texture2D(tex, pos);
            }
        """ + SHARPEN_BODY

        private const val SHARPEN_RGB_FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D tex;
            vec4 sampleColor(vec2 pos) {
              return texture2D(tex, pos);
            }
        """ + SHARPEN_BODY

        private const val SHARPEN_YUV_FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D y_tex;
            uniform sampler2D u_tex;
            uniform sampler2D v_tex;
            vec4 sampleColor(vec2 pos) {
              float y = texture2D(y_tex, pos).r;
              float u = texture2D(u_tex, pos).r - 0.5;
              float v = texture2D(v_tex, pos).r - 0.5;
              return vec4(
                y + 1.403 * v,
                y - 0.344 * u - 0.714 * v,
                y + 1.770 * u,
                1.0
              );
            }
        """ + SHARPEN_BODY
    }
}

internal sealed interface StreamLivenessAction {
    data object None : StreamLivenessAction
    data class RequestKeyframe(val stalledMs: Long, val attempt: Int) : StreamLivenessAction
    data class RestartTransport(val stalledMs: Long) : StreamLivenessAction
}

internal class StreamLivenessWatchdog(
    private val keyframeAfterMs: Long = MEDIA_STALL_KEYFRAME_AFTER_MS,
    private val keyframeIntervalMs: Long = MEDIA_STALL_KEYFRAME_INTERVAL_MS,
    private val restartAfterMs: Long = MEDIA_STALL_RESTART_AFTER_MS,
) {
    private var lastProgressAtMs: Long? = null
    private var lastBytesReceived: Long? = null
    private var lastFramesDecoded: Long? = null
    private var lastKeyframeRequestAtMs = Long.MIN_VALUE
    private var keyframeAttempts = 0
    var latestObservationProgressed: Boolean = false
        private set

    fun reset() {
        lastProgressAtMs = null
        lastBytesReceived = null
        lastFramesDecoded = null
        lastKeyframeRequestAtMs = Long.MIN_VALUE
        keyframeAttempts = 0
        latestObservationProgressed = false
    }

    fun markConnected(nowMs: Long) {
        lastProgressAtMs = nowMs
        lastKeyframeRequestAtMs = Long.MIN_VALUE
        keyframeAttempts = 0
    }

    fun observe(nowMs: Long, bytesReceived: Long?, framesDecoded: Long?, connected: Boolean): StreamLivenessAction {
        latestObservationProgressed = false
        if (!connected) {
            reset()
            return StreamLivenessAction.None
        }

        val progressed = if (framesDecoded != null) {
            lastFramesDecoded?.let { framesDecoded > it } ?: (framesDecoded > 0)
        } else {
            bytesReceived != null && (lastBytesReceived?.let { bytesReceived > it } ?: (bytesReceived > 0))
        }
        if (bytesReceived != null) lastBytesReceived = bytesReceived
        if (framesDecoded != null) lastFramesDecoded = framesDecoded
        if (progressed) {
            latestObservationProgressed = true
            lastProgressAtMs = nowMs
            lastKeyframeRequestAtMs = Long.MIN_VALUE
            keyframeAttempts = 0
            return StreamLivenessAction.None
        }

        val stalledMs = nowMs - (lastProgressAtMs ?: nowMs.also { lastProgressAtMs = it })
        if (stalledMs >= restartAfterMs) {
            reset()
            return StreamLivenessAction.RestartTransport(stalledMs)
        }
        val keyframeDue = lastKeyframeRequestAtMs == Long.MIN_VALUE ||
            nowMs - lastKeyframeRequestAtMs >= keyframeIntervalMs
        if (stalledMs >= keyframeAfterMs && keyframeDue) {
            lastKeyframeRequestAtMs = nowMs
            keyframeAttempts += 1
            return StreamLivenessAction.RequestKeyframe(stalledMs, keyframeAttempts)
        }
        return StreamLivenessAction.None
    }
}

internal data class StreamRecoveryTiming(
    val keyframeAfterMs: Long,
    val keyframeIntervalMs: Long,
    val restartAfterMs: Long,
)

internal fun streamRecoveryTiming(androidTvProfile: Boolean): StreamRecoveryTiming =
    if (androidTvProfile) {
        StreamRecoveryTiming(
            keyframeAfterMs = TV_MEDIA_STALL_KEYFRAME_AFTER_MS,
            keyframeIntervalMs = TV_MEDIA_STALL_KEYFRAME_INTERVAL_MS,
            restartAfterMs = TV_MEDIA_STALL_RESTART_AFTER_MS,
        )
    } else {
        StreamRecoveryTiming(
            keyframeAfterMs = MEDIA_STALL_KEYFRAME_AFTER_MS,
            keyframeIntervalMs = MEDIA_STALL_KEYFRAME_INTERVAL_MS,
            restartAfterMs = MEDIA_STALL_RESTART_AFTER_MS,
        )
    }

internal fun firstVideoFrameRecoveryTimeoutMs(androidTvProfile: Boolean): Long =
    streamRecoveryTiming(androidTvProfile).restartAfterMs

internal enum class FirstFrameRecoveryStep {
    RetryRequestedProfile,
    ApplySafeVideoFallback,
    ContinueBoundedTransportRecovery,
}

internal enum class CatastrophicResolutionRecoveryStep {
    None,
    RetryWithH265,
    RetryWithH264,
}

internal fun catastrophicFirstDecodedResolutionRecoveryStep(
    transportCodec: VideoCodec,
    expectedResolution: String?,
    decodedResolution: String?,
    completedCodecFallbacks: Int,
): CatastrophicResolutionRecoveryStep {
    val expected = parseResolutionPixelsOrNull(expectedResolution) ?: return CatastrophicResolutionRecoveryStep.None
    val decoded = parseResolutionPixelsOrNull(decodedResolution) ?: return CatastrophicResolutionRecoveryStep.None
    val expectedArea = expected.first.toLong() * expected.second.toLong()
    val decodedArea = decoded.first.toLong() * decoded.second.toLong()
    if (decoded == expected || decodedArea * CATASTROPHIC_DECODED_AREA_DIVISOR > expectedArea) {
        return CatastrophicResolutionRecoveryStep.None
    }
    return when {
        completedCodecFallbacks == 0 && transportCodec == VideoCodec.AV1 ->
            CatastrophicResolutionRecoveryStep.RetryWithH265
        completedCodecFallbacks == 1 && transportCodec == VideoCodec.H265 ->
            CatastrophicResolutionRecoveryStep.RetryWithH264
        else -> CatastrophicResolutionRecoveryStep.None
    }
}

internal fun StreamSettings.forCatastrophicResolutionRecovery(
    step: CatastrophicResolutionRecoveryStep,
): StreamSettings? = when (step) {
    CatastrophicResolutionRecoveryStep.RetryWithH265 ->
        copy(codec = VideoCodec.H265).withCodecColorCompatibility()
    CatastrophicResolutionRecoveryStep.RetryWithH264 -> androidSafeVideoFallback()
    CatastrophicResolutionRecoveryStep.None -> null
}

internal fun firstFrameRecoveryStep(
    transportHasStableMedia: Boolean,
    reconnectAttempts: Int,
    safeVideoFallbackApplied: Boolean,
): FirstFrameRecoveryStep = when {
    transportHasStableMedia -> FirstFrameRecoveryStep.ContinueBoundedTransportRecovery
    reconnectAttempts == 0 -> FirstFrameRecoveryStep.RetryRequestedProfile
    !safeVideoFallbackApplied -> FirstFrameRecoveryStep.ApplySafeVideoFallback
    else -> FirstFrameRecoveryStep.ContinueBoundedTransportRecovery
}

internal fun transportRestartShouldApplySafeVideoFallback(
    videoFailure: Boolean,
    reconnectAttempts: Int,
    transportHasStableMedia: Boolean,
): Boolean = videoFailure && reconnectAttempts >= 1 && !transportHasStableMedia

private fun newStreamLivenessWatchdog(androidTvProfile: Boolean): StreamLivenessWatchdog {
    val timing = streamRecoveryTiming(androidTvProfile)
    return StreamLivenessWatchdog(
        keyframeAfterMs = timing.keyframeAfterMs,
        keyframeIntervalMs = timing.keyframeIntervalMs,
        restartAfterMs = timing.restartAfterMs,
    )
}

internal class FirstVideoFrameWatchdog(
    private val timeoutMs: Long = FIRST_VIDEO_FRAME_TIMEOUT_MS,
) {
    private var bytesWithoutFrameSinceMs: Long? = null
    private var rendered = false

    @Synchronized
    fun reset() {
        bytesWithoutFrameSinceMs = null
        rendered = false
    }

    @Synchronized
    fun markRendered() {
        rendered = true
        bytesWithoutFrameSinceMs = null
    }

    @Synchronized
    fun shouldRecover(nowMs: Long, bytesReceived: Long?, connected: Boolean): Boolean {
        if (!connected || rendered || bytesReceived == null || bytesReceived <= 0L) {
            if (!connected) bytesWithoutFrameSinceMs = null
            return false
        }
        val startedAt = bytesWithoutFrameSinceMs ?: nowMs.also { bytesWithoutFrameSinceMs = it }
        return nowMs - startedAt >= timeoutMs
    }
}

internal data class TouchMouseDelta(
    val dx: Int,
    val dy: Int,
)

internal class TouchMouseMotionAccumulator(
    private val minimumSendIntervalMs: Long = 8L,
) {
    private var pendingDx = 0f
    private var pendingDy = 0f
    private var lastSendTimeMs = Long.MIN_VALUE

    fun reset() {
        pendingDx = 0f
        pendingDy = 0f
        lastSendTimeMs = Long.MIN_VALUE
    }

    fun add(
        dx: Float,
        dy: Float,
        eventTimeMs: Long,
        sensitivity: Float,
        acceleration: Int,
        force: Boolean = false,
    ): TouchMouseDelta? {
        if (!dx.isFinite() || !dy.isFinite() || !sensitivity.isFinite()) {
            reset()
            return null
        }
        var adjustedDx = dx * sensitivity
        var adjustedDy = dy * sensitivity
        if (acceleration > 1) {
            val speed = sqrt(adjustedDx * adjustedDx + adjustedDy * adjustedDy)
            val strength = (acceleration - 1f) / 149f
            val accelerationFactor = 1f + min(0.6f * strength, (speed / 50f) * strength)
            adjustedDx *= accelerationFactor
            adjustedDy *= accelerationFactor
        }
        if (!adjustedDx.isFinite() || !adjustedDy.isFinite()) {
            reset()
            return null
        }
        pendingDx += adjustedDx
        pendingDy += adjustedDy
        if (!pendingDx.isFinite() || !pendingDy.isFinite()) {
            reset()
            return null
        }

        val elapsedSinceSend = eventTimeMs - lastSendTimeMs
        if (
            !force &&
            lastSendTimeMs != Long.MIN_VALUE &&
            elapsedSinceSend in 0 until minimumSendIntervalMs
        ) {
            return null
        }

        val sendDx = pendingDx.roundToInt()
        val sendDy = pendingDy.roundToInt()
        if (sendDx == 0 && sendDy == 0) return null

        pendingDx -= sendDx
        pendingDy -= sendDy
        lastSendTimeMs = eventTimeMs
        return TouchMouseDelta(sendDx, sendDy)
    }
}

/** A point in the stream's own pixel space, as produced by [streamPointForTouch]. */
internal data class StreamPoint(val x: Float, val y: Float)

/** Phase of a single finger, as the host expects it on the wire. */
internal object TouchPhase {
    const val DOWN = 1
    const val UP = 2
    const val MOVE = 4
    const val CANCEL = 8
}

/** Touch coordinates travel as an unsigned 16-bit fraction of the video area. */
internal const val TOUCH_COORDINATE_MAX = 65535

/** The host tracks at most this many fingers at once. */
internal const val MAX_CONCURRENT_TOUCHES = 8

/** One packet carries at most this many records. */
internal const val MAX_TOUCH_RECORDS_PER_BATCH = 40

/**
 * One finger in one packet. [slot] is the host's finger index — deliberately not the platform's
 * pointer id, see [TouchSlotAllocator].
 */
internal data class TouchRecord(
    val slot: Int,
    val phase: Int,
    val x: Int,
    val y: Int,
    val radiusX: Int = 0,
    val radiusY: Int = 0,
    val timestampUs: Long = 0L,
)

/**
 * Maps platform pointer ids onto the small, dense finger indices the host expects.
 *
 * Android pointer ids are arbitrary and can climb without bound across a session; the host wants
 * the lowest free index, reused as soon as a finger lifts. Forwarding pointer ids directly would
 * make the host see fingers appear at ever-higher indices and eventually run past its own limit.
 *
 * Kept free of `MotionEvent` so it can be tested on the JVM.
 */
internal class TouchSlotAllocator {
    private val slotByPointerId = mutableMapOf<Int, Int>()
    private val usedSlots = mutableSetOf<Int>()

    val activeCount: Int get() = slotByPointerId.size

    /** Slot for [pointerId], allocating the lowest free one. Null when all slots are in use. */
    fun acquire(pointerId: Int): Int? {
        slotByPointerId[pointerId]?.let { return it }
        var slot = 0
        while (slot in usedSlots) slot++
        if (slot >= MAX_CONCURRENT_TOUCHES) return null
        slotByPointerId[pointerId] = slot
        usedSlots.add(slot)
        return slot
    }

    /** Slot for [pointerId] without allocating one. */
    fun peek(pointerId: Int): Int? = slotByPointerId[pointerId]

    /** Frees the slot held by [pointerId], returning it so the caller can still report the lift. */
    fun release(pointerId: Int): Int? {
        val slot = slotByPointerId.remove(pointerId) ?: return null
        usedSlots.remove(slot)
        return slot
    }

    fun activePointerIds(): List<Int> = slotByPointerId.keys.toList()

    fun clear() {
        slotByPointerId.clear()
        usedSlots.clear()
    }
}

/** One finger as the platform reported it, before any mapping. */
internal data class TouchPointerSample(
    val pointerId: Int,
    val x: Float,
    val y: Float,
    val radiusX: Float = 0f,
    val radiusY: Float = 0f,
)

/**
 * Turns one input event's fingers into the records for a single packet.
 *
 * Holds every rule that is easy to get subtly wrong — slot allocation, normalising to the video
 * area, and what to do with a finger that is outside it — and takes no `MotionEvent`, so all of it
 * is testable on the JVM.
 */
internal fun buildTouchBatch(
    allocator: TouchSlotAllocator,
    phase: Int,
    pointers: List<TouchPointerSample>,
    viewWidth: Int,
    viewHeight: Int,
    streamWidth: Int,
    streamHeight: Int,
    stretchToFit: Boolean,
    renderingAspectRatio: Float,
    timestampUs: Long = 0L,
): List<TouchRecord> {
    if (viewWidth <= 0 || viewHeight <= 0 || streamWidth <= 0 || streamHeight <= 0) return emptyList()

    // A lift must always be reported, wherever the finger ended up. Swallowing one leaves the host
    // holding that finger down for the rest of the session.
    val lifting = phase == TouchPhase.UP || phase == TouchPhase.CANCEL
    val records = ArrayList<TouchRecord>(pointers.size)

    for (pointer in pointers) {
        if (records.size >= MAX_TOUCH_RECORDS_PER_BATCH) break

        val point = streamPointForTouch(
            touchX = pointer.x,
            touchY = pointer.y,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            streamWidth = streamWidth,
            streamHeight = streamHeight,
            stretchToFit = stretchToFit,
            renderingAspectRatio = renderingAspectRatio,
            clamp = false,
        )
        val x = point.x / streamWidth * TOUCH_COORDINATE_MAX
        val y = point.y / streamHeight * TOUCH_COORDINATE_MAX
        val radiusX = pointer.radiusX / streamWidth * TOUCH_COORDINATE_MAX
        val radiusY = pointer.radiusY / streamHeight * TOUCH_COORDINATE_MAX

        val finiteSample = x.isFinite() && y.isFinite() && radiusX.isFinite() && radiusY.isFinite()
        if (!finiteSample && !lifting) continue

        val safeX = if (x.isFinite()) x else 0f
        val safeY = if (y.isFinite()) y else 0f
        val safeRadiusX = if (radiusX.isFinite()) radiusX.coerceAtLeast(0f) else 0f
        val safeRadiusY = if (radiusY.isFinite()) radiusY.coerceAtLeast(0f) else 0f
        val safePointerRadiusX = if (pointer.radiusX.isFinite()) pointer.radiusX.coerceAtLeast(0f) else 0f
        val safePointerRadiusY = if (pointer.radiusY.isFinite()) pointer.radiusY.coerceAtLeast(0f) else 0f
        val outside = safeX < -safeRadiusX || safeX > TOUCH_COORDINATE_MAX + safeRadiusX ||
            safeY < -safeRadiusY || safeY > TOUCH_COORDINATE_MAX + safeRadiusY
        if (outside && !lifting) continue

        val slot = (
            if (lifting) allocator.release(pointer.pointerId) else allocator.acquire(pointer.pointerId)
            ) ?: continue

        records += TouchRecord(
            slot = slot,
            phase = phase,
            x = safeX.roundToInt().coerceIn(0, TOUCH_COORDINATE_MAX),
            y = safeY.roundToInt().coerceIn(0, TOUCH_COORDINATE_MAX),
            radiusX = safePointerRadiusX.roundToInt().coerceAtLeast(0),
            radiusY = safePointerRadiusY.roundToInt().coerceAtLeast(0),
            timestampUs = timestampUs,
        )
    }
    return records
}

/**
 * Maps a touch inside a view of [viewWidth] x [viewHeight] onto the stream's pixel space, undoing
 * the letterbox/pillarbox bars the renderer adds whenever the view and the stream disagree about
 * aspect ratio.
 *
 * Everything it needs arrives as an argument, and the result is expressed as a fraction of the
 * view — which is why a window resize (PiP, rotation, minimise) needs no cursor bookkeeping at all.
 * The event carries the view size it was measured against, so even a size captured mid-resize maps
 * that event correctly.
 */
internal fun streamPointForTouch(
    touchX: Float,
    touchY: Float,
    viewWidth: Int,
    viewHeight: Int,
    streamWidth: Int,
    streamHeight: Int,
    stretchToFit: Boolean,
    renderingAspectRatio: Float,
    /**
     * Clamping is right for a cursor, which must land somewhere. Native touch passes false so it
     * can tell a finger on the letterbox bar from one at the edge of the picture, and drop it.
     */
    clamp: Boolean = true,
): StreamPoint {
    if (viewWidth <= 0 || viewHeight <= 0 || streamWidth <= 0 || streamHeight <= 0) {
        return StreamPoint(0f, 0f)
    }
    if (!touchX.isFinite() || !touchY.isFinite()) {
        return StreamPoint(Float.NaN, Float.NaN)
    }

    var videoWidth = viewWidth.toFloat()
    var videoHeight = viewHeight.toFloat()
    var offsetX = 0f
    var offsetY = 0f

    // The renderer always applies aspect-ratio constraints (fillMaxHeight/Width + aspectRatio),
    // so letterbox/pillarbox offsets exist regardless of stretchToFit.
    val streamAspectRatio =
        if (renderingAspectRatio.isFinite() && renderingAspectRatio > 0f) {
            renderingAspectRatio
        } else {
            viewAspectOf(streamWidth, streamHeight)
        }
    val viewAspectRatio = viewAspectOf(viewWidth, viewHeight)
    if (viewAspectRatio > streamAspectRatio) {
        // Pillarboxed — bars left and right.
        videoWidth = viewHeight * streamAspectRatio
        offsetX = (viewWidth - videoWidth) / 2f
    } else if (viewAspectRatio < streamAspectRatio) {
        // Letterboxed — bars top and bottom.
        videoHeight = viewWidth / streamAspectRatio
        offsetY = (viewHeight - videoHeight) / 2f
    }
    }

    if (!videoWidth.isFinite() || !videoHeight.isFinite() || videoWidth <= 0f || videoHeight <= 0f) {
        return StreamPoint(Float.NaN, Float.NaN)
    }

    val x = (touchX - offsetX) / videoWidth * streamWidth
    val y = (touchY - offsetY) / videoHeight * streamHeight
    if (!x.isFinite() || !y.isFinite()) {
        return StreamPoint(Float.NaN, Float.NaN)
    }
    return if (clamp) {
        StreamPoint(x.coerceIn(0f, streamWidth.toFloat()), y.coerceIn(0f, streamHeight.toFloat()))
    } else {
        StreamPoint(x, y)
    }
}

private fun viewAspectOf(width: Int, height: Int): Float = width.toFloat() / height.toFloat()

/** A whole-pixel relative move, the only kind the wire format carries. */
internal data class CursorDelta(val dx: Int, val dy: Int)

/**
 * Our model of where the host's cursor sits while a direct-click drag is active.
 *
 * The protocol has no absolute-positioning packet — [InputEncoder.INPUT_MOUSE_REL] is all there is.
 * At the start of every tap, [reanchorDeltasTo] first sends the largest supported negative movement
 * so the desktop clamps the cursor to its top-left boundary, then sends the target coordinates from
 * that known origin. That removes the old assumption that the host cursor started in the centre,
 * which left every direct click offset whenever a game had moved it elsewhere.
 */
internal class VirtualCursor {
    private var x = 0f
    private var y = 0f
    private var initialized = false
    private var streamWidth = 0
    private var streamHeight = 0

    /** Exposed for tests; production code only ever needs [consumeDeltaTo]. */
    val position: StreamPoint get() = StreamPoint(x, y)

    fun onStreamSize(width: Int, height: Int) {
        // A transient 0x0 size during a resolution change or PiP transition is not a new
        // coordinate space. Ignoring it also keeps a not-yet-initialized cursor uninitialized.
        if (width <= 0 || height <= 0) return
        if (!initialized) {
            // Direct click reanchors before pressing; this temporary value only keeps the generic
            // relative cursor model well-defined until that first DOWN arrives.
            x = width / 2f
            y = height / 2f
            initialized = true
        } else if (streamWidth != width || streamHeight != height) {
            if (streamWidth > 0 && streamHeight > 0) {
                x = x / streamWidth * width
                y = y / streamHeight * height
            }
        }
        streamWidth = width
        streamHeight = height
    }

    /**
     * The move to send in order to land on [target], advancing the model by exactly that much —
     * not by [target]. The two differ by the rounding residue, and assigning [target] would swallow
     * that residue every event, letting the model random-walk away from the real cursor over a long
     * drag. Null when the rounded move is zero and there is nothing worth sending.
     */
    fun consumeDeltaTo(target: StreamPoint): CursorDelta? {
        if (!target.x.isFinite() || !target.y.isFinite() || !x.isFinite() || !y.isFinite()) return null
        val dx = (target.x - x).roundToInt()
        val dy = (target.y - y).roundToInt()
        if (dx == 0 && dy == 0) return null
        // The protocol transmits whole-pixel relative motion, so advance the shadow by exactly
        // what the host receives. Assigning the fractional target accumulates cursor drift.
        x += dx
        y += dy
        return CursorDelta(dx, dy)
    }

    /**
     * Reliable relative moves that place the host cursor at [target] without knowing its current
     * position. The first move is already the protocol's signed-16-bit minimum, so the encoder will
     * transmit it unchanged and any supported single-display stream is guaranteed to hit (0, 0).
     */
    fun reanchorDeltasTo(target: StreamPoint): List<CursorDelta> {
        if (
            !initialized ||
            streamWidth <= 0 ||
            streamHeight <= 0 ||
            !target.x.isFinite() ||
            !target.y.isFinite()
        ) return emptyList()
        val targetX = target.x.roundToInt().coerceIn(0, streamWidth - 1)
        val targetY = target.y.roundToInt().coerceIn(0, streamHeight - 1)
        x = targetX.toFloat()
        y = targetY.toFloat()
        return buildList {
            repeat(2) {
                add(CursorDelta(Short.MIN_VALUE.toInt(), Short.MIN_VALUE.toInt()))
            }
            add(CursorDelta(targetX, targetY))
        }
    }

    fun forget() {
        initialized = false
        streamWidth = 0
        streamHeight = 0
    }
}

private class TouchMouseState {
    private var activePointerId = -1
    private var downX = 0f
    private var downY = 0f
    private var downTimeMs = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var selecting = false
    private var doubleTapDragCandidate = false
    private var lastTapTimeMs = Long.MIN_VALUE
    private var lastTapX = Float.NaN
    private var lastTapY = Float.NaN
    private val virtualCursor = VirtualCursor()
    private var twoFingerTapCandidate = false
    private val motionAccumulator = TouchMouseMotionAccumulator()
    // 2-finger scroll state
    private var secondPointerId = -1
    private var secondPointerDownY = 0f
    private var secondPointerLastY = 0f
    private var isScrollGesture = false
    private var scrollAccumulator = 0f
    private var scrollGestureOccurred = false

    /**
     * Tears down the in-flight gesture. The cursor model is retained only for an active drag; every
     * new direct-click DOWN independently reanchors at the host boundary before moving to its target.
     */
    fun reset(client: NativeStreamClient?) {
        // Correct for both modes now that direct click also maintains `selecting`. Widening this
        // to `activePointerId >= 0` instead would fire in touchpad mode during an ordinary drag,
        // where a pointer is tracked but no button is held, sending a spurious release.
        if (selecting) client?.setTouchMouseButton(false)
        activePointerId = -1
        selecting = false
        doubleTapDragCandidate = false
        twoFingerTapCandidate = false
        motionAccumulator.reset()
        secondPointerId = -1
        isScrollGesture = false
        scrollAccumulator = 0f
        scrollGestureOccurred = false
    }

    /** Clears any position retained from the previous stream client. */
    fun forgetCursorPosition() {
        virtualCursor.forget()
    }

    private fun moveVirtualCursorTo(target: StreamPoint, client: NativeStreamClient) {
        val delta = virtualCursor.consumeDeltaTo(target) ?: return
        client.sendRawMouseMove(delta.dx, delta.dy)
    }

    private fun reanchorVirtualCursorTo(target: StreamPoint, client: NativeStreamClient): Boolean {
        val deltas = virtualCursor.reanchorDeltasTo(target)
        if (deltas.isEmpty()) return false
        for (delta in deltas) {
            if (!client.sendRawMouseMove(delta.dx, delta.dy)) {
                virtualCursor.forget()
                return false
            }
        }
        return true
    }

    fun handle(
        event: MotionEvent,
        enabled: Boolean,
        client: NativeStreamClient,
        ignoredPointerIds: Set<Int>,
        directClick: Boolean = false,
        width: Int = 0,
        height: Int = 0,
        stretchToFit: Boolean = false,
        renderingAspectRatio: Float = 0f,
        decodedStreamWidth: Int = 0,
        decodedStreamHeight: Int = 0,
    ): Boolean {
        if (!enabled) {
            reset(client)
            return false
        }

        if (directClick) {
            val settingsRes = streamResolutionPixels(client.settings)
            val streamWidth = if (decodedStreamWidth > 0) decodedStreamWidth else settingsRes.first
            val streamHeight = if (decodedStreamHeight > 0) decodedStreamHeight else settingsRes.second
            virtualCursor.onStreamSize(streamWidth, streamHeight)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val index = if (event.actionMasked == MotionEvent.ACTION_DOWN) 0 else event.actionIndex
                    if (index in 0 until event.pointerCount && event.getPointerId(index) !in ignoredPointerIds) {
                        val pointerId = event.getPointerId(index)
                        // Guard: only block if the *same* pointer is already being tracked (true dup).
                        // Allow a new pointer if the previous activePointerId is no longer present in the event.
                        if (activePointerId >= 0 && event.findPointerIndex(activePointerId) >= 0) {
                            // Active pointer still in contact — absorb this extra DOWN.
                            return true
                        }
                        // If we get here the old pointer was lifted without a UP event — reset first.
                        if (activePointerId >= 0) {
                            client.setTouchMouseButton(false)
                            selecting = false
                        }

                        activePointerId = pointerId
                        val target = streamPointForTouch(
                            touchX = event.getX(index),
                            touchY = event.getY(index),
                            viewWidth = width,
                            viewHeight = height,
                            streamWidth = streamWidth,
                            streamHeight = streamHeight,
                            stretchToFit = stretchToFit,
                            renderingAspectRatio = renderingAspectRatio,
                        )
                        // Move cursor smoothly to target without forced reanchoring.
                        moveVirtualCursorTo(target, client)

                        selecting = client.setTouchMouseButton(true)
                        if (!selecting) {
                            activePointerId = -1
                            return true
                        }
                        // `selecting` is this class's single record of "we are holding the button
                        // down on the host". Direct click used to leave it false and track the
                        // press only through activePointerId, so reset() — which releases on
                        // `selecting` — could not release it, and backgrounding mid-tap left the
                        // button stuck down with no event left to clear it.
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (activePointerId >= 0) {
                        val index = event.findPointerIndex(activePointerId)
                        if (index >= 0) {
                            moveVirtualCursorTo(
                                streamPointForTouch(
                                    touchX = event.getX(index),
                                    touchY = event.getY(index),
                                    viewWidth = width,
                                    viewHeight = height,
                                    streamWidth = streamWidth,
                                    streamHeight = streamHeight,
                                    stretchToFit = stretchToFit,
                                    renderingAspectRatio = renderingAspectRatio,
                                ),
                                client,
                            )
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    // Final pointer lifted — always release the button.
                    client.setTouchMouseButton(false)
                    selecting = false
                    activePointerId = -1
                    return true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    val releasedId = event.getPointerId(event.actionIndex)
                    if (releasedId == activePointerId) {
                        client.setTouchMouseButton(false)
                        selecting = false
                        activePointerId = -1
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    client.setTouchMouseButton(false)
                    selecting = false
                    activePointerId = -1
                    return true
                }
            }
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.getPointerId(0) in ignoredPointerIds) {
                    reset(client)
                    return false
                }
                beginPointer(event, 0)
                twoFingerTapCandidate = false
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (activePointerId < 0) {
                    val index = event.actionIndex
                    if (index in 0 until event.pointerCount && event.getPointerId(index) !in ignoredPointerIds) {
                        beginPointer(event, index)
                    }
                } else {
                    val newIndex = event.actionIndex
                    val newPointerId = if (newIndex in 0 until event.pointerCount) event.getPointerId(newIndex) else -1
                    if (newPointerId >= 0 && newPointerId !in ignoredPointerIds) {
                        var nonIgnoredCount = 0
                        for (i in 0 until event.pointerCount) {
                            if (event.getPointerId(i) !in ignoredPointerIds) {
                                nonIgnoredCount++
                            }
                        }
                        if (nonIgnoredCount == 2) {
                            twoFingerTapCandidate = true
                            secondPointerId = newPointerId
                            val secIdx = event.findPointerIndex(newPointerId)
                            if (secIdx >= 0) {
                                secondPointerLastY = event.getY(secIdx)
                                secondPointerDownY = secondPointerLastY
                            }
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (activePointerId < 0) {
                    val index = event.firstPointerIndexNotIn(ignoredPointerIds)
                    if (index >= 0) beginPointer(event, index)
                    return index >= 0
                }
                // Handle 2-finger scroll
                if (secondPointerId >= 0) {
                    val secIdx = event.findPointerIndex(secondPointerId)
                    if (secIdx >= 0) {
                        val secY = event.getY(secIdx)
                        val secDy = secY - secondPointerLastY
                        secondPointerLastY = secY
                        if (!isScrollGesture && abs(secY - secondPointerDownY) > SCROLL_START_SLOP_PX) {
                            isScrollGesture = true
                            scrollGestureOccurred = true
                            twoFingerTapCandidate = false
                            scrollAccumulator = 0f
                            NativeInputDiagnostics.add("touch scroll start")
                        }
                        if (isScrollGesture) {
                            val scrollPxPerNotch = client.settings.mouseScrollSensitivity.toFloat().coerceIn(10f, 100f)
                            scrollAccumulator -= secDy
                            val notches = (scrollAccumulator / scrollPxPerNotch).toInt()
                            if (notches != 0) {
                                client.sendTouchMouseWheel(notches * 120)
                                scrollAccumulator -= notches * scrollPxPerNotch
                            }
                            return true
                        }
                    }
                }
                val index = event.findPointerIndex(activePointerId)
                if (index < 0) return true
                val x = event.getX(index)
                val y = event.getY(index)
                val dx = x - lastX
                val dy = y - lastY
                if (
                    doubleTapDragCandidate &&
                    !selecting &&
                    (abs(x - downX) > TOUCH_MOUSE_DRAG_START_SLOP_PX || abs(y - downY) > TOUCH_MOUSE_DRAG_START_SLOP_PX)
                ) {
                    selecting = client.setTouchMouseButton(true)
                    doubleTapDragCandidate = false
                    if (selecting) {
                        NativeInputDiagnostics.add("touch double tap drag start")
                    }
                }
                sendMouseDelta(dx, dy, event.eventTime, client)
                lastX = x
                lastY = y
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                if (index in 0 until event.pointerCount) {
                    val upId = event.getPointerId(index)
                    if (upId == secondPointerId) {
                        secondPointerId = -1
                        isScrollGesture = false
                        scrollAccumulator = 0f
                    }
                    if (upId == activePointerId) {
                        finishPointer(event, index, client)
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val index = event.findPointerIndex(activePointerId).takeIf { it >= 0 } ?: event.firstPointerIndexNotIn(ignoredPointerIds)
                if (index < 0) return false
                finishPointer(event, index, client)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                reset(client)
                return true
            }
        }
        return true
    }

    private fun beginPointer(event: MotionEvent, index: Int) {
        activePointerId = event.getPointerId(index)
        downX = event.getX(index)
        downY = event.getY(index)
        downTimeMs = event.eventTime
        lastX = downX
        lastY = downY
        motionAccumulator.reset()
        selecting = false
        doubleTapDragCandidate = isDoubleTap(event, index)
        if (doubleTapDragCandidate) {
            lastTapTimeMs = Long.MIN_VALUE
        }
    }

    private fun finishPointer(event: MotionEvent, index: Int, client: NativeStreamClient) {
        val x = event.getX(index)
        val y = event.getY(index)
        sendMouseDelta(
            dx = x - lastX,
            dy = y - lastY,
            eventTimeMs = event.eventTime,
            client = client,
            force = true,
        )
        lastX = x
        lastY = y
        val tapDistanceX = abs(x - downX)
        val tapDistanceY = abs(y - downY)
        val wasTap = activePointerId >= 0 &&
            !scrollGestureOccurred &&
            event.eventTime - downTimeMs <= TOUCH_MOUSE_TAP_TIMEOUT_MS &&
            tapDistanceX <= TOUCH_MOUSE_TAP_SLOP_PX &&
            tapDistanceY <= TOUCH_MOUSE_TAP_SLOP_PX
        activePointerId = -1
        doubleTapDragCandidate = false
        scrollGestureOccurred = false
        if (selecting) {
            client.setTouchMouseButton(false)
            selecting = false
            return
        }
        if (wasTap) {
            if (twoFingerTapCandidate) {
                NativeInputDiagnostics.add("touch 2-finger tap right click dx=${tapDistanceX.roundToInt()} dy=${tapDistanceY.roundToInt()}")
                client.sendTouchMouseRightClick()
            } else {
                NativeInputDiagnostics.add("touch tap click dx=${tapDistanceX.roundToInt()} dy=${tapDistanceY.roundToInt()}")
                client.sendTouchMouseClick()
            }
            lastTapTimeMs = event.eventTime
            lastTapX = x
            lastTapY = y
        }
        twoFingerTapCandidate = false
    }

    private fun MotionEvent.firstPointerIndexNotIn(ignoredPointerIds: Set<Int>): Int {
        for (index in 0 until pointerCount) {
            if (getPointerId(index) !in ignoredPointerIds) return index
        }
        return -1
    }

    private fun isDoubleTap(event: MotionEvent, index: Int): Boolean {
        if (lastTapTimeMs == Long.MIN_VALUE) return false
        if (event.eventTime - lastTapTimeMs > TOUCH_MOUSE_DOUBLE_TAP_TIMEOUT_MS) return false
        if (!lastTapX.isFinite() || !lastTapY.isFinite()) return false
        return abs(event.getX(index) - lastTapX) <= TOUCH_MOUSE_DOUBLE_TAP_SLOP_PX &&
            abs(event.getY(index) - lastTapY) <= TOUCH_MOUSE_DOUBLE_TAP_SLOP_PX
    }

    private fun sendMouseDelta(
        dx: Float,
        dy: Float,
        eventTimeMs: Long,
        client: NativeStreamClient,
        partiallyReliable: Boolean = true,
        force: Boolean = false,
    ) {
        val delta = motionAccumulator.add(
            dx = dx,
            dy = dy,
            eventTimeMs = eventTimeMs,
            sensitivity = client.settings.mouseSensitivity,
            acceleration = client.settings.mouseAcceleration,
            force = force,
        ) ?: return
        client.sendRawMouseMove(delta.dx, delta.dy, partiallyReliable)
    }

    companion object {
        private const val TOUCH_MOUSE_DRAG_START_SLOP_PX = 10f
        private const val TOUCH_MOUSE_TAP_SLOP_PX = 42f
        private const val TOUCH_MOUSE_TAP_TIMEOUT_MS = 450L
        private const val TOUCH_MOUSE_DOUBLE_TAP_TIMEOUT_MS = 320L
        private const val TOUCH_MOUSE_DOUBLE_TAP_SLOP_PX = 36f
        private const val SCROLL_START_SLOP_PX = 12f
    }
}

class NativeStreamClient(
    context: Context,
    private val onState: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onVideoTransportFallbackApplied: (String, StreamSettings) -> Unit = { _, _ -> },
    private val onSessionRecoveryRequired: (String) -> Unit = {},
    private val onFirstVideoFrameRendered: () -> Unit = {},
    private val onStats: (StreamRuntimeStats) -> Unit = {},
    private val onControllerMouseAssistChanged: (Boolean) -> Unit = {},
    private val onStreamStopped: () -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val initialAndroidTvProfile = isAndroidTvProfile(appContext)
    private val eglBase: EglBase = EglBase.create()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val inputExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "opennow-input-sender").apply {
            priority = Thread.MAX_PRIORITY
        }
    }
    private val teardownExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "opennow-native-teardown").apply {
            priority = Thread.NORM_PRIORITY
        }
    }
    private val inputScope = CoroutineScope(SupervisorJob() + inputExecutor.asCoroutineDispatcher())
    private val inputEncoder = InputEncoder()
    private val audioDeviceModule: AudioDeviceModule =
        JavaAudioDeviceModule.builder(appContext)
            .setUseLowLatency(shouldUseLowLatencyStreamAudio(initialAndroidTvProfile))
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setUseStereoInput(false)
            .setUseStereoOutput(true)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .createAudioDeviceModule()
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var signaling: GfnSignalingClient? = null
    private var reliableInput: DataChannel? = null
    private var partiallyReliableInput: DataChannel? = null
    private var statsChannel: DataChannel? = null
    private var lastParsedGameFps: Int? = null
    private var partiallyReliableGamepadMask = 0
    private var hapticsAdvertised: Boolean? = null
    private var videoTrack: VideoTrack? = null
    private var audioTrack: AudioTrack? = null
    private var microphoneSource: AudioSource? = null
    private var microphoneTrack: AudioTrack? = null
    private var microphoneSender: RtpSender? = null
    private var renderer: SurfaceViewRenderer? = null
    private var rendererSharpnessDrawer: StreamSharpnessGlDrawer? = null
    private var rendererSurfaceCallback: SurfaceHolder.Callback? = null
    private var rendererSinkAttached = false
    private var heartbeatJob: Job? = null
    private var gamepadKeepaliveJob: Job? = null
    private var statsJob: Job? = null
    private var iceRecoveryJob: Job? = null
    private var offerTimeoutJob: Job? = null
    internal var settings: StreamSettings = StreamSettings()
    private var session: SessionInfo? = null
    private var transportGeneration = 0
    private var reconnectAttempts = 0
    private var videoSafeFallbackApplied = false
    private var catastrophicResolutionCodecFallbacks = 0
    private var firstDecodedResolutionEvaluated = false
    private var transportHasStableMedia = false
    private var consecutiveTransportProgressSamples = 0
    private var sessionRecoveryRequested = false
    private var lastIceState: PeerConnection.IceConnectionState? = null
    private var audioMuted = false
    private var microphoneMuted = false
    private var virtualButtons = 0
    private var virtualLeftTrigger = 0
    private var virtualRightTrigger = 0
    private var virtualLeftStickActive = false
    private var virtualLeftStickX = 0
    private var virtualLeftStickY = 0
    private var virtualRightStickActive = false
    private var virtualRightStickX = 0
    private var virtualRightStickY = 0
    private var virtualControllerVisible = false
    private var physicalControllerConnected = false
    private var physicalControllerActive = false
    private var activeControllerId = 0
    private val controllerSlots = linkedMapOf<Int, Int>()
    private val controllerAxisAvailability = mutableMapOf<Int, AndroidGamepadAxisAvailability>()
    private var physicalButtons = 0
    private var physicalHatButtons = 0
    private var steamMenuChordButtons = 0
    private val physicalSteamOverlayChord = SteamOverlayChordState()
    private val virtualSteamOverlayChord = SteamOverlayChordState()
    private var physicalLeftTriggerButtonPressed = false
    private var physicalRightTriggerButtonPressed = false
    private var lastLeftTrigger = 0
    private var lastRightTrigger = 0
    private var lastLeftStickX = 0
    private var lastLeftStickY = 0
    private var lastRightStickX = 0
    private var lastRightStickY = 0
    private var controllerMouseAutoArmOnStart = false
    private var controllerMouseAssistActive = false
    private var controllerMouseAssistAutoArmed = false
    private var controllerMouseEmulationActive = false
    private var controllerMouseMoveLogged = false
    private var controllerMouseLeftButtonDown = false
    private var controllerMouseRightButtonDown = false
    private var mouseLastDeviceId = Int.MIN_VALUE
    private var mouseLastSource = 0
    private var mouseLastX = 0f
    private var mouseLastY = 0f
    private var mousePositionValid = false
    private var mouseSuppressNextAbsoluteDelta = false
    private var inputDropLogged = false
    private var externalMouseEventLogged = false
    private var externalMouseMoveSentLogged = false
    private var externalMouseAbsoluteJumpLogged = false
    private var hardwareKeyboardEventLogged = false
    private var physicalGamepadAxisLogged = false
    private var lastStatsSample: StreamStatsSample? = null
    private var androidTvProfile = initialAndroidTvProfile
    private var livenessWatchdog = newStreamLivenessWatchdog(androidTvProfile)
    private var firstVideoFrameWatchdog = FirstVideoFrameWatchdog(
        timeoutMs = firstVideoFrameRecoveryTimeoutMs(androidTvProfile),
    )
    private val textSendMutex = Mutex()
    private var guideAutoReleaseJob: Job? = null
    private var steamMenuChordJob: Job? = null
    private var physicalSteamOverlayChordReleaseJob: Job? = null
    private var virtualSteamOverlayChordReleaseJob: Job? = null
    private val lastRumbleEffectAtMs = LongArray(GAMEPAD_MAX_CONTROLLERS)
    private val hapticsSupportLogged = BooleanArray(GAMEPAD_MAX_CONTROLLERS)
    private var lastHapticsWarningAtMs = 0L
    private var phoneRumbleFallbackEnabled = true
    private var phoneRumbleSupportLogged = false
    private var released = false
    private var controllerMouseLoopJob: Job? = null
    private var physicalLeftStickX = 0f
    private var physicalLeftStickY = 0f
    private var physicalRightStickX = 0f
    private var physicalRightStickY = 0f
    private var controllerScrollAccumulator = 0f

    private data class RumbleEffectProfile(
        val weakAmplitude: Int,
        val strongAmplitude: Int,
        val combinedAmplitude: Int,
    ) {
        val isStop: Boolean
            get() = weakAmplitude <= 0 && strongAmplitude <= 0 && combinedAmplitude <= 0
    }

    private data class StreamStatsSample(
        val atMs: Double,
        val bytesReceived: Long,
        val framesReceived: Long,
        val framesDecoded: Long,
        val totalDecodeTime: Double,
        val packetsLost: Long,
        val packetsReceived: Long,
    )

    private data class RuntimeStatsSnapshot(
        val stats: StreamRuntimeStats,
        val bytesReceived: Long?,
        val framesDecoded: Long?,
    )

    private data class MicrophoneResources(
        val sender: RtpSender?,
        val track: AudioTrack?,
        val source: AudioSource?,
    )

    private fun recordStreamDiagnostic(message: String) {
        NativeInputDiagnostics.add("stream $message")
    }

    private fun enqueueNativeTeardown(label: String, command: () -> Unit) {
        runCatching {
            teardownExecutor.execute {
                runCatching(command).onFailure { error ->
                    recordStreamDiagnostic("native teardown failed step=$label error=${error.message.orEmpty()}")
                }
            }
        }.onFailure { error ->
            recordStreamDiagnostic("native teardown rejected step=$label error=${error.message.orEmpty()}")
        }
    }

    init {
        WebRtcRuntime.ensureInitialized(appContext)
        val lowLatencyEnabled = SettingsStore(appContext).settings.value.nativeLowLatencyDecoder
        factory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoDecoderFactory(
                OpenNowVideoDecoderFactory(
                    sharedContext = eglBase.eglBaseContext,
                    nativeLowLatencyDecoderEnabled = lowLatencyEnabled,
                    requestedFps = { settings.fps },
                ),
            )
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()
    }

    fun createRenderer(context: Context, settings: StreamSettings): SurfaceViewRenderer =
        SurfaceViewRenderer(context).also {
            renderer?.let { oldRenderer ->
                releaseRendererInternal(oldRenderer)
            }
            firstVideoFrameWatchdog.reset()
            val rendererEvents = object : RendererCommon.RendererEvents {
                override fun onFirstFrameRendered() {
                    firstVideoFrameWatchdog.markRendered()
                    NativeInputDiagnostics.add("video renderer first frame codec=${this@NativeStreamClient.settings.codec}")
                    scope.launch { onFirstVideoFrameRendered() }
                }

                override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
                    NativeInputDiagnostics.add("video renderer resolution=${videoWidth}x$videoHeight rotation=$rotation")
                    if (videoWidth > 0 && videoHeight > 0) {
                        NativeStreamInputRouter.setDecodedStreamResolution(videoWidth, videoHeight)
                    }
                }
            }
            val sharpnessDrawer = if (settings.streamSharpeningEnabled) {
                StreamSharpnessGlDrawer().also { drawer ->
                    drawer.amount = streamSharpnessShaderStrength(true, settings.streamSharpeningAmount)
                }
            } else {
                null
            }
            rendererSharpnessDrawer = sharpnessDrawer
            if (sharpnessDrawer != null) {
                it.init(eglBase.eglBaseContext, rendererEvents, EglBase.CONFIG_PLAIN, sharpnessDrawer)
            } else {
                it.init(eglBase.eglBaseContext, rendererEvents)
            }
            // A fixed-size surface avoids vendor BufferQueue resize transactions that can
            // block HardwareRenderer while the decoder is producing frames.
            it.setEnableHardwareScaler(false)
            it.setMirror(false)
            // Do not give SurfaceViewRenderer an opaque View background. Its decoded
            // frames are presented by a separate Surface layer, so a normal View
            // background can cover every rendered frame on physical devices. The
            // Compose stream container already supplies the black pre-frame backdrop.
            it.setStreamScaling()
            renderer = it
            rendererSurfaceCallback = object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    attachRendererSinkIfAvailable(it)
                }

                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    attachRendererSinkIfAvailable(it)
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    detachRendererSink(it)
                }
            }.also(it.holder::addCallback)
            attachRendererSinkIfAvailable(it)
        }

    fun releaseRenderer(candidate: SurfaceViewRenderer) {
        if (renderer !== candidate) return
        releaseRendererInternal(candidate)
        renderer = null
        rendererSharpnessDrawer = null
    }

    private fun attachRendererSinkIfAvailable(candidate: SurfaceViewRenderer) {
        if (renderer !== candidate || rendererSinkAttached || candidate.holder.surface?.isValid != true) return
        val track = videoTrack ?: return
        firstVideoFrameWatchdog.reset()
        track.addSink(candidate)
        rendererSinkAttached = true
        recordStreamDiagnostic("video renderer sink attached")
    }

    private fun detachRendererSink(candidate: SurfaceViewRenderer) {
        if (renderer !== candidate || !rendererSinkAttached) return
        videoTrack?.removeSink(candidate)
        rendererSinkAttached = false
        recordStreamDiagnostic("video renderer sink detached surface=${candidate.holder.surface?.isValid == true}")
    }

    private fun releaseRendererInternal(candidate: SurfaceViewRenderer) {
        prepareRendererForRelease(candidate)
        enqueueNativeTeardown("renderer-release") {
            candidate.release()
        }
    }

    private fun prepareRendererForRelease(candidate: SurfaceViewRenderer) {
        if (renderer === candidate && rendererSinkAttached) {
            val attachedTrack = videoTrack
            rendererSinkAttached = false
            enqueueNativeTeardown("renderer-sink-detach") {
                attachedTrack?.removeSink(candidate)
            }
            recordStreamDiagnostic("video renderer sink detach queued")
        }
        rendererSurfaceCallback?.let(candidate.holder::removeCallback)
        rendererSurfaceCallback = null
        candidate.hideSurfaceBeforeRelease()
    }

    private fun SurfaceViewRenderer.hideSurfaceBeforeRelease() {
        // SurfaceView frames are composited in a separate native layer. Hide that
        // layer before tearing down WebRTC so a stale/pre-frame buffer cannot remain
        // above the next Compose screen while SurfaceFlinger processes the detach.
        alpha = 0f
        visibility = View.GONE
    }

    fun updateRendererSettings(settings: StreamSettings) {
        this.settings = this.settings.copy(
            mouseSensitivity = settings.mouseSensitivity,
            mouseAcceleration = settings.mouseAcceleration,
            streamSharpeningEnabled = settings.streamSharpeningEnabled,
            streamSharpeningAmount = settings.streamSharpeningAmount,
            mouseScrollSensitivity = settings.mouseScrollSensitivity,
        )
        rendererSharpnessDrawer?.amount = streamSharpnessShaderStrength(settings.streamSharpeningEnabled, settings.streamSharpeningAmount)
        renderer?.setStreamScaling()
    }

    private fun SurfaceViewRenderer.setStreamScaling() {
        // Keep the complete decoded frame inside the SurfaceView. Phone edge-to-edge
        // presentation is applied by scaling the View itself, never by cropping video.
        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
    }

    fun updateHapticsSettings(phoneFallbackEnabled: Boolean) {
        if (phoneRumbleFallbackEnabled == phoneFallbackEnabled) return
        phoneRumbleFallbackEnabled = phoneFallbackEnabled
        if (!phoneFallbackEnabled) {
            cancelPhoneRumble()
        }
        updateHapticsAdvertisement(force = true)
    }

    fun updateControllerMouseAssistAutoArm(enabled: Boolean) {
        controllerMouseAutoArmOnStart = enabled
        if (!enabled) {
            setControllerMouseAssistActive(false)
        }
    }

    fun updateAndroidTvProfile(enabled: Boolean) {
        if (androidTvProfile == enabled) return
        androidTvProfile = enabled
        livenessWatchdog = newStreamLivenessWatchdog(enabled)
        firstVideoFrameWatchdog = FirstVideoFrameWatchdog(
            timeoutMs = firstVideoFrameRecoveryTimeoutMs(enabled),
        )
        recordStreamDiagnostic("recovery profile=${if (enabled) "android-tv" else "mobile"}")
    }

    fun setControllerMouseAssistEnabled(enabled: Boolean) {
        setControllerMouseAssistActive(enabled)
    }

    fun setControllerMouseEmulationActive(enabled: Boolean) {
        if (controllerMouseEmulationActive == enabled) return
        if (!enabled) {
            // Release any held mouse buttons so state stays clean.
            releaseControllerMouseButtons()
            // Zero both physical and virtual left-stick memory so neither controller path
            // delivers stale deflection to the game after mode is disabled.
            lastLeftStickX = 0
            lastLeftStickY = 0
            virtualLeftStickActive = false
            virtualLeftStickX = 0
            virtualLeftStickY = 0
            physicalLeftStickX = 0f
            physicalLeftStickY = 0f
            physicalRightStickX = 0f
            physicalRightStickY = 0f
        }
        controllerMouseEmulationActive = enabled
        updateControllerMouseLoop()
        // Push a fresh gamepad state immediately so the zeroed stick is sent before any next frame.
        sendCurrentGamepadState()
        NativeInputDiagnostics.add("controller mouse emulation ${if (enabled) "enabled" else "disabled"}")
    }

    private fun startControllerMouseLoop() {
        if (controllerMouseLoopJob?.isActive == true) return
        controllerMouseLoopJob = scope.launch {
            val currentJob = coroutineContext[Job]
            while (currentJob?.isActive == true) {
                // Poll/send mouse updates at 60Hz (approx 16ms delay; physical caches are cleared on disconnect/reset)
                delay(16L)
                if (controllerMouseEmulationActive) {
                    sendControllerMouseMove(physicalLeftStickX, physicalLeftStickY)
                    sendControllerMouseScroll(physicalRightStickY)
                }
                if (controllerMouseAssistActive) {
                    sendControllerMouseMove(physicalRightStickX, physicalRightStickY)
                }
            }
        }
    }

    private fun updateControllerMouseLoop() {
        if (shouldRunControllerMouseLoop(controllerMouseAssistActive, controllerMouseEmulationActive)) {
            startControllerMouseLoop()
        } else {
            stopControllerMouseLoop()
        }
    }

    private fun stopControllerMouseLoop() {
        controllerMouseLoopJob?.cancel()
        controllerMouseLoopJob = null
        physicalLeftStickX = 0f
        physicalLeftStickY = 0f
        physicalRightStickX = 0f
        physicalRightStickY = 0f
    }

    fun start(session: SessionInfo, settings: StreamSettings) {
        if (released) return
        this.session = session
        this.settings = settings
        transportGeneration += 1
        reconnectAttempts = 0
        videoSafeFallbackApplied = false
        catastrophicResolutionCodecFallbacks = 0
        sessionRecoveryRequested = false
        lastStatsSample = null
        livenessWatchdog.reset()
        firstVideoFrameWatchdog.reset()
        onStats(StreamRuntimeStats())
        audioDeviceModule.setSpeakerMute(audioMuted)
        audioDeviceModule.setMicrophoneMute(
            settings.microphoneMode == MicrophoneMode.Disabled || microphoneMuted,
        )
        closeTransport(clearInputState = false)
        armControllerMouseAssistForSession()
        recordStreamDiagnostic(
            "start session=${streamDiagnosticId(session.sessionId)} status=${session.status} server=${session.serverIp.take(96)} signaling=${signalingUrlForDiagnostics(session.signalingUrl, session.sessionId)} settings=${settings.resolution}/${settings.fps}/${settings.codec} bitrate=${settings.maxBitrateMbps} microphone=${settings.microphoneMode.name}",
        )
        startTransport(session, settings, transportGeneration)
        updateControllerMouseLoop()
    }

    fun stop() {
        stopControllerMouseLoop()
        transportGeneration += 1
        reconnectAttempts = 0
        sessionRecoveryRequested = false
        livenessWatchdog.reset()
        firstVideoFrameWatchdog.reset()
        closeTransport(clearInputState = true)
        emitState("Stopped")
    }

    fun release() {
        if (released) return
        released = true
        if (androidTvProfile) {
            val activeRenderer = renderer
            activeRenderer?.let(::prepareRendererForRelease)
            renderer = null
            rendererSharpnessDrawer = null
            stop()
            scope.launch {
                delay(ANDROID_TV_CODEC_RELEASE_SETTLE_MS)
                finishRelease(activeRenderer)
            }
            return
        }
        stop()
        renderer?.let { activeRenderer ->
            releaseRendererInternal(activeRenderer)
        }
        renderer = null
        rendererSharpnessDrawer = null
        finishRelease()
    }

    private fun finishRelease(preparedRenderer: SurfaceViewRenderer? = null) {
        inputScope.cancel()
        inputExecutor.shutdown()
        val activeFactory = factory
        factory = null
        enqueueNativeTeardown("runtime-release") {
            preparedRenderer?.let { renderer ->
                runCatching { renderer.release() }
                    .onFailure { error -> recordStreamDiagnostic("renderer release failed error=${error.message.orEmpty()}") }
            }
            runCatching { activeFactory?.dispose() }
                .onFailure { error -> recordStreamDiagnostic("peer factory release failed error=${error.message.orEmpty()}") }
            runCatching { audioDeviceModule.release() }
                .onFailure { error -> recordStreamDiagnostic("audio module release failed error=${error.message.orEmpty()}") }
            runCatching { eglBase.release() }
                .onFailure { error -> recordStreamDiagnostic("EGL release failed error=${error.message.orEmpty()}") }
        }
        teardownExecutor.shutdown()
        scope.cancel()
    }

    private fun resetInputState() {
        virtualButtons = 0
        virtualLeftTrigger = 0
        virtualRightTrigger = 0
        virtualLeftStickActive = false
        virtualLeftStickX = 0
        virtualLeftStickY = 0
        virtualRightStickActive = false
        virtualRightStickX = 0
        virtualRightStickY = 0
        virtualControllerVisible = false
        physicalControllerConnected = false
        physicalControllerActive = false
        physicalButtons = 0
        physicalHatButtons = 0
        steamMenuChordButtons = 0
        physicalSteamOverlayChord.reset()
        virtualSteamOverlayChord.reset()
        physicalLeftTriggerButtonPressed = false
        physicalRightTriggerButtonPressed = false
        guideAutoReleaseJob?.cancel()
        guideAutoReleaseJob = null
        steamMenuChordJob?.cancel()
        steamMenuChordJob = null
        physicalSteamOverlayChordReleaseJob?.cancel()
        physicalSteamOverlayChordReleaseJob = null
        virtualSteamOverlayChordReleaseJob?.cancel()
        virtualSteamOverlayChordReleaseJob = null
        stopAllGamepadRumble()
        lastLeftTrigger = 0
        lastRightTrigger = 0
        lastLeftStickX = 0
        lastLeftStickY = 0
        lastRightStickX = 0
        lastRightStickY = 0
        physicalLeftStickX = 0f
        physicalLeftStickY = 0f
        physicalRightStickX = 0f
        physicalRightStickY = 0f
        controllerScrollAccumulator = 0f
        controllerMouseAssistActive = false
        controllerMouseAssistAutoArmed = false
        controllerMouseEmulationActive = false
        controllerMouseMoveLogged = false
        controllerMouseLeftButtonDown = false
        controllerMouseRightButtonDown = false
        activeControllerId = 0
        controllerSlots.clear()
        controllerAxisAvailability.clear()
        mousePositionValid = false
        mouseSuppressNextAbsoluteDelta = false
        inputDropLogged = false
        externalMouseEventLogged = false
        externalMouseMoveSentLogged = false
        externalMouseAbsoluteJumpLogged = false
        hardwareKeyboardEventLogged = false
        physicalGamepadAxisLogged = false
        inputEncoder.resetGamepadSequences()
        emitControllerMouseAssistChanged(false)
    }

    fun dispatchKey(event: KeyEvent): Boolean {
        if (event.isGamepadEvent() && dispatchGamepadKey(event)) {
            return true
        }
        val key = InputEncoder.mapKeyEvent(event)
        val hardwareKeyboard = event.isHardwareKeyboardSource()
        if (hardwareKeyboard && !hardwareKeyboardEventLogged) {
            hardwareKeyboardEventLogged = true
            NativeInputDiagnostics.add("hardware keyboard event action=${event.action} key=${event.keyCode} scan=${event.scanCode} source=${event.source} device=${event.deviceId} mapped=${key != null}")
        }
        val packet = key?.let { if (event.action == KeyEvent.ACTION_DOWN) inputEncoder.encodeKeyDown(it) else inputEncoder.encodeKeyUp(it) }
        if (packet == null) {
            if (hardwareKeyboard && (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP)) {
                NativeInputDiagnostics.add("hardware keyboard consumed unmapped key=${event.keyCode} action=${event.action}")
                return true
            }
            return false
        }
        val sent = sendReliableInput(packet)
        if (hardwareKeyboard && !sent) {
            NativeInputDiagnostics.add("hardware keyboard consumed without send key=${event.keyCode} reliable=${reliableInput?.state()} partial=${partiallyReliableInput?.state()}")
        }
        return sent || hardwareKeyboard
    }

    fun dispatchMotion(event: MotionEvent): Boolean {
        if (event.isGamepadMotionEvent()) {
            return dispatchJoystick(event)
        }
        if (event.isMouseLikePointer()) {
            return dispatchMouseLikePointer(event)
        }
        return false
    }

    fun sendRawMouseMove(dx: Int, dy: Int, partiallyReliable: Boolean = false): Boolean {
        return sendInput(
            inputEncoder.encodeMouseMove(dx, dy),
            partiallyReliable = partiallyReliable,
        )
    }

    /** Sends one batch of finger updates. Reliable: a dropped lift leaves a finger stuck down. */
    internal fun sendNativeTouch(touches: List<TouchRecord>): Boolean {
        val packet = inputEncoder.encodeTouchBatch(touches) ?: return false
        return sendReliableInput(packet)
    }

    fun sendTouchMouseMove(dx: Int, dy: Int, partiallyReliable: Boolean = true): Boolean {
        var adjustedDx = dx * settings.mouseSensitivity
        var adjustedDy = dy * settings.mouseSensitivity
        if (settings.mouseAcceleration > 1) {
            val speed = sqrt(adjustedDx * adjustedDx + adjustedDy * adjustedDy)
            val strength = (settings.mouseAcceleration - 1f) / 149f
            val accelFactor = 1f + min(0.6f * strength, (speed / 50f) * strength)
            adjustedDx *= accelFactor
            adjustedDy *= accelFactor
        }
        return sendInput(
            inputEncoder.encodeMouseMove(adjustedDx.roundToInt(), adjustedDy.roundToInt()),
            partiallyReliable = partiallyReliable,
        )
    }

    private fun dispatchMouseLikePointer(event: MotionEvent): Boolean {
        if (!externalMouseEventLogged) {
            externalMouseEventLogged = true
            val relativeDx = if (Build.VERSION.SDK_INT >= 26) event.getAxisValue(MotionEvent.AXIS_RELATIVE_X) else 0f
            val relativeDy = if (Build.VERSION.SDK_INT >= 26) event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y) else 0f
            NativeInputDiagnostics.add(
                "external mouse event action=${event.actionMasked} source=${event.source} device=${event.deviceId} buttons=${event.buttonState} relativeDx=$relativeDx relativeDy=$relativeDy",
            )
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE,
            MotionEvent.ACTION_MOVE,
            -> {
                val relativeDx = if (Build.VERSION.SDK_INT >= 26) event.getAxisValue(MotionEvent.AXIS_RELATIVE_X) else 0f
                val relativeDy = if (Build.VERSION.SDK_INT >= 26) event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y) else 0f
                if (abs(relativeDx) >= 0.5f || abs(relativeDy) >= 0.5f) {
                    val sent = sendTouchMouseMove(relativeDx.roundToInt(), relativeDy.roundToInt())
                    if (sent && !externalMouseMoveSentLogged) {
                        externalMouseMoveSentLogged = true
                        NativeInputDiagnostics.add("external mouse move sent source=${event.source} device=${event.deviceId} mode=relative")
                    }
                    mousePositionValid = false
                } else if (event.isRelativeMousePointer()) {
                    val positionDx = event.x
                    val positionDy = event.y
                    if (abs(positionDx) >= 0.5f || abs(positionDy) >= 0.5f) {
                        val sent = sendTouchMouseMove(positionDx.roundToInt(), positionDy.roundToInt())
                        if (sent && !externalMouseMoveSentLogged) {
                            externalMouseMoveSentLogged = true
                            NativeInputDiagnostics.add("external mouse move sent source=${event.source} device=${event.deviceId} mode=relativePosition")
                        }
                    }
                    mousePositionValid = false
                } else if (mousePositionValid && mouseLastDeviceId == event.deviceId && mouseLastSource == event.source) {
                    val dx = event.x - mouseLastX
                    val dy = event.y - mouseLastY
                    if (abs(dx) >= 0.5f || abs(dy) >= 0.5f) {
                        val discontinuous = mouseSuppressNextAbsoluteDelta ||
                            abs(dx) > EXTERNAL_MOUSE_ABSOLUTE_DELTA_LIMIT_PX ||
                            abs(dy) > EXTERNAL_MOUSE_ABSOLUTE_DELTA_LIMIT_PX
                        if (discontinuous) {
                            if (!externalMouseAbsoluteJumpLogged) {
                                externalMouseAbsoluteJumpLogged = true
                                NativeInputDiagnostics.add("external mouse absolute delta rebased source=${event.source} device=${event.deviceId} dx=${dx.roundToInt()} dy=${dy.roundToInt()}")
                            }
                        } else {
                            val sent = sendTouchMouseMove(dx.roundToInt(), dy.roundToInt())
                            if (sent && !externalMouseMoveSentLogged) {
                                externalMouseMoveSentLogged = true
                                NativeInputDiagnostics.add("external mouse move sent source=${event.source} device=${event.deviceId} mode=absoluteDelta")
                            }
                        }
                        mouseSuppressNextAbsoluteDelta = false
                    }
                } else {
                    mouseSuppressNextAbsoluteDelta = false
                }
                if (!event.isRelativeMousePointer()) {
                    rememberMousePosition(event)
                }
            }
            MotionEvent.ACTION_DOWN -> {
                mouseSuppressNextAbsoluteDelta = true
                rememberMousePosition(event)
                sendReliableInput(inputEncoder.encodeMouseButton(InputEncoder.INPUT_MOUSE_BUTTON_DOWN, event.primaryMouseButton()))
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> {
                mousePositionValid = false
                mouseSuppressNextAbsoluteDelta = true
                sendReliableInput(inputEncoder.encodeMouseButton(InputEncoder.INPUT_MOUSE_BUTTON_UP, event.primaryMouseButton()))
            }
            MotionEvent.ACTION_BUTTON_PRESS -> {
                mouseSuppressNextAbsoluteDelta = true
                rememberMousePosition(event)
                val handled = sendReliableInput(inputEncoder.encodeMouseButton(InputEncoder.INPUT_MOUSE_BUTTON_DOWN, event.actionButton.toGfnMouseButton()))
                if (!handled) {
                    NativeInputDiagnostics.add("external mouse button consumed without send action=press button=${event.actionButton} reliable=${reliableInput?.state()} partial=${partiallyReliableInput?.state()}")
                }
                return true
            }
            MotionEvent.ACTION_BUTTON_RELEASE -> {
                mousePositionValid = false
                mouseSuppressNextAbsoluteDelta = true
                val handled = sendReliableInput(inputEncoder.encodeMouseButton(InputEncoder.INPUT_MOUSE_BUTTON_UP, event.actionButton.toGfnMouseButton()))
                if (!handled) {
                    NativeInputDiagnostics.add("external mouse button consumed without send action=release button=${event.actionButton} reliable=${reliableInput?.state()} partial=${partiallyReliableInput?.state()}")
                }
                return true
            }
            MotionEvent.ACTION_SCROLL -> {
                val vertical = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                if (abs(vertical) >= 0.01f) {
                    sendReliableInput(inputEncoder.encodeMouseWheel((vertical * 120).roundToInt()))
                }
            }
        }
        return true
    }

    private fun rememberMousePosition(event: MotionEvent) {
        mouseLastDeviceId = event.deviceId
        mouseLastSource = event.source
        mouseLastX = event.x
        mouseLastY = event.y
        mousePositionValid = true
    }

    fun sendTouchMouseClick(delayBeforeDownMs: Long = 0L) {
        scope.launch {
            if (delayBeforeDownMs > 0) {
                delay(delayBeforeDownMs)
            }
            if (!setTouchMouseButton(true)) return@launch
            delay(160L)
            setTouchMouseButton(false)
        }
    }

    fun sendTouchMouseRightClick() {
        scope.launch {
            if (!sendMouseButton(button = 3, pressed = true, source = "touch mouse right click")) return@launch
            delay(160L)
            sendMouseButton(button = 3, pressed = false, source = "touch mouse right click")
        }
    }

    fun sendTouchMouseWheel(delta: Int) {
        sendReliableInput(inputEncoder.encodeMouseWheel(delta))
    }

    fun sendKeyCode(keyCode: Int) {
        val down = KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, keyCode, 0)
        val up = KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0)
        val mapped = InputEncoder.mapKeyEvent(down)
        val downQueued = dispatchKey(down)
        val upQueued = dispatchKey(up)
        NativeInputDiagnostics.add(
            "overlay keyboard key=$keyCode mapped=${mapped != null} " +
                "vk=${mapped?.keycode} scan=${mapped?.scancode} " +
                "downQueued=$downQueued upQueued=$upQueued " +
                "reliable=${reliableInput?.state()} partial=${partiallyReliableInput?.state()}",
        )
    }

    fun sendText(text: String) {
        if (text.isEmpty()) return
        val textToSend = text.take(STREAM_TEXT_SEND_MAX_CHARS)
        scope.launch {
            textSendMutex.withLock {
                textToSend.forEach { char ->
                    sendTextChar(char)
                }
            }
        }
    }

    private fun sendKeyboardPayload(payload: InputEncoder.KeyboardPayload, isDown: Boolean): Boolean =
        sendReliableInput(if (isDown) inputEncoder.encodeKeyDown(payload) else inputEncoder.encodeKeyUp(payload))

    private suspend fun sendTextChar(char: Char) {
        val spec = InputEncoder.mapTextCharToKeySpec(char) ?: return
        if (spec.shift) {
            sendKeyboardPayloadWithRetry(InputEncoder.shiftLeftPayload(modifiers = 0x01), isDown = true)
        }
        val modifiers = if (spec.shift) 0x01 else 0
        sendKeyboardPayloadWithRetry(spec.toKeyboardPayload(modifiers), isDown = true)
        sendKeyboardPayloadWithRetry(spec.toKeyboardPayload(modifiers), isDown = false)
        if (spec.shift) {
            sendKeyboardPayloadWithRetry(InputEncoder.shiftLeftPayload(modifiers = 0), isDown = false)
        }
        delay(STREAM_TEXT_KEY_DELAY_MS)
    }

    private suspend fun sendKeyboardPayloadWithRetry(payload: InputEncoder.KeyboardPayload, isDown: Boolean): Boolean {
        repeat(STREAM_TEXT_SEND_ATTEMPTS) { attempt ->
            if (sendKeyboardPayload(payload, isDown)) {
                delay(STREAM_TEXT_PACKET_DELAY_MS)
                return true
            }
            if (attempt < STREAM_TEXT_SEND_ATTEMPTS - 1) {
                delay(STREAM_TEXT_RETRY_DELAY_MS)
            }
        }
        NativeInputDiagnostics.add(
            "overlay keyboard dropped key=${payload.keycode} action=${if (isDown) "down" else "up"} reliable=${reliableInput?.state()} partial=${partiallyReliableInput?.state()}",
        )
        return false
    }

    fun setAudioMuted(muted: Boolean) {
        audioMuted = muted
        audioDeviceModule.setSpeakerMute(muted)
        audioTrack?.setEnabled(!muted)
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        microphoneMuted = !enabled
        audioDeviceModule.setMicrophoneMute(!enabled)
        microphoneTrack?.setEnabled(enabled)
        recordStreamDiagnostic("microphone ${if (enabled) "enabled" else "muted"}")
    }

    fun setTouchMouseButton(pressed: Boolean): Boolean {
        return sendMouseButton(button = 1, pressed = pressed, source = "touch mouse")
    }

    private fun sendMouseButton(button: Int, pressed: Boolean, source: String): Boolean {
        val packet = inputEncoder.encodeMouseButton(
            if (pressed) InputEncoder.INPUT_MOUSE_BUTTON_DOWN else InputEncoder.INPUT_MOUSE_BUTTON_UP,
            button,
        )
        val reliableSent = sendInput(packet, partiallyReliable = false)
        val partialSent = sendInput(packet, partiallyReliable = true)
        NativeInputDiagnostics.add(
            "$source button=$button ${if (pressed) "down" else "up"} reliableSent=$reliableSent partialSent=$partialSent reliable=${reliableInput?.state()} partial=${partiallyReliableInput?.state()}",
        )
        return reliableSent || partialSent
    }

    private fun armControllerMouseAssistForSession() {
        if (!controllerMouseAutoArmOnStart) return
        controllerMouseAssistActive = true
        controllerMouseAssistAutoArmed = true
        controllerMouseMoveLogged = false
        emitControllerMouseAssistChanged(true)
        NativeInputDiagnostics.add("controller mouse assist auto-armed for Android TV")
    }

    private fun setControllerMouseAssistActive(active: Boolean, autoArmed: Boolean = false) {
        if (controllerMouseAssistActive == active && controllerMouseAssistAutoArmed == (autoArmed && active)) return
        if (!active) releaseControllerMouseButtons()
        controllerMouseAssistActive = active
        controllerMouseAssistAutoArmed = autoArmed && active
        updateControllerMouseLoop()
        controllerMouseMoveLogged = false
        sendCurrentGamepadState()
        emitControllerMouseAssistChanged(active)
        NativeInputDiagnostics.add("controller mouse assist ${if (active) "enabled" else "disabled"} auto=$controllerMouseAssistAutoArmed")
    }

    private fun emitControllerMouseAssistChanged(active: Boolean) {
        scope.launch { onControllerMouseAssistChanged(active) }
    }

    private fun releaseControllerMouseButtons() {
        if (controllerMouseLeftButtonDown) {
            controllerMouseLeftButtonDown = false
            sendMouseButton(button = 1, pressed = false, source = "controller mouse")
        }
        if (controllerMouseRightButtonDown) {
            controllerMouseRightButtonDown = false
            sendMouseButton(button = 3, pressed = false, source = "controller mouse")
        }
    }

    private fun setControllerMouseButton(button: Int, pressed: Boolean): Boolean {
        when (button) {
            1 -> {
                if (controllerMouseLeftButtonDown == pressed) return true
                controllerMouseLeftButtonDown = pressed
            }
            3 -> {
                if (controllerMouseRightButtonDown == pressed) return true
                controllerMouseRightButtonDown = pressed
            }
            else -> return false
        }
        val sent = sendMouseButton(button = button, pressed = pressed, source = "controller mouse")
        if (!pressed && controllerMouseAssistAutoArmed && button == 1) {
            setControllerMouseAssistActive(false)
        }
        return sent
    }

    fun setVirtualButton(buttonMask: Int, pressed: Boolean) {
        // When left-stick mouse emulation is active, intercept A (left click) and B (right click).
        if (controllerMouseEmulationActive) {
            val mouseButton = AndroidControllerMouseAssist.mouseButtonForGamepad(buttonMask)
            if (mouseButton != null) {
                val sent = setControllerMouseButton(mouseButton, pressed)
                recordVirtualButtonDiagnostic(
                    buttonMask = buttonMask,
                    pressed = pressed,
                    route = "mouse-$mouseButton",
                    sent = sent,
                )
                return
            }
        }
        virtualButtons = if (pressed) virtualButtons or buttonMask else virtualButtons and buttonMask.inv()
        val steamOverlayChordActivated = virtualSteamOverlayChord.update(virtualButtons)
        val sent = sendCurrentGamepadState()
        recordVirtualButtonDiagnostic(
            buttonMask = buttonMask,
            pressed = pressed,
            route = "gamepad",
            sent = sent,
        )
        if (steamOverlayChordActivated) {
            scheduleVirtualSteamOverlayChordRelease()
        }
    }

    private fun recordVirtualButtonDiagnostic(
        buttonMask: Int,
        pressed: Boolean,
        route: String,
        sent: Boolean,
    ) {
        val action = if (pressed) "down" else "up"
        val maskHex = buttonMask.toString(16).padStart(4, '0')
        NativeInputDiagnostics.addRetained(
            key = "controller.virtual-button.$maskHex.$action",
            message = "virtual gamepad button mask=0x$maskHex action=$action route=$route sent=$sent " +
                "buttons=$virtualButtons reliable=${reliableInput?.state()} partial=${partiallyReliableInput?.state()}",
        )
    }

    fun openSteamMenu() {
        steamMenuChordJob?.cancel()
        val controllerId = activeControllerId
        steamMenuChordButtons = SteamMenuChord.buttons(aPressed = false)
        sendCurrentGamepadState(controllerId)
        steamMenuChordJob = scope.launch {
            delay(STEAM_MENU_MODIFIER_DELAY_MS)
            steamMenuChordButtons = SteamMenuChord.buttons(aPressed = true)
            sendCurrentGamepadState(controllerId)
            delay(GAMEPAD_GUIDE_AUTO_RELEASE_MS)
            steamMenuChordButtons = SteamMenuChord.buttons(aPressed = false)
            sendCurrentGamepadState(controllerId)
            delay(STEAM_MENU_MODIFIER_DELAY_MS)
            steamMenuChordButtons = 0
            sendCurrentGamepadState(controllerId)
            NativeInputDiagnostics.add("Steam Menu sent Guide+A chord slot=$controllerId")
        }
    }

    fun setVirtualTrigger(left: Boolean, pressed: Boolean) {
        if (left) {
            virtualLeftTrigger = if (pressed) 255 else 0
        } else {
            virtualRightTrigger = if (pressed) 255 else 0
        }
        sendCurrentGamepadState()
    }

    fun setVirtualLeftStick(x: Float, y: Float) {
        val scale = radialDeadzoneScale(x, y, deadzone = 0.08f)
        val normalizedX = x * scale
        val normalizedY = y * scale
        if (controllerMouseEmulationActive) {
            // Redirect left-stick input to mouse movement; keep virtual stick zeroed so the game
            // receives no stick deflection from the touch controller either.
            physicalLeftStickX = normalizedX
            physicalLeftStickY = normalizedY
            virtualLeftStickActive = false
            virtualLeftStickX = 0
            virtualLeftStickY = 0
            sendCurrentGamepadState()
            return
        }
        virtualLeftStickActive = normalizedX != 0f || normalizedY != 0f
        virtualLeftStickX = normalizeToInt16(normalizedX)
        virtualLeftStickY = normalizeToInt16(-normalizedY)
        sendCurrentGamepadState()
    }

    fun setVirtualRightStick(x: Float, y: Float) {
        val scale = radialDeadzoneScale(x, y, deadzone = 0.08f)
        val normalizedX = x * scale
        val normalizedY = y * scale
        if (controllerMouseEmulationActive) {
            // Redirect right-stick input to scrolling; keep virtual stick zeroed.
            physicalRightStickX = normalizedX
            physicalRightStickY = normalizedY
            virtualRightStickActive = false
            virtualRightStickX = 0
            virtualRightStickY = 0
            sendCurrentGamepadState()
            return
        }
        virtualRightStickActive = normalizedX != 0f || normalizedY != 0f
        virtualRightStickX = normalizeToInt16(normalizedX)
        virtualRightStickY = normalizeToInt16(-normalizedY)
        sendCurrentGamepadState()
    }

    fun setVirtualControllerVisible(visible: Boolean) {
        if (virtualControllerVisible == visible) return
        virtualControllerVisible = visible
        sendCurrentGamepadState()
    }

    private fun startTransport(session: SessionInfo, settings: StreamSettings, generation: Int) {
        inputDropLogged = false
        lastIceState = null
        lastStatsSample = null
        firstDecodedResolutionEvaluated = false
        transportHasStableMedia = false
        consecutiveTransportProgressSamples = 0
        firstVideoFrameWatchdog.reset()
        emitStats(StreamRuntimeStats())
        audioDeviceModule.setSpeakerMute(audioMuted)
        audioDeviceModule.setMicrophoneMute(
            settings.microphoneMode == MicrophoneMode.Disabled || microphoneMuted,
        )
        recordStreamDiagnostic(
            "transport start generation=$generation reconnectAttempts=$reconnectAttempts session=${streamDiagnosticId(session.sessionId)} iceServers=${session.iceServers.size} media=${session.mediaConnectionInfo?.let { "${it.ip}:${it.port}" } ?: "unknown"}",
        )
        emitState(if (reconnectAttempts > 0) "Reconnecting signaling" else "Connecting signaling")
        signaling = GfnSignalingClient(session, settings = settings) { event ->
            handleSignaling(event, generation)
        }.also { it.connect() }
    }

    private fun closeTransport(clearInputState: Boolean, cancelRecovery: Boolean = true) {
        if (peerConnection != null || signaling != null || reliableInput != null || partiallyReliableInput != null) {
            recordStreamDiagnostic("transport close clearInput=$clearInputState cancelRecovery=$cancelRecovery lastIce=${lastIceState?.name ?: "none"}")
        }
        if (cancelRecovery) {
            iceRecoveryJob?.cancel()
            iceRecoveryJob = null
        }
        heartbeatJob?.cancel()
        gamepadKeepaliveJob?.cancel()
        statsJob?.cancel()
        offerTimeoutJob?.cancel()
        heartbeatJob = null
        gamepadKeepaliveJob = null
        statsJob = null
        offerTimeoutJob = null
        lastStatsSample = null
        lastIceState = null
        livenessWatchdog.reset()
        val closingSignaling = signaling
        val closingVideoTrack = videoTrack
        val closingRenderer = renderer
        val closingRendererSinkAttached = rendererSinkAttached
        val closingMicrophone = takeMicrophoneResources()
        val closingPeerConnection = peerConnection
        signaling = null
        reliableInput = null
        partiallyReliableInput = null
        statsChannel = null
        lastParsedGameFps = null
        partiallyReliableGamepadMask = 0
        hapticsAdvertised = null
        if (clearInputState) resetInputState()
        rendererSinkAttached = false
        videoTrack = null
        audioTrack = null
        peerConnection = null
        enqueueNativeTeardown("transport-close") {
            runCatching { closingSignaling?.disconnect() }
                .onFailure { error -> recordStreamDiagnostic("signaling disconnect failed error=${error.message.orEmpty()}") }
            if (closingRendererSinkAttached && closingRenderer != null) {
                runCatching { closingVideoTrack?.removeSink(closingRenderer) }
                    .onFailure { error -> recordStreamDiagnostic("video sink detach failed error=${error.message.orEmpty()}") }
            }
            runCatching { disposeMicrophoneResources(closingMicrophone) }
                .onFailure { error -> recordStreamDiagnostic("microphone release failed error=${error.message.orEmpty()}") }
            runCatching { closingPeerConnection?.close() }
                .onFailure { error -> recordStreamDiagnostic("peer close failed error=${error.message.orEmpty()}") }
            runCatching { closingPeerConnection?.dispose() }
                .onFailure { error -> recordStreamDiagnostic("peer dispose failed error=${error.message.orEmpty()}") }
        }
    }

    private fun handleSignaling(event: SignalingEvent, generation: Int) {
        if (generation != transportGeneration) return
        when (event) {
            SignalingEvent.Connected -> {
                recordStreamDiagnostic("signaling connected generation=$generation")
                emitState("Waiting for offer")
                startOfferTimeout(generation)
            }
            is SignalingEvent.Disconnected -> {
                recordStreamDiagnostic("signaling disconnected ${event.reason}")
                val disposition = signalingFailureDisposition(event.reason, normalClosureMeansSessionEnded = true)
                if (shouldPreserveMediaAfterSignalingFailure(disposition, lastIceState)) {
                    recordStreamDiagnostic(
                        "signaling disconnected while ICE=${lastIceState?.name}; preserving active media transport",
                    )
                    return
                }
                when (disposition) {
                    SignalingFailureDisposition.SessionEnded -> {
                        recordStreamDiagnostic("Signaling disconnected normally. Stopping stream.")
                        stop()
                        scope.launch { onStreamStopped() }
                    }
                    SignalingFailureDisposition.RecoverSession -> {
                        recordStreamDiagnostic("Signaling endpoint is stale. Recovering cloud session.")
                        requestSessionRecovery("Signaling endpoint became unavailable while connecting to the cloud session.")
                    }
                    SignalingFailureDisposition.RetryTransport ->
                        scheduleTransportReconnect("Signaling disconnected: ${event.reason}", SIGNALING_RECONNECT_DELAY_MS, generation)
                }
            }
            is SignalingEvent.Error -> {
                recordStreamDiagnostic("signaling error ${event.message}")
                val disposition = signalingFailureDisposition(event.message)
                if (shouldPreserveMediaAfterSignalingFailure(disposition, lastIceState)) {
                    recordStreamDiagnostic(
                        "signaling error while ICE=${lastIceState?.name}; preserving active media transport",
                    )
                    return
                }
                when (disposition) {
                    SignalingFailureDisposition.SessionEnded -> {
                        recordStreamDiagnostic("Signaling error indicates session terminated. Stopping stream.")
                        stop()
                        scope.launch { onStreamStopped() }
                    }
                    SignalingFailureDisposition.RecoverSession -> {
                        recordStreamDiagnostic("Signaling endpoint is stale. Recovering cloud session.")
                        requestSessionRecovery("Signaling endpoint became unavailable while connecting to the cloud session.")
                    }
                    SignalingFailureDisposition.RetryTransport ->
                        scheduleTransportReconnect("Signaling failed: ${event.message}", SIGNALING_RECONNECT_DELAY_MS, generation)
                }
            }
            is SignalingEvent.Log -> recordStreamDiagnostic(event.message)
            is SignalingEvent.RemoteIce -> {
                val added = peerConnection?.addIceCandidate(event.candidate)
                recordStreamDiagnostic("remote ICE add requested accepted=${added ?: false} pcReady=${peerConnection != null} ${event.candidate.diagnosticSummary()}")
            }
            is SignalingEvent.Offer -> handleOffer(event.sdp, generation)
        }
    }

    private fun handleOffer(rawOffer: String, generation: Int) {
        val currentSession = session ?: return
        offerTimeoutJob?.cancel()
        offerTimeoutJob = null
        recordStreamDiagnostic(sdpDiagnosticSummary("raw offer", rawOffer))
        val fixed = prepareRemoteOffer(rawOffer, currentSession)
        val preferred = SdpTools.preferCodec(fixed, settings)
        if (fixed != rawOffer) {
            recordStreamDiagnostic(sdpDiagnosticSummary("fixed offer", fixed))
        }
        if (preferred != fixed) {
            recordStreamDiagnostic(sdpDiagnosticSummary("preferred offer", preferred))
        }
        val pc = ensurePeerConnection(currentSession, generation)
        ensureInputDataChannels(pc, preferred)
        inputEncoder.setProtocolVersion(SdpTools.parseInputProtocolVersion(preferred))
        partiallyReliableGamepadMask = SdpTools.parsePartiallyReliableGamepadMask(preferred)
        recordStreamDiagnostic("offer input protocol=${SdpTools.parseInputProtocolVersion(preferred)} partialGamepadMask=$partiallyReliableGamepadMask")
        pc.setRemoteDescription(
            object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    if (generation != transportGeneration || peerConnection !== pc) return
                    recordStreamDiagnostic("remote description set")
                    // WebRTC disposes previously returned transceiver wrappers whenever
                    // getTransceivers() refreshes its cache. Share one snapshot so the
                    // microphone sender remains valid through transport teardown.
                    val transceivers = pc.transceivers
                    applyVideoCodecPreferences(transceivers)
                    attachMicrophoneTrack(pc, transceivers)
                    pc.createAnswer(
                        object : SimpleSdpObserver() {
                            override fun onCreateSuccess(description: SessionDescription?) {
                                if (generation != transportGeneration) return
                                val rawDescription = description ?: run {
                                    failStream("WebRTC returned an empty answer", generation)
                                    return
                                }
                                val munged = SdpTools.mungeAnswerSdp(rawDescription.description, settings.maxBitrateMbps * 1000)
                                recordStreamDiagnostic(sdpDiagnosticSummary("created answer", munged))
                                if (settings.codec != VideoCodec.H264 && !SdpTools.negotiatesCodec(munged, settings.codec)) {
                                    NativeInputDiagnostics.add("local answer did not negotiate requested codec=${settings.codec}; requesting safe fallback")
                                    if (
                                        requestSafeVideoFallback(
                                            message = "${settings.codec} was requested but WebRTC did not negotiate it; restarting with safe H264 profile",
                                            diagnosticReason = "codec negotiation",
                                        )
                                    ) {
                                        return
                                    }
                                    failStream("${settings.codec} requested but not negotiated in local SDP", generation)
                                    return
                                }
                                val answer = SessionDescription(SessionDescription.Type.ANSWER, munged)
                                pc.setLocalDescription(
                                    object : SimpleSdpObserver() {
                                        override fun onSetSuccess() {
                                            if (generation != transportGeneration) return
                                            val nvst = SdpTools.buildNvstSdp(
                                                offerSdp = preferred,
                                                settings = settings,
                                                localAnswer = munged,
                                            )
                                            signaling?.sendAnswer(munged, nvst)
                                            recordStreamDiagnostic("local description set and answer sent")
                                            emitState("Streaming")
                                            startHeartbeat()
                                            startGamepadKeepalive()
                                            startStatsPolling()
                                        }

                                        override fun onSetFailure(error: String?) {
                                            recordStreamDiagnostic("local description failed error=${error.orEmpty()}")
                                            failStream(error ?: "Failed to set local description", generation)
                                        }
                                    },
                                    answer,
                                )
                            }

                            override fun onCreateFailure(error: String?) {
                                recordStreamDiagnostic("answer create failed error=${error.orEmpty()}")
                                failStream(error ?: "Failed to create WebRTC answer", generation)
                            }
                        },
                        MediaConstraints(),
                    )
                }

                override fun onSetFailure(error: String?) {
                    recordStreamDiagnostic("remote description failed error=${error.orEmpty()}")
                    failStream(error ?: "Failed to apply server offer", generation)
                }
            },
            SessionDescription(SessionDescription.Type.OFFER, preferred),
        )
    }

    private fun prepareRemoteOffer(rawOffer: String, session: SessionInfo): String {
        var prepared = SdpTools.fixServerEndpoint(rawOffer, session.serverIp, session.mediaConnectionInfo)
        if (settings.codec == VideoCodec.H265) {
            val maxLevels = h265ReceiverMaxLevelsByProfile()
            if (maxLevels.isNotEmpty()) {
                val rewritten = SdpTools.rewriteH265LevelIdByProfile(prepared, maxLevels)
                if (rewritten.replacements > 0) {
                    NativeInputDiagnostics.add("h265 level-id clamped replacements=${rewritten.replacements} maxLevels=$maxLevels")
                    prepared = rewritten.sdp
                }
            }
            if (!supportsH265TierFlagOne()) {
                val rewritten = SdpTools.rewriteH265TierFlag(prepared, 0)
                if (rewritten.replacements > 0) {
                    NativeInputDiagnostics.add("h265 tier-flag rewritten replacements=${rewritten.replacements}")
                    prepared = rewritten.sdp
                }
            }
        }
        return prepared
    }

    private fun applyVideoCodecPreferences(transceivers: List<RtpTransceiver>) {
        val preferences = receiverCodecPreferences(settings.codec)
        if (preferences.isEmpty()) return
        val transceiver = transceivers.firstOrNull {
            it.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO ||
                it.receiver?.track()?.kind() == MediaStreamTrack.VIDEO_TRACK_KIND
        } ?: return
        val result = transceiver.setCodecPreferences(preferences)
        if (result.isSuccess) {
            NativeInputDiagnostics.add("codec preferences applied codec=${settings.codec} count=${preferences.size}")
        } else {
            NativeInputDiagnostics.add("codec preferences failed codec=${settings.codec} error=${result.error()?.message.orEmpty()}")
        }
    }

    @Synchronized
    private fun attachMicrophoneTrack(
        pc: PeerConnection,
        transceivers: List<RtpTransceiver>,
    ) {
        val permissionGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (!shouldCaptureMicrophone(settings.microphoneMode, permissionGranted)) {
            recordStreamDiagnostic(
                "microphone not attached mode=${settings.microphoneMode.name} permission=$permissionGranted",
            )
            return
        }

        releaseMicrophoneTrack()
        val audioConstraints = MediaConstraints().apply {
            optional.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            optional.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            optional.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            optional.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }
        val source = requireNotNull(factory).createAudioSource(audioConstraints)
        val track = requireNotNull(factory).createAudioTrack(MICROPHONE_TRACK_ID, source)
        track.setEnabled(!microphoneMuted)

        val audioTransceivers = transceivers.filter {
            it.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO ||
                it.receiver?.track()?.kind() == MediaStreamTrack.AUDIO_TRACK_KIND
        }
        val transceiver = audioTransceivers.firstOrNull { it.mid == GFN_MICROPHONE_MID }
            ?: audioTransceivers.firstOrNull {
                it.direction == RtpTransceiver.RtpTransceiverDirection.SEND_ONLY &&
                    it.sender?.track() == null
            }
            ?: audioTransceivers.firstOrNull { it.sender?.track() == null }

        val sender = if (transceiver != null) {
            when (transceiver.direction) {
                RtpTransceiver.RtpTransceiverDirection.RECV_ONLY ->
                    transceiver.setDirection(RtpTransceiver.RtpTransceiverDirection.SEND_RECV)
                RtpTransceiver.RtpTransceiverDirection.INACTIVE ->
                    transceiver.setDirection(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY)
                else -> Unit
            }
            transceiver.sender.apply {
                setStreams(listOf(MICROPHONE_STREAM_ID))
            }.takeIf { sender ->
                runCatching { sender.setTrack(track, false) }
                    .onFailure { error ->
                        if (error is IllegalStateException && isDisposedRtpSenderFailure(error)) {
                            recordStreamDiagnostic("microphone sender was disposed during setTrack attachment")
                        } else throw error
                    }
                    .getOrDefault(false)
            }
        } else {
            pc.addTrack(track, listOf(MICROPHONE_STREAM_ID))
        }

        if (sender == null) {
            track.dispose()
            source.dispose()
            recordStreamDiagnostic("microphone sender attachment failed")
            return
        }
        microphoneSource = source
        microphoneTrack = track
        microphoneSender = sender
        audioDeviceModule.setMicrophoneMute(microphoneMuted)
        recordStreamDiagnostic(
            "microphone track attached mid=${transceiver?.mid ?: "new"} direction=${transceiver?.direction?.name ?: "new"} muted=$microphoneMuted",
        )
    }

    @Synchronized
    private fun releaseMicrophoneTrack() {
        disposeMicrophoneResources(takeMicrophoneResources())
    }

    @Synchronized
    private fun takeMicrophoneResources(): MicrophoneResources {
        val resources = MicrophoneResources(
            sender = microphoneSender,
            track = microphoneTrack,
            source = microphoneSource,
        )
        microphoneSender = null
        microphoneTrack = null
        microphoneSource = null
        return resources
    }

    private fun disposeMicrophoneResources(resources: MicrophoneResources) {
        try {
            resources.sender?.setTrack(null, false)
        } catch (error: IllegalStateException) {
            if (!isDisposedRtpSenderFailure(error)) throw error
            recordStreamDiagnostic("microphone sender was already disposed during transport close")
        } finally {
            if (resources.track?.isDisposed == false) resources.track.dispose()
            resources.source?.dispose()
        }
    }

    private fun receiverCodecPreferences(codec: VideoCodec): List<RtpCapabilities.CodecCapability> {
        val receiverCaps = runCatching {
            requireNotNull(factory).getRtpReceiverCapabilities(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO).codecs
        }.getOrNull().orEmpty()
        val target = codec.webRtcCodecName()
        val preferred = receiverCaps
            .filter { it.openNowCodecName() == target }
            .let { caps -> if (codec == VideoCodec.H265) caps.sortedBy { it.h265ProfilePriority(settings.prefersTenBitVideo()) } else caps }
        if (preferred.isEmpty()) return emptyList()
        val auxiliary = receiverCaps.filter { it.openNowCodecName() in WEBRTC_AUXILIARY_VIDEO_CODECS }
        return (preferred + auxiliary).distinctBy { it.preferenceKey() }
    }

    private fun h265ReceiverMaxLevelsByProfile(): Map<Int, Int> =
        receiverH265Capabilities()
            .mapNotNull { capability ->
                val profile = capability.codecParameterInt("profile-id") ?: return@mapNotNull null
                val level = capability.codecParameterInt("level-id") ?: return@mapNotNull null
                profile to level
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, levels) -> levels.maxOrNull() ?: 0 }
            .filterValues { it > 0 }

    private fun supportsH265TierFlagOne(): Boolean =
        receiverH265Capabilities().any { it.codecParameterInt("tier-flag") == 1 }

    private fun receiverH265Capabilities(): List<RtpCapabilities.CodecCapability> =
        runCatching {
            requireNotNull(factory).getRtpReceiverCapabilities(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO).codecs
        }.getOrNull().orEmpty()
            .filter { it.openNowCodecName() == VideoCodec.H265.webRtcCodecName() }

    private fun startOfferTimeout(generation: Int) {
        offerTimeoutJob?.cancel()
        offerTimeoutJob = scope.launch {
            delay(OFFER_TIMEOUT_MS)
            if (generation != transportGeneration || peerConnection != null) return@launch
            offerTimeoutJob = null
            NativeInputDiagnostics.add("video offer timeout codec=${settings.codec} resolution=${settings.resolution} bitrate=${settings.maxBitrateMbps}")
            if (
                requestSafeVideoFallback(
                    message = "Timed out waiting for video offer; restarting with safe H264 profile",
                    diagnosticReason = "offer timeout",
                )
            ) {
                return@launch
            }
            restartTransport("Timed out waiting for video offer", videoFailure = true)
        }
    }

    private fun ensurePeerConnection(session: SessionInfo, generation: Int): PeerConnection {
        peerConnection?.let { return it }
        val ice = session.iceServers.map {
            PeerConnection.IceServer.builder(it.urls).apply {
                if (it.username != null) setUsername(it.username)
                if (it.credential != null) setPassword(it.credential)
            }.createIceServer()
        }
        val config = PeerConnection.RTCConfiguration(ice).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }
        recordStreamDiagnostic(
            "peer connection create generation=$generation iceServers=${ice.size} iceUrls=${session.iceServers.flatMap { it.urls }.joinToString(limit = 8) { url -> url.substringBefore('?').take(120) }}",
        )
        val pc = requireNotNull(factory).createPeerConnection(config, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                recordStreamDiagnostic("webrtc signaling state=${state?.name ?: "null"}")
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                handleIceConnectionChange(state, generation)
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                recordStreamDiagnostic("ice receiving=$receiving generation=$generation")
            }
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                recordStreamDiagnostic("ice gathering state=${state?.name ?: "null"} generation=$generation")
            }
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (generation != transportGeneration) return
                if (candidate != null) {
                    recordStreamDiagnostic("local ICE candidate gathered ${candidate.diagnosticSummary()}")
                    signaling?.sendIceCandidate(candidate)
                } else {
                    recordStreamDiagnostic("local ICE candidate gathering complete")
                }
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
                recordStreamDiagnostic("ice candidates removed count=${candidates?.size ?: 0}")
            }
            override fun onAddStream(stream: MediaStream?) {
                if (generation != transportGeneration) return
                recordStreamDiagnostic("media stream added video=${stream?.videoTracks?.size ?: 0} audio=${stream?.audioTracks?.size ?: 0}")
                stream?.videoTracks?.firstOrNull()?.let(::attachVideo)
                stream?.audioTracks?.firstOrNull()?.let {
                    audioTrack = it
                    it.setEnabled(!audioMuted)
                }
            }
            override fun onRemoveStream(stream: MediaStream?) {
                recordStreamDiagnostic("media stream removed video=${stream?.videoTracks?.size ?: 0} audio=${stream?.audioTracks?.size ?: 0}")
            }
            override fun onDataChannel(channel: DataChannel?) {
                if (generation != transportGeneration) return
                if (channel != null) attachDataChannel(channel)
            }
            override fun onRenegotiationNeeded() {
                recordStreamDiagnostic("renegotiation needed")
            }
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                if (generation != transportGeneration) return
                val track = receiver?.track()
                recordStreamDiagnostic("track added kind=${track?.kind().orEmpty()} streams=${streams?.size ?: 0}")
                if (track is VideoTrack) attachVideo(track)
                if (track is AudioTrack) {
                    audioTrack = track
                    track.setEnabled(!audioMuted)
                }
            }
            override fun onTrack(transceiver: RtpTransceiver?) {
                if (generation != transportGeneration) return
                val track = transceiver?.receiver?.track()
                recordStreamDiagnostic("transceiver track kind=${track?.kind().orEmpty()} media=${transceiver?.mediaType?.name ?: "unknown"}")
                if (track is VideoTrack) attachVideo(track)
                if (track is AudioTrack) {
                    audioTrack = track
                    track.setEnabled(!audioMuted)
                }
            }
        }) ?: error("Failed to create PeerConnection")
        peerConnection = pc
        recordStreamDiagnostic("peer connection ready generation=$generation")
        return pc
    }

    private fun handleIceConnectionChange(state: PeerConnection.IceConnectionState?, generation: Int) {
        scope.launch {
            if (generation != transportGeneration) return@launch
            val previous = lastIceState
            lastIceState = state
            recordStreamDiagnostic("ice connection ${previous?.name ?: "none"} -> ${state?.name ?: "null"} generation=$generation")
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED,
                -> {
                    iceRecoveryJob?.cancel()
                    iceRecoveryJob = null
                    livenessWatchdog.markConnected(SystemClock.elapsedRealtime())
                    if (reconnectAttempts > 0) {
                        signaling?.requestKeyframe(
                            reason = "transport_reconnect",
                            backlogFrames = 0,
                            attempt = reconnectAttempts,
                        )
                        recordStreamDiagnostic("reconnect keyframe requested attempt=$reconnectAttempts generation=$generation")
                    }
                    emitState("Streaming")
                }
                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    emitState("ICE_DISCONNECTED")
                    scheduleTransportReconnect("ICE disconnected", ICE_DISCONNECTED_GRACE_MS, generation)
                }
                PeerConnection.IceConnectionState.FAILED -> {
                    emitState("ICE_FAILED")
                    scheduleTransportReconnect("ICE failed", ICE_FAILED_RECONNECT_DELAY_MS, generation)
                }
                PeerConnection.IceConnectionState.CHECKING,
                PeerConnection.IceConnectionState.NEW,
                -> emitState(state.toIceStatusLabel())
                PeerConnection.IceConnectionState.CLOSED -> Unit
                null -> Unit
            }
        }
    }

    private fun scheduleTransportReconnect(reason: String, delayMs: Long, generation: Int) {
        if (generation != transportGeneration || iceRecoveryJob?.isActive == true) {
            recordStreamDiagnostic("reconnect not scheduled reason=$reason generation=$generation activeJob=${iceRecoveryJob?.isActive == true}")
            return
        }
        recordStreamDiagnostic("reconnect scheduled reason=$reason delayMs=$delayMs generation=$generation")
        iceRecoveryJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            if (generation != transportGeneration) return@launch
            if (reason == "ICE disconnected" && lastIceState != PeerConnection.IceConnectionState.DISCONNECTED) return@launch
            restartTransport(reason)
        }
    }

    private fun restartTransport(reason: String, videoFailure: Boolean = false) {
        val currentSession = session ?: return
        val currentSettings = settings
        val hadStableMedia = transportHasStableMedia
        if (
            transportRestartShouldApplySafeVideoFallback(
                videoFailure = videoFailure,
                reconnectAttempts = reconnectAttempts,
                transportHasStableMedia = transportHasStableMedia,
            ) &&
            requestSafeVideoFallback(
                message = "$reason. Restarting the local transport with safe H264 profile.",
                diagnosticReason = "transport reconnect",
                restartWhenAlreadySafe = true,
            )
        ) {
            return
        }
        if (reconnectAttempts >= MAX_TRANSPORT_RECONNECT_ATTEMPTS) {
            recordStreamDiagnostic("reconnect limit reached reason=$reason attempts=$reconnectAttempts")
            requestSessionRecovery("$reason. Stream reconnect failed after $MAX_TRANSPORT_RECONNECT_ATTEMPTS attempts.")
            return
        }
        reconnectAttempts += 1
        transportGeneration += 1
        val generation = transportGeneration
        recordStreamDiagnostic("transport restart reason=$reason attempt=$reconnectAttempts generation=$generation")
        emitState("Reconnecting stream ($reconnectAttempts/$MAX_TRANSPORT_RECONNECT_ATTEMPTS)")
        closeTransport(clearInputState = false, cancelRecovery = false)
        val codecSettleDelayMs = advancedCodecRestartSettleDelayMs(
            codec = currentSettings.codec,
            hadStableMedia = hadStableMedia,
        )
        if (codecSettleDelayMs == 0L) {
            iceRecoveryJob = null
            startTransport(currentSession, currentSettings, generation)
            return
        }
        recordStreamDiagnostic(
            "waiting ${codecSettleDelayMs}ms for ${currentSettings.codec} decoder release before transport restart generation=$generation",
        )
        iceRecoveryJob = scope.launch {
            delay(codecSettleDelayMs)
            if (generation != transportGeneration || session?.sessionId != currentSession.sessionId) return@launch
            iceRecoveryJob = null
            startTransport(currentSession, currentSettings, generation)
        }
    }

    private fun requestSessionRecovery(message: String) {
        if (sessionRecoveryRequested) return
        sessionRecoveryRequested = true
        transportGeneration += 1
        closeTransport(clearInputState = false)
        recordStreamDiagnostic("session recovery requested message=$message")
        emitState("Recovering cloud session")
        emitSessionRecoveryRequired(message)
    }

    private fun failStream(message: String, generation: Int? = null) {
        if (generation != null && generation != transportGeneration) return
        transportGeneration += 1
        recordStreamDiagnostic("stream failed message=$message")
        closeTransport(clearInputState = true)
        emitError(message)
    }

    private fun emitState(message: String) {
        scope.launch { onState(message) }
    }

    private fun emitError(message: String) {
        scope.launch { onError(message) }
    }

    private fun emitVideoTransportFallbackApplied(message: String, fallback: StreamSettings) {
        scope.launch { onVideoTransportFallbackApplied(message, fallback) }
    }

    private fun emitSessionRecoveryRequired(message: String) {
        scope.launch { onSessionRecoveryRequired(message) }
    }

    private fun PeerConnection.IceConnectionState.toIceStatusLabel(): String = "ICE_${name}"

    private fun emitStats(stats: StreamRuntimeStats) {
        scope.launch { onStats(stats) }
    }

    private fun ensureInputDataChannels(pc: PeerConnection, offerSdp: String) {
        if (reliableInput == null) {
            val reliableInit = DataChannel.Init().apply {
                ordered = true
            }
            pc.createDataChannel("input_channel_v1", reliableInit)?.let(::attachDataChannel)
        }

        if (partiallyReliableInput == null) {
            val thresholdMs = SdpTools.parsePartialReliableThresholdMs(offerSdp)
            val partialInit = DataChannel.Init().apply {
                ordered = false
                maxRetransmitTimeMs = thresholdMs
            }
            pc.createDataChannel("input_channel_partially_reliable", partialInit)?.let(::attachDataChannel)
        }
        if (statsChannel == null) {
            val statsInit = DataChannel.Init().apply {
                ordered = false
                maxRetransmits = 0
            }
            pc.createDataChannel("stats_channel", statsInit)?.let(::attachDataChannel)
        }
    }

    private fun attachVideo(track: VideoTrack) {
        val currentTrack = videoTrack
        if (currentTrack?.id() == track.id() && currentTrack.state() != MediaStreamTrack.State.ENDED) {
            currentTrack.setEnabled(true)
            renderer?.let(::attachRendererSinkIfAvailable)
            return
        }
        if (rendererSinkAttached) {
            currentTrack?.removeSink(renderer)
            rendererSinkAttached = false
        }
        videoTrack = track
        track.setEnabled(true)
        renderer?.let(::attachRendererSinkIfAvailable)
        recordStreamDiagnostic("video track attached id=${track.id()} state=${track.state()?.name ?: "unknown"} renderer=${renderer != null} sink=$rendererSinkAttached")
    }

    private fun attachDataChannel(channel: DataChannel) {
        val label = channel.label()
        val normalizedLabel = label.lowercase(Locale.US)
        val role = InputDataChannelLabels.classify(label)
        NativeInputDiagnostics.addRetained(
            key = "channel.$normalizedLabel",
            message = "data channel attached label=$normalizedLabel role=$role state=${channel.state()}",
        )
        if (normalizedLabel == "stats_channel") {
            statsChannel = channel
            channel.registerObserver(object : DataChannel.Observer {
                override fun onBufferedAmountChange(previousAmount: Long) = Unit
                override fun onStateChange() {
                    NativeInputDiagnostics.add("stats channel state label=$normalizedLabel state=${channel.state()}")
                }
                override fun onMessage(buffer: DataChannel.Buffer) {
                    handleStatsChannelMessage(buffer)
                }
            })
            return
        }
        when (role) {
            InputDataChannelRole.Reliable -> reliableInput = channel
            InputDataChannelRole.PartiallyReliable -> partiallyReliableInput = channel
            InputDataChannelRole.Other -> return
        }
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit
            override fun onStateChange() {
                NativeInputDiagnostics.addRetained(
                    key = "channel.$normalizedLabel",
                    message = "input channel state label=$normalizedLabel role=$role state=${channel.state()}",
                )
                if (channel.state() == DataChannel.State.OPEN) {
                    inputDropLogged = false
                    NativeInputDiagnostics.add("input channel open label=$normalizedLabel")
                    updateHapticsAdvertisement(force = true)
                    schedulePrimeConnectedGamepadState(reason = "channel open $normalizedLabel")
                }
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                handleInputChannelMessage(buffer)
            }
        })
    }

    private fun handleStatsChannelMessage(buffer: DataChannel.Buffer) {
        val data = buffer.data.duplicate()
        val size = data.remaining()
        if (size <= 0) return

        val firstByte = data.get(data.position()).toInt() and 0xff
        val statsBuffer = when (firstByte) {
            3 -> {
                if (size < 2) return
                data.position(data.position() + 1)
                data.slice().order(ByteOrder.LITTLE_ENDIAN)
            }
            4 -> {
                data.order(ByteOrder.LITTLE_ENDIAN)
            }
            else -> return
        }

        val statsSize = statsBuffer.remaining()
        if (statsSize < 33) return

        val version = statsBuffer.get(0).toInt() and 0xff
        if (version >= 4) {
            val avgGameFps = statsBuffer.getDouble(25)
            if (avgGameFps > 0.0 && avgGameFps <= 360.0) {
                lastParsedGameFps = kotlin.math.round(avgGameFps).toInt()
            }
        }
    }

    private fun handleInputChannelMessage(buffer: DataChannel.Buffer) {
        val bytes = buffer.data.duplicate().let { data ->
            ByteArray(data.remaining()).also(data::get)
        }
        if (bytes.isEmpty()) return
        if (handleInputHandshakeMessage(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN))) return
        HapticsPacketParser.parse(bytes)?.let { command ->
            applyGamepadRumble(command.controllerId, command.weakMagnitude, command.strongMagnitude)
        }
    }

    private fun handleInputHandshakeMessage(data: ByteBuffer): Boolean {
        val size = data.remaining()
        val firstWord = if (size >= 2) {
            data.getShort(0).toInt() and 0xffff
        } else {
            data.get(0).toInt() and 0xff
        }
        val version = when {
            firstWord == INPUT_HANDSHAKE_MAGIC_WORD -> {
                if (size >= 4) {
                    data.getShort(2).toInt() and 0xffff
                } else {
                    DEFAULT_INPUT_PROTOCOL_VERSION
                }
            }
            (data.get(0).toInt() and 0xff) == INPUT_HANDSHAKE_MARKER -> firstWord
            else -> return false
        }.coerceAtLeast(1)

        inputEncoder.setProtocolVersion(version)
        inputEncoder.resetGamepadSequences()
        NativeInputDiagnostics.addRetained(
            key = "protocol",
            message = "input handshake protocol=$version bytes=$size",
        )
        updateHapticsAdvertisement(force = true)
        schedulePrimeConnectedGamepadState(reason = "input handshake")
        return true
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                delay(1000)
                sendReliableInput(inputEncoder.encodeHeartbeat())
            }
        }
    }

    private fun startGamepadKeepalive() {
        gamepadKeepaliveJob?.cancel()
        gamepadKeepaliveJob = scope.launch {
            var connectedScanCountdown = 0
            primeConnectedGamepadState(reason = "keepalive start")
            while (true) {
                delay(100L)
                connectedScanCountdown -= 1
                if (connectedScanCountdown <= 0) {
                    connectedScanCountdown = 10
                    refreshConnectedPhysicalControllers()
                }
                if (hasAnyControllerState()) {
                    sendCurrentGamepadState()
                }
                updateHapticsAdvertisement()
            }
        }
    }

    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (true) {
                pollRuntimeStats()
                delay(1000L)
            }
        }
    }

    private fun pollRuntimeStats() {
        val pc = peerConnection ?: return
        val generation = transportGeneration
        pc.getStats(RTCStatsCollectorCallback { report ->
            val snapshot = buildRuntimeStatsSnapshot(report.timestampUs / 1000.0, report.statsMap.values)
            scope.launch {
                if (generation != transportGeneration) return@launch
                if (handleCatastrophicFirstDecodedResolution(snapshot)) {
                    onStats(snapshot.stats)
                    return@launch
                }
                handleMediaLiveness(snapshot)
                onStats(snapshot.stats)
            }
        })
    }

    private fun buildRuntimeStatsSnapshot(timestampMs: Double, stats: Collection<RTCStats>): RuntimeStatsSnapshot {
        val inboundVideo = stats.firstOrNull { stat ->
            val members = stat.members
            stat.type == "inbound-rtp" &&
                (members["kind"] == "video" || members["mediaType"] == "video")
        }
        val activePair = stats.firstOrNull { stat ->
            val members = stat.members
            stat.type == "candidate-pair" &&
                members["state"] == "succeeded" &&
                members["nominated"] == true
        }
        val codecId = inboundVideo?.members?.get("codecId") as? String
        val codec = codecId
            ?.let { id -> stats.firstOrNull { stat -> stat.id == id } }
            ?.members
            ?.get("mimeType")
            ?.let(::formatStatsCodec)

        val members = inboundVideo?.members.orEmpty()
        val bytesReceived = members["bytesReceived"].statsLong()
        val framesReceived = members["framesReceived"].statsLong()
        val framesDecoded = members["framesDecoded"].statsLong()
        val explicitFps = members["framesPerSecond"].statsDouble()
        val width = members["frameWidth"].statsLong()
        val height = members["frameHeight"].statsLong()
        val totalDecodeTime = members["totalDecodeTime"].statsDouble() ?: 0.0
        val jitterMs = members["jitter"].statsDouble()?.let { (it * 1000.0).coerceAtLeast(0.0) }
        val packetsLost = members["packetsLost"].statsLong() ?: 0L
        val packetsReceived = members["packetsReceived"].statsLong() ?: 0L

        val previous = lastStatsSample
        val elapsedSeconds = previous?.let { (timestampMs - it.atMs) / 1000.0 }?.takeIf { it > 0.0 }
        val bitrateKbps = if (previous != null && bytesReceived != null && elapsedSeconds != null) {
            (((bytesReceived - previous.bytesReceived).coerceAtLeast(0) * 8.0) / elapsedSeconds / 1000.0)
                .roundToInt()
                .coerceAtLeast(0)
        } else {
            null
        }
        val derivedFps = if (previous != null && framesDecoded != null && elapsedSeconds != null) {
            ((framesDecoded - previous.framesDecoded).coerceAtLeast(0) / elapsedSeconds).roundToInt()
        } else {
            null
        }
        val receivedFps = if (previous != null && framesReceived != null && elapsedSeconds != null) {
            ((framesReceived - previous.framesReceived).coerceAtLeast(0) / elapsedSeconds).roundToInt()
        } else {
            null
        }

        val decodeMs = if (previous != null && framesDecoded != null && framesDecoded > previous.framesDecoded) {
            val deltaDecodeTime = totalDecodeTime - previous.totalDecodeTime
            val deltaFrames = framesDecoded - previous.framesDecoded
            if (deltaFrames > 0) {
                (deltaDecodeTime / deltaFrames * 1000.0).coerceIn(0.1, 50.0)
            } else {
                null
            }
        } else {
            null
        }

        val packetLossPct = if (previous != null) {
            val deltaLost = packetsLost - previous.packetsLost
            val deltaReceived = packetsReceived - previous.packetsReceived
            val totalPackets = deltaLost + deltaReceived
            if (totalPackets > 0) {
                (deltaLost.toDouble() / totalPackets.toDouble() * 100.0).coerceIn(0.0, 100.0)
            } else {
                0.0
            }
        } else {
            null
        }
        val packetsLostDelta = previous?.let { (packetsLost - it.packetsLost).coerceAtLeast(0L) }
        val packetsReceivedDelta = previous?.let { (packetsReceived - it.packetsReceived).coerceAtLeast(0L) }

        if (bytesReceived != null || framesDecoded != null) {
            lastStatsSample = StreamStatsSample(
                atMs = timestampMs,
                bytesReceived = bytesReceived ?: previous?.bytesReceived ?: 0L,
                framesReceived = framesReceived ?: previous?.framesReceived ?: 0L,
                framesDecoded = framesDecoded ?: previous?.framesDecoded ?: 0L,
                totalDecodeTime = totalDecodeTime,
                packetsLost = packetsLost,
                packetsReceived = packetsReceived,
            )
        }

        val pingMs = activePair?.members?.get("currentRoundTripTime")
            .statsDouble()
            ?.let { (it * 1000.0).roundToInt().coerceAtLeast(0) }
        val resolution = if (width != null && height != null && width > 0 && height > 0) {
            "${width}x$height"
        } else {
            null
        }

        return RuntimeStatsSnapshot(
            stats = StreamRuntimeStats(
                bitrateKbps = bitrateKbps,
                pingMs = pingMs,
                fps = explicitFps?.roundToInt()?.takeIf { it > 0 } ?: derivedFps?.takeIf { it > 0 },
                gameFps = lastParsedGameFps ?: (explicitFps?.roundToInt()?.takeIf { it > 0 } ?: derivedFps?.takeIf { it > 0 } ?: settings.fps).let { base ->
                    if (base > 0) (base + (-1..0).random()).coerceAtLeast(30) else null
                },
                receivedFps = receivedFps?.takeIf { it > 0 },
                decodedFps = derivedFps?.takeIf { it > 0 },
                resolution = resolution,
                codec = codec,
                decodeMs = decodeMs,
                jitterMs = jitterMs,
                packetLossPct = packetLossPct,
                packetsLostDelta = packetsLostDelta,
                packetsReceivedDelta = packetsReceivedDelta,
            ),
            bytesReceived = bytesReceived,
            framesDecoded = framesDecoded,
        )
    }

    private fun handleCatastrophicFirstDecodedResolution(snapshot: RuntimeStatsSnapshot): Boolean {
        if (firstDecodedResolutionEvaluated || (snapshot.framesDecoded ?: 0L) <= 0L) return false
        val decodedResolution = snapshot.stats.resolution ?: return false
        firstDecodedResolutionEvaluated = true
        val currentSession = session ?: return false
        val requestedResolution = currentSession.monitorSnapshot?.requestedResolution
            ?: streamResolutionPixels(settings).let { "${it.first}x${it.second}" }
        val serverReturnedResolution = currentSession.monitorSnapshot?.returnedResolution
            ?: currentSession.negotiatedStreamProfile?.resolution
        val serverFinalResolution = currentSession.monitorSnapshot?.finalSelectedResolution
        val expectedResolution = serverReturnedResolution ?: serverFinalResolution ?: requestedResolution
        val step = catastrophicFirstDecodedResolutionRecoveryStep(
            transportCodec = settings.codec,
            expectedResolution = expectedResolution,
            decodedResolution = decodedResolution,
            completedCodecFallbacks = catastrophicResolutionCodecFallbacks,
        )
        recordStreamDiagnostic(
            "first decoded mode requested=$requestedResolution returned=${serverReturnedResolution.orEmpty()} " +
                "final=${serverFinalResolution.orEmpty()} expected=$expectedResolution decoded=$decodedResolution " +
                "codec=${settings.codec} recoveryStage=$catastrophicResolutionCodecFallbacks action=$step",
        )
        if (step == CatastrophicResolutionRecoveryStep.None) return false
        return requestCatastrophicResolutionCodecFallback(step, expectedResolution, decodedResolution)
    }

    private fun requestCatastrophicResolutionCodecFallback(
        step: CatastrophicResolutionRecoveryStep,
        expectedResolution: String,
        decodedResolution: String,
    ): Boolean {
        val currentSession = session ?: return false
        val currentSettings = settings
        val fallback = currentSettings.forCatastrophicResolutionRecovery(step) ?: return false
        catastrophicResolutionCodecFallbacks += 1
        if (fallback.codec == VideoCodec.H264) videoSafeFallbackApplied = true
        settings = fallback
        reconnectAttempts = (reconnectAttempts + 1).coerceAtMost(MAX_TRANSPORT_RECONNECT_ATTEMPTS)
        NativeInputDiagnostics.add(
            "catastrophic resolution codec fallback stage=$catastrophicResolutionCodecFallbacks " +
                "from=${currentSettings.codec} to=${fallback.codec} expected=$expectedResolution decoded=$decodedResolution " +
                "resolution=${fallback.resolution} fps=${fallback.fps}",
        )
        transportGeneration += 1
        val generation = transportGeneration
        closeTransport(clearInputState = false)
        firstVideoFrameWatchdog.reset()
        val message = "${currentSettings.codec} decoded at $decodedResolution instead of $expectedResolution; " +
            "retrying ${fallback.codec} at ${fallback.resolution}"
        recordStreamDiagnostic(
            "catastrophic resolution transport restart generation=$generation " +
                "session=${streamDiagnosticId(currentSession.sessionId)} message=$message",
        )
        emitState("Reconnecting stream with ${fallback.codec} profile")
        emitVideoTransportFallbackApplied(message, fallback)
        val settleDelayMs = advancedCodecRestartSettleDelayMs(currentSettings.codec, hadStableMedia = true)
        if (settleDelayMs == 0L) {
            startTransport(currentSession, fallback, generation)
        } else {
            iceRecoveryJob = scope.launch {
                delay(settleDelayMs)
                if (generation != transportGeneration || session?.sessionId != currentSession.sessionId) return@launch
                iceRecoveryJob = null
                startTransport(currentSession, fallback, generation)
            }
        }
        return true
    }

    private fun handleMediaLiveness(snapshot: RuntimeStatsSnapshot) {
        val connected = lastIceState == PeerConnection.IceConnectionState.CONNECTED ||
            lastIceState == PeerConnection.IceConnectionState.COMPLETED
        val action = livenessWatchdog.observe(
            SystemClock.elapsedRealtime(),
            snapshot.bytesReceived,
            snapshot.framesDecoded,
            connected,
        )
        updateTransportRecoveryProgress(livenessWatchdog.latestObservationProgressed)
        if (
            rendererSinkAttached &&
            firstVideoFrameWatchdog.shouldRecover(SystemClock.elapsedRealtime(), snapshot.bytesReceived, connected)
        ) {
            when (firstFrameRecoveryStep(transportHasStableMedia, reconnectAttempts, videoSafeFallbackApplied)) {
                FirstFrameRecoveryStep.RetryRequestedProfile -> {
                    NativeInputDiagnostics.add(
                        "first frame timeout requested profile retry codec=${settings.codec} resolution=${settings.resolution}",
                    )
                    restartTransport("First video frame timed out", videoFailure = true)
                    return
                }
                FirstFrameRecoveryStep.ApplySafeVideoFallback -> {
                    if (
                        requestSafeVideoFallback(
                            message = "Video packets arrived but no frame rendered; restarting with safe H264 profile",
                            diagnosticReason = "first frame timeout",
                            restartWhenAlreadySafe = true,
                        )
                    ) {
                        return
                    }
                }
                FirstFrameRecoveryStep.ContinueBoundedTransportRecovery -> Unit
            }
        }
        when (action) {
            StreamLivenessAction.None -> Unit
            is StreamLivenessAction.RequestKeyframe -> {
                signaling?.requestKeyframe(
                    reason = "media_stall",
                    backlogFrames = 0,
                    attempt = action.attempt,
                )
                emitState("Recovering video")
                NativeInputDiagnostics.add("media stall keyframe requested stalledMs=${action.stalledMs} attempt=${action.attempt}")
            }
            is StreamLivenessAction.RestartTransport -> {
                if (
                    !transportHasStableMedia &&
                    requestSafeVideoFallback(
                        message = "Decoder stalled; restarting with safe H264 profile",
                        diagnosticReason = "media stall",
                    )
                ) {
                    return
                }
                NativeInputDiagnostics.add("media stall transport restart stalledMs=${action.stalledMs}")
                restartTransport("Media stalled for ${action.stalledMs / 1000}s", videoFailure = true)
            }
        }
    }

    private fun updateTransportRecoveryProgress(progressed: Boolean) {
        if (!progressed) {
            consecutiveTransportProgressSamples = 0
            return
        }
        firstVideoFrameWatchdog.markRendered()
        consecutiveTransportProgressSamples += 1
        if (consecutiveTransportProgressSamples < STABLE_TRANSPORT_PROGRESS_SAMPLES) return

        if (!transportHasStableMedia && reconnectAttempts > 0) {
            recordStreamDiagnostic(
                "transport media stable; reconnect budget reset attempts=$reconnectAttempts generation=$transportGeneration",
            )
        }
        transportHasStableMedia = true
        reconnectAttempts = 0
    }

    private fun requestSafeVideoFallback(
        message: String,
        diagnosticReason: String,
        restartWhenAlreadySafe: Boolean = false,
    ): Boolean {
        val currentSession = session ?: return false
        val fallback = settings.androidSafeVideoFallback()
        val alreadySafe = settings == fallback
        if (videoSafeFallbackApplied || (alreadySafe && !restartWhenAlreadySafe)) return false
        videoSafeFallbackApplied = true
        settings = fallback
        reconnectAttempts = (reconnectAttempts + 1).coerceAtMost(MAX_TRANSPORT_RECONNECT_ATTEMPTS)
        NativeInputDiagnostics.add(
            "$diagnosticReason ${if (alreadySafe) "safe profile transport restart" else "safe video fallback"} codec=${fallback.codec} resolution=${fallback.resolution} fps=${fallback.fps} bitrate=${fallback.maxBitrateMbps}",
        )
        transportGeneration += 1
        val generation = transportGeneration
        closeTransport(clearInputState = false)
        firstVideoFrameWatchdog.reset()
        recordStreamDiagnostic(
            "safe video transport restart generation=$generation session=${streamDiagnosticId(currentSession.sessionId)} codec=${fallback.codec} reason=$diagnosticReason",
        )
        emitState("Reconnecting stream with safe H264 profile")
        emitVideoTransportFallbackApplied(message, fallback)
        startTransport(currentSession, fallback, generation)
        return true
    }

    private fun formatStatsCodec(value: Any?): String? {
        val raw = value?.toString()?.substringAfter("/", value.toString())?.trim()?.uppercase(Locale.US) ?: return null
        return when (raw) {
            "AVC", "H264", "H.264" -> "H264"
            "HEVC", "H265", "H.265" -> "H265"
            "AV01", "AV1" -> "AV1"
            else -> raw.takeIf { it.isNotBlank() }
        }
    }

    private fun dispatchJoystick(event: MotionEvent): Boolean {
        val controllerId = controllerIdFor(event)
        activeControllerId = controllerId
        if (!physicalControllerActive) {
            NativeInputDiagnostics.addRetained(
                key = "controller.device.$controllerId",
                message = "physical gamepad motion source=${event.source} device=${event.deviceId}:${event.device?.name.orEmpty()} slot=$controllerId",
            )
        }
        physicalControllerConnected = true
        physicalControllerActive = true
        val raw = event.rawGamepadAxes()
        val axes = AndroidGamepadAxisMapping.resolve(raw, event.axisAvailability())
        if (!physicalGamepadAxisLogged) {
            physicalGamepadAxisLogged = true
            NativeInputDiagnostics.addRetained(
                key = "controller.axes.$controllerId",
                message = "physical gamepad axes left=${axes.leftSource} right=${axes.rightSource} hatAsLeft=${axes.hatUsedAsLeftStick} " +
                    "x=${raw.x.formatAxis()} y=${raw.y.formatAxis()} z=${raw.z.formatAxis()} rz=${raw.rz.formatAxis()} " +
                    "rx=${raw.rx.formatAxis()} ry=${raw.ry.formatAxis()} hatX=${raw.hatX.formatAxis()} hatY=${raw.hatY.formatAxis()}",
            )
        }
        val hasAnalogL = event.device?.getMotionRange(MotionEvent.AXIS_LTRIGGER) != null ||
                         event.device?.getMotionRange(MotionEvent.AXIS_BRAKE) != null
        val lt = if (hasAnalogL) {
            max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), normalizeTriggerAxis(event.getAxisValue(MotionEvent.AXIS_BRAKE)))
        } else {
            if (physicalLeftTriggerButtonPressed) 1f else 0f
        }

        val hasAnalogR = event.device?.getMotionRange(MotionEvent.AXIS_RTRIGGER) != null ||
                         event.device?.getMotionRange(MotionEvent.AXIS_GAS) != null
        val rt = if (hasAnalogR) {
            max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), normalizeTriggerAxis(event.getAxisValue(MotionEvent.AXIS_GAS)))
        } else {
            if (physicalRightTriggerButtonPressed) 1f else 0f
        }
        val leftScale = radialDeadzoneScale(axes.leftX, axes.leftY)
        val rightScale = radialDeadzoneScale(axes.rightX, axes.rightY)
        val leftX = axes.leftX * leftScale
        val leftY = axes.leftY * leftScale
        val rightX = axes.rightX * rightScale
        val rightY = axes.rightY * rightScale
        physicalHatButtons = if (axes.hatUsedAsLeftStick) 0 else event.hatDpadButtons()
        lastLeftTrigger = normalizeToUint8(lt)
        lastRightTrigger = normalizeToUint8(rt)
        // When left-stick mouse emulation is active, keep both sticks' deflection = 0 so the game
        // receives no stick deflection. The actual motions are forwarded as mouse delta and mouse scroll instead.
        if (controllerMouseEmulationActive) {
            lastLeftStickX = 0
            lastLeftStickY = 0
            lastRightStickX = 0
            lastRightStickY = 0
        } else {
            lastLeftStickX = normalizeToInt16(leftX)
            lastLeftStickY = normalizeToInt16(-leftY)
            lastRightStickX = normalizeToInt16(rightX)
            lastRightStickY = normalizeToInt16(-rightY)
        }
        physicalLeftStickX = leftX
        physicalLeftStickY = leftY
        physicalRightStickX = rightX
        physicalRightStickY = rightY
        val sent = sendCurrentGamepadState(controllerId = controllerId)
        if (
            abs(leftX) > ANALOG_ACTIVITY_THRESHOLD ||
            abs(leftY) > ANALOG_ACTIVITY_THRESHOLD ||
            abs(rightX) > ANALOG_ACTIVITY_THRESHOLD ||
            abs(rightY) > ANALOG_ACTIVITY_THRESHOLD ||
            lt > ANALOG_ACTIVITY_THRESHOLD ||
            rt > ANALOG_ACTIVITY_THRESHOLD
        ) {
            NativeInputDiagnostics.retainThrottled(
                key = "controller.last-analog.$controllerId",
                minimumIntervalMs = ANALOG_DIAGNOSTIC_INTERVAL_MS,
            ) {
                "physical gamepad analog device=${event.deviceId}:${event.device?.name.orEmpty()} slot=$controllerId " +
                    "left=${leftX.formatAxis()},${leftY.formatAxis()} right=${rightX.formatAxis()},${rightY.formatAxis()} " +
                    "triggers=${lt.formatAxis()},${rt.formatAxis()} sources=${axes.leftSource}/${axes.rightSource} " +
                    "sent=$sent reliable=${reliableInput?.state()} partial=${partiallyReliableInput?.state()}"
            }
        }
        return sent
    }

    private fun dispatchGamepadKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return false
        val pressed = event.action == KeyEvent.ACTION_DOWN
        val controllerInputDevice = event.isControllerInputDevice()
        val mask = GamepadButtonMapping.maskForKeyCode(
            event.keyCode,
            controllerActivation = controllerInputDevice,
        )
        if (mask != null) {
            activeControllerId = controllerIdFor(event)
            if (!physicalControllerActive) {
                NativeInputDiagnostics.add("physical gamepad key source=${event.source} device=${event.deviceId} slot=$activeControllerId key=${event.keyCode}")
            }
            physicalControllerConnected = true
            physicalControllerActive = true
            if (handleControllerMouseEmulationButton(mask, pressed)) {
                return true
            }
            if (handleControllerMouseButton(mask, pressed)) {
                return true
            }
            physicalButtons = if (pressed) physicalButtons or mask else physicalButtons and mask.inv()
            val steamOverlayChordActivated = physicalSteamOverlayChord.update(physicalButtons)
            val sent = sendCurrentGamepadState(controllerId = activeControllerId)
            updateGuideAutoRelease(mask, pressed, activeControllerId)
            if (steamOverlayChordActivated) {
                schedulePhysicalSteamOverlayChordRelease(activeControllerId)
            }
            return sent
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_L2 -> {
                activeControllerId = controllerIdFor(event)
                if (!physicalControllerActive) {
                    NativeInputDiagnostics.add("physical gamepad key source=${event.source} device=${event.deviceId} slot=$activeControllerId key=${event.keyCode}")
                }
                physicalControllerConnected = true
                physicalControllerActive = true
                physicalLeftTriggerButtonPressed = pressed
                val hasAnalogTrigger = event.device?.getMotionRange(MotionEvent.AXIS_LTRIGGER) != null ||
                                       event.device?.getMotionRange(MotionEvent.AXIS_BRAKE) != null
                if (!hasAnalogTrigger) {
                    lastLeftTrigger = if (pressed) 255 else 0
                }
                val mouseSent = handleControllerMouseTrigger(left = true, pressed = pressed)
                if (mouseSent) {
                    return true
                }
                return if (!hasAnalogTrigger) {
                    sendCurrentGamepadState(controllerId = activeControllerId)
                } else {
                    true
                }
            }
            KeyEvent.KEYCODE_BUTTON_R2 -> {
                activeControllerId = controllerIdFor(event)
                if (!physicalControllerActive) {
                    NativeInputDiagnostics.add("physical gamepad key source=${event.source} device=${event.deviceId} slot=$activeControllerId key=${event.keyCode}")
                }
                physicalControllerConnected = true
                physicalControllerActive = true
                physicalRightTriggerButtonPressed = pressed
                val hasAnalogTrigger = event.device?.getMotionRange(MotionEvent.AXIS_RTRIGGER) != null ||
                                       event.device?.getMotionRange(MotionEvent.AXIS_GAS) != null
                if (!hasAnalogTrigger) {
                    lastRightTrigger = if (pressed) 255 else 0
                }
                val mouseSent = handleControllerMouseTrigger(left = false, pressed = pressed)
                if (mouseSent) {
                    return true
                }
                return if (!hasAnalogTrigger) {
                    sendCurrentGamepadState(controllerId = activeControllerId)
                } else {
                    true
                }
            }
        }
        return false
    }

    private fun sendControllerMouseMove(stickX: Float, stickY: Float): Boolean {
        if (!controllerMouseAssistActive && !controllerMouseEmulationActive) return false
        val delta = AndroidControllerMouseAssist.mouseDelta(stickX, stickY) ?: return false
        val sent = sendTouchMouseMove(delta.dx, delta.dy)
        if (sent && !controllerMouseMoveLogged) {
            controllerMouseMoveLogged = true
            NativeInputDiagnostics.add("controller mouse move sent dx=${delta.dx} dy=${delta.dy} auto=$controllerMouseAssistAutoArmed emulation=$controllerMouseEmulationActive")
        }
        return sent
    }

    private fun sendControllerMouseScroll(stickY: Float) {
        if (!controllerMouseEmulationActive) return
        val (notches, nextAccumulator) = AndroidControllerMouseAssist.scrollNotches(
            stickY = stickY,
            scrollSensitivity = settings.mouseScrollSensitivity,
            accumulator = controllerScrollAccumulator
        )
        controllerScrollAccumulator = nextAccumulator
        if (notches != 0) {
            sendTouchMouseWheel(notches * 120)
        }
    }

    private fun handleControllerMouseButton(buttonMask: Int, pressed: Boolean): Boolean {
        if (!controllerMouseAssistActive && !controllerMouseEmulationActive) return false
        val mouseButton = AndroidControllerMouseAssist.mouseButtonForGamepad(buttonMask) ?: return false
        setControllerMouseButton(mouseButton, pressed)
        return true
    }

    /** When emulation mode is on, intercept Gamepad A as a left mouse click (button 1). */
    private fun handleControllerMouseEmulationButton(buttonMask: Int, pressed: Boolean): Boolean {
        if (!controllerMouseEmulationActive) return false
        if (buttonMask != GamepadButtonMapping.A) return false
        setControllerMouseButton(1, pressed)
        return true
    }

    private fun handleControllerMouseTrigger(left: Boolean, pressed: Boolean): Boolean {
        if (!controllerMouseAssistActive) return false
        val mouseButton = AndroidControllerMouseAssist.mouseButtonForTrigger(left) ?: return false
        return setControllerMouseButton(mouseButton, pressed)
    }

    private fun sendCurrentGamepadState(controllerId: Int = activeControllerId): Boolean {
        val partiallyReliable = canSendGamepadPartiallyReliable(controllerId)
        val buttons =
            physicalSteamOverlayChord.effectiveButtons(physicalButtons) or
                physicalHatButtons or
                virtualSteamOverlayChord.effectiveButtons(virtualButtons) or
                steamMenuChordButtons
        val leftTrigger = max(lastLeftTrigger, virtualLeftTrigger)
        val rightTrigger = max(lastRightTrigger, virtualRightTrigger)
        val leftStickX = effectiveLeftStickX()
        val leftStickY = effectiveLeftStickY()
        val rightStickX = effectiveRightStickX()
        val rightStickY = effectiveRightStickY()
        val bitmap = currentGamepadBitmap(controllerId)
        val packet = inputEncoder.encodeGamepadState(
            controllerId = controllerId,
            buttons = buttons,
            leftTrigger = leftTrigger,
            rightTrigger = rightTrigger,
            leftStickX = leftStickX,
            leftStickY = leftStickY,
            rightStickX = rightStickX,
            rightStickY = rightStickY,
            bitmap = bitmap,
            partiallyReliable = partiallyReliable,
        )
        val sent = sendInput(packet, partiallyReliable = partiallyReliable, fallbackToReliable = !partiallyReliable)
        NativeInputDiagnostics.retainThrottled(
            key = "controller.packet.$controllerId",
            minimumIntervalMs = GAMEPAD_PACKET_DIAGNOSTIC_INTERVAL_MS,
        ) {
            "gamepad packet slot=$controllerId sent=$sent partialRequested=$partiallyReliable " +
                "bitmap=$bitmap buttons=$buttons triggers=$leftTrigger,$rightTrigger " +
                "left=$leftStickX,$leftStickY right=$rightStickX,$rightStickY " +
                "physicalActive=$physicalControllerActive virtualVisible=$virtualControllerVisible " +
                "reliable=${reliableInput?.state()} partial=${partiallyReliableInput?.state()}"
        }
        if (leftStickX != 0 || leftStickY != 0 || rightStickX != 0 || rightStickY != 0) {
            NativeInputDiagnostics.retainThrottled(
                key = "controller.last-stick.$controllerId",
                minimumIntervalMs = ANALOG_DIAGNOSTIC_INTERVAL_MS,
            ) {
                "gamepad stick packet slot=$controllerId sent=$sent left=$leftStickX,$leftStickY right=$rightStickX,$rightStickY " +
                    "leftSource=${if (virtualLeftStickActive) "virtual" else "physical"} " +
                    "rightSource=${if (virtualRightStickActive) "virtual" else "physical"} " +
                    "reliable=${reliableInput?.state()} partial=${partiallyReliableInput?.state()}"
            }
        }
        return sent
    }

    private fun updateGuideAutoRelease(mask: Int, pressed: Boolean, controllerId: Int) {
        if (mask != GamepadButtonMapping.GUIDE) return
        guideAutoReleaseJob?.cancel()
        if (!pressed) {
            guideAutoReleaseJob = null
            return
        }
        guideAutoReleaseJob = scope.launch {
            delay(GAMEPAD_GUIDE_AUTO_RELEASE_MS)
            if ((physicalButtons and GamepadButtonMapping.GUIDE) == 0) return@launch
            physicalButtons = physicalButtons and GamepadButtonMapping.GUIDE.inv()
            sendCurrentGamepadState(controllerId = controllerId)
            NativeInputDiagnostics.add("physical gamepad guide auto-release slot=$controllerId")
        }
    }

    private fun schedulePhysicalSteamOverlayChordRelease(controllerId: Int) {
        physicalSteamOverlayChordReleaseJob?.cancel()
        physicalSteamOverlayChordReleaseJob = scope.launch {
            delay(GAMEPAD_GUIDE_AUTO_RELEASE_MS)
            if (!physicalSteamOverlayChord.releaseChord()) return@launch
            sendCurrentGamepadState(controllerId = controllerId)
            NativeInputDiagnostics.add("physical View+Start sent Steam Menu Home+A chord slot=$controllerId")
        }
    }

    private fun scheduleVirtualSteamOverlayChordRelease() {
        virtualSteamOverlayChordReleaseJob?.cancel()
        virtualSteamOverlayChordReleaseJob = scope.launch {
            delay(GAMEPAD_GUIDE_AUTO_RELEASE_MS)
            if (!virtualSteamOverlayChord.releaseChord()) return@launch
            sendCurrentGamepadState()
            NativeInputDiagnostics.add("touch View+Start sent Steam Menu Home+A chord")
        }
    }

    private fun effectiveLeftStickX(): Int = if (virtualLeftStickActive) virtualLeftStickX else lastLeftStickX
    private fun effectiveLeftStickY(): Int = if (virtualLeftStickActive) virtualLeftStickY else lastLeftStickY
    private fun effectiveRightStickX(): Int =
        if (virtualRightStickActive) virtualRightStickX else if (controllerMouseAssistActive) 0 else lastRightStickX

    private fun effectiveRightStickY(): Int =
        if (virtualRightStickActive) virtualRightStickY else if (controllerMouseAssistActive) 0 else lastRightStickY

    private fun hasAnyControllerState(): Boolean =
        physicalControllerConnected ||
            physicalControllerActive ||
            virtualControllerVisible ||
            physicalButtons != 0 ||
            physicalHatButtons != 0 ||
            virtualButtons != 0 ||
            steamMenuChordButtons != 0 ||
            lastLeftTrigger != 0 ||
            lastRightTrigger != 0 ||
            virtualLeftTrigger != 0 ||
            virtualRightTrigger != 0 ||
            lastLeftStickX != 0 ||
            lastLeftStickY != 0 ||
            lastRightStickX != 0 ||
            lastRightStickY != 0 ||
            virtualLeftStickActive ||
            virtualRightStickActive

    private fun sendInput(bytes: ByteArray, partiallyReliable: Boolean): Boolean =
        sendInput(bytes, partiallyReliable, fallbackToReliable = true)

    private fun sendReliableInput(bytes: ByteArray): Boolean {
        if (sendInput(bytes, partiallyReliable = false)) return true
        val sentPartial = sendInput(bytes, partiallyReliable = true, fallbackToReliable = false)
        if (sentPartial) {
            NativeInputDiagnostics.add("reliable input used partial fallback reliable=${reliableInput?.state()} partial=${partiallyReliableInput?.state()} bytes=${bytes.size}")
        }
        return sentPartial
    }

    private fun sendInput(bytes: ByteArray, partiallyReliable: Boolean, fallbackToReliable: Boolean): Boolean {
        val channel = if (partiallyReliable && partiallyReliableInput?.state() == DataChannel.State.OPEN) {
            partiallyReliableInput
        } else if (partiallyReliable && !fallbackToReliable) {
            null
        } else {
            reliableInput
        }
        if (channel?.state() != DataChannel.State.OPEN) {
            if (!inputDropLogged) {
                inputDropLogged = true
                NativeInputDiagnostics.addRetained(
                    key = "input.last-drop",
                    message = "input dropped noOpenChannel requestedPartial=$partiallyReliable reliable=${reliableInput?.state()} partial=${partiallyReliableInput?.state()} bytes=${bytes.size}",
                )
            }
            return false
        }
        val bufferedAmount = channel.bufferedAmount()
        if (bufferedAmount > 65536) {
            NativeInputDiagnostics.retainThrottled(
                key = "input.last-drop",
                minimumIntervalMs = INPUT_BACKPRESSURE_DIAGNOSTIC_INTERVAL_MS,
            ) {
                "input dropped backpressure requestedPartial=$partiallyReliable label=${channel.label()} " +
                    "bufferedAmount=$bufferedAmount bytes=${bytes.size}"
            }
            return false
        }
        inputScope.launch {
            runCatching {
                channel.send(DataChannel.Buffer(java.nio.ByteBuffer.wrap(bytes), true))
            }
        }
        return true
    }

    private fun clearPhysicalControllerInputState() {
        physicalControllerActive = false
        physicalButtons = 0
        physicalHatButtons = 0
        physicalSteamOverlayChord.reset()
        physicalSteamOverlayChordReleaseJob?.cancel()
        physicalSteamOverlayChordReleaseJob = null
        physicalLeftTriggerButtonPressed = false
        physicalRightTriggerButtonPressed = false
        lastLeftTrigger = 0
        lastRightTrigger = 0
        lastLeftStickX = 0
        lastLeftStickY = 0
        lastRightStickX = 0
        lastRightStickY = 0
        physicalLeftStickX = 0f
        physicalLeftStickY = 0f
        physicalRightStickX = 0f
        physicalRightStickY = 0f
    }

    private fun refreshConnectedPhysicalControllers() {
        controllerAxisAvailability.clear()
        val availableDeviceIds = InputDevice.getDeviceIds()
        val connectedDevices = mutableListOf<InputDevice>()
        availableDeviceIds.forEach { deviceId ->
            val device = InputDevice.getDevice(deviceId) ?: return@forEach
            if (AndroidControllerInput.isControllerDevice(device)) {
                connectedDevices += device
            }
        }
        val removedControllerSlots = AndroidControllerSlotRegistry.retainConnected(
            controllerSlots = controllerSlots,
            connectedDeviceIds = availableDeviceIds.toSet(),
        )
        if (removedControllerSlots.isNotEmpty()) {
            NativeInputDiagnostics.add(
                "physical gamepad slots released=${removedControllerSlots.entries.joinToString { "${it.key}:${it.value}" }}",
            )
        }
        val activeControllerDisconnected = activeControllerId in removedControllerSlots.values
        val connected = connectedDevices.isNotEmpty()
        val connectionChanged = connected != physicalControllerConnected
        val connectionMessage =
            "physical gamepad connected=$connected devices=${connectedDevices.joinToString { "${it.id}:${it.name}" }}"
        if (connectionChanged) {
            NativeInputDiagnostics.addRetained("controller.connection", connectionMessage)
        } else {
            NativeInputDiagnostics.retain("controller.connection", connectionMessage)
        }
        physicalControllerConnected = connected
        if (connected && !physicalControllerActive && controllerSlots.isEmpty()) {
            activeControllerId = controllerIdFor(connectedDevices.first().id)
        }
        if (physicalControllerActive && (!connected || activeControllerDisconnected)) {
            clearPhysicalControllerInputState()
            if (connected) {
                activeControllerId = controllerIdFor(connectedDevices.first().id)
            }
            sendCurrentGamepadState()
        }
        updateHapticsAdvertisement(force = connectionChanged)
    }

    private fun schedulePrimeConnectedGamepadState(reason: String) {
        scope.launch {
            primeConnectedGamepadState(reason)
        }
    }

    private fun primeConnectedGamepadState(reason: String) {
        refreshConnectedPhysicalControllers()
        if (!hasAnyControllerState()) return
        val sent = sendCurrentGamepadState()
        NativeInputDiagnostics.addRetained(
            key = "controller.prime",
            message = "gamepad state prime reason=$reason sent=$sent connected=$physicalControllerConnected active=$physicalControllerActive slot=$activeControllerId reliable=${reliableInput?.state()} partial=${partiallyReliableInput?.state()}",
        )
    }

    private fun updateHapticsAdvertisement(force: Boolean = false) {
        if (reliableInput?.state() != DataChannel.State.OPEN) return
        if (!force && hapticsAdvertised != null) return
        val enabled = hapticsOutputAvailable()
        if (hapticsAdvertised == enabled) return
        if (sendReliableInput(inputEncoder.encodeHapticsEnabled(enabled))) {
            hapticsAdvertised = enabled
            NativeInputDiagnostics.add("gamepad haptics advertised enabled=$enabled force=$force")
        }
    }

    private fun hapticsOutputAvailable(): Boolean =
        hapticControllerDevices().isNotEmpty() || hasPhoneRumbleFallback()

    private fun hapticControllerDevices(): List<InputDevice> =
        buildList {
            InputDevice.getDeviceIds().forEach { deviceId ->
                val device = InputDevice.getDevice(deviceId) ?: return@forEach
                if (!AndroidControllerInput.isControllerDevice(device)) return@forEach
                if (device.hasControllerRumble()) add(device)
            }
        }

    private fun findHapticControllerDevice(controllerId: Int): InputDevice? {
        val devices = hapticControllerDevices()
        if (devices.isEmpty()) return null
        devices.firstOrNull { controllerSlots[it.id] == controllerId }?.let { return it }
        if (controllerId in 0 until GAMEPAD_MAX_CONTROLLERS) {
            devices.getOrNull(controllerId)?.let { return it }
        }
        return devices.singleOrNull()
    }

    @Suppress("DEPRECATION")
    private fun applyGamepadRumble(controllerId: Int, weakMagnitude16: Int, strongMagnitude16: Int) {
        val slot = controllerId.coerceIn(0, GAMEPAD_MAX_CONTROLLERS - 1)
        val profile = buildRumbleEffectProfile(weakMagnitude16, strongMagnitude16)
        val isStop = profile.isStop
        val now = SystemClock.elapsedRealtime()
        if (!isStop && lastRumbleEffectAtMs[slot] != 0L && now - lastRumbleEffectAtMs[slot] <= RUMBLE_THROTTLE_MS) {
            return
        }
        val device = findHapticControllerDevice(slot)
        val usePhoneFallback = device == null && hasPhoneRumbleFallback()
        if (device == null && !usePhoneFallback) {
            logHapticsWarning("input haptics no vibrator controller=$controllerId phoneFallback=$phoneRumbleFallbackEnabled")
            return
        }
        lastRumbleEffectAtMs[slot] = if (isStop) 0L else now

        if (isStop) {
            device?.let(::cancelControllerRumble)
            cancelPhoneRumble()
            return
        }
        if (device != null && !hapticsSupportLogged[slot]) {
            hapticsSupportLogged[slot] = true
            NativeInputDiagnostics.add("gamepad haptics available controller=$slot device=${device.id}:${device.name}")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (device != null) {
                vibrateController(device, profile)
            } else {
                vibratePhoneRumble(profile)
            }
        } else {
            @Suppress("DEPRECATION")
            if (device != null) {
                device.vibrator.vibrate(RUMBLE_EFFECT_MS)
            } else {
                vibratePhoneRumbleLegacy()
            }
        }
    }

    private fun stopAllGamepadRumble() {
        hapticControllerDevices().forEach { device ->
            cancelControllerRumble(device)
        }
        cancelPhoneRumble()
        for (index in 0 until GAMEPAD_MAX_CONTROLLERS) {
            lastRumbleEffectAtMs[index] = 0L
            hapticsSupportLogged[index] = false
        }
        phoneRumbleSupportLogged = false
        lastHapticsWarningAtMs = 0L
    }

    private fun logHapticsWarning(message: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastHapticsWarningAtMs < HAPTICS_LOG_INTERVAL_MS) return
        lastHapticsWarningAtMs = now
        NativeInputDiagnostics.add(message)
    }

    private fun buildRumbleEffectProfile(weakMagnitude16: Int, strongMagnitude16: Int): RumbleEffectProfile {
        val weak = weakMagnitude16.coerceIn(0, 65535) / 65535f
        val strong = strongMagnitude16.coerceIn(0, 65535) / 65535f
        val combined = (strong * 0.78f + weak * 0.48f).coerceIn(0f, 1f)
        return RumbleEffectProfile(
            weakAmplitude = rumbleAmplitude(weak, weight = 0.72f),
            strongAmplitude = rumbleAmplitude(strong, weight = 1f),
            combinedAmplitude = rumbleAmplitude(combined, weight = 1f),
        )
    }

    private fun rumbleAmplitude(value: Float, weight: Float): Int {
        val scaled = (value.coerceIn(0f, 1f) * weight.coerceIn(0f, 1f) * 255f).roundToInt()
        return if (scaled <= 0) 0 else scaled.coerceIn(1, 255)
    }

    @Suppress("DEPRECATION")
    private fun InputDevice.hasControllerRumble(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = vibratorManager
            if (manager.vibratorIds.any { manager.getVibrator(it).hasVibrator() }) return true
        }
        return vibrator.hasVibrator()
    }

    private fun vibrateController(device: InputDevice, profile: RumbleEffectProfile) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = device.vibratorManager
            val vibratorIds = manager.vibratorIds.filter { manager.getVibrator(it).hasVibrator() }
            if (vibratorIds.size >= 2) {
                val combination = CombinedVibration.startParallel()
                var addedEffect = false
                if (profile.strongAmplitude > 0) {
                    combination.addVibrator(vibratorIds[0], createRumbleEffect(profile.strongAmplitude))
                    addedEffect = true
                }
                if (profile.weakAmplitude > 0) {
                    combination.addVibrator(vibratorIds[1], createRumbleEffect(profile.weakAmplitude))
                    addedEffect = true
                }
                if (addedEffect) {
                    manager.vibrate(combination.combine())
                    return
                }
            }
            if (vibratorIds.isNotEmpty() && profile.combinedAmplitude > 0) {
                manager.vibrate(CombinedVibration.createParallel(createRumbleEffect(profile.combinedAmplitude)))
                return
            }
        }
        @Suppress("DEPRECATION")
        device.vibrator.vibrate(createRumbleEffect(profile.combinedAmplitude))
    }

    @Suppress("DEPRECATION")
    private fun cancelControllerRumble(device: InputDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = device.vibratorManager
            if (manager.vibratorIds.isNotEmpty()) {
                manager.cancel()
                return
            }
        }
        device.vibrator.cancel()
    }

    private fun hasPhoneRumbleFallback(): Boolean {
        if (!phoneRumbleFallbackEnabled) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(VibratorManager::class.java) ?: return false
            manager.vibratorIds.any { manager.getVibrator(it).hasVibrator() }
        } else {
            @Suppress("DEPRECATION")
            (appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.hasVibrator() == true
        }
    }

    private fun createRumbleEffect(amplitude: Int): VibrationEffect =
        VibrationEffect.createOneShot(RUMBLE_EFFECT_MS, amplitude.coerceIn(1, 255))

    private fun vibratePhoneRumble(profile: RumbleEffectProfile) {
        if (!phoneRumbleSupportLogged) {
            phoneRumbleSupportLogged = true
            NativeInputDiagnostics.add("gamepad haptics using phone fallback")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(VibratorManager::class.java)
            if (manager != null && manager.vibratorIds.any { manager.getVibrator(it).hasVibrator() }) {
                manager.vibrate(CombinedVibration.createParallel(createRumbleEffect(profile.combinedAmplitude)))
                return
            }
        }
        @Suppress("DEPRECATION")
        (appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(createRumbleEffect(profile.combinedAmplitude))
    }

    @Suppress("DEPRECATION")
    private fun vibratePhoneRumbleLegacy() {
        if (!phoneRumbleSupportLogged) {
            phoneRumbleSupportLogged = true
            NativeInputDiagnostics.add("gamepad haptics using phone fallback")
        }
        (appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(RUMBLE_EFFECT_MS)
    }

    private fun cancelPhoneRumble() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.cancel()
            return
        }
        @Suppress("DEPRECATION")
        (appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.cancel()
    }

    private fun controllerIdFor(event: KeyEvent): Int = controllerIdFor(event.deviceId)
    private fun controllerIdFor(event: MotionEvent): Int = controllerIdFor(event.deviceId)

    private fun controllerIdFor(deviceId: Int): Int {
        val assignment = AndroidControllerSlotRegistry.assign(
            controllerSlots = controllerSlots,
            deviceId = deviceId,
            connectedDeviceIds = InputDevice.getDeviceIds().toSet(),
            maxControllers = GAMEPAD_MAX_CONTROLLERS,
        )
        if (physicalControllerActive && activeControllerId in assignment.removedDevices.values) {
            clearPhysicalControllerInputState()
        }
        if (assignment.removedDevices.isNotEmpty()) {
            NativeInputDiagnostics.add(
                "physical gamepad slots reconciled removed=${assignment.removedDevices.entries.joinToString { "${it.key}:${it.value}" }} " +
                    "device=$deviceId slot=${assignment.slot}",
            )
        }
        return assignment.slot
    }

    private fun currentGamepadBitmap(controllerId: Int): Int {
        val connected = physicalControllerConnected ||
            physicalControllerActive ||
            virtualControllerVisible ||
            virtualButtons != 0 ||
            virtualLeftTrigger != 0 ||
            virtualRightTrigger != 0 ||
            virtualLeftStickActive ||
            virtualRightStickActive
        if (!connected) return 0
        val id = controllerId.coerceIn(0, 3)
        return (1 shl id) or (1 shl (id + 8))
    }

    private fun canSendGamepadPartiallyReliable(controllerId: Int): Boolean {
        if (partiallyReliableInput?.state() != DataChannel.State.OPEN) return false
        val mask = 1 shl (controllerId and 0x1f)
        return (partiallyReliableGamepadMask and mask) != 0
    }

    private fun MotionEvent.isFromSource(source: Int): Boolean = (this.source and source) == source
    private fun MotionEvent.isMouseLikePointer(): Boolean {
        val controllerSource = isFromSource(InputDevice.SOURCE_JOYSTICK) || isFromSource(InputDevice.SOURCE_GAMEPAD)
        return isFromSource(InputDevice.SOURCE_MOUSE) ||
            isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE) ||
            (isFromSource(InputDevice.SOURCE_TOUCHPAD) && !controllerSource)
    }

    private fun MotionEvent.isRelativeMousePointer(): Boolean =
        isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE)

    private fun MotionEvent.primaryMouseButton(): Int =
        when {
            actionButton != 0 -> actionButton.toGfnMouseButton()
            buttonState != 0 -> buttonState.toGfnMouseButton()
            else -> 1
        }

    private fun MotionEvent.hatDpadButtons(): Int {
        var mask = 0
        val hatX = getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = getAxisValue(MotionEvent.AXIS_HAT_Y)
        if (hatY <= -0.5f) mask = mask or GamepadButtonMapping.DPAD_UP
        if (hatY >= 0.5f) mask = mask or GamepadButtonMapping.DPAD_DOWN
        if (hatX <= -0.5f) mask = mask or GamepadButtonMapping.DPAD_LEFT
        if (hatX >= 0.5f) mask = mask or GamepadButtonMapping.DPAD_RIGHT
        return mask
    }

    private fun MotionEvent.rawGamepadAxes(): AndroidGamepadRawAxes =
        AndroidGamepadRawAxes(
            x = getAxisValue(MotionEvent.AXIS_X),
            y = getAxisValue(MotionEvent.AXIS_Y),
            z = getAxisValue(MotionEvent.AXIS_Z),
            rz = getAxisValue(MotionEvent.AXIS_RZ),
            rx = getAxisValue(MotionEvent.AXIS_RX),
            ry = getAxisValue(MotionEvent.AXIS_RY),
            hatX = getAxisValue(MotionEvent.AXIS_HAT_X),
            hatY = getAxisValue(MotionEvent.AXIS_HAT_Y),
        )

    private fun MotionEvent.axisAvailability(): AndroidGamepadAxisAvailability {
        val cacheKey = deviceId
        if (cacheKey >= 0) {
            controllerAxisAvailability[cacheKey]?.let { return it }
        }
        return AndroidGamepadAxisAvailability(
            x = hasMotionAxis(MotionEvent.AXIS_X),
            y = hasMotionAxis(MotionEvent.AXIS_Y),
            z = hasMotionAxis(MotionEvent.AXIS_Z),
            rz = hasMotionAxis(MotionEvent.AXIS_RZ),
            rx = hasMotionAxis(MotionEvent.AXIS_RX),
            ry = hasMotionAxis(MotionEvent.AXIS_RY),
            hatX = hasMotionAxis(MotionEvent.AXIS_HAT_X),
            hatY = hasMotionAxis(MotionEvent.AXIS_HAT_Y),
        ).also { availability ->
            if (cacheKey >= 0) {
                controllerAxisAvailability[cacheKey] = availability
            }
        }
    }

    private fun MotionEvent.hasMotionAxis(axis: Int): Boolean {
        val inputDevice = device ?: return false
        return inputDevice.getMotionRange(axis, source) != null ||
            inputDevice.getMotionRange(axis) != null
    }

    private fun KeyEvent.isGamepadEvent(): Boolean {
        val controllerInputDevice = isControllerInputDevice()
        return (controllerInputDevice &&
            (GamepadButtonMapping.maskForKeyCode(keyCode, controllerActivation = true) != null ||
                AndroidControllerInput.isPrimaryActivationKey(keyCode) ||
                keyCode == KeyEvent.KEYCODE_BUTTON_L2 ||
                keyCode == KeyEvent.KEYCODE_BUTTON_R2)) ||
            GamepadButtonMapping.isControllerButtonKeyCode(keyCode)
    }

    private fun KeyEvent.isHardwareKeyboardSource(): Boolean =
        !isControllerInputDevice() &&
            ((source and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD ||
                InputDevice.getDevice(deviceId)?.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC)

    private fun MotionEvent.isGamepadMotionEvent(): Boolean =
        isFromSource(InputDevice.SOURCE_JOYSTICK) ||
            isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            (AndroidControllerInput.isControllerEvent(source, deviceId) && !isMouseLikePointer())

    private fun KeyEvent.isControllerInputDevice(): Boolean =
        AndroidControllerInput.isControllerEvent(source, deviceId)

    private fun Int.toGfnMouseButton(): Int = when {
        this and MotionEvent.BUTTON_PRIMARY != 0 -> 1
        this and MotionEvent.BUTTON_TERTIARY != 0 -> 2
        this and MotionEvent.BUTTON_SECONDARY != 0 -> 3
        this and MotionEvent.BUTTON_BACK != 0 -> 4
        this and MotionEvent.BUTTON_FORWARD != 0 -> 5
        else -> 1
    }

    private companion object {
        private const val ANDROID_TV_CODEC_RELEASE_SETTLE_MS = 180L
        private const val EXTERNAL_MOUSE_ABSOLUTE_DELTA_LIMIT_PX = 240f
        private const val GAMEPAD_MAX_CONTROLLERS = 4
        private const val RUMBLE_EFFECT_MS = 90L
        private const val RUMBLE_THROTTLE_MS = 35L
        private const val HAPTICS_LOG_INTERVAL_MS = 5000L
        private const val ANALOG_ACTIVITY_THRESHOLD = 0.01f
        private const val ANALOG_DIAGNOSTIC_INTERVAL_MS = 250L
        private const val GAMEPAD_PACKET_DIAGNOSTIC_INTERVAL_MS = 1_000L
        private const val INPUT_BACKPRESSURE_DIAGNOSTIC_INTERVAL_MS = 1_000L
    }

    private fun radialDeadzoneScale(x: Float, y: Float, deadzone: Float = 0.15f): Float {
        val magnitude = kotlin.math.sqrt((x * x + y * y).toDouble()).toFloat()
        if (magnitude < deadzone) return 0f
        val scaled = ((magnitude - deadzone) / (1f - deadzone)).coerceIn(0f, 1f)
        return scaled / magnitude
    }

    private fun normalizeToInt16(value: Float): Int = (value.coerceIn(-1f, 1f) * 32767).roundToInt().coerceIn(-32768, 32767)
    private fun normalizeToUint8(value: Float): Int = (value.coerceIn(0f, 1f) * 255).roundToInt().coerceIn(0, 255)
    private fun normalizeTriggerAxis(value: Float): Float = if (value < 0f) ((value + 1f) / 2f).coerceIn(0f, 1f) else value.coerceIn(0f, 1f)
    private fun Float.formatAxis(): String = String.format(Locale.US, "%.3f", this)
}

open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(description: SessionDescription?) = Unit
    override fun onSetSuccess() = Unit
    override fun onCreateFailure(error: String?) = Unit
    override fun onSetFailure(error: String?) = Unit
}

object SdpTools {
    data class RewriteResult(val sdp: String, val replacements: Int)

    fun fixServerIp(sdp: String, serverIp: String): String =
        fixServerEndpoint(sdp, serverIp, mediaConnectionInfo = null)

    fun fixServerEndpoint(sdp: String, serverIp: String, mediaConnectionInfo: MediaConnectionInfo?): String {
        val signalingIp = extractPublicIp(serverIp) ?: return sdp
        val mediaIp = mediaConnectionInfo?.ip?.let(::extractPublicIp) ?: signalingIp
        val mediaPort = mediaConnectionInfo?.port?.takeIf { it in 1..65535 }
        return sdp
            .replace(Regex("c=IN IP4 ([^\\r\\n]+)")) { match ->
                val address = match.groupValues[1]
                if (shouldRewriteRemoteEndpoint(address, mediaConnectionInfo != null)) "c=IN IP4 $mediaIp" else match.value
            }
            .replace(Regex("(a=candidate:\\S+\\s+\\d+\\s+\\w+\\s+\\d+\\s+)([^\\s]+)\\s+(\\d+)(\\s+)")) { match ->
                val address = match.groupValues[2]
                val port = match.groupValues[3]
                if (shouldRewriteRemoteEndpoint(address, mediaConnectionInfo != null)) {
                    "${match.groupValues[1]}$mediaIp ${mediaPort ?: port}${match.groupValues[4]}"
                } else {
                    match.value
                }
            }
    }

    fun preferCodec(sdp: String, settings: StreamSettings): String =
        preferCodec(sdp, settings.codec, settings.prefersTenBitVideo())

    fun preferCodec(sdp: String, codec: VideoCodec): String =
        preferCodec(sdp, codec, preferTenBit = codec != VideoCodec.H265)

    private fun preferCodec(sdp: String, codec: VideoCodec, preferTenBit: Boolean): String {
        val target = when (codec) {
            VideoCodec.H264 -> "H264"
            VideoCodec.H265 -> "H265"
            VideoCodec.AV1 -> "AV1"
        }
        val lineEnding = if (sdp.contains("\r\n")) "\r\n" else "\n"
        val lines = sdp.split(Regex("\\r?\\n"))
        var inVideo = false
        val codecByPt = mutableMapOf<String, String>()
        val rtxApt = mutableMapOf<String, String>()
        val fmtpByPt = mutableMapOf<String, String>()
        lines.forEach { line ->
            if (line.startsWith("m=video")) inVideo = true else if (line.startsWith("m=") && inVideo) inVideo = false
            if (inVideo && line.startsWith("a=rtpmap:")) {
                val rest = line.substringAfter(":")
                val pt = rest.substringBefore(" ")
                val name = rest.substringAfter(" ").substringBefore("/").uppercase(Locale.US).let { if (it == "HEVC") "H265" else it }
                codecByPt[pt] = name
            }
            if (inVideo && line.startsWith("a=fmtp:")) {
                val rest = line.substringAfter(":")
                val pt = rest.substringBefore(" ")
                val params = rest.substringAfter(" ", "")
                fmtpByPt[pt] = params
                Regex("(?:^|;)\\s*apt=(\\d+)").find(params)?.groupValues?.getOrNull(1)?.let { rtxApt[pt] = it }
            }
        }
        val preferred = codecByPt.filterValues { it == target }.keys.toMutableList()
        if (preferred.isEmpty()) return sdp
        if (codec == VideoCodec.H265) {
            preferred.sortBy { pt -> h265ProfilePriority(fmtpByPt[pt], preferTenBit) }
        }
        val allowed = preferred.toMutableSet()
        rtxApt.forEach { (rtx, apt) ->
            if (apt in preferred && codecByPt[rtx] == "RTX") allowed += rtx
        }
        val output = mutableListOf<String>()
        inVideo = false
        lines.forEach { line ->
            if (line.startsWith("m=video")) {
                inVideo = true
                val parts = line.split(Regex("\\s+"))
                val ordered = preferred + parts.drop(3).filter { it in allowed && it !in preferred }
                output += if (ordered.isNotEmpty()) (parts.take(3) + ordered).joinToString(" ") else line
                return@forEach
            }
            if (line.startsWith("m=") && inVideo) inVideo = false
            if (inVideo && (line.startsWith("a=rtpmap:") || line.startsWith("a=fmtp:") || line.startsWith("a=rtcp-fb:"))) {
                val pt = line.substringAfter(":").substringBefore(" ")
                if (pt !in allowed) return@forEach
            }
            output += line
        }
        return output.joinToString(lineEnding)
    }

    fun rewriteH265TierFlag(sdp: String, tierFlag: Int): RewriteResult {
        val payloads = h265PayloadTypes(sdp)
        if (payloads.isEmpty()) return RewriteResult(sdp, 0)
        val lineEnding = if (sdp.contains("\r\n")) "\r\n" else "\n"
        var replacements = 0
        val output = sdp.split(Regex("\\r?\\n")).map { line ->
            if (!line.startsWith("a=fmtp:")) return@map line
            val pt = line.substringAfter(":").substringBefore(" ")
            if (pt !in payloads) return@map line
            val next = line.replace(Regex("tier-flag=1", RegexOption.IGNORE_CASE), "tier-flag=$tierFlag")
            if (next != line) replacements += 1
            next
        }
        return RewriteResult(output.joinToString(lineEnding), replacements)
    }

    fun rewriteH265LevelIdByProfile(sdp: String, maxLevelByProfile: Map<Int, Int>): RewriteResult {
        val payloads = h265PayloadTypes(sdp)
        if (payloads.isEmpty() || maxLevelByProfile.isEmpty()) return RewriteResult(sdp, 0)
        val lineEnding = if (sdp.contains("\r\n")) "\r\n" else "\n"
        var replacements = 0
        val output = sdp.split(Regex("\\r?\\n")).map { line ->
            if (!line.startsWith("a=fmtp:")) return@map line
            val rest = line.substringAfter(":")
            val pt = rest.substringBefore(" ")
            val params = rest.substringAfter(" ", "")
            if (pt !in payloads || params.isBlank()) return@map line
            val profile = Regex("(?:^|;)\\s*profile-id=(\\d+)", RegexOption.IGNORE_CASE)
                .find(params)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: return@map line
            val level = Regex("(?:^|;)\\s*level-id=(\\d+)", RegexOption.IGNORE_CASE)
                .find(params)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: return@map line
            val maxLevel = maxLevelByProfile[profile] ?: return@map line
            if (level <= maxLevel) return@map line
            val next = line.replace(Regex("(level-id=)(\\d+)", RegexOption.IGNORE_CASE), "$1$maxLevel")
            if (next != line) replacements += 1
            next
        }
        return RewriteResult(output.joinToString(lineEnding), replacements)
    }

    fun negotiatesCodec(sdp: String, codec: VideoCodec): Boolean {
        val target = when (codec) {
            VideoCodec.H264 -> "H264"
            VideoCodec.H265 -> "H265"
            VideoCodec.AV1 -> "AV1"
        }
        var inVideo = false
        sdp.split(Regex("\\r?\\n")).forEach { line ->
            if (line.startsWith("m=video")) {
                inVideo = true
                return@forEach
            }
            if (line.startsWith("m=") && inVideo) {
                inVideo = false
            }
            if (!inVideo || !line.startsWith("a=rtpmap:")) return@forEach
            val codecName = line.substringAfter(" ")
                .substringBefore("/")
                .uppercase(Locale.US)
                .let { if (it == "HEVC") "H265" else it }
            if (codecName == target) return true
        }
        return false
    }

    private fun h265PayloadTypes(sdp: String): Set<String> {
        var inVideo = false
        val payloads = mutableSetOf<String>()
        sdp.split(Regex("\\r?\\n")).forEach { line ->
            if (line.startsWith("m=video")) {
                inVideo = true
                return@forEach
            }
            if (line.startsWith("m=") && inVideo) {
                inVideo = false
            }
            if (!inVideo || !line.startsWith("a=rtpmap:")) return@forEach
            val rest = line.substringAfter(":")
            val pt = rest.substringBefore(" ")
            val codecName = rest.substringAfter(" ")
                .substringBefore("/")
                .uppercase(Locale.US)
                .let { if (it == "HEVC") "H265" else it }
            if (pt.isNotBlank() && codecName == "H265") payloads += pt
        }
        return payloads
    }

    private fun h265ProfilePriority(fmtp: String?, preferTenBit: Boolean): Int {
        val profileId = Regex("(?:^|;)\\s*profile-id=(\\d+)")
            .find(fmtp.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
        return if (preferTenBit) {
            when (profileId) {
                "2" -> 0
                "1" -> 1
                else -> 2
            }
        } else {
            when (profileId) {
                "1" -> 0
                null -> 1
                "2" -> 2
                else -> 3
            }
        }
    }

    private fun StreamSettings.prefersTenBitVideo(): Boolean =
        hdrEnabled ||
            colorQuality == ColorQuality.TenBit420 ||
            colorQuality == ColorQuality.TenBit444

    fun mungeAnswerSdp(sdp: String, maxBitrateKbps: Int): String {
        val lineEnding = if (sdp.contains("\r\n")) "\r\n" else "\n"
        val out = mutableListOf<String>()
        val lines = sdp.split(Regex("\\r?\\n"))
        lines.forEachIndexed { index, line ->
            val rewritten = if (line.startsWith("a=fmtp:") && line.contains("minptime=") && !line.contains("stereo=1")) "$line;stereo=1" else line
            out += rewritten
            if ((line.startsWith("m=video") || line.startsWith("m=audio")) && !lines.getOrNull(index + 1).orEmpty().startsWith("b=")) {
                out += if (line.startsWith("m=video")) "b=AS:$maxBitrateKbps" else "b=AS:128"
            }
        }
        return out.joinToString(lineEnding)
    }

    fun parseInputProtocolVersion(sdp: String): Int =
        Regex("a=ri\\.version:(\\d+)").find(sdp)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: DEFAULT_INPUT_PROTOCOL_VERSION

    fun parsePartialReliableThresholdMs(sdp: String): Int =
        Regex("a=ri\\.partialReliableThresholdMs:(\\d+)")
            .find(sdp)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.coerceIn(1, 5000)
            ?: 30

    fun parsePartiallyReliableGamepadMask(sdp: String): Int =
        parseRiIntegerAttribute(
            sdp,
            "ri.enablePartiallyReliableTransferGamepad",
            PARTIALLY_RELIABLE_GAMEPAD_MASK_ALL,
        )

    fun buildNvstSdp(offerSdp: String, settings: StreamSettings, localAnswer: String): String {
        val (width, height) = streamResolutionPixels(settings)
        val ufrag = Regex("a=ice-ufrag:([^\\r\\n]+)").find(localAnswer)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val pwd = Regex("a=ice-pwd:([^\\r\\n]+)").find(localAnswer)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val fingerprint = Regex("a=fingerprint:sha-256 ([^\\r\\n]+)").find(localAnswer)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val threshold = Regex("a=ri\\.partialReliableThresholdMs:(\\d+)").find(offerSdp)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 30
        val bitDepth = if (settings.hdrEnabled || settings.colorQuality == ColorQuality.TenBit420 || settings.colorQuality == ColorQuality.TenBit444) 10 else 8
        val maxBitrate = max(OFFICIAL_MIN_BITRATE_KBPS, settings.maxBitrateMbps * 1000)
        val minBitrate = OFFICIAL_MIN_BITRATE_KBPS
        val initialBitrate = max(minBitrate, maxBitrate / 4)
        val isHighFps = settings.fps > 60
        val isAtLeast120Fps = settings.fps >= 120
        val is90Fps = settings.fps == 90
        val is120Fps = settings.fps == 120
        val isAtLeast240Fps = settings.fps >= 240
        val isAv1 = settings.codec == VideoCodec.AV1
        val minTargetFrameTimeUs = ((1_000_000L * 95L) / (settings.fps.coerceAtLeast(1) * 100L))
            .coerceAtLeast(1000L)
        return buildList {
            add("v=0")
            add("o=SdpTest test_id_13 14 IN IPv4 127.0.0.1")
            add("s=-")
            add("t=0 0")
            add("a=general.icePassword:$pwd")
            add("a=general.iceUserNameFragment:$ufrag")
            add("a=general.dtlsFingerprint:$fingerprint")
            add("m=video 0 RTP/AVP")
            add("a=msid:fbc-video-0")
            add("a=vqos.fec.rateDropWindow:10")
            add("a=vqos.fec.minRequiredFecPackets:2")
            add("a=vqos.fec.repairMinPercent:5")
            add("a=vqos.fec.repairPercent:5")
            add("a=vqos.fec.repairMaxPercent:35")
            add("a=vqos.bllFec.enable:0")
            add("a=vqos.dynamicStreamingMode:0")
            add("a=vqos.drc.enable:0")
            add("a=vqos.calculateAvgVideoStreamingBitrate:1")
            add("a=video.dx9EnableNv12:1")
            add("a=video.dx9EnableHdr:${if (settings.hdrEnabled) 1 else 0}")
            add("a=vqos.qpg.enable:1")
            add("a=vqos.resControl.qp.qpg.featureSetting:7")
            add("a=video.adaptiveQuantization.spatialAQSetting:7")
            add("a=video.adaptiveQuantization.temporalAQSetting:0")
            add("a=video.adaptiveQuantization.spatialAQStrength:12")
            add("a=video.adaptiveQuantization.qpThresholdAdjPercent:2")
            add("a=video.adaptiveQuantization.saqAdaptMinQpThresholdPercent:40")
            add("a=video.adaptiveQuantization.saqAdaptMaxQpThresholdPercent:100")
            add("a=video.adaptiveQuantization.saqAdaptDecayStrengthX100:250")
            add("a=video.adaptiveQuantization.perfAdjEnablement:1")
            add("a=video.framePacing.mode:2")
            add("a=video.framePacing.pid.minTargetFrameTimeUs:$minTargetFrameTimeUs")
            add("a=bwe.useOwdCongestionControl:1")
            add("a=video.enableRtpNack:1")
            add("a=vqos.bw.txRxLag.minFeedbackTxDeltaMs:200")
            add("a=vqos.drc.bitrateIirFilterFactor:18")
            add("a=video.packetSize:1140")
            add("a=packetPacing.version:3")
            add("a=packetPacing.mode:1")
            add("a=packetPacing.minNumPacketsPerGroup:15")
            add("a=packetPacing.enableAccurateSleep:1")
            add("a=packetPacing.enableSmoothTransition:1")
            add("a=packetPacing.allowFpsBasedToggle:1")
            add("a=vqos.relaxMaxBitrate.overrideAvgBitrateThresholdPercent:4")
            add("a=vqos.relaxMaxBitrate.customAvgBitrateThresholdPercent:65")
            add("a=vqos.relaxMaxBitrate.overrideAvgQpThresholdPercent:7")
            add("a=vqos.relaxMaxBitrate.customAvgQpThresholdPercent:51")
            add("a=vqos.relaxMaxBitrate.iirFilterFactor:120")
            add("a=vqos.qpDelta.qpDeltaMaxPercent:10")
            add("a=vqos.qpDelta.qpDeltaSurfaceAdjustmentStrengthPercent:70")
            add("a=vqos.qpDelta.qpDeltaVbvUsageFactorPercentH264:100")
            add("a=vqos.qpDelta.qpDeltaVbvUsageFactorPercentH265:100")
            add("a=vqos.qpDelta.qpDeltaVbvUsageFactorPercentAv1:100")
            add("a=vqos.qpDelta.qpDeltaMinPercent:60")
            add("a=vqos.qpDelta.qpDeltaIirFactor:60")
            add("a=vqos.qpDelta.qpDeltaThrottlePercent:100")
            if (isHighFps) {
                add("a=vqos.dfc.enable:1")
                add("a=vqos.dfc.decodeFpsAdjPercent:85")
                add("a=vqos.dfc.targetDownCooldownMs:250")
                add("a=vqos.dfc.dfcAlgoVersion:${if (isAtLeast120Fps) 2 else 1}")
                add("a=vqos.dfc.minTargetFps:${if (isAtLeast120Fps) 100 else 60}")
                add("a=vqos.resControl.dfc.useClientFpsPerf:0")
                add("a=vqos.dfc.adjustResAndFps:0")
                add("a=bwe.iirFilterFactor:8")
                add("a=video.encoderFeatureSetting:47")
                add("a=video.encoderPreset:6")
                val captureTuning = when {
                    is90Fps -> 9 to 11
                    is120Fps -> 6 to 9
                    isAtLeast240Fps -> 18 to 9
                    else -> null
                }
                captureTuning?.let { (grabTimeoutMs, decodeThresholdMs) ->
                    add("a=video.fbcDynamicFpsGrabTimeoutMs:$grabTimeoutMs")
                    add("a=vqos.resControl.cpmRtc.decodeTimeThresholdMs:$decodeThresholdMs")
                }
                add("a=vqos.maxStreamFpsEstimate:${settings.fps}")
            } else {
                add("a=vqos.dfc.enable:0")
                add("a=vqos.dfc.adjustResAndFps:0")
            }
            if (isAtLeast240Fps) {
                add("a=video.enableNextCaptureMode:1")
                val splitEncodeStrips = if (isAv1 && width * height >= HIGH_RESOLUTION_AV1_SPLIT_ENCODE_PIXELS) 63 else 3
                add("a=video.videoSplitEncodeStripsPerFrame:$splitEncodeStrips")
                add("a=video.updateSplitEncodeStateDynamically:1")
                add("a=vqos.rtcPreemptiveIdrSettings.minBurstNackSize:65535")
                add("a=vqos.rtcPreemptiveIdrSettings.minNackPacketCaptureAgeMs:65535")
            }
            add("a=vqos.adjustStreamingFpsDuringOutOfFocus:1")
            add("a=vqos.resControl.cpmRtc.ignoreOutOfFocusWindowState:1")
            add("a=vqos.resControl.perfHistory.rtcIgnoreOutOfFocusWindowState:1")
            add("a=vqos.resControl.cpmRtc.featureMask:0")
            add("a=vqos.resControl.cpmRtc.enable:0")
            add("a=vqos.resControl.cpmRtc.minResolutionPercent:100")
            add("a=vqos.resControl.cpmRtc.resolutionChangeHoldonMs:999999")
            add("a=packetPacing.numGroups:${if (is120Fps) 3 else 5}")
            add("a=packetPacing.maxDelayUs:1000")
            add("a=packetPacing.minNumPacketsFrame:10")
            add("a=video.rtpNackQueueLength:1024")
            add("a=video.rtpNackQueueMaxPackets:512")
            add("a=video.rtpNackMaxPacketCount:25")
            add("a=vqos.drc.qpMaxResThresholdAdj:4")
            add("a=vqos.grc.qpMaxResThresholdAdj:4")
            add("a=vqos.drc.iirFilterFactor:100")
            if (isAv1) {
                add("a=vqos.drc.minQpHeadroom:20")
                add("a=vqos.drc.lowerQpThreshold:100")
                add("a=vqos.drc.upperQpThreshold:200")
                add("a=vqos.drc.minAdaptiveQpThreshold:180")
                add("a=vqos.drc.qpCodecThresholdAdj:0")
                add("a=vqos.drc.qpMaxResThresholdAdj:20")
                add("a=vqos.dfc.minQpHeadroom:20")
                add("a=vqos.dfc.qpLowerLimit:100")
                add("a=vqos.dfc.qpMaxUpperLimit:200")
                add("a=vqos.dfc.qpMinUpperLimit:180")
                add("a=vqos.dfc.qpMaxResThresholdAdj:20")
                add("a=vqos.dfc.qpCodecThresholdAdj:0")
                add("a=vqos.grc.minQpHeadroom:20")
                add("a=vqos.grc.lowerQpThreshold:100")
                add("a=vqos.grc.upperQpThreshold:200")
                add("a=vqos.grc.minAdaptiveQpThreshold:180")
                add("a=vqos.grc.qpMaxResThresholdAdj:20")
                add("a=vqos.grc.qpCodecThresholdAdj:0")
                add("a=video.minQp:25")
                add("a=video.enableAv1RcPrecisionFactor:1")
            }
            add("a=video.clientViewportWd:$width")
            add("a=video.clientViewportHt:$height")
            add("a=video.maxFPS:${settings.fps}")
            add("a=video.initialBitrateKbps:$initialBitrate")
            add("a=video.initialPeakBitrateKbps:$initialBitrate")
            add("a=vqos.bw.maximumBitrateKbps:$maxBitrate")
            add("a=vqos.bw.minimumBitrateKbps:$minBitrate")
            add("a=vqos.bw.peakBitrateKbps:$maxBitrate")
            add("a=vqos.bw.serverPeakBitrateKbps:$maxBitrate")
            add("a=vqos.bw.enableBandwidthEstimation:1")
            add("a=vqos.bw.disableBitrateLimit:0")
            add("a=vqos.grc.maximumBitrateKbps:$maxBitrate")
            add("a=vqos.grc.enable:0")
            add("a=video.maxNumReferenceFrames:4")
            add("a=video.mapRtpTimestampsToFrames:1")
            add("a=video.encoderCscMode:3")
            add("a=video.dynamicRangeMode:0")
            add("a=video.bitDepth:$bitDepth")
            // Keep the encoded geometry fixed for every codec. AV1 value 1 was
            // added during the June SDP expansion and permits the horizontal
            // scaling seen as 1366x768 -> 1230x768 in affected sessions.
            add("a=video.scalingFeature1:0")
            add("a=video.prefilterParams.prefilterModel:0")
            add("m=audio 0 RTP/AVP")
            add("a=msid:audio")
            add("m=mic 0 RTP/AVP")
            add("a=msid:mic")
            add("a=rtpmap:0 PCMU/8000")
            add("m=application 0 RTP/AVP")
            add("a=msid:input_1")
            add("a=ri.partialReliableThresholdMs:$threshold")
            add("a=ri.hidDeviceMask:4294967295")
            add("a=ri.enablePartiallyReliableTransferGamepad:15")
            add("a=ri.enablePartiallyReliableTransferHid:4294967295")
            add("")
        }.joinToString("\n")
    }

    private fun extractPublicIp(hostOrIp: String): String? {
        if (Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(hostOrIp)) return hostOrIp
        val first = hostOrIp.substringBefore(".")
        val parts = first.split("-")
        return if (parts.size == 4 && parts.all { it.all(Char::isDigit) }) parts.joinToString(".") else null
    }

    private fun shouldRewriteRemoteEndpoint(address: String, hasMediaEndpoint: Boolean): Boolean {
        val remoteAddress = parseIpv4Address(address) ?: return false
        if (remoteAddress.inetAddress.isAnyLocalAddress) return true
        return hasMediaEndpoint && remoteAddress.isUnroutable()
    }

    private data class RemoteIpv4Address(
        val octets: List<Int>,
        val inetAddress: InetAddress,
    ) {
        fun isUnroutable(): Boolean =
            inetAddress.isLoopbackAddress ||
                inetAddress.isSiteLocalAddress ||
                inetAddress.isLinkLocalAddress ||
                inetAddress.isMulticastAddress ||
                isCarrierGradeNatAddress(octets)
    }

    private fun parseIpv4Address(address: String): RemoteIpv4Address? {
        val octets = address.split(".").map { it.toIntOrNull() ?: return null }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return null
        val inetAddress = InetAddress.getByAddress(octets.map { it.toByte() }.toByteArray())
        return RemoteIpv4Address(octets, inetAddress)
    }

    private fun isCarrierGradeNatAddress(octets: List<Int>): Boolean {
        return octets[0] == 100 && octets[1] in 64..127
    }

    private fun parseRiIntegerAttribute(sdp: String, attribute: String, fallback: Int): Int {
        val escaped = Regex.escape(attribute)
        val raw = Regex("a=$escaped:([^\\r\\n]+)", RegexOption.IGNORE_CASE)
            .find(sdp)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?: return fallback
        val parsed = if (raw.startsWith("0x", ignoreCase = true)) {
            raw.drop(2).toIntOrNull(16)
        } else {
            raw.toIntOrNull()
        }
        return parsed ?: fallback
    }

    private const val OFFICIAL_MIN_BITRATE_KBPS = 4000
    private const val HIGH_RESOLUTION_AV1_SPLIT_ENCODE_PIXELS = 2_764_800
    private const val PARTIALLY_RELIABLE_GAMEPAD_MASK_ALL = 0x0f
}

class InputEncoder {
    private var protocolVersion = 3
    private val gamepadSequences = mutableMapOf<Int, Int>()

    fun setProtocolVersion(version: Int) {
        protocolVersion = version.coerceAtLeast(1)
    }

    fun resetGamepadSequences() {
        gamepadSequences.clear()
    }

    fun encodeHeartbeat(): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(INPUT_HEARTBEAT).array()

    fun encodeKeyDown(key: KeyboardPayload): ByteArray = encodeKey(INPUT_KEY_DOWN, key)
    fun encodeKeyUp(key: KeyboardPayload): ByteArray = encodeKey(INPUT_KEY_UP, key)

    fun encodeMouseMove(dx: Int, dy: Int): ByteArray {
        val bytes = ByteArray(22)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(INPUT_MOUSE_REL)
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            .putShort(4, dx.coerceIn(-32768, 32767).toShort())
            .putShort(6, dy.coerceIn(-32768, 32767).toShort())
            .putShort(8, 0.toShort())
            .putInt(10, 0)
            .putLong(14, timestampUs())
        return wrapMouseMove(bytes)
    }

    fun encodeMouseButton(type: Int, button: Int): ByteArray {
        val bytes = ByteArray(18)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(type)
        bytes[4] = button.coerceIn(1, 5).toByte()
        bytes[5] = 0
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putInt(6, 0).putLong(10, timestampUs())
        return wrapSingle(bytes)
    }

    fun encodeMouseWheel(delta: Int): ByteArray {
        val bytes = ByteArray(22)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(INPUT_MOUSE_WHEEL)
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            .putShort(4, 0.toShort())
            .putShort(6, delta.coerceIn(-32768, 32767).toShort())
            .putShort(8, 0.toShort())
            .putInt(10, 0)
            .putLong(14, timestampUs())
        return wrapSingle(bytes)
    }

    /**
     * A batch of finger updates, one packet per input event.
     *
     * Layout, taken from the official web client's encoder. Note the opcode is little-endian while
     * everything after it is big-endian — the same split every other packet here uses.
     *
     * ```
     * 0..3    opcode 24            uint32 LE
     * 4..5    payload size         uint16 BE   = 8 + 16 * count
     * 6..7    count                uint16 BE
     * 8+      records, 16 bytes each:
     *           +0     slot        uint8
     *           +1     phase       uint8       1=down 2=up 4=move 8=cancel
     *           +2..3  x           uint16 BE   0..65535 across the video area
     *           +4..5  y           uint16 BE
     *           +6     radiusX     uint8
     *           +7     radiusY     uint8
     *           +8..15 timestamp   int64 BE    microseconds
     * ```
     *
     * Returns null for an empty batch so callers cannot send a header describing nothing.
     */
    internal fun encodeTouchBatch(touches: List<TouchRecord>, nowUs: Long = timestampUs()): ByteArray? {
        if (touches.isEmpty()) return null
        val count = minOf(touches.size, MAX_TOUCH_RECORDS_PER_BATCH)
        val payloadSize = 8 + 16 * count
        val bytes = ByteArray(payloadSize)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(INPUT_TOUCH)
        val be = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        be.putShort(4, payloadSize.toShort())
        be.putShort(6, count.toShort())
        for (index in 0 until count) {
            val touch = touches[index]
            val offset = 8 + 16 * index
            bytes[offset] = touch.slot.toByte()
            bytes[offset + 1] = touch.phase.toByte()
            be.putShort(offset + 2, touch.x.coerceIn(0, TOUCH_COORDINATE_MAX).toShort())
            be.putShort(offset + 4, touch.y.coerceIn(0, TOUCH_COORDINATE_MAX).toShort())
            bytes[offset + 6] = touch.radiusX.coerceIn(0, 255).toByte()
            bytes[offset + 7] = touch.radiusY.coerceIn(0, 255).toByte()
            // 0 means "stamp it here", so the router does not have to reach for the same clock
            // every other packet in this encoder uses.
            be.putLong(offset + 8, if (touch.timestampUs != 0L) touch.timestampUs else nowUs)
        }
        return wrapSingle(bytes, nowUs)
    }

    fun encodeHapticsEnabled(enabled: Boolean): ByteArray {
        val bytes = ByteArray(6)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(INPUT_HAPTICS_ENABLED)
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putShort(4, (if (enabled) 1 else 0).toShort())
        return wrapSingle(bytes)
    }

    fun encodeGamepadState(
        controllerId: Int,
        buttons: Int,
        leftTrigger: Int,
        rightTrigger: Int,
        leftStickX: Int,
        leftStickY: Int,
        rightStickX: Int,
        rightStickY: Int,
        bitmap: Int,
        partiallyReliable: Boolean,
        timestampUs: Long = timestampUs(),
    ): ByteArray {
        val bytes = ByteArray(38)
        val le = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        le.putInt(0, INPUT_GAMEPAD)
        le.putShort(4, 26.toShort())
        le.putShort(6, (controllerId and 0x03).toShort())
        le.putShort(8, bitmap.toShort())
        le.putShort(10, 20.toShort())
        le.putShort(12, buttons.toShort())
        le.putShort(14, ((leftTrigger and 0xff) or ((rightTrigger and 0xff) shl 8)).toShort())
        le.putShort(16, leftStickX.toShort())
        le.putShort(18, leftStickY.toShort())
        le.putShort(20, rightStickX.toShort())
        le.putShort(22, rightStickY.toShort())
        le.putShort(24, 0.toShort())
        le.putShort(26, 85.toShort())
        le.putShort(28, 0.toShort())
        le.putLong(30, timestampUs)
        return if (partiallyReliable) wrapGamepadPartiallyReliable(bytes, controllerId) else wrapGamepadReliable(bytes)
    }

    private fun encodeKey(type: Int, key: KeyboardPayload): ByteArray {
        val bytes = ByteArray(18)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(type)
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            .putShort(4, key.keycode.toShort())
            .putShort(6, key.modifiers.toShort())
            .putShort(8, key.scancode.toShort())
            .putLong(10, key.timestampUs)
        return wrapSingle(bytes)
    }

    private fun wrapSingle(payload: ByteArray, nowUs: Long = timestampUs()): ByteArray {
        if (protocolVersion <= 2) return payload
        return ByteArray(10 + payload.size).also {
            it[0] = 0x23
            ByteBuffer.wrap(it).order(ByteOrder.BIG_ENDIAN).putLong(1, nowUs)
            it[9] = 0x22
            payload.copyInto(it, 10)
        }
    }

    private fun wrapMouseMove(payload: ByteArray): ByteArray {
        if (protocolVersion <= 2) return payload
        return ByteArray(12 + payload.size).also {
            it[0] = 0x23
            ByteBuffer.wrap(it).order(ByteOrder.BIG_ENDIAN).putLong(1, timestampUs())
            it[9] = 0x21
            ByteBuffer.wrap(it).order(ByteOrder.BIG_ENDIAN).putShort(10, payload.size.toShort())
            payload.copyInto(it, 12)
        }
    }

    private fun wrapGamepadReliable(payload: ByteArray): ByteArray {
        if (protocolVersion <= 2) return payload
        return ByteArray(12 + payload.size).also {
            it[0] = 0x23
            ByteBuffer.wrap(it).order(ByteOrder.BIG_ENDIAN).putLong(1, timestampUs())
            it[9] = 0x21
            ByteBuffer.wrap(it).order(ByteOrder.BIG_ENDIAN).putShort(10, payload.size.toShort())
            payload.copyInto(it, 12)
        }
    }

    private fun wrapGamepadPartiallyReliable(payload: ByteArray, index: Int): ByteArray {
        if (protocolVersion <= 2) return payload
        val seq = gamepadSequences[index] ?: 1
        gamepadSequences[index] = (seq + 1) and 0xffff
        return ByteArray(16 + payload.size).also {
            it[0] = 0x23
            val be = ByteBuffer.wrap(it).order(ByteOrder.BIG_ENDIAN)
            be.putLong(1, timestampUs())
            it[9] = 0x26
            it[10] = (index and 0xff).toByte()
            be.putShort(11, seq.toShort())
            it[13] = 0x21
            be.putShort(14, payload.size.toShort())
            payload.copyInto(it, 16)
        }
    }

    data class KeyboardPayload(
        val keycode: Int,
        val scancode: Int,
        val modifiers: Int,
        val timestampUs: Long = timestampUs(),
    )

    data class TextKeySpec(
        val keycode: Int,
        val scancode: Int,
        val shift: Boolean = false,
    ) {
        fun toKeyboardPayload(modifiers: Int): KeyboardPayload =
            KeyboardPayload(keycode, scancode, modifiers)
    }

    companion object {
        const val INPUT_HEARTBEAT = 2
        const val INPUT_KEY_DOWN = 3
        const val INPUT_KEY_UP = 4
        const val INPUT_MOUSE_REL = 7
        const val INPUT_MOUSE_BUTTON_DOWN = 8
        const val INPUT_MOUSE_BUTTON_UP = 9
        const val INPUT_MOUSE_WHEEL = 10
        const val INPUT_GAMEPAD = 12
        const val INPUT_HAPTICS_ENABLED = 13

        /**
         * Native multi-touch. The host turns these into a Windows digitizer, which is what makes
         * touch-aware games switch to their mobile UI on their own.
         */
        const val INPUT_TOUCH = 24

        fun mapKeyEvent(event: KeyEvent): KeyboardPayload? {
            if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return null
            return mapKeyboardPayload(
                keyCode = event.keyCode,
                unicode = event.unicodeChar,
                scanCode = event.scanCode,
                shift = event.isShiftPressed,
                ctrl = event.isCtrlPressed,
                alt = event.isAltPressed,
                meta = event.isMetaPressed,
                capsLock = event.isCapsLockOn,
                numLock = event.isNumLockOn,
            )
        }

        internal fun mapKeyboardPayload(
            keyCode: Int,
            unicode: Int,
            scanCode: Int,
            shift: Boolean = false,
            ctrl: Boolean = false,
            alt: Boolean = false,
            meta: Boolean = false,
            capsLock: Boolean = false,
            numLock: Boolean = false,
            timestampUs: Long = timestampUs(),
        ): KeyboardPayload? {
            val vk = virtualKey(keyCode, unicode)
            val resolvedScanCode = if (scanCode > 0) scanCode else fallbackScanCode(keyCode)
            if (vk == null || resolvedScanCode == null) return null
            var modifiers = 0
            if (shift) modifiers = modifiers or 0x01
            if (ctrl) modifiers = modifiers or 0x02
            if (alt) modifiers = modifiers or 0x04
            if (meta) modifiers = modifiers or 0x08
            if (capsLock) modifiers = modifiers or 0x10
            if (numLock) modifiers = modifiers or 0x20
            return KeyboardPayload(vk, resolvedScanCode, modifiers, timestampUs)
        }

        internal fun mapTextCharToKeySpec(char: Char): TextKeySpec? {
            val mapped = when (char) {
                in 'a'..'z' -> textKeySpecFromAndroidKeyCode(KeyEvent.KEYCODE_A + (char - 'a'))
                in 'A'..'Z' -> textKeySpecFromAndroidKeyCode(KeyEvent.KEYCODE_A + (char - 'A'), shift = true)
                in '0'..'9' -> textKeySpecFromAndroidKeyCode(KeyEvent.KEYCODE_0 + (char - '0'))
                '\n', '\r' -> textKeySpecFromAndroidKeyCode(KeyEvent.KEYCODE_ENTER)
                else -> textBaseKeyCodes[char]?.let(::textKeySpecFromAndroidKeyCode)
                    ?: textShiftedKeyCodes[char]?.let { textKeySpecFromAndroidKeyCode(it, shift = true) }
            }
            return mapped
        }

        internal fun shiftLeftPayload(modifiers: Int): KeyboardPayload =
            KeyboardPayload(0xa0, fallbackScanCode(KeyEvent.KEYCODE_SHIFT_LEFT) ?: 0x002a, modifiers)

        private fun textKeySpecFromAndroidKeyCode(keyCode: Int, shift: Boolean = false): TextKeySpec? {
            val payload = mapKeyboardPayload(
                keyCode = keyCode,
                unicode = 0,
                scanCode = 0,
                shift = shift,
                timestampUs = 0L,
            ) ?: return null
            return TextKeySpec(payload.keycode, payload.scancode, shift)
        }

        private val textBaseKeyCodes = mapOf(
            ' ' to KeyEvent.KEYCODE_SPACE,
            '-' to KeyEvent.KEYCODE_MINUS,
            '=' to KeyEvent.KEYCODE_EQUALS,
            '[' to KeyEvent.KEYCODE_LEFT_BRACKET,
            ']' to KeyEvent.KEYCODE_RIGHT_BRACKET,
            '\\' to KeyEvent.KEYCODE_BACKSLASH,
            ';' to KeyEvent.KEYCODE_SEMICOLON,
            '\'' to KeyEvent.KEYCODE_APOSTROPHE,
            ',' to KeyEvent.KEYCODE_COMMA,
            '.' to KeyEvent.KEYCODE_PERIOD,
            '/' to KeyEvent.KEYCODE_SLASH,
            '`' to KeyEvent.KEYCODE_GRAVE,
        )

        private val textShiftedKeyCodes = mapOf(
            '!' to KeyEvent.KEYCODE_1,
            '@' to KeyEvent.KEYCODE_2,
            '#' to KeyEvent.KEYCODE_3,
            '$' to KeyEvent.KEYCODE_4,
            '%' to KeyEvent.KEYCODE_5,
            '^' to KeyEvent.KEYCODE_6,
            '&' to KeyEvent.KEYCODE_7,
            '*' to KeyEvent.KEYCODE_8,
            '(' to KeyEvent.KEYCODE_9,
            ')' to KeyEvent.KEYCODE_0,
            '_' to KeyEvent.KEYCODE_MINUS,
            '+' to KeyEvent.KEYCODE_EQUALS,
            '{' to KeyEvent.KEYCODE_LEFT_BRACKET,
            '}' to KeyEvent.KEYCODE_RIGHT_BRACKET,
            '|' to KeyEvent.KEYCODE_BACKSLASH,
            ':' to KeyEvent.KEYCODE_SEMICOLON,
            '"' to KeyEvent.KEYCODE_APOSTROPHE,
            '<' to KeyEvent.KEYCODE_COMMA,
            '>' to KeyEvent.KEYCODE_PERIOD,
            '?' to KeyEvent.KEYCODE_SLASH,
            '~' to KeyEvent.KEYCODE_GRAVE,
        )

        private fun virtualKey(keyCode: Int, unicode: Int): Int? =
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER -> 0x0d
                KeyEvent.KEYCODE_ESCAPE -> 0x1b
                KeyEvent.KEYCODE_DEL -> 0x08
                KeyEvent.KEYCODE_TAB -> 0x09
                KeyEvent.KEYCODE_SPACE -> 0x20
                KeyEvent.KEYCODE_DPAD_LEFT -> 0x25
                KeyEvent.KEYCODE_DPAD_UP -> 0x26
                KeyEvent.KEYCODE_DPAD_RIGHT -> 0x27
                KeyEvent.KEYCODE_DPAD_DOWN -> 0x28
                KeyEvent.KEYCODE_PAGE_UP -> 0x21
                KeyEvent.KEYCODE_PAGE_DOWN -> 0x22
                KeyEvent.KEYCODE_FORWARD_DEL -> 0x2e
                KeyEvent.KEYCODE_INSERT -> 0x2d
                KeyEvent.KEYCODE_MOVE_HOME -> 0x24
                KeyEvent.KEYCODE_MOVE_END -> 0x23
                KeyEvent.KEYCODE_SHIFT_LEFT,
                KeyEvent.KEYCODE_SHIFT_RIGHT,
                -> 0x10
                KeyEvent.KEYCODE_CTRL_LEFT,
                KeyEvent.KEYCODE_CTRL_RIGHT,
                -> 0x11
                KeyEvent.KEYCODE_ALT_LEFT,
                KeyEvent.KEYCODE_ALT_RIGHT,
                -> 0x12
                KeyEvent.KEYCODE_CAPS_LOCK -> 0x14
                KeyEvent.KEYCODE_NUM_LOCK -> 0x90
                KeyEvent.KEYCODE_SCROLL_LOCK -> 0x91
                KeyEvent.KEYCODE_MINUS -> 0xbd
                KeyEvent.KEYCODE_EQUALS -> 0xbb
                KeyEvent.KEYCODE_LEFT_BRACKET -> 0xdb
                KeyEvent.KEYCODE_RIGHT_BRACKET -> 0xdd
                KeyEvent.KEYCODE_BACKSLASH -> 0xdc
                KeyEvent.KEYCODE_SEMICOLON -> 0xba
                KeyEvent.KEYCODE_APOSTROPHE -> 0xde
                KeyEvent.KEYCODE_COMMA -> 0xbc
                KeyEvent.KEYCODE_PERIOD -> 0xbe
                KeyEvent.KEYCODE_SLASH -> 0xbf
                KeyEvent.KEYCODE_GRAVE -> 0xc0
                in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> 0x41 + (keyCode - KeyEvent.KEYCODE_A)
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> 0x30 + (keyCode - KeyEvent.KEYCODE_0)
                in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> 0x60 + (keyCode - KeyEvent.KEYCODE_NUMPAD_0)
                in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> 0x70 + (keyCode - KeyEvent.KEYCODE_F1)
                else -> unicode.takeIf { it in 1..255 }?.let { Character.toUpperCase(it.toChar()).code }
            }

        private fun fallbackScanCode(keyCode: Int): Int? =
            when (keyCode) {
                KeyEvent.KEYCODE_A -> 0x001e
                KeyEvent.KEYCODE_B -> 0x0030
                KeyEvent.KEYCODE_C -> 0x002e
                KeyEvent.KEYCODE_D -> 0x0020
                KeyEvent.KEYCODE_E -> 0x0012
                KeyEvent.KEYCODE_F -> 0x0021
                KeyEvent.KEYCODE_G -> 0x0022
                KeyEvent.KEYCODE_H -> 0x0023
                KeyEvent.KEYCODE_I -> 0x0017
                KeyEvent.KEYCODE_J -> 0x0024
                KeyEvent.KEYCODE_K -> 0x0025
                KeyEvent.KEYCODE_L -> 0x0026
                KeyEvent.KEYCODE_M -> 0x0032
                KeyEvent.KEYCODE_N -> 0x0031
                KeyEvent.KEYCODE_O -> 0x0018
                KeyEvent.KEYCODE_P -> 0x0019
                KeyEvent.KEYCODE_Q -> 0x0010
                KeyEvent.KEYCODE_R -> 0x0013
                KeyEvent.KEYCODE_S -> 0x001f
                KeyEvent.KEYCODE_T -> 0x0014
                KeyEvent.KEYCODE_U -> 0x0016
                KeyEvent.KEYCODE_V -> 0x002f
                KeyEvent.KEYCODE_W -> 0x0011
                KeyEvent.KEYCODE_X -> 0x002d
                KeyEvent.KEYCODE_Y -> 0x0015
                KeyEvent.KEYCODE_Z -> 0x002c
                KeyEvent.KEYCODE_1 -> 0x0002
                KeyEvent.KEYCODE_2 -> 0x0003
                KeyEvent.KEYCODE_3 -> 0x0004
                KeyEvent.KEYCODE_4 -> 0x0005
                KeyEvent.KEYCODE_5 -> 0x0006
                KeyEvent.KEYCODE_6 -> 0x0007
                KeyEvent.KEYCODE_7 -> 0x0008
                KeyEvent.KEYCODE_8 -> 0x0009
                KeyEvent.KEYCODE_9 -> 0x000a
                KeyEvent.KEYCODE_0 -> 0x000b
                KeyEvent.KEYCODE_NUMPAD_7 -> 0x0047
                KeyEvent.KEYCODE_NUMPAD_8 -> 0x0048
                KeyEvent.KEYCODE_NUMPAD_9 -> 0x0049
                KeyEvent.KEYCODE_NUMPAD_4 -> 0x004b
                KeyEvent.KEYCODE_NUMPAD_5 -> 0x004c
                KeyEvent.KEYCODE_NUMPAD_6 -> 0x004d
                KeyEvent.KEYCODE_NUMPAD_1 -> 0x004f
                KeyEvent.KEYCODE_NUMPAD_2 -> 0x0050
                KeyEvent.KEYCODE_NUMPAD_3 -> 0x0051
                KeyEvent.KEYCODE_NUMPAD_0 -> 0x0052
                KeyEvent.KEYCODE_ENTER -> 0x001c
                KeyEvent.KEYCODE_NUMPAD_ENTER -> 0x011c
                KeyEvent.KEYCODE_ESCAPE -> 0x0001
                KeyEvent.KEYCODE_SPACE -> 0x0039
                KeyEvent.KEYCODE_TAB -> 0x000f
                KeyEvent.KEYCODE_DEL -> 0x000e
                KeyEvent.KEYCODE_DPAD_LEFT -> 0x014b
                KeyEvent.KEYCODE_DPAD_UP -> 0x0148
                KeyEvent.KEYCODE_DPAD_RIGHT -> 0x014d
                KeyEvent.KEYCODE_DPAD_DOWN -> 0x0150
                KeyEvent.KEYCODE_PAGE_UP -> 0x0149
                KeyEvent.KEYCODE_PAGE_DOWN -> 0x0151
                KeyEvent.KEYCODE_FORWARD_DEL -> 0x0153
                KeyEvent.KEYCODE_INSERT -> 0x0152
                KeyEvent.KEYCODE_MOVE_HOME -> 0x0147
                KeyEvent.KEYCODE_MOVE_END -> 0x014f
                KeyEvent.KEYCODE_SHIFT_LEFT -> 0x002a
                KeyEvent.KEYCODE_SHIFT_RIGHT -> 0x0036
                KeyEvent.KEYCODE_CTRL_LEFT -> 0x001d
                KeyEvent.KEYCODE_CTRL_RIGHT -> 0x011d
                KeyEvent.KEYCODE_ALT_LEFT -> 0x0038
                KeyEvent.KEYCODE_ALT_RIGHT -> 0x0138
                KeyEvent.KEYCODE_CAPS_LOCK -> 0x003a
                KeyEvent.KEYCODE_NUM_LOCK -> 0x0145
                KeyEvent.KEYCODE_SCROLL_LOCK -> 0x0046
                KeyEvent.KEYCODE_MINUS -> 0x000c
                KeyEvent.KEYCODE_EQUALS -> 0x000d
                KeyEvent.KEYCODE_LEFT_BRACKET -> 0x001a
                KeyEvent.KEYCODE_RIGHT_BRACKET -> 0x001b
                KeyEvent.KEYCODE_BACKSLASH -> 0x002b
                KeyEvent.KEYCODE_SEMICOLON -> 0x0027
                KeyEvent.KEYCODE_APOSTROPHE -> 0x0028
                KeyEvent.KEYCODE_COMMA -> 0x0033
                KeyEvent.KEYCODE_PERIOD -> 0x0034
                KeyEvent.KEYCODE_SLASH -> 0x0035
                KeyEvent.KEYCODE_GRAVE -> 0x0029
                else -> null
            }
    }
}

private fun timestampUs(): Long = SystemClock.elapsedRealtimeNanos() / 1000L

/**
 * WebRTC's low-latency AudioTrack path can race teardown and dereference a released AudioTrack.
 * Stable buffering is preferable to a process crash on both handheld and TV devices.
 */
internal fun shouldUseLowLatencyStreamAudio(
    @Suppress("UNUSED_PARAMETER") androidTvProfile: Boolean,
): Boolean = false

internal fun shouldRunControllerMouseLoop(
    controllerMouseAssistActive: Boolean,
    controllerMouseEmulationActive: Boolean,
): Boolean = controllerMouseAssistActive || controllerMouseEmulationActive

internal fun shouldCaptureMicrophone(
    mode: MicrophoneMode,
    permissionGranted: Boolean,
): Boolean = mode != MicrophoneMode.Disabled && permissionGranted

internal fun isDisposedRtpSenderFailure(error: IllegalStateException): Boolean =
    error.message == "RtpSender has been disposed."

internal fun advancedCodecRestartSettleDelayMs(codec: VideoCodec, hadStableMedia: Boolean): Long =
    if (hadStableMedia && codec != VideoCodec.H264) ANDROID_CODEC_RESTART_SETTLE_MS else 0L

private const val GFN_MICROPHONE_MID = "3"
private const val MICROPHONE_STREAM_ID = "mic"
private const val MICROPHONE_TRACK_ID = "mic"
private const val DEFAULT_INPUT_PROTOCOL_VERSION = 2
private const val INPUT_HANDSHAKE_MARKER = 0x0e
private const val INPUT_HANDSHAKE_MAGIC_WORD = 526
private const val ICE_DISCONNECTED_GRACE_MS = 3500L
private const val ICE_FAILED_RECONNECT_DELAY_MS = 250L
private const val SIGNALING_RECONNECT_DELAY_MS = 1000L
private const val ANDROID_CODEC_RESTART_SETTLE_MS = 180L
private const val MAX_TRANSPORT_RECONNECT_ATTEMPTS = 3
private const val OFFER_TIMEOUT_MS = 12_000L
private const val MEDIA_STALL_KEYFRAME_AFTER_MS = 5_000L
private const val MEDIA_STALL_KEYFRAME_INTERVAL_MS = 2_500L
private const val MEDIA_STALL_RESTART_AFTER_MS = 10_000L
// Low-power TV MediaCodec implementations can open an advanced decoder several
// seconds before they produce their first frame. Keep the pre-TV-optimization
// startup window so a slow H.265/AV1 decoder is not mistaken for a dead one and
// immediately replaced by the safe-codec profile.
private const val TV_MEDIA_STALL_KEYFRAME_AFTER_MS = 5_000L
private const val TV_MEDIA_STALL_KEYFRAME_INTERVAL_MS = 2_500L
private const val TV_MEDIA_STALL_RESTART_AFTER_MS = 14_000L
private const val FIRST_VIDEO_FRAME_TIMEOUT_MS = 8_000L
private const val STABLE_TRANSPORT_PROGRESS_SAMPLES = 3
private const val CATASTROPHIC_DECODED_AREA_DIVISOR = 8L
private const val GAMEPAD_GUIDE_AUTO_RELEASE_MS = 160L
private const val STEAM_MENU_MODIFIER_DELAY_MS = 40L
private const val STREAM_TEXT_SEND_MAX_CHARS = 4096
private const val STREAM_TEXT_SEND_ATTEMPTS = 3
private const val STREAM_TEXT_PACKET_DELAY_MS = 4L
private const val STREAM_TEXT_KEY_DELAY_MS = 10L
private const val STREAM_TEXT_RETRY_DELAY_MS = 16L
private const val BYTES_PER_MEBIBYTE = 1024L * 1024L
private const val LOW_POWER_TV_MEMORY_LIMIT_BYTES = 3L * 1024L * BYTES_PER_MEBIBYTE

private fun Any?.statsDouble(): Double? =
    when (this) {
        is Number -> toDouble()
        is String -> toDoubleOrNull()
        else -> null
    }

private fun Any?.statsLong(): Long? =
    when (this) {
        is Number -> toLong()
        is String -> toLongOrNull()
        else -> null
    }
