"""Download client jars, mappings and libraries for every MC version; remap <=1.21.11 to Mojang names."""
import json, os, sys, urllib.request, hashlib, subprocess
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "mcjars")
LIBS = os.path.join(OUT, "libs")
VERSIONS = sys.argv[1:] or ["1.21","1.21.1","1.21.2","1.21.3","1.21.4","1.21.5","1.21.6","1.21.7","1.21.8","1.21.9","1.21.10","1.21.11","26.1","26.1.1","26.1.2","26.2","26.3"]
def get(url, path):
    if os.path.exists(path) and os.path.getsize(path) > 0: return path
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp = path + ".part"
    with urllib.request.urlopen(url) as r, open(tmp, "wb") as f:
        while True:
            b = r.read(1 << 20)
            if not b: break
            f.write(b)
    os.replace(tmp, path)
    return path
manifest = json.load(open(get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json", os.path.join(OUT, "manifest.json"))))
urls = {v["id"]: v["url"] for v in manifest["versions"]}
def allowed(lib):
    rules = lib.get("rules")
    if not rules: return True
    ok = False
    for r in rules:
        osn = r.get("os", {}).get("name")
        if osn is None or osn == "linux": ok = r["action"] == "allow"
    return ok
for v in VERSIONS:
    vd = os.path.join(OUT, v)
    vj = json.load(open(get(urls[v], os.path.join(vd, "version.json"))))
    get(vj["downloads"]["client"]["url"], os.path.join(vd, "client.jar"))
    if "client_mappings" in vj["downloads"]:
        get(vj["downloads"]["client_mappings"]["url"], os.path.join(vd, "client.txt"))
    cp = []
    for lib in vj["libraries"]:
        if not allowed(lib): continue
        art = lib.get("downloads", {}).get("artifact")
        if not art: continue
        if "natives" in art["path"]: continue
        p = os.path.join(LIBS, art["path"])
        get(art["url"], p)
        cp.append(p)
    open(os.path.join(vd, "classpath.txt"), "w").write("\n".join(cp))
    print(v, "java", vj.get("javaVersion", {}).get("majorVersion"), "libs", len(cp), "mappings" if "client_mappings" in vj["downloads"] else "unobfuscated", flush=True)
