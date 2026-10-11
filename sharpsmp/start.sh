#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SERVER="$ROOT/server"
PLUGIN="$ROOT/plugin"
mkdir -p "$SERVER/plugins"

find_java() {
  if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
    echo "$JAVA_HOME"
    return
  fi
  local candidate
  for candidate in \
      /usr/lib/jvm/java-25-openjdk-amd64 \
      "$HOME/.local/share/PrismLauncher/java/java-runtime-epsilon"
  do
    if [ -x "$candidate/bin/java" ]; then
      echo "$candidate"
      return
    fi
  done
  echo ""
}

JAVA_HOME="$(find_java)"
if [ -z "$JAVA_HOME" ]; then
  echo "Java 25 was not found. Install a JDK 25 and set JAVA_HOME."
  exit 1
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

if [ ! -x "$JAVA_HOME/bin/javac" ]; then
  echo "JAVA_HOME is a runtime only. SharpSMP needs a JDK 25 to compile."
  exit 1
fi

if [ ! -f "$SERVER/paper.jar" ]; then
  echo "Downloading Paper 26.3..."
  BUILD="$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/26.3" | python3 -c 'import json,sys; print(json.load(sys.stdin)["builds"][0])')"
  URL="$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/26.3/builds/${BUILD}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["downloads"]["server:default"]["url"])')"
  curl -fL --retry 3 -o "$SERVER/paper.jar" "$URL"
fi

echo "Building SharpSMP..."
(cd "$PLUGIN" && mvn -q package)
cp "$PLUGIN/target/SharpSMP.jar" "$SERVER/plugins/SharpSMP.jar"

cd "$SERVER"
echo "Starting SharpSMP on port 25565. Stop the server with 'stop'."
exec "$JAVA_HOME/bin/java" -Xms1G -Xmx2G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 \
  -jar paper.jar --nogui
