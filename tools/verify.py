"""Resolve every Minecraft / loader reference of a built JAR against each game version of its era.
Usage: verify.py <loader> <era> <jar>
Mojang-named JARs only (Forge, NeoForge, Fabric 26.x, or a Fabric dev JAR)."""
import os, struct, sys, zipfile, urllib.request, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from targets import era, NEOFORGE, FORGE

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, "mcjars", "loaderjars")


def parse_class(data):
    off = 8; n = struct.unpack_from(">H", data, off)[0]; off += 2; cp = [None] * n; i = 1
    while i < n:
        tag = data[off]; off += 1
        if tag == 1:
            ln = struct.unpack_from(">H", data, off)[0]; off += 2
            cp[i] = ("utf8", data[off:off + ln].decode("utf-8", "replace")); off += ln
        elif tag in (3, 4): off += 4
        elif tag in (5, 6): off += 8; i += 1
        elif tag == 7: cp[i] = ("class", struct.unpack_from(">H", data, off)[0]); off += 2
        elif tag == 8: off += 2
        elif tag in (9, 10, 11):
            a, b = struct.unpack_from(">HH", data, off); off += 4
            cp[i] = ({9: "field", 10: "method", 11: "method"}[tag], a, b)
        elif tag == 12:
            a, b = struct.unpack_from(">HH", data, off); off += 4; cp[i] = ("nat", a, b)
        elif tag == 15: off += 3
        elif tag in (16, 19, 20): off += 2
        elif tag in (17, 18): off += 4
        else: raise ValueError(tag)
        i += 1
    utf = lambda x: cp[x][1]
    cls = lambda x: utf(cp[x][1]) if x else None
    _, this_i, super_i = struct.unpack_from(">HHH", data, off); off += 6
    ni = struct.unpack_from(">H", data, off)[0]; off += 2
    ifaces = [cls(struct.unpack_from(">H", data, off + 2 * k)[0]) for k in range(ni)]; off += 2 * ni

    def members():
        nonlocal off
        cnt = struct.unpack_from(">H", data, off)[0]; off += 2; out = []
        for _ in range(cnt):
            _a, nm, de, ac = struct.unpack_from(">HHHH", data, off); off += 8
            for _ in range(ac):
                _x, ln = struct.unpack_from(">HI", data, off); off += 6 + ln
            out.append((utf(nm), utf(de)))
        return out
    fields, methods = members(), members()
    refs = [(e[0], cls(e[1]), utf(cp[e[2]][1]), utf(cp[e[2]][2])) for e in cp if e and e[0] in ("field", "method")]
    classes = [utf(e[1]) for e in cp if e and e[0] == "class"]
    return dict(name=cls(this_i), super=cls(super_i), ifaces=ifaces, fields=set(fields), methods=set(methods),
                refs=refs, classes=classes)


def index_jar(path, idx):
    if not zipfile.is_zipfile(path):
        return idx
    with zipfile.ZipFile(path) as z:
        for n in z.namelist():
            if n.endswith(".class") and not n.startswith("META-INF/"):
                try:
                    c = parse_class(z.read(n)); idx[c["name"]] = c
                except Exception:
                    pass
            elif n.endswith(".jar"):
                # nested jars (Forge/NeoForge universal carry some)
                try:
                    import io
                    with zipfile.ZipFile(io.BytesIO(z.read(n))) as inner:
                        for m in inner.namelist():
                            if m.endswith(".class"):
                                try:
                                    c = parse_class(inner.read(m)); idx[c["name"]] = c
                                except Exception:
                                    pass
                except Exception:
                    pass
    return idx


def get(url, path):
    if not os.path.exists(path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req) as r, open(path + ".part", "wb") as f:
            f.write(r.read())
        os.replace(path + ".part", path)
    return path


def installer_libs(installer, groups):
    """Loader libraries (FML, event bus, SPI…) listed in the installer's version.json."""
    out = []
    with zipfile.ZipFile(installer) as z:
        names = [n for n in ("version.json", "install_profile.json") if n in z.namelist()]
        libs = []
        for n in names:
            libs += json.loads(z.read(n)).get("libraries", [])
    for lib in libs:
        art = lib.get("downloads", {}).get("artifact")
        if not art or not art.get("url"): continue
        if not any(lib["name"].startswith(g) for g in groups): continue
        if "installertools" in lib["name"] or "binarypatcher" in lib["name"] or "jarsplitter" in lib["name"]: continue
        path = os.path.join(CACHE, "libs", art["path"])
        try:
            out.append(get(art["url"], path))
        except Exception as e:
            print("    (could not fetch %s: %s)" % (lib["name"], e))
    return out


def loader_jars(loader, mc):
    if loader == "neoforge":
        v = NEOFORGE.get(mc)
        if not v: return []
        base = "https://maven.neoforged.net/releases/net/neoforged/neoforge/%s/neoforge-%s" % (v, v)
        inst = get(base + "-installer.jar", os.path.join(CACHE, "neoforge-%s-installer.jar" % v))
        return [get(base + "-universal.jar", os.path.join(CACHE, "neoforge-%s-universal.jar" % v))] + \
            installer_libs(inst, ("net.neoforged",))
    if loader == "forge":
        v = FORGE.get(mc)
        if not v: return []
        base = "https://maven.minecraftforge.net/net/minecraftforge/forge/%s-%s/forge-%s-%s" % (mc, v, mc, v)
        inst = get(base + "-installer.jar", os.path.join(CACHE, "forge-%s-%s-installer.jar" % (mc, v)))
        return [get(base + "-universal.jar", os.path.join(CACHE, "forge-%s-%s-universal.jar" % (mc, v)))] + \
            installer_libs(inst, ("net.minecraftforge",))
    return []


def resolve(idx, owner, name, desc, kind):
    if owner.startswith("["): return "array"
    if owner not in idx: return None
    if name == "<init>":
        return owner if (name, desc) in idx[owner]["methods"] else None
    seen, stack = set(), [owner]
    while stack:
        c = stack.pop()
        if c in seen or c not in idx: continue
        seen.add(c)
        members = idx[c]["methods"] if kind == "method" else idx[c]["fields"]
        if (name, desc) in members: return c
        for sup in [idx[c]["super"]] + idx[c]["ifaces"]:
            if not sup: continue
            # inherited from the JDK (Iterable.iterator, Enum.ordinal…): not indexed, trust it
            if sup.startswith("java/") and sup not in idx:
                if name in JDK_MEMBERS.get(sup, ()) or name in JDK_MEMBERS["java/lang/Object"]: return sup
                continue
            stack.append(sup)
    return None


JDK_MEMBERS = {
    "java/lang/Object": {"equals", "hashCode", "toString", "getClass", "notify", "notifyAll", "wait", "clone", "finalize"},
    "java/lang/Enum": {"ordinal", "name", "compareTo", "getDeclaringClass", "describeConstable"},
    "java/lang/Iterable": {"iterator", "forEach", "spliterator"},
    "java/lang/Record": set(),
    "java/lang/Comparable": {"compareTo"},
    "java/util/Collection": {"size", "isEmpty", "contains", "iterator", "stream", "toArray", "add", "remove"},
    "java/util/List": {"get", "size", "isEmpty", "iterator", "add", "stream", "contains"},
    "java/util/AbstractList": {"get", "size", "isEmpty", "iterator", "add", "stream", "contains", "set", "remove", "clear", "indexOf"},
    "java/util/AbstractCollection": {"size", "isEmpty", "contains", "iterator", "stream", "toArray", "add", "remove"},
    "java/util/function/Function": {"apply"},
    "java/util/function/Supplier": {"get"},
}

PREFIXES = ("net/minecraft/", "com/mojang/", "net/neoforged/", "net/minecraftforge/")
# Methods the loaders patch straight into Minecraft classes (not visible in the vanilla jar or the universal jar).
PATCHED = {("net/minecraft/client/gui/screens/inventory/AbstractContainerScreen", "getSlotUnderMouse"),
           ("net/minecraft/client/KeyMapping", "<init>")}
EXT_PREFIX = {"neoforge": ("net/neoforged/neoforge/common/extensions/", "net/neoforged/neoforge/client/extensions/"),
              "forge": ("net/minecraftforge/common/extensions/", "net/minecraftforge/client/extensions/")}


def verify(loader, era_name, jar):
    name, compile_mc, versions = era(era_name)
    mod = {}
    index_jar(jar, mod)
    refs = set()
    classrefs = set()
    for c in mod.values():
        for kind, owner, n, d in c["refs"]:
            if owner and owner.startswith(PREFIXES) and owner not in mod:
                refs.add((kind, owner, n, d))
        for k in c["classes"]:
            if k and k.startswith(PREFIXES) and k not in mod:
                classrefs.add(k)
    ok = True
    for v in versions:
        idx = {}
        mcjar = os.path.join(ROOT, "mcjars", v, "client-named.jar")
        index_jar(mcjar, idx)
        # Mojang's libraries (DataFixerUpper's Pair, Brigadier…) come with the game, not in the client jar
        cpf = os.path.join(ROOT, "mcjars", v, "classpath.txt")
        if os.path.exists(cpf):
            for lib in open(cpf).read().split():
                if "/com/mojang/" in lib and "blaze3d" not in lib: index_jar(lib, idx)
        ljs = loader_jars(loader, v)
        if loader in ("forge", "neoforge") and not ljs:
            print("  %s %s: no %s release for this version (skipped)" % (loader, v, loader))
            continue
        for lj in ljs:
            index_jar(lj, idx)
        ext = [k for k in idx if loader in EXT_PREFIX and k.startswith(EXT_PREFIX[loader])]
        missing = []
        for k in sorted(classrefs):
            if k not in idx: missing.append("class " + k)
        for kind, owner, n, d in sorted(refs):
            if resolve(idx, owner, n, d, kind): continue
            if (owner, n) in PATCHED: continue
            # loader extension interfaces (IItemStackExtension, IForgeItemStack…) add default methods to MC classes
            if owner.startswith("net/minecraft/") and any((n, d) in idx[e]["methods"] for e in ext): continue
            missing.append("%s %s.%s%s" % (kind, owner, n, d))
        if missing:
            ok = False
            print("  %s %s: %d MISSING" % (loader, v, len(missing)))
            for m in missing[:25]: print("     " + m)
        else:
            print("  %s %s: all %d refs resolve" % (loader, v, len(refs)))
    return ok


if __name__ == "__main__":
    sys.exit(0 if verify(sys.argv[1], sys.argv[2], sys.argv[3]) else 1)
