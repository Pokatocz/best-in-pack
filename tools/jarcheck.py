"""Check the release JARs in dist/ before publishing: one per loader and era, the right version and Java level,
and no test code (the self-test starts xdotool with ProcessBuilder - it must never ship).
Usage: jarcheck.py [--compare <dir with the previous release's JARs>]
--compare also reports, per JAR, how many classes differ from the previous release."""
import json, os, re, struct, sys, zipfile
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from targets import ERAS, java_for, loaders_for

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VERSION = open(os.path.join(ROOT, "VERSION")).read().strip()
DIST = os.path.join(ROOT, "dist")
OLD = sys.argv[sys.argv.index("--compare") + 1] if "--compare" in sys.argv else None
# byte patterns no release class may contain: starting other programs, loading code, the test itself
FORBIDDEN = [re.compile(p) for p in (rb"java/lang/ProcessBuilder", rb"java/lang/Runtime(?![A-Za-z])", rb"xdotool",
                                     rb"URLClassLoader", rb"defineClass", rb"java/net/URL", rb"java/net/http",
                                     rb"java/net/Socket")]
MAJOR = {17: 61, 21: 65, 25: 69}


def classes(path):
    with zipfile.ZipFile(path) as z:
        return {n: z.read(n) for n in z.namelist() if n.endswith(".class")}, z.namelist(), {
            n: z.read(n).decode("utf-8", "replace") for n in z.namelist()
            if n in ("fabric.mod.json", "META-INF/mods.toml", "META-INF/neoforge.mods.toml")}


bad = 0
count = 0
for name, compile_mc, versions in ERAS:
    for loader in loaders_for(name):
        count += 1
        jar = "BestInPack-%s-%s-%s.jar" % (loader, name, VERSION)
        path = os.path.join(DIST, jar)
        problems = []
        if not os.path.exists(path):
            print("MISSING", jar); bad += 1; continue
        cls, names, meta = classes(path)
        if any("SmokeTest" in n for n in names): problems.append("contains SmokeTest")
        for n, data in cls.items():
            for pat in FORBIDDEN:
                if pat.search(data): problems.append("%s references %s" % (n.split("/")[-1], pat.pattern.decode()))
            major = struct.unpack(">H", data[6:8])[0]
            if major != MAJOR[java_for(compile_mc)]:
                problems.append("%s is class version %d, expected %d" % (n.split("/")[-1], major, MAJOR[java_for(compile_mc)]))
        nested = [n for n in names if n.endswith((".jar", ".so", ".dll", ".dylib", ".exe", ".sh", ".bat"))]
        if nested: problems.append("nested files %s" % nested)
        if loader == "fabric":
            v = json.loads(meta["fabric.mod.json"])["version"] if "fabric.mod.json" in meta else None
        else:
            toml = meta.get("META-INF/neoforge.mods.toml") or meta.get("META-INF/mods.toml") or ""
            m = re.search(r'^version\s*=\s*"([^"]+)"', toml, re.M)
            v = m.group(1) if m else None
        if v != VERSION: problems.append("metadata version %s" % v)
        # the only reference to the left-out self-test may be the guarded call in the loader entry point
        dangling = sorted(n.split("/")[-1] for n, d in cls.items() if b"com/bestinpack/client/SmokeTest" in d)
        if any(not d.startswith("BestInPack") for d in dangling): problems.append("SmokeTest referenced from %s" % dangling)
        note = ""
        if OLD:
            cand = [f for f in os.listdir(OLD) if f.startswith("BestInPack-%s-%s-" % (loader, name)) and f.endswith(".jar")]
            if cand:
                ocls, _, _ = classes(os.path.join(OLD, cand[0]))
                gone = sorted(n for n in ocls if n not in cls and "SmokeTest" not in n)
                new = sorted(n for n in cls if n not in ocls)
                diff = sorted(n.split("/")[-1] for n in cls if n in ocls and cls[n] != ocls[n])
                note = " | vs %s: %d classes, %d identical, %d changed%s%s" % (
                    cand[0].rsplit("-", 1)[1][:-4], len(cls), len(cls) - len(diff) - len(new), len(diff),
                    (", new %s" % new) if new else "", (", gone %s" % gone) if gone else "")
        print("%-4s %s (%d classes, %d bytes)%s%s" % ("OK" if not problems else "BAD", jar, len(cls),
              os.path.getsize(path), note, ("  <- " + "; ".join(sorted(set(problems))[:6])) if problems else ""))
        bad += bool(problems)
print("%d of %d JARs fine" % (count - bad, count))
sys.exit(1 if bad else 0)
