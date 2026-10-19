import json
import difflib
from pathlib import Path

root = Path(__file__).resolve().parent.parent
base = root / 'docs/matrix'
failures = [json.loads(line) for line in (base / 'regional-failure-replays.jsonl').read_text().splitlines()]
for case in failures:
    if case.get('accepted_with_independent_path_evidence', False):
        continue
    print('CASE', {key: value for key, value in case.items() if key != 'path_evidence'})
    evidence = case['path_evidence']
    independent = evidence['independent_route']
    shared = evidence['shared_selected']
    a = independent['original_directed_edge_keys']
    b = shared['original_directed_edge_keys']
    print('FLAGS', {key: value for key, value in evidence.items() if not isinstance(value, (dict,list))})
    for tag, i, j, x, y in difflib.SequenceMatcher(a=a, b=b, autojunk=False).get_opcodes():
        if tag == 'equal':
            continue
        print('DIFF', tag, i, j, x, y, 'INDEPENDENT', a[max(0, i-2):j+2], 'SHARED', b[max(0,x-2):y+2])
        keys = set(a[max(0,i-1):j+1] + b[max(0,x-1):y+1])
        for name, description in [('INDEPENDENT', independent), ('SHARED', shared)]:
            print(name, json.dumps([edge for edge in description['edge_contributions'] if edge['original_edge_key'] in keys]))
    print('METRICS', independent['metrics'], shared['metrics'])

reconciliation_path = base / 'regional-reconciliation.json'
if reconciliation_path.exists():
    reconciliation = json.loads(reconciliation_path.read_text())
    print('RECONCILIATION', {key: reconciliation.get(key) for key in
          ('status', 'replayed_cells', 'newly_explained_mismatches', 'remaining_unexplained_mismatches',
           'original_report_sha256', 'original_replays_sha256')})
    for case in reconciliation.get('replays', []):
        details = case['path_evidence']
        print('REPLAY', case['origin_index'], case['destination_index'], details['classification'],
              'COPY_METRICS', details['changed_copy_edges_have_equal_metrics'],
              'DELTA', details['independent_minus_shared'])
