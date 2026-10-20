#!/usr/bin/env python3
r"""Bounded geographic all-OD benchmark; detailed outputs belong in a release.

Uses stdlib only. The default 4x4 grid is fixed before querying the server.
Run against run-matrix.cmd regional (prepared CH, unchanged matrix limits):
  python scripts\benchmark-manchester-area.py --output-dir ..\audit\manchester-area-comparison
Do not commit the generated response ledger or measurement files.
"""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
import csv
from datetime import datetime, timezone
import hashlib
import http.client
import json
import math
from pathlib import Path
import platform
import statistics
import sys
import time


def dump(value):
    return json.dumps(value, separators=(',', ':'), allow_nan=False)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


class Client:
    def __init__(self, host, port):
        self.connection = http.client.HTTPConnection(host, port, timeout=75)
        self.opened = 0
        self.closed = 0

    def request(self, endpoint, payload=None):
        if self.connection.sock is None:
            self.opened += 1
        start = time.perf_counter_ns()
        self.connection.request('GET' if payload is None else 'POST', endpoint, body=payload,
                                headers={'Content-Type': 'application/json', 'Connection': 'keep-alive',
                                         'Accept-Encoding': 'identity'})
        response = self.connection.getresponse()
        raw = response.read()
        self.closed += int(response.will_close)
        body = json.loads(raw)
        elapsed = (time.perf_counter_ns() - start) / 1e6
        return {'status': response.status, 'body': body, 'raw': raw.decode('utf-8'),
                'bytes': len(raw), 'elapsed_ms': elapsed}

    def close(self):
        self.connection.close()


def route_cell(response):
    body, status = response['body'], response['status']
    if status == 200 and 'message' not in body:
        assert len(body['paths']) == 1
        p = body['paths'][0]
        assert all(isinstance(p[k], (int, float)) and math.isfinite(p[k]) and p[k] >= 0
                   for k in ('weight', 'time', 'distance'))
        return {'kind': 'route', 'weight': p['weight'], 'time_ms': p['time'], 'distance_m': p['distance']}
    kinds = {h.get('details') for h in body.get('hints', [])}
    if status == 400 and kinds == {'com.graphhopper.util.exceptions.ConnectionNotFoundException'}:
        return {'kind': 'disconnected', 'weight': None, 'time_ms': None, 'distance_m': None}
    raise ValueError(f'Unclassified route error HTTP {status}: {body}')


def matrix_cells(response, n):
    b = response['body']
    if response['status'] != 200 or 'message' in b:
        raise ValueError(f'Matrix HTTP {response["status"]}: {b}')
    for metric in ('weights', 'times_ms', 'distances', 'times'):
        assert len(b[metric]) == n and all(len(row) == n for row in b[metric]), metric
    pairs, invalid = set(), False
    for hint in b.get('hints', []):
        if hint.get('details') == 'CONNECTION_NOT_FOUND':
            pairs.update(tuple(p) for p in hint['point_pairs'])
        else:
            invalid = True
    assert not invalid, b.get('hints')
    cells = []
    for i in range(n):
        for j in range(n):
            w, t, d, sec = (b[k][i][j] for k in ('weights', 'times_ms', 'distances', 'times'))
            if w is None or t is None or d is None:
                assert w is t is d is sec is None and (i, j) in pairs
                cells.append({'kind': 'disconnected', 'weight': None, 'time_ms': None, 'distance_m': None})
            else:
                assert (i, j) not in pairs and type(t) is int and sec == t // 1000
                assert all(math.isfinite(x) and x >= 0 for x in (w, t, d))
                cells.append({'kind': 'route', 'weight': w, 'time_ms': t, 'distance_m': d})
    return cells


def java_round(value, places):
    scaled = value * 10**places
    integer = math.floor(scaled)
    return (integer + int(scaled - integer >= .5)) / 10**places


def comparison(reference, actual, points):
    assert len(reference) == len(actual) == len(points)**2
    differences = []
    maxima = {k: 0 for k in ('weight', 'time_ms', 'distance_m')}
    exact = {k: 0 for k in maxima}
    serialized = {k: 0 for k in maxima}
    reachability = 0
    for index, (a, b) in enumerate(zip(reference, actual)):
        issues, delta = [], {}
        if a['kind'] != b['kind']:
            reachability += 1
            issues.append('reachability')
        elif a['kind'] == 'route':
            for metric in maxima:
                difference = b[metric] - a[metric]
                delta[metric] = difference
                maxima[metric] = max(maxima[metric], abs(difference))
                exact[metric] += int(difference == 0)
                normalized = b[metric] if metric == 'time_ms' else java_round(b[metric], 6 if metric == 'weight' else 3)
                serialized[metric] += int(normalized == a[metric])
                if normalized != a[metric]:
                    issues.append(metric)
        if issues:
            differences.append({'from': points[index // len(points)]['id'], 'to': points[index % len(points)]['id'],
                                'issues': issues, 'delta': delta, 'reference': a, 'actual': b})
    return {'cells': len(actual), 'reachability_mismatches': reachability,
            'reachable_reference': sum(c['kind'] == 'route' for c in reference),
            'exact_metric_agreements': exact, 'http_rounding_agreements': serialized,
            'max_abs_delta': maxima, 'differing_cells': differences}


def write_csv(path, rows):
    if not rows:
        return
    with path.open('w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def run(args):
    out = args.output_dir.resolve()
    out.mkdir(parents=True, exist_ok=True)
    result_path = out / 'results.json'
    assert not result_path.exists(), 'Refusing to overwrite an earlier run; select another output directory'
    point_path = out / 'points.json'
    if not point_path.exists():
        point_path.write_text(json.dumps({'title': 'Manchester and Salford: 16-point routing comparison',
            'bounds': {'west': -2.270, 'east': -2.215, 'south': 53.465, 'north': 53.495},
            'points': [{'id': f'P{r*4+c+1:02}', 'lon': round(-2.270+c*.055/3, 10),
                        'lat': round(53.465+r*.030/3, 10)} for r in range(4) for c in range(4)]}, indent=2)+'\n')
    area = json.loads(point_path.read_text())
    points = area['points']
    assert len(points) == 16 and len({(p['lon'], p['lat']) for p in points}) == 16
    coords = [[p['lon'], p['lat']] for p in points]
    n = len(points)
    report = {'status': 'INCOMPLETE', 'created_utc': datetime.now(timezone.utc).isoformat(),
        'area': area, 'points_sha256': sha(point_path), 'script_sha256': sha(Path(__file__)),
        'profiles': {}, 'samples': [], 'repeats': args.repeats, 'warmup_batches_per_method': 1,
        'python': sys.version, 'executable': sys.executable, 'platform': platform.platform(),
        'endpoint': f'http://{args.host}:{args.port}', 'workers': 4,
        'timing_scope': 'Full client workload wall time including HTTP, JSON parsing and worker dispatch; preencoded requests, persistent connections pre-opened, archiving and metric comparison outside timer. Loaded prepared graph; import/CH preparation excluded.',
        'route_dispatch': 'Configured prepared CH, source/config inferred; normal route JSON has no backend marker. Dijkstra oracle explicitly disables CH/LM.',
        'limits': {'timeout_ms': 30000, 'max_visited_states': args.matrix_max_visited_states,
                   'admission': 2, 'note': 'Recorded operator assertion; verify server JVM flags in server-runtime.json.'},
        'requests': {}, 'summary': [], 'schedule': []}
    root = Path(__file__).resolve().parents[1]
    report['server_config_sha256'] = sha(root / 'docs/matrix/config-manchester.yml')
    jar = root / 'web/target/graphhopper-web-11.0-SNAPSHOT.jar'
    report['server_jar_sha256'] = sha(jar)
    (out / 'benchmark-script.py').write_bytes(Path(__file__).read_bytes())
    clients = [Client(args.host, args.port) for _ in range(7)]
    serial, oracle_client, matrix_client, *parallel_clients = clients
    raw_path = out / 'http-responses.jsonl'
    raw = raw_path.open('x', encoding='utf-8', newline='\n')
    pool = ThreadPoolExecutor(max_workers=4)

    def batch(profile, method, repeat, payloads):
        connections_before = sum(c.opened for c in clients)
        closes_before = sum(c.closed for c in clients)
        def subset(client, indices):
            return [(i, client.request('/route', payloads[i])) for i in indices]
        start = time.perf_counter_ns()
        if method.startswith('matrix'):
            responses = [(0, matrix_client.request('/matrix', payloads))]
        elif method == 'route_parallel4':
            jobs = [pool.submit(subset, parallel_clients[w], range(w, n*n, 4)) for w in range(4)]
            responses = sorted(item for job in jobs for item in job.result())
        else:
            responses = subset(oracle_client if method == 'route_dijkstra' else serial, range(n*n))
        elapsed = (time.perf_counter_ns() - start) / 1e6
        for index, response in responses:
            raw.write(dump({'profile': profile, 'method': method, 'repeat': repeat, 'index': index,
                            'status': response['status'], 'elapsed_ms': response['elapsed_ms'],
                            'request': payloads.decode() if method.startswith('matrix') else payloads[index].decode(),
                            'response': response['raw']})+'\n')
        raw.flush()
        info = {}
        if method.startswith('matrix'):
            response = responses[0][1]
            if response['status'] != 200:
                codes = [h.get('details') for h in response['body'].get('hints', [])]
                assert response['status'] in (422, 503) and codes and all(
                    c in ('SEARCH_LIMIT_EXCEEDED', 'TIMEOUT', 'BUSY') for c in codes), response['body']
                cells = None
                info = {'error': response['body'], 'http_status': response['status']}
            else:
                cells = matrix_cells(response, n)
                info = response['body']['info']['local_matrix']
                assert info['profile'] == profile
                if method == 'matrix_auto':
                    assert info['algorithm'] == 'flexible', info
                elif profile == 'foot':
                    assert info['algorithm'] == 'ch_experimental', info
                else:
                    assert info['algorithm'] == 'flexible' and info.get('fallback_reason') == 'TURN_COST_PROFILE_UNSUPPORTED', info
        else:
            cells = [route_cell(response) for _, response in responses]
        sample = {'profile': profile, 'method': method, 'repeat': repeat, 'elapsed_ms': elapsed,
                  'http_requests': len(responses), 'response_bytes': sum(r['bytes'] for _, r in responses),
                  'connections_opened': sum(c.opened for c in clients)-connections_before,
                  'server_connection_closes': sum(c.closed for c in clients)-closes_before,
                  'status': 'COMPLETE' if cells is not None else 'REQUEST_FAILED',
                  'reachable': sum(c['kind'] == 'route' for c in cells) if cells is not None else None,
                  'http_status': responses[0][1]['status'] if method.startswith('matrix') else 200,
                  'visited_states': info.get('visited_states'), 'searches': info.get('searches'),
                  'algorithm': info.get('algorithm')}
        print(f'{profile} {method} repeat={repeat}: {elapsed:.2f} ms; {sample["reachable"]}/{n*n} reachable', flush=True)
        return sample, cells, info

    try:
        for c in clients:
            info_response = c.request('/info')
            assert info_response['status'] == 200
        report['server_info'] = info_response['body']
        for profile in ('foot', 'car'):
            route_requests = [dump({'profile': profile, 'points': [a, b], 'instructions': False,
                                   'calc_points': False, 'points_encoded': False, 'snap_preventions': []}).encode()
                              for a in coords for b in coords]
            oracle_requests = [dump({**json.loads(p), 'ch.disable': True, 'lm.disable': True,
                                     'algorithm': 'dijkstra'}).encode() for p in route_requests]
            matrix_base = {'profile': profile, 'points': coords, 'out_arrays': ['weights','times_ms','distances','times'],
                           'fail_fast': False}
            payloads = {'route_serial': route_requests, 'route_parallel4': route_requests,
                        'route_dijkstra': oracle_requests,
                        'matrix_auto': dump({**matrix_base, 'local': {'algorithm': 'auto'}}).encode(),
                        'matrix_ch_experimental': dump({**matrix_base, 'local': {'algorithm': 'ch_experimental'}}).encode()}
            report['requests'][profile] = {method: json.loads(p) if isinstance(p, bytes) else [json.loads(s) for s in p]
                                           for method, p in payloads.items()}
            entry = report['profiles'][profile] = {'measurements': [], 'comparisons': {}, 'warmup': {}}
            _, reference, _ = batch(profile, 'route_dijkstra', 'reference', oracle_requests)
            entry['dijkstra_reference'] = reference
            methods = ['route_serial', 'route_parallel4', 'matrix_auto', 'matrix_ch_experimental']
            warm = {}
            for method in methods:
                sample, cells, info = batch(profile, method, 'warmup', payloads[method])
                entry['warmup'][method] = sample
                warm[method] = cells
                if method.startswith('matrix'):
                    entry[method+'_metadata'] = info
            for repeat in range(args.repeats):
                rotation = repeat % len(methods)
                order = methods[rotation:]+methods[:rotation]
                report['schedule'].append({'profile': profile, 'repeat': repeat, 'methods': order})
                for method in order:
                    sample, cells, info = batch(profile, method, repeat, payloads[method])
                    assert cells == warm[method], f'Nondeterministic metrics: {profile}/{method}/{repeat}'
                    report['samples'].append(sample)
            entry['outcomes'] = warm
            for method, cells in warm.items():
                if cells is not None:
                    entry['comparisons']['dijkstra_vs_'+method] = comparison(reference, cells, points)
            if warm['matrix_auto'] is not None:
                entry['comparisons']['default_route_vs_matrix_auto'] = comparison(warm['route_serial'], warm['matrix_auto'], points)
            entry['comparisons']['matrix_auto_vs_ch_experimental_exact'] = {
                'exact_equal': warm['matrix_auto'] == warm['matrix_ch_experimental'] if warm['matrix_auto'] is not None else None,
                'cells': n*n if warm['matrix_auto'] is not None else 0}
            for method in methods:
                samples = [s for s in report['samples'] if s['profile'] == profile and s['method'] == method]
                times = [s['elapsed_ms'] for s in samples]
                report['summary'].append({'profile': profile, 'method': method, 'repeats': len(samples),
                    'median_ms': statistics.median(times), 'min_ms': min(times), 'max_ms': max(times),
                    'status': samples[0]['status'], 'http_status': samples[0]['http_status'],
                    'reachable': samples[0]['reachable'], 'http_requests': samples[0]['http_requests'],
                    'response_bytes': samples[0]['response_bytes'], 'visited_states': samples[0]['visited_states']})
        report['status'] = 'MEASURED'
    except Exception as exc:
        report['fatal_error'] = f'{type(exc).__name__}: {exc}'
        raise
    finally:
        pool.shutdown(wait=True)
        raw.close()
        for c in clients:
            c.close()
        report['finished_utc'] = datetime.now(timezone.utc).isoformat()
        report['ledger_sha256'] = sha(raw_path)
        report['connections'] = [{'opened': c.opened, 'server_closes': c.closed} for c in clients]
        result_path.write_text(json.dumps(report, indent=2, allow_nan=False)+'\n')
        write_csv(out / 'timing-samples.csv', report['samples'])
        write_csv(out / 'timing-summary.csv', report['summary'])
        rows = []
        for profile, entry in report['profiles'].items():
            for method, cells in {'route_dijkstra': entry.get('dijkstra_reference', []), **entry.get('outcomes', {})}.items():
                if cells is None:
                    continue
                for i, cell in enumerate(cells):
                    rows.append({'profile': profile, 'method': method, 'from': points[i//n]['id'],
                                 'to': points[i%n]['id'], **cell})
        write_csv(out / 'all-od-results.csv', rows)
        print(f'{report["status"]}: {result_path}', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-dir', type=Path, required=True)
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=8999)
    parser.add_argument('--repeats', type=int, default=5)
    parser.add_argument('--matrix-max-visited-states', type=int, default=1000000,
                        help='Record actual server property; this argument does not change server settings')
    args = parser.parse_args()
    assert 1 <= args.repeats <= 10
    run(args)
