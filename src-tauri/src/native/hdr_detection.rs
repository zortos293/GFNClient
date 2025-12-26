//! HDR display capability detection
//!
//! Platform-specific code to detect if the display supports HDR and query capabilities

use anyhow::Result;
use log::{info, warn};

#[derive(Debug, Clone)]
pub struct HdrCapabilities {
    /// Whether the display supports HDR output
    pub hdr_supported: bool,

    /// Maximum luminance in nits (cd/m²)
    pub max_luminance: f32,

    /// Minimum luminance in nits
    pub min_luminance: f32,

    /// Maximum frame-average luminance in nits
    pub max_frame_average_luminance: f32,

    /// Supported color space
    pub color_space: ColorSpace,

    /// Display name/identifier
    pub display_name: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ColorSpace {
    /// Standard Dynamic Range (sRGB/Rec. 709)
    Sdr,

    /// HDR10 (Rec. 2020 + ST 2084 PQ)
    Hdr10,

    /// Dolby Vision
    DolbyVision,

    /// HDR10+ (dynamic metadata)
    Hdr10Plus,
}

impl HdrCapabilities {
    /// Check if HDR is supported
    pub fn is_supported(&self) -> bool {
        self.hdr_supported
    }

    /// Get maximum luminance
    pub fn max_luminance(&self) -> f32 {
        self.max_luminance
    }

    /// Get minimum luminance
    pub fn min_luminance(&self) -> f32 {
        self.min_luminance
    }

    /// Get maximum frame-average luminance
    pub fn max_frame_average_luminance(&self) -> f32 {
        self.max_frame_average_luminance
    }
}

impl Default for HdrCapabilities {
    fn default() -> Self {
        Self {
            hdr_supported: false,
            max_luminance: 80.0,  // Standard SDR display
            min_luminance: 0.0,
            max_frame_average_luminance: 80.0,
            color_space: ColorSpace::Sdr,
            display_name: "Unknown Display".to_string(),
        }
    }
}

/// Detect HDR capabilities of the primary display
pub fn detect_hdr_capabilities() -> Result<HdrCapabilities> {
    #[cfg(target_os = "windows")]
    return detect_windows_hdr();

    #[cfg(target_os = "macos")]
    return detect_macos_hdr();

    #[cfg(target_os = "linux")]
    return detect_linux_hdr();

    #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
    {
        warn!("HDR detection not supported on this platform");
        Ok(HdrCapabilities::default())
    }
}

#[cfg(target_os = "windows")]
fn detect_windows_hdr() -> Result<HdrCapabilities> {
    use windows::Win32::Graphics::Dxgi::{
        IDXGIFactory1, IDXGIOutput6, CreateDXGIFactory1,
    };
    use windows::Win32::Graphics::Dxgi::Common::DXGI_COLOR_SPACE_RGB_FULL_G2084_NONE_P2020;
    use windows::core::Interface;

    info!("Detecting HDR capabilities on Windows using DXGI 1.6...");

    // Create DXGI factory
    let factory: IDXGIFactory1 = unsafe {
        match CreateDXGIFactory1() {
            Ok(f) => f,
            Err(e) => {
                warn!("Failed to create DXGI factory: {:?}", e);
                return Ok(HdrCapabilities::default());
            }
        }
    };

    // Get primary adapter (GPU)
    let adapter = unsafe {
        match factory.EnumAdapters(0) {
            Ok(a) => a,
            Err(e) => {
                warn!("No adapters found: {:?}", e);
                return Ok(HdrCapabilities::default());
            }
        }
    };

    // Get primary output (display)
    let output = unsafe {
        match adapter.EnumOutputs(0) {
            Ok(o) => o,
            Err(e) => {
                warn!("No outputs found: {:?}", e);
                return Ok(HdrCapabilities::default());
            }
        }
    };

    // Try to cast to IDXGIOutput6 for HDR support
    let output6: IDXGIOutput6 = match output.cast() {
        Ok(o) => o,
        Err(e) => {
            info!("DXGI 1.6 not available (Windows 10 1703+ required): {:?}", e);
            info!("Falling back to SDR");
            return Ok(HdrCapabilities::default());
        }
    };

    // Get output description with HDR info
    let desc = unsafe {
        match output6.GetDesc1() {
            Ok(d) => d,
            Err(e) => {
                warn!("Failed to get output description: {:?}", e);
                return Ok(HdrCapabilities::default());
            }
        }
    };

    // Check if HDR is supported (ColorSpace is HDR10)
    let hdr_supported = desc.ColorSpace == DXGI_COLOR_SPACE_RGB_FULL_G2084_NONE_P2020;

    let max_luminance = desc.MaxLuminance;
    let min_luminance = desc.MinLuminance;
    let max_frame_average_luminance = desc.MaxFullFrameLuminance;

    // Get display name
    let display_name = unsafe {
        String::from_utf16_lossy(&desc.DeviceName)
            .trim_end_matches('\0')
            .to_string()
    };

    info!("Display: {}", display_name);
    info!("HDR supported: {}", hdr_supported);
    info!("Max luminance: {:.1} nits", max_luminance);
    info!("Min luminance: {:.4} nits", min_luminance);
    info!("Max full-frame luminance: {:.1} nits", max_frame_average_luminance);

    Ok(HdrCapabilities {
        hdr_supported,
        max_luminance,
        min_luminance,
        max_frame_average_luminance,
        color_space: if hdr_supported { ColorSpace::Hdr10 } else { ColorSpace::Sdr },
        display_name,
    })
}

#[cfg(target_os = "macos")]
fn detect_macos_hdr() -> Result<HdrCapabilities> {
    // macOS uses EDR (Extended Dynamic Range) instead of HDR10
    // EDR is available on all modern Macs with supported displays

    info!("Detecting EDR capabilities on macOS...");

    // Check if running on Apple Silicon or Intel with EDR-capable display
    // This requires calling into CoreGraphics/CoreDisplay

    use core_graphics::display::{CGDisplay, CGDirectDisplayID};

    let main_display = CGDirectDisplayID::main();
    let display = CGDisplay::new(main_display);

    // Check if display supports extended dynamic range
    // Note: This is a simplified check - real implementation would query display properties
    let bounds = display.bounds();

    // macOS EDR typically supports up to 1600 nits on Pro Display XDR
    // Standard displays: 500 nits, Pro Display XDR: 1600 nits peak
    let is_xdr = bounds.size.width >= 3840.0; // Heuristic for Pro Display XDR

    let capabilities = if is_xdr {
        HdrCapabilities {
            hdr_supported: true,
            max_luminance: 1600.0,
            min_luminance: 0.0005,
            max_frame_average_luminance: 1000.0,
            color_space: ColorSpace::Hdr10, // macOS uses EDR but compatible with HDR10
            display_name: "macOS Display (EDR)".to_string(),
        }
    } else {
        // Standard Retina display with basic EDR
        HdrCapabilities {
            hdr_supported: false, // Conservative - enable if confirmed EDR capable
            max_luminance: 500.0,
            min_luminance: 0.001,
            max_frame_average_luminance: 500.0,
            color_space: ColorSpace::Sdr,
            display_name: "macOS Display".to_string(),
        }
    };

    info!("macOS Display: {} x {}", bounds.size.width, bounds.size.height);
    info!("EDR Support: {}", capabilities.hdr_supported);
    info!("Max Luminance: {} nits", capabilities.max_luminance);

    Ok(capabilities)
}

#[cfg(target_os = "linux")]
fn detect_linux_hdr() -> Result<HdrCapabilities> {
    info!("Detecting HDR capabilities on Linux...");

    // Linux HDR support varies by compositor:
    // - Wayland: KDE Plasma 5.27+, GNOME 46+ (experimental)
    // - X11: Limited/no HDR support

    // Check for Wayland and HDR support via environment variables
    let session_type = std::env::var("XDG_SESSION_TYPE").unwrap_or_default();
    let is_wayland = session_type == "wayland";

    if !is_wayland {
        warn!("X11 detected - HDR not supported (requires Wayland)");
        return Ok(HdrCapabilities::default());
    }

    // On Wayland, we need to check compositor capabilities
    // This requires querying Wayland protocols (wlroots, KWin, etc.)

    // For now, return conservative defaults
    // Real implementation would query via Wayland protocols

    info!("Wayland detected - HDR may be supported");
    info!("Note: HDR support on Linux requires KDE Plasma 5.27+ or GNOME 46+");

    // Conservative: assume HDR available but with standard values
    Ok(HdrCapabilities {
        hdr_supported: false, // Set to true once confirmed via Wayland protocols
        max_luminance: 1000.0, // Typical HDR display
        min_luminance: 0.0005,
        max_frame_average_luminance: 400.0,
        color_space: ColorSpace::Sdr,
        display_name: "Linux Display (Wayland)".to_string(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_detect_capabilities() {
        let caps = detect_hdr_capabilities();
        assert!(caps.is_ok());

        let caps = caps.unwrap();
        println!("Detected capabilities: {:?}", caps);

        assert!(caps.max_luminance > 0.0);
        assert!(caps.max_luminance >= caps.min_luminance);
    }
}
