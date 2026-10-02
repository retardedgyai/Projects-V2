"""Use the complete-wire checker on the separate V2 goal study."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];path=ROOT/'scripts/audit-skill-tree-clear-wiring-v4.py'
code=path.read_text(encoding='utf-8').replace('workshop-clear-wiring-v4','workshop-goal-study-v2').replace('candidate-graph.json','candidate-region.json').replace('minimumWirePaintGapAt10_5Percent','minimumWirePaintGapAtMinimum19Percent').replace('minimum*.105-1.65','minimum*.19-2.2')
exec(compile(code,str(path),'exec'),{'__file__':str(path)})
