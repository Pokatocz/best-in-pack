#!/bin/bash
# Usage: prodcheck.sh <loader> <minecraft version> <mod jar> [extra mods dir]
# Starts a JAR in a real (production, non-development) client: installs Fabric, Forge or NeoForge for that game
# version into a scratch game folder the way the launcher does, adds the mod (plus Fabric API on Fabric and any extra
# mods such as JEI), starts the game headless and checks that it reaches the title screen with Best in Pack loaded and
# without errors. This is the only test that runs the published file itself - including the reobfuscated (SRG) JARs
# for Forge 1.20 - 1.20.4. Release JARs carry no self-test, so by default this is a load test; for the full in-game
# self-test in a production install build with `gradle build -PwithSelfTest` and set SELFTEST=true.
# FABRIC_LOADER=0.14.9 starts Fabric with that loader instead of the newest one, FABRIC_API=<Modrinth version number>
# with that Fabric API, FORGE_BUILD=40.0.47 installs that Forge build instead of the one in tools/targets.py.
# Prints one line: "prodcheck <loader> <mc>: OK ..." or "... FAILED ..."; the game folder (PRODDIR, default
# /tmp/prodcheck) keeps run.log and title.png and is otherwise deleted unless KEEP=1.
L="$1"; MC="$2"; JAR=$(realpath "$3"); EXTRA="$4"
[ -n "$EXTRA" ] && EXTRA=$(realpath "$EXTRA")
TOOLS=$(dirname "$(realpath "$0")")
W=${PRODDIR:-/tmp/prodcheck}/$L-$MC
JV=$(python3 -c "import sys; sys.path.insert(0,'$TOOLS'); from targets import java_for; print(java_for('$MC'))")
JAVA_BIN=${JAVA_BIN:-/usr/lib/jvm/java-$JV-openjdk-amd64/bin/java}
fail() { echo "prodcheck $L $MC: FAILED $*"; exit 1; }
pkill -KILL -f "gameDir $W" 2>/dev/null
rm -rf "$W"; mkdir -p "$W/versions/$MC" "$W/mods" "$W/assets/indexes"
echo '{"profiles":{}}' > "$W/launcher_profiles.json"
# PRODCACHE=<dir>: keep the installed game (versions, libraries, assets, Fabric API) of each loader + game version
# there after its first run and copy it back for the next ones, instead of downloading and installing it again.
CACHE=; [ -n "$PRODCACHE" ] && CACHE="$PRODCACHE/$L-$MC${FABRIC_LOADER:+-loader$FABRIC_LOADER}${FABRIC_API:+-api$FABRIC_API}${FORGE_BUILD:+-forge$FORGE_BUILD}"
if [ -n "$CACHE" ] && [ -f "$CACHE/ready" ]; then
  cp -a "$CACHE/versions" "$CACHE/assets" "$CACHE/libraries" "$W/" || fail "could not copy the cached install"
  [ -d "$CACHE/mods" ] && cp -a "$CACHE/mods/." "$W/mods/"
  CACHED=1
fi
[ -z "$CACHED" ] && {
# the vanilla version (json, client jar, asset index), then what the loader needs: an installer URL for Forge and
# NeoForge, a launcher profile for Fabric (plus Fabric API from Modrinth)
INSTALLER=$(python3 - "$W" "$MC" "$L" "$TOOLS" <<'EOF'
import json, os, sys, urllib.parse, urllib.request
W, MC, L, TOOLS = sys.argv[1:5]
sys.path.insert(0, TOOLS)
from targets import FORGE, NEOFORGE
def get(u):
    return urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": "Mozilla/5.0"})).read()
man = json.loads(get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
vj = get(next(v["url"] for v in man["versions"] if v["id"] == MC))
open(os.path.join(W, "versions", MC, MC + ".json"), "wb").write(vj)
v = json.loads(vj)
open(os.path.join(W, "versions", MC, MC + ".jar"), "wb").write(get(v["downloads"]["client"]["url"]))
index = get(v["assetIndex"]["url"])
open(os.path.join(W, "assets", "indexes", v["assetIndex"]["id"] + ".json"), "wb").write(index)
# the assets like the launcher downloads them, but without the sounds and translations (hundreds of MB): the game
# only warns about those, while a missing texture or resource pack crashes 1.21.4+ on the title screen
def asset(o):
    h = o["hash"]; path = os.path.join(W, "assets", "objects", h[:2], h)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "wb").write(get("https://resources.download.minecraft.net/%s/%s" % (h[:2], h)))
from concurrent.futures import ThreadPoolExecutor
wanted = [o for k, o in json.loads(index)["objects"].items() if "/sounds/" not in k and "/lang/" not in k]
with ThreadPoolExecutor(8) as ex: list(ex.map(asset, wanted))
if L == "forge":
    f = os.environ.get("FORGE_BUILD") or FORGE[MC]   # an older Forge build on request
    print("https://maven.minecraftforge.net/net/minecraftforge/forge/%s-%s/forge-%s-%s-installer.jar" % (MC, f, MC, f))
elif L == "neoforge" and MC == "1.20.1":   # NeoForge 1.20.1 is the Forge 47 fork and runs the Forge JAR
    print("https://maven.neoforged.net/releases/net/neoforged/forge/1.20.1-47.1.106/forge-1.20.1-47.1.106-installer.jar")
elif L == "neoforge":
    n = NEOFORGE[MC]
    print("https://maven.neoforged.net/releases/net/neoforged/neoforge/%s/neoforge-%s-installer.jar" % (n, n))
else:
    loaders = json.loads(get("https://meta.fabricmc.net/v2/versions/loader/" + MC))
    lv = next((x for x in loaders if x["loader"].get("stable")), loaders[0])["loader"]["version"]
    lv = os.environ.get("FABRIC_LOADER") or lv   # an older loader on request (packs of old versions rarely update it)
    prof = get("https://meta.fabricmc.net/v2/versions/loader/%s/%s/profile/json" % (MC, lv))
    vid = json.loads(prof)["id"]
    os.makedirs(os.path.join(W, "versions", vid))
    open(os.path.join(W, "versions", vid, vid + ".json"), "wb").write(prof)
    q = "https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%s&loaders=%s" % (
        urllib.parse.quote('["%s"]' % MC), urllib.parse.quote('["fabric"]'))
    vs = json.loads(get(q))
    want = os.environ.get("FABRIC_API")
    pick = next((x for x in vs if x["version_number"] == want), vs[0]) if want else vs[0]
    f = [x for x in pick["files"] if x.get("primary")] or pick["files"]
    open(os.path.join(W, "mods", f[0]["filename"]), "wb").write(get(f[0]["url"]))
EOF
) || fail "could not download Minecraft $MC or the loader"
if [ -n "$INSTALLER" ]; then
  curl -sSfL -A "Mozilla/5.0" -o "$W/installer.jar" "$INSTALLER" || fail "installer download ($INSTALLER)"
  # The installer checks the SHA-1 of the jars it writes; zlib-ng compresses differently than classic zlib, so make
  # sure Java uses the system's classic zlib (a zlib-ng in /usr/local/lib breaks the install with "invalid outputs").
  ZLIB=$([ -e /lib/x86_64-linux-gnu/libz.so.1 ] && echo /lib/x86_64-linux-gnu)
  (cd "$W" && LD_LIBRARY_PATH=$ZLIB JAVA_TOOL_OPTIONS= "$JAVA_BIN" -jar installer.jar --installClient "$W" > install.log 2>&1) \
    || { tail -5 "$W/install.log"; fail "loader install"; }
fi
}
VID=$(ls "$W/versions" | grep -v "^$MC$" | head -1)
[ -n "$VID" ] || fail "no loader version installed"
[ -n "$CACHE" ] && [ -z "$CACHED" ] && { rm -rf "$CACHE"; mkdir -p "$CACHE/mods"; cp -a "$W/mods/." "$CACHE/mods/"; }
cp "$JAR" "$W/mods/"
[ -n "$EXTRA" ] && cp "$EXTRA"/*.jar "$W/mods/" 2>/dev/null
printf "onboardAccessibility:false\nnarrator:0\ntutorialStep:none\njoinedFirstServer:true\nskipMultiplayerWarning:true\n" > "$W/options.txt"
export DISPLAY=:99 XDG_RUNTIME_DIR=/tmp/xdg LIBGL_ALWAYS_SOFTWARE=1 JAVA_TOOL_OPTIONS=
export SDL_VIDEO_FORCE_EGL=1 SDL_VIDEO_X11_FORCE_EGL=1   # 26.3 opens its window through SDL: EGL works under Xvfb, GLX does not
mkdir -p /tmp/xdg
pgrep -x Xvfb >/dev/null || (Xvfb :99 -screen 0 1280x720x24 +extension GLX +render -noreset >/dev/null 2>&1 &)
sleep 1
START=$(date +%s)
savecache() {   # the install as it is after a run: with the libraries the launcher fetched on first start
  [ -n "$CACHE" ] && [ -z "$CACHED" ] && [ -d "$W/libraries" ] && cp -a "$W/versions" "$W/assets" "$W/libraries" "$CACHE/" && touch "$CACHE/ready"
}
if [ "$SELFTEST" = "true" ]; then
  JAVA="$JAVA_BIN" timeout ${SMOKE_TIMEOUT:-1200} python3 "$TOOLS/prodlaunch.py" "$W" "$VID" -Dbestinpack.selftest=true > "$W/run.log" 2>&1 &
  PID=$!
  # A loader that refuses the mods (a recipe viewer built for a newer game or loader version) shows an error screen
  # and the self-test never starts: stop then instead of waiting for the timeout.
  REFUSED=
  while kill -0 $PID 2>/dev/null; do
    sleep 5
    cat "$W/run.log" "$W/logs/latest.log" 2>/dev/null | grep -qE "Missing or unsupported mandatory dependencies|Incompatible mods found|Mod loading has failed|LoadingFailedException" && { REFUSED=1; sleep 5; break; }
  done
  grep -E "SMOKE" "$W/run.log" | grep -v "SMOKE: waiting" | sed -E 's/^\[[0-9:]+\] \[[^]]*\] (\[[^]]*\]: |\([^)]*\) )//' | cut -c1-300
  pkill -KILL -f "gameDir $W" 2>/dev/null; kill $PID 2>/dev/null; wait $PID 2>/dev/null
  savecache
  [ -n "$REFUSED" ] && { cat "$W/run.log" "$W/logs/latest.log" 2>/dev/null | grep -E "requires |Incompatible mods|depends on" | sed -E 's/^.*\]: //' | sort -u | head -4 | cut -c1-200; fail "the loader refused to load the mods ($VID)"; }
  grep -q "SMOKE OK" "$W/run.log" && { echo "prodcheck $L $MC: OK (self-test, $VID)"; exit 0; }
  fail "self-test ($VID)"
fi
JAVA="$JAVA_BIN" python3 "$TOOLS/prodlaunch.py" "$W" "$VID" > "$W/run.log" 2>&1 &
PID=$!
# The title screen follows the first resource reload: the texture atlases are stitched and the sound engine starts.
# Wait for that, then half a minute more so anything that breaks on the first frames shows up.
SETTLE=0; READY=
for i in $(seq 1 $(( ${LOAD_TIMEOUT:-420} / 5 ))); do
  sleep 5
  kill -0 $PID 2>/dev/null || break
  if grep -qE "textures/atlas/|Sound engine started|Error starting SoundSystem" "$W/run.log"; then
    READY=1; SETTLE=$((SETTLE + 1)); [ $SETTLE -ge 6 ] && break
  fi
done
T=$(( $(date +%s) - START ))
ALIVE=; kill -0 $PID 2>/dev/null && ALIVE=1
[ -n "$ALIVE" ] && import -display :99 -window root "$W/title.png" 2>/dev/null
pkill -KILL -f "gameDir $W" 2>/dev/null; kill $PID 2>/dev/null; wait $PID 2>/dev/null
savecache
LOG="$W/run.log"; [ -f "$W/logs/latest.log" ] && LOG="$W/run.log $W/logs/latest.log"
PROBLEMS=$(cat $LOG | grep -iE "bestinpack|best in pack" | grep -iE "ERROR|Exception|NoClassDefFound|NoSuchMethod|NoSuchField|Failed|Caused by" | sort -u | head -5)
[ -n "$PROBLEMS" ] && { echo "$PROBLEMS" | cut -c1-400; }
CRASH=$(ls "$W/crash-reports/"*.txt 2>/dev/null | head -1)
RESULT=OK
[ -n "$PROBLEMS" ] && RESULT="FAILED errors from the mod in the log"
cat $LOG | grep -qE "Mod loading has failed|LoadingFailedException|Incompatible mods found|ModLoadingException|Missing or unsupported mandatory dependencies" \
  && RESULT="FAILED the loader refused to load the mods"
cat $LOG | grep -q "Best in Pack loaded" || RESULT="FAILED 'Best in Pack loaded' is not in the log"
[ -n "$READY" ] || RESULT="FAILED the title screen was not reached in ${T}s"
[ -n "$ALIVE" ] || RESULT="FAILED the game exited by itself after ${T}s"
[ -n "$CRASH" ] && RESULT="FAILED crash report $(basename "$CRASH")"
MODS=$(ls "$W/mods" 2>/dev/null | tr '\n' ' ')
# keep only the log and the screenshot (the install is large)
mkdir -p "$W.keep" && cp "$W/run.log" "$W/install.log" "$W/title.png" "$W/logs/latest.log" $CRASH "$W.keep/" 2>/dev/null
[ -z "$KEEP" ] && { rm -rf "$W"; mv "$W.keep" "$W"; }
if [ "$RESULT" = OK ]; then
  echo "prodcheck $L $MC: OK ($VID, title screen after ${T}s, mods: $MODS)"
else
  echo "prodcheck $L $MC: $RESULT ($VID, ${T}s) - see $W"
  exit 1
fi
