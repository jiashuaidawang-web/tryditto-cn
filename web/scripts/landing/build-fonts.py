"""Build the landing-page webfonts into public/landing/fonts/.

Latin faces are copied from @fontsource woff2 files. The CJK display/script faces are
merged from the @fontsource unicode-range slices and subset to exactly the CJK
characters used in src/landing/**, so the page ships ~100 KB instead of ~10 MB.

usage:
  python3 -m venv /tmp/ld/venv && /tmp/ld/venv/bin/pip install fonttools brotli
  (cd /tmp/ld/fonts && npm pack @fontsource/smythe @fontsource/caveat \
      @fontsource/zcool-xiaowei @fontsource/ma-shan-zheng && for t in *.tgz; do mkdir -p ${t%.tgz}; tar xzf $t -C ${t%.tgz}; done)
  /tmp/ld/venv/bin/python scripts/landing/build-fonts.py /tmp/ld/fonts
"""
import glob
import os
import re
import shutil
import sys

from fontTools import subset
from fontTools.merge import Merger
from fontTools.ttLib import TTFont

SRC = sys.argv[1] if len(sys.argv) > 1 else '/tmp/ld/fonts'
WEB = os.path.join(os.path.dirname(__file__), '..', '..')
OUT = os.path.join(WEB, 'public', 'landing', 'fonts')
os.makedirs(OUT, exist_ok=True)

CJK = re.compile(r'[\u3000-\u303f\u3400-\u9fff\uff00-\uffef]')
text = ''
for path in glob.glob(os.path.join(WEB, 'src', 'landing', '**', '*.ts*'), recursive=True):
    text += open(path, encoding='utf-8').read()
chars = sorted(set(CJK.findall(text)))
print(f'{len(chars)} CJK chars')


def pkg(name):
    hits = glob.glob(os.path.join(SRC, f'fontsource-{name}-*', 'package', 'files'))
    if not hits:
        raise SystemExit(f'missing @fontsource/{name} in {SRC}')
    return hits[0]


def build_cjk(name, family_file, wanted):
    files = sorted(glob.glob(os.path.join(pkg(name), f'{name}-[0-9]*-400-normal.woff2')))
    parts = []
    for i, f in enumerate(files):
        font = TTFont(f)
        cmap = font.getBestCmap()
        need = [c for c in wanted if ord(c) in cmap]
        if not need:
            continue
        opts = subset.Options()
        opts.flavor = None
        opts.layout_features = ['*']
        opts.name_IDs = ['*']
        sub = subset.Subsetter(opts)
        sub.populate(text=''.join(need))
        sub.subset(font)
        tmp = f'/tmp/ld/{name}-part-{i}.ttf'
        font.flavor = None
        font.save(tmp)
        parts.append(tmp)
    merged = Merger().merge(parts) if len(parts) > 1 else TTFont(parts[0])
    merged.flavor = 'woff2'
    dest = os.path.join(OUT, family_file)
    merged.save(dest)
    print(dest, os.path.getsize(dest), 'bytes from', len(parts), 'slices')


shutil.copy(os.path.join(pkg('smythe'), 'smythe-latin-400-normal.woff2'), os.path.join(OUT, 'smythe-latin.woff2'))
shutil.copy(os.path.join(pkg('caveat'), 'caveat-latin-500-normal.woff2'), os.path.join(OUT, 'caveat-latin.woff2'))
build_cjk('zcool-xiaowei', 'zcool-xiaowei-subset.woff2', chars)
script_chars = sorted(set('约会，不必滑动'))
build_cjk('ma-shan-zheng', 'ma-shan-zheng-subset.woff2', script_chars)
