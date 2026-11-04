#!/usr/bin/env bash
# Fetch all pinned third-party map references without executing their code.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
git submodule sync --recursive
git -c protocol.file.allow=never submodule update --init --recursive --jobs 4
echo "Installed map reference sources:"
git submodule status --recursive
echo
echo "All sources are isolated in third_party; Android/Gradle dependencies remain unchanged."
