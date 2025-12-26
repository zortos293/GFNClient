// YUV (Rec. 2020) to RGB conversion shader with HDR10 support
// Uses PQ (ST 2084) transfer function for HDR output

struct VertexInput {
    @location(0) position: vec2<f32>,
    @location(1) tex_coords: vec2<f32>,
}

struct VertexOutput {
    @builtin(position) clip_position: vec4<f32>,
    @location(0) tex_coords: vec2<f32>,
}

struct HdrConfig {
    max_luminance: f32,           // Display max luminance in nits
    min_luminance: f32,           // Display min luminance in nits
    content_max_luminance: f32,   // Content max luminance in nits
    content_min_luminance: f32,   // Content min luminance in nits
}

@group(0) @binding(0) var y_texture: texture_2d<f32>;
@group(0) @binding(1) var u_texture: texture_2d<f32>;
@group(0) @binding(2) var v_texture: texture_2d<f32>;
@group(0) @binding(3) var tex_sampler: sampler;
@group(0) @binding(4) var<uniform> hdr_config: HdrConfig;

@vertex
fn vs_main(in: VertexInput) -> VertexOutput {
    var out: VertexOutput;
    out.clip_position = vec4<f32>(in.position, 0.0, 1.0);
    out.tex_coords = in.tex_coords;
    return out;
}

@fragment
fn fs_main(in: VertexOutput) -> @location(0) vec4<f32> {
    // Sample YUV planes (10-bit for HDR)
    let y = textureSample(y_texture, tex_sampler, in.tex_coords).r;
    let u = textureSample(u_texture, tex_sampler, in.tex_coords).r;
    let v = textureSample(v_texture, tex_sampler, in.tex_coords).r;

    // Convert YUV to RGB using Rec. 2020 (wide color gamut for HDR)
    // 10-bit YUV: Y range [64, 940], UV range [64, 960] for limited range

    // Normalize to [0, 1] range (10-bit limited range)
    let y_norm = (y - 0.0625) / 0.859375;
    let u_norm = (u - 0.5);
    let v_norm = (v - 0.5);

    // YUV to RGB conversion matrix (Rec. 2020 for HDR)
    var rgb: vec3<f32>;
    rgb.r = y_norm + 1.4746 * v_norm;
    rgb.g = y_norm - 0.1646 * u_norm - 0.5714 * v_norm;
    rgb.b = y_norm + 1.8814 * u_norm;

    // Clamp to valid range
    rgb = clamp(rgb, vec3<f32>(0.0), vec3<f32>(1.0));

    // Convert from linear to PQ (Perceptual Quantizer - SMPTE ST 2084)
    // This maps scene-linear light values to the PQ curve for HDR displays
    rgb = linear_to_pq(rgb, hdr_config.content_max_luminance);

    return vec4<f32>(rgb, 1.0);
}

// Convert linear RGB to PQ (Perceptual Quantizer) for HDR10
// Input: linear RGB values [0, 1] representing scene luminance
// Output: PQ-encoded values [0, 1] for HDR display
fn linear_to_pq(linear: vec3<f32>, max_nits: f32) -> vec3<f32> {
    // SMPTE ST 2084 (PQ) constants
    let m1 = 0.1593017578125;      // ( 2610 / 4096 ) / 4
    let m2 = 78.84375;             // ( 2523 / 4096 ) * 128
    let c1 = 0.8359375;            // 3424 / 4096
    let c2 = 18.8515625;           // ( 2413 / 4096 ) * 32
    let c3 = 18.6875;              // ( 2392 / 4096 ) * 32

    // Normalize to absolute luminance (nits)
    // PQ is defined for 0-10000 nits range
    let normalized = linear * (max_nits / 10000.0);

    // Apply PQ curve
    let y_pow = pow(clamp(normalized, vec3<f32>(0.0), vec3<f32>(1.0)), vec3<f32>(m1));
    return pow((c1 + c2 * y_pow) / (1.0 + c3 * y_pow), vec3<f32>(m2));
}

// Inverse PQ (for debugging/testing)
fn pq_to_linear(pq: vec3<f32>) -> vec3<f32> {
    let m1 = 0.1593017578125;
    let m2 = 78.84375;
    let c1 = 0.8359375;
    let c2 = 18.8515625;
    let c3 = 18.6875;

    let y_pow = pow(clamp(pq, vec3<f32>(0.0), vec3<f32>(1.0)), vec3<f32>(1.0 / m2));
    let y = pow(max((y_pow - c1) / (c2 - c3 * y_pow), vec3<f32>(0.0)), vec3<f32>(1.0 / m1));

    return y;
}

// Tone mapping for when content exceeds display capabilities
// Maps content luminance range to display luminance range
fn tone_map(rgb: vec3<f32>, content_max: f32, display_max: f32) -> vec3<f32> {
    if (content_max <= display_max) {
        // No tone mapping needed
        return rgb;
    }

    // Simple Reinhard-based tone mapping
    // Preserves highlights while compressing high luminance values
    let scale = display_max / content_max;
    let compressed = rgb / (rgb + vec3<f32>(1.0));

    // Convert scalar edges to vec3 for WGSL smoothstep compatibility
    let edge0 = vec3<f32>(display_max * 0.5);
    let edge1 = vec3<f32>(display_max);
    return mix(rgb * scale, compressed * display_max,
               smoothstep(edge0, edge1, rgb));
}
