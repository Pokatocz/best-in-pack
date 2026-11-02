"""Tiny line preprocessor for the multi-version source tree.

    //#if MC >= 1.21.2 && LOADER == neoforge
    ...                      active lines stay; lines written as '//$$ code' are uncommented
    //#elif MC >= 1.21.9
    //#else
    //#endif

Inactive lines become empty lines (line numbers stay the same, so stack traces still point at the source).
"""
import os, re, shutil, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

def vnum(s):
    p = [int(x) for x in s.split(".")]
    while len(p) < 3: p.append(0)
    return p[0] * 10000 + p[1] * 100 + p[2]

TOKEN = re.compile(r"\s*(\d+\.\d+(?:\.\d+)?|MC|LOADER|fabric|forge|neoforge|>=|<=|==|!=|>|<|&&|\|\||!|\(|\))")

def evaluate(expr, mc, loader):
    out, pos = [], 0
    expr = expr.strip()
    while pos < len(expr):
        m = TOKEN.match(expr, pos)
        if not m:
            raise SyntaxError("bad condition: %r at %d" % (expr, pos))
        t = m.group(1); pos = m.end()
        if re.fullmatch(r"\d+\.\d+(?:\.\d+)?", t): out.append(str(vnum(t)))
        elif t == "MC": out.append(str(mc))
        elif t == "LOADER": out.append(repr(loader))
        elif t in ("fabric", "forge", "neoforge"): out.append(repr(t))
        elif t == "&&": out.append(" and ")
        elif t == "||": out.append(" or ")
        elif t == "!": out.append(" not ")
        else: out.append(t)
    return bool(eval("".join(out), {"__builtins__": {}}))

DIRECTIVE = re.compile(r"^\s*//#(if|elif|else|endif)\b(.*)$")
UNCOMMENT = re.compile(r"^(\s*)//\$\$ ?(.*)$")

def process_text(text, mc, loader, name="?"):
    lines = text.split("\n")
    out = []
    stack = []  # each: [parent_active, branch_taken, current_active]
    active = True
    for i, line in enumerate(lines):
        m = DIRECTIVE.match(line)
        if m:
            kind, rest = m.group(1), m.group(2)
            if kind == "if":
                cond = active and evaluate(rest, mc, loader)
                stack.append([active, cond])
                active = cond
            elif kind == "elif":
                if not stack: raise SyntaxError("%s:%d elif without if" % (name, i + 1))
                parent, taken = stack[-1]
                cond = parent and not taken and evaluate(rest, mc, loader)
                stack[-1][1] = taken or cond
                active = cond
            elif kind == "else":
                if not stack: raise SyntaxError("%s:%d else without if" % (name, i + 1))
                parent, taken = stack[-1]
                active = parent and not taken
                stack[-1][1] = True
            else:
                if not stack: raise SyntaxError("%s:%d endif without if" % (name, i + 1))
                parent, _ = stack.pop()
                active = parent
            out.append("")
            continue
        if not active:
            out.append("")
            continue
        u = UNCOMMENT.match(line)
        out.append(u.group(1) + u.group(2) if u else line)
    if stack: raise SyntaxError("%s: unclosed #if" % name)
    return "\n".join(out)

def apply_swaps(text, mc, loader):
    try:
        from swaps import SWAPS
    except ImportError:
        return text
    for cond, rx, repl in SWAPS:
        if evaluate(cond, mc, loader):
            text = re.sub(rx, repl, text)
    return text

TEXT_EXT = (".java", ".json", ".toml", ".mcmeta", ".txt", ".cfg", ".properties")

def process_tree(src, dst, mc, loader):
    for root, dirs, files in os.walk(src):
        rel = os.path.relpath(root, src)
        for f in files:
            sp = os.path.join(root, f)
            dp = os.path.join(dst, rel, f)
            os.makedirs(os.path.dirname(dp), exist_ok=True)
            if f.endswith(TEXT_EXT):
                text = open(sp, encoding="utf-8").read()
                res = process_text(text, mc, loader, sp)
                if f.endswith(".java"):
                    res = apply_swaps(res, mc, loader)
                # a Java file whose whole body is switched off is left out (loader- or version-only classes)
                if f.endswith(".java") and not res.strip():
                    continue
                open(dp, "w", encoding="utf-8").write(res)
            else:
                shutil.copy2(sp, dp)

if __name__ == "__main__":
    src, dst, mc, loader = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
    process_tree(src, dst, vnum(mc), loader)
