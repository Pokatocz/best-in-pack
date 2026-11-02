#!/bin/bash
# Usage: smoke.sh <build dir> [extra mods dir]   – runs the in-game smoke test headless and prints the SMOKE lines.
B="$1"; EXTRA="$2"
[ -n "$EXTRA" ] && EXTRA=$(realpath "$EXTRA")
cd "$B" || exit 1
JAVA=21; grep -q "JavaLanguageVersion.of(25)" build.gradle && JAVA=25
# Tools (override with environment variables): Gradle 9.5+ for NeoForge/Forge, Gradle 9.8+ on JDK 25 for Fabric (Loom 1.19)
GRADLE=${GRADLE:-gradle}; GRADLE_FABRIC=${GRADLE_FABRIC:-$GRADLE}
JAVA21_HOME=${JAVA21_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}; JAVA25_HOME=${JAVA25_HOME:-/usr/lib/jvm/java-25-openjdk-amd64}
export JAVA_TOOL_OPTIONS=; [ $JAVA = 25 ] && export JAVA_HOME=$JAVA25_HOME || export JAVA_HOME=$JAVA21_HOME
export DISPLAY=:99 XDG_RUNTIME_DIR=/tmp/xdg LIBGL_ALWAYS_SOFTWARE=1 SDL_VIDEO_FORCE_EGL=1 SDL_VIDEO_X11_FORCE_EGL=1
mkdir -p /tmp/xdg
pgrep -x Xvfb >/dev/null || (Xvfb :99 -screen 0 1280x720x24 +extension GLX +render -noreset >/dev/null 2>&1 &)
sleep 1
rm -rf run/saves run/config/bestinpack run/screenshots
# SMOKE_CONFIG='{"openKey": "N"}' starts the game with that bestinpack.json (like a modpack shipping one)
# (and like a fresh install: no key binding of ours saved in options.txt yet)
[ -n "$SMOKE_CONFIG" ] && mkdir -p run/config/bestinpack && printf '%s' "$SMOKE_CONFIG" > run/config/bestinpack/bestinpack.json \
  && sed -i '/key_key.bestinpack/d' run/options.txt 2>/dev/null
mkdir -p run/mods
rm -f run/mods/*.jar
[ -n "$EXTRA" ] && cp "$EXTRA"/*.jar run/mods/ 2>/dev/null
grep -q onboardAccessibility run/options.txt 2>/dev/null || printf "onboardAccessibility:false\nnarrator:0\ntutorialStep:none\njoinedFirstServer:true\nskipMultiplayerWarning:true\n" >> run/options.txt
G=$GRADLE
grep -q "fabric-loom" build.gradle && export JAVA_HOME=$JAVA25_HOME && G=$GRADLE_FABRIC
TASK=runClient
timeout ${SMOKE_TIMEOUT:-1500} $G $TASK -Dbestinpack.selftest=true --console=plain > smoke.log 2>&1
echo "exit $?"
grep -E "SMOKE|Best in Pack indexed|Exception in|Mod loading has failed|LoadingFailed|Crash report|FATAL" smoke.log | grep -v "SMOKE: waiting" | sed -E 's/^\[[0-9:]+\] \[[^]]*\] (\[[^]]*\]: |\([^)]*\) )//' | cut -c1-300 | head -80
