import json
import difflib
from decimal import Decimal
from pathlib import Path
report = json.loads((Path(__file__).resolve().parent.parent / 'docs/matrix/regional-http-benchmark/ch-weight-reconciliation.json').read_text())
for proof in report['unique_pair_proofs']:
    if proof['same_copy_canonical_expanded_path']:
        continue
    print('PAIR', proof['id'], proof['from'], proof['to'])
    a, b = proof['expanded_ch_edges'], proof['plain_dijkstra_edges']
    def total(path):
        return {k: sum((Decimal.from_float(e[k]) for e in path), Decimal(0)) if k not in ('edge_time_ms', 'turn_time_ms') else sum(e[k] for e in path)
                for k in ('edge_weight', 'turn_weight', 'distance_m', 'edge_time_ms', 'turn_time_ms')}
    print('FULL TOTAL', total(a), total(b))
    keys_a, keys_b = [e['copy_canonical_edge_key'] for e in a], [e['copy_canonical_edge_key'] for e in b]
    for tag, i, j, x, y in difflib.SequenceMatcher(a=keys_a,b=keys_b,autojunk=False).get_opcodes():
        if tag == 'equal': continue
        print('DIFF', tag, (i,j), (x,y), 'A', a[i:j], 'B', b[x:y])
        print('DIFF TOTAL', total(a[i:j]), total(b[x:y]))
