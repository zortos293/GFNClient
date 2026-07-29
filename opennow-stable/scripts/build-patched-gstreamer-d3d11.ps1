param(
  [Parameter(Mandatory = $true)]
  [string] $GStreamerRoot,
  [Parameter(Mandatory = $true)]
  [string] $Version
)

$ErrorActionPreference = "Stop"
$patch = Resolve-Path (Join-Path $PSScriptRoot "..\..\native\gstreamer-patches\0001-d3d11-enable-tearing-vrr.patch")
$workRoot = Join-Path ([System.IO.Path]::GetTempPath()) "opennow-gstreamer-$Version"
$archive = Join-Path $workRoot "gstreamer-$Version.tar.gz"
$sourceRoot = Join-Path $workRoot "gstreamer-$Version"
$buildRoot = Join-Path $workRoot "build"
$sourceUrl = "https://gitlab.freedesktop.org/gstreamer/gstreamer/-/archive/$Version/gstreamer-$Version.tar.gz"
$sourceMirrorUrl = "https://github.com/GStreamer/gstreamer/archive/refs/tags/$Version.tar.gz"

Remove-Item -Recurse -Force $workRoot -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $workRoot | Out-Null

& curl.exe --fail --location --retry 5 --retry-all-errors --output $archive $sourceUrl
if ($LASTEXITCODE -ne 0) {
  & curl.exe --fail --location --retry 5 --retry-all-errors --output $archive $sourceMirrorUrl
  if ($LASTEXITCODE -ne 0) {
    throw "Failed to download GStreamer $Version source."
  }
}

& tar.exe -xzf $archive -C $workRoot
if ($LASTEXITCODE -ne 0) {
  throw "Failed to extract GStreamer $Version source."
}

& git.exe -C $sourceRoot apply --check $patch
if ($LASTEXITCODE -ne 0) {
  throw "OpenNOW D3D11 tearing patch does not apply to GStreamer $Version."
}
& git.exe -C $sourceRoot apply $patch
if ($LASTEXITCODE -ne 0) {
  throw "Failed to apply OpenNOW D3D11 tearing patch."
}

python -m pip install --disable-pip-version-check --quiet meson ninja
if ($LASTEXITCODE -ne 0) {
  throw "Failed to install Meson and Ninja."
}

$env:PKG_CONFIG_PATH = "$(Join-Path $GStreamerRoot "lib\pkgconfig");$env:PKG_CONFIG_PATH"
$env:PATH = "$(Join-Path $GStreamerRoot "bin");$env:PATH"
$badPluginsSource = Join-Path $sourceRoot "subprojects\gst-plugins-bad"

meson setup $buildRoot $badPluginsSource `
  --buildtype=release `
  --vsenv `
  -Dauto_features=disabled `
  -Dd3d11=enabled `
  -Dtests=disabled `
  -Dexamples=disabled `
  -Dtools=disabled `
  -Dintrospection=disabled `
  -Dgpl=disabled
if ($LASTEXITCODE -ne 0) {
  throw "Failed to configure the patched GStreamer D3D11 plugin."
}

meson compile -C $buildRoot gstd3d11
if ($LASTEXITCODE -ne 0) {
  throw "Failed to compile the patched GStreamer D3D11 plugin."
}

$plugin = Get-ChildItem -Path $buildRoot -Filter "gstd3d11.dll" -Recurse |
  Select-Object -First 1
if (-not $plugin) {
  throw "Patched gstd3d11.dll was not produced."
}

$pluginDestination = Join-Path $GStreamerRoot "lib\gstreamer-1.0\gstd3d11.dll"
Copy-Item -Force $plugin.FullName $pluginDestination

$d3d11Library = Get-ChildItem -Path $buildRoot -Filter "gstd3d11-1.0-0.dll" -Recurse |
  Select-Object -First 1
if ($d3d11Library) {
  Copy-Item -Force $d3d11Library.FullName (Join-Path $GStreamerRoot "bin\gstd3d11-1.0-0.dll")
}

$gstInspect = Join-Path $GStreamerRoot "bin\gst-inspect-1.0.exe"
& $gstInspect d3d11videosink
if ($LASTEXITCODE -ne 0) {
  throw "Patched GStreamer D3D11 plugin failed its gst-inspect smoke check."
}

Write-Host "Installed patched GStreamer D3D11 plugin: $pluginDestination"
