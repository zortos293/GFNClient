#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ ! -x "$ROOT/build/opennow-native" || ! -x "$ROOT/streamer/target/debug/opennow-webrtc-demo" ]]; then
  "$ROOT/scripts/build.sh"
fi

cd "$ROOT"
if [[ -z "${XDG_RUNTIME_DIR:-}" ]]; then
  export XDG_RUNTIME_DIR="${TMPDIR:-/tmp}/opennow-runtime-$UID"
  mkdir -p "$XDG_RUNTIME_DIR"
  chmod 700 "$XDG_RUNTIME_DIR"
fi
exec "$ROOT/build/opennow-native" "$@"
