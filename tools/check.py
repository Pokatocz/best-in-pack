"""Compile the shared source against vanilla Minecraft (Mojang names) for each version – fast API check.
Usage: check.py [versions...]   (prints the first errors per version)"""
import os, sys, subprocess, shutil
sys.path.insert(0, os.path.dirname(__file__))
import pp
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ALL = ["1.20","1.20.1","1.20.2","1.20.3","1.20.4","1.20.5","1.20.6","1.21","1.21.1","1.21.2","1.21.3","1.21.4","1.21.5","1.21.6","1.21.7","1.21.8","1.21.9","1.21.10","1.21.11","26.1","26.1.1","26.1.2","26.2","26.3"]
EXCLUDE = ("JeiBridge.java", "ReiBridge.java")
versions = sys.argv[1:] or ALL
maxerr = int(os.environ.get("MAXERR", "30"))
for v in versions:
    out = os.path.join(ROOT, "build-check", v)
    shutil.rmtree(out, ignore_errors=True)
    src = os.path.join(out, "src")
    pp.process_tree(os.path.join(ROOT, "src/main/java"), src, pp.vnum(v), "neoforge")
    files = []
    for r, d, fs in os.walk(src):
        for f in fs:
            if f.endswith(".java") and f not in EXCLUDE: files.append(os.path.join(r, f))
    mc = os.path.join(ROOT, "mcjars", v)
    cp = [os.path.join(mc, "client-named.jar")] + open(os.path.join(mc, "classpath.txt")).read().split()
    java = os.path.join(os.environ.get("JAVA25_HOME" if v.startswith("26") else "JAVA21_HOME",
        "/usr/lib/jvm/java-%s-openjdk-amd64" % (25 if v.startswith("26") else 21)), "bin", "javac")
    rel = "25" if v.startswith("26") else ("17" if pp.vnum(v) < pp.vnum("1.20.5") else "21")
    cmd = [java, "-nowarn", "-proc:none", "--release", rel, "-d", os.path.join(out, "classes"), "-cp", ":".join(cp), "-Xmaxerrs", str(maxerr)] + files
    r = subprocess.run(cmd, capture_output=True, text=True, env={**os.environ, "JAVA_TOOL_OPTIONS": ""})
    lines = [l for l in (r.stdout + r.stderr).splitlines() if "Picked up" not in l and "Note:" not in l]
    status = "OK" if r.returncode == 0 else "FAIL"
    print("==== %s %s" % (v, status), flush=True)
    if r.returncode != 0:
        print("\n".join(l.replace(src + "/", "") for l in lines[:maxerr * 4]))
