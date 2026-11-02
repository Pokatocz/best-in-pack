#!/bin/bash
# Usage: smokeall.sh <loader> [eras...] – headless in-game test of each era's build (with JEI when it exists).
# Prints one result line per era; full output in builds/<loader>-<era>/smoke.out
L="$1"; shift
cd "$(dirname "$0")/.."
ERAS="$@"
[ -z "$ERAS" ] && ERAS=$(python3 -c "import sys; sys.path.insert(0,'tools'); from targets import ERAS; print(' '.join(e[0] for e in ERAS))")
for E in $ERAS; do
  D=builds/$L-$E
  MC=$(python3 -c "import sys; sys.path.insert(0,'tools'); from targets import era; print(era('$E')[1])")
  python3 tools/gen.py $L $E > /dev/null
  python3 tools/testmods.py $L $MC $D/testmods > $D/testmods.txt 2>&1 || echo "testmods failed" >> $D/testmods.txt
  START=$(date +%s)
  tools/smoke.sh $D $D/testmods > $D/smoke.out 2>&1
  T=$(( $(date +%s) - START ))
  JEI=$(head -1 $D/testmods.txt)
  if grep -q "SMOKE OK" $D/smoke.out; then R="OK"
  elif grep -q "SMOKE FAILED" $D/smoke.out; then R="FAILED $(grep -o 'SMOKE FAILED.*' $D/smoke.out | head -1 | cut -c14-300)"
  else R="NO RESULT $(grep -E 'Exception|failed|FATAL|exit' $D/smoke.out | head -3 | tr '\n' ' ' | cut -c1-300)"; fi
  # rebuild the release JAR from exactly the tested source
  B=$(tools/buildall.sh $L $E | head -1)
  echo "$L $E (mc $MC, ${T}s, $JEI): $R | build: $B"
done
