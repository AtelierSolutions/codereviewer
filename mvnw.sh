#!/usr/bin/env sh
set -eu

# Minimal Maven Wrapper for Unix (Linux/macOS).
# - Downloads maven-wrapper.jar if missing
# - Executes org.apache.maven.wrapper.MavenWrapperMain

BASEDIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_DIR="$BASEDIR/.mvn/wrapper"
PROPS_FILE="$WRAPPER_DIR/maven-wrapper.properties"
WRAPPER_JAR="$WRAPPER_DIR/maven-wrapper.jar"

if [ ! -f "$PROPS_FILE" ]; then
  echo "[ERROR] Missing $PROPS_FILE" >&2
  exit 1
fi

WRAPPER_URL=$(
  awk -F= 'tolower($1)=="wrapperurl"{print $2}' "$PROPS_FILE" | tail -n 1
)

if [ -z "${WRAPPER_URL:-}" ]; then
  echo "[ERROR] wrapperUrl not set in $PROPS_FILE" >&2
  exit 1
fi

JAVA_EXE="java"
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_EXE="$JAVA_HOME/bin/java"
fi

if ! command -v "$JAVA_EXE" >/dev/null 2>&1; then
  echo "[ERROR] Java not found. Install JDK 17+ and set JAVA_HOME or PATH." >&2
  exit 1
fi

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Downloading Maven Wrapper JAR..."
  mkdir -p "$WRAPPER_DIR"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$WRAPPER_URL" -o "$WRAPPER_JAR"
  elif command -v wget >/dev/null 2>&1; then
    wget -qO "$WRAPPER_JAR" "$WRAPPER_URL"
  else
    echo "[ERROR] Need curl or wget to download $WRAPPER_URL" >&2
    exit 1
  fi
fi

exec "$JAVA_EXE" \
  -Dmaven.multiModuleProjectDirectory="$BASEDIR" \
  -classpath "$WRAPPER_JAR" \
  org.apache.maven.wrapper.MavenWrapperMain "$@"

