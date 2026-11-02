"""Download the runtime JEI jar for a loader + game version into a folder (for the smoke test).
Usage: testmods.py <loader> <mc> <dir>"""
import json, os, sys, urllib.request
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from targets import modrinth_id
loader, mc, out = sys.argv[1], sys.argv[2], sys.argv[3]
os.makedirs(out, exist_ok=True)
for f in os.listdir(out):
    if f.endswith(".jar"): os.remove(os.path.join(out, f))
if len(sys.argv) > 4 and sys.argv[4] == "rei":
    # REI instead of JEI: REI + Architectury + Cloth Config, newest release for this game version and loader
    import urllib.parse
    for proj in ("rei", "architectury-api", "cloth-config"):
        q = "https://api.modrinth.com/v2/project/%s/version?game_versions=%s&loaders=%s" % (
            proj, urllib.parse.quote('["%s"]' % mc), urllib.parse.quote('["%s"]' % loader))
        vs = json.load(urllib.request.urlopen(urllib.request.Request(q, headers={"User-Agent": "bestinpack-test"})))
        if not vs:
            print("no", proj, "for", loader, mc); sys.exit(0)
        f = [x for x in vs[0]["files"] if x.get("primary")] or vs[0]["files"]
        with urllib.request.urlopen(f[0]["url"]) as r, open(os.path.join(out, f[0]["filename"]), "wb") as o:
            o.write(r.read())
        print(proj, f[0]["filename"])
    sys.exit(0)
vid = modrinth_id("jei", mc, loader)
if not vid and loader == "fabric":
    from targets import JEI_FABRIC_MAVEN
    if mc in JEI_FABRIC_MAVEN:
        ver = JEI_FABRIC_MAVEN[mc]
        name = "jei-%s-fabric-%s.jar" % (mc, ver)
        url = "https://maven.blamejared.com/mezz/jei/jei-%s-fabric/%s/%s" % (mc, ver, name)
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req) as r, open(os.path.join(out, name), "wb") as o:
            o.write(r.read())
        print("JEI", name); sys.exit(0)
if not vid:
    print("no JEI for", loader, mc); sys.exit(0)
v = json.load(urllib.request.urlopen("https://api.modrinth.com/v2/version/" + vid))
f = [x for x in v["files"] if x.get("primary")] or v["files"]
url = f[0]["url"]; name = f[0]["filename"]
with urllib.request.urlopen(url) as r, open(os.path.join(out, name), "wb") as o:
    o.write(r.read())
print("JEI", name)
