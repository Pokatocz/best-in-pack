#!/bin/bash
# Usage: prodall.sh [loader...]   (default: fabric forge neoforge)
# Starts every release JAR in dist/ in a real client of EVERY game version it covers (tools/prodcheck.sh): Fabric,
# Forge and NeoForge installed the way the launcher does, the mod alone - plus JEI on the newest version of each
# range when the in-game test downloaded one. Also starts the Forge 1.20-1.20.1 JAR on NeoForge 1.20.1.
# This is what catches a JAR whose metadata refuses a game or loader version it claims to support.
# One result line per combination; set PRODDIR for the scratch game folders.
cd "$(dirname "$0")/.."
VERSION=$(cat VERSION)
LOADERS="$@"; [ -z "$LOADERS" ] && LOADERS="fabric forge neoforge"
for L in $LOADERS; do
  python3 -c "
import sys; sys.path.insert(0,'tools')
from targets import ERAS, FORGE, NEOFORGE, loaders_for
for name, compile_mc, versions in ERAS:
    if '$L' not in loaders_for(name): continue
    for v in versions:
        if ('$L' == 'forge' and v not in FORGE) or ('$L' == 'neoforge' and v not in NEOFORGE): continue
        print(name, v, 'jei' if v == compile_mc else '-')
" | while read E MC JEI; do
    EXTRA=; [ "$JEI" = jei ] && ls builds/$L-$E/testmods/*.jar >/dev/null 2>&1 && EXTRA=builds/$L-$E/testmods
    tools/prodcheck.sh $L $MC dist/BestInPack-$L-$E-$VERSION.jar $EXTRA < /dev/null 2>&1 | tail -3 | cut -c1-400
  done
  if [ "$L" = neoforge ]; then   # NeoForge 1.20.1 (the Forge 47 fork) runs the Forge JAR
    EXTRA=; ls builds/forge-1.20-1.20.1/testmods/*.jar >/dev/null 2>&1 && EXTRA=builds/forge-1.20-1.20.1/testmods
    tools/prodcheck.sh neoforge 1.20.1 dist/BestInPack-forge-1.20-1.20.1-$VERSION.jar $EXTRA < /dev/null 2>&1 | tail -3 | cut -c1-400
  fi
done
