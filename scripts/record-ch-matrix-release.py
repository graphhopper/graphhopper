"""Bind a CH follow-on artifact to its tested production bytes and separately retained evidence."""
import argparse
from datetime import datetime, timezone
import errno
import hashlib
import json
import math
from pathlib import Path
import subprocess
import zipfile


def sha256(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def java_lines(raw):
    text = raw.decode('utf-8').replace('\r\n', '\n').replace('\r', '\n')
    lines = text.split('\n')
    if lines and lines[-1] == '':
        lines.pop()
    return lines


def local_info(case):
    require(case['http_status'] == 200, 'A successful live smoke case did not return HTTP 200')
    return case['response']['info']['local_matrix']


def validate_live_smoke(smoke, artifact_sha, source_commit, config_sha):
    """Recheck captured response bodies rather than trusting a summary PASS flag."""
    require(smoke['status'] == 'PASS' and smoke['server_stopped'] is True, 'Live smoke is incomplete or server is still running')
    require(smoke['source_commit'] == source_commit, 'Smoke source commit does not match the release')
    require(smoke['artifact_sha256'] == artifact_sha, 'Smoke artifact checksum does not match the release JAR')
    require(smoke['config'] == {'path': 'docs/matrix/config-matrix.yml', 'sha256': config_sha}, 'Smoke config binding differs')
    require(all(smoke['ports_closed'].get(str(port)) is True for port in (8997, 8998)), 'Isolated smoke ports are not confirmed closed')
    cases = smoke['cases']
    required = {'foot_experimental', 'foot_flexible', 'foot_auto', 'car_experimental', 'car_flexible', 'invalid_snap', 'fail_fast', 'unsupported_request'}
    require(required <= cases.keys(), 'Live smoke is missing required cases')
    captured = dict(cases)
    if 'existing_route' in smoke:
        captured['existing_route'] = smoke['existing_route']
        require(smoke['existing_route']['http_status'] == 200 and smoke['existing_route']['response'].get('paths'), 'Captured existing route endpoint failed')
    for name, case in captured.items():
        raw_response = Path(case['raw_response_file'])
        require(sha256(raw_response) == case['raw_response_sha256'], 'Captured raw response changed: ' + name)
        require(json.loads(raw_response.read_text(encoding='utf-8')) == case['response'], 'Parsed live response differs from its raw capture: ' + name)
        if case['request'] is not None:
            raw_request = Path(case['raw_request_file'])
            require(sha256(raw_request) == case['raw_request_sha256'], 'Captured raw request changed: ' + name)
            require(json.loads(raw_request.read_text(encoding='utf-8')) == case['request'], 'Parsed live request differs from its raw capture: ' + name)
    metrics = ('times', 'times_ms', 'distances', 'weights')
    a, b, c = [7.42135, 43.730729], [7.419199, 43.727697], [7.4277, 43.739213]
    successful = ('foot_experimental', 'foot_flexible', 'foot_auto', 'car_experimental', 'car_flexible', 'invalid_snap')
    for name in successful:
        case, request = cases[name], cases[name]['request']
        info, body = local_info(case), case['response']
        profile = 'car' if name.startswith('car_') else 'foot'
        algorithm = 'auto' if name == 'foot_auto' else ('flexible' if name.endswith('_flexible') else 'ch_experimental')
        require(request['profile'] == profile and request['local']['algorithm'] == algorithm, 'Unexpected smoke request backend/profile: ' + name)
        require(request['from_points'] == ([a, [0, 0], a] if name == 'invalid_snap' else [a, b, a]), 'Unexpected smoke origin axes: ' + name)
        require(request['to_points'] == [c, a, c] and request['out_arrays'] == list(metrics) and request['fail_fast'] is False, 'Unexpected smoke destination/options: ' + name)
        require(info['profile'] == profile and info['requested_algorithm'] == algorithm and info['build_commit'] == source_commit, 'Live backend/build metadata mismatch: ' + name)
        require(info['units'] == {'times': 'seconds', 'times_ms': 'milliseconds', 'distances': 'metres', 'weights': 'profile_cost'}, 'Unexpected live metric units')
        require(info['origin_count'] == info['destination_count'] == 3 and info['threads'] == 1, 'Unexpected live grid/worker count')
        for metric in metrics:
            require(len(body[metric]) == 3 and all(len(row) == 3 for row in body[metric]), 'Malformed live output shape: ' + name)
            require(body[metric][0] == body[metric][2], 'Duplicate origin rows differ: ' + name)
            require(all(row[0] == row[2] for row in body[metric]), 'Duplicate destination columns differ: ' + name)
            require(body[metric][0][1] == body[metric][2][1] == 0, 'Same snapped point is not zero: ' + name)
        for row in range(3):
            for column in range(3):
                values = {metric: body[metric][row][column] for metric in metrics}
                if name == 'invalid_snap' and row == 1:
                    require(all(value is None for value in values.values()), 'Invalid source row is not entirely null')
                elif profile == 'car' and (row, column) == (1, 1):
                    require(all(value is None for value in values.values()), 'Expected car B-to-A disconnection is not consistently null')
                else:
                    require(all(type(value) in (int, float) and math.isfinite(value) and value >= 0 for value in values.values()), 'Invalid finite/reachable live metric: ' + name)
                    require(type(values['times']) is type(values['times_ms']) is int and values['times'] == values['times_ms'] // 1000, 'Live time units/rounding differ')
        if name in {'foot_experimental', 'invalid_snap'}:
            require(info['algorithm'] == 'ch_experimental' and info['experimental'] is True and 'fallback_reason' not in info, 'Expected experimental foot backend')
            require(info['optimality_verification'] == 'flexible_each_origin', 'Mandatory live flexible certificate is absent')
            ch = info['ch_matrix']
            origins = 1 if name == 'invalid_snap' else 2
            require(ch['backward_searches'] == 2 and ch['forward_searches'] == ch['certificate_searches'] == origins, 'Live CH/certificate search counters differ')
            require(info['searches'] == 2 + 2 * origins, 'Live total search accounting differs')
            require(info['visited_states_complete'] is True and info['visited_states'] == ch['candidate_visited_states'] + ch['certificate_visited_states'], 'Live aggregate visited-state accounting differs')
            require(ch['candidate_optimality_guaranteed'] is False and ch['corrected_cells_basis'] == 'distinct_origin_coordinate_by_destination_index', 'Live experimental scope metadata differs')
        else:
            require(info['algorithm'] == 'flexible' and 'ch_matrix' not in info, 'A live flexible request selected CH')
            if name == 'car_experimental':
                require(info['fallback_reason'] == 'TURN_COST_PROFILE_UNSUPPORTED', 'Car fallback is not explicit')
            else:
                require('fallback_reason' not in info, 'Unexpected live fallback')
        if name == 'invalid_snap':
            require(any(hint.get('details') == 'INVALID_SNAP' and hint.get('invalid_from_points') == [1] for hint in body['hints']), 'Invalid snap hint/index is absent')
        if profile == 'car':
            require(len(body['hints']) == 1 and body['hints'][0].get('details') == 'CONNECTION_NOT_FOUND'
                    and body['hints'][0].get('point_pairs') == [[1, 1]], 'Exact car fixture disconnection hint differs')
            require(all(body[metric][0][0] > 0 for metric in metrics), 'Known car cross-cell is not reachable')
    for actual, expected in (('foot_experimental', 'foot_flexible'), ('foot_auto', 'foot_flexible'), ('car_experimental', 'car_flexible')):
        for field in (*metrics, 'hints'):
            require(cases[actual]['response'][field] == cases[expected]['response'][field], 'Live certificate/fallback differs from flexible: ' + actual + '/' + field)
    failed = cases['fail_fast']
    expected_request = dict(cases['invalid_snap']['request'], fail_fast=True)
    require(failed['request'] == expected_request and failed['http_status'] == 400, 'Unexpected fail-fast request/status')
    require(any(hint.get('details') == 'INVALID_SNAP' for hint in failed['response']['hints']), 'Fail-fast did not return INVALID_SNAP')
    unsupported = cases['unsupported_request']
    require(unsupported['request'] == dict(cases['foot_experimental']['request'], headings=[0, 0, 0])
            and unsupported['http_status'] == 400, 'Unexpected unsupported-option request/status')
    require(any(hint.get('details') == 'INVALID_REQUEST' for hint in unsupported['response']['hints']), 'Unsupported option did not return INVALID_REQUEST')
    require(not any(metric in error['response'] for error in (failed, unsupported) for metric in metrics), 'Live error returned a partial matrix')
    for port in ('8997', '8998'):
        attempts = smoke['server_stop_confirmation']['port_checks'][port]
        require(attempts and all(row['connection_refused'] is True and row['connect_ex'] in (errno.ECONNREFUSED, 10061) for row in attempts), 'Stopped-port evidence lacks actual connection refusal: ' + port)
    return {'captured_cases_checked': sorted(required), 'public_metric_equality': 'exact_arrays_and_hints',
            'experimental_selection': True, 'auto_stays_flexible': True, 'car_fallback': 'TURN_COST_PROFILE_UNSUPPORTED',
            'duplicate_axes_and_same_point_zero': True, 'invalid_snap_nulls_and_fail_fast': True,
            'source_commit_in_all_successful_matrix_responses': source_commit}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-commit', required=True)
    parser.add_argument('--artifact', default='web/target/graphhopper-web-11.0-SNAPSHOT.jar')
    parser.add_argument('--verification', default='docs/matrix/ch-matrix-full-verification.json')
    parser.add_argument('--analysis', default='docs/matrix/ch-matrix-benchmark-analysis.json')
    parser.add_argument('--live-smoke', default='docs/matrix/ch-matrix-live-smoke-final.json')
    parser.add_argument('--output', default='docs/matrix/ch-matrix-release.json')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = root / args.output
    require(not output.exists(), 'Preserve prior release records; select a new --output')
    git = lambda *argv: subprocess.check_output(['git', *argv], cwd=root, text=True).strip()
    source_commit = git('rev-parse', args.source_commit)
    require(subprocess.run(['git', 'merge-base', '--is-ancestor', source_commit, 'HEAD'], cwd=root).returncode == 0, 'Source commit is not an ancestor of the current checkout')
    assert git('remote', 'get-url', '--push', 'origin') == 'https://github.com/hulsiejames/graphhopper.git'
    assert git('remote', 'get-url', '--push', 'upstream') == 'DISABLED'
    source_paths = ['core/src/main/java/com/graphhopper/routing/matrix',
                    'web-api/src/main/java/com/graphhopper/matrix',
                    'web-bundle/src/main/java/com/graphhopper/resources/MatrixResource.java',
                    'web-bundle/src/main/java/com/graphhopper/http/GraphHopperBundle.java',
                    'pom.xml', 'run-matrix.cmd', 'docs/matrix/config-matrix.yml', 'docs/matrix/config-manchester.yml',
                    'core/src/main/resources/com/graphhopper/custom_models/foot.json',
                    'core/src/main/resources/com/graphhopper/custom_models/car.json']
    # Guard every Java module's production/build/test inputs, including untracked
    # files. git diff alone does not see a newly added, uncommitted source file.
    bound_paths = ['pom.xml', 'run-matrix.cmd', 'docs/matrix/config-matrix.yml', 'docs/matrix/config-manchester.yml']
    for module in sorted(root.iterdir()):
        if module.is_dir() and (module / 'pom.xml').is_file():
            bound_paths.append(module.name + '/pom.xml')
            for name in ('src/main', 'src/test'):
                if (module / name).is_dir():
                    bound_paths.append(module.name + '/' + name)
    require(not git('diff', '--name-only', source_commit, '--', *bound_paths), 'Production/build/test inputs changed after the source commit')
    require(not git('ls-files', '--others', '--', *bound_paths), 'Untracked production/build/test inputs are not part of the source commit')
    verification_path = root / args.verification
    verification = json.loads(verification_path.read_text(encoding='utf-8'))
    assert verification['status'] == 'PASS_FULL_MAVEN_VERIFY'
    assert verification['totals']['errors'] == verification['totals']['failures'] == 0
    require(verification['head_commit'] == source_commit, 'Final full verification was not run at the release source commit')
    require(verification['command'] and 'verify' in verification['command'], 'Final verification command is missing')
    require(verification['totals']['passed'] >= 3000, 'Incomplete final full reactor verification')
    for name, count in {'com.graphhopper.routing.matrix.BucketCHMatrixTest': 8,
                        'com.graphhopper.routing.matrix.MatrixCHIntegrationTest': 5,
                        'com.graphhopper.application.resources.MatrixResourceTest': 69,
                        'com.graphhopper.routing.matrix.CHMatrixPairwiseAuditTest': 4}.items():
        rows = [suite for suite in verification['suites'] if suite['name'] == name]
        require(len(rows) == 1 and rows[0]['tests'] >= count and not any(rows[0][key] for key in ('failures', 'errors', 'skipped')), 'Required final suite did not execute completely: ' + name)
    for suite in verification['suites']:
        require(sha256(root / suite['report']) == suite['sha256'], 'A later run replaced final verification XML: ' + suite['report'])
    require(sha256(root / verification['log']) == verification['log_sha256'], 'Final verification log changed')
    analysis_path = root / args.analysis
    analysis = json.loads(analysis_path.read_text(encoding='utf-8'))
    require(analysis['status'] == 'MEASURED_WITH_SCOPED_BASELINE_ACCURACY_DIFFERENCES', 'Analyzer must preserve the known baseline accuracy differences')
    require(analysis['ordinary_ch_original_rational_optimality_claimed'] is False, 'Ordinary CH exact-rational optimality must not be claimed')
    experiments = {item['dataset']: item for item in analysis['experiments']}
    require(set(experiments) == {'monaco', 'manchester'} and len(analysis['experiments']) == 2, 'Expected exactly two benchmark datasets')
    require(len(analysis['summaries']) == 48 and len(analysis['ratios']) == 16, 'Incomplete measured comparison grid')
    review_path = root / 'docs/matrix/ch-matrix-review.json'
    review = json.loads(review_path.read_text(encoding='utf-8'))
    smoke_path = root / args.live_smoke
    smoke = json.loads(smoke_path.read_text(encoding='utf-8'))
    require(smoke['script_sha256'] == sha256(root / 'scripts/smoke-ch-matrix-release.py'), 'Smoke was not recorded with the current evidence helper')
    artifact = root / args.artifact
    artifact_sha = sha256(artifact)
    smoke_checks = validate_live_smoke(smoke, artifact_sha, source_commit, sha256(root / 'docs/matrix/config-matrix.yml'))
    patterns = {'core': ['com/graphhopper/routing/matrix/*.class'],
                'web-api': ['com/graphhopper/matrix/*.class'],
                'web-bundle': ['com/graphhopper/http/GraphHopperBundle*.class',
                               'com/graphhopper/resources/MatrixResource*.class']}
    classes, models, git_properties = [], [], {}
    with zipfile.ZipFile(artifact) as archive:
        assert archive.testzip() is None, 'JAR CRC failed'
        resources = {name: archive.read('com/graphhopper/' + name) for name in ('gitinfo', 'version', 'builddate')}
        git_lines = java_lines(resources['gitinfo'])
        require(len(git_lines) == 6 and git_lines[0].startswith('#'), 'GraphHopper gitinfo must contain the comment and five positional fields')
        require(git_lines[1] == source_commit and git_lines[5] == 'false', 'JAR GraphHopper gitinfo must bind the clean complete release source commit')
        version_lines, date_lines = java_lines(resources['version']), java_lines(resources['builddate'])
        require(version_lines and date_lines and version_lines[0] and date_lines[0]
                and not version_lines[0].startswith('$') and not date_lines[0].startswith('$'), 'Unfiltered GraphHopper version/builddate resources')
        graphhopper_identity = {'entry': 'com/graphhopper/gitinfo', 'commit': git_lines[1], 'commit_time': git_lines[2],
                               'commit_message': git_lines[3], 'branch': git_lines[4], 'dirty': git_lines[5],
                               'version': version_lines[0], 'builddate': date_lines[0],
                               'resource_sha256': {'com/graphhopper/' + name: hashlib.sha256(raw).hexdigest() for name, raw in resources.items()}}
        for module, globs in patterns.items():
            base = root / module / 'target/classes'
            for glob in globs:
                matches = sorted(base.glob(glob))
                assert matches, (module, glob)
                for path in matches:
                    entry = path.relative_to(base).as_posix()
                    current, packaged = path.read_bytes(), archive.read(entry)
                    assert current == packaged, entry
                    classes.append({'module': module, 'entry': entry,
                                    'sha256': hashlib.sha256(current).hexdigest()})
        for model in ('foot.json', 'car.json'):
            entry = 'com/graphhopper/custom_models/' + model
            source = root / 'core/src/main/resources' / entry
            compiled = root / 'core/target/classes' / entry
            assert source.read_bytes() == compiled.read_bytes() == archive.read(entry), entry
            models.append({'entry': entry, 'sha256': sha256(source)})
        for name in archive.namelist():
            if name == 'git.properties' or name.endswith('/git.properties'):
                values = {}
                for line in archive.read(name).decode('utf-8').splitlines():
                    if '=' not in line or line.startswith('#'):
                        continue
                    key, value = line.split('=', 1)
                    if key.startswith('git.commit.id') or key in {'git.dirty', 'git.branch', 'git.build.time', 'git.remote.origin.url'}:
                        values[key] = value
                git_properties[name] = values
    require(smoke['artifact']['graphhopper_gitinfo']['commit'] == graphhopper_identity['commit']
            and smoke['artifact']['graphhopper_gitinfo']['dirty'] == graphhopper_identity['dirty']
            and smoke['artifact']['version'] == graphhopper_identity['version']
            and smoke['artifact']['builddate'] == graphhopper_identity['builddate']
            and smoke['artifact']['resource_sha256'] == graphhopper_identity['resource_sha256'], 'Captured smoke JAR resource identity differs from final archive')
    benchmark_bindings = []
    for dataset in ('monaco', 'manchester'):
        directory = root / ('docs/matrix/ch-matrix-benchmark-' + dataset)
        metadata_path = directory / 'metadata.json'
        metadata = json.loads(metadata_path.read_text(encoding='utf-8'))
        experiment = experiments[dataset]
        require(sha256(metadata_path) == experiment['metadata_sha256'], 'Analyzer does not bind the current dataset metadata: ' + dataset)
        warmups, repeats, samples = (5, 20, 480) if dataset == 'monaco' else (2, 5, 120)
        require((metadata['warmups'], metadata['repeats'], metadata['completed_measured_samples']) == (warmups, repeats, samples), 'Unexpected benchmark repetition grid: ' + dataset)
        require(experiment['samples'] == samples and experiment['original_experiment_status'] == metadata['status'], 'Original benchmark status/count was not preserved')
        approval = experiment['case_bound_posthoc_approval']
        if approval is not None:
            require(approval in review['approved_posthoc_reconciliations'], 'Exact case-bound posthoc evidence was not reviewed')
            for key, name in {'posthoc_review_sha256': 'pairwise-ch-posthoc-review.json', 'metadata_sha256': 'metadata.json',
                              'reconciliation_sha256': 'pairwise-ch-reconciliation.json', 'witnesses_sha256': 'pairwise-ch-witnesses.jsonl.gz',
                              'raw_candidate_reconciliation_sha256': 'raw-candidate-reconciliation.json'}.items():
                if key in approval:
                    require(sha256(directory / name) == approval[key], 'Posthoc case-bound evidence changed: ' + name)
        else:
            require(metadata['pairwise_ch_unexplained_unique_case_proofs'] == 0, 'Unapproved original benchmark proof failures remain')
        for name, digest in metadata['evidence_sha256'].items():
            require(sha256(directory / name) == digest, 'Original benchmark evidence changed: ' + dataset + '/' + name)
        candidate_path = directory / 'candidate-path-reconciliation.json'
        candidate_witnesses = directory / 'candidate-path-witnesses.jsonl.gz'
        raw_rejection = directory / 'raw-candidate-reconciliation.json'
        candidate_approval = {'dataset': dataset, 'reconciliation_sha256': sha256(candidate_path),
                              'witnesses_sha256': sha256(candidate_witnesses),
                              'raw_candidate_reconciliation_sha256': sha256(raw_rejection)}
        require(candidate_approval in review['approved_candidate_path_reconciliations'], 'Fresh actual candidate replay evidence is not bound by root review: ' + dataset)
        candidate = json.loads(candidate_path.read_text(encoding='utf-8'))
        require(candidate['status'] == 'FRESH_ACTUAL_CANDIDATE_PATHS_PROVEN'
                and candidate['production_algorithm_changed'] is False and candidate['original_reports_modified'] is False
                and candidate['unexplained_unique_case_proofs'] == 0, 'Actual candidate replay is incomplete or changed production/evidence')
        require(candidate['actual_indexed_raw_flags'] == candidate['expected_indexed_raw_flags'] == len(candidate['indexed_raw_flags'])
                and candidate['actual_unique_case_proofs'] == len(candidate['unique_case_proofs'])
                and all(row['proved'] is True for row in candidate['unique_case_proofs']), 'Incomplete actual candidate replay coverage')
        for name, digest in candidate['original_evidence_sha256'].items():
            require(sha256(directory / name) == digest, 'Candidate replay original evidence binding changed: ' + name)
        require(candidate['unchanged_measured_source_sha256'] == metadata['source_sha256']
                and candidate['unchanged_measured_class_sha256'] == metadata['compiled_class_sha256'], 'Candidate replay used different measured source/class manifests')
        for name, digest in candidate['new_diagnostic_source_sha256'].items():
            require(Path(name).name == name and sha256(root / 'core/src/test/java/com/graphhopper/routing/matrix' / name) == digest, 'New candidate diagnostic source changed: ' + name)
        for name, digest in candidate['new_diagnostic_class_sha256'].items():
            require(Path(name).name == name and sha256(root / 'core/target/test-classes/com/graphhopper/routing/matrix' / name) == digest, 'New candidate diagnostic class changed: ' + name)
        assert metadata['experimental_vs_flexible_exact_metric_equality'] is True
        assert metadata['cache_file_bytes_unchanged_after_close'] is True
        required_classes = {'core/target/classes/com/graphhopper/routing/matrix/' + name + '.class'
                            for name in ('BucketCHMatrix', 'MatrixEngine', 'TargetSetDijkstra', 'MatrixProvenance')}
        require(required_classes <= metadata['compiled_class_sha256'].keys(), 'Benchmark production class coverage is incomplete')
        for name in ('CHWeightAudit', 'CHMatrixPairwiseAudit', 'MatrixPrecisionDiagnostics', 'MatrixCHMatrixBenchmarkTest'):
            require('core/target/test-classes/com/graphhopper/routing/matrix/' + name + '.class' in metadata['compiled_class_sha256'], 'Benchmark helper class binding is absent: ' + name)
        required_sources = {'core/src/main/java/com/graphhopper/routing/matrix/' + name + '.java'
                            for name in ('BucketCHMatrix', 'MatrixEngine', 'TargetSetDijkstra', 'MatrixProvenance')}
        required_sources |= {'core/src/test/java/com/graphhopper/routing/matrix/' + name + '.java'
                             for name in ('CHWeightAudit', 'CHMatrixPairwiseAudit', 'MatrixPrecisionDiagnostics', 'MatrixCHMatrixBenchmarkTest')}
        require(required_sources <= metadata['source_sha256'].keys(), 'Benchmark source/helper hash coverage is incomplete')
        production_count = 0
        for relative, digest in metadata['compiled_class_sha256'].items():
            require(sha256(root / relative) == digest, 'Measured compiled class bytes changed: ' + relative)
            if '/target/classes/' not in relative:
                continue
            production_count += 1
        assert production_count > 20
        for relative, digest in metadata['source_sha256'].items():
            assert sha256(root / relative) == digest, relative
        # MatrixProvenance intentionally includes build_commit in graph/profile
        # fingerprints. The measured dirty base build and final clean source
        # commit therefore have different identities despite unchanged storage.
        # Bind actual cache bytes instead of rewriting or equating fingerprints.
        cache = root / ('target/matrix-' + dataset + '-server')
        manifest = metadata['cache_file_manifest']
        require(len({row['file'] for row in manifest}) == len(manifest), 'Duplicate prepared-cache manifest files')
        require({row['file'] for row in manifest} == {path.name for path in cache.iterdir() if path.is_file() and path.name != 'gh.lock'}, 'Prepared cache manifest coverage differs: ' + dataset)
        for row in manifest:
            require(Path(row['file']).name == row['file'], 'Nonlocal prepared-cache manifest path')
            path = cache / row['file']
            require(path.stat().st_size == row['bytes'] and sha256(path) == row['sha256'], 'Prepared cache bytes changed since measurement: ' + dataset + '/' + row['file'])
        benchmark_bindings.append({'dataset': dataset, 'metadata_sha256': sha256(metadata_path),
                                   'measured_samples': metadata['completed_measured_samples'],
                                   'benchmark_build_commit': metadata['build_commit'],
                                   'current_compiled_production_classes_matching_benchmark': production_count,
                                   'cache_bytes_unchanged': True,
                                   'original_experiment_status': metadata['status'], 'case_bound_posthoc_approval': approval,
                                   'fresh_actual_candidate_path_approval': candidate_approval,
                                   'actual_candidate_indexed_flags': candidate['actual_indexed_raw_flags'],
                                   'actual_candidate_unique_path_proofs': candidate['actual_unique_case_proofs']})
    source_files = []
    for relative in source_paths:
        path = root / relative
        source_files.extend(sorted(path.rglob('*.java')) if path.is_dir() else [path])
    evidence_names = [args.verification, args.analysis, args.live_smoke, 'docs/matrix/ch-matrix-review.json',
                      'docs/matrix/ch-matrix-benchmark-report.md', 'docs/matrix/ch-matrix-benchmark-results.csv',
                      'docs/matrix/ch-matrix-validation.md', 'docs/matrix/ch-matrix-design.md',
                      'docs/matrix/ch-matrix-fixture-validation-final.json',
                      'docs/matrix/ch-matrix-precision-diagnostics-final.json',
                      'docs/matrix/ch-matrix-packaging-rejection.json',
                      'docs/matrix/ch-matrix-client-validation.json', 'docs/matrix/ch-matrix-client-smoke.json',
                      'docs/matrix/ch-matrix-client-flexible-smoke.json', 'docs/matrix/ch-matrix-client-smoke.csv',
                      'scripts/smoke-ch-matrix-release.py', 'scripts/record-ch-matrix-release.py',
                      'scripts/snapshot-matrix-verification.py']
    raw_cases = list(smoke['cases'].values()) + ([smoke['existing_route']] if 'existing_route' in smoke else [])
    for case in raw_cases:
        for key in ('raw_request_file', 'raw_response_file'):
            if key in case:
                evidence_names.append(Path(case[key]).resolve().relative_to(root).as_posix())
    evidence_names = sorted(set(evidence_names))
    evidence_commit = git('rev-parse', 'HEAD')
    require(not git('diff', '--name-only', evidence_commit, '--', *evidence_names), 'Finalize and commit the evidence inputs before recording release provenance')
    require(not git('ls-files', '--others', '--', *evidence_names), 'Evidence inputs are not all committed at the recording HEAD')
    evidence = [{'path': name, 'sha256': sha256(root / name), 'bytes': (root / name).stat().st_size}
                for name in evidence_names]
    sidecar = artifact.with_suffix(artifact.suffix + '.sha256')
    sidecar.write_text(artifact_sha + '  ' + artifact.name + '\n', encoding='ascii')
    record = {'status': 'PASS_WITH_SCOPED_LIMITATIONS', 'created_utc': datetime.now(timezone.utc).isoformat(),
              'repository': 'https://github.com/hulsiejames/graphhopper', 'pr_base': 'matrix-base-11.1',
              'upstream_base_commit': 'ebb578f4e94db72e82f2f8d3bc69417758e16e1c',
              'source_commit': source_commit, 'head_at_recording': evidence_commit,
              'evidence_input_commit': evidence_commit, 'evidence_inputs_equal_commit': True,
              'evidence_identity_note': 'The evidence commit binds current Python tools, final documents and captured request/response bytes separately from the clean compiled-artifact source commit. The release record itself is written afterward.',
              'worktree_status': git('status', '--short'), 'artifact': args.artifact,
              'artifact_size_bytes': artifact.stat().st_size, 'artifact_sha256': artifact_sha,
              'artifact_crc': 'PASS', 'graphhopper_artifact_identity': graphhopper_identity,
              'ancillary_dependency_git_properties': git_properties,
              'analyzer_status': analysis['status'], 'ordinary_ch_original_rational_optimality_claimed': False,
              'final_verification_source_commit': verification['head_commit'], 'live_smoke_checks': smoke_checks,
              'all_tracked_production_build_test_inputs_equal_source_commit': True,
              'final_live_graph_fingerprint': smoke['graph_fingerprint'],
              'final_live_profile_fingerprints': smoke['profile_fingerprints'],
              'fingerprint_scope': 'Graph/profile fingerprints include build identity. Historical measured fingerprints remain unchanged; actual prepared-cache file SHA manifests, matching production classes and configured models bind the release storage/code instead.',
              'production_classes_checked': len(classes), 'production_classes': classes, 'configured_models': models,
              'source_sha256': {path.relative_to(root).as_posix(): sha256(path) for path in source_files},
              'benchmark_binary_bindings': benchmark_bindings, 'evidence': evidence,
              'build_identity_note': 'Measurements precede the release source commit. Their original build identities remain recorded. Production class bytes and source hashes are checked against the final artifact; no measured fingerprint is rewritten.',
              'historical_core_release': 'docs/matrix/provenance.json (a separate earlier artifact, not this JAR)',
              'scope': ['Experimental node-based fixed-profile CH candidates always receive original-weight flexible certification/correction.',
                        'auto remains flexible; turn-cost profiles report explicit flexible fallback.',
                        'Known baseline objective differences remain separate case-bound evidence, not exact-original-optimality claims.',
                        'No national-scale or concurrent-throughput claim.']}
    with output.open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(record, indent=2) + '\n')
    print(json.dumps({'status': record['status'], 'artifact_sha256': artifact_sha,
                      'classes': len(classes), 'models': len(models), 'output': str(output)}))


if __name__ == '__main__':
    main()
