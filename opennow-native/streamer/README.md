# OpenNOW WebRTC demo runtime

The runtime is a restartable JSON-lines child process. Its demo creates two local GStreamer `webrtcbin` peers, exchanges SDP and ICE in-process, sends a live VP8 test source over SRTP, decodes it, and presents it through a native video sink.

The 2 ms jitter-buffer target and one-buffer leaky queues are prototype low-latency settings. They are deliberately explicit rather than relying on `webrtcbin`'s general-purpose defaults.

## Commands

```json
{"type":"hello"}
{"type":"start-demo","quality":"1080p120"}
{"type":"set-quality","quality":"4k60"}
{"type":"ping"}
{"type":"stop"}
{"type":"shutdown"}
```

Runtime events are emitted as one JSON object per stdout line. Diagnostics go to stderr so they cannot corrupt the control protocol.
