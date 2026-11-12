#!/usr/bin/env bash
# Side-by-side for logged-in pages: ours en | Ditto | ours zh (zh column only if present).
# Inputs: compare/app/ours/{en,zh}/<name>.png from scripts/app-walk.mjs; ref = docs/research/assets/ditto-app/logged-in/<name>.png
# usage: bash scripts/compare-app.sh [name ...]
set -euo pipefail
cd "$(dirname "$0")/.."
FFMPEG=${FFMPEG:-$(command -v ffmpeg || echo /usr/local/bin/ffmpeg)}
REF=../docs/research/assets/ditto-app/logged-in
OUT=compare/app/side-by-side
mkdir -p "$OUT"

if [ $# -eq 0 ]; then
  set -- $(cd "$REF" && ls *.png | sed 's/\.png$//')
fi

for n in "$@"; do
  en="compare/app/ours/en/$n.png"
  zh="compare/app/ours/zh/$n.png"
  ditto="$REF/$n.png"
  [ -f "$en" ] && [ -f "$ditto" ] || { echo "skip $n"; continue; }
  if [ -f "$zh" ]; then
    "$FFMPEG" -v error -y -i "$en" -i "$ditto" -i "$zh" -filter_complex \
      "[0]scale=390:844,pad=400:844:0:0:color=white[a];[1]scale=390:844:flags=lanczos,pad=400:844:0:0:color=white[b];[2]scale=390:844[c];[a][b][c]hstack=3" \
      "$OUT/$n.png"
  else
    "$FFMPEG" -v error -y -i "$en" -i "$ditto" -filter_complex \
      "[0]scale=390:844,pad=400:844:0:0:color=white[a];[1]scale=390:844:flags=lanczos[b];[a][b]hstack=2" \
      "$OUT/$n.png"
  fi
  echo "$OUT/$n.png"
done
