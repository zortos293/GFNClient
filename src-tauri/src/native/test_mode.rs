//! Test mode for the renderer without needing a GFN session
//!
//! Generates synthetic YUV frames to test the GPU renderer

use super::gpu_renderer::YuvFrame;
use std::time::Instant;

/// Generate a test pattern YUV frame
pub fn generate_test_frame(width: u32, height: u32, frame_number: u64) -> YuvFrame {
    let y_size = (width * height) as usize;
    let uv_size = (width / 2 * height / 2) as usize;

    let mut y_plane = vec![0u8; y_size];
    let mut u_plane = vec![128u8; uv_size];
    let mut v_plane = vec![128u8; uv_size];

    // Generate animated test pattern
    let time = (frame_number as f32 * 0.016) % 10.0; // ~60 FPS, loop every 10 seconds

    for y in 0..height {
        for x in 0..width {
            let idx = (y * width + x) as usize;

            // Moving gradient pattern
            let gradient = ((x as f32 / width as f32 + time * 0.1).fract() * 255.0) as u8;
            let vertical = ((y as f32 / height as f32) * 255.0) as u8;

            // Combine patterns
            y_plane[idx] = gradient.wrapping_add(vertical) / 2;

            // Add some color in UV planes
            if x % 2 == 0 && y % 2 == 0 {
                let uv_idx = (y / 2 * (width / 2) + x / 2) as usize;

                // Animated color shifts
                let hue_shift = (time * 50.0) as u8;
                u_plane[uv_idx] = 128u8.wrapping_add(hue_shift);
                v_plane[uv_idx] = 128u8.wrapping_sub(hue_shift / 2);
            }
        }
    }

    // Add text-like pattern in top-left (simulate "TEST MODE" text)
    let text_patterns = [
        // T
        (10, 10, 50, 10), (25, 10, 25, 80),
        // E
        (60, 10, 100, 10), (60, 10, 60, 80), (60, 45, 90, 45), (60, 80, 100, 80),
        // S
        (110, 10, 150, 10), (110, 10, 110, 45), (110, 45, 150, 45),
        (150, 45, 150, 80), (110, 80, 150, 80),
        // T
        (160, 10, 200, 10), (175, 10, 175, 80),
    ];

    for (x1, y1, x2, y2) in text_patterns.iter() {
        for y in *y1..*y2 {
            for x in *x1..*x2 {
                if x < width && y < height {
                    let idx = (y * width + x) as usize;
                    y_plane[idx] = 235; // Bright white
                }
            }
        }
    }

    YuvFrame {
        y_plane,
        u_plane,
        v_plane,
        width,
        height,
        y_stride: width as usize,
        u_stride: (width / 2) as usize,
        v_stride: (width / 2) as usize,
    }
}

/// Test the renderer with animated patterns
pub fn run_renderer_test(width: u32, height: u32, duration_seconds: u64) {
    use super::gpu_renderer::{GpuRenderer, RendererConfig};
    use std::sync::Arc;
    use winit::application::ApplicationHandler;
    use winit::event::{WindowEvent, StartCause};
    use winit::event_loop::{ActiveEventLoop, ControlFlow, EventLoop};
    use winit::window::{Window, WindowId};
    use winit::dpi::LogicalSize;

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
                .with_title("OpenNOW - GPU Renderer Test Mode")
                .with_inner_size(LogicalSize::new(self.width, self.height));

            let window = Arc::new(event_loop.create_window(window_attrs).unwrap());

            let renderer_config = RendererConfig {
                hdr_enabled: false,
                max_luminance: 80.0,
                min_luminance: 0.0,
                content_max_luminance: 80.0,
                content_min_luminance: 0.0,
                color_space: super::gpu_renderer::ColorSpace::Rec709,
            };

            let renderer = GpuRenderer::new(window.clone(), renderer_config).unwrap();

            println!("✅ GPU Renderer Test Mode");
            println!("Window: {}x{}", self.width, self.height);
            println!("Duration: {} seconds", self.duration_seconds);
            println!("Press ESC to exit early");

            self.renderer = Some(renderer);
            self.window = Some(window);
        }

        fn window_event(&mut self, event_loop: &ActiveEventLoop, _id: WindowId, event: WindowEvent) {
            match event {
                WindowEvent::CloseRequested => {
                    event_loop.exit();
                }
                WindowEvent::RedrawRequested => {
                    if let Some(renderer) = &mut self.renderer {
                        // Generate test frame
                        let frame = generate_test_frame(self.width, self.height, self.frame_number);

                        // Render
                        if let Err(e) = renderer.render_frame(&frame) {
                            eprintln!("Render error: {}", e);
                        }

                        self.frame_number += 1;

                        // Check if test duration exceeded
                        let elapsed = self.start_time.elapsed().as_secs();
                        if elapsed >= self.duration_seconds {
                            println!("✅ Test complete! Rendered {} frames in {} seconds",
                                self.frame_number, elapsed);
                            println!("Average FPS: {:.1}", self.frame_number as f64 / elapsed as f64);
                            event_loop.exit();
                        }
                    }
                }
                WindowEvent::KeyboardInput { event, .. } => {
                    if event.physical_key == winit::keyboard::PhysicalKey::Code(winit::keyboard::KeyCode::Escape) {
                        println!("Test aborted by user");
                        event_loop.exit();
                    }
                }
                WindowEvent::Resized(size) => {
                    if let Some(renderer) = &mut self.renderer {
                        renderer.resize(size.width, size.height);
                    }
                }
                _ => {}
            }
        }

        fn about_to_wait(&mut self, _event_loop: &ActiveEventLoop) {
            if let Some(window) = &self.window {
                window.request_redraw();
            }
        }

        fn new_events(&mut self, _event_loop: &ActiveEventLoop, _cause: StartCause) {}
    }

    let event_loop = EventLoop::new().unwrap();
    let mut app = TestApp {
        window: None,
        renderer: None,
        frame_number: 0,
        start_time: Instant::now(),
        duration_seconds,
        width,
        height,
    };

    event_loop.run_app(&mut app).unwrap();
}
