#!/bin/bash
# Usage: forgeprod.sh <installer url> <minecraft version> <mod jar> [extra mods dir]
# Installs a real (production, non-development) Forge or NeoForge client into a scratch game folder with the
# official installer, adds the mod (and optional extra mods such as JEI), runs the in-game self-test headless and
# prints the SMOKE lines. Needed for Forge 1.20 – 1.20.4 and NeoForge 1.20.1, which run with SRG names: only a real
# install shows that the reobfuscated JAR works. Example:
#   forgeprod.sh https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.28/forge-1.20.1-47.4.28-installer.jar \
#       1.20.1 dist/BestInPack-forge-1.20-1.20.1-0.9.3.jar
URL="$1"; MC="$2"; JAR=$(realpath "$3"); EXTRA="$4"
[ -n "$EXTRA" ] && EXTRA=$(realpath "$EXTRA")
TOOLS=$(dirname "$(realpath "$0")")
W=${PRODDIR:-/tmp/forgeprod}/$(basename "$URL" .jar)
JAVA=${JAVA:-/usr/lib/jvm/java-17-openjdk-amd64/bin/java}
rm -rf "$W"; mkdir -p "$W/versions/$MC" "$W/mods" "$W/assets/indexes"
echo '{"profiles":{}}' > "$W/launcher_profiles.json"
# the vanilla version the installer builds on (json, client jar, asset index)
python3 - "$W" "$MC" <<'EOF'
import json, os, sys, urllib.request
W, MC = sys.argv[1:3]
get = lambda u: urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": "Mozilla/5.0"})).read()
man = json.loads(get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
vj = get(next(v["url"] for v in man["versions"] if v["id"] == MC))
open(os.path.join(W, "versions", MC, MC + ".json"), "wb").write(vj)
v = json.loads(vj)
open(os.path.join(W, "versions", MC, MC + ".jar"), "wb").write(get(v["downloads"]["client"]["url"]))
open(os.path.join(W, "assets", "indexes", v["assetIndex"]["id"] + ".json"), "wb").write(get(v["assetIndex"]["url"]))
EOF
curl -sSfL -A "Mozilla/5.0" -o "$W/installer.jar" "$URL" || { echo "installer download failed"; exit 1; }
# The installer checks the SHA-1 of the jars it writes; zlib-ng compresses differently than classic zlib, so make
# sure Java uses the system's classic zlib (a zlib-ng in /usr/local/lib breaks the install with "invalid outputs").
ZLIB=$([ -e /lib/x86_64-linux-gnu/libz.so.1 ] && echo /lib/x86_64-linux-gnu)
(cd "$W" && LD_LIBRARY_PATH=$ZLIB "$JAVA" -jar installer.jar --installClient "$W" > install.log 2>&1) || { tail -20 "$W/install.log"; exit 1; }
VID=$(ls "$W/versions" | grep -v "^$MC$" | head -1)
echo "installed $VID"
cp "$JAR" "$W/mods/"
[ -n "$EXTRA" ] && cp "$EXTRA"/*.jar "$W/mods/" 2>/dev/null
printf "onboardAccessibility:false\nnarrator:0\ntutorialStep:none\njoinedFirstServer:true\nskipMultiplayerWarning:true\n" > "$W/options.txt"
export DISPLAY=:99 XDG_RUNTIME_DIR=/tmp/xdg LIBGL_ALWAYS_SOFTWARE=1 JAVA_TOOL_OPTIONS=
mkdir -p /tmp/xdg
pgrep -x Xvfb >/dev/null || (Xvfb :99 -screen 0 1280x720x24 +extension GLX +render -noreset >/dev/null 2>&1 &)
sleep 1
JAVA="$JAVA" timeout ${SMOKE_TIMEOUT:-1200} python3 "$TOOLS/prodlaunch.py" "$W" "$VID" -Dbestinpack.selftest=true > "$W/run.log" 2>&1
echo "exit $?"
grep -E "SMOKE|Best in Pack indexed|Exception in|Mod loading has failed|LoadingFailed|Crash report|FATAL" "$W/run.log" \
  | grep -v "SMOKE: waiting" | sed -E 's/^\[[0-9:]+\] \[[^]]*\] (\[[^]]*\]: |\([^)]*\) )//' | cut -c1-300 | head -80
