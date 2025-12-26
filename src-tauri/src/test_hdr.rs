//! Comprehensive HDR testing binary
//! Tests the complete HDR pipeline with multiple modes

use std::sync::Arc;
use std::time::Instant;
use clap::Parser;
use winit::application::ApplicationHandler;
use winit::dpi::LogicalSize;
use winit::event::{WindowEvent, KeyEvent, StartCause};
use winit::event_loop::{ActiveEventLoop, ControlFlow, EventLoop};
use winit::keyboard::{KeyCode, PhysicalKey};
use winit::window::{Window, WindowId};

mod native;

use native::gpu_renderer::{GpuRenderer, RendererConfig, ColorSpace};
use native::hdr_detection;
use native::test_mode;

/// HDR Testing Application
#[derive(Parser, Debug, Clone)]
#[command(author, version, about = "OpenNOW HDR Pipeline Test", long_about = None)]
struct Args {
    /// Test mode: auto, sdr, hdr, compare
    #[arg(short, long, default_value = "auto")]
    mode: String,

    /// Window width
    #[arg(long, default_value = "1920")]
    width: u32,

    /// Window height
    #[arg(long, default_value = "1080")]
    height: u32,

    /// Test duration in seconds
    #[arg(long, default_value = "15")]
    duration: u64,

    /// Force HDR max luminance (nits)
    #[arg(long)]
    force_max_nits: Option<f32>,

    /// Verbose logging
    #[arg(short, long)]
    verbose: bool,
}

struct TestApp {
    window: Option<Arc<Window>>,
    renderer: Option<GpuRenderer>,
    frame_number: u64,
    start_time: Instant,
    args: Args,
    hdr_caps: hdr_detection::HdrCapabilities,
    test_mode: TestMode,
}

#[derive(Debug, Clone)]
enum TestMode {
    SDR,
    HDR,
    Compare { phase: ComparePhase },
}

#[derive(Debug, Clone)]
enum ComparePhase {
    SDR,
    HDR,
}

impl ApplicationHandler for TestApp {
    fn resumed(&mut self, event_loop: &ActiveEventLoop) {
        if self.window.is_some() {
            return;
        }

        let window_title = match &self.test_mode {
            TestMode::SDR => "OpenNOW - SDR Test Mode",
            TestMode::HDR => "OpenNOW - HDR Test Mode",
            TestMode::Compare { .. } => "OpenNOW - SDR/HDR Comparison",
        };

        let window_attrs = Window::default_attributes()
            .with_title(window_title)
            .with_inner_size(LogicalSize::new(self.args.width, self.args.height));

        let window = Arc::new(event_loop.create_window(window_attrs).unwrap());

        // Create renderer config based on test mode
        let renderer_config = match &self.test_mode {
            TestMode::SDR => RendererConfig {
                hdr_enabled: false,
                max_luminance: 80.0,
                min_luminance: 0.0,
                content_max_luminance: 80.0,
                content_min_luminance: 0.0,
                color_space: ColorSpace::Rec709,
            },
            TestMode::HDR | TestMode::Compare { phase: ComparePhase::HDR } => {
                let max_lum = self.args.force_max_nits.unwrap_or(self.hdr_caps.max_luminance);
                RendererConfig {
                    hdr_enabled: true,
                    max_luminance: max_lum,
                    min_luminance: self.hdr_caps.min_luminance,
                    content_max_luminance: max_lum,
                    content_min_luminance: 0.0001,
                    color_space: ColorSpace::Rec2020,
                }
            },
            TestMode::Compare { phase: ComparePhase::SDR } => RendererConfig {
                hdr_enabled: false,
                max_luminance: 80.0,
                min_luminance: 0.0,
                content_max_luminance: 80.0,
                content_min_luminance: 0.0,
                color_space: ColorSpace::Rec709,
            },
        };

        let renderer = match GpuRenderer::new(window.clone(), renderer_config.clone()) {
            Ok(r) => r,
            Err(e) => {
                eprintln!("❌ Failed to create GPU renderer: {}", e);
                eprintln!("   This might be because:");
                eprintln!("   - Your GPU doesn't support the required features");
                eprintln!("   - Graphics drivers need updating");
                eprintln!("   - HDR is not available on this display");
                event_loop.exit();
                return;
            }
        };

        self.print_test_info(&renderer_config);

        self.renderer = Some(renderer);
        self.window = Some(window);
    }

    fn window_event(&mut self, event_loop: &ActiveEventLoop, _id: WindowId, event: WindowEvent) {
        match event {
            WindowEvent::CloseRequested => {
                self.print_results();
                event_loop.exit();
            }
            WindowEvent::KeyboardInput {
                event: KeyEvent {
                    physical_key: PhysicalKey::Code(KeyCode::Escape),
                    ..
                },
                ..
            } => {
                println!("\n⚠️  ESC pressed - exiting early");
                self.print_results();
                event_loop.exit();
            }
            WindowEvent::KeyboardInput {
                event: KeyEvent {
                    physical_key: PhysicalKey::Code(KeyCode::Space),
                    ..
                },
                ..
            } => {
                // Toggle between SDR/HDR in compare mode
                if let TestMode::Compare { ref mut phase } = self.test_mode {
                    *phase = match phase {
                        ComparePhase::SDR => {
                            println!("\n🔄 Switching to HDR mode...");
                            ComparePhase::HDR
                        },
                        ComparePhase::HDR => {
                            println!("\n🔄 Switching to SDR mode...");
                            ComparePhase::SDR
                        },
                    };
                    // Force recreate renderer
                    self.window = None;
                    self.renderer = None;
                }
            }
            WindowEvent::RedrawRequested => {
                // Render frame
                if let Some(ref mut renderer) = self.renderer {
                    let frame = test_mode::generate_test_frame(
                        self.args.width,
                        self.args.height,
                        self.frame_number,
                    );

                    match renderer.render_frame(&frame) {
                        Ok(_) => self.frame_number += 1,
                        Err(e) => eprintln!("⚠️  Render error: {:?}", e),
                    }
                }

                // Check if test duration exceeded
                let elapsed = self.start_time.elapsed().as_secs();
                if elapsed >= self.args.duration {
                    self.print_results();
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

impl TestApp {
    fn print_test_info(&self, config: &RendererConfig) {
        println!("\n╔════════════════════════════════════════════════════════╗");
        println!("║         OpenNOW HDR Pipeline Test                     ║");
        println!("╚════════════════════════════════════════════════════════╝");

        println!("\n📊 Test Configuration:");
        println!("   Mode:       {:?}", self.test_mode);
        println!("   Resolution: {}x{}", self.args.width, self.args.height);
        println!("   Duration:   {} seconds", self.args.duration);

        println!("\n🖥️  Display Capabilities:");
        println!("   HDR Support: {}", if self.hdr_caps.hdr_supported { "✅ Yes" } else { "❌ No" });
        println!("   Max Luminance: {:.1} nits", self.hdr_caps.max_luminance);
        println!("   Min Luminance: {:.4} nits", self.hdr_caps.min_luminance);
        println!("   Color Space: {:?}", self.hdr_caps.color_space);

        println!("\n🎨 Renderer Configuration:");
        println!("   HDR Enabled: {}", if config.hdr_enabled { "✅ Yes" } else { "❌ No (SDR)" });
        println!("   Target Max Luminance: {:.1} nits", config.max_luminance);
        println!("   Color Space: {:?}", config.color_space);

        if config.hdr_enabled {
            println!("   Transfer Function: PQ (SMPTE ST 2084)");
            println!("   Color Gamut: Rec. 2020 (wide)");
        } else {
            println!("   Transfer Function: sRGB gamma 2.2");
            println!("   Color Gamut: Rec. 709 (standard)");
        }

        println!("\n🎮 Controls:");
        if matches!(self.test_mode, TestMode::Compare { .. }) {
            println!("   SPACE: Toggle SDR/HDR");
        }
        println!("   ESC:   Exit test");

        println!("\n🚀 Test Running...\n");
    }

    fn print_results(&self) {
        let elapsed = self.start_time.elapsed().as_secs_f64();
        let avg_fps = self.frame_number as f64 / elapsed;

        println!("\n╔════════════════════════════════════════════════════════╗");
        println!("║         Test Results                                   ║");
        println!("╚════════════════════════════════════════════════════════╝");

        println!("\n📈 Performance:");
        println!("   Frames Rendered: {}", self.frame_number);
        println!("   Duration:        {:.2}s", elapsed);
        println!("   Average FPS:     {:.2}", avg_fps);

        if avg_fps >= 55.0 {
            println!("   Status:          ✅ Excellent (target: 60 FPS)");
        } else if avg_fps >= 45.0 {
            println!("   Status:          ✅ Good (target: 60 FPS)");
        } else if avg_fps >= 30.0 {
            println!("   Status:          ⚠️  Acceptable");
        } else {
            println!("   Status:          ❌ Poor - check GPU drivers");
        }

        println!("\n✅ Test completed successfully!\n");
    }
}

fn main() {
    let args = Args::parse();

    // Initialize logging
    if args.verbose {
        env_logger::Builder::from_default_env()
            .filter_level(log::LevelFilter::Debug)
            .init();
    } else {
        env_logger::Builder::from_default_env()
            .filter_level(log::LevelFilter::Info)
            .init();
    }

    // Detect HDR capabilities
    println!("🔍 Detecting HDR display capabilities...");
    let hdr_caps = match hdr_detection::detect_hdr_capabilities() {
        Ok(caps) => {
            println!("✅ Detection complete");
            caps
        },
        Err(e) => {
            eprintln!("⚠️  HDR detection failed: {}", e);
            eprintln!("   Falling back to SDR defaults");
            hdr_detection::HdrCapabilities::default()
        }
    };

    // Determine test mode
    let test_mode = match args.mode.as_str() {
        "sdr" => {
            println!("📺 Running in SDR mode (forced)");
            TestMode::SDR
        },
        "hdr" => {
            if !hdr_caps.hdr_supported && args.force_max_nits.is_none() {
                eprintln!("❌ HDR mode requested but display doesn't support HDR");
                eprintln!("   Use --force-max-nits <value> to test anyway");
                std::process::exit(1);
            }
            println!("✨ Running in HDR mode");
            TestMode::HDR
        },
        "compare" => {
            println!("🔄 Running in comparison mode (press SPACE to toggle)");
            TestMode::Compare { phase: ComparePhase::SDR }
        },
        "auto" | _ => {
            if hdr_caps.hdr_supported {
                println!("✨ HDR display detected - running in HDR mode");
                TestMode::HDR
            } else {
                println!("📺 SDR display detected - running in SDR mode");
                TestMode::SDR
            }
        }
    };

    let event_loop = EventLoop::new().unwrap();
    event_loop.set_control_flow(ControlFlow::Poll);

    let mut app = TestApp {
        window: None,
        renderer: None,
        frame_number: 0,
        start_time: Instant::now(),
        args,
        hdr_caps,
        test_mode,
    };

    let _ = event_loop.run_app(&mut app);
}
