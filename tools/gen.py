"""Generate a Gradle project for one loader + era:  gen.py <fabric|neoforge|forge> <era>  -> builds/<loader>-<era>/"""
import os, shutil, sys, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pp
from targets import ERAS, NEOFORGE, FORGE, era, modrinth_id, java_for, jei_fabric_maven, BLAMEJARED_REPO

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MOD_VERSION = open(os.path.join(ROOT, "VERSION")).read().strip()
DESCRIPTION = ("Which food, armor, weapon, tool and fuel is the best in your modpack? Press B to see every item ranked. "
               "Hold Shift over an item to see its rank.")

FABRIC_API = {}  # filled from maven metadata on demand


def fabric_api_for(mc):
    if not FABRIC_API:
        import re, urllib.request
        xml = urllib.request.urlopen("https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml").read().decode()
        for v in re.findall(r"<version>([^<]+)</version>", xml):
            if "+" not in v: continue
            num, game = v.split("+", 1)
            key = tuple(int(x) for x in num.split(".") if x.isdigit())
            if game not in FABRIC_API or key > FABRIC_API[game][0]:
                FABRIC_API[game] = (key, v)
    return FABRIC_API[mc][1]


def mc_range_fabric(versions):
    lo, hi = versions[0], versions[-1]
    return ">=%s <=%s" % (lo, hi) if lo != hi else lo


def mc_range_forge(versions):
    lo, hi = versions[0], versions[-1]
    return "[%s]" % lo if lo == hi else "[%s,%s]" % (lo, hi)


def pick(project, mc, loader, versions):
    vid = modrinth_id(project, mc, loader)
    if vid: return vid
    for v in reversed(versions):
        vid = modrinth_id(project, v, loader)
        if vid: return vid
    return None


def generate(loader, era_name):
    name, mc, versions = era(era_name)
    out = os.path.join(ROOT, "builds", "%s-%s" % (loader, name))
    if os.path.isdir(os.path.join(out, "src")): shutil.rmtree(os.path.join(out, "src"))
    os.makedirs(out, exist_ok=True)
    v = pp.vnum(mc)
    java = java_for(mc)
    src_java = os.path.join(out, "src/main/java")
    src_res = os.path.join(out, "src/main/resources")
    pp.process_tree(os.path.join(ROOT, "src/main/java"), src_java, v, loader)
    pp.process_tree(os.path.join(ROOT, "src/main/resources"), src_res, v, loader)
    pp.process_tree(os.path.join(ROOT, "loader", loader, "java"), src_java, v, loader)
    if os.path.isdir(os.path.join(ROOT, "loader", loader, "resources")):
        pp.process_tree(os.path.join(ROOT, "loader", loader, "resources"), src_res, v, loader)

    jei = pick("jei", mc, loader, versions)
    jei_dep = 'compileOnly "maven.modrinth:jei:%s"' % jei if jei else None
    if not jei and loader == "fabric" and jei_fabric_maven(versions):
        jv, jver = jei_fabric_maven(versions)
        jei = "mezz.jei:jei-%s-fabric:%s" % (jv, jver)
        jei_dep = 'compileOnly "%s"' % jei
    rei = pick("rei", mc, loader, versions)
    arch = pick("architectury-api", mc, loader, versions)
    if not (rei and arch): rei = arch = None
    if not jei: os.remove(os.path.join(src_java, "com/bestinpack/compat/JeiBridge.java"))
    if not rei: os.remove(os.path.join(src_java, "com/bestinpack/compat/ReiBridge.java"))
    deps = []
    if jei_dep: deps.append(jei_dep)
    if rei: deps += ['compileOnly "maven.modrinth:rei:%s"' % rei, 'compileOnly "maven.modrinth:architectury-api:%s"' % arch]

    subst = {"${version}": MOD_VERSION, "${mc_range}": mc_range_forge(versions)}
    if loader == "neoforge":
        parts = NEOFORGE[versions[0]].replace("-beta", "").split(".")
        subst["${neo_min}"] = ".".join(parts[:3] + ["0"]) if versions[0].startswith("26") else ".".join(parts[:2] + ["0"])
    if loader == "forge":
        majors = [int(FORGE[x].split(".")[0]) for x in versions if x in FORGE]
        subst["${forge_min}"] = str(min(majors))
    for root, dirs, files in os.walk(src_res):
        for f in files:
            if f.endswith((".toml", ".json")):
                p = os.path.join(root, f)
                s = open(p, encoding="utf-8").read()
                for k, val in subst.items(): s = s.replace(k, val)
                open(p, "w", encoding="utf-8").write(s)

    if loader == "fabric":
        api = fabric_api_for(mc)
        fmj = {
            "schemaVersion": 1, "id": "bestinpack", "version": MOD_VERSION, "name": "Best in Pack",
            "description": DESCRIPTION, "authors": ["Pokato"], "license": "MIT", "environment": "client",
            "icon": "assets/bestinpack/icon.png",
            "entrypoints": {"client": ["com.bestinpack.fabric.BestInPackFabric"]},
            "mixins": ["bestinpack.mixins.json"],
            "depends": {"fabricloader": ">=0.16.0", "minecraft": mc_range_fabric(versions), "java": ">=%d" % java,
                        "fabric-api": "*"},
            "suggests": {"jei": "*", "roughlyenoughitems": "*"},
        }
        if jei: fmj["entrypoints"]["jei_mod_plugin"] = ["com.bestinpack.compat.JeiBridge"]
        json.dump(fmj, open(os.path.join(src_res, "fabric.mod.json"), "w"), indent=2)
        write_fabric(out, mc, java, api, deps, versions)
    elif loader == "neoforge":
        write_neoforge(out, mc, java, deps)
    elif loader == "forge":
        write_forge(out, mc, java, deps)
    open(os.path.join(out, "gradle.properties"), "w").write(
        "org.gradle.jvmargs=-Xmx2G\norg.gradle.parallel=false\norg.gradle.daemon=false\n")
    print(out)
    return out


RUN_ENV = """
            systemProperty 'bestinpack.selftest', System.getProperty('bestinpack.selftest', 'false')
            environment 'DISPLAY', System.getenv('DISPLAY') ?: ':99'
            environment 'XDG_RUNTIME_DIR', '/tmp/xdg'
            environment 'LIBGL_ALWAYS_SOFTWARE', '1'
            environment 'SDL_VIDEO_FORCE_EGL', '1'
            environment 'SDL_VIDEO_X11_FORCE_EGL', '1'"""

NEO_ENV = """
            systemProperty 'bestinpack.selftest', System.getProperty('bestinpack.selftest', 'false')
            environment.put('DISPLAY', System.getenv('DISPLAY') ?: ':99')
            environment.put('XDG_RUNTIME_DIR', '/tmp/xdg')
            environment.put('LIBGL_ALWAYS_SOFTWARE', '1')
            environment.put('SDL_VIDEO_FORCE_EGL', '1')
            environment.put('SDL_VIDEO_X11_FORCE_EGL', '1')"""

MODRINTH_REPO = """    exclusiveContent {
        forRepository { maven { name = 'Modrinth'; url = 'https://api.modrinth.com/maven' } }
        filter { includeGroup 'maven.modrinth' }
    }"""


def write_neoforge(out, mc, java, deps):
    open(os.path.join(out, "settings.gradle"), "w").write("""pluginManagement {
    repositories {
        maven { url = 'https://maven.neoforged.net/releases' }
        maven { url = 'https://repo1.maven.org/maven2' }
        gradlePluginPortal()
    }
}
rootProject.name = 'bestinpack-neoforge'
""")
    open(os.path.join(out, "build.gradle"), "w").write("""plugins {
    id 'java-library'
    id 'net.neoforged.moddev' version '2.0.148'
}
version = '%(ver)s'
base { archivesName = 'BestInPack-neoforge' }
java.toolchain.languageVersion = JavaLanguageVersion.of(%(java)d)
repositories {
    maven { url = 'https://repo1.maven.org/maven2' }
%(modrinth)s
}
neoForge {
    version = '%(neo)s'
    runs {
        client {
            client()
            gameDirectory = project.file('run')
            systemProperty 'fml.earlyWindowControl', 'false'
            jvmArgument '-Xmx3G'%(env)s
        }
    }
    mods { bestinpack { sourceSet(sourceSets.main) } }
}
dependencies {
    %(deps)s
}
tasks.withType(JavaCompile).configureEach { options.release = %(java)d; options.encoding = 'UTF-8' }
""" % {"ver": MOD_VERSION, "java": java, "neo": NEOFORGE[mc], "modrinth": MODRINTH_REPO, "env": NEO_ENV,
       "deps": "\n    ".join(deps)})


def write_fabric(out, mc, java, api, deps, versions):
    obf = not mc.startswith("26")
    plugin = "id 'fabric-loom' version '1.19.3'" if obf else "id 'net.fabricmc.fabric-loom' version '1.19.3'"
    mappings = "    mappings loom.officialMojangMappings()\n" if obf else ""
    impl = "modImplementation" if obf else "implementation"
    compile_only = "modCompileOnly" if obf else "compileOnly"
    deps = [d.replace("compileOnly", compile_only, 1) for d in deps]
    open(os.path.join(out, "settings.gradle"), "w").write("""pluginManagement {
    repositories {
        maven { name = 'Fabric'; url = 'https://maven.fabricmc.net/' }
        maven { url = 'https://repo1.maven.org/maven2' }
        gradlePluginPortal()
    }
}
rootProject.name = 'bestinpack-fabric'
""")
    open(os.path.join(out, "build.gradle"), "w").write("""plugins {
    %(plugin)s
}
version = '%(ver)s'
base { archivesName = 'BestInPack-fabric' }
repositories {
    maven { url = 'https://repo1.maven.org/maven2' }
%(modrinth)s
}
loom {
    mods { bestinpack { sourceSet sourceSets.main } }
    runs {
        client {
            client()
            runDir 'run'
            vmArg '-Xmx3G'
            property 'bestinpack.selftest', System.getProperty('bestinpack.selftest', 'false')
        }
    }
}
dependencies {
    minecraft 'com.mojang:minecraft:%(mc)s'
%(mappings)s    %(impl)s 'net.fabricmc:fabric-loader:%(loader)s'
    %(impl)s 'net.fabricmc.fabric-api:fabric-api:%(api)s'
    %(deps)s
}
tasks.withType(JavaCompile).configureEach { it.options.release = %(java)d; it.options.encoding = 'UTF-8' }
java { toolchain.languageVersion = JavaLanguageVersion.of(%(java)d) }
""" % {"plugin": plugin, "ver": MOD_VERSION, "mc": mc, "mappings": mappings, "impl": impl, "api": api,
       # 26.x Fabric API needs loader 0.18.4+; 1.21.x keeps the loader it was tested with
       "loader": "0.19.5",
       "deps": "\n    ".join(deps), "java": java, "modrinth": MODRINTH_REPO + "\n" + BLAMEJARED_REPO})


def write_forge(out, mc, java, deps):
    open(os.path.join(out, "settings.gradle"), "w").write("""pluginManagement {
    repositories {
        maven { url = 'https://maven.minecraftforge.net/' }
        maven { url = 'https://repo1.maven.org/maven2' }
        gradlePluginPortal()
    }
}
rootProject.name = 'bestinpack-forge'
""")
    mappings = "    mappings channel: 'official', version: '%s'\n" % mc if not mc.startswith("26") else ""
    open(os.path.join(out, "build.gradle"), "w").write("""plugins {
    id 'java'
    id 'net.minecraftforge.gradle' version '[7.0.3,8)'
}
version = '%(ver)s'
base { archivesName = 'BestInPack-forge' }
java.toolchain.languageVersion = JavaLanguageVersion.of(%(java)d)
minecraft {
%(mappings)s    runs {
        configureEach { workingDir = layout.projectDirectory.dir('run') }
        register('client') {
            systemProperty 'bestinpack.selftest', System.getProperty('bestinpack.selftest', 'false')
        }
    }
}
repositories {
    minecraft.mavenizer(it)
    maven fg.forgeMaven
    maven fg.minecraftLibsMaven
    maven { url = 'https://repo1.maven.org/maven2' }
%(modrinth)s
}
dependencies {
    implementation minecraft.dependency('net.minecraftforge:forge:%(mc)s-%(forge)s')
    %(deps)s
}
tasks.withType(JavaCompile).configureEach { options.release = %(java)d; options.encoding = 'UTF-8' }
// Dev runs: Forge wants classes and resources in one folder to see them as one mod file.
sourceSets.each {
    def dir = layout.buildDirectory.dir("sourcesSets/$it.name")
    it.output.resourcesDir = dir
    it.java.destinationDirectory = dir
}
""" % {"ver": MOD_VERSION, "java": java, "mappings": mappings, "mc": mc, "forge": FORGE[mc], "deps": "\n    ".join(deps),
       "modrinth": MODRINTH_REPO})


if __name__ == "__main__":
    loader = sys.argv[1]
    eras = sys.argv[2:] or [e[0] for e in ERAS]
    for e in eras:
        generate(loader, e)
