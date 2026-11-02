"""Version eras: each era ships one JAR per loader. compile = the game version the JAR is built against."""
import json, os
# Modrinth version ids of JEI/REI/Architectury/Mod Menu per game version (compile-only dependencies)
LAND = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")

ERAS = [
    # name,              compile,  covered versions
    ("1.20-1.20.1",      "1.20.1", ["1.20", "1.20.1"]),
    ("1.20.2",           "1.20.2", ["1.20.2"]),
    ("1.20.3-1.20.4",    "1.20.4", ["1.20.3", "1.20.4"]),
    ("1.20.5-1.20.6",    "1.20.6", ["1.20.5", "1.20.6"]),
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

NEOFORGE = {"1.20.2": "20.2.93", "1.20.3": "20.3.8-beta", "1.20.4": "20.4.253", "1.20.5": "20.5.21-beta", "1.20.6": "20.6.144",
            "1.21": "21.0.167", "1.21.1": "21.1.265", "1.21.2": "21.2.1-beta", "1.21.3": "21.3.100", "1.21.4": "21.4.161",
            "1.21.5": "21.5.101", "1.21.6": "21.6.20-beta", "1.21.7": "21.7.25-beta", "1.21.8": "21.8.57",
            "1.21.9": "21.9.16-beta", "1.21.10": "21.10.64", "1.21.11": "21.11.48", "26.1": "26.1.0.19-beta",
            "26.1.1": "26.1.1.15-beta", "26.1.2": "26.1.2.125", "26.2": "26.2.0.93", "26.3": "26.3.0.100"}
FORGE = {"1.20": "46.0.14", "1.20.1": "47.4.28", "1.20.2": "48.1.0", "1.20.3": "49.0.2", "1.20.4": "49.2.13", "1.20.6": "50.2.14",
         "1.21": "51.0.33", "1.21.1": "52.1.20", "1.21.3": "53.1.16", "1.21.4": "54.1.22", "1.21.5": "55.1.18",
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
    """Java the JAR targets (class files and fabric.mod.json): 17 up to 1.20.4, 21 for 1.20.5 – 1.21.11, 25 for 26.x."""
    if mc.startswith("26"): return 25
    p = [int(x) for x in mc.split(".")] + [0]
    return 17 if (p[1], p[2]) < (20, 5) else 21


def toolchain_for(mc):
    """JDK that compiles and runs the dev client – the one players use: 17 up to 1.20.4, 21, 25 for 26.x."""
    return java_for(mc)


def loaders_for(era_name):
    """NeoForge starts at 1.20.2 (NeoForge for 1.20.1 is a Forge fork and loads the Forge JAR)."""
    _, _, versions = era(era_name)
    out = ["fabric"]
    if any(v in FORGE for v in versions): out.append("forge")
    if any(v in NEOFORGE for v in versions): out.append("neoforge")
    return out
