"""Minimal production launcher (no dev environment): starts a real client from a launcher-style game folder.
Usage: prodlaunch.py <game dir> <version id> [extra JVM args...]
Resolves inheritsFrom, downloads missing libraries, builds the classpath and game arguments like the vanilla launcher."""
import json, os, subprocess, sys, urllib.request, uuid

GAME, VID = sys.argv[1], sys.argv[2]
EXTRA = sys.argv[3:]
LIB = os.path.join(GAME, "libraries")


def load(vid):
    return json.load(open(os.path.join(GAME, "versions", vid, vid + ".json")))


def fetch(url, path):
    if os.path.exists(path): return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req) as r, open(path + ".part", "wb") as f: f.write(r.read())
    os.replace(path + ".part", path)


def allowed(rules):
    if not rules: return True
    ok = False
    for r in rules:
        osr = r.get("os", {})
        if r.get("features"): continue
        match = (not osr) or (osr.get("name") == "linux" and ("arch" not in osr or osr["arch"] == "x86_64"))
        if match: ok = r["action"] == "allow"
    return ok


def maven_path(name):
    parts = name.split(":")
    g, a, v = parts[0], parts[1], parts[2]
    cls = "-" + parts[3] if len(parts) > 3 else ""
    return "%s/%s/%s/%s-%s%s.jar" % (g.replace(".", "/"), a, v, a, v, cls)


chain = []
v = load(VID)
chain.append(v)
while "inheritsFrom" in v:
    v = load(v["inheritsFrom"])
    chain.append(v)
base = chain[-1]
main = next(c["mainClass"] for c in chain if "mainClass" in c)

cp, seen = [], {}
for c in chain:  # child libraries win over the parent's (same group:artifact)
    for lib in c.get("libraries", []):
        if not allowed(lib.get("rules")): continue
        key = ":".join(lib["name"].split(":")[:2] + lib["name"].split(":")[3:])
        if key in seen: continue
        art = lib.get("downloads", {}).get("artifact")
        if art:
            path = os.path.join(LIB, art["path"]); fetch(art["url"], path)
        elif "url" in lib:
            rel = maven_path(lib["name"]); path = os.path.join(LIB, rel); fetch(lib["url"].rstrip("/") + "/" + rel, path)
        else:
            continue
        seen[key] = path
        cp.append(path)
cp.append(os.path.join(GAME, "versions", base["id"], base["id"] + ".jar"))

sub = {"${auth_player_name}": "Tester", "${version_name}": VID, "${game_directory}": GAME,
       "${assets_root}": os.path.join(GAME, "assets"), "${assets_index_name}": base["assetIndex"]["id"],
       "${auth_uuid}": str(uuid.uuid4()), "${auth_access_token}": "0", "${clientid}": "0", "${auth_xuid}": "0",
       "${user_type}": "legacy", "${version_type}": "release", "${natives_directory}": os.path.join(GAME, "natives"),
       "${launcher_name}": "bip-test", "${launcher_version}": "1", "${classpath}": os.pathsep.join(cp),
       "${library_directory}": LIB, "${classpath_separator}": os.pathsep}


def expand(args):
    out = []
    for a in args:
        if isinstance(a, dict):
            if not allowed(a.get("rules")): continue
            vals = a["value"] if isinstance(a["value"], list) else [a["value"]]
        else:
            vals = [a]
        for x in vals:
            for k, val in sub.items(): x = x.replace(k, val)
            out.append(x)
    return out


jvm, game = [], []
for c in reversed(chain):
    jvm += expand(c.get("arguments", {}).get("jvm", []))
    game += expand(c.get("arguments", {}).get("game", []))
cmd = [os.environ.get("JAVA", "java"), "-Xmx3G"] + EXTRA + jvm + [main] + game
os.makedirs(os.path.join(GAME, "natives"), exist_ok=True)
print("launching", main, "with", len(cp), "classpath entries", flush=True)
sys.exit(subprocess.call(cmd, cwd=GAME))
