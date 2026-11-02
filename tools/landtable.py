"""Add Modrinth version ids (newest per game version + loader) to tools/data/<project>-table.json.
Usage: landtable.py <game versions...>   – existing entries are kept."""
import json, os, sys, urllib.parse, urllib.request
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from targets import LAND
PROJECTS = ["jei", "rei", "architectury-api", "modmenu"]
LOADERS = ["fabric", "forge", "neoforge"]

def versions(project, mc, loader):
    q = "https://api.modrinth.com/v2/project/%s/version?game_versions=%s&loaders=%s" % (
        project, urllib.parse.quote('["%s"]' % mc), urllib.parse.quote('["%s"]' % loader))
    return json.load(urllib.request.urlopen(urllib.request.Request(q, headers={"User-Agent": "bestinpack-build"})))

for project in PROJECTS:
    path = os.path.join(LAND, project + "-table.json")
    table = json.load(open(path)) if os.path.exists(path) else {}
    for mc in sys.argv[1:]:
        for loader in LOADERS:
            if table.get(mc, {}).get(loader): continue
            vs = versions(project, mc, loader)
            rel = [v for v in vs if v["version_type"] == "release"] or vs
            if not rel: continue
            table.setdefault(mc, {})[loader] = "%s|%s" % (rel[0]["id"], rel[0]["version_number"])
            print(project, mc, loader, table[mc][loader], flush=True)
    json.dump(table, open(path, "w"), indent=1, sort_keys=True)
