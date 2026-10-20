#!/usr/bin/env python3
"""Streaming, restartable local all-OD benchmark; generated evidence belongs in a release.

The input points are fixed before any requests. Binary records are little-endian
<qdd: milliseconds, metres, profile weight; an unreachable pair is (-1, NaN, NaN).
Only an explicit ConnectionNotFoundException is recorded as unreachable.
"""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import csv
from datetime import datetime, timezone
import gzip
import hashlib
import heapq
import http.client
import json
import math
from pathlib import Path
import platform
import random
import struct
import sys
import threading
import time


RECORD = struct.Struct('<qdd')
MISSING = RECORD.pack(-1, math.nan, math.nan)


def stamp():
    return datetime.now(timezone.utc).isoformat()


def encoded(value):
    return json.dumps(value, separators=(',', ':'), allow_nan=False).encode('utf-8')


def sha(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def save(path, value):
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_bytes(encoded(value) + b'\n')
    temporary.replace(path)


class Client:
    def __init__(self, args):
        self.connection = http.client.HTTPConnection(args.host, args.port, timeout=args.http_timeout)
        self.opened = 0
        self.closes = 0

    def request(self, endpoint, body=None):
        self.opened += self.connection.sock is None
        start = time.perf_counter()
        self.connection.request('GET' if body is None else 'POST', endpoint, body=body,
                                headers={'Content-Type': 'application/json', 'Connection': 'keep-alive',
                                         'Accept-Encoding': 'identity'})
        response = self.connection.getresponse()
        raw = response.read()
        self.closes += int(response.will_close)
        parsed = json.loads(raw)
        return response.status, parsed, raw, time.perf_counter() - start

    def preopen(self):
        status, body, _, _ = self.request('/info')
        if status != 200:
            raise RuntimeError(f'/info failed: HTTP {status}: {body}')
        return body

    def close(self):
        self.connection.close()


def point_data(args):
    path = args.points or args.output_dir / 'points.json'
    area = json.loads(path.read_text(encoding='utf-8'))
    points = area['points'] if isinstance(area, dict) else area
    coords, ids = [], []
    for i, point in enumerate(points):
        coords.append([point['lon'], point['lat']] if isinstance(point, dict) else list(point))
        ids.append(str(point.get('id', f'P{i + 1:04}')) if isinstance(point, dict) else f'P{i + 1:04}')
    if not coords or len({tuple(p) for p in coords}) != len(coords) or len(set(ids)) != len(ids):
        raise ValueError('Points must have unique coordinates and IDs')
    if not all(len(p) == 2 and all(isinstance(v, (int, float)) and math.isfinite(v) for v in p)
               and abs(p[0]) <= 180 and abs(p[1]) <= 90 for p in coords):
        raise ValueError('Invalid longitude/latitude points')
    return path, coords, ids


def provenance(args, point_path, n):
    root = Path(__file__).resolve().parents[1]
    artifact = root / 'web/target/graphhopper-web-11.0-SNAPSHOT.jar'
    config = args.server_config or root / 'docs/matrix/config-manchester.yml'
    return {'status': 'INCOMPLETE', 'created_utc': stamp(), 'profile': args.profile,
            'points': n, 'cells': n * n, 'off_diagonal_directed_pairs': n * (n - 1),
            'points_file': str(point_path.resolve()), 'points_sha256': sha(point_path),
            'script_sha256': sha(Path(__file__)), 'python': sys.version,
            'python_executable': sys.executable, 'platform': platform.platform(),
            'endpoint': f'http://{args.host}:{args.port}',
            'jar_sha256': sha(artifact) if artifact.exists() else None,
            'server_config_file': str(config.resolve()),
            'config_sha256': sha(config) if config.exists() else None,
            'binary_format': '<qdd = int64 time_ms, float64 distance_m, float64 weight; -1,NaN,NaN is unreachable',
            'declared_server_matrix_limits': {'max_cells': args.matrix_max_cells,
                'max_visited_states': args.matrix_max_visited_states, 'timeout_ms': args.matrix_timeout_ms,
                'note': 'Operator assertions only; these arguments do not change the server.'}}


def route_payload(profile, coords, i, j):
    return encoded({'profile': profile, 'points': [coords[i], coords[j]],
                    'instructions': False, 'calc_points': False, 'snap_preventions': []})


def route_record(status, body):
    if status == 200 and 'message' not in body and len(body.get('paths', [])) == 1:
        p = body['paths'][0]
        t, d, w = p['time'], p['distance'], p['weight']
        if type(t) is not int or t < 0 or not all(isinstance(v, (int, float)) and math.isfinite(v) and v >= 0 for v in (d, w)):
            raise ValueError(f'Invalid route metrics: {p}')
        return RECORD.pack(t, d, w)
    details = {hint.get('details') for hint in body.get('hints', [])}
    if status == 400 and details == {'com.graphhopper.util.exceptions.ConnectionNotFoundException'}:
        return MISSING
    raise RuntimeError(f'Unexpected /route HTTP {status}: {body}')


def matrix_request(args, coords, origins=None):
    payload = {'profile': args.profile, 'out_arrays': ['times_ms', 'distances', 'weights'],
               'fail_fast': False, 'local': {'algorithm': args.mode}}
    if origins is None:
        payload['points'] = coords
    else:
        payload.update(from_points=[coords[i] for i in origins], to_points=coords)
    return encoded(payload)


def matrix_binary(body, rows, columns, path):
    keys = ('times_ms', 'distances', 'weights')
    for key in keys:
        if len(body.get(key, [])) != rows or any(len(row) != columns for row in body[key]):
            raise ValueError(f'Matrix dimensions invalid: {key}')
    disconnected = set()
    for hint in body.get('hints', []):
        if hint.get('details') != 'CONNECTION_NOT_FOUND':
            raise ValueError(f'Matrix contains invalid snaps or unexpected hints: {hint}')
        for pair in hint['point_pairs']:
            if not (isinstance(pair, list) and len(pair) == 2 and all(type(v) is int for v in pair)
                    and 0 <= pair[0] < rows and 0 <= pair[1] < columns):
                raise ValueError(f'Invalid disconnected pair indexes: {pair}')
            disconnected.add(tuple(pair))
    reachable = 0
    with path.open('xb') as stream:
        for i in range(rows):
            row = bytearray()
            for j in range(columns):
                t, d, w = (body[key][i][j] for key in keys)
                if t is None or d is None or w is None:
                    if not (t is d is w is None and (i, j) in disconnected):
                        raise ValueError(f'Invalid matrix null classification: {i},{j}')
                    row.extend(MISSING)
                else:
                    if (i, j) in disconnected or type(t) is not int or t < 0 or not all(
                            isinstance(v, (int, float)) and math.isfinite(v) and v >= 0 for v in (d, w)):
                        raise ValueError(f'Invalid matrix metrics: {i},{j}')
                    row.extend(RECORD.pack(t, d, w))
                    reachable += 1
            stream.write(row)
    return reachable


def matrix_phase(args, coords, point_path):
    stem = f'matrix-{args.profile}-{args.mode}-{args.label}'
    meta_path = args.output_dir / (stem + '.json')
    if meta_path.exists():
        raise FileExistsError(meta_path)
    report = provenance(args, point_path, len(coords))
    report.update(method='matrix', mode=args.mode,
                  timing_scope='One full HTTP request including response bytes and JSON parsing; pre-opened persistent connection; archiving excluded.')
    client = Client(args)
    try:
        report['server_info'] = client.preopen()
        request = matrix_request(args, coords)
        status, body, raw, elapsed = client.request('/matrix', request)
        report.update(http_status=status, elapsed_seconds=elapsed, request_bytes=len(request), response_bytes=len(raw))
        with gzip.open(args.output_dir / (stem + '-response.json.gz'), 'xb') as archive:
            archive.write(raw)
        if status != 200 or 'message' in body:
            report.update(status='REQUEST_FAILED', error=body)
            raise RuntimeError(f'Matrix HTTP {status}: {body}')
        info = body['info']['local_matrix']
        if info.get('profile') != args.profile or info.get('origin_count') != len(coords) or info.get('destination_count') != len(coords):
            raise ValueError(f'Matrix metadata disagrees with request: {info}')
        if args.mode == 'auto' and info.get('algorithm') != 'flexible':
            raise ValueError(f'Unexpected auto backend: {info}')
        if args.mode == 'ch_experimental' and args.profile == 'foot' and info.get('algorithm') != 'ch_experimental':
            raise ValueError(f'Experimental foot backend was not selected: {info}')
        if args.mode == 'ch_experimental' and args.profile == 'car' and not (
                info.get('algorithm') == 'flexible' and info.get('fallback_reason') == 'TURN_COST_PROFILE_UNSUPPORTED'):
            raise ValueError(f'Unexpected experimental car fallback: {info}')
        binary = args.output_dir / (stem + '.bin')
        report['reachable'] = matrix_binary(body, len(coords), len(coords), binary)
        report.update(status='COMPLETE', metadata=info, binary_sha256=sha(binary), binary_bytes=binary.stat().st_size)
        print(f'{stem}: {elapsed:.3f} s, {report["reachable"]}/{len(coords)**2} reachable', flush=True)
    except Exception as exc:
        report.setdefault('error', f'{type(exc).__name__}: {exc}')
        if report['status'] == 'INCOMPLETE':
            report['status'] = 'FAILED'
        raise
    finally:
        report.update(finished_utc=stamp(), connections_opened=client.opened, server_closes=client.closes)
        client.close()
        save(meta_path, report)


def sample_routes(args, coords, indices, workers):
    clients = [Client(args) for _ in range(workers)]
    try:
        for client in clients:
            client.preopen()
        for index in indices[:min(32, len(indices))]:
            status, body, _, _ = clients[0].request('/route', route_payload(args.profile, coords, *divmod(index, len(coords))))
            route_record(status, body)
        def work(worker):
            total_bytes, request_seconds = 0, 0.0
            for index in indices[worker::workers]:
                status, body, raw, elapsed = clients[worker].request('/route', route_payload(args.profile, coords, *divmod(index, len(coords))))
                route_record(status, body)
                total_bytes += len(raw)
                request_seconds += elapsed
            return total_bytes, request_seconds
        start = time.perf_counter()
        with ThreadPoolExecutor(max_workers=workers) as pool:
            results = list(pool.map(work, range(workers)))
        elapsed = time.perf_counter() - start
        return {'workers': workers, 'requests': len(indices), 'elapsed_seconds': elapsed,
                'requests_per_second': len(indices) / elapsed,
                'projected_full_workload_seconds': elapsed * len(coords)**2 / len(indices),
                'projection_is_not_a_measured_full_run': True,
                'response_bytes': sum(r[0] for r in results), 'sum_request_seconds': sum(r[1] for r in results),
                'connections_opened': sum(c.opened for c in clients), 'server_closes': sum(c.closes for c in clients)}
    finally:
        for client in clients:
            client.close()


def pilot_phase(args, coords, point_path):
    path = args.output_dir / f'pilot-{args.profile}-{args.mode}-{args.label}.json'
    if path.exists():
        raise FileExistsError(path)
    n = len(coords)
    report = provenance(args, point_path, n)
    origins = sorted({round(i * (n - 1) / 19) for i in range(20)})
    report['matrix_origin_indexes'] = origins
    client = Client(args)
    try:
        report['server_info'] = client.preopen()
        status, body, raw, elapsed = client.request('/matrix', matrix_request(args, coords, origins))
        with gzip.open(path.with_suffix('.matrix-response.json.gz'), 'xb') as archive:
            archive.write(raw)
        report['matrix'] = {'http_status': status, 'elapsed_seconds': elapsed, 'response_bytes': len(raw),
                            'metadata': body.get('info', {}).get('local_matrix'),
                            'error': body if status != 200 else None}
        if status == 200:
            report['matrix']['reachable'] = matrix_binary(body, len(origins), n, path.with_suffix('.matrix.bin'))
        print(f'Pilot {args.profile} matrix {len(origins)}x{n}: HTTP {status}, {elapsed:.3f} s', flush=True)
        rng = random.Random(20261021)
        pairs = sorted(rng.sample(range(n*n), min(10000, n*n)))
        serial_pairs = [pairs[round(i * (len(pairs) - 1) / (min(1000, len(pairs)) - 1))]
                        for i in range(min(1000, len(pairs)))] if len(pairs) > 1 else pairs
        report['route_samples'] = []
        for workers, sample in ((1, serial_pairs), (4, pairs), (8, pairs)):
            result = sample_routes(args, coords, sample, workers)
            report['route_samples'].append(result)
            print(f'Pilot {args.profile} /route workers={workers}: {len(sample)} calls in {result["elapsed_seconds"]:.3f} s; projected full {result["projected_full_workload_seconds"]:.1f} s', flush=True)
            save(path, report)
        report['status'] = 'COMPLETE'
    except Exception as exc:
        report.update(status='FAILED', error=f'{type(exc).__name__}: {exc}')
        raise
    finally:
        client.close()
        report['finished_utc'] = stamp()
        save(path, report)


def routes_phase(args, coords, point_path):
    n = len(coords)
    stem = f'routes-{args.profile}-w{args.workers}-{args.label}'
    meta_path = args.output_dir / (stem + '.json')
    binary = args.output_dir / (stem + '.bin')
    checkpoint = args.output_dir / (stem + '-checkpoint.json')
    report = provenance(args, point_path, n)
    completed, segments, previous_elapsed = set(), [], 0.0
    if checkpoint.exists() or meta_path.exists() or binary.exists():
        if not args.resume or not checkpoint.exists() or not binary.exists():
            raise FileExistsError(f'Existing run {stem}; use a new label, or --resume for an incomplete checkpoint')
        old = json.loads(checkpoint.read_text())
        if old.get('status') == 'COMPLETE' or old['points_sha256'] != report['points_sha256'] or old['profile'] != args.profile or old['workers'] != args.workers:
            raise ValueError('Cannot resume complete or incompatible checkpoint')
        if old.get('script_sha256') != report['script_sha256'] or old.get('jar_sha256') != report['jar_sha256'] or old.get('config_sha256') != report['config_sha256']:
            raise ValueError('Cannot resume with a different script, JAR, or server config')
        completed = set(old['completed_rows'])
        segments = old.get('segments', [])
        previous_elapsed = old.get('active_elapsed_seconds', 0.0)
        if binary.stat().st_size != n*n*RECORD.size:
            raise ValueError('Resume binary length mismatch')
    else:
        with binary.open('xb') as stream:
            stream.truncate(n*n*RECORD.size)
    report.update(method='default_route', workers=args.workers, segments=segments,
                  timing_scope='Complete client workload wall time, including payload creation, HTTP, JSON parsing, binary writes and progress checkpoints; connections pre-opened and 32 warmup requests excluded. A resumed run reports summed active wall time and its segments.')
    lock, stop = threading.Lock(), threading.Event()
    clients = [Client(args) for _ in range(args.workers)]
    row_records = []
    baseline_count = len(completed)
    started = None
    errors = []

    def checkpoint_now(status='INCOMPLETE'):
        elapsed = previous_elapsed + (time.perf_counter() - started if started is not None else 0)
        save(checkpoint, {**report, 'status': status, 'completed_rows': sorted(completed),
                         'completed_requests': len(completed)*n, 'active_elapsed_seconds': elapsed,
                         'updated_utc': stamp()})

    def worker(worker_index):
        client = clients[worker_index]
        with binary.open('r+b', buffering=0) as stream:
            for i in range(worker_index, n, args.workers):
                if stop.is_set():
                    return
                if i in completed:
                    continue
                row = bytearray()
                request_seconds, response_bytes = 0.0, 0
                row_start = time.perf_counter()
                try:
                    for j in range(n):
                        if stop.is_set():
                            return
                        status, body, raw, seconds = client.request('/route', route_payload(args.profile, coords, i, j))
                        row.extend(route_record(status, body))
                        request_seconds += seconds
                        response_bytes += len(raw)
                    stream.seek(i*n*RECORD.size)
                    stream.write(row)
                    record = {'row': i, 'worker': worker_index, 'requests': n,
                              'row_elapsed_seconds': time.perf_counter()-row_start,
                              'sum_request_seconds': request_seconds, 'response_bytes': response_bytes}
                    with lock:
                        completed.add(i)
                        row_records.append(record)
                        if len(completed) % 10 == 0 or len(completed) == n:
                            checkpoint_now()
                            elapsed = time.perf_counter()-started
                            done_now = (len(completed)-baseline_count)*n
                            eta = elapsed*(n*n-len(completed)*n)/done_now if done_now else None
                            print(f'{stem}: {len(completed)*n:,}/{n*n:,} calls; segment {elapsed:.1f} s; ETA {eta:.1f} s', flush=True)
                except Exception as exc:
                    with lock:
                        errors.append({'row': i, 'destination': len(row)//RECORD.size,
                                       'type': type(exc).__name__, 'message': str(exc)})
                        stop.set()
                    return

    try:
        checkpoint_now()
        for client in clients:
            report['server_info'] = client.preopen()
        for index in range(32):
            status, body, _, _ = clients[index % args.workers].request('/route', route_payload(args.profile, coords, index % n, (index*37) % n))
            route_record(status, body)
        started = time.perf_counter()
        checkpoint_now()
        with ThreadPoolExecutor(max_workers=args.workers) as pool:
            futures = [pool.submit(worker, i) for i in range(args.workers)]
            for future in as_completed(futures):
                future.result()
        elapsed = time.perf_counter()-started
        report['segments'].append({'started_from_completed_rows': baseline_count, 'finished_rows': len(completed),
                                   'elapsed_seconds': elapsed, 'finished_utc': stamp()})
        report.update(status='COMPLETE' if len(completed) == n and not errors else 'INCOMPLETE',
                      elapsed_seconds=previous_elapsed+elapsed, completed_requests=len(completed)*n,
                      errors=errors, response_bytes_this_segment=sum(r['response_bytes'] for r in row_records),
                      sum_request_seconds_this_segment=sum(r['sum_request_seconds'] for r in row_records))
        checkpoint_now(report['status'])
        if report['status'] != 'COMPLETE':
            raise RuntimeError(f'Incomplete route batch: {errors}')
        report.update(binary_sha256=sha(binary), binary_bytes=binary.stat().st_size)
        print(f'{stem}: COMPLETE {n*n:,} calls in {report["elapsed_seconds"]:.3f} s', flush=True)
    except Exception as exc:
        report['fatal_error'] = f'{type(exc).__name__}: {exc}'
        if started is not None:
            checkpoint_now()
        raise
    finally:
        for client in clients:
            client.close()
        report.update(finished_utc=stamp(), connections_opened=sum(c.opened for c in clients),
                      server_closes=sum(c.closes for c in clients))
        if row_records:
            row_path = args.output_dir / (stem + '-rows.jsonl')
            with row_path.open('a', encoding='utf-8') as stream:
                for record in row_records:
                    stream.write(encoded(record).decode()+'\n')
        save(meta_path, report)


def java_round(value, places):
    scaled = value * 10**places
    integer = math.floor(scaled)
    return (integer + int(scaled - integer >= .5)) / 10**places


def compare_phase(args, coords, ids):
    matrix_label, routes_label = args.matrix_label or args.label, args.routes_label or args.label
    matrix_stem = f'matrix-{args.profile}-{args.mode}-{matrix_label}'
    route_stem = f'routes-{args.profile}-w{args.workers}-{routes_label}'
    stem = f'comparison-{args.profile}-{args.mode}-w{args.workers}-{matrix_label}-{routes_label}'
    target = args.output_dir / (stem + '.json')
    if target.exists():
        raise FileExistsError(target)
    matrix_meta = json.loads((args.output_dir / (matrix_stem+'.json')).read_text())
    route_meta = json.loads((args.output_dir / (route_stem+'.json')).read_text())
    if matrix_meta['status'] != 'COMPLETE' or route_meta['status'] != 'COMPLETE' or matrix_meta['points_sha256'] != route_meta['points_sha256']:
        raise ValueError('Comparison requires complete runs with identical ordered points')
    report = {'status': 'INCOMPLETE', 'created_utc': stamp(), 'points_sha256': matrix_meta['points_sha256'],
              'matrix': matrix_stem, 'route': route_stem, 'cells': len(coords)**2,
              'reachable_both': 0, 'unreachable_both': 0, 'reachability_mismatches': 0,
              'exact_agreements': {'time_ms': 0, 'distance_m': 0, 'weight': 0},
              'http_rounding_agreements': {'time_ms': 0, 'distance_m': 0, 'weight': 0},
              'max_abs_delta': {'time_ms': 0, 'distance_m': 0.0, 'weight': 0.0},
              'time_or_distance_differing_cells': 0, 'distance_rounding_differing_cells': 0,
              'delta_convention': 'matrix minus /route; exact equality and HTTP rounding are reported separately, without tolerance.'}
    heaps = {'time_ms': [], 'distance_m': []}
    n = len(coords)
    fields = ['from', 'to', 'route_time_ms', 'matrix_time_ms', 'delta_time_ms',
              'route_distance_m', 'matrix_distance_m', 'delta_distance_m',
              'route_weight', 'matrix_weight', 'delta_weight']
    matrix_path, route_path = args.output_dir/(matrix_stem+'.bin'), args.output_dir/(route_stem+'.bin')
    if any(path.stat().st_size != n*n*RECORD.size for path in (matrix_path, route_path)):
        raise ValueError('Comparison binary length mismatch')
    report['binary_sha256'] = {'matrix': sha(matrix_path), 'route': sha(route_path)}
    if report['binary_sha256']['matrix'] != matrix_meta['binary_sha256'] or report['binary_sha256']['route'] != route_meta['binary_sha256']:
        raise ValueError('Comparison binary SHA-256 disagrees with completed-run metadata')
    with matrix_path.open('rb') as matrix, route_path.open('rb') as routes, gzip.open(
            args.output_dir/(stem+'-time-distance-differences.csv.gz'), 'xt', encoding='utf-8', newline='') as differences:
        writer = csv.DictWriter(differences, fieldnames=fields)
        writer.writeheader()
        for i in range(n):
            matrix_row, route_row = matrix.read(n*RECORD.size), routes.read(n*RECORD.size)
            for j, (m, r) in enumerate(zip(RECORD.iter_unpack(matrix_row), RECORD.iter_unpack(route_row))):
                if (m[0] < 0) != (r[0] < 0):
                    report['reachability_mismatches'] += 1
                    writer.writerow({'from': ids[i], 'to': ids[j], 'route_time_ms': r[0], 'matrix_time_ms': m[0]})
                    continue
                if m[0] < 0:
                    report['unreachable_both'] += 1
                    continue
                report['reachable_both'] += 1
                delta = [m[k]-r[k] for k in range(3)]
                normalized = [m[0], java_round(m[1], 3), java_round(m[2], 6)]
                for k, metric in enumerate(('time_ms', 'distance_m', 'weight')):
                    report['exact_agreements'][metric] += m[k] == r[k]
                    report['http_rounding_agreements'][metric] += normalized[k] == r[k]
                    report['max_abs_delta'][metric] = max(report['max_abs_delta'][metric], abs(delta[k]))
                row = dict(zip(fields, [ids[i], ids[j], r[0], m[0], delta[0], r[1], m[1], delta[1], r[2], m[2], delta[2]]))
                if delta[0] != 0 or delta[1] != 0:
                    report['time_or_distance_differing_cells'] += 1
                    writer.writerow(row)
                report['distance_rounding_differing_cells'] += normalized[1] != r[1]
                for k, metric in ((0, 'time_ms'), (1, 'distance_m')):
                    if delta[k] == 0:
                        continue
                    item = (abs(delta[k]), i*n+j, row)
                    if len(heaps[metric]) < 20:
                        heapq.heappush(heaps[metric], item)
                    elif item[:2] > heaps[metric][0][:2]:
                        heapq.heapreplace(heaps[metric], item)
    report['largest_differences'] = {metric: [item[2] for item in sorted(heap, reverse=True)] for metric, heap in heaps.items()}
    report.update(status='COMPLETE', finished_utc=stamp(),
                  matrix_elapsed_seconds=matrix_meta['elapsed_seconds'], route_elapsed_seconds=route_meta['elapsed_seconds'],
                  route_divided_by_matrix_runtime=route_meta['elapsed_seconds']/matrix_meta['elapsed_seconds'])
    save(target, report)
    print(json.dumps(report, indent=2), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--phase', choices=['pilot', 'matrix', 'routes', 'compare'], required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    parser.add_argument('--points', type=Path)
    parser.add_argument('--server-config', type=Path,
                        help='Actual loaded YAML config path for provenance; does not change the server')
    parser.add_argument('--profile', choices=['car', 'foot'], default='car')
    parser.add_argument('--workers', type=int, choices=[1, 4, 8], default=8)
    parser.add_argument('--mode', choices=['auto', 'ch_experimental'], default='auto')
    parser.add_argument('--label', default='r1')
    parser.add_argument('--matrix-label')
    parser.add_argument('--routes-label')
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=8999)
    parser.add_argument('--http-timeout', type=float, default=660)
    parser.add_argument('--matrix-max-cells', type=int, default=100000)
    parser.add_argument('--matrix-max-visited-states', type=int, default=1000000)
    parser.add_argument('--matrix-timeout-ms', type=int, default=30000)
    parser.add_argument('--resume', action='store_true')
    args = parser.parse_args()
    for label in (args.label, args.matrix_label, args.routes_label):
        if label is not None and (not label or any(ch not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-' for ch in label)):
            parser.error('Labels must contain only letters, digits, underscore or hyphen')
    args.output_dir.mkdir(parents=True, exist_ok=True)
    point_path, coords, ids = point_data(args)
    if args.phase == 'matrix':
        matrix_phase(args, coords, point_path)
    elif args.phase == 'pilot':
        pilot_phase(args, coords, point_path)
    elif args.phase == 'routes':
        routes_phase(args, coords, point_path)
    else:
        compare_phase(args, coords, ids)


if __name__ == '__main__':
    main()
