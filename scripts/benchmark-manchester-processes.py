#!/usr/bin/env python3
"""Independent-process /route benchmark, using the frozen streaming benchmark.

Each process owns one persistent HTTP connection and one output-file handle.
Process startup, connection opening, and 32 total warmups precede a shared start
barrier. Never run this client concurrently with another measured workload.
Generated binaries, row ledgers, and reports belong in a release, outside Git.
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import multiprocessing
from pathlib import Path
import queue
import random
import sys
import time
import traceback


def support():
    path = Path(__file__).with_name('benchmark-manchester-scale.py')
    specification = importlib.util.spec_from_file_location('manchester_scale_support', path)
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


def child(worker, args, coords, indices, binary, ready, stop, messages):
    benchmark = support()
    client = benchmark.Client(args)
    stream = None
    row, destination = None, None
    try:
        info = client.preopen()
        warmups = 0
        for index in range(worker, 32, args.processes):
            i, j = index % len(coords), index * 37 % len(coords)
            status, body, _, _ = client.request('/route', benchmark.route_payload(args.profile, coords, i, j))
            benchmark.route_record(status, body)
            warmups += 1
        if binary is not None:
            stream = Path(binary).open('r+b', buffering=0)
        messages.put({'kind': 'ready', 'worker': worker, 'pid': multiprocessing.current_process().pid,
                      'server_info': info, 'warmup_requests': warmups,
                      'connections_opened': client.opened, 'server_closes': client.closes})
        ready.wait()
        if stop.is_set():
            return
        started = time.perf_counter()
        total_bytes, total_requests, request_seconds = 0, 0, 0.0
        if args.phase == 'pilot':
            for index in indices:
                if stop.is_set():
                    return
                row, destination = divmod(index, len(coords))
                status, body, raw, elapsed = client.request('/route', benchmark.route_payload(args.profile, coords, row, destination))
                benchmark.route_record(status, body)
                total_bytes += len(raw)
                total_requests += 1
                request_seconds += elapsed
        else:
            for row in range(worker, len(coords), args.processes):
                if stop.is_set():
                    return
                row_started = time.perf_counter()
                row_data = bytearray()
                row_bytes, row_request_seconds = 0, 0.0
                for destination in range(len(coords)):
                    if stop.is_set():
                        return
                    status, body, raw, elapsed = client.request('/route', benchmark.route_payload(args.profile, coords, row, destination))
                    row_data.extend(benchmark.route_record(status, body))
                    row_bytes += len(raw)
                    row_request_seconds += elapsed
                stream.seek(row*len(coords)*benchmark.RECORD.size)
                if stream.write(row_data) != len(row_data):
                    raise OSError(f'Short binary row write: {row}')
                total_bytes += row_bytes
                total_requests += len(coords)
                request_seconds += row_request_seconds
                messages.put({'kind': 'row', 'row': row, 'worker': worker, 'requests': len(coords),
                              'row_elapsed_seconds': time.perf_counter()-row_started,
                              'sum_request_seconds': row_request_seconds, 'response_bytes': row_bytes})
        if stream is not None:
            stream.close()
            stream = None
        messages.put({'kind': 'done', 'worker': worker, 'requests': total_requests,
                      'elapsed_seconds': time.perf_counter()-started,
                      'sum_request_seconds': request_seconds, 'response_bytes': total_bytes,
                      'connections_opened': client.opened, 'server_closes': client.closes})
    except BaseException as exc:
        stop.set()
        messages.put({'kind': 'error', 'worker': worker, 'row': row, 'destination': destination,
                      'type': type(exc).__name__, 'message': str(exc), 'traceback': traceback.format_exc()})
    finally:
        if stream is not None:
            stream.close()
        client.close()


def execute(args):
    benchmark = support()
    point_path, coords, _ = benchmark.point_data(args)
    n = len(coords)
    stem = (f'pilot-processes-{args.profile}-p{args.processes}-{args.label}' if args.phase == 'pilot'
            else f'routes-{args.profile}-w{args.processes}-{args.label}')
    output = args.output_dir / (stem+'.json')
    binary = None if args.phase == 'pilot' else args.output_dir/(stem+'.bin')
    row_path = args.output_dir/(stem+'-rows.jsonl')
    checkpoint = args.output_dir/(stem+'-checkpoint.json')
    for path in (output, binary, row_path, checkpoint):
        if path is not None and path.exists():
            raise FileExistsError(f'Use a new label; refusing to overwrite {path}')
    if binary is not None:
        with binary.open('xb') as stream:
            stream.truncate(n*n*benchmark.RECORD.size)
    report = benchmark.provenance(args, point_path, n)
    report.update(method='default_route_multiprocess', processes=args.processes, threads_per_process=1,
                  workers=args.processes, connections_per_process=1,
                  client_script_sha256=benchmark.sha(Path(__file__)),
                  support_script_sha256=benchmark.sha(Path(benchmark.__file__)),
                  timing_scope='Parent wall time from shared start release to all completion messages, including payload creation, HTTP, JSON parsing, row binary writes, progress/checkpoint writes and queue coordination; process startup, preopened connections, and 32 total warmup requests excluded.',
                  expected_requests=n*n if args.phase == 'routes' else min(args.sample_count, n*n),
                  warmup_requests=32, process_start_method='spawn', worker_startup=[], worker_results=[], errors=[])
    indices = None
    if args.phase == 'pilot':
        indices = sorted(random.Random(20261021).sample(range(n*n), report['expected_requests']))
        report.update(sample_seed=20261021,
                      sample_indexes_sha256=__import__('hashlib').sha256(benchmark.encoded(indices)).hexdigest(),
                      projection_is_not_a_measured_full_run=True)
    context = multiprocessing.get_context('spawn')
    ready, stop = context.Event(), context.Event()
    messages = context.Queue(maxsize=128)
    processes = [context.Process(target=child, args=(worker, args, coords,
                 indices[worker::args.processes] if indices is not None else None,
                 str(binary) if binary is not None else None, ready, stop, messages),
                 name=f'graphhopper-benchmark-{args.profile}-{worker}') for worker in range(args.processes)]
    completed_rows, done, ready_workers = set(), set(), set()
    started, measured_elapsed = None, None
    ledger = None

    def write_checkpoint(status='INCOMPLETE'):
        if binary is not None:
            elapsed = measured_elapsed if status == 'COMPLETE' and measured_elapsed is not None else (
                time.perf_counter()-started if started is not None else 0.0)
            benchmark.save(checkpoint, {**report, 'status': status, 'completed_rows': sorted(completed_rows),
                                        'completed_requests': len(completed_rows)*n,
                                        'active_elapsed_seconds': elapsed, 'updated_utc': benchmark.stamp()})

    def receive():
        while True:
            try:
                return messages.get(timeout=5)
            except queue.Empty:
                bad = [process for i, process in enumerate(processes)
                       if i not in done and process.exitcode is not None]
                if bad:
                    raise RuntimeError('Child exited without a completion message: '+
                                       ', '.join(f'{process.name}={process.exitcode}' for process in bad))

    try:
        write_checkpoint()
        benchmark.save(output, report)
        for process in processes:
            process.start()
        while len(ready_workers) != args.processes:
            message = receive()
            if message['kind'] == 'error':
                report['errors'].append(message)
                raise RuntimeError(f'Worker setup failed: {message}')
            if message['kind'] != 'ready' or message['worker'] in ready_workers:
                raise RuntimeError(f'Unexpected startup message: {message}')
            ready_workers.add(message['worker'])
            report['worker_startup'].append(message)
        report['server_info'] = report['worker_startup'][0]['server_info']
        if any(worker['server_info'] != report['server_info'] for worker in report['worker_startup']):
            raise RuntimeError('Workers reported inconsistent server information')
        if sum(worker['warmup_requests'] for worker in report['worker_startup']) != 32:
            raise RuntimeError('Incorrect total warmup count')
        if binary is not None:
            ledger = row_path.open('x', encoding='utf-8', newline='\n')
        report['barrier_release_utc'] = benchmark.stamp()
        started = time.perf_counter()
        ready.set()
        while len(done) != args.processes:
            message = receive()
            kind, worker = message['kind'], message['worker']
            if kind == 'error':
                report['errors'].append(message)
                raise RuntimeError(f'Worker request failed: {message}')
            if kind == 'row':
                row = message['row']
                if row in completed_rows or not 0 <= row < n or row % args.processes != worker or message['requests'] != n:
                    raise RuntimeError(f'Duplicate, missing or misassigned row: {message}')
                completed_rows.add(row)
                ledger.write(benchmark.encoded({key: value for key, value in message.items() if key != 'kind'}).decode()+'\n')
                if len(completed_rows) % 10 == 0 or len(completed_rows) == n:
                    ledger.flush()
                    write_checkpoint()
                    elapsed = time.perf_counter()-started
                    completed = len(completed_rows)*n
                    eta = elapsed*(n*n-completed)/completed
                    print(f'{stem}: {completed:,}/{n*n:,} calls; {elapsed:.1f}s; ETA {eta:.1f}s', flush=True)
            elif kind == 'done':
                if worker in done:
                    raise RuntimeError(f'Duplicate worker completion: {worker}')
                done.add(worker)
                report['worker_results'].append(message)
            else:
                raise RuntimeError(f'Unexpected measured-run message: {message}')
        measured_elapsed = time.perf_counter()-started
        report.update(elapsed_seconds=measured_elapsed,
                      completed_requests=sum(result['requests'] for result in report['worker_results']),
                      response_bytes=sum(result['response_bytes'] for result in report['worker_results']),
                      sum_request_seconds=sum(result['sum_request_seconds'] for result in report['worker_results']),
                      connections_opened=sum(result['connections_opened'] for result in report['worker_results']),
                      server_closes=sum(result['server_closes'] for result in report['worker_results']))
        if report['completed_requests'] != report['expected_requests'] or (binary is not None and len(completed_rows) != n):
            raise RuntimeError('Incomplete workload coverage')
        for process in processes:
            process.join(timeout=10)
            if process.exitcode != 0:
                raise RuntimeError(f'Unexpected child exit: {process.name}={process.exitcode}')
        if ledger is not None:
            ledger.close()
            ledger = None
        if binary is not None:
            if binary.stat().st_size != n*n*benchmark.RECORD.size:
                raise RuntimeError('Full binary length mismatch')
            report.update(binary_sha256=benchmark.sha(binary), binary_bytes=binary.stat().st_size,
                          row_ledger_sha256=benchmark.sha(row_path), unique_completed_rows=len(completed_rows))
        else:
            report.update(requests_per_second=report['completed_requests']/measured_elapsed,
                          projected_full_workload_seconds=measured_elapsed*n*n/report['completed_requests'])
        report['status'] = 'COMPLETE'
        write_checkpoint('COMPLETE')
        print(f'{stem}: COMPLETE {report["completed_requests"]:,} requests in {measured_elapsed:.3f}s', flush=True)
        if binary is None:
            print(f'Projection only: {report["projected_full_workload_seconds"]:.1f}s for {n*n:,}; no full workload measured', flush=True)
    except BaseException as exc:
        report.update(status='FAILED', fatal_error=f'{type(exc).__name__}: {exc}')
        if started is not None:
            report.setdefault('elapsed_seconds', time.perf_counter()-started)
        stop.set()
        ready.set()
        write_checkpoint('FAILED')
        raise
    finally:
        stop.set()
        ready.set()
        for process in processes:
            if process.pid is not None:
                process.join(timeout=2)
                if process.is_alive():
                    process.terminate()
                    process.join(timeout=2)
        if ledger is not None:
            ledger.close()
        report['finished_utc'] = benchmark.stamp()
        report['owned_child_exit_codes'] = [{'name': process.name, 'pid': process.pid, 'exitcode': process.exitcode}
                                            for process in processes]
        benchmark.save(output, report)
        messages.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--phase', choices=['pilot', 'routes'], required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    parser.add_argument('--points', type=Path)
    parser.add_argument('--server-config', type=Path)
    parser.add_argument('--profile', choices=['car', 'foot'], default='car')
    parser.add_argument('--processes', type=int, default=8)
    parser.add_argument('--sample-count', type=int, default=10000)
    parser.add_argument('--label', default='mp8')
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=8999)
    parser.add_argument('--http-timeout', type=float, default=660)
    parser.add_argument('--matrix-max-cells', type=int, default=1000000)
    parser.add_argument('--matrix-max-visited-states', type=int, default=1000000000)
    parser.add_argument('--matrix-timeout-ms', type=int, default=600000)
    args = parser.parse_args()
    if not 1 <= args.processes <= 64 or not 1 <= args.sample_count <= 100000:
        parser.error('Processes must be 1-64 and sample count 1-100000')
    if not args.label or any(ch not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-' for ch in args.label):
        parser.error('Labels must contain only letters, digits, underscore or hyphen')
    args.output_dir.mkdir(parents=True, exist_ok=True)
    execute(args)


if __name__ == '__main__':
    multiprocessing.freeze_support()
    main()
