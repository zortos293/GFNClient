// YUV (Rec. 709) to RGB conversion shader
// Supports both SDR (8-bit) and HDR (10-bit) output

struct VertexInput {
    @location(0) position: vec2<f32>,
    @location(1) tex_coords: vec2<f32>,
}

struct VertexOutput {
    @builtin(position) clip_position: vec4<f32>,
    @location(0) tex_coords: vec2<f32>,
}

@group(0) @binding(0) var y_texture: texture_2d<f32>;
@group(0) @binding(1) var u_texture: texture_2d<f32>;
@group(0) @binding(2) var v_texture: texture_2d<f32>;
@group(0) @binding(3) var tex_sampler: sampler;

@vertex
fn vs_main(in: VertexInput) -> VertexOutput {
    var out: VertexOutput;
    out.clip_position = vec4<f32>(in.position, 0.0, 1.0);
    out.tex_coords = in.tex_coords;
    return out;
}

@fragment
fn fs_main(in: VertexOutput) -> @location(0) vec4<f32> {
    // Sample YUV planes
    let y = textureSample(y_texture, tex_sampler, in.tex_coords).r;
    let u = textureSample(u_texture, tex_sampler, in.tex_coords).r;
    let v = textureSample(v_texture, tex_sampler, in.tex_coords).r;

    // Convert YUV to RGB using Rec. 709 (HDTV standard)
    // Y range: [16, 235] (limited range), U/V range: [16, 240] centered at 128
    // Formula from ITU-R BT.709

    // Normalize to [0, 1] range
    let y_norm = (y - 0.0625) / 0.859375;  // (y - 16/255) / (235-16)/255
    let u_norm = (u - 0.5);                 // Center at 0
    let v_norm = (v - 0.5);                 // Center at 0

    // YUV to RGB conversion matrix (Rec. 709)
    var rgb: vec3<f32>;
    rgb.r = y_norm + 1.5748 * v_norm;
    rgb.g = y_norm - 0.1873 * u_norm - 0.4681 * v_norm;
    rgb.b = y_norm + 1.8556 * u_norm;

    // Clamp to valid range
    rgb = clamp(rgb, vec3<f32>(0.0), vec3<f32>(1.0));

    // Apply sRGB gamma (for SDR output)
    // For HDR output, this would be replaced with PQ or HLG transfer function
    rgb = linear_to_srgb(rgb);

    return vec4<f32>(rgb, 1.0);
}

// Linear to sRGB conversion (gamma correction for SDR)
fn linear_to_srgb(linear: vec3<f32>) -> vec3<f32> {
    let cutoff = vec3<f32>(0.0031308);
    let lower = linear * 12.92;
    let higher = pow(linear, vec3<f32>(1.0 / 2.4)) * 1.055 - 0.055;
    return select(higher, lower, linear <= cutoff);
}

// PQ (Perceptual Quantizer) EOTF for HDR10
// This will be used when HDR mode is enabled
fn pq_eotf(linear: vec3<f32>) -> vec3<f32> {
    // SMPTE ST 2084 (PQ) constants
    let m1 = 0.1593017578125;
    let m2 = 78.84375;
    let c1 = 0.8359375;
    let c2 = 18.8515625;
    let c3 = 18.6875;

    let y = pow(linear, vec3<f32>(m1));
    return pow((c1 + c2 * y) / (1.0 + c3 * y), vec3<f32>(m2));
}
