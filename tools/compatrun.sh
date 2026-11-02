#!/bin/bash
# Usage: compatrun.sh <loader> <era> <mods dir> <label> – in-game test with an extra set of mods; prints one summary line.
L="$1"; E="$2"; M="$3"; N="$4"
cd "$(dirname "$0")/.."
D=builds/$L-$E
python3 tools/gen.py $L $E > /dev/null
START=$(date +%s)
SMOKE_TIMEOUT=1200 tools/smoke.sh $D "$M" > $D/smoke-$N.out 2>&1
cp $D/smoke.log $D/smoke-$N.log 2>/dev/null
T=$(( $(date +%s) - START ))
CR=$(ls -t $D/run/crash-reports/*.txt 2>/dev/null | head -1)
if grep -q "SMOKE OK" $D/smoke-$N.out; then R="OK"
elif grep -q "SMOKE FAILED" $D/smoke-$N.out; then R="FAILED $(grep -o 'SMOKE FAILED.*' $D/smoke-$N.out | head -1 | cut -c14-300)"
else R="NO RESULT $(grep -E 'Incompatible|Exception|crash|exit' $D/smoke-$N.out | head -3 | tr '\n' ' ' | cut -c1-300)"; fi
BIPERR=$(grep -iE "bestinpack" $D/smoke-$N.log | grep -ciE "error|exception")
echo "$L $E [$N] $(ls "$M" | wc -l) mods, ${T}s: $R | bestinpack errors in log: $BIPERR${CR:+ | crash report: $CR}"
