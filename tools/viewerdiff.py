"""What the recipe-viewer bridges of a built JAR use that a given release of JEI / REI / EMI does not have.
Usage: viewerdiff.py <built jar> <dir for downloads> <project> <loader> <mc>..."""
import json, os, sys, urllib.request
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import verify as V
from targets import table
PFX = {"jei": "mezz/jei/", "rei": "me/shedaniel/rei/", "emi": "dev/emi/"}
jar, dl, project, loader = sys.argv[1:5]
mod = {}; V.index_jar(jar, mod)
refs, classes = set(), set()
for c in mod.values():
    for kind, owner, n, d in c["refs"]:
        if owner and owner.startswith(PFX[project]): refs.add((kind, owner, n, d))
    for k in c["classes"]:
        if k and k.startswith(PFX[project]): classes.add(k)
for mc in sys.argv[5:]:
    e = table(project).get(mc, {}).get(loader)
    if not e:
        print("== %s %s %s: no release" % (project, loader, mc)); continue
    vid, ver = e.split("|")
    path = os.path.join(dl, "%s-%s-%s.jar" % (project, loader, ver.replace("+", "_")))
    if not os.path.exists(path):
        v = json.load(urllib.request.urlopen(urllib.request.Request("https://api.modrinth.com/v2/version/" + vid, headers={"User-Agent": "bestinpack-build"})))
        f = [x for x in v["files"] if x.get("primary")] or v["files"]
        with urllib.request.urlopen(f[0]["url"]) as r, open(path, "wb") as o: o.write(r.read())
    idx = {}; V.index_jar(path, idx)
    missing = []
    gone = set(k for k in classes if k not in idx)
    for k in sorted(gone): missing.append("class " + k)
    for kind, owner, n, d in sorted(refs):
        if owner in gone: continue
        # names only: descriptors mention game classes whose names differ per mapping
        seen, stack, found, other = set(), [owner], False, None
        while stack and not found:
            c = stack.pop()
            if c in seen or c not in idx: continue
            seen.add(c)
            members = idx[c]["methods"] if kind == "method" else idx[c]["fields"]
            if (n, d) in members: found = True
            elif any(m[0] == n for m in members): other = sorted(m[1] for m in members if m[0] == n)
            stack += [x for x in [idx[c]["super"]] + idx[c]["ifaces"] if x]
        if not found: missing.append("%s %s.%s%s%s" % (kind, owner, n, d, ("   (has: " + " | ".join(other) + ")") if other else ""))
    print("== %s %s %s (%s): %d missing of %d" % (project, loader, mc, ver, len(missing), len(refs)))
    for m in missing: print("    " + m)
