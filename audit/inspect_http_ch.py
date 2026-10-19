import json
from pathlib import Path
base = Path(__file__).resolve().parent.parent / 'docs/matrix/regional-http-benchmark'
report = json.loads((base / 'http-benchmark-report.json').read_text())
requests = json.loads((base / 'requests.json').read_text())
print('REPORT KEYS', list(report))
for key, value in report.items():
    if isinstance(value, list):
        print('LIST', key, 'LEN', len(value), 'FIRST', json.dumps(value[0] if value else None)[:7000])
    elif not isinstance(value, dict):
        print(key, value)
print('REQUESTS', json.dumps(requests, indent=2)[:9000])
print('PROVENANCE', json.dumps(report.get('matrix_provenance'), indent=2)[:3000])
