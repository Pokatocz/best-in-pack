#!/bin/bash
# Usage: verifyall.sh [loader...]   (default: fabric forge neoforge)
# tools/verify.py for every build: resolves every reference the built classes make to Minecraft and the loader
# against each game version of the range. verify.py needs Mojang names, so it gets the JAR before remapping where
# the release JAR is remapped: Fabric up to 1.21.11 (build/devlibs) and Forge 1.20 - 1.20.4 (build/classes).
# One line per build: "<loader> <era>: OK (n versions)" or the versions with unresolved references.
cd "$(dirname "$0")/.."
VERSION=$(cat VERSION)
LOADERS="$@"; [ -z "$LOADERS" ] && LOADERS="fabric forge neoforge"
TMP=$(mktemp -d)
for L in $LOADERS; do
  for E in $(python3 -c "import sys; sys.path.insert(0,'tools'); from targets import ERAS, loaders_for; print(' '.join(e[0] for e in ERAS if '$L' in loaders_for(e[0])))"); do
    D=builds/$L-$E
    JAR=$D/build/libs/BestInPack-$L-$VERSION.jar
    [ -f $D/build/devlibs/BestInPack-$L-$VERSION-dev.jar ] && JAR=$D/build/devlibs/BestInPack-$L-$VERSION-dev.jar
    if grep -q "net.minecraftforge.gradle' version '\[6" $D/build.gradle; then
      JAR=$TMP/$L-$E.jar; ( cd $D/build/classes/java/main && zip -qr $JAR . )
    fi
    [ -f "$JAR" ] || { echo "$L $E: no JAR ($JAR)"; continue; }
    python3 tools/verify.py $L $E $JAR > $D/verify.out 2>&1; RC=$?
    if [ $RC = 0 ]; then echo "$L $E: OK ($(grep -c "refs resolve" $D/verify.out) versions) $(basename $JAR)"
    else echo "$L $E: FAILED"; grep -vE "refs resolve" $D/verify.out | head -12 | cut -c1-240; fi
  done
done
rm -rf $TMP
