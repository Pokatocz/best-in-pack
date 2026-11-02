#!/bin/bash
# Usage: buildall.sh <loader> [eras...]  – generate + build each era, log to builds/<loader>-<era>/build.log
L="$1"; shift
cd "$(dirname "$0")/.."
ERAS="$@"
[ -z "$ERAS" ] && ERAS=$(python3 -c "import sys; sys.path.insert(0,'tools'); from targets import ERAS; print(' '.join(e[0] for e in ERAS))")
# Tools (override with environment variables): Gradle 9.5+ for NeoForge/Forge, Gradle 9.8+ on JDK 25 for Fabric (Loom 1.19),
# Gradle 8.x (GRADLE8) for ForgeGradle 6 (Forge 1.20 – 1.20.4)
GRADLE=${GRADLE:-gradle}; GRADLE_FABRIC=${GRADLE_FABRIC:-$GRADLE}
JAVA21_HOME=${JAVA21_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}; JAVA25_HOME=${JAVA25_HOME:-/usr/lib/jvm/java-25-openjdk-amd64}
G=$GRADLE; [ "$L" = "fabric" ] && G=$GRADLE_FABRIC
for E in $ERAS; do
  D=builds/$L-$E
  python3 tools/gen.py $L $E >/dev/null || { echo "$L $E GEN-FAIL"; continue; }
  JAVA=21; grep -q "JavaLanguageVersion.of(25)" $D/build.gradle && JAVA=25
  # ForgeGradle 6 (Forge 1.20 – 1.20.4, SRG names) needs Gradle 8
  GE=$G; grep -qE "net.minecraftforge.gradle' version '\[6|net.neoforged.gradle.userdev" $D/build.gradle && GE=${GRADLE8:-gradle}
  # Gradle itself runs on 21; the toolchain picks 25 for 26.x
  GJ=$JAVA21_HOME; [ "$L" = "fabric" ] && GJ=$JAVA25_HOME
  ( cd $D && rm -rf build/libs && JAVA_HOME=$GJ JAVA_TOOL_OPTIONS= timeout 2400 $GE build --console=plain -x test > build.log 2>&1 )
  if ls $D/build/libs/*.jar >/dev/null 2>&1; then echo "$L $E OK $(ls $D/build/libs/ | grep -v sources | head -1)"; else echo "$L $E FAIL"; grep -E "error:|FAILURE|What went wrong" -A3 $D/build.log | head -20; fi
done
