import path from "node:path";
import { Config } from "@remotion/cli/config";

Config.setVideoImageFormat("jpeg");
Config.setOverwriteOutput(true);
Config.setCodec("h264");

// GPU encoding (NVENC): CRF is not supported with hardware encoders, so use a
// high constant bitrate instead. tools/ffmpeg-nvenc is the Remotion compositor
// with its ffmpeg swapped for a full NVENC-capable build (see README).
Config.setHardwareAcceleration("if-possible");
Config.setVideoBitrate("12M");
Config.setBinariesDirectory(path.join(process.cwd(), "tools", "ffmpeg-nvenc"));

// The NVENC ffmpeg build is GPL and lacks libfdk_aac (Remotion's AAC encoder),
// so embed MP3 audio instead. YouTube re-encodes audio on upload either way.
Config.setAudioCodec("mp3");
Config.setAudioBitrate("320k");

Config.setConcurrency(16);
Config.setChromiumOpenGlRenderer("angle");
