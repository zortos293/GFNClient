package com.opencloudgaming.opennow

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock

internal class AndroidNerdAudioController(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioThread = HandlerThread("opennow-cue-audio").apply { start() }
    private val audioHandler = Handler(audioThread.looper)
    private var cuePlayer: MediaPlayer? = null
    private var cuePurpose: MusicCuePurpose? = null
    private var cuePlayingChanged: ((Boolean) -> Unit)? = null
    private var cuePaused = false
    private var cueRemainingDurationMs = 0L
    private var cueStopsAtUptimeMs = 0L
    private var toneGenerator: ToneGenerator? = null
    private var lastToneAtMs = 0L
    @Volatile private var released = false
    private val stopCueRunnable = Runnable {
        stopMusicCueInternal(cuePlayingChanged)
    }

    fun startIntro(enabled: Boolean, onPlayingChanged: (Boolean) -> Unit) {
        postAudio {
            startMusicCueInternal(
                purpose = MusicCuePurpose.Intro,
                enabled = enabled,
                maxDurationMs = INTRO_MUSIC_MAX_DURATION_MS,
                onPlayingChanged = onPlayingChanged,
            )
        }
    }

    fun startQueueReadyReminder(enabled: Boolean, onPlayingChanged: (Boolean) -> Unit = {}) {
        postAudio {
            startMusicCueInternal(
                purpose = MusicCuePurpose.QueueReady,
                enabled = enabled,
                maxDurationMs = QUEUE_READY_CUE_DURATION_MS,
                onPlayingChanged = onPlayingChanged,
            )
        }
    }

    private fun startMusicCueInternal(
        purpose: MusicCuePurpose,
        enabled: Boolean,
        maxDurationMs: Long,
        onPlayingChanged: (Boolean) -> Unit,
    ) {
        stopMusicCueInternal(cuePlayingChanged)
        if (!enabled || released) return

        val descriptor = runCatching {
            appContext.resources.openRawResourceFd(musicCueResource(purpose))
        }.getOrNull() ?: return
        val player = MediaPlayer()
        runCatching {
            descriptor.use {
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                player.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            player.setVolume(MUSIC_CUE_VOLUME, MUSIC_CUE_VOLUME)
            player.isLooping = false
            player.setOnPreparedListener { prepared ->
                if (cuePlayer !== prepared || released) {
                    runCatching { prepared.release() }
                    return@setOnPreparedListener
                }
                runCatching {
                    prepared.start()
                    cuePaused = false
                    cueRemainingDurationMs = maxDurationMs
                    scheduleCueStop(maxDurationMs)
                }.onSuccess {
                    notifyPlaying(onPlayingChanged, true)
                }.onFailure {
                    finishMusicCue(prepared, onPlayingChanged)
                }
            }
            player.setOnCompletionListener { completed ->
                finishMusicCue(completed, onPlayingChanged)
            }
            player.setOnErrorListener { failed, _, _ ->
                finishMusicCue(failed, onPlayingChanged)
                true
            }
            cuePlayer = player
            cuePurpose = purpose
            cuePlayingChanged = onPlayingChanged
            cuePaused = false
            cueRemainingDurationMs = maxDurationMs
            player.prepareAsync()
        }.onFailure {
            if (cuePlayer === player) clearMusicCueState()
            runCatching { player.release() }
            notifyPlaying(onPlayingChanged, false)
        }
    }

    fun stopIntro(onPlayingChanged: (Boolean) -> Unit = {}) {
        postAudio {
            if (cuePurpose == MusicCuePurpose.Intro) {
                stopMusicCueInternal(onPlayingChanged)
            }
        }
    }

    fun stopQueueReadyReminder(onPlayingChanged: (Boolean) -> Unit = {}) {
        postAudio {
            if (cuePurpose == MusicCuePurpose.QueueReady) {
                stopMusicCueInternal(onPlayingChanged)
            }
        }
    }

    fun stopAll(onPlayingChanged: (Boolean) -> Unit = {}) {
        postAudio { stopMusicCueInternal(onPlayingChanged) }
    }

    fun pauseAll(onPlayingChanged: (Boolean) -> Unit = {}) {
        postAudio {
            val player = cuePlayer ?: return@postAudio
            if (cuePaused) return@postAudio
            val playing = runCatching { player.isPlaying }.getOrElse {
                stopMusicCueInternal(onPlayingChanged)
                return@postAudio
            }
            if (!playing) return@postAudio
            cueRemainingDurationMs = (cueStopsAtUptimeMs - SystemClock.uptimeMillis()).coerceAtLeast(0L)
            audioHandler.removeCallbacks(stopCueRunnable)
            runCatching { player.pause() }
                .onSuccess {
                    cuePaused = true
                    notifyPlaying(onPlayingChanged, false)
                }
                .onFailure { stopMusicCueInternal(onPlayingChanged) }
        }
    }

    fun resumeAll(onPlayingChanged: (Boolean) -> Unit = {}) {
        postAudio {
            val player = cuePlayer ?: return@postAudio
            if (!cuePaused) return@postAudio
            if (cueRemainingDurationMs <= 0L) {
                stopMusicCueInternal(onPlayingChanged)
                return@postAudio
            }
            runCatching { player.start() }
                .onSuccess {
                    cuePaused = false
                    scheduleCueStop(cueRemainingDurationMs)
                    notifyPlaying(onPlayingChanged, true)
                }
                .onFailure { stopMusicCueInternal(onPlayingChanged) }
        }
    }

    private fun scheduleCueStop(delayMs: Long) {
        cueRemainingDurationMs = delayMs.coerceAtLeast(0L)
        cueStopsAtUptimeMs = SystemClock.uptimeMillis() + cueRemainingDurationMs
        audioHandler.removeCallbacks(stopCueRunnable)
        audioHandler.postDelayed(stopCueRunnable, cueRemainingDurationMs)
    }

    private fun finishMusicCue(player: MediaPlayer, onPlayingChanged: (Boolean) -> Unit) {
        if (cuePlayer !== player) {
            runCatching { player.release() }
            return
        }
        clearMusicCueState()
        runCatching { player.release() }
        notifyPlaying(onPlayingChanged, false)
    }

    private fun stopMusicCueInternal(onPlayingChanged: ((Boolean) -> Unit)? = null) {
        val player = cuePlayer ?: return
        val callback = onPlayingChanged ?: cuePlayingChanged
        clearMusicCueState()
        runCatching { player.stop() }
        runCatching { player.release() }
        callback?.let { notifyPlaying(it, false) }
    }

    private fun clearMusicCueState() {
        cuePlayer = null
        cuePurpose = null
        cuePlayingChanged = null
        cuePaused = false
        cueRemainingDurationMs = 0L
        cueStopsAtUptimeMs = 0L
        audioHandler.removeCallbacks(stopCueRunnable)
    }

    fun playButtonTone(enabled: Boolean) {
        if (!enabled) return
        postAudio {
            val now = SystemClock.uptimeMillis()
            if (now - lastToneAtMs < MIN_BUTTON_TONE_INTERVAL_MS) return@postAudio
            lastToneAtMs = now
            val generator = toneGenerator ?: runCatching {
                ToneGenerator(AudioManager.STREAM_MUSIC, BUTTON_TONE_VOLUME)
            }.getOrNull()?.also {
                toneGenerator = it
            } ?: return@postAudio
            runCatching {
                generator.startTone(ToneGenerator.TONE_PROP_ACK, BUTTON_TONE_DURATION_MS)
            }
        }
    }

    fun release() {
        if (released) return
        released = true
        audioHandler.post {
            stopMusicCueInternal(cuePlayingChanged)
            toneGenerator?.release()
            toneGenerator = null
            audioThread.quitSafely()
        }
    }

    private fun postAudio(command: () -> Unit) {
        if (released) return
        runCatching { audioHandler.post(command) }
    }

    private fun notifyPlaying(callback: (Boolean) -> Unit, playing: Boolean) {
        mainHandler.post { callback(playing) }
    }

    private enum class MusicCuePurpose {
        Intro,
        QueueReady,
    }

    private fun musicCueResource(purpose: MusicCuePurpose): Int =
        when (purpose) {
            MusicCuePurpose.Intro -> R.raw.nerd_stream_intro
            MusicCuePurpose.QueueReady -> R.raw.nerd_queue_ready
        }

    private companion object {
        private const val MUSIC_CUE_VOLUME = 0.20f
        private const val INTRO_MUSIC_MAX_DURATION_MS = 150_000L
        private const val QUEUE_READY_CUE_DURATION_MS = 7_000L
        private const val BUTTON_TONE_VOLUME = 34
        private const val BUTTON_TONE_DURATION_MS = 48
        private const val MIN_BUTTON_TONE_INTERVAL_MS = 55L
    }
}
