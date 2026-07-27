interface PeerMediaLifecycleDependencies {
  videoElement: HTMLVideoElement;
  audioElement: HTMLAudioElement;
  onRenderFrame: () => void;
  log: (message: string) => void;
}

export class PeerMediaLifecycleController {
  private readonly videoStream = new MediaStream();
  private readonly audioStream = new MediaStream();
  private audioContext: AudioContext | null = null;
  private audioSourceNode: MediaStreamAudioSourceNode | null = null;
  private audioGainNode: GainNode | null = null;
  private outputVolume = 1;

  constructor(private readonly dependencies: PeerMediaLifecycleDependencies) {
    dependencies.videoElement.srcObject = this.videoStream;
    dependencies.audioElement.srcObject = this.audioStream;
    dependencies.audioElement.muted = true;
    dependencies.audioElement.volume = this.outputVolume;
  }

  getVideoTrack(): MediaStreamTrack | null {
    return this.videoStream.getVideoTracks()[0] ?? null;
  }

  attachTrack(track: MediaStreamTrack): void {
    if (track.kind === "video") {
      this.replaceTrackInStream(this.videoStream, track);
      const video = this.dependencies.videoElement;
      const frameCallback = () => {
        this.dependencies.onRenderFrame();
        if (this.videoStream.active) {
          video.requestVideoFrameCallback(frameCallback);
        }
      };
      video.requestVideoFrameCallback(frameCallback);

      this.dependencies.log(
        `Video element before play: paused=${video.paused}, readyState=${video.readyState}, size=${video.videoWidth}x${video.videoHeight}`,
      );
      video
        .play()
        .then(() => {
          this.dependencies.log("Video element playback started");
        })
        .catch((playError) => {
          this.dependencies.log(`Video play() failed: ${String(playError)}`);
        });
      window.setTimeout(() => {
        this.dependencies.log(
          `Video element post-play: paused=${video.paused}, readyState=${video.readyState}, size=${video.videoWidth}x${video.videoHeight}`,
        );
      }, 1500);

      track.onunmute = () => {
        this.dependencies.log("Video track unmuted");
      };
      track.onmute = () => {
        this.dependencies.log("Warning: video track muted by sender");
      };
      track.onended = () => {
        this.dependencies.log("Warning: video track ended");
      };
      this.dependencies.log("Video track attached");
      return;
    }

    if (track.kind === "audio") {
      this.replaceTrackInStream(this.audioStream, track);
      this.cleanupAudioRouting();

      let audioContext: AudioContext | null = null;
      let audioSourceNode: MediaStreamAudioSourceNode | null = null;
      let audioGainNode: GainNode | null = null;
      try {
        audioContext = new AudioContext({
          latencyHint: "interactive",
          sampleRate: 48000,
        });
        audioSourceNode = audioContext.createMediaStreamSource(this.audioStream);
        audioGainNode = audioContext.createGain();
        audioGainNode.gain.value = this.outputVolume;
        audioSourceNode.connect(audioGainNode);
        audioGainNode.connect(audioContext.destination);
        if (audioContext.state === "suspended") {
          void audioContext.resume();
        }
        this.audioContext = audioContext;
        this.audioSourceNode = audioSourceNode;
        this.audioGainNode = audioGainNode;
        this.dependencies.log(
          `Audio routed through AudioContext (latency: ${(audioContext.baseLatency * 1000).toFixed(1)}ms, sampleRate: ${audioContext.sampleRate}Hz)`,
        );
      } catch (error) {
        if (audioSourceNode) {
          try {
            audioSourceNode.disconnect();
          } catch {
            // Ignore cleanup errors from a partially-created node.
          }
        }
        if (audioGainNode) {
          try {
            audioGainNode.disconnect();
          } catch {
            // Ignore cleanup errors from a partially-created node.
          }
        }
        if (audioContext) {
          void audioContext.close().catch(() => {});
        }
        this.startDirectAudioPlayback(
          `AudioContext creation failed, falling back to audio element: ${String(error)}`,
        );
      }
    }
  }

  setOutputVolume(volume: number): void {
    this.outputVolume = Math.max(
      0,
      Math.min(1, Number.isFinite(volume) ? volume : 1),
    );
    this.dependencies.audioElement.volume = this.outputVolume;
    if (this.audioGainNode) {
      this.audioGainNode.gain.value = this.outputVolume;
    }
  }

  reset(): void {
    this.cleanupAudioRouting();
    this.clearTracks();
  }

  cleanupAudio(): void {
    this.cleanupAudioRouting();
  }

  clearTracks(): void {
    for (const track of this.videoStream.getTracks()) {
      this.videoStream.removeTrack(track);
    }
    for (const track of this.audioStream.getTracks()) {
      this.audioStream.removeTrack(track);
    }
  }

  private replaceTrackInStream(
    stream: MediaStream,
    track: MediaStreamTrack,
  ): void {
    const existingTracks = track.kind === "video"
      ? stream.getVideoTracks()
      : stream.getAudioTracks();
    for (const existingTrack of existingTracks) {
      stream.removeTrack(existingTrack);
    }
    stream.addTrack(track);
  }

  private cleanupAudioRouting(): void {
    if (this.audioSourceNode) {
      try {
        this.audioSourceNode.disconnect();
      } catch {
        // Ignore cleanup errors from an already-disconnected node.
      }
      this.audioSourceNode = null;
    }
    if (this.audioGainNode) {
      try {
        this.audioGainNode.disconnect();
      } catch {
        // Ignore cleanup errors from an already-disconnected node.
      }
      this.audioGainNode = null;
    }
    if (this.audioContext) {
      void this.audioContext.close().catch(() => {});
      this.audioContext = null;
    }
    this.dependencies.audioElement.pause();
    this.dependencies.audioElement.muted = true;
  }

  private startDirectAudioPlayback(reason: string): void {
    this.dependencies.log(reason);
    this.dependencies.audioElement.muted = false;
    this.dependencies.audioElement.volume = this.outputVolume;
    this.dependencies.audioElement
      .play()
      .then(() => {
        this.dependencies.log("Audio track attached (fallback)");
      })
      .catch((playError) => {
        this.dependencies.log(`Audio autoplay blocked: ${String(playError)}`);
      });
  }
}
