# FFmpeg Setup Script for Windows
# Automatically downloads and configures FFmpeg for the project

$ErrorActionPreference = "Stop"

Write-Host "==================================" -ForegroundColor Cyan
Write-Host "FFmpeg Setup for OpenNOW HDR" -ForegroundColor Cyan
Write-Host "==================================" -ForegroundColor Cyan
Write-Host ""

# Check if FFmpeg is already installed
$ffmpegInstalled = Get-Command ffmpeg -ErrorAction SilentlyContinue
if ($ffmpegInstalled) {
    Write-Host "✓ FFmpeg is already installed at: $($ffmpegInstalled.Path)" -ForegroundColor Green
    Write-Host ""
    Write-Host "Checking version..." -ForegroundColor Yellow
    ffmpeg -version | Select-Object -First 1
    Write-Host ""
    $response = Read-Host "Do you want to reinstall? (y/N)"
    if ($response -ne "y" -and $response -ne "Y") {
        Write-Host "Using existing FFmpeg installation." -ForegroundColor Green
        exit 0
    }
}

# Check for vcpkg
Write-Host "Checking for vcpkg..." -ForegroundColor Yellow
$vcpkgRoot = $env:VCPKG_ROOT
if ($vcpkgRoot -and (Test-Path "$vcpkgRoot\vcpkg.exe")) {
    Write-Host "✓ Found vcpkg at: $vcpkgRoot" -ForegroundColor Green
    Write-Host ""
    Write-Host "Installing FFmpeg via vcpkg..." -ForegroundColor Yellow
    & "$vcpkgRoot\vcpkg.exe" install ffmpeg:x64-windows

    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ FFmpeg installed successfully via vcpkg!" -ForegroundColor Green
        Write-Host ""
        Write-Host "Integrating vcpkg with Visual Studio..." -ForegroundColor Yellow
        & "$vcpkgRoot\vcpkg.exe" integrate install
        Write-Host ""
        Write-Host "✓ Setup complete! You can now build the project." -ForegroundColor Green
        exit 0
    } else {
        Write-Host "✗ vcpkg installation failed. Falling back to manual installation..." -ForegroundColor Yellow
    }
}

# Manual installation
Write-Host ""
Write-Host "Installing FFmpeg manually..." -ForegroundColor Yellow
Write-Host ""

$ffmpegDir = "C:\ffmpeg"
$downloadUrl = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl-shared.zip"
$zipFile = "$env:TEMP\ffmpeg.zip"

# Create directory
if (Test-Path $ffmpegDir) {
    Write-Host "Removing existing FFmpeg installation at $ffmpegDir..." -ForegroundColor Yellow
    Remove-Item -Path $ffmpegDir -Recurse -Force
}
New-Item -ItemType Directory -Path $ffmpegDir -Force | Out-Null

# Download FFmpeg
Write-Host "Downloading FFmpeg from GitHub..." -ForegroundColor Yellow
Write-Host "URL: $downloadUrl" -ForegroundColor Gray
try {
    Invoke-WebRequest -Uri $downloadUrl -OutFile $zipFile -UseBasicParsing
    Write-Host "✓ Download complete!" -ForegroundColor Green
} catch {
    Write-Host "✗ Failed to download FFmpeg: $_" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please download manually from:" -ForegroundColor Yellow
    Write-Host "https://github.com/BtbN/FFmpeg-Builds/releases" -ForegroundColor Cyan
    exit 1
}

# Extract
Write-Host ""
Write-Host "Extracting FFmpeg..." -ForegroundColor Yellow
try {
    # Clean up any previous extraction attempts first to be safe
    if (Test-Path "$env:TEMP\ffmpeg-extract") {
        Remove-Item -Path "$env:TEMP\ffmpeg-extract" -Recurse -Force
    }

    Expand-Archive -Path $zipFile -DestinationPath "$env:TEMP\ffmpeg-extract" -Force

    # Find the extracted folder (it has a version suffix)
    $extractedFolder = Get-ChildItem -Path "$env:TEMP\ffmpeg-extract" -Directory | Select-Object -First 1

    if (-not $extractedFolder) {
        throw "Could not find extracted folder in $env:TEMP\ffmpeg-extract"
    }

    # KORREKTUR HIER: Wir nutzen .FullName um sicherzustellen, dass der absolute Pfad genutzt wird
    $sourcePath = Join-Path -Path $extractedFolder.FullName -ChildPath "*"
    
    Write-Host "Moving files from: $sourcePath" -ForegroundColor Gray
    
    # Move contents to C:\ffmpeg
    Move-Item -Path $sourcePath -Destination $ffmpegDir -Force

    # Cleanup
    Remove-Item -Path "$env:TEMP\ffmpeg-extract" -Recurse -Force
    Remove-Item -Path $zipFile -Force

    Write-Host "✓ Extraction complete!" -ForegroundColor Green
} catch {
    Write-Host "✗ Failed during extraction/move: $_" -ForegroundColor Red
    exit 1
}

# Set environment variables
Write-Host ""
Write-Host "Configuring environment variables..." -ForegroundColor Yellow

# Set FFMPEG_DIR for current session
$env:FFMPEG_DIR = $ffmpegDir
Write-Host "Set FFMPEG_DIR=$ffmpegDir" -ForegroundColor Gray

# Add to PATH for current session
$env:PATH = "$ffmpegDir\bin;$env:PATH"
Write-Host "Added $ffmpegDir\bin to PATH" -ForegroundColor Gray

# Set persistent environment variables (user level)
try {
    [Environment]::SetEnvironmentVariable("FFMPEG_DIR", $ffmpegDir, "User")

    $currentPath = [Environment]::GetEnvironmentVariable("PATH", "User")
    if ($currentPath -notlike "*$ffmpegDir\bin*") {
        [Environment]::SetEnvironmentVariable("PATH", "$ffmpegDir\bin;$currentPath", "User")
    }

    Write-Host "✓ Environment variables set permanently!" -ForegroundColor Green
} catch {
    Write-Host "⚠ Could not set persistent environment variables." -ForegroundColor Yellow
    Write-Host "  You may need to run this script as Administrator." -ForegroundColor Yellow
}

# Verify installation
Write-Host ""
Write-Host "Verifying installation..." -ForegroundColor Yellow
$ffmpegExe = "$ffmpegDir\bin\ffmpeg.exe"
if (Test-Path $ffmpegExe) {
    Write-Host "✓ FFmpeg executable found!" -ForegroundColor Green
    & $ffmpegExe -version | Select-Object -First 1
} else {
    Write-Host "✗ FFmpeg executable not found at $ffmpegExe" -ForegroundColor Red
    exit 1
}

# Success message
Write-Host ""
Write-Host "==================================" -ForegroundColor Cyan
Write-Host "✓ FFmpeg Setup Complete!" -ForegroundColor Green
Write-Host "==================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "FFmpeg is installed at: $ffmpegDir" -ForegroundColor White
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "1. Restart your terminal/IDE to load new environment variables" -ForegroundColor White
Write-Host "2. Run: cd src-tauri" -ForegroundColor White
Write-Host "3. Run: cargo build" -ForegroundColor White
Write-Host ""
Write-Host "Note: If you get errors, restart PowerShell and try building again." -ForegroundColor Gray