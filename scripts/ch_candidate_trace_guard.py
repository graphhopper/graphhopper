"""Validate exact, explicitly reviewed fresh bucket-CH candidate path evidence.

Arithmetic and full-index checks are adapted from the completed independent root
review. Original measurements and failure statuses are never modified.
"""
from collections import Counter
from fractions import Fraction as Q
import gzip
import hashlib
import json
import math
from pathlib import Path

sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
load = lambda p: json.loads(p.read_text())
triple = lambda body, i, j: {k: body[k][i][j] for k in ('weights', 'times_ms', 'distances')}
plus = lambda a, b: {k: a[k] + b[k] for k in a}
zero = {'weights': 0.0, 'distances': 0.0, 'times_ms': 0}

def path_check(path, source, target):
    node, weight, distance, millis = source, 0.0, 0.0, 0
    exact_weight, exact_distance, physical = Q(), Q(), []
    for edge in path['edge_contributions']:
        assert edge['from_node'] == node
        assert edge['turn_weight'] == edge['turn_time_ms'] == 0
        assert edge['encoded_flags_equal_original'] is True
        family = edge['upstream_copy_family_directed_keys']
        assert edge['original_edge_key'] in family and edge['copy_canonical_edge_key'] == min(family)
        assert all(math.isfinite(edge[k]) and edge[k] >= 0 for k in ('edge_weight', 'distance_m', 'edge_time_ms'))
        weight += edge['edge_weight']; distance += edge['distance_m']; millis += edge['edge_time_ms']
        exact_weight += Q(edge['edge_weight']); exact_distance += Q(edge['distance_m'])
        if not physical or physical[-1] != edge['copy_canonical_edge_key']:
            physical.append(edge['copy_canonical_edge_key'])
        node = edge['to_node']
    assert node == target and path['legal_and_requested_endpoints'] is True
    assert path['metrics'] == {'weights': weight, 'times_ms': millis, 'distances': distance}
    assert Q(path['exact_objective_terms_sum']) == exact_weight
    assert Q(path['exact_distance_terms_sum']) == exact_distance
    assert physical == path['copy_canonical_directed_edge_keys']
    return exact_weight, exact_distance

def inspect_candidate_paths(directory, repo):
    """Validate every fresh replay path and indexed rejection; require exact root approval."""
    directory, repo = Path(directory), Path(repo)
    dataset = load(directory / 'metadata.json')['dataset']
    approvals, results = [], []
    report_path, witnesses_path = directory / 'candidate-path-reconciliation.json', directory / 'candidate-path-witnesses.jsonl.gz'
    report, metadata = load(report_path), load(directory / 'metadata.json')
    raw, outcomes = load(directory / 'raw-candidates.json'), load(directory / 'first-outcomes.json')
    requests = {r['workload']: r['request'] for r in load(directory / 'requests.json')['workloads'] if r['backend'] == 'flexible'}
    assert report['status'] == 'FRESH_ACTUAL_CANDIDATE_PATHS_PROVEN'
    assert report['witnesses_sha256'] == sha(witnesses_path)
    for name, digest in report['original_evidence_sha256'].items(): assert sha(directory / name) == digest
    for field in ('unchanged_measured_source_sha256', 'unchanged_measured_class_sha256'):
        for name, digest in report[field].items(): assert sha(repo / name) == digest
    summaries = {s['proof_id']: s for s in report['unique_case_proofs']}
    witnesses, classifications, splits_count = {}, Counter(), 0
    with gzip.open(witnesses_path, 'rb') as stream:
        for line in stream:
            w = json.loads(line); pid = w['proof_id']; assert pid not in witnesses
            assert hashlib.sha256(line.rstrip(b'\r\n')).hexdigest() == summaries[pid]['witness_line_sha256']
            assert w['proved'] is True and not w['failures'] and w['actual_candidate_path_captured'] is True
            assert w['candidate_original_objective_optimality_claimed'] is False
            source, target = w['source_node'], w['target_node']
            actual, represented, oracle = (w[k] for k in ('actual_original_candidate_path', 'same_candidate_path_represented_on_complete_overlay', 'independent_shared_dijkstra_path'))
            a, r, o = (path_check(p, source, target) for p in (actual, represented, oracle))
            assert all(e['present_in_shared_overlay_explorer'] for p in (represented, oracle) for e in p['edge_contributions'])
            assert w['observed_raw'] == w['observed_raw_candidate'] == w['raw_join_metrics']
            assert plus(w['forward_meeting_metrics'], w['backward_meeting_metrics']) == w['raw_join_metrics']
            arcs = w['selected_ch_arcs']
            for arc in arcs: assert plus(arc['parent_metrics'], arc['cached_actual_arc_metrics']) == arc['current_metrics']
            forward, backward = [a for a in arcs if not a['reverse_search']], [a for a in arcs if a['reverse_search']]
            node, metrics = source, zero
            for arc in forward:
                assert arc['parent_node'] == node and arc['parent_metrics'] == metrics
                node, metrics = arc['adjacent'], arc['current_metrics']
            assert node == w['meeting_node'] and metrics == w['forward_meeting_metrics']
            node, metrics = w['meeting_node'], w['backward_meeting_metrics']
            for arc in backward:
                assert arc['adjacent'] == node and arc['current_metrics'] == metrics
                node, metrics = arc['parent_node'], arc['parent_metrics']
            assert node == target and metrics == zero
            normalized, split_iter = [], iter(w['actual_removed_original_leaf_split_deltas'])
            for edge in actual['edge_contributions']:
                if edge['present_in_shared_overlay_explorer']:
                    normalized.append(edge); continue
                split = next(split_iter); assert split['original_leaf'] == edge
                pieces = split['shared_overlay_pieces']; current = edge['from_node']; coord = edge['from_coordinate']
                for piece in pieces:
                    assert piece['from_node'] == current and piece['from_coordinate'] == coord
                    for key in ('encoded_flags', 'normalized_distance_one_metrics', 'copy_canonical_edge_key', 'upstream_copy_family_directed_keys'):
                        assert piece[key] == edge[key]
                    assert piece['virtual'] is True and piece['present_in_shared_overlay_explorer'] is True
                    current, coord = piece['to_node'], piece['to_coordinate']
                assert current == edge['to_node'] and coord == edge['to_coordinate']
                assert Q(split['exact_original_minus_shared_weight']) == Q(edge['edge_weight']) - sum((Q(p['edge_weight']) for p in pieces), Q())
                assert Q(split['exact_original_minus_shared_distance_m']) == Q(edge['distance_m']) - sum((Q(p['distance_m']) for p in pieces), Q())
                assert split['original_minus_shared_time_ms'] == edge['edge_time_ms'] - sum(p['edge_time_ms'] for p in pieces)
                assert split['same_actual_upstream_copy_family_flags_and_unit_metrics'] is True
                normalized.extend(pieces); splits_count += 1
            assert list(split_iter) == [] and normalized == represented['edge_contributions']
            assert w['observed_raw']['times_ms'] == actual['metrics']['times_ms']
            for key, exact_sum in [('raw_weight_regrouping', a[0]), ('raw_distance_regrouping', a[1])]:
                check = w[key]; operations = max(1, 2 * len(actual['edge_contributions']) + 1)
                assert check['addition_operation_bound'] == operations and Q(check['unit_roundoff']) == Q(1, 2**53)
                assert Q(check['exact_selected_terms_sum']) == exact_sum
                residual = Q(check['reported']) - exact_sum
                assert Q(check['exact_reported_minus_terms']) == residual
                assert Q(check['derived_absolute_bound']) >= Q(operations, 2**53 - operations) * exact_sum
                assert abs(residual) <= Q(check['derived_absolute_bound'])
            assert Q(w['exact_original_candidate_minus_represented_objective']) == a[0] - r[0]
            assert Q(w['exact_represented_candidate_minus_oracle_objective']) == r[0] - o[0]
            assert w['returned_certified'] == w['returned_flexible']
            if dataset == 'manchester':
                assert r == o and represented['metrics']['times_ms'] == oracle['metrics']['times_ms']
                assert represented['copy_canonical_directed_edge_keys'] == oracle['copy_canonical_directed_edge_keys']
                assert w['classification'] == 'ACTUAL_CANDIDATE_SAME_PHYSICAL_ROUTE_WITH_EXPLICIT_QUERY_SPLIT_METRICS'
            else:
                assert r[0] - o[0] == Q(15, 2**51)
                assert w['classification'] == 'ACTUAL_CANDIDATE_DIFFERENT_PATH_OR_OBJECTIVE_REJECTED_BY_CERTIFICATE'
            classifications[w['classification']] += 1; witnesses[pid] = w
    assert witnesses.keys() == summaries.keys()
    flags = {(name, e['origin_index'], e['destination_index']): e for name, obj in raw.items() for e in obj['raw_candidate_vs_independent_oracle']['all_mismatches']}
    indexed = {(e['workload'], e['origin_index'], e['destination_index']): e for e in report['indexed_raw_flags']}
    assert len(indexed) == len(report['indexed_raw_flags']) == len(flags) == report['actual_indexed_raw_flags']
    assert indexed.keys() == flags.keys()
    for (name, i, j), case in indexed.items():
        w = witnesses[case['proof_id']]
        assert case['from'] == requests[name]['from_points'][i] and case['to'] == requests[name]['to_points'][j]
        assert case['source_node'] == w['source_node'] and case['target_node'] == w['target_node']
        assert triple(raw[name]['candidate_metrics'], i, j) == case['observed_raw_candidate'] == w['observed_raw_candidate']
        assert triple(raw[name]['independent_plain_dijkstra_metrics'], i, j) == w['recorded_independent_oracle']
        assert triple(outcomes[name][metadata['experimental_algorithm']], i, j) == w['returned_certified']
        assert triple(outcomes[name]['flexible'], i, j) == w['returned_flexible']
    ledger_path = directory / 'raw-candidate-reconciliation.json'
    ledger = load(ledger_path)
    assert ledger['raw_flagged_cells'] == len(flags) and ledger['remaining_unhandled_raw_flags'] == 0
    for name, digest in ledger['original_evidence_sha256'].items(): assert sha(directory / name) == digest
    if dataset == 'manchester':
        ledger_cases = {(e['workload'], e['origin_index'], e['destination_index']): e for e in ledger['rejected_indexed_cases']}
        assert len(ledger_cases) == len(ledger['rejected_indexed_cases']) == len(flags) and ledger_cases.keys() == flags.keys()
        for (name, i, j), case in ledger_cases.items():
            w = witnesses[case['fresh_candidate_proof_id']]
            assert case['fresh_candidate_witness_line_sha256'] == summaries[w['proof_id']]['witness_line_sha256']
            assert case['fresh_candidate_proof_id'] == indexed[name, i, j]['proof_id']
            assert case['from'] == requests[name]['from_points'][i] and case['to'] == requests[name]['to_points'][j]
            assert case['raw_candidate'] == triple(raw[name]['candidate_metrics'], i, j) == w['observed_raw_candidate']
            assert case['independent_plain_dijkstra'] == triple(raw[name]['independent_plain_dijkstra_metrics'], i, j)
            assert case['returned_certified'] == triple(outcomes[name][metadata['experimental_algorithm']], i, j) == case['returned_flexible']
            assert case['returned_flexible'] == triple(outcomes[name]['flexible'], i, j) and case['raw_candidate'] != case['returned_certified']
            assert case['original_raw_issues'] == flags[name, i, j]['issues']
            assert case['original_raw_candidate_path_captured'] is False and case['fresh_replay_actual_candidate_path_captured'] is True
            assert case['classification'] == 'REJECTED_BY_MANDATORY_FLEXIBLE_CERTIFICATE'
            assert case['fresh_candidate_classification'] == w['classification']
            assert case['same_physical_route_after_explicit_query_split_accounting'] is True and case['represented_candidate_exact_all_metrics_equal_oracle'] is True
            assert case['returned_certified_exactly_matches_flexible'] is True and case['returned_certified_matches_independent_oracle_fixed_thresholds'] is True
            actual, expected = case['returned_certified'], case['independent_plain_dijkstra']
            assert abs(actual['weights'] - expected['weights']) <= 1e-7 + abs(expected['weights']) * 1e-10
            assert abs(actual['distances'] - expected['distances']) <= .002 and abs(actual['times_ms'] - expected['times_ms']) <= 1
    approvals.append({'dataset': dataset, 'reconciliation_sha256': sha(report_path), 'witnesses_sha256': sha(witnesses_path),
                      'raw_candidate_reconciliation_sha256': sha(ledger_path)})
    results.append({'dataset': dataset, 'indexed_flags': len(flags), 'unique_proofs': len(witnesses), 'split_leaf_occurrences': splits_count, 'classifications': dict(classifications)})
    approval = approvals[0]
    review = load(repo / 'docs/matrix/ch-matrix-review.json')
    assert approval in review['approved_candidate_path_reconciliations'], 'Fresh candidate replay is not explicitly approved at these exact hashes'
    assert report['production_algorithm_changed'] is False and report['original_reports_modified'] is False
    assert report['unchanged_measured_source_sha256'] == metadata['source_sha256']
    assert report['unchanged_measured_class_sha256'] == metadata['compiled_class_sha256']
    expected_sources = {'MatrixCHCandidateReplayTest.java', 'BucketCHCandidateTrace.java'}
    assert report['new_diagnostic_source_sha256'].keys() == expected_sources
    for name, digest in report['new_diagnostic_source_sha256'].items():
        assert sha(repo / 'core/src/test/java/com/graphhopper/routing/matrix' / name) == digest
    expected_classes = {path.name for path in (repo / 'core/target/test-classes/com/graphhopper/routing/matrix').glob('*.class')
                        if path.name.startswith(('MatrixCHCandidateReplayTest', 'BucketCHCandidateTrace'))}
    assert report['new_diagnostic_class_sha256'].keys() == expected_classes
    for name, digest in report['new_diagnostic_class_sha256'].items():
        assert sha(repo / 'core/target/test-classes/com/graphhopper/routing/matrix' / name) == digest
    return {**results[0], 'approval': approval, 'raw_rejection': ledger}
