package com.opencloudgaming.opennow

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.SystemClock
import android.view.Surface
import org.webrtc.EncodedImage
import org.webrtc.VideoCodecStatus
import org.webrtc.VideoDecoder
import org.webrtc.VideoFrame
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * A receive-only opaque frame. WebRTC schedules its presentation and records decoder statistics;
 * pixels remain in the MediaCodec output buffer until the sink presents it. Dropped frames release
 * the buffer without displaying it. There is deliberately no lossy 8-bit I420 readback of HDR.
 */
internal class HdrSurfaceBuffer(
    private val width: Int,
    private val height: Int,
    private val finish: (Boolean) -> Boolean,
) : VideoFrame.Buffer {
    private val references = AtomicInteger(1)
    private val finished = AtomicBoolean(false)
    override fun getWidth() = width
    override fun getHeight() = height
    override fun toI420(): VideoFrame.I420Buffer? = null
    override fun retain() { check(references.incrementAndGet() > 1) }
    override fun release() {
        if (references.decrementAndGet() == 0 && finished.compareAndSet(false, true)) finish(false)
    }
    fun present(): Boolean = finished.compareAndSet(false, true) && finish(true)
    override fun cropAndScale(cropX: Int, cropY: Int, cropWidth: Int, cropHeight: Int,
        scaleWidth: Int, scaleHeight: Int): VideoFrame.Buffer {
        // Receive sinks must render the complete HDR frame; view layout owns aspect fitting.
        require(cropX == 0 && cropY == 0 && cropWidth == width && cropHeight == height &&
            scaleWidth == width && scaleHeight == height)
        retain()
        return this
    }
}

internal class HdrSurfaceTarget(val surface: Surface)

internal class HdrSurfaceVideoDecoder(
    private val fps: Int,
    private val surface: () -> HdrSurfaceTarget?,
) : VideoDecoder {
    private data class FrameInfo(val timestampNs: Long, val rotation: Int, val queuedAtMs: Long)
    private data class PendingFrame(val image: EncodedImage, val queuedAtMs: Long)
    private val pendingFrames = java.util.ArrayDeque<PendingFrame>()
    private val lock = Any()
    private var codec: MediaCodec? = null
    private var outputSurface: HdrSurfaceTarget? = null
    private var callback: VideoDecoder.Callback? = null
    private var width = 0
    private var height = 0
    private var outputWidth = 0
    private var outputHeight = 0
    private var rotation = 0
    private var generation = 0
    private var nextPtsUs = 0L
    private val frames = mutableMapOf<Long, FrameInfo>()
    private var needsKeyFrame = true
    private var failed = false
    @Volatile private var running = false
    private var outputThread: Thread? = null

    override fun initDecode(settings: VideoDecoder.Settings?, decodeCallback: VideoDecoder.Callback?): VideoCodecStatus {
        if (settings == null || decodeCallback == null) return VideoCodecStatus.ERR_PARAMETER
        synchronized(lock) {
            width = settings.width
            height = settings.height
            callback = decodeCallback
            failed = false
            needsKeyFrame = true
        }
        running = true
        outputThread = Thread({
            while (running) {
                val delivered = drainOutput()
                if (!delivered) SystemClock.sleep(2)
            }
        }, "OpenNOW-HDR-output").also { it.start() }
        return VideoCodecStatus.OK
    }

    override fun decode(frame: EncodedImage?, info: VideoDecoder.DecodeInfo?): VideoCodecStatus = synchronized(lock) {
        if (!running || frame == null) return@synchronized VideoCodecStatus.UNINITIALIZED
        val target = surface()?.takeIf { it.surface.isValid }
        if (target == null) {
            stopCodecLocked()
            return@synchronized VideoCodecStatus.NO_OUTPUT
        }
        val nextWidth = frame.encodedWidth.takeIf { it > 0 } ?: width
        val nextHeight = frame.encodedHeight.takeIf { it > 0 } ?: height
        if (failed || target !== outputSurface || nextWidth != width || nextHeight != height || frame.rotation != rotation) stopCodecLocked()
        if (needsKeyFrame && frame.frameType != EncodedImage.FrameType.VideoFrameKey) {
            return@synchronized VideoCodecStatus.ERROR
        }
        try {
            if (codec == null) {
                val name = StreamHdr.decoderName(nextWidth, nextHeight, fps)
                    ?: return@synchronized VideoCodecStatus.ERROR
                width = nextWidth
                height = nextHeight
                rotation = frame.rotation
                outputWidth = width
                outputHeight = height
                // Retain the instance before configure so failures still release it.
                codec = MediaCodec.createByCodecName(name)
                codec!!.configure(StreamHdr.format(width, height, fps).apply {
                    setInteger(MediaFormat.KEY_ROTATION, rotation)
                }, target.surface, null, 0)
                codec!!.start()
                outputSurface = target
                failed = false
                NativeInputDiagnostics.add("HDR direct surface decoder=$name size=${width}x$height transfer=PQ color=BT2020")
            }
            if (frames.size + pendingFrames.size >= 32) {
                failed = true
                return@synchronized VideoCodecStatus.ERROR
            }
            // Startup and surface recreation can briefly have no input buffers. Retain the
            // encoded frame for the worker instead of dropping it and starting a keyframe storm.
            frame.retain()
            pendingFrames.addLast(PendingFrame(frame, SystemClock.elapsedRealtime()))
            needsKeyFrame = false
            VideoCodecStatus.OK
        } catch (error: Exception) {
            failed = true
            NativeInputDiagnostics.add("HDR decoder input failed: ${error.javaClass.simpleName}")
            VideoCodecStatus.ERROR
        }
    }

    private fun drainOutput(): Boolean {
        var decoded: VideoFrame? = null
        var decodeTime = 0
        var deliveryCallback: VideoDecoder.Callback? = null
        synchronized(lock) {
            val decoder = codec ?: return false
            if (failed) return false
            try {
                feedInputLocked(decoder)
                val output = MediaCodec.BufferInfo()
                val index = decoder.dequeueOutputBuffer(output, 0)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val format = decoder.outputFormat
                    outputWidth = croppedDimension(format, "crop-left", "crop-right", MediaFormat.KEY_WIDTH, width)
                    outputHeight = croppedDimension(format, "crop-top", "crop-bottom", MediaFormat.KEY_HEIGHT, height)
                    // Fail closed if the decoder reports an SDR conversion. Sending that image to
                    // an HDR surface as PQ would produce incorrect brightness and white levels.
                    val standard = format.integerOrNull(MediaFormat.KEY_COLOR_STANDARD)
                    val transfer = format.integerOrNull(MediaFormat.KEY_COLOR_TRANSFER)
                    if (!hdrOutputColorSupported(standard, transfer)) {
                        failed = true
                        NativeInputDiagnostics.add("HDR decoder rejected output standard=$standard transfer=$transfer")
                        return false
                    }
                    NativeInputDiagnostics.add("HDR decoder output format=$format")
                    return true
                }
                if (index < 0) return false
                val frame = frames.remove(output.presentationTimeUs)
                if (frame == null || output.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    decoder.releaseOutputBuffer(index, false)
                    return true
                }
                val frameGeneration = generation
                val buffer = HdrSurfaceBuffer(outputWidth, outputHeight) { render ->
                    synchronized(lock) {
                        if (codec !== decoder || generation != frameGeneration) false
                        else runCatching {
                            val present = render && surface() === outputSurface && outputSurface?.surface?.isValid == true
                            decoder.releaseOutputBuffer(index, present)
                            present
                        }.getOrDefault(false)
                    }
                }
                decoded = VideoFrame(buffer, frame.rotation, frame.timestampNs)
                decodeTime = (SystemClock.elapsedRealtime() - frame.queuedAtMs).toInt()
                deliveryCallback = callback
            } catch (error: Exception) {
                failed = true
                NativeInputDiagnostics.add("HDR decoder output failed: ${error.javaClass.simpleName}")
            }
        }
        val frame = decoded ?: return false
        try { deliveryCallback?.onDecodedFrame(frame, decodeTime, null) }
        finally { frame.release() }
        return true
    }

    private fun feedInputLocked(decoder: MediaCodec) {
        if (pendingFrames.isEmpty()) return
        val index = decoder.dequeueInputBuffer(0)
        if (index < 0) return
        val pending = pendingFrames.removeFirst()
        val image = pending.image
        try {
            val input = decoder.getInputBuffer(index) ?: error("Missing HDR input buffer")
            input.clear()
            val encoded = image.buffer.duplicate()
            val size = encoded.remaining()
            require(size <= input.remaining()) { "HDR input exceeds codec buffer" }
            input.put(encoded)
            nextPtsUs = maxOf(nextPtsUs + 1, image.captureTimeNs / 1000)
            frames[nextPtsUs] = FrameInfo(image.captureTimeNs, image.rotation, pending.queuedAtMs)
            decoder.queueInputBuffer(index, 0, size, nextPtsUs, 0)
        } finally {
            image.release()
        }
    }

    private fun MediaFormat.integerOrNull(key: String): Int? =
        if (containsKey(key)) getInteger(key) else null

    private fun croppedDimension(format: MediaFormat, start: String, end: String, size: String, fallback: Int): Int =
        if (format.containsKey(start) && format.containsKey(end)) format.getInteger(end) - format.getInteger(start) + 1
        else if (format.containsKey(size)) format.getInteger(size) else fallback

    private fun stopCodecLocked() {
        generation++
        val previous = codec
        codec = null
        outputSurface = null
        frames.clear()
        while (pendingFrames.isNotEmpty()) pendingFrames.removeFirst().image.release()
        needsKeyFrame = true
        if (previous != null) {
            runCatching { previous.stop() }
            runCatching { previous.release() }
        }
    }

    override fun release(): VideoCodecStatus {
        running = false
        outputThread?.join(1000)
        if (outputThread?.isAlive == true) return VideoCodecStatus.TIMEOUT
        synchronized(lock) {
            stopCodecLocked()
            callback = null
        }
        outputThread = null
        return VideoCodecStatus.OK
    }

    override fun getImplementationName() = "OpenNOW-MediaCodec-HDR10-Surface"
}
