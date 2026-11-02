#!/bin/bash
# Usage: smokerei.sh <loader> <era>... – the in-game test with REI (+ Architectury, Cloth Config) instead of JEI.
L="$1"; shift
cd "$(dirname "$0")/.."
for E in "$@"; do
  D=builds/$L-$E
  MC=$(python3 -c "import sys; sys.path.insert(0,'tools'); from targets import era; print(era('$E')[1])")
  python3 tools/testmods.py $L $MC $D/testmods-rei rei > $D/testmods-rei.txt 2>&1
  START=$(date +%s)
  tools/smoke.sh $D $D/testmods-rei > $D/smoke-rei.out 2>&1
  T=$(( $(date +%s) - START ))
  MODS=$(ls $D/testmods-rei | tr '\n' ' ')
  if grep -q "SMOKE OK" $D/smoke-rei.out; then R="OK"
  elif grep -q "SMOKE FAILED" $D/smoke-rei.out; then R="FAILED $(grep -o 'SMOKE FAILED.*' $D/smoke-rei.out | head -1 | cut -c14-300)"
  else R="NO RESULT $(grep -E 'Exception|failed|FATAL|exit' $D/smoke-rei.out | head -3 | tr '\n' ' ' | cut -c1-300)"; fi
  echo "$L $E REI (mc $MC, ${T}s, $MODS): $R; $(grep -h 'recipe viewer =\|row click' $D/smoke-rei.out | tr '\n' ' ')"
done
