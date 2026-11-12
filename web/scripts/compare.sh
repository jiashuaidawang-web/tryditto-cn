#!/usr/bin/env bash
# Side-by-side: left = ours (compare/ours/<run>/NN.png), right = Ditto pass3 <run>/NN-a.png scaled to 390x844.
# usage: bash scripts/compare.sh [S1|N1] [NN ...]   (defaults to key pages)
set -euo pipefail
cd "$(dirname "$0")/.."
FFMPEG=${FFMPEG:-$(command -v ffmpeg || echo /usr/local/bin/ffmpeg)}
REF=../docs/research/assets/ditto-onboarding/shots/pass3
OUT=compare/side-by-side
mkdir -p "$OUT"

run=${1:-S1}
shift || true
if [ $# -eq 0 ]; then
  if [ "$run" = S1 ]; then set -- 01 03 04 05 06 07 08 15 16 17 18; else set -- 01 06 10 18 19 20 21 23; fi
fi

for n in "$@"; do
  ours="compare/ours/$run/$n.png"
  ditto="$REF/$run/$n-a.png"
  [ -f "$ours" ] && [ -f "$ditto" ] || { echo "skip $run/$n"; continue; }
  "$FFMPEG" -v error -y -i "$ours" -i "$ditto" -filter_complex \
    "[0]scale=390:844,pad=400:844:0:0:color=white[a];[1]scale=390:844:flags=lanczos[b];[a][b]hstack=2" \
    "$OUT/$run-$n.png"
  echo "$OUT/$run-$n.png"
done
