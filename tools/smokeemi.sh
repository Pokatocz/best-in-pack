#!/bin/bash
# Usage: smokeemi.sh [--with-jei] <loader> <era>... – the in-game test with EMI instead of JEI (or, with --with-jei,
# EMI next to JEI: EMI then takes over JEI's screens and Best in Pack has to use EMI).
MODE=emi; SUFFIX=emi
[ "$1" = "--with-jei" ] && { MODE=emi+jei; SUFFIX=emijei; shift; }
L="$1"; shift
cd "$(dirname "$0")/.."
for E in "$@"; do
  D=builds/$L-$E
  MC=$(python3 -c "import sys; sys.path.insert(0,'tools'); from targets import era; print(era('$E')[1])")
  python3 tools/gen.py $L $E > /dev/null
  python3 tools/testmods.py $L $MC $D/testmods-$SUFFIX $MODE > $D/testmods-$SUFFIX.txt 2>&1
  START=$(date +%s)
  tools/smoke.sh $D $D/testmods-$SUFFIX > $D/smoke-$SUFFIX.out 2>&1
  rm -rf $D/shots-$SUFFIX; cp -r $D/run/screenshots $D/shots-$SUFFIX 2>/dev/null   # the screenshots of this run, for a look afterwards
  T=$(( $(date +%s) - START ))
  MODS=$(ls $D/testmods-$SUFFIX | tr '\n' ' ')
  if grep -q "SMOKE OK" $D/smoke-$SUFFIX.out; then R="OK"
  elif grep -q "SMOKE FAILED" $D/smoke-$SUFFIX.out; then R="FAILED $(grep -o 'SMOKE FAILED.*' $D/smoke-$SUFFIX.out | head -1 | cut -c14-300)"
  else R="NO RESULT $(grep -E 'Exception|failed|FATAL|exit' $D/smoke-$SUFFIX.out | head -3 | tr '\n' ' ' | cut -c1-300)"; fi
  echo "$L $E EMI (mc $MC, ${T}s, $MODS): $R; $(grep -h 'recipe viewer =\|row click\|EMI' $D/smoke-$SUFFIX.out | grep -c PASS) EMI checks passed"
done
