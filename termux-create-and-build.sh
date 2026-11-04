#!/data/data/com.termux/files/usr/bin/bash
# NaviTH - create a new private GitHub repository and start an APK build.
# Runs ONLY on your own phone with your own GitHub login. No credentials saved to Git.
set -euo pipefail

OWNER="Natreethon"
NAME="NaviTH-AMAP"
REPO="$OWNER/$NAME"

if ! command -v gh >/dev/null 2>&1; then
  echo "Missing GitHub CLI. Install with: pkg install gh git unzip -y" >&2
  exit 1
fi
if ! command -v git >/dev/null 2>&1; then
  echo "Missing git. Install with: pkg install git -y" >&2
  exit 1
fi
if [ ! -f "app/build.gradle" ] || [ ! -f ".github/workflows/build-android.yml" ]; then
  echo "Run this file INSIDE the unpacked NaviTH-AMAP project directory." >&2
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "Sign into your GitHub account. Browser authentication will be required."
  gh auth login --web --git-protocol https
fi
ME="$(gh api user --jq .login)"
if [ "$ME" != "$OWNER" ]; then
  echo "Logged in as '$ME', but this script targets '$OWNER'. Nothing changed." >&2
  exit 1
fi
if gh repo view "$REPO" >/dev/null 2>&1; then
  echo "Repository $REPO already exists. Nothing changed to avoid accidental overwriting." >&2
  exit 1
fi

git init -b main
git add .
if ! git diff --cached --quiet; then
  git -c user.name="$ME" -c user.email="$ME@users.noreply.github.com" \
      commit -m "Initial NaviTH Android Auto preview"
fi

echo "Paste your AMAP Android API key (not shown on screen):"
read -r -s AMAP_KEY
printf '\n'
if [ -z "$AMAP_KEY" ]; then
  echo "No key entered. Nothing has been created." >&2
  exit 2
fi

echo "Creating PRIVATE repository: $REPO"
gh repo create "$REPO" --private --source=. --remote=origin
printf '%s' "$AMAP_KEY" | gh secret set AMAP_KEY -R "$REPO"
unset AMAP_KEY

echo "Uploading source code and triggering APK build: $REPO"
git push --set-upstream origin main

echo "Repository created. GitHub Actions Build runs automatically on push."
echo "Open: https://github.com/$REPO/actions"
echo "When build completes, choose the workflow run and download NaviTH-debug-APK."
