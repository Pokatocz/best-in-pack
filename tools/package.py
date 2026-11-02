"""Collect the built JARs into dist/ with clear names and zip the sources.
Usage: package.py"""
import os, shutil, sys, zipfile
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from targets import ERAS

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VERSION = open(os.path.join(ROOT, "VERSION")).read().strip()
DIST = os.path.join(ROOT, "dist")


def main():
    shutil.rmtree(DIST, ignore_errors=True)
    os.makedirs(DIST)
    found = []
    for loader in ("fabric", "forge", "neoforge"):
        for name, _compile, _versions in ERAS:
            libs = os.path.join(ROOT, "builds", "%s-%s" % (loader, name), "build", "libs")
            jars = [j for j in (os.listdir(libs) if os.path.isdir(libs) else [])
                    if j.endswith(".jar") and "sources" not in j and "-dev" not in j]
            if not jars:
                print("MISSING %s %s" % (loader, name))
                continue
            out = "BestInPack-%s-%s-%s.jar" % (loader, name, VERSION)
            shutil.copy2(os.path.join(libs, jars[0]), os.path.join(DIST, out))
            found.append(out)
    # all JARs in one zip, plus the sources
    with zipfile.ZipFile(os.path.join(DIST, "BestInPack-%s-all-jars.zip" % VERSION), "w", zipfile.ZIP_DEFLATED) as z:
        for j in found:
            z.write(os.path.join(DIST, j), j)
        z.write(os.path.join(ROOT, "README.md"), "README.md")
    with zipfile.ZipFile(os.path.join(DIST, "BestInPack-%s-sources.zip" % VERSION), "w", zipfile.ZIP_DEFLATED) as z:
        for top in ("src", "loader", "tools", "VERSION", "README.md"):
            p = os.path.join(ROOT, top)
            if os.path.isfile(p):
                z.write(p, top)
                continue
            for d, _dirs, files in os.walk(p):
                if "__pycache__" in d: continue
                for f in files:
                    full = os.path.join(d, f)
                    z.write(full, os.path.relpath(full, ROOT))
    print("%d jars in %s" % (len(found), DIST))


if __name__ == "__main__":
    main()
