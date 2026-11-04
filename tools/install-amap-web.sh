#!/usr/bin/env bash
# Install all nonempty public AMap-Web source repositories into the project workspace.
# This does not execute source code, run npm install, or merge JS packages into Android.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
if ! command -v git >/dev/null 2>&1; then
  echo "ERROR: git is required" >&2
  exit 1
fi
echo "Installing AMap-Web reference repositories (pinned Git submodules)..."
git submodule sync --recursive
git -c protocol.file.allow=never submodule update --init --recursive --jobs 4 --depth 1
echo "Installed:"
git submodule status --recursive
echo
echo "Note: AMap-Web/amap-loca-types- is empty (no branch/commit to install)."
echo "These are Web/JS/AI skill sources; nothing has been added to Android runtime or Gradle."
