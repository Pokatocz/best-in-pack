"""Version eras: each era ships one JAR per loader. compile = the game version the JAR is built against."""
import json, os
# Modrinth version ids of JEI/REI/Architectury/Mod Menu per game version (compile-only dependencies)
LAND = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")

ERAS = [
    # name,              compile,  covered versions
    ("1.21-1.21.1",      "1.21.1", ["1.21", "1.21.1"]),
    ("1.21.2-1.21.4",    "1.21.4", ["1.21.2", "1.21.3", "1.21.4"]),
    ("1.21.5",           "1.21.5", ["1.21.5"]),
    ("1.21.6-1.21.8",    "1.21.8", ["1.21.6", "1.21.7", "1.21.8"]),
    ("1.21.9-1.21.10",   "1.21.10", ["1.21.9", "1.21.10"]),
    ("1.21.11",          "1.21.11", ["1.21.11"]),
    ("26.1-26.1.2",      "26.1.2", ["26.1", "26.1.1", "26.1.2"]),
    ("26.2",             "26.2",   ["26.2"]),
    ("26.3",             "26.3",   ["26.3"]),
]

NEOFORGE = {"1.21": "21.0.167", "1.21.1": "21.1.265", "1.21.2": "21.2.1-beta", "1.21.3": "21.3.100", "1.21.4": "21.4.161",
            "1.21.5": "21.5.101", "1.21.6": "21.6.20-beta", "1.21.7": "21.7.25-beta", "1.21.8": "21.8.57",
            "1.21.9": "21.9.16-beta", "1.21.10": "21.10.64", "1.21.11": "21.11.48", "26.1": "26.1.0.19-beta",
            "26.1.1": "26.1.1.15-beta", "26.1.2": "26.1.2.125", "26.2": "26.2.0.93", "26.3": "26.3.0.100"}
FORGE = {"1.21": "51.0.33", "1.21.1": "52.1.20", "1.21.3": "53.1.16", "1.21.4": "54.1.22", "1.21.5": "55.1.18",
         "1.21.6": "56.0.9", "1.21.7": "57.0.3", "1.21.8": "58.1.26", "1.21.9": "59.0.5", "1.21.10": "60.1.18",
         "1.21.11": "61.2.4", "26.1": "62.0.9", "26.1.1": "63.0.2", "26.1.2": "64.1.5", "26.2": "65.1.5", "26.3": "66.1.2"}

# JEI for Fabric 1.21.5 – 1.21.8 is only on JEI's own maven (not on Modrinth).
JEI_FABRIC_MAVEN = {"1.21.5": "21.4.0.27", "1.21.7": "23.1.0.8", "1.21.8": "24.2.0.6"}
BLAMEJARED_REPO = """    exclusiveContent {
        forRepository { maven { name = 'BlameJared'; url = 'https://maven.blamejared.com' } }
        filter { includeGroup 'mezz.jei' }
    }"""


def jei_fabric_maven(versions):
    for v in reversed(versions):
        if v in JEI_FABRIC_MAVEN: return v, JEI_FABRIC_MAVEN[v]
    return None


def table(name):
    p = os.path.join(LAND, name + "-table.json")
    return json.load(open(p)) if os.path.exists(p) else {}

def modrinth_id(project, mc, loader):
    e = table(project).get(mc, {}).get(loader)
    return e.split("|")[0] if e else None

def era(name):
    for e in ERAS:
        if e[0] == name: return e
    raise KeyError(name)

def java_for(mc):
    return 25 if mc.startswith("26") else 21
