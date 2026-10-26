"""dev_film_strip.py <mp4> <start s> <seconds> <out.png> [fps=20] [cols=8] [width=400]: frames of a filmed run, small."""
import sys, subprocess, tempfile, glob
from PIL import Image, ImageDraw
mp4, t0, dur, out = sys.argv[1], float(sys.argv[2]), float(sys.argv[3]), sys.argv[4]
fps = float(sys.argv[5]) if len(sys.argv) > 5 else 20; cols = int(sys.argv[6]) if len(sys.argv) > 6 else 8
width = int(sys.argv[7]) if len(sys.argv) > 7 else 400
d = tempfile.mkdtemp()
subprocess.run(['ffmpeg', '-v', 'error', '-ss', str(t0), '-t', str(dur), '-i', mp4, '-vf', f'fps={fps},scale={width}:-1', d + '/f%03d.png'], check=True)
cells = [Image.open(f) for f in sorted(glob.glob(d + '/*.png'))]; w, h = cells[0].size
s = Image.new('RGB', (w * cols, h * ((len(cells) + cols - 1) // cols)))
for j, c in enumerate(cells):
    ImageDraw.Draw(c).text((4, 4), f'{t0 + j / fps:.2f}', fill=(255, 255, 0)); s.paste(c, ((j % cols) * w, (j // cols) * h))
s.save(out); print(out, len(cells))
