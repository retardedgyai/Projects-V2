"""dev_film_closeup.py <mp4> <start s> <seconds> <out.png> [fps=30] [cols=6]: frames of a filmed swing, larger than
dev_film.sh's sheets, the screen's lower two thirds (where a held weapon moves)."""
import sys, subprocess, tempfile, glob, os
from PIL import Image, ImageDraw
mp4, t0, dur, out = sys.argv[1], float(sys.argv[2]), float(sys.argv[3]), sys.argv[4]
fps = int(sys.argv[5]) if len(sys.argv) > 5 else 30; cols = int(sys.argv[6]) if len(sys.argv) > 6 else 6
d = tempfile.mkdtemp()
subprocess.run(['ffmpeg', '-v', 'error', '-ss', str(t0), '-t', str(dur), '-i', mp4, '-vf',
                f'fps={fps},crop=1920:900:0:180,scale=520:-1', d + '/f%03d.png'], check=True)
fs = sorted(glob.glob(d + '/f*.png')); cells = [Image.open(f) for f in fs]
w, h = cells[0].size
s = Image.new('RGB', (w * cols, h * ((len(cells) + cols - 1) // cols)))
for j, c in enumerate(cells):
    ImageDraw.Draw(c).text((4, 4), f'{t0 + j / fps:.2f}', fill=(255, 255, 0)); s.paste(c, ((j % cols) * w, (j // cols) * h))
s.save(out); print(out, len(cells))
