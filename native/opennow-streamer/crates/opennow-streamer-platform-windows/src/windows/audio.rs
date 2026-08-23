use std::collections::VecDeque;

use ::windows::Win32::Media::Audio::{
    AUDCLNT_SHAREMODE_SHARED, AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM,
    AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY, IAudioClient, IAudioRenderClient, IMMDeviceEnumerator,
    MMDeviceEnumerator, WAVEFORMATEX, eConsole, eRender,
};
use ::windows::Win32::System::Com::{CLSCTX_ALL, CoCreateInstance};

use crate::queue::BoundedQueue;
use crate::{AudioFormat, PcmFrame};

const WAVE_FORMAT_IEEE_FLOAT: u16 = 3;
const BUFFER_DURATION_100NS: i64 = 200_000;

pub(super) struct AudioRenderer {
    render: IAudioRenderClient,
    client: IAudioClient,
    buffer_frames: u32,
    format: AudioFormat,
    pending: VecDeque<f32>,
    pending_capacity: usize,
    paused: bool,
}

impl AudioRenderer {
    pub(super) fn probe() -> Result<(), String> {
        let mut renderer = Self::new(AudioFormat {
            sample_rate: 48_000,
            channels: 2,
        })?;
        renderer.stop();
        Ok(())
    }

    pub(super) fn new(format: AudioFormat) -> Result<Self, String> {
        let block_align = format
            .channels
            .checked_mul(4)
            .ok_or("PCM block alignment overflow")?;
        let wave_format = WAVEFORMATEX {
            wFormatTag: WAVE_FORMAT_IEEE_FLOAT,
            nChannels: format.channels,
            nSamplesPerSec: format.sample_rate,
            nAvgBytesPerSec: format
                .sample_rate
                .checked_mul(block_align as u32)
                .ok_or("PCM byte rate overflow")?,
            nBlockAlign: block_align,
            wBitsPerSample: 32,
            cbSize: 0,
        };

        unsafe {
            let enumerator: IMMDeviceEnumerator =
                CoCreateInstance(&MMDeviceEnumerator, None, CLSCTX_ALL)
                    .map_err(|error| error.to_string())?;
            let device = enumerator
                .GetDefaultAudioEndpoint(eRender, eConsole)
                .map_err(|error| error.to_string())?;
            let client: IAudioClient = device
                .Activate(CLSCTX_ALL, None)
                .map_err(|error| error.to_string())?;
            client
                .Initialize(
                    AUDCLNT_SHAREMODE_SHARED,
                    AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM | AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY,
                    BUFFER_DURATION_100NS,
                    0,
                    &wave_format,
                    None,
                )
                .map_err(|error| error.to_string())?;
            let buffer_frames = client.GetBufferSize().map_err(|error| error.to_string())?;
            let render: IAudioRenderClient =
                client.GetService().map_err(|error| error.to_string())?;
            client.Start().map_err(|error| error.to_string())?;
            Ok(Self {
                render,
                client,
                buffer_frames,
                format,
                pending: VecDeque::new(),
                pending_capacity: buffer_frames as usize * format.channels as usize * 2,
                paused: false,
            })
        }
    }

    pub(super) fn render(&mut self, queue: &BoundedQueue<PcmFrame>) -> Result<bool, String> {
        let mut dropped = false;
        while let Some(frame) = queue.try_pop() {
            if frame.format != self.format {
                return Err(format!(
                    "PCM packet format {:?} does not match active format {:?}",
                    frame.format, self.format
                ));
            }
            self.pending.extend(frame.samples);
            while self.pending.len() > self.pending_capacity {
                self.pending.pop_front();
                dropped = true;
            }
        }
        if self.pending.is_empty() {
            return Ok(dropped);
        }

        unsafe {
            let padding = self
                .client
                .GetCurrentPadding()
                .map_err(|error| error.to_string())?;
            let available_frames = self.buffer_frames.saturating_sub(padding);
            let pending_frames = self.pending.len() / self.format.channels as usize;
            let write_frames = available_frames.min(pending_frames as u32);
            if write_frames == 0 {
                return Ok(dropped);
            }
            let sample_count = write_frames as usize * self.format.channels as usize;
            let destination = self
                .render
                .GetBuffer(write_frames)
                .map_err(|error| error.to_string())? as *mut f32;
            for index in 0..sample_count {
                destination
                    .add(index)
                    .write(self.pending.pop_front().unwrap_or(0.0));
            }
            self.render
                .ReleaseBuffer(write_frames, 0)
                .map_err(|error| error.to_string())?;
        }
        Ok(dropped)
    }

    pub(super) fn stop(&mut self) {
        unsafe {
            let _ = self.client.Stop();
        }
        self.pending.clear();
        self.paused = true;
    }

    pub(super) fn set_paused(&mut self, paused: bool) -> Result<(), String> {
        if self.paused == paused {
            return Ok(());
        }
        self.pending.clear();
        unsafe {
            if paused {
                self.client.Stop()
            } else {
                self.client.Start()
            }
            .map_err(|error| error.to_string())
        }?;
        self.paused = paused;
        Ok(())
    }
}

impl Drop for AudioRenderer {
    fn drop(&mut self) {
        self.stop();
    }
}
