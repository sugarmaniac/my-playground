#!/usr/bin/env bash
# Cloud oturumlarında Android SDK'yı kurar ve Gradle'ı Maven Central aynasına yönlendirir.
# Yerelde hiçbir şey yapmaz; Android Studio'nun SDK'sı kullanılır.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-.}"

SDK_DIR="${ANDROID_HOME:-$HOME/android-sdk}"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-16111833_latest.zip"
PAKETLER=("platform-tools" "platforms;android-37.0" "build-tools;37.0.0")

# Kurulum yalnızca bu script değiştiğinde yeniden yapılır.
hashes="$SDK_DIR/.session-start.sha256"
if ! sha256sum --check --status "$hashes" 2>/dev/null; then
  rm -f "$hashes"

  if [ ! -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
    mkdir -p "$SDK_DIR/cmdline-tools"
    gecici="$(mktemp -d)"
    curl -sSfL "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP" -o "$gecici/tools.zip"
    unzip -q "$gecici/tools.zip" -d "$gecici"
    rm -rf "$SDK_DIR/cmdline-tools/latest"
    mv "$gecici/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
    rm -rf "$gecici"
  fi

  "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK_DIR" "${PAKETLER[@]}" > /dev/null

  sha256sum "$PWD/.claude/hooks/session-start.sh" > "$hashes"
fi

# Cloud oturumlarının çıkış IP'si Maven Central'da sık sık 429 (rate limit) alıyor;
# yalnızca bu oturum için Google'ın Maven Central aynasını öne ekle.
mkdir -p "$HOME/.gradle/init.d"
cat > "$HOME/.gradle/init.d/maven-central-aynasi.init.gradle.kts" <<'INIT'
val ayna = "https://maven-central.storage-download.googleapis.com/maven2/"
beforeSettings {
    pluginManagement.repositories { maven(ayna) }
    dependencyResolutionManagement.repositories { maven(ayna) }
}
INIT

# Backend testleri Testcontainers ile gerçek PostgreSQL çalıştırır; Docker daemon'ı ayakta olmalı.
if command -v dockerd > /dev/null && ! docker info > /dev/null 2>&1; then
  setsid nohup dockerd > /tmp/dockerd.log 2>&1 < /dev/null &
  for _ in $(seq 1 30); do docker info > /dev/null 2>&1 && break; sleep 1; done
fi

echo "sdk.dir=$SDK_DIR" > local.properties
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo "export ANDROID_HOME=\"$SDK_DIR\"" >> "$CLAUDE_ENV_FILE"
fi
