"""What a built JAR uses that a game version does not have:  apidiff.py <loader> <jar> <mc version>...
Like verify.py, but for any version and with the whole list (grouped by class) - the to-do list of a port."""
import os, sys, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import verify as V

def diff(loader, jar, v):
    mod = {}; V.index_jar(jar, mod)
    refs, classrefs = set(), set()
    for c in mod.values():
        for kind, owner, n, d in c["refs"]:
            if owner and owner.startswith(V.PREFIXES) and owner not in mod: refs.add((kind, owner, n, d, c["name"]))
        for k in c["classes"]:
            if k and k.startswith(V.PREFIXES) and k not in mod: classrefs.add((k, c["name"]))
    idx = {}
    V.index_jar(os.path.join(V.ROOT, "mcjars", v, "client-named.jar"), idx)
    cpf = os.path.join(V.ROOT, "mcjars", v, "classpath.txt")
    for lib in open(cpf).read().split():
        if "/com/mojang/" in lib and "blaze3d" not in lib: V.index_jar(lib, idx)
    for lj in V.loader_jars(loader, v): V.index_jar(lj, idx)
    ext = [k for k in idx if loader in V.EXT_PREFIX and k.startswith(V.EXT_PREFIX[loader])]
    missing = collections.defaultdict(set)
    gone = set()
    for k, user in sorted(classrefs):
        if k not in idx: gone.add(k); missing["class " + k].add(user.split("/")[-1])
    for kind, owner, n, d, user in sorted(refs):
        if owner in gone: continue
        if V.resolve(idx, owner, n, d, kind): continue
        if (owner, n) in V.PATCHED: continue
        if owner.startswith("net/minecraft/") and any((n, d) in idx[e]["methods"] for e in ext): continue
        missing["%s %s.%s%s" % (kind, owner, n, d)].add(user.split("/")[-1])
    print("== %s %s: %d missing (of %d refs)" % (loader, v, len(missing), len(refs)))
    for m in sorted(missing): print("   %s   <- %s" % (m, ", ".join(sorted(missing[m]))))

if __name__ == "__main__":
    for v in sys.argv[3:]: diff(sys.argv[1], sys.argv[2], v)
