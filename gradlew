#!/usr/bin/env sh
set -eu

# Lightweight bootstrap used by this repository. GitHub Actions installs Gradle 8.9
# with gradle/actions/setup-gradle before invoking this file.
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi

GRADLE_VERSION=8.9
CACHE_ROOT="${HOME:-.}/.gradle/root-ca-toggle-bootstrap"
GRADLE_HOME="$CACHE_ROOT/gradle-$GRADLE_VERSION"
ZIP="$CACHE_ROOT/gradle-$GRADLE_VERSION-bin.zip"

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$CACHE_ROOT"
  if ! command -v curl >/dev/null 2>&1 || ! command -v unzip >/dev/null 2>&1; then
    echo "Gradle is not installed. Install Gradle 8.9, or install curl+unzip so this bootstrap can fetch it." >&2
    exit 1
  fi
  curl -fL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ZIP"
  rm -rf "$GRADLE_HOME"
  unzip -q "$ZIP" -d "$CACHE_ROOT"
fi

exec "$GRADLE_HOME/bin/gradle" "$@"
