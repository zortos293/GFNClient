//! GPU-accelerated video renderer using wgpu
//!
//! Handles YUV to RGB conversion and HDR output support.
//! Uses compute shaders for efficient colorspace conversion.

use std::sync::Arc;
use anyhow::{Result, Context};
use log::{info, warn};
use winit::window::Window;
use wgpu::util::DeviceExt;

/// Video frame data in YUV format (from decoder)
pub struct YuvFrame {
    pub y_plane: Vec<u8>,
    pub u_plane: Vec<u8>,
    pub v_plane: Vec<u8>,
    pub width: u32,
    pub height: u32,
    pub y_stride: usize,
    pub u_stride: usize,
    pub v_stride: usize,
}

/// Configuration for the renderer
#[derive(Debug, Clone)]
pub struct RendererConfig {
    /// Enable HDR output (10-bit or 16-bit float)
    pub hdr_enabled: bool,

    /// Display max luminance in nits (from display capabilities)
    pub max_luminance: f32,

    /// Display min luminance in nits
    pub min_luminance: f32,

    /// Content max luminance in nits (from stream metadata)
    pub content_max_luminance: f32,

    /// Content min luminance in nits (from stream metadata)
    pub content_min_luminance: f32,

    /// Color space (sRGB/Rec709 for SDR, Rec2020 for HDR)
    pub color_space: ColorSpace,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ColorSpace {
    /// sRGB / Rec. 709 (SDR)
    Rec709,
    /// Rec. 2020 (HDR wide color gamut)
    Rec2020,
}

impl Default for RendererConfig {
    fn default() -> Self {
        Self {
            hdr_enabled: false,
            max_luminance: 80.0,      // Standard SDR display
            min_luminance: 0.0,
            content_max_luminance: 80.0,
            content_min_luminance: 0.0,
            color_space: ColorSpace::Rec709,
        }
    }
}

/// GPU renderer for video frames
pub struct GpuRenderer {
    device: wgpu::Device,
    queue: wgpu::Queue,
    surface: wgpu::Surface<'static>,
    surface_config: wgpu::SurfaceConfiguration,
    render_pipeline: wgpu::RenderPipeline,

    // YUV textures
    y_texture: wgpu::Texture,
    u_texture: wgpu::Texture,
    v_texture: wgpu::Texture,

    // Bind groups
    bind_group: wgpu::BindGroup,
    bind_group_layout: wgpu::BindGroupLayout,

    // Vertex buffer (fullscreen quad)
    vertex_buffer: wgpu::Buffer,

    // HDR uniform buffer
    hdr_uniform_buffer: wgpu::Buffer,

    // Current frame dimensions
    frame_width: u32,
    frame_height: u32,

    config: RendererConfig,
}

impl GpuRenderer {
    /// Create a new GPU renderer
    pub fn new(window: Arc<Window>, config: RendererConfig) -> Result<Self> {
        // Create wgpu instance
        // Force D3D12 on Windows for better HDR support
        let instance = wgpu::Instance::new(wgpu::InstanceDescriptor {
            backends: wgpu::Backends::DX12,
            ..Default::default()
        });

        // Create surface
        let surface = instance.create_surface(window.clone())
            .context("Failed to create surface")?;

        // Request adapter (GPU)
        let adapter = pollster::block_on(instance.request_adapter(&wgpu::RequestAdapterOptions {
            power_preference: wgpu::PowerPreference::HighPerformance,
            compatible_surface: Some(&surface),
            force_fallback_adapter: false,
        }))
        .context("Failed to find suitable GPU adapter")?;

        info!("Using GPU: {} ({:?})", adapter.get_info().name, adapter.get_info().backend);

        // Request device and queue
        let (device, queue) = pollster::block_on(adapter.request_device(
            &wgpu::DeviceDescriptor {
                label: Some("GFN Renderer Device"),
                required_features: wgpu::Features::empty(),
                required_limits: wgpu::Limits::default(),
                memory_hints: wgpu::MemoryHints::Performance,
            },
            None,
        ))
        .context("Failed to create device")?;

        // Configure surface
        let size = window.inner_size();
        let surface_caps = surface.get_capabilities(&adapter);

        // Choose surface format (HDR if requested and available)
        let surface_format = if config.hdr_enabled {
            // Try to find HDR format
            surface_caps.formats.iter()
                .find(|f| matches!(f,
                    wgpu::TextureFormat::Rgba16Float |  // ScRGB/EDR (macOS, Windows)
                    wgpu::TextureFormat::Rgb10a2Unorm   // HDR10 (Windows)
                ))
                .copied()
                .unwrap_or_else(|| {
                    warn!("HDR requested but not supported, falling back to SDR");
                    surface_caps.formats[0]
                })
        } else {
            surface_caps.formats[0]  // SDR
        };

        info!("Surface format: {:?}", surface_format);
        if config.hdr_enabled && matches!(surface_format,
            wgpu::TextureFormat::Rgba16Float | wgpu::TextureFormat::Rgb10a2Unorm) {
            info!("HDR output enabled!");
        }

        let surface_config = wgpu::SurfaceConfiguration {
            usage: wgpu::TextureUsages::RENDER_ATTACHMENT,
            format: surface_format,
            width: size.width.max(1),
            height: size.height.max(1),
            present_mode: wgpu::PresentMode::Fifo, // VSync
            alpha_mode: surface_caps.alpha_modes[0],
            view_formats: vec![],
            desired_maximum_frame_latency: 2,
        };

        surface.configure(&device, &surface_config);

        // Create bind group layout for YUV textures
        let bind_group_layout = device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
            label: Some("YUV Bind Group Layout"),
            entries: &[
                // Y texture
                wgpu::BindGroupLayoutEntry {
                    binding: 0,
                    visibility: wgpu::ShaderStages::FRAGMENT,
                    ty: wgpu::BindingType::Texture {
                        sample_type: wgpu::TextureSampleType::Float { filterable: true },
                        view_dimension: wgpu::TextureViewDimension::D2,
                        multisampled: false,
                    },
                    count: None,
                },
                // U texture
                wgpu::BindGroupLayoutEntry {
                    binding: 1,
                    visibility: wgpu::ShaderStages::FRAGMENT,
                    ty: wgpu::BindingType::Texture {
                        sample_type: wgpu::TextureSampleType::Float { filterable: true },
                        view_dimension: wgpu::TextureViewDimension::D2,
                        multisampled: false,
                    },
                    count: None,
                },
                // V texture
                wgpu::BindGroupLayoutEntry {
                    binding: 2,
                    visibility: wgpu::ShaderStages::FRAGMENT,
                    ty: wgpu::BindingType::Texture {
                        sample_type: wgpu::TextureSampleType::Float { filterable: true },
                        view_dimension: wgpu::TextureViewDimension::D2,
                        multisampled: false,
                    },
                    count: None,
                },
                // Sampler
                wgpu::BindGroupLayoutEntry {
                    binding: 3,
                    visibility: wgpu::ShaderStages::FRAGMENT,
                    ty: wgpu::BindingType::Sampler(wgpu::SamplerBindingType::Filtering),
                    count: None,
                },
                // HDR config uniform buffer (only needed for HDR shader)
                wgpu::BindGroupLayoutEntry {
                    binding: 4,
                    visibility: wgpu::ShaderStages::FRAGMENT,
                    ty: wgpu::BindingType::Buffer {
                        ty: wgpu::BufferBindingType::Uniform,
                        has_dynamic_offset: false,
                        min_binding_size: None,
                    },
                    count: None,
                },
            ],
        });

        // Create initial YUV textures (16x16 placeholder - must be even for UV planes)
        let (y_texture, u_texture, v_texture) = Self::create_yuv_textures(&device, 16, 16);

        // Create sampler
        let sampler = device.create_sampler(&wgpu::SamplerDescriptor {
            label: Some("YUV Sampler"),
            address_mode_u: wgpu::AddressMode::ClampToEdge,
            address_mode_v: wgpu::AddressMode::ClampToEdge,
            address_mode_w: wgpu::AddressMode::ClampToEdge,
            mag_filter: wgpu::FilterMode::Linear,
            min_filter: wgpu::FilterMode::Linear,
            mipmap_filter: wgpu::FilterMode::Nearest,
            ..Default::default()
        });

        // Create HDR config uniform buffer
        #[repr(C)]
        #[derive(Copy, Clone, bytemuck::Pod, bytemuck::Zeroable)]
        struct HdrConfigUniform {
            max_luminance: f32,
            min_luminance: f32,
            content_max_luminance: f32,
            content_min_luminance: f32,
        }

        let hdr_uniform = HdrConfigUniform {
            max_luminance: config.max_luminance,
            min_luminance: config.min_luminance,
            content_max_luminance: config.content_max_luminance,
            content_min_luminance: config.content_min_luminance,
        };

        let hdr_uniform_buffer = device.create_buffer_init(&wgpu::util::BufferInitDescriptor {
            label: Some("HDR Config Uniform Buffer"),
            contents: bytemuck::cast_slice(&[hdr_uniform]),
            usage: wgpu::BufferUsages::UNIFORM | wgpu::BufferUsages::COPY_DST,
        });

        // Create bind group
        let bind_group = device.create_bind_group(&wgpu::BindGroupDescriptor {
            label: Some("YUV Bind Group"),
            layout: &bind_group_layout,
            entries: &[
                wgpu::BindGroupEntry {
                    binding: 0,
                    resource: wgpu::BindingResource::TextureView(
                        &y_texture.create_view(&wgpu::TextureViewDescriptor::default())
                    ),
                },
                wgpu::BindGroupEntry {
                    binding: 1,
                    resource: wgpu::BindingResource::TextureView(
                        &u_texture.create_view(&wgpu::TextureViewDescriptor::default())
                    ),
                },
                wgpu::BindGroupEntry {
                    binding: 2,
                    resource: wgpu::BindingResource::TextureView(
                        &v_texture.create_view(&wgpu::TextureViewDescriptor::default())
                    ),
                },
                wgpu::BindGroupEntry {
                    binding: 3,
                    resource: wgpu::BindingResource::Sampler(&sampler),
                },
                wgpu::BindGroupEntry {
                    binding: 4,
                    resource: hdr_uniform_buffer.as_entire_binding(),
                },
            ],
        });

        // Create shader module (choose SDR or HDR shader based on config)
        let shader_source = if config.hdr_enabled {
            include_str!("shaders/yuv_to_rgb_hdr.wgsl")
        } else {
            include_str!("shaders/yuv_to_rgb.wgsl")
        };

        let shader = device.create_shader_module(wgpu::ShaderModuleDescriptor {
            label: Some(if config.hdr_enabled { "YUV to RGB HDR Shader" } else { "YUV to RGB SDR Shader" }),
            source: wgpu::ShaderSource::Wgsl(shader_source.into()),
        });

        info!("Loaded {} shader", if config.hdr_enabled { "HDR" } else { "SDR" });

        // Create render pipeline
        let pipeline_layout = device.create_pipeline_layout(&wgpu::PipelineLayoutDescriptor {
            label: Some("Render Pipeline Layout"),
            bind_group_layouts: &[&bind_group_layout],
            push_constant_ranges: &[],
        });

        let render_pipeline = device.create_render_pipeline(&wgpu::RenderPipelineDescriptor {
            label: Some("Render Pipeline"),
            layout: Some(&pipeline_layout),
            vertex: wgpu::VertexState {
                module: &shader,
                entry_point: "vs_main",
                buffers: &[wgpu::VertexBufferLayout {
                    array_stride: 16, // 4 floats * 4 bytes
                    step_mode: wgpu::VertexStepMode::Vertex,
                    attributes: &wgpu::vertex_attr_array![
                        0 => Float32x2,  // position
                        1 => Float32x2,  // tex_coords
                    ],
                }],
                compilation_options: Default::default(),
            },
            fragment: Some(wgpu::FragmentState {
                module: &shader,
                entry_point: "fs_main",
                targets: &[Some(wgpu::ColorTargetState {
                    format: surface_format,
                    blend: Some(wgpu::BlendState::REPLACE),
                    write_mask: wgpu::ColorWrites::ALL,
                })],
                compilation_options: Default::default(),
            }),
            primitive: wgpu::PrimitiveState {
                topology: wgpu::PrimitiveTopology::TriangleList,
                strip_index_format: None,
                front_face: wgpu::FrontFace::Ccw,
                cull_mode: None,
                unclipped_depth: false,
                polygon_mode: wgpu::PolygonMode::Fill,
                conservative: false,
            },
            depth_stencil: None,
            multisample: wgpu::MultisampleState::default(),
            multiview: None,
            cache: None,
        });

        // Create fullscreen quad vertex buffer
        #[repr(C)]
        #[derive(Copy, Clone, bytemuck::Pod, bytemuck::Zeroable)]
        struct Vertex {
            position: [f32; 2],
            tex_coords: [f32; 2],
        }

        let vertices = [
            // Triangle 1
            Vertex { position: [-1.0, -1.0], tex_coords: [0.0, 1.0] }, // Bottom-left
            Vertex { position: [ 1.0, -1.0], tex_coords: [1.0, 1.0] }, // Bottom-right
            Vertex { position: [-1.0,  1.0], tex_coords: [0.0, 0.0] }, // Top-left
            // Triangle 2
            Vertex { position: [-1.0,  1.0], tex_coords: [0.0, 0.0] }, // Top-left
            Vertex { position: [ 1.0, -1.0], tex_coords: [1.0, 1.0] }, // Bottom-right
            Vertex { position: [ 1.0,  1.0], tex_coords: [1.0, 0.0] }, // Top-right
        ];

        let vertex_buffer = device.create_buffer_init(&wgpu::util::BufferInitDescriptor {
            label: Some("Vertex Buffer"),
            contents: bytemuck::cast_slice(&vertices),
            usage: wgpu::BufferUsages::VERTEX,
        });

        Ok(Self {
            device,
            queue,
            surface,
            surface_config,
            render_pipeline,
            y_texture,
            u_texture,
            v_texture,
            bind_group,
            bind_group_layout,
            vertex_buffer,
            hdr_uniform_buffer,
            frame_width: 16,
            frame_height: 16,
            config,
        })
    }

    /// Create YUV textures for given dimensions
    fn create_yuv_textures(device: &wgpu::Device, width: u32, height: u32) -> (wgpu::Texture, wgpu::Texture, wgpu::Texture) {
        let y_texture = device.create_texture(&wgpu::TextureDescriptor {
            label: Some("Y Texture"),
            size: wgpu::Extent3d { width, height, depth_or_array_layers: 1 },
            mip_level_count: 1,
            sample_count: 1,
            dimension: wgpu::TextureDimension::D2,
            format: wgpu::TextureFormat::R8Unorm,
            usage: wgpu::TextureUsages::TEXTURE_BINDING | wgpu::TextureUsages::COPY_DST,
            view_formats: &[],
        });

        let u_texture = device.create_texture(&wgpu::TextureDescriptor {
            label: Some("U Texture"),
            size: wgpu::Extent3d { width: width / 2, height: height / 2, depth_or_array_layers: 1 },
            mip_level_count: 1,
            sample_count: 1,
            dimension: wgpu::TextureDimension::D2,
            format: wgpu::TextureFormat::R8Unorm,
            usage: wgpu::TextureUsages::TEXTURE_BINDING | wgpu::TextureUsages::COPY_DST,
            view_formats: &[],
        });

        let v_texture = device.create_texture(&wgpu::TextureDescriptor {
            label: Some("V Texture"),
            size: wgpu::Extent3d { width: width / 2, height: height / 2, depth_or_array_layers: 1 },
            mip_level_count: 1,
            sample_count: 1,
            dimension: wgpu::TextureDimension::D2,
            format: wgpu::TextureFormat::R8Unorm,
            usage: wgpu::TextureUsages::TEXTURE_BINDING | wgpu::TextureUsages::COPY_DST,
            view_formats: &[],
        });

        (y_texture, u_texture, v_texture)
    }

    /// Update YUV frame data and render
    pub fn render_frame(&mut self, frame: &YuvFrame) -> Result<()> {
        // Recreate textures if size changed
        if frame.width != self.frame_width || frame.height != self.frame_height {
            info!("Video resolution changed: {}x{} -> {}x{}",
                self.frame_width, self.frame_height, frame.width, frame.height);

            let (y_tex, u_tex, v_tex) = Self::create_yuv_textures(&self.device, frame.width, frame.height);
            self.y_texture = y_tex;
            self.u_texture = u_tex;
            self.v_texture = v_tex;
            self.frame_width = frame.width;
            self.frame_height = frame.height;

            // Recreate bind group with new textures
            let sampler = self.device.create_sampler(&wgpu::SamplerDescriptor {
                label: Some("YUV Sampler"),
                address_mode_u: wgpu::AddressMode::ClampToEdge,
                address_mode_v: wgpu::AddressMode::ClampToEdge,
                address_mode_w: wgpu::AddressMode::ClampToEdge,
                mag_filter: wgpu::FilterMode::Linear,
                min_filter: wgpu::FilterMode::Linear,
                mipmap_filter: wgpu::FilterMode::Nearest,
                ..Default::default()
            });

            self.bind_group = self.device.create_bind_group(&wgpu::BindGroupDescriptor {
                label: Some("YUV Bind Group"),
                layout: &self.bind_group_layout,
                entries: &[
                    wgpu::BindGroupEntry {
                        binding: 0,
                        resource: wgpu::BindingResource::TextureView(
                            &self.y_texture.create_view(&wgpu::TextureViewDescriptor::default())
                        ),
                    },
                    wgpu::BindGroupEntry {
                        binding: 1,
                        resource: wgpu::BindingResource::TextureView(
                            &self.u_texture.create_view(&wgpu::TextureViewDescriptor::default())
                        ),
                    },
                    wgpu::BindGroupEntry {
                        binding: 2,
                        resource: wgpu::BindingResource::TextureView(
                            &self.v_texture.create_view(&wgpu::TextureViewDescriptor::default())
                        ),
                    },
                    wgpu::BindGroupEntry {
                        binding: 3,
                        resource: wgpu::BindingResource::Sampler(&sampler),
                    },
                    wgpu::BindGroupEntry {
                        binding: 4,
                        resource: self.hdr_uniform_buffer.as_entire_binding(),
                    },
                ],
            });
        }

        // Upload YUV data to GPU textures
        self.queue.write_texture(
            wgpu::ImageCopyTexture {
                texture: &self.y_texture,
                mip_level: 0,
                origin: wgpu::Origin3d::ZERO,
                aspect: wgpu::TextureAspect::All,
            },
            &frame.y_plane,
            wgpu::ImageDataLayout {
                offset: 0,
                bytes_per_row: Some(frame.y_stride as u32),
                rows_per_image: Some(frame.height),
            },
            wgpu::Extent3d {
                width: frame.width,
                height: frame.height,
                depth_or_array_layers: 1,
            },
        );

        self.queue.write_texture(
            wgpu::ImageCopyTexture {
                texture: &self.u_texture,
                mip_level: 0,
                origin: wgpu::Origin3d::ZERO,
                aspect: wgpu::TextureAspect::All,
            },
            &frame.u_plane,
            wgpu::ImageDataLayout {
                offset: 0,
                bytes_per_row: Some(frame.u_stride as u32),
                rows_per_image: Some(frame.height / 2),
            },
            wgpu::Extent3d {
                width: frame.width / 2,
                height: frame.height / 2,
                depth_or_array_layers: 1,
            },
        );

        self.queue.write_texture(
            wgpu::ImageCopyTexture {
                texture: &self.v_texture,
                mip_level: 0,
                origin: wgpu::Origin3d::ZERO,
                aspect: wgpu::TextureAspect::All,
            },
            &frame.v_plane,
            wgpu::ImageDataLayout {
                offset: 0,
                bytes_per_row: Some(frame.v_stride as u32),
                rows_per_image: Some(frame.height / 2),
            },
            wgpu::Extent3d {
                width: frame.width / 2,
                height: frame.height / 2,
                depth_or_array_layers: 1,
            },
        );

        // Get current surface texture
        let output = self.surface.get_current_texture()
            .context("Failed to acquire next swap chain texture")?;

        let view = output.texture.create_view(&wgpu::TextureViewDescriptor::default());

        // Create command encoder
        let mut encoder = self.device.create_command_encoder(&wgpu::CommandEncoderDescriptor {
            label: Some("Render Encoder"),
        });

        {
            let mut render_pass = encoder.begin_render_pass(&wgpu::RenderPassDescriptor {
                label: Some("Render Pass"),
                color_attachments: &[Some(wgpu::RenderPassColorAttachment {
                    view: &view,
                    resolve_target: None,
                    ops: wgpu::Operations {
                        load: wgpu::LoadOp::Clear(wgpu::Color::BLACK),
                        store: wgpu::StoreOp::Store,
                    },
                })],
                depth_stencil_attachment: None,
                timestamp_writes: None,
                occlusion_query_set: None,
            });

            render_pass.set_pipeline(&self.render_pipeline);
            render_pass.set_bind_group(0, &self.bind_group, &[]);
            render_pass.set_vertex_buffer(0, self.vertex_buffer.slice(..));
            render_pass.draw(0..6, 0..1);
        }

        // Submit commands
        self.queue.submit(std::iter::once(encoder.finish()));
        output.present();

        Ok(())
    }

    /// Handle window resize
    pub fn resize(&mut self, width: u32, height: u32) {
        if width > 0 && height > 0 {
            self.surface_config.width = width;
            self.surface_config.height = height;
            self.surface.configure(&self.device, &self.surface_config);
            info!("Window resized to {}x{}", width, height);
        }
    }
}
