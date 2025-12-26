//! Standalone GPU renderer test binary
//! Tests the wgpu rendering pipeline without requiring FFmpeg

use std::sync::Arc;
use std::time::Instant;
use winit::application::ApplicationHandler;
use winit::dpi::LogicalSize;
use winit::event::{WindowEvent, KeyEvent, StartCause};
use winit::event_loop::{ActiveEventLoop, ControlFlow, EventLoop};
use winit::keyboard::{KeyCode, PhysicalKey};
use winit::window::{Window, WindowId};

mod native;

use native::gpu_renderer::{GpuRenderer, RendererConfig};
use native::test_mode;

struct TestApp {
    window: Option<Arc<Window>>,
    renderer: Option<GpuRenderer>,
    frame_number: u64,
    start_time: Instant,
    duration_seconds: u64,
    width: u32,
    height: u32,
}

impl ApplicationHandler for TestApp {
    fn resumed(&mut self, event_loop: &ActiveEventLoop) {
        if self.window.is_some() {
            return;
        }

        let window_attrs = Window::default_attributes()
            .with_title("OpenNOW - GPU Renderer Test (SDR Mode)")
            .with_inner_size(LogicalSize::new(self.width, self.height));

        let window = Arc::new(event_loop.create_window(window_attrs).unwrap());

        let renderer_config = RendererConfig {
            hdr_enabled: false,
            max_luminance: 80.0,
            min_luminance: 0.0,
            content_max_luminance: 80.0,
            content_min_luminance: 0.0,
            color_space: native::gpu_renderer::ColorSpace::Rec709,
        };

        let renderer = GpuRenderer::new(window.clone(), renderer_config).unwrap();

        println!("✅ GPU Renderer Test - SDR Mode");
        println!("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        println!("Window: {}x{}", self.width, self.height);
        println!("Duration: {} seconds", self.duration_seconds);
        println!("Pipeline: wgpu + YUV→RGB shader");
        println!("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        println!("Press ESC to exit early");
        println!();

        self.renderer = Some(renderer);
        self.window = Some(window);
    }

    fn window_event(&mut self, event_loop: &ActiveEventLoop, _id: WindowId, event: WindowEvent) {
        match event {
            WindowEvent::CloseRequested => {
                event_loop.exit();
            }
            WindowEvent::KeyboardInput {
                event: KeyEvent {
                    physical_key: PhysicalKey::Code(KeyCode::Escape),
                    ..
                },
                ..
            } => {
                println!("ESC pressed - exiting");
                event_loop.exit();
            }
            WindowEvent::RedrawRequested => {
                // Render frame
                if let Some(ref mut renderer) = self.renderer {
                    let frame = test_mode::generate_test_frame(
                        self.width,
                        self.height,
                        self.frame_number,
                    );

                    match renderer.render_frame(&frame) {
                        Ok(_) => self.frame_number += 1,
                        Err(e) => eprintln!("Render error: {:?}", e),
                    }
                }

                // Check if test duration exceeded
                let elapsed = self.start_time.elapsed().as_secs();
                if elapsed >= self.duration_seconds {
                    println!("\n✅ Test completed successfully!");
                    println!("Total frames rendered: {}", self.frame_number);
                    println!("Average FPS: {:.2}", self.frame_number as f64 / elapsed as f64);
                    event_loop.exit();
                }
            }
            _ => {}
        }
    }

    fn new_events(&mut self, _event_loop: &ActiveEventLoop, _cause: StartCause) {
        if let Some(window) = &self.window {
            window.request_redraw();
        }
    }
}

fn main() {
    env_logger::init();

    let width = 1920;
    let height = 1080;
    let duration = 15; // 15 seconds

    let event_loop = EventLoop::new().unwrap();
    event_loop.set_control_flow(ControlFlow::Poll);

    let mut app = TestApp {
        window: None,
        renderer: None,
        frame_number: 0,
        start_time: Instant::now(),
        duration_seconds: duration,
        width,
        height,
    };

    let _ = event_loop.run_app(&mut app);
}
