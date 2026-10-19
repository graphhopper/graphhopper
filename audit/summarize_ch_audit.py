import json
import sys
from pathlib import Path
root = Path(__file__).resolve().parent.parent
file = root / (sys.argv[1] if len(sys.argv) > 1 else 'docs/matrix/regional-http-benchmark/ch-weight-reconciliation.json')
report = json.loads(file.read_text())
print({key: value for key, value in report.items() if not isinstance(value, (dict,list))})
for proof in report.get('unique_pair_proofs', []):
    print('PAIR', {k: v for k, v in proof.items() if not isinstance(v, (dict,list))})
    precision = proof.get('quantization_audit', {})
    print('PRECISION', {k: v for k, v in precision.items() if not isinstance(v, (dict,list))})
    if not precision.get('proved'):
        print('PRECISION FAILURES', precision.get('failures'))
for case in report.get('indexed_cases', []):
    if case.get('accepted_with_independent_path_evidence'):
        continue
    print('INDEXED FAILED', {k: v for k, v in case.items() if not isinstance(v, (dict,list))})
    print('MATRIX FLAGS', {k: v for k, v in case.get('matrix_vs_independent_path_evidence', {}).items() if isinstance(v,(bool,str))})
