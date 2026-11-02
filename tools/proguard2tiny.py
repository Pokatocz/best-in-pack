"""ProGuard client mappings (named -> obf) to tiny v2 (official -> named)."""
import sys, re
src, dst = sys.argv[1], sys.argv[2]
PRIM = {"byte":"B","char":"C","double":"D","float":"F","int":"I","long":"J","short":"S","boolean":"Z","void":"V"}
classes = []  # (named, obf, fields[(type,named,obf)], methods[(ret,named,args,obf)])
cur = None
for line in open(src, encoding="utf-8"):
    if line.startswith("#") or not line.strip(): continue
    if not line.startswith(" "):
        m = re.match(r"(\S+) -> (\S+):", line)
        cur = (m.group(1), m.group(2), [], [])
        classes.append(cur)
        continue
    line = line.strip()
    m = re.match(r"(?:\d+:\d+:)?(\S+) (\S+)\((.*)\)(?::\d+:\d+)? -> (\S+)", line)
    if m:
        cur[3].append((m.group(1), m.group(2), m.group(3), m.group(4)))
        continue
    m = re.match(r"(\S+) (\S+) -> (\S+)", line)
    if m:
        cur[2].append((m.group(1), m.group(2), m.group(3)))
named2obf = {c[0]: c[1] for c in classes}
def desc(t):
    dims = 0
    while t.endswith("[]"):
        dims += 1; t = t[:-2]
    if t in PRIM: d = PRIM[t]
    else: d = "L" + named2obf.get(t, t).replace(".", "/") + ";"
    return "[" * dims + d
with open(dst, "w", encoding="utf-8") as o:
    o.write("tiny\t2\t0\tofficial\tnamed\n")
    for named, obf, fields, methods in classes:
        o.write("c\t%s\t%s\n" % (obf.replace(".", "/"), named.replace(".", "/")))
        for t, n, ob in fields:
            o.write("\tf\t%s\t%s\t%s\n" % (desc(t), ob, n))
        for ret, n, args, ob in methods:
            if n in ("<init>", "<clinit>"): continue
            a = "".join(desc(x) for x in args.split(",")) if args else ""
            o.write("\tm\t(%s)%s\t%s\t%s\n" % (a, desc(ret), ob, n))
