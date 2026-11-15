#!/bin/bash
# Usage: prodself.sh <loader> <era> <minecraft version> [jei|rei|emi|emi+jei|none]   (default: jei)
# The whole in-game self-test in a real (production) install, with a recipe viewer: builds this era's JAR with the
# self-test inside (gradle build -PwithSelfTest) into SELFDIR (default /tmp/prodself), downloads the viewer for that
# loader and game version (tools/testmods.py) and runs tools/prodcheck.sh with SELFTEST=true.
# This is how viewers are tested where the development run cannot load them (Forge 1.20 - 1.20.4: mods from Modrinth
# are SRG-named there) and how the remapped release JARs are tested as a whole.
# Afterwards build/libs holds the release JAR again (copied back from dist/ when it is there, else rebuilt).
L="$1"; E="$2"; MC="$3"; VIEWER="${4:-jei}"
cd "$(dirname "$0")/.."
VERSION=$(cat VERSION)
D=builds/$L-$E
OUT=${SELFDIR:-/tmp/prodself}; mkdir -p $OUT/mods-$L-$MC-$VIEWER
GRADLE=${GRADLE:-gradle}; GRADLE_FABRIC=${GRADLE_FABRIC:-$GRADLE}
JAVA21_HOME=${JAVA21_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}; JAVA25_HOME=${JAVA25_HOME:-/usr/lib/jvm/java-25-openjdk-amd64}
python3 tools/gen.py $L $E > /dev/null || { echo "prodself $L $MC $VIEWER: GEN-FAIL"; exit 1; }
G=$GRADLE; [ "$L" = fabric ] && G=$GRADLE_FABRIC
grep -qE "net.minecraftforge.gradle' version '\[6|net.neoforged.gradle.userdev" $D/build.gradle && G=${GRADLE8:-gradle}
GJ=$JAVA21_HOME; [ "$L" = fabric ] && GJ=$JAVA25_HOME
build() { ( cd $D && rm -rf build/libs && JAVA_HOME=$GJ JAVA_TOOL_OPTIONS= timeout 2400 $G build --console=plain -x test "$@" > build-self.log 2>&1 ); }
build -PwithSelfTest
JAR=$(ls $D/build/libs/*.jar 2>/dev/null | grep -v sources | head -1)
[ -n "$JAR" ] || { echo "prodself $L $MC $VIEWER: BUILD-FAIL"; grep -E "error:|FAILURE|What went wrong" -A3 $D/build-self.log | head -12; exit 1; }
SELF=$OUT/BestInPack-$L-$E-$VERSION-selftest.jar
cp "$JAR" "$SELF"
unzip -l "$SELF" | grep -q "client/SmokeTest" || { echo "prodself $L $MC $VIEWER: the self-test is not in the JAR"; exit 1; }
# the release JAR back into build/libs, so nothing with test code can be packaged by accident
if [ -f dist/BestInPack-$L-$E-$VERSION.jar ]; then rm -f "$JAR"; cp dist/BestInPack-$L-$E-$VERSION.jar "$JAR"; else build; fi
EXTRA=
if [ "$VIEWER" != none ]; then
  M=$(realpath $OUT/mods-$L-$MC-$VIEWER)
  if [ "$VIEWER" = jei ]; then python3 tools/testmods.py $L $MC $M > $M.txt 2>&1; else python3 tools/testmods.py $L $MC $M $VIEWER > $M.txt 2>&1; fi
  ls $M/*.jar >/dev/null 2>&1 && EXTRA=$M || { echo "prodself $L $MC $VIEWER: no $VIEWER for $L $MC ($(head -1 $M.txt))"; exit 1; }
fi
SELFTEST=true tools/prodcheck.sh $L $MC "$SELF" $EXTRA > $OUT/$L-$MC-$VIEWER.out 2>&1 < /dev/null
PASS=$(grep -c "PASS" $OUT/$L-$MC-$VIEWER.out); FAIL=$(grep -c "FAIL " $OUT/$L-$MC-$VIEWER.out)
echo "prodself $L $MC $VIEWER: $(tail -1 $OUT/$L-$MC-$VIEWER.out | cut -c1-200) | PASS $PASS FAIL $FAIL | $(grep -h 'recipe viewer =' $OUT/$L-$MC-$VIEWER.out | head -1 | cut -c1-60) | mods: $([ -n "$EXTRA" ] && ls $EXTRA | tr '\n' ' ')"
