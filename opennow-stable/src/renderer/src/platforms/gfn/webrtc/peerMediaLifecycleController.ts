interface PeerMediaLifecycleDependencies {
  videoElement: HTMLVideoElement;
  audioElement: HTMLAudioElement;
  onRenderFrame: () => void;
  log: (message: string) => void;
  createAudioContext?: () => AudioContext;
}

export class PeerMediaLifecycleController {
  private readonly videoStream = new MediaStream();
  private readonly audioStream = new MediaStream();
  private audioContext: AudioContext | null = null;
  private audioSourceNode: MediaStreamAudioSourceNode | null = null;
  private audioGainNode: GainNode | null = null;
  private audioRoutingGeneration = 0;
  private videoFrameCallbackId: number | null = null;
  private videoFrameGeneration = 0;
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
      this.stopVideoFrameCallback();
      this.replaceTrackInStream(this.videoStream, track);
      const video = this.dependencies.videoElement;
      const generation = this.videoFrameGeneration;
      const frameCallback = () => {
        if (generation !== this.videoFrameGeneration || this.getVideoTrack() !== track) {
          return;
        }
        this.dependencies.onRenderFrame();
        this.videoFrameCallbackId = video.requestVideoFrameCallback(frameCallback);
      };
      this.videoFrameCallbackId = video.requestVideoFrameCallback(frameCallback);

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
      this.cleanupAudioRouting();
      this.replaceTrackInStream(this.audioStream, track);
      const generation = this.audioRoutingGeneration;

      let audioContext: AudioContext | null = null;
      let audioSourceNode: MediaStreamAudioSourceNode | null = null;
      let audioGainNode: GainNode | null = null;
      try {
        audioContext = this.dependencies.createAudioContext?.() ?? new AudioContext({
          latencyHint: "interactive",
          sampleRate: 48000,
        });
        audioSourceNode = audioContext.createMediaStreamSource(this.audioStream);
        audioGainNode = audioContext.createGain();
        audioGainNode.gain.value = this.outputVolume;
        audioSourceNode.connect(audioGainNode);
        audioGainNode.connect(audioContext.destination);
        this.audioContext = audioContext;
        this.audioSourceNode = audioSourceNode;
        this.audioGainNode = audioGainNode;
        if (audioContext.state === "running") {
          this.logAudioContextRouting(audioContext);
        } else {
          void this.resumeAudioContext(
            audioContext,
            audioSourceNode,
            audioGainNode,
            track,
            generation,
          );
        }
      } catch (error) {
        if (this.audioContext === audioContext) {
          this.audioContext = null;
          this.audioSourceNode = null;
          this.audioGainNode = null;
        }
        this.releaseAudioGraph(audioContext, audioSourceNode, audioGainNode);
        if (this.isCurrentAudioTrack(track, generation)) {
          this.startDirectAudioPlayback(
            `AudioContext creation failed, falling back to audio element: ${String(error)}`,
            track,
            generation,
          );
        }
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
    this.clearTracks();
  }

  cleanupAudio(): void {
    this.cleanupAudioRouting();
  }

  clearTracks(): void {
    this.cleanupAudioRouting();
    this.stopVideoFrameCallback();
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

  private stopVideoFrameCallback(): void {
    this.videoFrameGeneration++;
    if (this.videoFrameCallbackId === null) {
      return;
    }
    this.dependencies.videoElement.cancelVideoFrameCallback(this.videoFrameCallbackId);
    this.videoFrameCallbackId = null;
  }

  private cleanupAudioRouting(): void {
    this.audioRoutingGeneration++;
    const audioContext = this.audioContext;
    const audioSourceNode = this.audioSourceNode;
    const audioGainNode = this.audioGainNode;
    this.audioContext = null;
    this.audioSourceNode = null;
    this.audioGainNode = null;
    this.releaseAudioGraph(audioContext, audioSourceNode, audioGainNode);
    this.dependencies.audioElement.pause();
    this.dependencies.audioElement.muted = true;
  }

  private releaseAudioGraph(
    audioContext: AudioContext | null,
    audioSourceNode: MediaStreamAudioSourceNode | null,
    audioGainNode: GainNode | null,
  ): void {
    if (audioSourceNode) {
      try {
        audioSourceNode.disconnect();
      } catch {
        // Ignore cleanup errors from an already-disconnected node.
      }
    }
    if (audioGainNode) {
      try {
        audioGainNode.disconnect();
      } catch {
        // Ignore cleanup errors from an already-disconnected node.
      }
    }
    if (audioContext) {
      try {
        void audioContext.close().catch(() => {});
      } catch {
        // Ignore cleanup errors from a partially-created context.
      }
    }
  }

  private async resumeAudioContext(
    audioContext: AudioContext,
    audioSourceNode: MediaStreamAudioSourceNode,
    audioGainNode: GainNode,
    track: MediaStreamTrack,
    generation: number,
  ): Promise<void> {
    let resumeError: unknown = null;
    try {
      await audioContext.resume();
    } catch (error) {
      resumeError = error;
    }

    if (
      !this.isCurrentAudioTrack(track, generation) ||
      this.audioContext !== audioContext ||
      this.audioSourceNode !== audioSourceNode ||
      this.audioGainNode !== audioGainNode
    ) {
      return;
    }

    if (audioContext.state === "running") {
      this.logAudioContextRouting(audioContext);
      return;
    }

    this.audioContext = null;
    this.audioSourceNode = null;
    this.audioGainNode = null;
    const failedState = audioContext.state;
    this.releaseAudioGraph(audioContext, audioSourceNode, audioGainNode);
    const reason = resumeError
      ? `AudioContext resume failed, falling back to audio element: ${String(resumeError)}`
      : `AudioContext remained ${failedState} after resume, falling back to audio element`;
    this.startDirectAudioPlayback(reason, track, generation);
  }

  private logAudioContextRouting(audioContext: AudioContext): void {
    this.dependencies.log(
      `Audio routed through AudioContext (latency: ${(audioContext.baseLatency * 1000).toFixed(1)}ms, sampleRate: ${audioContext.sampleRate}Hz)`,
    );
  }

  private isCurrentAudioTrack(track: MediaStreamTrack, generation: number): boolean {
    return (
      generation === this.audioRoutingGeneration &&
      this.audioStream.getAudioTracks()[0] === track
    );
  }

  private startDirectAudioPlayback(
    reason: string,
    track: MediaStreamTrack,
    generation: number,
  ): void {
    if (!this.isCurrentAudioTrack(track, generation)) {
      return;
    }
    this.dependencies.log(reason);
    this.dependencies.audioElement.muted = false;
    this.dependencies.audioElement.volume = this.outputVolume;
    this.dependencies.audioElement
      .play()
      .then(() => {
        if (this.isCurrentAudioTrack(track, generation)) {
          this.dependencies.log("Audio track attached (fallback)");
        }
      })
      .catch((playError) => {
        if (this.isCurrentAudioTrack(track, generation)) {
          this.dependencies.log(`Audio autoplay blocked: ${String(playError)}`);
        }
      });
  }
}
