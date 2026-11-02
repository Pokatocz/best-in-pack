"""Download a set of Modrinth mods (newest release for a game version + loader) with their required dependencies.
Usage: compatmods.py <loader> <mc> <out dir> <slug> [slug...]
Prints what was taken and what does not exist for that version."""
import json, os, sys, urllib.parse, urllib.request

loader, mc, out = sys.argv[1], sys.argv[2], sys.argv[3]
slugs = sys.argv[4:]
os.makedirs(out, exist_ok=True)
for f in os.listdir(out):
    if f.endswith(".jar"): os.remove(os.path.join(out, f))
UA = {"User-Agent": "bestinpack-compat-test"}
# Loader ids a mod may be published under for this loader
LOADERS = {"neoforge": ["neoforge"], "fabric": ["fabric"], "forge": ["forge"]}[loader]
# Already provided by the dev environment
SKIP = {"fabric-api", "P7dR8mSH"}


def get(url):
    return json.load(urllib.request.urlopen(urllib.request.Request(url, headers=UA)))


def best_version(project):
    q = "https://api.modrinth.com/v2/project/%s/version?game_versions=%s&loaders=%s" % (
        project, urllib.parse.quote(json.dumps([mc])), urllib.parse.quote(json.dumps(LOADERS)))
    vs = get(q)
    if not vs: return None
    rel = [v for v in vs if v["version_type"] == "release"]
    return (rel or vs)[0]


done, missing = {}, []


def take(project, why):
    if project in SKIP or project in done: return
    try:
        v = best_version(project)
    except Exception as e:
        missing.append("%s (%s)" % (project, e)); return
    if not v:
        missing.append("%s%s" % (project, " (needed by " + why + ")" if why else "")); return
    done[project] = v
    f = [x for x in v["files"] if x.get("primary")] or v["files"]
    path = os.path.join(out, f[0]["filename"])
    with urllib.request.urlopen(urllib.request.Request(f[0]["url"], headers=UA)) as r, open(path, "wb") as o:
        o.write(r.read())
    for d in v.get("dependencies", []):
        if d.get("dependency_type") == "required" and d.get("project_id"):
            take(d["project_id"], project)


for s in slugs:
    take(s, "")
for p, v in done.items():
    print("OK   %-28s %s" % (p, v["version_number"]))
for m in missing:
    print("NONE " + m)
