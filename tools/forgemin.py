"""The oldest Forge build of each game version of an era that has everything a built JAR uses.
Usage: forgemin.py <era> <jar with Mojang names>     (the JAR tools/verify.py takes: build/classes zipped for ForgeGradle 6)
Packs of old game versions stay on the Forge build they were made with, so the JAR's lower bound in mods.toml
(FORGE_MIN in targets.py) should be a build that was really checked. Bisects Forge's build list per game version
(downloads go to a temporary folder) and prints the first build that resolves every reference."""
import os, re, shutil, sys, tempfile, urllib.request
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import verify as V
from targets import era

era_name, jar = sys.argv[1], sys.argv[2]
_, _, versions = era(era_name)
xml = urllib.request.urlopen(urllib.request.Request(
    "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml", headers={"User-Agent": "Mozilla/5.0"})).read().decode()
builds = {}
for v in re.findall(r"<version>([^<]+)</version>", xml):
    mc, f = v.split("-", 1)
    if re.fullmatch(r"[\d.]+", f): builds.setdefault(mc, []).append(f)
num = lambda f: tuple(int(x) for x in f.split("."))

mod = {}; V.index_jar(jar, mod)
refs, classrefs = set(), set()
for c in mod.values():
    for kind, owner, n, d in c["refs"]:
        if owner and owner.startswith(V.PREFIXES) and owner not in mod: refs.add((kind, owner, n, d))
    for k in c["classes"]:
        if k and k.startswith(V.PREFIXES) and k not in mod: classrefs.add(k)

def missing(mc, forge, game):
    tmp = tempfile.mkdtemp()
    try:
        V.CACHE = tmp
        V.FORGE[mc] = forge
        try:
            jars = V.loader_jars("forge", mc)
        except Exception as e:
            return ["(no universal jar: %s)" % e]
        idx = dict(game)
        for j in jars: V.index_jar(j, idx)
        ext = [k for k in idx if k.startswith(V.EXT_PREFIX["forge"])]
        out = [("class " + k) for k in sorted(classrefs) if k not in idx]
        for kind, owner, n, d in sorted(refs):
            if V.resolve(idx, owner, n, d, kind) or (owner, n) in V.PATCHED: continue
            if owner.startswith("net/minecraft/") and any((n, d) in idx[e]["methods"] for e in ext): continue
            out.append("%s %s.%s%s" % (kind, owner, n, d))
        return out
    finally:
        shutil.rmtree(tmp, ignore_errors=True)

for mc in versions:
    game = {}
    V.index_jar(os.path.join(V.ROOT, "mcjars", mc, "client-named.jar"), game)
    cpf = os.path.join(V.ROOT, "mcjars", mc, "classpath.txt")
    for lib in open(cpf).read().split():
        if "/com/mojang/" in lib and "blaze3d" not in lib: V.index_jar(lib, game)
    lst = sorted(builds[mc], key=num)
    newest = missing(mc, lst[-1], game)
    if newest:
        print("%s: even the newest build %s lacks %d: %s" % (mc, lst[-1], len(newest), newest[:3])); continue
    lo, hi = 0, len(lst) - 1          # invariant: lst[hi] resolves
    first_missing = None
    while lo < hi:
        mid = (lo + hi) // 2
        m = missing(mc, lst[mid], game)
        if m: lo = mid + 1; first_missing = (lst[mid], m)
        else: hi = mid
    line = "%s: oldest Forge with everything = %s (of %s … %s)" % (mc, lst[hi], lst[0], lst[-1])
    if first_missing: line += "; %s lacks %s" % (first_missing[0], ", ".join(x.split("/")[-1] for x in first_missing[1][:4]))
    print(line, flush=True)
