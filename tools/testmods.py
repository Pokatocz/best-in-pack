"""Download the runtime JEI jar for a loader + game version into a folder (for the smoke test).
Usage: testmods.py <loader> <mc> <dir> [rei|emi|emi+jei]   – REI (with its libraries) or EMI instead of JEI, or EMI next to JEI
JEI_VERSION=<Modrinth version number> picks that JEI release instead of the one the build was compiled against."""
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
        if not vs and proj == "rei" and mc in ("1.18", "1.18.1"):
            # REI 7 (1.18 - 1.18.1) is not on Modrinth: from its author's maven, like the build takes it
            from targets import REI_MAVEN
            ver = REI_MAVEN["1.18.1"][0]
            name = "RoughlyEnoughItems-%s-%s.jar" % (loader, ver)
            url = "https://maven.shedaniel.me/me/shedaniel/RoughlyEnoughItems-%s/%s/%s" % (loader, ver, name)
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "bestinpack-test"})) as r, open(os.path.join(out, name), "wb") as o:
                o.write(r.read())
            print(proj, name); continue
        if not vs:
            print("no", proj, "for", loader, mc); sys.exit(0)
        f = [x for x in vs[0]["files"] if x.get("primary")] or vs[0]["files"]
        with urllib.request.urlopen(f[0]["url"]) as r, open(os.path.join(out, f[0]["filename"]), "wb") as o:
            o.write(r.read())
        print(proj, f[0]["filename"])
    sys.exit(0)
if len(sys.argv) > 4 and sys.argv[4] in ("emi", "emi+jei"):
    # EMI (it needs no library mods): the release this build was compiled against, else the newest for this version
    import urllib.parse
    evid = modrinth_id("emi", mc, loader)
    if evid:
        v = json.load(urllib.request.urlopen(urllib.request.Request("https://api.modrinth.com/v2/version/" + evid, headers={"User-Agent": "bestinpack-test"})))
    else:
        q = "https://api.modrinth.com/v2/project/emi/version?game_versions=%s&loaders=%s" % (
            urllib.parse.quote('["%s"]' % mc), urllib.parse.quote('["%s"]' % loader))
        vs = json.load(urllib.request.urlopen(urllib.request.Request(q, headers={"User-Agent": "bestinpack-test"})))
        if not vs:
            print("no EMI for", loader, mc); sys.exit(0)
        v = vs[0]
    f = [x for x in v["files"] if x.get("primary")] or v["files"]
    with urllib.request.urlopen(f[0]["url"]) as r, open(os.path.join(out, f[0]["filename"]), "wb") as o:
        o.write(r.read())
    print("EMI", f[0]["filename"])
    if sys.argv[4] == "emi": sys.exit(0)
vid = modrinth_id("jei", mc, loader)
if os.environ.get("JEI_VERSION"):
    # a chosen JEI release instead of the newest one (old game versions: packs run anything from JEI 9.0 to 13)
    import urllib.parse
    q = "https://api.modrinth.com/v2/project/jei/version?game_versions=%s&loaders=%s" % (
        urllib.parse.quote('["%s"]' % mc), urllib.parse.quote('["%s"]' % loader))
    vs = json.load(urllib.request.urlopen(urllib.request.Request(q, headers={"User-Agent": "bestinpack-test"})))
    hit = [x for x in vs if x["version_number"] == os.environ["JEI_VERSION"]]
    if not hit:
        print("no JEI", os.environ["JEI_VERSION"], "for", loader, mc, "- there is:", ", ".join(x["version_number"] for x in vs[:12])); sys.exit(0)
    vid = hit[0]["id"]
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
