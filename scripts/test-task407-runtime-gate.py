"""Host-only gate protocol tests. All native I/O is injected; raw results stay RAM-only."""
import ast
import contextlib
import base64
import copy
import dataclasses
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import threading
import time
from types import SimpleNamespace
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]


def load(name, filename):
    path = ROOT / 'scripts' / filename
    if not path.exists():
        return SimpleNamespace()
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


s = load('gate_test_supervisor', 'task407-runtime-supervisor.py')
r = load('gate_test_runner', 'task407-runtime-runner.py')
g = load('gate_test_transport', 'task407-runtime-gate.py')


def stream(data=b'', *, count=None, buffered=0, dropped=0, error=False,
           eof=False, reader_complete=False):
    return dict(base64=base64.b64encode(data).decode('ascii'), returned_bytes=len(data),
                bytes=len(data) if count is None else count, dropped_bytes=dropped,
                buffered_bytes=buffered, read_error=error, truncated=bool(dropped), storage='bounded_memory',
                eof=eof, reader_complete=reader_complete)


def frame(state='complete', data=b'', err=b'', *, code=0, reason='completed'):
    out, error = (stream(value, eof=state == 'complete', reader_complete=state == 'complete')
                  for value in (data, err))
    return dict(state=state, process=dict(pid=123, created_ticks=456), kind='adb',
                exit_verified=state != 'running', exit_code=code if state != 'running' else None,
                reason=reason if state != 'running' else None, error=None,
                guest_completion='not_inferred', timeout_seconds=15, timeout_reason='Original command budget',
                streams={name: {k: v for k, v in row.items() if k in (
                    'bytes', 'dropped_bytes', 'buffered_bytes', 'read_error', 'eof', 'reader_complete')}
                    for name, row in (('stdout', out), ('stderr', error))},
                stdout=out, stderr=error, replay=False)


def record():
    return dict(kind='task407-gated-emulator-runtime-session-v1', serial='emulator-5554',
        avd='NavicReaderLab', scope='original-three-with-independent-supervision',
        user_session_authorized=True, no_snapshot_save=True, preserve_app_data=True, clear_logcat=False,
        watchdog_id='host-fixture', apk_path='fixture-only.apk',
        gate=dict(helper='C:/Users/darka/.claude/skills/android-emulator-gate/scripts/invoke_native_gate.py',
            release='df52528334e1391b', version='0.16.4', emulator_profile='reader-diagnostics',
            component_version='37.2.12', scratch_root='D:/Temp/navic-task407-gated-runtime/r146',
            owner_task='Validate original reader runtime cases', thread_name='Navic',
            expected_owner=dict(system='claude', session_sha256=hashlib.sha256(b'host-chat').hexdigest(), thread_name='Navic'),
            binding=dict(avd='NavicReaderLab', serial='emulator-5554', port=5554,
                avd_directory='D:/AVD/NavicReaderLab.avd', avd_home='D:/AVD',
                sdk_root='D:/SDK', emulator_root='D:/Components/37.2.12'),
            pins=[dict(path=__file__, sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())]))


def proof():
    return dict(kind='coordinator-emulator-lease', ticket_id='original-ticket',
        owner=dict(system='claude', session_id='host-chat', thread_name='Navic'),
        emulator=record()['gate']['binding'], no_snapshot=True, native_processes=[123],
        capacity=2, launch_profile='reader-diagnostics')


class Wire:
    """Actual installed result shapes, with caller-visible exact command ownership."""
    def __init__(self):
        self.calls = []
        self.clients = {}
        self.reads = {}
        self.counter = 0
        self.instrument = threading.Event()
        self.closed = set()
        self.native_code = 0
        self.fail_wait = False
        self.status = dict(emulator=record()['gate']['binding'], no_snapshot=True,
            native_processes=[123], console_owned=True, stop_requested=False,
            launch_profile='reader-diagnostics', sdk_output={}, adb_clients=[])

    def request(self, method, params=None, timeout=15):
        params = params or {}
        self.calls.append((method, copy.deepcopy(params), timeout))
        if method == 'emulator.status':
            return copy.deepcopy(self.status)
        if method == 'emulator.output.read':
            return dict(stdout=stream(), stderr=stream())
        if method == 'emulator.adb.start':
            self.counter += 1
            token = 'client-' + str(self.counter)
            args = params['arguments']
            if args[:2] == ['shell', '-T']:
                import shlex
                remote = shlex.split(args[2])
            else:
                remote = args
            pending = remote[0] == 'logcat'
            if remote[:2] == ['am', 'instrument']:
                if not any(v['pending'] for v in self.clients.values()):
                    raise AssertionError('Independent observer missing')
                self.instrument.set()
                lines = []
                for name in r.METHODS:
                    for code in (1, 0):
                        lines += ['INSTRUMENTATION_STATUS: class=' + r.CLASS,
                                  'INSTRUMENTATION_STATUS: test=' + name,
                                  'INSTRUMENTATION_STATUS_CODE: ' + str(code)]
                lines += ['INSTRUMENTATION_CODE: -1']
                data = ('\n'.join(lines) + '\n').encode()
            elif remote[:1] == ['install']:
                data = b'Success\n'
            elif remote == ['toybox', 'date', '+%s.%N']:
                data = b'1760000000.123456789\n'
            elif remote == ['getprop', 'sys.boot_completed']:
                data = b'1\n'
            else:
                data = b''
            self.clients[token] = dict(pending=pending, cancelled=False, data=data)
            return dict(command_id=token, **frame('running' if pending else 'complete'))
        if method.startswith('emulator.adb.'):
            token = params['command_id']
            value = self.clients[token]
            if method.endswith('.cancel'):
                value.update(pending=False, cancelled=True)
            if method.endswith('.close'):
                if value['pending']:
                    raise AssertionError('Closing live client')
                self.closed.add(token)
                return dict(command_id=token, closed=True, replay=False)
            if method.endswith('.read'):
                if token in self.reads and self.reads[token]:
                    result = self.reads[token].pop(0)
                else:
                    data, value['data'] = value['data'], b''
                    result = frame('running' if value['pending'] else 'complete', data,
                        code=1 if value['cancelled'] else 0,
                        reason='cancelled' if value['cancelled'] else 'completed')
                return dict(command_id=token, **result)
            return dict(command_id=token, **frame('running' if value['pending'] else 'complete',
                code=1 if value['cancelled'] else 0,
                reason='cancelled' if value['cancelled'] else 'completed'))
        if method == 'emulator.request-stop':
            if set(self.clients) != self.closed:
                raise AssertionError('Retained clients at stop')
            self.status.update(stop_requested=True, console_owned=False, native_processes=[])
            return dict(state='stop_requested', replay=False)
        if method == 'emulator.wait-exit':
            if self.fail_wait:
                raise TimeoutError('PRIVATE_PROTOCOL_CANARY')
            return dict(state='native_exited', exit_code=self.native_code, exit_verified=True,
                sdk_output_available=True, lease_retained_for_inspection=True)
        if method == 'workflow.end':
            return dict(id='end', ok=self.native_code == 0, state='released', ticket_id='original-ticket',
                native_exit_code=self.native_code, native_tree_exit_verified=True,
                shutdown_disposition='native_exit_failure' if self.native_code else 'native_exit_success',
                command_failures=False, app_completion='not_inferred',
                workflow_exit_code=self.native_code)
        raise AssertionError('Unsupported protocol method')


class MigrationTests(unittest.TestCase):
    def api(self, module, name):
        value = getattr(module, name, None)
        self.assertTrue(callable(value), 'MISSING_' + name)
        return value

    def session(self, wire=None, **kwargs):
        wire = wire or Wire()
        session = self.api(g, 'GateSession')(record(), transport=wire,
            acquired=dict(state='acquired', tool='android-emulator', ticket_id='original-ticket',
                engine=dict(pid=123, created_ticks=134000000000000000)),
            verify=lambda timeout=15: proof(), wrapper_settle=lambda timeout: True,
            storage_check=lambda path, timeout: True, launch_started=time.monotonic(), **kwargs)
        return session, wire

    def command(self, wire=None, **kwargs):
        session, wire = self.session(wire)
        cls = self.api(g, 'command_type')(s, session)
        cmd = cls.spawn(['gate-adb', '-s', 'emulator-5554', 'get-state'],
            timeout=15, max_bytes=65536, **kwargs)
        return cmd, session, wire

    def test_live_cli_rejects_old_peer_only_contract_before_dispatch(self):
        legacy = dict(kind='task407-owned-emulator-runtime-session', serial=r.SERIAL, avd=r.AVD,
            scope='original-three-with-independent-supervision', user_session_authorized=True,
            peer_coordination_complete=True, no_snapshot_save=True, preserve_app_data=True,
            clear_logcat=False, watchdog_id='host-fixture', adb='old-adb', emulator='old-sdk', apk_path='old.apk')
        validate = self.api(r, 'validate_live_session')
        with self.assertRaises(ValueError):
            validate(legacy, 'host-fixture')

    def test_gated_contract_preserves_user_scope_and_rejects_profile_or_target_override(self):
        validate = self.api(r, 'validate_live_session')
        self.assertIsNone(validate(record(), 'host-fixture'))
        for group, key, value in ((None, 'user_session_authorized', False),
                (None, 'serial', 'physical'), (None, 'clear_logcat', True),
                ('gate', 'emulator_profile', 'standard'), ('gate', 'component_version', '36.6.11'),
                ('gate', 'scratch_root', 'C:/Temp/unsafe'), ('gate', 'thread_name', 'Other')):
            bad = record()
            (bad if group is None else bad[group])[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                validate(bad, 'host-fixture')

    def test_live_lease_rejects_stale_wrong_owner_ticket_target_profile_and_empty_native_job(self):
        validate = self.api(g, 'validate_lease')
        self.assertIsNone(validate(proof(), record(), 'original-ticket'))
        for key, value in (('ticket_id', 'stale'), ('owner', dict(system='claude', session_id='other', thread_name='Navic')),
                ('emulator', dict(proof()['emulator'], serial='emulator-5556')),
                ('launch_profile', 'standard'), ('no_snapshot', False), ('native_processes', []),
                ('capacity', True), ('kind', 'status')):
            bad = proof()
            bad[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                validate(bad, record(), 'original-ticket')

    def test_launch_anchor_uses_native_creation_not_queue_or_post_acquired_preflight(self):
        anchor = self.api(g, 'launch_anchor')
        # Windows FILETIME: Unix100 seconds; acquisition wall120/mono500 -> mono480.
        self.assertEqual(anchor(116444737000000000, now_wall=120, now_mono=500), 480)
        for value in (True, -1, 116444738000000000):
            with self.assertRaises(ValueError):
                anchor(value, now_wall=120, now_mono=500)

    def test_command_does_not_finish_on_draining_root_exit(self):
        session, wire = self.session()
        cls = self.api(g, 'command_type')(s, session)
        cmd = cls(session, 'manual', max_bytes=65536)
        wire.clients['manual'] = dict(pending=False, cancelled=False, data=b'')
        cmd.ingest(dict(command_id='manual', **frame('draining', b'a')))
        self.assertFalse(cmd.done.is_set())
        second = dict(command_id='manual', **frame('complete', b'b'))
        second['stdout']['bytes'] = 2
        cmd.ingest(second)
        self.assertTrue(cmd.done.is_set())
        self.assertEqual(cmd.take_output(), (b'ab', b''))

    def test_partial_base64_utf8_and_event_lines_preserve_exact_bytes(self):
        session, wire = self.session()
        cls = self.api(g, 'command_type')(s, session)
        wire.clients['manual'] = dict(pending=False, cancelled=False, data=b'')
        cmd = cls(session, 'manual', max_bytes=65536)
        cmd.ingest(dict(command_id='manual', **frame('draining', b'\xe2')))
        second = dict(command_id='manual', **frame('complete', b'\x82\xac\n'))
        second['stdout']['bytes'] = 4
        cmd.ingest(second)
        self.assertEqual(cmd.take_output()[0], '€\n'.encode())
        lines = []
        source = cls(session, 'manual', max_bytes=65536, on_line=lines.append)
        source.ingest(dict(command_id='manual', **frame('running', b'first\r')))
        second = dict(command_id='manual', **frame('running', b'\nsecond\n'))
        second['stdout']['bytes'] = 14
        source.ingest(second)
        self.assertEqual(lines, [b'first', b'second'])
        self.assertEqual(source.buffered_bytes, 0)

    def test_output_drop_read_error_malformed_cap_or_wrong_client_disqualifies(self):
        for kind in ('drop', 'read_error', 'base64', 'identity', 'cap', 'bytes'):
            session, wire = self.session()
            cls = self.api(g, 'command_type')(s, session)
            wire.clients['manual'] = dict(pending=False, cancelled=False, data=b'')
            cmd = cls(session, 'manual', max_bytes=8)
            value = dict(command_id='manual', **frame('complete', b'123456789' if kind == 'cap' else b'x'))
            if kind == 'drop':
                value['stdout'].update(dropped_bytes=1, truncated=True)
            elif kind == 'read_error':
                value['stdout']['read_error'] = True
            elif kind == 'base64':
                value['stdout']['base64'] = '!'
            elif kind == 'identity':
                value['command_id'] = 'foreign'
            elif kind == 'bytes':
                value['stdout']['returned_bytes'] = 2
            cmd.ingest(value)
            self.assertIsNotNone(cmd.failure, kind)

    def test_exact_client_cancel_settle_close_and_natural_output_survives_close(self):
        cmd, session, wire = self.command()
        self.assertTrue(cmd.done.wait(1))
        self.assertTrue(cmd.settle(.5))
        self.assertIn(cmd.command_id, wire.closed)
        self.assertEqual(cmd.take_output(), (b'', b''))
        cls = self.api(g, 'command_type')(s, session)
        source = cls.spawn(['gate-adb', '-s', r.SERIAL, 'shell', '-T', 'logcat'],
            timeout=15, max_bytes=65536, on_line=lambda line: None)
        self.assertTrue(source.cancel(.5))
        self.assertTrue(source.cancelled)
        self.assertEqual(source.failure, 'CANCELLED')
        cancelled = [params['command_id'] for method, params, _ in wire.calls if method.endswith('.cancel')]
        self.assertEqual(cancelled, [source.command_id])

    def test_complete_buffered_output_is_drained_before_close_and_take(self):
        session, wire = self.session()
        cls = self.api(g, 'command_type')(s, session)
        cmd = cls(session, 'manual', max_bytes=65536)
        wire.clients['manual'] = dict(pending=False, cancelled=False, data=b'last')
        value = dict(command_id='manual', **frame('complete', b'first'))
        value['stdout'].update(buffered_bytes=4, bytes=9)
        cmd.ingest(value)
        self.assertFalse(cmd.done.is_set())
        second = dict(command_id='manual', **frame('complete', b'last'))
        second['stdout']['bytes'] = 9
        cmd.ingest(second)
        self.assertTrue(cmd.settle(.5))
        self.assertEqual(cmd.take_output(), (b'firstlast', b''))

    def test_protocol_exception_is_closed_failure_never_raw_or_replayed(self):
        session, wire = self.session()
        cls = self.api(g, 'command_type')(s, session)
        wire.request = mock.Mock(side_effect=TimeoutError('PRIVATE_PROTOCOL_CANARY'))
        with self.assertRaises(ValueError) as caught:
            cls.spawn(['gate-adb', '-s', r.SERIAL, 'get-state'], timeout=15, max_bytes=65536)
        self.assertNotIn('PRIVATE', str(caught.exception))
        self.assertEqual(wire.request.call_count, 1)
        self.assertTrue(session.uncertain)

    def test_sdk_nonzero_is_separate_from_native_exit_release_and_snapshot_absence(self):
        session, wire = self.session()
        wire.native_code = 3221226505
        caps = s.Caps(preparation=180, postboot_preparation=45, instrumentation=420,
            command=15, process_wait=5, stop=5, settle=2, snapshot_budget=15)
        closed = session.finish(caps)
        self.assertTrue(closed['native_tree_exit_verified'])
        self.assertTrue(closed['lease_released'])
        self.assertTrue(closed['snapshots_empty'])
        self.assertEqual(closed['native_exit_code'], 3221226505)
        self.assertEqual(closed['shutdown_disposition'], 'native_exit_failure')
        self.assertEqual(closed['workflow_exit_code'], 3221226505)
        self.assertLessEqual(next(t for m, _, t in wire.calls if m == 'emulator.wait-exit'), 5)

    def test_uncertain_wait_preserves_original_keeper_and_never_ends_or_replays(self):
        session, wire = self.session()
        wire.fail_wait = True
        caps = s.Caps(420, 15, 5, 5, 2, 15, 180, 45)
        first = session.finish(caps)
        second = session.finish(caps)
        self.assertFalse(first['lease_released'])
        self.assertEqual(first, second)
        self.assertTrue(session.uncertain)
        self.assertEqual([m for m, _, _ in wire.calls].count('emulator.wait-exit'), 1)
        self.assertNotIn('workflow.end', [m for m, _, _ in wire.calls])
        self.assertNotIn('PRIVATE', json.dumps(first))

    def test_snapshot_unknown_or_nonempty_never_becomes_flag_based_proof(self):
        session, wire = self.session()
        session.storage_check = lambda path, timeout: False
        closed = session.finish(s.Caps(420, 15, 5, 5, 2, 15, 180, 45))
        self.assertTrue(closed['native_tree_exit_verified'])
        self.assertFalse(closed['snapshots_empty'])
        self.assertIn('SNAPSHOTS_UNCONFIRMED', closed['cleanup_failures'])

    def test_filesystem_probe_requires_postexit_exact_paths_and_rejects_reparse(self):
        inspect = self.api(g, 'inspect_snapshot_storage')
        with mock.patch.object(g.os, 'lstat', side_effect=OSError('PRIVATE')):
            self.assertFalse(inspect('D:/AVD/NavicReaderLab.avd', 15))
        # Unknown directory/root or symlink never produces absence.
        for attrs in (0x400,):
            with mock.patch.object(g.os, 'lstat', return_value=SimpleNamespace(
                    st_file_attributes=attrs, st_mode=0o040755)):
                self.assertFalse(inspect('D:/AVD/NavicReaderLab.avd', 15))

    def test_original_supervisor_concurrency_oracles_and_gate_closure_integrate(self):
        execute = self.api(r, 'run_gated_session')
        session, wire = self.session()
        caps = s.Caps(.8, .5, .2, .2, .2, .5, 1, 1)
        pins = [dict(path=__file__, sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())]
        result = execute(s, record(), set(), pins=pins, caps=caps,
            watchdog_id='host-fixture', gate=session, cleanup=lambda: True)
        self.assertTrue(wire.instrument.is_set())
        self.assertTrue(result['automatic_emulator_teardown_verified'])
        self.assertTrue(result['all_three_original_runtime_cases_passed'])
        self.assertEqual(result['supervisor']['lifecycle_proof'], 'GATE_NATIVE_TREE_LEASE_HOST_STORAGE_V1')
        self.assertFalse(result['supervisor']['selected_disconnect'])
        self.assertFalse(result['supervisor']['strict_absence'])
        self.assertEqual(result['supervisor']['cause'], 'NATURAL_COMPLETION')
        safe = json.dumps(result)
        for private in ('original-ticket', 'host-chat', 'created_ticks', 'client-'):
            self.assertNotIn(private, safe)
        methods = [m for m, _, _ in wire.calls]
        self.assertLess(methods.index('emulator.request-stop'), methods.index('emulator.wait-exit'))
        self.assertLess(methods.index('emulator.wait-exit'), methods.index('workflow.end'))

    def test_r156_transport_snapshot_gap_never_changes_natural_gated_run(self):
        execute = self.api(r, 'run_gated_session')
        caps = s.Caps(.8, .5, .2, .2, .2, .5, 1, 1)
        pins = [dict(path=__file__, sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())]
        gap = {'sample_state': 'SNAPSHOT_GAP', 'first_role': 'UNAVAILABLE',
               'first_phase': 'UNAVAILABLE', 'request_admission_sequence': None,
               'failure_observation_sequence': None, 'sequence_saturated': False,
               'source_branch': 'UNAVAILABLE', 'source_failure': 'UNAVAILABLE',
               'source_request_admission_sequence': None,
               'source_failure_observation_sequence': None, 'receive_side': 'UNAVAILABLE',
               'event_source_first_phase': 'UNAVAILABLE', 'event_source_request_admission_sequence': None,
               'event_source_failure_observation_sequence': None,
               'first_outer_ok': 'UNAVAILABLE', 'first_outer_result': 'UNAVAILABLE',
               'first_outer_error': 'UNAVAILABLE'}
        for defect in ('MISSING', 'THROWING', 'MALFORMED'):
            with self.subTest(defect=defect):
                session, wire = self.session()
                if defect == 'THROWING':
                    session.transport_diagnostic_snapshot = lambda: (_ for _ in ()).throw(
                        RuntimeError('R156_PRIVATE_CANARY'))
                elif defect == 'MALFORMED':
                    session.transport_diagnostic_snapshot = lambda: {'sample_state': 'SAMPLED'}
                result = execute(s, record(), set(), pins=pins, caps=caps,
                    watchdog_id='host-fixture', gate=session, cleanup=lambda: True)
                self.assertEqual(result['transport_first_failure_diagnostics'], gap)
                self.assertEqual(result['supervisor']['cause'], 'NATURAL_COMPLETION')
                self.assertTrue(result['supervisor']['eligible_for_oracle'])
                self.assertTrue(result['automatic_emulator_teardown_verified'])
                self.assertNotIn('R156_PRIVATE_CANARY', json.dumps(result))

    def test_gated_cli_receipt_links_old_seal_without_exempting_android_drift(self):
        validate = self.api(r, 'validate_host_qualification')
        host = ['scripts/task407-runtime-runner.py', 'scripts/task407-runtime-supervisor.py',
                'scripts/test-task407-runtime-runner.py', 'scripts/test-task407-runtime-supervisor.py',
                'scripts/task407-runtime-gate.py', 'scripts/test-task407-runtime-gate.py',
                'docs/superpowers/specs/2026-10-04-reader-fresh-session-checkpoint-amendment.md']
        unaffected = ['composeApp/src/androidMain/Untouched.kt', 'scripts/task407-runtime-diagnostics.py',
                      'scripts/test-task407-runtime-diagnostics.py']
        seal = dict(kind='full-lower-qualified-pushed-source-emulator-only-apk-seal',
            original_timeout_and_acceptance_oracles_changed=False, runtime_accepted=False,
            base_source_commit='a' * 40,
            apk=dict(package=r.PACKAGE, production_signed=False, path='fixture-only.apk',
                sha256='65cc8d619c03aba32043eb77603c6254fc61aac792e9581b8993bd9b00a8c517'),
            source_pins=[dict(path=p, sha256='a' * 64) for p in host[:4] + host[6:] + unaffected])
        receipt = dict(kind='task407-gate-host-qualification-v1', seal_sha256=r.IMMUTABLE_SEAL_SHA256,
            source_commit='c' * 40, ordered=True, spec='PASS', quality='PASS', main_after_spec='PASS',
            main_after_quality='PASS', host_tests='PASS', runtime_accepted=False,
            apk_sha256=seal['apk']['sha256'],
            pins=[dict(path=p, sha256='c' * 64) for p in host])
        pins, commit = validate(seal, receipt, ROOT, r.IMMUTABLE_SEAL_SHA256)
        self.assertEqual(commit, 'c' * 40)
        indexed = {Path(p['path']).name: p['sha256'] for p in pins}
        self.assertEqual(indexed['Untouched.kt'], 'a' * 64)
        self.assertEqual(indexed['task407-runtime-runner.py'], 'c' * 64)
        for mutation in ('android', 'seal', 'apk', 'review', 'missing', 'duplicate'):
            bad = copy.deepcopy(receipt)
            if mutation == 'android':
                bad['pins'].append(dict(path=unaffected[0], sha256='c' * 64))
            elif mutation == 'seal':
                bad['seal_sha256'] = 'd' * 64
            elif mutation == 'apk':
                bad['apk_sha256'] = 'd' * 64
            elif mutation == 'review':
                bad['main_after_spec'] = 'FAIL'
            elif mutation == 'missing':
                bad['pins'].pop()
            else:
                bad['pins'].append(bad['pins'][0])
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                validate(seal, bad, ROOT, r.IMMUTABLE_SEAL_SHA256)


class BoundaryTests(unittest.TestCase):
    """Additional protocol/clock closure boundaries; no live gate operations."""
    api = MigrationTests.api
    session = MigrationTests.session
    def test_cumulative_wire_gap_without_drop_metadata_is_rejected(self):
        session, wire = self.session()
        cls = self.api(g, 'command_type')(s, session)
        wire.clients['manual'] = dict(pending=False, cancelled=False, data=b'')
        cmd = cls(session, 'manual', max_bytes=65536)
        value = dict(command_id='manual', **frame('complete', b'a'))
        value['stdout']['bytes'] = 10
        cmd.ingest(value)
        self.assertIsNotNone(cmd.failure)

    def test_settled_adb_stream_failure_does_not_strand_native_lease(self):
        # Rejecting capture evidence must not discard independent exit proof.
        for problem in ('drop', 'read_error'):
            with self.subTest(problem=problem):
                session, wire = self.session()
                cls = self.api(g, 'command_type')(s, session)
                wire.clients['manual'] = dict(pending=False, cancelled=False, data=b'')
                cmd = cls(session, 'manual', max_bytes=65536)
                value = dict(command_id='manual', **frame('complete', b'x'))
                if problem == 'drop':
                    value['stdout'].update(bytes=2, dropped_bytes=1, truncated=True)
                else:
                    value['stdout']['read_error'] = True
                cmd.ingest(value)
                self.assertEqual(cmd.failure, 'STREAM_FAILED')
                closed = session.finish(s.Caps(420, 15, 5, 5, .01, 15, 180, 45))
                self.assertTrue(closed['lease_released'], 'SETTLED_ADB_GAP_STRANDED_LEASE')
                self.assertTrue(closed['native_tree_exit_verified'])
                self.assertEqual(cmd.failure, 'STREAM_FAILED')
                self.assertEqual(cmd.take_output(), (b'', b''))
                self.assertEqual(wire.closed, {'manual'})
                methods = [method for method, _, _ in wire.calls]
                self.assertEqual(methods.count('emulator.adb.close'), 1)
                self.assertEqual(methods.count('workflow.end'), 1)
                self.assertNotIn('emulator.adb.start', methods)

    def test_stream_failure_never_settles_without_exact_complete_exit_proof(self):
        for problem in ('running', 'draining', 'foreign', 'unverified', 'missing_code'):
            with self.subTest(problem=problem):
                session, wire = self.session()
                cls = self.api(g, 'command_type')(s, session)
                cmd = cls(session, 'manual', max_bytes=65536)
                value = dict(command_id='manual', **frame('complete'))
                value['stdout']['read_error'] = True
                if problem in ('running', 'draining'):
                    value['state'] = problem
                elif problem == 'foreign':
                    value['command_id'] = 'foreign'
                elif problem == 'unverified':
                    value['exit_verified'] = False
                else:
                    value['exit_code'] = None
                cmd.ingest(value)
                self.assertIsNotNone(cmd.failure)
                self.assertFalse(cmd.done.is_set())
                self.assertFalse(cmd.settle(.001))
                self.assertFalse(wire.closed)

    def test_sdk_stream_gap_blocks_oracle_but_does_not_skip_safe_release(self):
        session, wire = self.session()
        original = wire.request
        def request(method, params=None, timeout=15):
            if method == 'emulator.output.read':
                return dict(stdout=stream(dropped=1), stderr=stream())
            return original(method, params, timeout)
        wire.request = request
        result = session.finish(s.Caps(420, 15, 5, 5, 2, 15, 180, 45))
        self.assertTrue(result['lease_released'])
        self.assertFalse(result['sdk_output_complete'])
        self.assertIn('SDK_OUTPUT_UNCONFIRMED', result['cleanup_failures'])

    def test_original_launch_deadline_cannot_restart_after_gate_preflight(self):
        execute = self.api(r, 'run_gated_session')
        session, wire = self.session()
        session.launch_started = time.monotonic() - 2
        pins = [dict(path=__file__, sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())]
        result = execute(s, record(), set(), pins=pins, caps=s.Caps(.5, .2, .1, .1, .2, .2, 1, .5),
            watchdog_id='host-fixture', gate=session)
        self.assertFalse(wire.instrument.is_set())
        self.assertFalse(result['runtime_result_analysis_performed'])
        self.assertEqual(result['supervisor']['cause'], 'PREPARATION_CAP')
        self.assertFalse(any(m == 'emulator.adb.start' for m, _, _ in wire.calls))

    def test_bad_live_lease_stops_before_install_and_still_uses_original_closure(self):
        execute = self.api(r, 'run_gated_session')
        session, wire = self.session()
        session.verify = lambda timeout=15: dict(proof(), ticket_id='stale')
        pins = [dict(path=__file__, sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())]
        result = execute(s, record(), set(), pins=pins, caps=s.Caps(.5, .2, .1, .1, .2, .2, 1, .5),
            watchdog_id='host-fixture', gate=session)
        self.assertFalse(wire.instrument.is_set())
        self.assertFalse(result['runtime_result_analysis_performed'])
        self.assertTrue(result['automatic_emulator_teardown_verified'])
        self.assertFalse(any(m == 'emulator.adb.start' for m, _, _ in wire.calls))

    def test_shutdown_fault_never_rewrites_original_instrument_result_or_sdk_code(self):
        execute = self.api(r, 'run_gated_session')
        session, wire = self.session()
        wire.native_code = 3221226505
        pins = [dict(path=__file__, sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())]
        result = execute(s, record(), set(), pins=pins, caps=s.Caps(.5, .2, .1, .1, .2, .2, 1, .5),
            watchdog_id='host-fixture', gate=session, cleanup=lambda: True)
        self.assertTrue(result['all_three_original_runtime_cases_passed'])
        self.assertEqual(result['supervisor']['instrumentation_exit_code'], 0)
        self.assertEqual(result['supervisor']['native_exit_code'], 3221226505)
        self.assertEqual(result['supervisor']['shutdown_disposition'], 'native_exit_failure')

    def test_owned_anr_still_cancels_instrumentation_and_never_projects_oracle(self):
        execute = self.api(r, 'run_gated_session')
        session, wire = self.session()
        original = wire.request
        def request(method, params=None, timeout=15):
            value = original(method, params, timeout)
            if method == 'emulator.adb.start' and wire.instrument.is_set():
                wire.clients[value['command_id']]['pending'] = True
                value['state'] = 'running'
                for token, child in wire.clients.items():
                    if token != value['command_id'] and child['pending']:
                        child['data'] = (b'1760000000.123456790 1000 1001 I am_anr: '
                            b'[0,123,paige.navic.task407.runtime,0,PRIVATE_ANR_CANARY]\n')
            return value
        wire.request = request
        pins = [dict(path=__file__, sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())]
        result = execute(s, record(), set(), pins=pins, caps=s.Caps(.8, .2, .1, .1, .2, .2, 1, .5),
            watchdog_id='host-fixture', gate=session)
        self.assertEqual(result['supervisor']['cause'], 'OWN_ANR')
        self.assertFalse(result['runtime_result_analysis_performed'])
        self.assertNotIn('PRIVATE_ANR_CANARY', json.dumps(result))
        self.assertTrue(all(not child['pending'] for child in wire.clients.values()))

    def test_sdk_observation_drains_while_original_wait_client_is_running(self):
        execute = self.api(r, 'run_gated_session')
        session, wire = self.session()
        original = wire.request
        waiting = []
        def request(method, params=None, timeout=15):
            value = original(method, params, timeout)
            if method == 'emulator.adb.start' and params['arguments'] == ['wait-for-device']:
                token = value['command_id']
                wire.clients[token]['pending'] = True
                value['state'] = 'running'
                waiting.append(token)
            elif method == 'emulator.output.read' and waiting:
                wire.clients[waiting[0]]['pending'] = False
            return value
        wire.request = request
        pins = [dict(path=__file__, sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())]
        result = execute(s, record(), set(), pins=pins, caps=s.Caps(.5, .2, .1, .1, .2, .2, .5, .5),
            watchdog_id='host-fixture', gate=session, cleanup=lambda: True)
        self.assertTrue(result['all_three_original_runtime_cases_passed'])

    def test_new_doc_receipt_does_not_require_rewriting_historical_seal(self):
        validate = self.api(r, 'validate_host_qualification')
        paths = sorted(r.HOST_MIGRATION_PATHS)
        original = [p for p in paths if p.startswith('scripts/') and '-gate.py' not in p]
        seal = dict(kind='full-lower-qualified-pushed-source-emulator-only-apk-seal',
            original_timeout_and_acceptance_oracles_changed=False, runtime_accepted=False,
            base_source_commit='a' * 40, apk=dict(package=r.PACKAGE, production_signed=False,
                sha256=r.IMMUTABLE_APK_SHA256), source_pins=[dict(path=p, sha256='a' * 64)
                    for p in original + ['scripts/task407-runtime-diagnostics.py', 'scripts/test-task407-runtime-diagnostics.py']])
        receipt = dict(kind='task407-gate-host-qualification-v1', seal_sha256=r.IMMUTABLE_SEAL_SHA256,
            apk_sha256=r.IMMUTABLE_APK_SHA256, source_commit='c' * 40, ordered=True, spec='PASS',
            quality='PASS', main_after_spec='PASS', main_after_quality='PASS', host_tests='PASS',
            runtime_accepted=False, pins=[dict(path=p, sha256='c' * 64) for p in paths])
        try:
            pins, commit = validate(seal, receipt, ROOT, r.IMMUTABLE_SEAL_SHA256)
        except ValueError:
            self.fail('Historical Android seal need not contain new prospective document')
        self.assertEqual(commit, 'c' * 40)
        self.assertEqual(len(pins), 9)

    def test_live_launcher_uses_original_helper_profile_and_explicit_child_scratch(self):
        self.assertTrue(callable(getattr(g, 'GateSession', None)), 'MISSING_GateSession')
        acquired = dict(state='acquired', tool='android-emulator', ticket_id='original-ticket',
            engine=dict(pid=123, created_ticks=116444736000000000 + int(time.time() * 10000000) - 100000))
        transport = SimpleNamespace(acquire=lambda: acquired, settle_wrapper=lambda timeout: True)
        keeper = SimpleNamespace()
        verify_process = SimpleNamespace(stdout=io.BytesIO(json.dumps(proof()).encode()),
            stderr=io.BytesIO(), returncode=0, wait=lambda timeout: 0, kill=lambda: None)
        with mock.patch.object(g, 'JsonLineTransport', return_value=transport), \
             mock.patch.object(g.subprocess, 'Popen', side_effect=[keeper, verify_process]) as launch:
            session = g.GateSession.launch(record())
            actual = session.verify()
        self.assertEqual(actual['ticket_id'], 'original-ticket')
        first, second = launch.call_args_list
        command = first.args[0]
        self.assertEqual(command[:5], [sys.executable, '-B', record()['gate']['helper'], 'run', '--tool'])
        self.assertIn('reader-diagnostics', command)
        self.assertNotIn('--emulator-root', command,
            'Default pinned component verification must not be bypassed by explicit-root selection')
        self.assertNotIn('emu', command)
        for key in ('TEMP', 'TMP', 'TMPDIR'):
            self.assertEqual(first.kwargs['env'][key], 'D:/Temp/navic-task407-gated-runtime/r146')
        self.assertIn('verify-lease', second.args[0])
        self.assertIn('--resume-ticket', second.args[0])
        self.assertIn('original-ticket', second.args[0])

    def test_settlement_does_not_wait_past_original_cap_for_concurrent_close(self):
        session, wire = self.session()
        cls = self.api(g, 'command_type')(s, session)
        wire.clients['manual'] = dict(pending=False, cancelled=False, data=b'')
        cmd = cls(session, 'manual', max_bytes=65536)
        cmd.ingest(dict(command_id='manual', **frame()))
        cmd._closing_lock.acquire()
        release = threading.Timer(.1, cmd._closing_lock.release)
        release.start()
        try:
            self.assertFalse(cmd.settle(.01))
        finally:
            release.join(.5)
        self.assertTrue(cmd.settle(.2))

    def test_host_storage_probe_distinguishes_empty_snapshot_tree_from_ram_or_disk_residue(self):
        inspect = self.api(g, 'inspect_snapshot_storage')
        from contextlib import contextmanager
        directory = SimpleNamespace(st_file_attributes=0, st_mode=0o040755)
        snapshots = SimpleNamespace(name='snapshots', stat=lambda **kwargs: directory,
                                    is_symlink=lambda: False)
        for root_names, children, expected in (([snapshots], [], True),
                ([SimpleNamespace(name='ram.img')], [], False),
                ([SimpleNamespace(name='snapshots.img')], [], False),
                ([snapshots], [SimpleNamespace(name='ram.img')], False)):
            @contextmanager
            def scan(path):
                yield iter(children if Path(path).name == 'snapshots' else root_names)
            with mock.patch.object(g.os, 'lstat', return_value=directory), \
                 mock.patch.object(g.os, 'scandir', side_effect=scan):
                self.assertEqual(inspect('D:/AVD/NavicReaderLab.avd', 15), expected)

    def test_contract_owner_is_hashed_without_persisted_opaque_session_identity(self):
        value = record()
        value['gate']['expected_owner'] = dict(system='claude', thread_name='Navic',
            session_sha256=hashlib.sha256(b'host-chat').hexdigest())
        try:
            r.validate_live_session(value, 'host-fixture')
        except ValueError:
            self.fail('Contract must admit the current-owner digest, not require raw session identity')
        with self.assertRaises(ValueError):
            r.validate_live_session(dict(value, gate=dict(value['gate'],
                expected_owner=dict(system='claude', thread_name='Navic', session_id='host-chat'))), 'host-fixture')
        self.assertIsNone(g.validate_lease(proof(), value, 'original-ticket'))

    def test_cli_preserves_sdk_shutdown_failure_even_when_original_oracle_passes(self):
        value = record()
        value.update(seal_path='seal', seal_sha256='a' * 64,
            host_qualification_path='receipt', host_qualification_sha256='b' * 64)
        value['gate']['pins'] = []
        runtime = SimpleNamespace(RELEASE='df52528334e1391b', RELEASE_ROOT='D:/fixture', RUNTIME_PATHS=())
        seal = dict(base_source_commit='a' * 40, apk=dict(path='fixture-only.apk', sha256='c' * 64))
        result = dict(automatic_emulator_teardown_verified=True, all_three_original_runtime_cases_passed=True,
            supervisor=dict(native_exit_code=3221226505, workflow_exit_code=3221226505,
                            shutdown_disposition='native_exit_failure', command_failures=False))
        with mock.patch.object(r, 'read_checked_json', side_effect=[value, seal, {}]), \
             mock.patch.object(r, 'validate_live_session'), \
             mock.patch.object(r, 'validate_host_qualification', return_value=([], 'c' * 40)), \
             mock.patch.object(r, '_runtime_gate', return_value=runtime), \
             mock.patch.object(r, 'pins_match', return_value=True), \
             mock.patch.object(r.Path, 'read_bytes', side_effect=[
                 b'{"release":"df52528334e1391b","version":"0.16.4"}',
                 b'{"profiles":{"reader-diagnostics":{"version":"37.2.12","root":"D:/Components/37.2.12"}}}']), \
             mock.patch.object(r.Path, 'open', mock.mock_open()), \
             mock.patch.object(r.os, 'environ', dict(WATCHDOG_ID='host-fixture')), \
             mock.patch.object(r.subprocess, 'run', side_effect=[
                 SimpleNamespace(returncode=0, stdout=b'c' * 40), SimpleNamespace(returncode=0, stdout=b'')]), \
             mock.patch.object(r, 'run_gated_session', return_value=result):
            from contextlib import redirect_stdout
            output = io.StringIO()
            with redirect_stdout(output):
                code = r.main(['--contract', 'fixture', '--contract-sha256', 'd' * 64,
                               '--output', 'never-created'])
        self.assertNotEqual(code, 0)
        terminal = json.loads(output.getvalue())
        self.assertEqual(terminal.get('native_exit_code'), 3221226505)
        self.assertEqual(terminal.get('shutdown_disposition'), 'native_exit_failure')

    def test_event_partial_terminal_is_feed_loss_not_silent_success(self):
        session, wire = self.session()
        cls = self.api(g, 'command_type')(s, session)
        cmd = cls(session, 'manual', max_bytes=65536, on_line=lambda line: None)
        cmd.ingest(dict(command_id='manual', **frame('complete', b'partial')))
        self.assertTrue(cmd.feed_lost.is_set())
        self.assertIsNotNone(cmd.failure)


class R147Tests(unittest.TestCase):
    """Four project-owned SPEC findings; native and cleanup I/O only are injected."""
    session = MigrationTests.session
    api = MigrationTests.api

    def execute(self, session, *, cleanup=None, **options):
        arguments = dict(pins=[dict(path=__file__, sha256=hashlib.sha256(
            Path(__file__).read_bytes()).hexdigest())],
            caps=s.Caps(.8, .5, .2, .2, .05, .5, 1, 1),
            watchdog_id='host-fixture', gate=session, cleanup=cleanup)
        arguments.update(options)
        return r.run_gated_session(s, record(), set(), **arguments)

    def test_complete_supervision_error_closes_exact_client_without_usable_output(self):
        session, wire = self.session()
        cls = g.command_type(s, session)
        wire.clients['manual'] = dict(pending=False, cancelled=False, data=b'')
        cmd = cls(session, 'manual', max_bytes=65536)
        cmd.ingest(dict(command_id='manual', **dict(frame(data=b'PRIVATE'),
            reason='supervision_error', error='PRIVATE_SUPERVISION_ERROR')))
        self.assertEqual(cmd.failure, 'STREAM_FAILED')
        closed = session.finish(s.Caps(420, 15, 5, 5, .01, 15, 180, 45))
        self.assertTrue(closed['lease_released'], 'COMPLETE_ERROR_STRANDED_LEASE')
        self.assertTrue(closed['native_tree_exit_verified'])
        self.assertEqual(wire.closed, {'manual'})
        self.assertEqual(cmd.take_output(), (b'', b''))
        self.assertNotIn('PRIVATE', json.dumps(closed))

    def test_instrument_supervision_error_rejects_original_oracle_but_releases_native_lease(self):
        session, wire = self.session()
        original = wire.request
        instrument = []
        def request(method, params=None, timeout=15):
            value = original(method, params, timeout)
            if method == 'emulator.adb.start' and wire.instrument.is_set():
                instrument.append(value['command_id'])
            if method == 'emulator.adb.read' and params['command_id'] in instrument:
                value.update(reason='supervision_error', error='PRIVATE_SUPERVISION_ERROR')
            if method == 'workflow.end':
                value.update(command_failures=True, ok=False, workflow_exit_code=1)
            return value
        wire.request = request
        result = self.execute(session, cleanup=lambda: True)
        self.assertTrue(result['automatic_emulator_teardown_verified'])
        self.assertTrue(result['supervisor']['lease_released'])
        self.assertEqual(result['supervisor']['cause'], 'INSTRUMENT_FAILED')
        self.assertTrue(result['supervisor']['command_failures'])
        self.assertFalse(result['runtime_result_analysis_performed'])
        self.assertFalse(result['all_three_original_runtime_cases_passed'])
        self.assertEqual(set(wire.clients), wire.closed)
        self.assertNotIn('PRIVATE', json.dumps(result))

    def test_error_without_exact_complete_integer_exit_proof_stays_unsettled(self):
        for problem in ('running', 'draining', 'foreign', 'unverified', 'missing_code', 'bool_code'):
            session, wire = self.session()
            cmd = g.command_type(s, session)(session, 'manual', max_bytes=65536)
            value = dict(command_id='manual', **dict(frame(), error='PRIVATE', reason='supervision_error'))
            if problem in ('running', 'draining'):
                value['state'] = problem
            elif problem == 'foreign':
                value['command_id'] = 'foreign'
            elif problem == 'unverified':
                value['exit_verified'] = False
            else:
                value['exit_code'] = True if problem == 'bool_code' else None
            cmd.ingest(value)
            self.assertFalse(cmd.done.is_set(), problem)
            self.assertFalse(cmd.settle(.001), problem)
            self.assertFalse(wire.closed)

    def test_cleanup_pending_failed_or_missing_blocks_output_take_and_original_oracle(self):
        for outcome in (None, False, 'pending', 'throws'):
            session, wire = self.session()
            calls = []
            def cleanup():
                calls.append('cleanup')
                self.assertTrue(session._finished['native_tree_exit_verified'])
                self.assertTrue(session._finished['lease_released'])
                self.assertTrue(session._finished['wrapper_settled'])
                self.assertEqual(set(wire.clients), wire.closed)
                if outcome == 'throws':
                    raise RuntimeError('PRIVATE_CLEANUP_ERROR')
                return outcome
            with mock.patch.object(s.Supervisor, 'take_instrumentation_output', autospec=True,
                    side_effect=s.Supervisor.take_instrumentation_output) as take, \
                 mock.patch.object(r, 'project_instrumentation', wraps=r.project_instrumentation) as oracle:
                result = self.execute(session, cleanup=cleanup if outcome is not None else None)
            self.assertFalse(result['runtime_result_analysis_performed'], 'CLEANUP_MUST_PRECEDE_ANALYSIS')
            self.assertFalse(result['all_three_original_runtime_cases_passed'])
            self.assertTrue(result['automatic_emulator_teardown_verified'])
            self.assertFalse(take.called)
            self.assertFalse(oracle.called)
            self.assertTrue(result['host_snapshot_residue_cleanup_required'])
            self.assertNotIn('PRIVATE', json.dumps(result))

    def test_cleanup_success_occurs_after_closure_before_take_projection_and_original_pass(self):
        session, wire = self.session()
        order = []
        original_take, original_project, original_pass = (s.Supervisor.take_instrumentation_output,
            r.project_instrumentation, r.original_three_passed)
        def cleanup():
            self.assertTrue(all(session._finished[key] for key in (
                'native_tree_exit_verified', 'lease_released', 'wrapper_settled', 'snapshots_empty')))
            self.assertEqual(set(wire.clients), wire.closed)
            order.append('cleanup')
            return True
        def take(guardian):
            order.append('take')
            return original_take(guardian)
        def project(*args, **kwargs):
            order.append('project')
            return original_project(*args, **kwargs)
        def passed(*args, **kwargs):
            order.append('original_pass')
            return original_pass(*args, **kwargs)
        with mock.patch.object(s.Supervisor, 'take_instrumentation_output', take), \
             mock.patch.object(r, 'project_instrumentation', project), \
             mock.patch.object(r, 'original_three_passed', passed):
            result = self.execute(session, cleanup=cleanup)
        self.assertEqual(order, ['cleanup', 'take', 'project', 'original_pass'])
        self.assertTrue(result['all_three_original_runtime_cases_passed'])
        self.assertFalse(result['host_snapshot_residue_cleanup_required'])

    def test_cleanup_never_runs_while_keeper_or_closure_is_uncertain(self):
        session, wire = self.session()
        wire.fail_wait = True
        calls = []
        result = self.execute(session, cleanup=lambda: calls.append(True) or True)
        self.assertFalse(calls)
        self.assertFalse(result['runtime_result_analysis_performed'])
        self.assertFalse(result['automatic_emulator_teardown_verified'])

    def test_sdk_throwing_diagnostic_does_not_gap_or_desynchronize_either_stream(self):
        session, wire = self.session()
        original = wire.request
        fragments = [dict(stdout=stream(b'a'), stderr=stream(b'b')),
            dict(stdout=stream(b'c', count=2), stderr=stream(b'd', count=2))]
        def request(method, params=None, timeout=15):
            if method == 'emulator.output.read':
                return fragments.pop(0) if fragments else dict(
                    stdout=stream(count=2), stderr=stream(count=2))
            return original(method, params, timeout)
        wire.request = request
        callbacks = []
        def diagnostic(index, data):
            callbacks.append(index)
            raise RuntimeError('PRIVATE_DIAGNOSTIC_ERROR')
        session._sdk_observer = diagnostic
        try:
            session.drain_sdk(.5)
            session.drain_sdk(.5)
        except ValueError:
            pass  # Assertion RED, not an exception/import failure.
        self.assertFalse(session._sdk_gap, 'DIAGNOSTIC_IS_NOT_TRANSPORT')
        self.assertEqual(session._sdk_received, [2, 2])
        self.assertEqual(tuple(map(bytes, session._sdk_buffers)), (b'ac', b'bd'))
        self.assertEqual(callbacks, [0])
        closed = session.finish(s.Caps(420, 15, 5, 5, .05, 15, 180, 45))
        self.assertTrue(closed['sdk_output_complete'])
        self.assertTrue(closed['lease_released'])
        self.assertEqual(getattr(session, 'diagnostic_output_errors', 0), 1)
        self.assertNotIn('PRIVATE', json.dumps(closed))

    def test_sdk_diagnostic_failure_preserves_original_runner_outcome_and_closed_count(self):
        session, wire = self.session()
        diagnostics = r._runtime_diagnostics()
        with mock.patch.object(diagnostics.EmulatorStartupLog, 'feed',
                side_effect=RuntimeError('PRIVATE_DIAGNOSTIC_ERROR')):
            result = self.execute(session, cleanup=lambda: True)
        self.assertTrue(result['all_three_original_runtime_cases_passed'], 'DIAGNOSTIC_CHANGED_ORACLE')
        self.assertFalse(result['gate_transport_observations']['sdk_output_gap'])
        self.assertEqual(result['diagnostic_errors'], 1)
        self.assertNotIn('PRIVATE', json.dumps(result))

    def test_sdk_real_wire_faults_still_reject_after_diagnostic_detachment(self):
        for problem in ('drop', 'read_error', 'base64', 'accounting', 'cap'):
            session, wire = self.session()
            original = wire.request
            bad = dict(stdout=stream(), stderr=stream())
            if problem == 'drop':
                bad['stdout'].update(dropped_bytes=1, truncated=True)
            elif problem == 'read_error':
                bad['stdout']['read_error'] = True
            elif problem == 'base64':
                bad['stdout']['base64'] = '!'
            elif problem == 'accounting':
                bad['stderr']['bytes'] = 1
            else:
                session._sdk_total = 4 * 1024 * 1024
                bad['stdout'] = stream(b'x')
            wire.request = lambda method, params=None, timeout=15: (
                bad if method == 'emulator.output.read' else original(method, params, timeout))
            session._sdk_observer = None
            with self.assertRaises(ValueError):
                session.drain_sdk(.1)
            closed = session.finish(s.Caps(420, 15, 5, 5, .01, 15, 180, 45))
            self.assertFalse(closed['sdk_output_complete'], problem)
            self.assertTrue(closed['lease_released'], problem)

    def verification_fixture(self, *, settle_after_kill):
        exited = threading.Event()
        class Pipe:
            def __init__(self):
                self.closed = False
            def read(self, size):
                exited.wait()
                return b''
            def close(self):
                self.closed = True
        class Process:
            returncode = None
            def __init__(self):
                self.stdout, self.stderr = Pipe(), Pipe()
                self.kills, self.waits = 0, []
            def kill(self):
                self.kills += 1  # Request deliberately does not establish exit.
            def wait(self, timeout=None):
                self.waits.append(timeout)
                if self.kills and settle_after_kill:
                    exited.set()
                if not exited.is_set():
                    raise g.subprocess.TimeoutExpired('host-fixture', timeout)
                self.returncode = -1
                return -1
        process = Process()
        wire = Wire()
        wire.acquire = lambda: dict(state='acquired', tool='android-emulator',
            ticket_id='original-ticket', engine=dict(pid=123,
                created_ticks=116444736000000000 + int(time.time() * 10000000) - 100000))
        wire.settle_wrapper = lambda timeout: True
        return process, wire, exited

    def verification_run(self, settle_after_kill):
        process, wire, exited = self.verification_fixture(settle_after_kill=settle_after_kill)
        baseline = set(threading.enumerate())
        try:
            with mock.patch.object(g, 'JsonLineTransport', return_value=wire), \
                 mock.patch.object(g.subprocess, 'Popen', side_effect=[SimpleNamespace(), process]):
                session = g.GateSession.launch(record())
                session.storage_check = lambda path, timeout: True
                with self.assertRaises(ValueError):
                    session.verify(.01)
            # Same real Supervisor/runner: verify failure aborts preflight; independent
            # owned native stop/release is still safe and must not be skipped.
            session.verify = lambda timeout: (_ for _ in ()).throw(ValueError('fixed'))
            result = self.execute(session, cleanup=lambda: True)
            return result, session, process
        finally:
            exited.set()
            for reader in set(threading.enumerate()) - baseline:
                reader.join(.5)

    def test_unsettled_verification_client_prevents_teardown_success_but_not_owned_vm_close(self):
        result, session, process = self.verification_run(False)
        self.assertFalse(result['automatic_emulator_teardown_verified'], 'KILL_IS_NOT_SETTLEMENT')
        self.assertTrue(result['supervisor']['native_tree_exit_verified'])
        self.assertTrue(result['supervisor']['lease_released'])
        self.assertIn('VERIFICATION_CLIENT_UNSETTLED', result['supervisor']['cleanup_failures'])
        self.assertFalse(result['runtime_result_analysis_performed'])
        self.assertTrue(getattr(session, 'verification_clients', []))
        self.assertEqual(process.kills, 1)
        self.assertGreaterEqual(len(process.waits), 2)
        self.assertLessEqual(process.waits[0], .01)

    def test_verification_cancellation_waits_for_actual_exit_and_both_reader_joins(self):
        result, session, process = self.verification_run(True)
        self.assertTrue(result['automatic_emulator_teardown_verified'])
        self.assertEqual(process.returncode, -1, 'POST_KILL_WAIT_REQUIRED')
        self.assertTrue(process.stdout.closed and process.stderr.closed)
        self.assertTrue(getattr(session, 'verification_clients', []))
        self.assertTrue(all(client.settled for client in session.verification_clients))
        self.assertEqual(process.kills, 1)
        self.assertGreaterEqual(len(process.waits), 2)


class R147CleanupTests(unittest.TestCase):
    """Installed v2 CLI result fixtures, never a deletion or a cleanup receipt."""
    def fixture(self, *, rows=None, mutate=None):
        value = record()
        value['cleanup_transaction'] = 'a' * 32
        root = Path('D:/Temp/navic-task407-gated-runtime/r146')
        item = dict(path=str(root), identity='fixture-root', identity_kind='windows_file_id_128',
            size=0, mtime=1, links=1, directory=True, reparse=False, volume=1, filesystem='NTFS',
            allow_hardlinks=False, allow_leaf_reparse=False, allow_disposable_repository=False,
            disposable_repository_root=None, disposable_repository_identity=None,
            decision='candidate', reason='attributed_generated_output', kind='temporary',
            evidence='Exact task native scratch, registered before launch',
            discovery_source='explicit_registration', git_status='not_tracked_or_outside_repository',
            preexisting=False)
        rows = [item] if rows is None else rows
        calls = []
        def invoke(arguments):
            op = arguments[0]
            calls.append(arguments)
            candidates = sum(row['decision'] == 'candidate' for row in rows)
            result = dict(ok=True, operation=op, transaction_id='a' * 32,
                workspace=str(ROOT), state='open' if op == 'status' and len(calls) == 1 else 'reviewed',
                discovery_coverage=dict(workspace=False, known_or_explicit_roots=[str(root)],
                    explicit_registration=True, usn='unavailable_v2', etw='unavailable_v2',
                    complete_host_coverage=False),
                candidate_count=candidates, candidate_bytes=0, excluded_count=len(rows) - candidates,
                result_counts={}, deleted_bytes=0, warnings=[], errors=[], failure_kind=None)
            if op == 'status':
                result.update(ticket_id=None, ticket_state=None, detailed_state_retained=True,
                    progress=dict(operation=None, phase='idle', processed_items=0,
                        total_items=None, processed_bytes=0, updated_at='fixture', owner_pid=None),
                    state_database='fixture-state', residual_helper_files=[])
                if any(call[0] == 'apply' for call in calls):
                    result.update(state='applied', ticket_id='b' * 64, ticket_state='applied',
                        detailed_state_retained=False, result_counts={'deleted': candidates})
            if op in ('review', 'ticket'):
                result['manifest_sha256'] = 'c' * 64
            if op == 'review':
                result.update(manifest_path='fixture-state', manifest_ref='fixture-manifest',
                    diagnostics=[], diagnostics_truncated=False)
            if op == 'inspect':
                offset = int(arguments[arguments.index('--offset') + 1])
                page = rows[offset:offset + 1000]
                result.update(page_items=page, offset=offset, limit=1000,
                    total_items=len(rows), next_offset=offset + len(page) if offset + len(page) < len(rows) else None)
            if op == 'ticket':
                result.update(state='ticketed', ticket_id='b' * 64, ticket_state='issued')
            if op == 'apply':
                result.update(state='applied', ticket_id='b' * 64, ticket_state='applied',
                    dry_run=False, result_counts={'deleted': candidates}, diagnostics=[],
                    diagnostics_truncated=False, durability_batch_size=256)
            if mutate:
                mutate(op, result)
            return result
        return value, rows, calls, invoke

    def cleanup(self, value, invoke, absent=lambda path: True):
        function = getattr(g, 'cleanup_runtime_scratch', None)
        self.assertTrue(callable(function), 'REAL_CLEANUP_BOUNDARY_MISSING')
        return function(value, invoke=invoke, absent=absent, installation_check=lambda: True)

    def test_supported_cleanup_reviews_all_members_then_separate_ticket_apply_and_absence(self):
        value, rows, calls, invoke = self.fixture()
        absences = []
        self.assertTrue(self.cleanup(value, invoke, lambda path: absences.append(path) or True))
        self.assertEqual([call[0] for call in calls], ['status', 'review', 'inspect', 'ticket', 'apply', 'status'])
        self.assertEqual(calls[2], ['inspect', '--transaction', 'a' * 32, '--offset', '0', '--limit', '1000'])
        self.assertEqual(calls[3], ['ticket', '--transaction', 'a' * 32, '--manifest-sha256', 'c' * 64])
        self.assertEqual(calls[4], ['apply', '--ticket', 'b' * 64])
        self.assertEqual([Path(path) for path in absences], [Path('D:/Temp/navic-task407-gated-runtime/r146')])

    def test_cleanup_inspects_all_exclusions_and_never_tickets_invalid_review(self):
        value, rows, calls, invoke = self.fixture()
        rows += [dict(rows[0], path=rows[0]['path'] + '/excluded-' + str(i),
                      decision='excluded', reason='preexisting_protected', preexisting=True) for i in range(1001)]
        self.assertFalse(self.cleanup(value, invoke))
        self.assertEqual([call[0] for call in calls], ['status', 'review', 'inspect', 'inspect'])
        self.assertEqual(calls[-1][-4:], ['--offset', '1000', '--limit', '1000'])

    def test_cleanup_rejects_unbound_transaction_root_member_pin_and_pagination(self):
        for problem in ('foreign', 'workspace', 'root', 'terminal', 'outside_member', 'tracked',
                'reparse', 'preexisting', 'manifest', 'pagination', 'counts'):
            def mutate(op, result):
                if op == 'status' and problem == 'foreign':
                    result['transaction_id'] = 'd' * 32
                elif op == 'status' and problem == 'workspace':
                    result['workspace'] = 'D:/Other'
                elif op == 'status' and problem == 'root':
                    result['discovery_coverage']['known_or_explicit_roots'] = ['D:/Temp']
                elif op == 'status' and problem == 'terminal':
                    result['state'] = 'applied'
                elif op == 'review' and problem == 'manifest':
                    result['manifest_sha256'] = 'invalid'
                elif op == 'inspect' and problem == 'pagination':
                    result['next_offset'] = 0
                elif op == 'inspect' and problem == 'counts':
                    result['total_items'] += 1
            value, rows, calls, invoke = self.fixture(mutate=mutate)
            if problem == 'outside_member':
                rows[0]['path'] = 'D:/Other'
            elif problem == 'tracked':
                rows[0]['git_status'] = 'git_tracked'
            elif problem == 'reparse':
                rows[0]['reparse'] = True
            elif problem == 'preexisting':
                rows[0]['preexisting'] = True
            self.assertFalse(self.cleanup(value, invoke), problem)
            self.assertNotIn('ticket', [call[0] for call in calls], problem)

    def test_cleanup_pending_failed_apply_or_remaining_root_never_permits_analysis(self):
        for problem in ('partial', 'apply_failed', 'status_failed', 'remaining'):
            def mutate(op, result):
                if op == 'apply' and problem == 'partial':
                    result.update(state='partially_applied', ticket_state='partially_applied')
                elif op == 'apply' and problem == 'apply_failed':
                    result.update(ok=False, failure_kind='state_database_busy', errors=['PRIVATE'])
                elif op == 'status' and len(calls) > 1 and problem == 'status_failed':
                    result['ok'] = False
            value, rows, calls, invoke = self.fixture(mutate=mutate)
            self.assertFalse(self.cleanup(value, invoke, lambda path: problem != 'remaining'), problem)
            self.assertEqual([call[0] for call in calls].count('apply'), 1)

    def test_empty_review_still_consumes_exact_current_transaction_and_verifies_absence(self):
        value, rows, calls, invoke = self.fixture(rows=[])
        self.assertTrue(self.cleanup(value, invoke))
        self.assertEqual([call[0] for call in calls], ['status', 'review', 'inspect', 'ticket', 'apply', 'status'])

    def test_cleanup_installation_or_missing_transaction_fails_before_helper(self):
        function = getattr(g, 'cleanup_runtime_scratch', None)
        self.assertTrue(callable(function), 'REAL_CLEANUP_BOUNDARY_MISSING')
        value, rows, calls, invoke = self.fixture()
        self.assertFalse(function(value, invoke=invoke, absent=lambda path: True, installation_check=lambda: False))
        self.assertFalse(calls)
        value.pop('cleanup_transaction')
        self.assertFalse(self.cleanup(value, invoke))
        self.assertFalse(calls)


    def test_cleanup_helper_cannot_follow_foreign_environment_or_legacy_state_routing(self):
        process = SimpleNamespace(stdout=io.BytesIO(b'{"ok":true}'), stderr=io.BytesIO(),
            returncode=0, wait=lambda timeout=None: 0)
        with mock.patch.dict(g.os.environ, LOCALAPPDATA='E:/Foreign', AGENT_TOOLBELT_HOME='E:/Foreign'), \
             mock.patch.object(g.os.path, 'lexists', return_value=False), \
             mock.patch.object(g.subprocess, 'Popen', return_value=process) as popen:
            g._cleanup_invoke(['status', '--transaction', 'a' * 32])
        self.assertEqual(Path(popen.call_args.kwargs['env']['LOCALAPPDATA']),
                         Path('C:/Users/darka/AppData/Local'), 'CLEANUP_ROUTING_MUST_MATCH_PINNED_ACTIVE')
    def test_cleanup_helper_rejects_unpinned_legacy_state_before_dispatch(self):
        with mock.patch.object(g.os.path, 'lexists', return_value=True), \
             mock.patch.object(g.subprocess, 'Popen', side_effect=ValueError('fixed-native-boundary')) as launch:
            with self.assertRaises(ValueError):
                g._cleanup_invoke(['status', '--transaction', 'a' * 32])
        self.assertFalse(launch.called)

    def test_live_cleanup_adapter_invokes_pinned_helper_and_routes_temp_without_protecting_root(self):
        process = SimpleNamespace(stdout=io.BytesIO(b'{"ok":true}'), stderr=io.BytesIO(),
            returncode=0, wait=lambda timeout=None: 0)
        with mock.patch.object(g.os.path, 'lexists', return_value=False), \
             mock.patch.object(g.subprocess, 'Popen', return_value=process) as popen:
            result = g._cleanup_invoke(['status', '--transaction', 'a' * 32])
        self.assertEqual(result, {'ok': True})
        self.assertEqual(popen.call_args.args[0], [sys.executable, '-B',
            'C:/Users/darka/.claude/skills/transactional-cleanup/scripts/invoke_transactional_cleanup.py',
            'status', '--transaction', 'a' * 32])
        environment = popen.call_args.kwargs['env']
        for key in ('TEMP', 'TMP', 'TMPDIR'):
            self.assertEqual(Path(environment[key]),
                Path('D:/Temp/navic-task407-gated-runtime/r146/cleanup-client-temp'))
        self.assertEqual(environment['PYTHONDONTWRITEBYTECODE'], '1')
        self.assertEqual(environment['GIT_OPTIONAL_LOCKS'], '0')
        self.assertNotIn('AGENT_TOOLBELT_HOME', environment)
        self.assertFalse(g._CLEANUP_CLIENTS)
        self.assertTrue(process.stdout.closed and process.stderr.closed)

    def test_live_cleanup_capture_overflow_is_bounded_drained_settled_and_never_killed(self):
        kills = []
        process = SimpleNamespace(stdout=io.BytesIO(b'x' * (4 * 1024 * 1024 + 1)),
            stderr=io.BytesIO(), returncode=0, wait=lambda timeout=None: 0,
            kill=lambda: kills.append(True))
        with mock.patch.object(g.os.path, 'lexists', return_value=False), \
             mock.patch.object(g.subprocess, 'Popen', return_value=process):
            with self.assertRaises(ValueError):
                g._cleanup_invoke(['review', '--transaction', 'a' * 32])
        self.assertFalse(kills)
        self.assertFalse(g._CLEANUP_CLIENTS)
        self.assertTrue(process.stdout.closed and process.stderr.closed)

    def test_live_missing_cleanup_transaction_blocks_before_any_helper_or_native_launch(self):
        with mock.patch.object(g.subprocess, 'Popen') as popen:
            with self.assertRaises(ValueError):
                r.run_gated_session(s, record(), set(), pins=[],
                    caps=s.Caps(420, 15, 5, 5, 2, 15, 180, 45), watchdog_id='host-fixture')
        self.assertFalse(popen.called)


class R148SchemaTests(unittest.TestCase):
    """Catch private-payload assumptions at the pinned public serializer boundary."""
    fixture = R147CleanupTests.fixture
    cleanup = R147CleanupTests.cleanup
    execute = R147Tests.execute
    session = MigrationTests.session
    api = MigrationTests.api

    def test_inspect_fixture_keys_are_bound_to_pinned_public_serializer_and_cli(self):
        source = Path(g.CLEANUP_SOURCE) / 'agent_toolbelt_transactional_cleanup'
        engine_bytes = (source / 'engine.py').read_bytes()
        self.assertEqual(hashlib.sha256(engine_bytes).hexdigest(),
            'c66cd712bca9c6a50054af023706ce734664a2f7c8bc316341d5752b136c5940')
        self.assertTrue(g.cleanup_installation_valid())
        engine = next(node for node in ast.parse(engine_bytes).body
                      if isinstance(node, ast.ClassDef) and node.name == 'Engine')
        methods = {node.name: node for node in engine.body if isinstance(node, ast.FunctionDef)}
        report = next(node for node in ast.walk(methods['report']) if isinstance(node, ast.Return))
        base_keys = {key.value for key in report.value.keys if isinstance(key, ast.Constant)}
        inspect_call = next(node for node in ast.walk(methods['inspect'])
            if isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute) and node.func.attr == 'report')
        consumed = {arg.arg for arg in methods['report'].args.args}
        exported = base_keys | {kw.arg for kw in inspect_call.keywords if kw.arg not in consumed}
        expected = {'ok', 'operation', 'transaction_id', 'workspace', 'state', 'discovery_coverage',
            'candidate_count', 'candidate_bytes', 'excluded_count', 'result_counts', 'deleted_bytes',
            'warnings', 'errors', 'failure_kind', 'page_items', 'offset', 'limit', 'total_items', 'next_offset'}
        self.assertEqual(exported, expected)
        self.assertNotIn('items', exported)
        self.assertNotIn('manifest_sha256', exported)
        value, rows, calls, invoke = self.fixture()
        self.assertEqual(set(invoke(['inspect', '--offset', '0'])), expected)
        # Static route characterization only: never instantiate Engine or execute extracted AST.
        cli = ast.parse((source / 'cli.py').read_bytes())
        inspect_dispatch = [node for node in ast.walk(cli) if isinstance(node, ast.Assign)
            and isinstance(node.value, ast.Call) and isinstance(node.value.func, ast.Attribute)
            and node.value.func.attr == 'inspect']
        self.assertEqual(len(inspect_dispatch), 1)
        self.assertEqual(ast.unparse(inspect_dispatch[0].value),
            'engine.inspect(args.transaction, args.offset, args.limit, args.decision)')
        outputs = [node for node in ast.walk(cli) if isinstance(node, ast.Call)
            and isinstance(node.func, ast.Name) and node.func.id == 'print']
        self.assertTrue(any(ast.unparse(node) == 'print(json.dumps(result, ensure_ascii=True, indent=2))'
                            for node in outputs))
        wrapper = ast.parse(Path(g.CLEANUP_HELPER).read_bytes())
        self.assertTrue(any(isinstance(node, ast.ImportFrom)
            and node.module == 'agent_toolbelt_transactional_cleanup.cli' for node in ast.walk(wrapper)))
        # Pins authenticate source_root/active routing and ticket/apply integrity code too.

    def test_public_empty_and_multipage_cleanup_precedes_real_take_projection_oracle(self):
        for count in (0, 1001):
            with self.subTest(count=count):
                value, rows, calls, invoke = self.fixture()
                template = rows[0]
                rows[:] = [dict(template, path=str(Path(g.SCRATCH) / ('member-' + str(i))))
                           for i in range(count)]
                session, wire = self.session()
                order = []
                original_take = s.Supervisor.take_instrumentation_output
                original_project, original_pass = r.project_instrumentation, r.original_three_passed
                def external(arguments):
                    order.append(arguments[0])
                    return invoke(arguments)
                def absent(path):
                    self.assertTrue(session._finished['verification_clients_settled'])
                    self.assertTrue(session._finished['lease_released'])
                    order.append('absence')
                    return True
                def take(guardian):
                    order.append('take')
                    return original_take(guardian)
                def project(*args, **kwargs):
                    order.append('projection')
                    return original_project(*args, **kwargs)
                def passed(*args, **kwargs):
                    order.append('oracle')
                    return original_pass(*args, **kwargs)
                with mock.patch.object(s.Supervisor, 'take_instrumentation_output', take), \
                     mock.patch.object(r, 'project_instrumentation', project), \
                     mock.patch.object(r, 'original_three_passed', passed):
                    result = self.execute(session, cleanup=lambda: self.cleanup(value, external, absent))
                self.assertTrue(result['all_three_original_runtime_cases_passed'], 'PUBLIC_PAGE_ITEMS_REQUIRED')
                self.assertEqual(order, ['status', 'review'] + ['inspect'] * (2 if count else 1)
                                 + ['ticket', 'apply', 'status', 'absence', 'take', 'projection', 'oracle'])
                self.assertTrue(all('--decision' not in call for call in calls))
                self.assertEqual(next(call for call in calls if call[0] == 'ticket'),
                    ['ticket', '--transaction', 'a' * 32, '--manifest-sha256', 'c' * 64])

    def test_public_malformed_filtered_changed_manifest_and_residual_block_real_analysis(self):
        problems = ('missing', 'not_list', 'foreign', 'workspace', 'root', 'state', 'count', 'bytes',
            'offset_bool', 'limit_bool', 'total_bool', 'short', 'duplicate', 'unsafe', 'filtered',
            'excluded', 'changed_manifest', 'ticket_digest', 'partial', 'failed', 'residual')
        for problem in problems:
            with self.subTest(problem=problem):
                def mutate(op, result):
                    if op == 'inspect':
                        if problem == 'missing': result.pop('page_items')
                        elif problem == 'not_list': result['page_items'] = {}
                        elif problem == 'foreign': result['transaction_id'] = 'd' * 32
                        elif problem == 'workspace': result['workspace'] = 'D:/Other'
                        elif problem == 'root': result['discovery_coverage']['known_or_explicit_roots'] = ['D:/Temp']
                        elif problem == 'state': result['state'] = 'open'
                        elif problem == 'count': result['candidate_count'] += 1
                        elif problem == 'bytes': result['candidate_bytes'] += 1
                        elif problem == 'offset_bool': result['offset'] = False
                        elif problem == 'limit_bool': result['limit'] = True
                        elif problem == 'total_bool': result['total_items'] = True
                        elif problem in ('short', 'filtered'): result['page_items'] = []
                        elif problem == 'duplicate': result['page_items'] = result['page_items'] * 2
                    if op == 'ticket' and problem == 'changed_manifest':
                        # Actual separate ticket boundary rejects a digest changed since review.
                        result.update(ok=False, failure_kind='review_mismatch', errors=['fixed'])
                    if op == 'ticket' and problem == 'ticket_digest': result['manifest_sha256'] = 'd' * 64
                    if op == 'apply' and problem == 'partial': result['state'] = 'partially_applied'
                    if op == 'apply' and problem == 'failed': result['ok'] = False
                value, rows, calls, invoke = self.fixture(mutate=mutate)
                if problem == 'unsafe': rows[0]['reparse'] = True
                if problem == 'excluded': rows[0].update(decision='excluded', reason='protected')
                session, wire = self.session()
                with mock.patch.object(s.Supervisor, 'take_instrumentation_output',
                        wraps=s.Supervisor.take_instrumentation_output) as take, \
                     mock.patch.object(r, 'project_instrumentation', wraps=r.project_instrumentation) as project:
                    result = self.execute(session, cleanup=lambda: self.cleanup(value, invoke,
                        lambda path: problem != 'residual'))
                self.assertFalse(result['runtime_result_analysis_performed'], problem)
                self.assertFalse(result['all_three_original_runtime_cases_passed'], problem)
                self.assertFalse(take.called, problem)
                self.assertFalse(project.called, problem)
                self.assertLessEqual(sum(call[0] == 'apply' for call in calls), 1)


class R148PublicationTests(unittest.TestCase):
    """The real guardian must not certify in-flight Popen or partly published readers."""
    execute = R147Tests.execute

    def race(self, boundary, *, release_within_allowance=False, interrupt_reader=False):
        process, wire, exited = R147Tests.verification_fixture(self, settle_after_kill=False)
        entered, release, closing, released = (threading.Event() for _ in range(4))
        # For the partial-reader race process exit is real, stdout joins, stderr
        # remains a genuine consumer until the fixture explicitly releases it.
        if boundary == 'reader':
            process.stdout = io.BytesIO()
            process.wait = lambda timeout=None: 0
            process.returncode = 0
        original_request = wire.request
        def request(method, params=None, timeout=15):
            value = original_request(method, params, timeout)
            if method == 'workflow.end': released.set()
            return value
        wire.request = request
        original_start = threading.Thread.start
        def start(thread):
            target = getattr(thread, '_target', None)
            if (boundary == 'reader' and getattr(target, '__name__', '') == '_read'
                    and isinstance(getattr(target, '__self__', None), g._CapturedClient)
                    and thread._args[0] == 1):
                entered.set()
                if not release.wait(3): raise AssertionError('BARRIER_NOT_RELEASED')
                if interrupt_reader: raise RuntimeError('fixed-constructor-interruption')
            return original_start(thread)
        launches = []
        def popen(arguments, **kwargs):
            launches.append(arguments)
            if len(launches) == 1: return SimpleNamespace()
            if boundary == 'popen':
                entered.set()
                if not release.wait(3): raise AssertionError('BARRIER_NOT_RELEASED')
            return process
        outcome, errors, cleanups = [], [], []
        baseline = set(threading.enumerate())
        session = None
        def run():
            try:
                outcome.append(self.execute(session, cleanup=lambda: cleanups.append(True) or True,
                    caps=s.Caps(.5, .5, .2, .2, .2, .5, .1, .1)))
            except BaseException:
                errors.append('caller_failed')
        try:
            with mock.patch.object(g, 'JsonLineTransport', return_value=wire), \
                 mock.patch.object(g.subprocess, 'Popen', side_effect=popen), \
                 mock.patch.object(threading.Thread, 'start', start):
                session = g.GateSession.launch(record())
                session.storage_check = lambda path, timeout: True
                finish = session.finish
                def closing_finish(caps):
                    closing.set()
                    return finish(caps)
                session.finish = closing_finish
                caller = threading.Thread(target=run)
                caller.start()
                self.assertTrue(entered.wait(2), 'VERIFICATION_BOUNDARY_NOT_REACHED')
                self.assertTrue(closing.wait(2), 'REAL_GUARDIAN_DID_NOT_EXPIRE_PREPARATION')
                if release_within_allowance:
                    exited.set()
                    release.set()
                else:
                    self.assertTrue(released.wait(2), 'INDEPENDENT_VM_CLOSE_STRANDED')
                    # Capture closure truth while native creation/publication is
                    # still blocked, not after test fixture recovery.
                    self.assertFalse(session._finished['verification_clients_settled'],
                        'CACHED_CLOSURE_OMITTED_INFLIGHT_OR_PARTIAL_VERIFIER')
                    if boundary == 'reader':
                        self.assertFalse(any(client.settled for client in session.verification_clients),
                            'PARTIAL_READER_SET_IS_NOT_SETTLED')
                    release.set()
                caller.join(3)
                self.assertFalse(caller.is_alive())
            self.assertFalse(errors)
            self.assertEqual(len(outcome), 1)
            result = outcome[0]
            self.assertEqual(result['supervisor']['cause'], 'PREPARATION_CAP')
            self.assertTrue(result['supervisor']['native_tree_exit_verified'])
            self.assertTrue(result['supervisor']['lease_released'])
            self.assertEqual(bool(cleanups), release_within_allowance)
            self.assertEqual(result['automatic_emulator_teardown_verified'], release_within_allowance)
            self.assertFalse(result['runtime_result_analysis_performed'])
            self.assertFalse(result['all_three_original_runtime_cases_passed'])
            self.assertEqual(len(launches), 2, 'NO_REPLAY_OR_LATE_ADMISSION')
            if not release_within_allowance:
                self.assertIn('VERIFICATION_CLIENT_UNSETTLED', result['supervisor']['cleanup_failures'])
            self.assertTrue(session.verification_clients, 'LATE_ACTUAL_CLIENT_MUST_BE_RETAINED')
            exited.set()
            for client in session.verification_clients:
                if not interrupt_reader:
                    self.assertTrue(client.settle(.5), 'ACTUAL_LATE_PROCESS_AND_READER_RECOVERY')
            before = len(launches)
            with self.assertRaises(ValueError): session.verify(.1)
            self.assertEqual(len(launches), before, 'CLOSING_FENCE_MUST_REJECT_NEW_POPEN')
        finally:
            release.set()
            exited.set()
            for thread in set(threading.enumerate()) - baseline:
                thread.join(3)
            self.assertFalse(set(threading.enumerate()) - baseline, 'FIXTURE_THREAD_LEAK')
            if session is not None:
                for client in session.verification_clients:
                    client.settle(.5)
                self.assertTrue(process.stdout.closed)
                # Interrupted registration has no stderr consumer; original owner
                # closes the retained pipe after actual process exit, never fakes join.
                if interrupt_reader: process.stderr.close()
                self.assertTrue(process.stderr.closed)

    def test_capture_constructor_interruption_cannot_lose_an_already_created_process(self):
        process, wire, exited = R147Tests.verification_fixture(self, settle_after_kill=False)
        try:
            with mock.patch.object(g, 'JsonLineTransport', return_value=wire), \
                 mock.patch.object(g.subprocess, 'Popen', side_effect=[SimpleNamespace(), process]) as popen:
                session = g.GateSession.launch(record())
                with mock.patch.object(g._CapturedClient, '__init__', side_effect=RuntimeError('fixed')):
                    with self.assertRaises(ValueError): session.verify(.1)
            if popen.call_count == 2:
                self.assertTrue(any(client.process is process for client in session.verification_clients),
                    'CREATED_PROCESS_LOST_BY_CONSTRUCTOR_INTERRUPTION')
            else:
                self.assertEqual(popen.call_count, 1)
        finally:
            exited.set()
            process.stdout.close()
            process.stderr.close()

    def test_guardian_expiry_during_popen_cannot_cache_teardown_success(self):
        self.race('popen')

    def test_guardian_expiry_during_partial_reader_publication_cannot_cache_success(self):
        self.race('reader')

    def test_inflight_popen_can_actually_settle_within_existing_allowance(self):
        self.race('popen', release_within_allowance=True)

    def test_partial_reader_publication_can_actually_settle_within_existing_allowance(self):
        self.race('reader', release_within_allowance=True)

    def test_interrupted_partial_publication_retains_client_and_fails_honestly(self):
        self.race('reader', interrupt_reader=True)


class R149EOFTests(unittest.TestCase):
    """Catch lost stdout EOF at the actual installed Capture/status/read boundary.

    Only native process creation/polling and control RPCs are fixtures. Importing
    this pinned module has stdlib-only top-level effects; never construct its
    native AdbCommand, Engine or JobClient. No raw frame is persisted.
    """
    session = MigrationTests.session
    api = MigrationTests.api
    execute = R147Tests.execute

    @classmethod
    def setUpClass(cls):
        path = Path('C:/Users/darka/AppData/Local/Tools/llm-coordinator/releases/'
                    'df52528334e1391b/src/llm_coordinator/emulator_adb.py')
        if hashlib.sha256(path.read_bytes()).hexdigest() != (
                '3789b278e21892e745b4b89ad06a86b8dc3a740a8ce545c3b6a3762c7d122f0e'):
            raise AssertionError('INSTALLED_CAPTURE_PIN_CHANGED')
        spec = importlib.util.spec_from_file_location('r149_installed_capture', path)
        cls.installed = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.installed)

    def public_client(self):
        # Bypass ONLY the native constructor, retaining actual status/read methods.
        client = object.__new__(self.installed.AdbCommand)
        client.capture = self.installed.Capture(65536)
        client.done = threading.Event()
        code = [None]
        client.child = SimpleNamespace(poll=lambda: code[0])
        client.identity = dict(pid=123, created_ticks=456)
        client.kind, client.reason, client.error = 'adb', None, None
        client.timeout_seconds, client.timeout_reason = 15, 'Original command budget'
        return client, code

    def read(self, client, limit=65536):
        return dict(command_id='manual', **client.read(limit))

    def source(self, on_line=None):
        session, wire = self.session()
        source = g.command_type(s, session)(session, 'manual', max_bytes=65536,
            on_line=on_line or (lambda line: None))
        observer = s.Observer(r.PACKAGE)
        self.assertTrue(observer.arm(epoch='host-fixture', after_ns=1, source=source))
        return source, observer

    def test_actual_stdout_eof_fails_armed_observer_while_client_and_stderr_live(self):
        client, _ = self.public_client()
        client.capture.consume('stdout', io.BytesIO())
        state = client.status()
        self.assertEqual(state['state'], 'running')
        self.assertTrue(state['streams']['stdout']['eof'])
        self.assertFalse(state['streams']['stderr']['reader_complete'])
        source, observer = self.source()
        source.ingest(self.read(client))
        self.assertTrue(source.feed_lost.is_set(), 'SUPPORTED_STDOUT_EOF_MUST_LOSE_FEED')
        self.assertTrue(observer.unavailable)
        self.assertEqual(source.failure, 'FEED_EOF')
        self.assertFalse(source.done.is_set(), 'EOF_IS_NOT_CLIENT_COMPLETION')
        self.assertFalse(source.settle(.001), 'EOF_CANNOT_AUTHORIZE_CLOSE')

    def test_actual_eof_drains_multiple_reads_and_complete_lines_before_feed_settlement(self):
        client, _ = self.public_client()
        client.capture.consume('stdout', io.BytesIO(b'first\r\nsecond\n'))
        lines, during = [], []
        source, observer = self.source(lambda line: (
            lines.append(line), during.append(source.feed_lost.is_set())))
        for _ in range(6):
            value = self.read(client, 2)
            self.assertGreater(value['stdout']['buffered_bytes'], 0)
            source.ingest(value)
            self.assertFalse(source.feed_lost.is_set(), 'BUFFERED_EOF_MUST_DRAIN_FIRST')
        source.ingest(self.read(client, 2))
        self.assertEqual(lines, [b'first', b'second'])
        self.assertEqual(during, [False, False])
        self.assertTrue(source.feed_lost.is_set(), 'DRAINED_EOF_MUST_SETTLE_FEED')
        self.assertTrue(observer.unavailable)
        self.assertFalse(source.done.is_set())

    def test_actual_partial_line_at_eof_is_failed_not_silently_discarded(self):
        client, _ = self.public_client()
        client.capture.consume('stdout', io.BytesIO(b'ok\npartial'))
        lines = []
        source, observer = self.source(lines.append)
        while True:
            value = self.read(client, 3)
            source.ingest(value)
            if not value['stdout']['buffered_bytes']: break
            self.assertFalse(source.feed_lost.is_set())
        self.assertEqual(lines, [b'ok'])
        self.assertTrue(source.feed_lost.is_set(), 'PARTIAL_EOF_MUST_FAIL_FEED')
        self.assertEqual(source.failure, 'STREAM_FAILED')
        self.assertTrue(observer.unavailable)
        self.assertFalse(source.done.is_set())

    def test_observed_buffered_eof_cannot_be_hidden_by_later_intentional_cancel(self):
        client, code = self.public_client()
        client.capture.consume('stdout', io.BytesIO(b'line\n'))
        source, observer = self.source()
        source.ingest(self.read(client, 2))
        self.assertFalse(source.feed_lost.is_set())
        # Original cancellation bookkeeping may interleave with buffered reads.
        source.cancelled, source.failure = True, 'CANCELLED'
        source.ingest(self.read(client, 3))
        self.assertTrue(source.feed_lost.is_set(), 'CANCEL_MUST_NOT_HIDE_OBSERVED_EOF')
        self.assertTrue(observer.unavailable)
        client.capture.consume('stderr', io.BytesIO())
        client.reason, code[0] = 'cancelled', 1
        client.done.set()
        source.ingest(self.read(client))
        self.assertTrue(source.done.is_set())
        self.assertNotEqual(source.failure, 'CANCELLED')
        self.assertTrue(source.feed_lost.is_set())

    def test_failed_drain_after_observed_eof_is_not_relabelled_intentional_cancellation(self):
        for problem in ('read_error', 'missing_flag', 'drop'):
            with self.subTest(problem=problem):
                client, code = self.public_client()
                client.capture.consume('stdout', io.BytesIO(b'line\n'))
                source, _ = self.source()
                source.ingest(self.read(client, 2))
                self.assertFalse(source.feed_lost.is_set())
                source.cancelled, source.failure = True, 'CANCELLED'
                client.capture.consume('stderr', io.BytesIO())
                code[0], client.reason = 1, 'cancelled'
                client.done.set()
                value = self.read(client)
                if problem == 'read_error': value['stdout']['read_error'] = True
                elif problem == 'missing_flag': value['stdout'].pop('eof')
                else: value['stdout'].update(dropped_bytes=1, truncated=True)
                source.ingest(value)
                self.assertEqual(source.failure, 'STREAM_FAILED', 'CANCEL_CANNOT_HIDE_FAILED_DRAIN')
                self.assertTrue(source.done.is_set(), 'PHYSICAL_ERROR_SETTLEMENT_MUST_SURVIVE')
                self.assertEqual(source.take_output(), (b'', b''))

    def test_actual_empty_live_reads_and_stderr_eof_do_not_guess_stdout_silence(self):
        client, code = self.public_client()
        client.capture.consume('stderr', io.BytesIO())
        source, observer = self.source()
        for _ in range(5): source.ingest(self.read(client))
        code[0] = 0  # Client exit while output reader is still live is not EOF.
        source.ingest(self.read(client))
        self.assertFalse(source.feed_lost.is_set())
        self.assertFalse(observer.unavailable)
        self.assertIsNone(source.failure)
        self.assertFalse(source.done.is_set())

    def test_reader_complete_alone_is_not_stdout_eof(self):
        client, _ = self.public_client()
        source, observer = self.source()
        value = self.read(client)
        value['stdout']['reader_complete'] = True
        source.ingest(value)
        self.assertFalse(source.feed_lost.is_set())
        self.assertFalse(observer.unavailable)
        self.assertIsNone(source.failure)

    def test_actual_eof_before_reader_finally_completes_is_not_rejected_as_impossible(self):
        client, _ = self.public_client()
        source, observer = self.source()
        observed = []
        class ClosingPipe(io.BytesIO):
            def close(pipe):
                # Genuine consume race: EOF published, close not yet returned.
                value = self.read(client)
                observed.append((value['stdout']['eof'], value['stdout']['reader_complete']))
                source.ingest(value)
                super().close()
        client.capture.consume('stdout', ClosingPipe())
        self.assertEqual(observed, [(True, False)])
        self.assertEqual(source.failure, 'FEED_EOF')
        self.assertTrue(observer.unavailable)

    def test_actual_read_error_is_stream_failure_never_fabricated_eof(self):
        client, _ = self.public_client()
        class BrokenPipe(io.BytesIO):
            def read1(pipe, size): raise OSError('PRIVATE_READ_ERROR')
        client.capture.consume('stdout', BrokenPipe())
        value = self.read(client)
        self.assertFalse(value['stdout']['eof'])
        self.assertTrue(value['stdout']['reader_complete'])
        self.assertTrue(value['stdout']['read_error'])
        source, observer = self.source()
        source.ingest(value)
        self.assertEqual(source.failure, 'STREAM_FAILED')
        self.assertTrue(observer.unavailable)
        self.assertFalse(source.done.is_set())

    def test_missing_or_malformed_eof_and_reader_flags_fail_closed(self):
        for name in ('stdout', 'stderr'):
            for flag in ('eof', 'reader_complete'):
                for bad in ('missing', None, 0, 1, 'true', []):
                    with self.subTest(stream=name, flag=flag, case=type(bad).__name__):
                        client, _ = self.public_client()
                        value = self.read(client)
                        if bad == 'missing': value[name].pop(flag)
                        else: value[name][flag] = bad
                        source, observer = self.source()
                        source.ingest(value)
                        self.assertEqual(source.failure, 'STREAM_FAILED', 'NEW_FLAGS_MUST_BE_TYPED')
                        self.assertTrue(observer.unavailable)
                        self.assertFalse(source.done.is_set())

    def test_real_runner_rejects_installed_eof_before_natural_instrument_completion(self):
        session, wire = self.session()
        client, code = self.public_client()
        stderr_reading, stderr_release = threading.Event(), threading.Event()
        class LiveStderr(io.BytesIO):
            def read1(pipe, size):
                stderr_reading.set()
                stderr_release.wait()
                return b''
        client.capture.attach('stderr', LiveStderr())
        def settle_fixture():
            stderr_release.set()
            client.capture.join()
        self.addCleanup(settle_fixture)
        self.assertTrue(stderr_reading.wait(1))
        original_request = wire.request
        observed_eof, attached = threading.Event(), threading.Event()
        instrument, source_ids = [], []
        eof_started = False
        runtime = r._runtime_gate()
        original_type = runtime.command_type
        sources = []
        def command_type(*args):
            cls = original_type(*args)
            ingest = cls.ingest
            def actual_ingest(command, value):
                ingest(command, value)
                if command.is_event_source:
                    if command not in sources: sources.append(command)
                    if value['stdout']['eof'] and not value['stdout']['buffered_bytes']:
                        observed_eof.set()  # Real ingest precedes native fixture completion.
            cls.ingest = actual_ingest
            return cls
        def request(method, params=None, timeout=15):
            nonlocal eof_started
            params = params or {}
            token = params.get('command_id')
            if method == 'emulator.adb.read' and token in instrument and not observed_eof.is_set():
                wire.calls.append((method, copy.deepcopy(params), timeout))
                return dict(command_id=token, **frame('running'))
            if method == 'emulator.adb.read' and token in source_ids:
                wire.calls.append((method, copy.deepcopy(params), timeout))
                if attached.is_set() and not eof_started:
                    client.capture.consume('stdout', io.BytesIO(b'--------- beginning of events\n'))
                    state = client.status()
                    self.assertEqual(state['state'], 'running')
                    self.assertFalse(state['streams']['stderr']['reader_complete'])
                    self.assertTrue(client.capture.readers[0].is_alive())
                    eof_started = True
                return dict(command_id=token, **client.read(7))
            if method == 'emulator.adb.cancel' and token in source_ids:
                client.capture.consume('stdout', io.BytesIO())
                settle_fixture()
                code[0], client.reason = 1, 'cancelled'
                client.done.set()
            value = original_request(method, params, timeout)
            if method == 'emulator.adb.start':
                if wire.instrument.is_set(): instrument.append(value['command_id'])
                elif wire.clients[value['command_id']]['pending']: source_ids.append(value['command_id'])
            return value
        wire.request = request
        original_attach = s.Supervisor.attach_instrumentation
        def attach(guardian, command):
            accepted = original_attach(guardian, command)
            if accepted: attached.set()
            return accepted
        with mock.patch.object(s.Supervisor, 'attach_instrumentation', attach), \
             mock.patch.object(runtime, 'command_type', side_effect=command_type), \
             mock.patch.object(s.Supervisor, 'take_instrumentation_output',
                autospec=True, side_effect=s.Supervisor.take_instrumentation_output) as take, \
             mock.patch.object(r, 'project_instrumentation', wraps=r.project_instrumentation) as project:
            result = self.execute(session, cleanup=lambda: True)
        self.eof_boundary = dict(cause=result['supervisor']['cause'],
            eligible_for_oracle=result['supervisor']['eligible_for_oracle'],
            runtime_result_analysis_performed=result['runtime_result_analysis_performed'],
            automatic_teardown_verified=result['automatic_emulator_teardown_verified'],
            native_tree_exit_verified=result['supervisor']['native_tree_exit_verified'],
            lease_released=result['supervisor']['lease_released'],
            original_take_called=take.called, original_projection_called=project.called,
            exact_clients_closed=set(wire.clients) == wire.closed,
            stderr_reader_joined=not client.capture.readers[0].is_alive())
        self.assertTrue(wire.instrument.is_set())
        self.assertTrue(observed_eof.is_set())
        self.assertEqual(result['supervisor']['cause'], 'OBSERVER_FAILED', 'ORIGINAL_STDOUT_EOF_GUARANTEE')
        self.assertFalse(result['supervisor']['eligible_for_oracle'])
        self.assertFalse(result['runtime_result_analysis_performed'])
        self.assertFalse(result['all_three_original_runtime_cases_passed'])
        self.assertFalse(take.called)
        self.assertFalse(project.called)
        self.assertTrue(result['automatic_emulator_teardown_verified'])
        self.assertTrue(result['supervisor']['native_tree_exit_verified'])
        self.assertTrue(result['supervisor']['lease_released'])
        self.assertEqual(set(wire.clients), wire.closed)
        self.assertTrue(sources[0].feed_lost.is_set())
        self.assertTrue(sources[0].cancelled)
        self.assertTrue(sources[0].done.is_set())
        methods = [method for method, _, _ in wire.calls]
        self.assertEqual(methods.count('workflow.end'), 1)
        self.assertEqual([params['command_id'] for method, params, _ in wire.calls
                          if method == 'emulator.adb.cancel'].count(source_ids[0]), 1)
        self.assertNotIn('manual', json.dumps(result))

    def test_natural_completion_remains_eligible_with_explicit_delivered_eof_profile(self):
        session, _ = self.session()
        result = self.execute(session, cleanup=lambda: True)
        self.assertTrue(result['all_three_original_runtime_cases_passed'])
        self.assertEqual(result['launch_transport_profile'],
            'COORDINATOR_0_16_4_READER_DIAGNOSTICS_37_2_12_EOF_V1')

    test_delivered_active_release_reaches_cli_without_old_version_block = (
        BoundaryTests.test_cli_preserves_sdk_shutdown_failure_even_when_original_oracle_passes)

    def test_current_live_admission_accepts_delivered_release_and_rejects_old_delivery(self):
        current = record()
        current['gate'].update(release='df52528334e1391b', version='0.16.4')
        try:
            self.assertIsNone(r.validate_live_session(current, 'host-fixture'))
        except ValueError:
            self.fail('DELIVERED_EOF_RELEASE_NOT_ADMITTED')
        old = record()
        old['gate'].update(release='e501a86cc7460fa2', version='0.16.0')
        with self.assertRaises(ValueError): r.validate_live_session(old, 'host-fixture')
        self.assertTrue(all('df52528334e1391b' in path for path in g.RUNTIME_PATHS[1:]))

    def test_r162_current_admission_accepts_exact_0_16_4_and_rejects_prior_or_mixed_pairs(self):
        current = record()
        current['gate'].update(release='df52528334e1391b', version='0.16.4')
        try:
            self.assertIsNone(r.validate_live_session(current, 'host-fixture'))
        except ValueError:
            self.fail('R162_CURRENT_RELEASE_NOT_ADMITTED')
        for release, version in (
                ('a0eb32e6e700bb9e', '0.16.3'), ('a84522cf993339ea', '0.16.2'),
                ('5fc7390b4f9b73d3', '0.16.1'), ('e501a86cc7460fa2', '0.16.0'),
                ('df52528334e1391b', '0.16.3'), ('a0eb32e6e700bb9e', '0.16.4'),
                ('a84522cf993339ea', '0.16.4'), ('df52528334e1391b', '0.16.2')):
            with self.subTest(release=release, version=version):
                rejected = record()
                rejected['gate'].update(release=release, version=version)
                with self.assertRaises(ValueError):
                    r.validate_live_session(rejected, 'host-fixture')
        self.assertEqual(len(g.RUNTIME_PATHS), 14)
        self.assertEqual(len(set(g.RUNTIME_PATHS)), 14)
        self.assertTrue(all('df52528334e1391b' in path for path in g.RUNTIME_PATHS[1:]))


class R150VMExitTests(unittest.TestCase):
    """VM loss fails useful work, not independently proved physical settlement.

    Mutations caught: treating vm_exited as unknown settlement or numeric success,
    accepting incomplete/foreign exits, bypassing uncertain RPC ownership, and
    interpreting output before genuine full closure. Native creation/process/VM
    handles are injected; installed observe/Capture/status/read/close are real.
    """
    session = MigrationTests.session
    api = MigrationTests.api
    execute = R147Tests.execute

    @classmethod
    def setUpClass(cls):
        path = Path('C:/Users/darka/AppData/Local/Tools/llm-coordinator/releases/'
                    'df52528334e1391b/src/llm_coordinator/emulator_adb.py')
        if hashlib.sha256(path.read_bytes()).hexdigest() != (
                '3789b278e21892e745b4b89ad06a86b8dc3a740a8ce545c3b6a3762c7d122f0e'):
            raise AssertionError('INSTALLED_CAPTURE_PIN_CHANGED')
        spec = importlib.util.spec_from_file_location('r150_installed.emulator_adb', path)
        cls.installed = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.installed)

    def vm_exit_client(self, code=17, data=b''):
        client = object.__new__(self.installed.AdbCommand)
        trace = []
        class NativeClient:
            returncode = None
            def poll(child): return child.returncode
            def terminate(child):
                trace.append('terminate')
                child.returncode = code
            def wait(child):
                if child.returncode is None: raise AssertionError('EXACT_CLIENT_STILL_RUNNING')
                trace.append('wait')
                return child.returncode
        client.child = NativeClient()
        client.capture = self.installed.Capture(65536)
        client.done = threading.Event()
        client.identity = dict(pid=123, created_ticks=456)
        client.kind, client.reason, client.error = 'adb', None, None
        client.timeout_seconds, client.timeout_reason = 15, 'Original command budget'
        client.deadline = None
        client.cancel_event = SimpleNamespace(Close=lambda: trace.append('close'))
        def vm_handles():
            trace.append('vm_handles')
            return []  # The unavoidable native VM boundary, not a wire reason.
        client.vm_handles = vm_handles
        self.assertEqual(client.status()['state'], 'running')
        client.capture.attach('stdout', io.BytesIO(data))
        client.capture.attach('stderr', io.BytesIO())
        client.monitor = threading.Thread(target=client.observe)
        # Only native imports used by observe are substituted, never its producer.
        with mock.patch.dict(sys.modules, {'win32event': SimpleNamespace(),
                'r150_installed.workflows': SimpleNamespace(NativeWait=lambda: SimpleNamespace())}):
            client.monitor.start()
            client.monitor.join(1)
        self.assertFalse(client.monitor.is_alive())
        self.assertTrue(client.done.is_set())
        self.assertEqual(trace, ['vm_handles', 'terminate', 'wait'])
        state = client.status()
        self.assertEqual(state['state'], 'complete')
        self.assertEqual(state['reason'], 'vm_exited')
        self.assertIsNone(state['error'])
        self.assertIs(state['exit_verified'], True)
        self.assertIs(type(state['exit_code']), int)
        self.assertEqual(state['exit_code'], code)
        self.assertTrue(all(row['eof'] and row['reader_complete'] for row in state['streams'].values()))
        return client, trace

    def test_spawned_ordinary_vm_loss_closes_exact_public_client_and_releases_lease(self):
        session, wire = self.session()
        client, trace = self.vm_exit_client()
        original = wire.request
        def request(method, params=None, timeout=15):
            if method == 'emulator.adb.read':
                return dict(command_id=params['command_id'], **client.read())
            if method == 'emulator.adb.close': client.close()
            return original(method, params, timeout)
        wire.request = request
        cmd = g.command_type(s, session).spawn(['gate-adb', '-s', r.SERIAL, 'get-state'],
            timeout=15, max_bytes=65536)
        self.assertTrue(cmd.done.wait(1))
        physically_settled = cmd.settle(.5)
        self.assertEqual(trace[-1], 'close')
        self.assertEqual(wire.closed, {cmd.command_id})
        self.assertTrue(physically_settled, 'SUPPORTED_VM_LOSS_IS_NOT_UNKNOWN_PHYSICAL_SETTLEMENT')
        self.assertEqual(cmd.failure, 'VM_EXITED')
        self.assertFalse(cmd.cancelled)
        self.assertEqual(cmd.returncode, 17)
        closed = session.finish(s.Caps(420, 15, 5, 5, 2, 15, 180, 45))
        self.assertTrue(closed['native_tree_exit_verified'])
        self.assertTrue(closed['lease_released'])
        self.assertTrue(closed['snapshots_empty'])
        self.assertEqual(trace.count('close'), 1)

    def test_actual_complete_vm_loss_drains_all_public_fragments_before_close(self):
        session, wire = self.session()
        client, _ = self.vm_exit_client(data=b'line\n')
        cmd = g.command_type(s, session)(session, 'manual', max_bytes=65536)
        wire.clients['manual'] = dict(pending=False, cancelled=False, data=b'')
        cmd.ingest(dict(command_id='manual', **client.read(2)))
        self.assertFalse(cmd.done.is_set())
        self.assertFalse(cmd.settle(.001))
        self.assertFalse(wire.closed)
        cmd.ingest(dict(command_id='manual', **client.read(3)))
        self.assertTrue(cmd.done.is_set())
        self.assertTrue(cmd.settle(.5), 'DRAINED_VM_LOSS_MUST_ALLOW_EXACT_CLOSE')
        self.assertEqual(cmd.failure, 'VM_EXITED')
        client.close()

    def test_vm_loss_without_exact_complete_integer_exit_stays_unsettled(self):
        for problem in ('running', 'draining', 'foreign', 'unverified', 'missing_code', 'bool_code'):
            with self.subTest(problem=problem):
                session, wire = self.session()
                client, _ = self.vm_exit_client()
                value = dict(command_id='manual', **client.read())
                if problem in ('running', 'draining'): value['state'] = problem
                elif problem == 'foreign': value['command_id'] = 'foreign'
                elif problem == 'unverified': value['exit_verified'] = False
                else: value['exit_code'] = True if problem == 'bool_code' else None
                cmd = g.command_type(s, session)(session, 'manual', max_bytes=65536)
                cmd.ingest(value)
                self.assertFalse(cmd.done.is_set())
                self.assertFalse(cmd.settle(.001))
                self.assertFalse(wire.closed)
                client.close()  # Corrupt wire proof does not leak the host-only fixture.

    def test_unknown_reason_and_prior_physical_uncertainty_are_not_whitelisted(self):
        for problem in ('unknown_reason', 'prior_uncertainty'):
            with self.subTest(problem=problem):
                session, wire = self.session()
                client, _ = self.vm_exit_client()
                value = dict(command_id='manual', **client.read())
                if problem == 'unknown_reason': value['reason'] = 'not_a_supported_reason'
                cmd = g.command_type(s, session)(session, 'manual', max_bytes=65536)
                wire.clients['manual'] = dict(pending=False, cancelled=False, data=b'')
                if problem == 'prior_uncertainty': cmd.failure = 'CHILD_UNCONFIRMED'
                cmd.ingest(value)
                self.assertEqual(cmd.failure, 'CHILD_UNCONFIRMED')
                self.assertFalse(cmd.settle(.1))
                closed = session.finish(s.Caps(420, 15, 5, 5, .01, 15, 180, 45))
                self.assertFalse(closed['lease_released'])
                self.assertNotIn('emulator.request-stop', [method for method, _, _ in wire.calls])
                client.close()

    def instrument_boundary(self, *, client_code=17, native_code=3221226505, problem=None):
        session, wire = self.session()
        wire.native_code = native_code
        original = wire.request
        instruments, clients, traces, order = [], [], [], []
        def request(method, params=None, timeout=15):
            params = params or {}
            token = params.get('command_id')
            if method == 'emulator.adb.read' and token in instruments:
                value = dict(command_id=token, **clients[0].read())
                if problem == 'capture_error': value['stdout']['read_error'] = True
                if problem == 'malformed_capture': value['stdout'].pop('reader_complete')
                return value
            if method == 'emulator.adb.close' and token in instruments:
                if problem == 'uncertain_close': raise TimeoutError('fixed-native-boundary')
                clients[0].close()
                order.append('exact_client_close')
            value = original(method, params, timeout)
            if method == 'emulator.adb.start' and wire.instrument.is_set():
                instruments.append(value['command_id'])
                client, trace = self.vm_exit_client(code=client_code,
                    data=wire.clients[value['command_id']]['data'])
                clients.append(client)
                traces.append(trace)
                return dict(command_id=value['command_id'], **client.status(), replay=False)
            if method in ('emulator.request-stop', 'emulator.wait-exit', 'workflow.end'):
                order.append(method)
            if method == 'workflow.end':
                value.update(command_failures=True, ok=False, workflow_exit_code=native_code or 1)
            return value
        wire.request = request
        def storage(path, timeout):
            self.assertEqual(set(wire.clients), wire.closed)
            self.assertEqual(order[-1], 'workflow.end')
            order.append('storage')
            return True
        session.storage_check = storage
        def cleanup():
            self.assertTrue(all(session._finished[key] for key in (
                'native_tree_exit_verified', 'lease_released', 'wrapper_settled',
                'verification_clients_settled', 'sdk_output_complete', 'snapshots_empty')))
            self.assertEqual(set(wire.clients), wire.closed)
            self.assertEqual(order[-1], 'storage')
            order.append('cleanup')
            return True
        with mock.patch.object(s.Supervisor, 'take_instrumentation_output',
                autospec=True, side_effect=s.Supervisor.take_instrumentation_output) as take, \
             mock.patch.object(r, 'project_instrumentation', wraps=r.project_instrumentation) as project, \
             mock.patch.object(r, 'original_three_passed', wraps=r.original_three_passed) as oracle:
            result = self.execute(session, cleanup=cleanup)
        cmd = next(command for command in session.clients if command.command_id in instruments)
        boundary = dict(client_exit_code=cmd.returncode, command_failure=cmd.failure,
            cause=result['supervisor']['cause'], eligible_for_oracle=result['supervisor']['eligible_for_oracle'],
            runtime_result_analysis_performed=result['runtime_result_analysis_performed'],
            automatic_teardown_verified=result['automatic_emulator_teardown_verified'],
            native_tree_exit_verified=result['supervisor']['native_tree_exit_verified'],
            lease_released=result['supervisor']['lease_released'],
            native_exit_code=result['supervisor']['native_exit_code'],
            workflow_exit_code=result['supervisor']['workflow_exit_code'],
            command_failures=result['supervisor']['command_failures'],
            original_take_called=take.called, original_projection_called=project.called,
            original_oracle_called=oracle.called, exact_clients_closed=set(wire.clients) == wire.closed,
            installed_public_client_closed=bool(traces and traces[0][-1] == 'close'),
            capture_readers_joined=all(not reader.is_alive() for client in clients for reader in client.capture.readers),
            closure_order=order)
        if not hasattr(self, 'vm_loss_boundaries'): self.vm_loss_boundaries = []
        self.vm_loss_boundaries.append(boundary)
        self.assertTrue(wire.instrument.is_set())
        self.assertEqual(result['supervisor']['cause'], 'INSTRUMENT_FAILED')
        self.assertFalse(result['supervisor']['eligible_for_oracle'])
        self.assertFalse(result['runtime_result_analysis_performed'])
        self.assertFalse(result['all_three_original_runtime_cases_passed'])
        self.assertFalse(take.called or project.called or oracle.called)
        if problem == 'uncertain_close':
            self.assertTrue(session.uncertain)
            self.assertFalse(result['automatic_emulator_teardown_verified'])
            self.assertFalse(result['supervisor']['lease_released'])
            self.assertNotIn('emulator.request-stop', order)
            self.assertNotIn('cleanup', order)
            # Host fixture ends here; uncertain production keeper was NOT replayed.
            clients[0].close()
        else:
            self.assertTrue(result['automatic_emulator_teardown_verified'], 'VM_LOSS_STRANDED_SAFE_CLOSURE')
            self.assertTrue(result['supervisor']['native_tree_exit_verified'])
            self.assertTrue(result['supervisor']['lease_released'])
            self.assertEqual(result['supervisor']['native_exit_code'], native_code)
            self.assertEqual(result['supervisor']['workflow_exit_code'], native_code or 1)
            self.assertTrue(result['supervisor']['command_failures'])
            self.assertEqual(cmd.returncode, client_code)
            self.assertEqual(cmd.failure, 'STREAM_FAILED' if problem else 'VM_EXITED')
            self.assertEqual(order, ['exact_client_close', 'emulator.request-stop',
                'emulator.wait-exit', 'workflow.end', 'storage', 'cleanup'])
            self.assertEqual(traces[0].count('close'), 1)

    def test_real_runner_vm_loss_rejects_oracle_preserves_nonzero_and_closes_before_cleanup(self):
        self.instrument_boundary()

    def test_real_runner_vm_loss_with_zero_client_exit_is_not_natural_app_success(self):
        self.instrument_boundary(client_code=0, native_code=0)

    def test_vm_loss_capture_faults_stay_ineligible_but_exact_closure_survives(self):
        for problem in ('capture_error', 'malformed_capture'):
            with self.subTest(problem=problem): self.instrument_boundary(problem=problem)

    def test_vm_loss_uncertain_close_rpc_never_releases_or_enters_cleanup(self):
        self.instrument_boundary(problem='uncertain_close')


class TransportTests(unittest.TestCase):
    def process(self, reply=True):
        import queue
        frames = queue.Queue()
        frames.put(json.dumps(dict(state='acquired', tool='android-emulator',
            ticket_id='original-ticket', engine=dict(pid=123, created_ticks=456))).encode() + b'\n')
        writes = []
        class Output:
            def readline(self, cap):
                return frames.get(timeout=1)
            def close(self):
                pass
        def write(raw):
            value = json.loads(raw)
            writes.append(value)
            if reply:
                if value['method'] == 'workflow.end':
                    result = dict(id=value['id'], state='released', ok=False, workflow_exit_code=2)
                else:
                    result = dict(id=value['id'], ok=True, result=dict(state='running'))
                frames.put(json.dumps(result).encode() + b'\n')
        def wait(timeout):
            frames.put(b'')
            return 2
        process = SimpleNamespace(stdout=Output(), stderr=io.BytesIO(),
            stdin=SimpleNamespace(write=write, flush=lambda: None, close=lambda: None),
            wait=wait, returncode=2)
        return process, writes

    def test_real_jsonline_envelope_and_terminal_frame_are_distinct(self):
        self.assertTrue(callable(getattr(g, 'JsonLineTransport', None)), 'MISSING_JsonLineTransport')
        process, writes = self.process()
        transport = g.JsonLineTransport(process)
        self.assertEqual(transport.acquire()['ticket_id'], 'original-ticket')
        self.assertEqual(transport.request('emulator.status', {}, .2), dict(state='running'))
        terminal = transport.request('workflow.end', {}, .2)
        self.assertEqual(terminal['state'], 'released')
        self.assertEqual(terminal['workflow_exit_code'], 2)
        self.assertEqual([v['method'] for v in writes], ['emulator.status', 'workflow.end'])
        self.assertTrue(transport.settle_wrapper(.2))

    def test_uncertain_rpc_is_not_replayed_and_keeps_stdin_open_for_recovery(self):
        self.assertTrue(callable(getattr(g, 'JsonLineTransport', None)), 'MISSING_JsonLineTransport')
        process, writes = self.process(reply=False)
        transport = g.JsonLineTransport(process)
        transport.acquire()
        with self.assertRaises(ValueError):
            transport.request('emulator.wait-exit', {}, .02)
        with self.assertRaises(ValueError):
            transport.request('workflow.end', {}, .02)
        self.assertEqual(len(writes), 1)
        self.assertTrue(transport.uncertain)
        process.wait(.1)  # Exact injected host fixture only, no gate operation.
        for reader in transport._readers:
            reader.join(.5)

    def test_r156_response_wait_records_fixed_sdk_first_failure_without_rpc_identity(self):
        process, writes = self.process(reply=False)
        transport = g.JsonLineTransport(process)
        transport.acquire()
        with self.assertRaises(ValueError):
            transport.request('emulator.output.read', {}, .02)
        snapshot = getattr(transport, 'diagnostic_snapshot', lambda: None)()
        self.assertEqual(snapshot, {
            'sample_state': 'SAMPLED', 'first_role': 'SDK_OUTPUT',
            'first_phase': 'RESPONSE_WAIT', 'request_admission_sequence': 1,
            'failure_observation_sequence': 1, 'sequence_saturated': False,
            'source_branch': 'UNAVAILABLE', 'source_failure': 'UNAVAILABLE',
            'source_request_admission_sequence': None,
            'source_failure_observation_sequence': None,
            'receive_side': 'UNAVAILABLE', 'event_source_first_phase': 'UNAVAILABLE',
            'event_source_request_admission_sequence': None,
            'event_source_failure_observation_sequence': None,
               'first_outer_ok': 'UNAVAILABLE', 'first_outer_result': 'UNAVAILABLE',
               'first_outer_error': 'UNAVAILABLE'})
        self.assertEqual(len(writes), 1)
        process.wait(.1)
        for reader in transport._readers:
            reader.join(.5)


class R152FifoNoticeTests(unittest.TestCase):
    """Installed FIFO queue notices are diagnostic-only, never RPC authority."""
    queue_source = Path('C:/Users/darka/AppData/Local/Tools/llm-coordinator/releases/'
                        'df52528334e1391b/engines/agent_toolbelt_gradle_build_gate/queue.py')

    @classmethod
    def setUpClass(cls):
        helper = load('r152_installed_gate_helper',
            'C:/Users/darka/.claude/skills/android-emulator-gate/scripts/invoke_native_gate.py')
        helper.bootstrap()
        import llm_coordinator  # Initializes the installed pinned engine search path.
        import agent_toolbelt_gradle_build_gate.queue as queue
        cls.queue = queue
        cls.assertEqual = unittest.TestCase().assertEqual
        cls.assertEqual(hashlib.sha256(cls.queue_source.read_bytes()).hexdigest(),
                        '94a81e5320b5160ec34aeff3058eb8da67ee8355f43f14db0cfa61eccc163451')

    def installed_notice(self):
        state = dict(schema=1, next_number=1, tickets=[])
        queue = self.queue.TicketQueue.__new__(self.queue.TicketQueue)
        queue.native = SimpleNamespace(process=lambda pid: (1, 1), event=lambda name: 1,
            signal=lambda handle: None, k=SimpleNamespace(CloseHandle=lambda handle: None))
        queue.root = Path('E:/Temp/navic-task407-fifo-r152/in-memory-only-never-created')
        queue.namespace = 'R152FixtureInMemoryOnly'
        queue.ticket = queue.event = None
        queue.on_registered = None
        queue.save = lambda value: None
        queue.prune = lambda value: False
        captured = io.StringIO()
        with mock.patch.object(self.queue, 'NamedMutex', lambda name: contextlib.nullcontext()), \
                mock.patch.object(self.queue, 'read_state', lambda root: state), \
                contextlib.redirect_stderr(captured):
            queue.register()
        return captured.getvalue().encode('utf-8')

    @staticmethod
    def transport(stderr, frames=()):
        transport = g.JsonLineTransport.__new__(g.JsonLineTransport)
        transport.process = SimpleNamespace(stderr=stderr)
        transport._condition = threading.Condition()
        transport._frames = list(frames)
        transport._failed = transport._eof = transport.uncertain = False
        return transport

    def test_actual_installed_queued_notices_preserve_pending_then_acquired_stdout(self):
        class ChunkedReader:
            def __init__(self, chunks):
                self.chunks = list(chunks)
            def read(self, cap):
                return self.chunks.pop(0) if self.chunks else b''
            def close(self):
                pass
        first, second = self.installed_notice(), self.installed_notice()
        self.assertEqual(set(json.loads(first)), {'state', 'ticket', 'ticket_id'})
        self.assertEqual(set(json.loads(second)), {'state', 'ticket', 'ticket_id'})
        transport = self.transport(ChunkedReader((first[:7], first[7:] + second[:3], second[3:])))
        transport._drain_errors()
        self.assertFalse(transport._failed)
        self.assertEqual(transport._frames, [])
        self.assertFalse(transport.uncertain)
        acquired = dict(state='acquired', ticket_id='fixture-only')
        transport._frames.append(acquired)
        self.assertIs(transport.acquire(), acquired)
        after_acquired = self.transport(ChunkedReader((first, second)), (acquired,))
        self.assertIs(after_acquired.acquire(), acquired)
        after_acquired._drain_errors()
        self.assertFalse(after_acquired._failed)
        self.assertFalse(after_acquired.uncertain)

    def test_only_exact_complete_queued_lines_are_nonfatal(self):
        valid = b'{"state":"queued","ticket":1,"ticket_id":"0123456789abcdef0123456789abcdef"}\n'
        invalid = (
            b'{"state":"queued","ticket":true,"ticket_id":"0123456789abcdef0123456789abcdef"}\n',
            b'{"state":"queued","ticket":1,"ticket_id":"0123456789abcdef0123456789abcdef","extra":0}\n',
            b'{"state":"queued","state":"queued","ticket":1,"ticket_id":"0123456789abcdef0123456789abcdef"}\n',
            b'{"state":"queued","ticket":1,"ticket_id":"0123456789ABCDEF0123456789ABCDEF"}\n',
            valid[:-1], b'x' * 4097 + b'\n')
        transport = self.transport(io.BytesIO(valid))
        transport._drain_errors()
        self.assertFalse(transport._failed)
        for raw in invalid:
            with self.subTest(raw_size=len(raw)):
                transport = self.transport(io.BytesIO(raw))
                transport._drain_errors()
                self.assertTrue(transport._failed)
        class BrokenReader:
            def read(self, cap):
                raise OSError('fixture read failure')
            def close(self):
                pass
        transport = self.transport(BrokenReader())
        transport._drain_errors()
        self.assertTrue(transport._failed)

    def test_runtime_paths_pin_actual_installed_queue_producer(self):
        self.assertIn(str(self.queue_source).replace('\\', '/'),
                      tuple(path.replace('\\', '/') for path in g.RUNTIME_PATHS))


class R156CompletionTests(unittest.TestCase):
    """Catch diagnostic misattribution/noninterference at actual production seams."""
    session = MigrationTests.session
    api = MigrationTests.api

    def transport(self, response=None):
        import queue
        out, err = queue.Queue(), queue.Queue()
        writes = []
        class Pipe:
            def __init__(pipe, values):
                pipe.values, pipe.closed = values, threading.Event()
            def read(pipe, cap):
                value = pipe.values.get()
                if isinstance(value, BaseException):
                    raise value
                return value
            readline = read
            def close(pipe):
                pipe.closed.set()
        def write(raw):
            value = json.loads(raw)
            writes.append(value)
            if response is not None:
                reply = response(value)
                if reply is not None:
                    out.put(json.dumps(reply).encode() + b'\n')
        process = SimpleNamespace(stdout=Pipe(out), stderr=Pipe(err),
            stdin=SimpleNamespace(write=write, flush=lambda: None))
        transport = g.JsonLineTransport(process)
        def close():
            out.put(b'')
            err.put(b'')
            for reader in transport._readers:
                reader.join(1)
            self.assertFalse(any(reader.is_alive() for reader in transport._readers), 'FIXTURE_READER_LEAK')
        self.addCleanup(close)
        return transport, writes, out, err

    @staticmethod
    def reply(value):
        return dict(id=value['id'], ok=True, result=dict(state='running'))

    def test_r160_matching_outer_reply_classifies_only_detached_fixed_scalars(self):
        cases = (
            ({}, 'MISSING', 'MISSING', 'MISSING'),
            ({'ok': None, 'result': {}}, 'OTHER', 'DICT', 'MISSING'),
            ({'ok': False, 'result': {}}, 'FALSE', 'DICT', 'MISSING'),
            ({'ok': 1, 'result': {}}, 'OTHER', 'DICT', 'MISSING'),
            ({'ok': True}, 'TRUE', 'MISSING', 'MISSING'),
            ({'ok': True, 'result': None}, 'TRUE', 'OTHER', 'MISSING'),
            ({'ok': True, 'result': []}, 'TRUE', 'OTHER', 'MISSING'),
        )
        for reply_fields, ok_class, result_class, error_class in cases:
            with self.subTest(reply_fields=reply_fields):
                transport, writes, _, _ = self.transport(lambda value: dict(id=value['id'], **reply_fields))
                with self.assertRaises(ValueError):
                    transport.request('emulator.output.read', {}, .2)
                snap = transport.diagnostic_snapshot()
                self.assertEqual((snap['first_role'], snap['first_phase']),
                                 ('SDK_OUTPUT', 'OUTER_REPLY_VALIDATION'))
                self.assertEqual((snap['first_outer_ok'], snap['first_outer_result'], snap['first_outer_error']),
                                 (ok_class, result_class, error_class))
                self.assertEqual(len(writes), 1)
        transport, writes, _, _ = self.transport(lambda value: dict(id=value['id'], ok=True, result={}))
        with mock.patch.object(g, '_classify_first_outer_reply', side_effect=AssertionError('R160_PRIVATE')) as classify:
            self.assertEqual(transport.request('emulator.output.read', {}, .2), {})
        classify.assert_not_called()
        self.assertFalse(transport.uncertain)
        self.assertEqual(transport.diagnostic_snapshot()['first_outer_ok'], 'UNAVAILABLE')
        self.assertEqual(len(writes), 1)

    def test_r160_outer_error_categories_are_closed_and_never_retain_sentinels(self):
        cases = (
            ('[WinError 5] R160_PRIVATE_MESSAGE', 'REMOTE_WIN32_5'),
            ('[WinError 32] R160_PRIVATE_MESSAGE', 'REMOTE_WIN32_32'),
            ('[Errno 13] R160_PRIVATE_MESSAGE', 'REMOTE_ERRNO_13'),
            ('Read cap must be 1 to 1048576 bytes per stream', 'REMOTE_READ_CAP_REJECTED'),
            ('Native requests require a method and parameter object', 'REMOTE_REQUEST_SCHEMA_REJECTED'),
            ('[WinError 5]R160_PRIVATE_MESSAGE', 'REMOTE_OTHER'),
            ('R160_PRIVATE_MESSAGE [WinError 5] ', 'REMOTE_OTHER'),
            ('R160_PRIVATE_MESSAGE', 'REMOTE_OTHER'),
            (None, 'NONSTRING'),
            ({'R160_PRIVATE_TYPE': 'R160_PRIVATE_MESSAGE'}, 'NONSTRING'),
        )
        for error, expected in cases:
            with self.subTest(expected=expected):
                transport, _, _, _ = self.transport(lambda value: dict(id=value['id'], ok=False, result={},
                    error=error, message='R160_PRIVATE_MESSAGE', path='R160_PRIVATE_PATH',
                    url='R160_PRIVATE_URL', cfi='R160_PRIVATE_CFI', ids='R160_PRIVATE_IDS'))
                with self.assertRaises(ValueError):
                    transport.request('emulator.output.read', {}, .2)
                snap = transport.diagnostic_snapshot()
                self.assertEqual(snap['first_outer_error'], expected)
                retained = json.dumps(snap, sort_keys=True)
                for sentinel in ('R160_PRIVATE_MESSAGE', 'R160_PRIVATE_PATH', 'R160_PRIVATE_URL',
                                 'R160_PRIVATE_CFI', 'R160_PRIVATE_IDS'):
                    self.assertNotIn(sentinel, retained)
        transport, _, _, _ = self.transport(lambda value: dict(id=value['id'], ok=False, result={}))
        with self.assertRaises(ValueError):
            transport.request('emulator.output.read', {}, .2)
        self.assertEqual(transport.diagnostic_snapshot()['first_outer_error'], 'MISSING')

    def test_r160_first_nonouter_and_later_reply_cannot_overwrite_or_alias_diagnostics(self):
        transport, _, _, _ = self.transport(lambda value: dict(id=value['id'], ok=False, result={},
            error='R160_PRIVATE_MESSAGE'))
        transport.record_local_failure('CONTROL', 'PRODUCER_REPLY_VALIDATION')
        first = transport.diagnostic_snapshot()
        with self.assertRaises(ValueError):
            transport.request('emulator.output.read', {}, .2)
        second = transport.diagnostic_snapshot()
        self.assertEqual((second['first_outer_ok'], second['first_outer_result'], second['first_outer_error']),
                         ('UNAVAILABLE', 'UNAVAILABLE', 'UNAVAILABLE'))
        first['first_outer_ok'] = 'OTHER'
        self.assertEqual(transport.diagnostic_snapshot(), second)

    def test_r160b_first_outer_snapshot_stays_detached_through_later_uncertainty_and_source_failure(self):
        sentinels = ('R160B_PRIVATE_ERROR', 'R160B_PRIVATE_PATH', 'R160B_PRIVATE_URL',
                     'R160B_PRIVATE_CFI', 'R160B_PRIVATE_ID')
        transport, writes, _, _ = self.transport(lambda value: dict(
            id=value['id'], ok=False, result={}, error='[WinError 5] R160B_PRIVATE_ERROR',
            message='R160B_PRIVATE_ERROR', path='R160B_PRIVATE_PATH', url='R160B_PRIVATE_URL',
            cfi='R160B_PRIVATE_CFI', request_id='R160B_PRIVATE_ID'))
        with self.assertRaises(ValueError):
            transport.request('emulator.output.read', {}, .2)
        first = transport.diagnostic_snapshot()
        expected_outer = ('FALSE', 'DICT', 'REMOTE_WIN32_5')
        expected_sequences = (first['request_admission_sequence'], first['failure_observation_sequence'])
        self.assertEqual((first['first_outer_ok'], first['first_outer_result'], first['first_outer_error']),
                         expected_outer)
        first['first_outer_ok'] = 'OTHER'
        with self.assertRaises(ValueError):
            transport.request('emulator.output.read', {}, .2)
        transport.record_source_failure('SOURCE_PUMP_EXCEPTION', 'PUMP_CALLBACK_FAILURE', 1)
        retained = transport.diagnostic_snapshot()
        self.assertEqual((retained['first_outer_ok'], retained['first_outer_result'], retained['first_outer_error']),
                         expected_outer)
        self.assertEqual((retained['request_admission_sequence'], retained['failure_observation_sequence']),
                         expected_sequences)
        self.assertEqual(retained['source_branch'], 'SOURCE_PUMP_EXCEPTION')
        self.assertEqual(len(writes), 1)
        serialized = json.dumps(retained, sort_keys=True)
        for sentinel in sentinels:
            self.assertNotIn(sentinel, serialized)

    def test_r160_classifier_failure_keeps_original_rejection_and_unavailable_projection(self):
        transport, writes, _, _ = self.transport(lambda value: dict(id=value['id'], ok=False, result={}))
        with mock.patch.object(g, '_classify_first_outer_reply', side_effect=RuntimeError('R160_PRIVATE')):
            with self.assertRaises(ValueError):
                transport.request('emulator.output.read', {}, .2)
        snap = transport.diagnostic_snapshot()
        self.assertEqual((snap['first_role'], snap['first_phase']),
                         ('SDK_OUTPUT', 'OUTER_REPLY_VALIDATION'))
        self.assertEqual((snap['first_outer_ok'], snap['first_outer_result'], snap['first_outer_error']),
                         ('UNAVAILABLE', 'UNAVAILABLE', 'UNAVAILABLE'))
        self.assertEqual(len(writes), 1)
        self.assertNotIn('R160_PRIVATE', json.dumps(snap))

    def test_r156_late_receive_teardown_cannot_rewrite_first_wait_failure(self):
        transport, writes, out, err = self.transport()
        with self.assertRaises(ValueError):
            transport.request('emulator.output.read', {}, .001)
        before = transport.diagnostic_snapshot()
        out.put(b'')
        self.assertTrue(transport.process.stdout.closed.wait(1))
        err.put(b'private-invalid-stderr\n')
        self.assertTrue(transport.process.stderr.closed.wait(1))
        self.assertEqual(transport.diagnostic_snapshot(), before, 'FIRST_RECEIVE_FACT_MUTATED')
        self.assertEqual(before['receive_side'], 'UNAVAILABLE')
        self.assertEqual(len(writes), 1)

    def test_r156c_timeout_rejection_freezes_receive_classification_before_outer_recording(self):
        for name, injected, closed in (
                ('EOF', lambda out, err: out.put(b''), 'stdout'),
                ('STDERR', lambda out, err: err.put(b'private-invalid-stderr\n'), 'stderr')):
            with self.subTest(interval=name):
                transport, writes, out, err = self.transport()
                rejected, release = threading.Event(), threading.Event()
                original_next = transport._next
                def next_with_barrier(deadline):
                    try:
                        return original_next(deadline)
                    except ValueError:
                        rejected.set()
                        self.assertTrue(release.wait(1), 'OUTER_RECORD_RELEASE')
                        raise
                transport._next = next_with_barrier
                outcome = []
                def request():
                    try:
                        transport.request('emulator.output.read', {}, .02)
                    except ValueError:
                        outcome.append('REJECTED')
                worker = threading.Thread(target=request)
                worker.start()
                try:
                    self.assertTrue(rejected.wait(1), 'NEXT_TIMEOUT_REJECTED')
                    injected(out, err)
                    pipe = transport.process.stdout if closed == 'stdout' else transport.process.stderr
                    self.assertTrue(pipe.closed.wait(1), 'RECEIVE_READER_CONSUMED_INJECTED_FAILURE')
                finally:
                    release.set()
                    worker.join(1)
                self.assertFalse(worker.is_alive(), 'OUTER_REQUEST_STILL_RUNNING')
                self.assertEqual(outcome, ['REJECTED'])
                snapshot = transport.diagnostic_snapshot()
                self.assertEqual((snapshot['first_role'], snapshot['first_phase'], snapshot['receive_side']),
                                 ('SDK_OUTPUT', 'RESPONSE_WAIT', 'UNAVAILABLE'))
                self.assertEqual(len(writes), 1)

    def test_r156c_verify_live_status_validation_records_control_without_wire_change(self):
        transport, writes, _, _ = self.transport(lambda value: dict(id=value['id'], ok=True, result={}))
        session, _ = self.session(transport)
        with self.assertRaises(ValueError):
            session.verify_live(.2)
        snapshot = transport.diagnostic_snapshot()
        self.assertEqual((snapshot['first_role'], snapshot['first_phase'],
                          snapshot['request_admission_sequence'], snapshot['failure_observation_sequence']),
                         ('CONTROL', 'PRODUCER_REPLY_VALIDATION', None, 1))
        self.assertEqual(writes, [dict(id='1', method='emulator.status', params={})])
        self.assertFalse(session.uncertain or transport.uncertain)

    def test_r156c_malformed_status_run_session_preserves_terminal_outcome_when_diagnostics_fail(self):
        def execute(diagnostic):
            session, wire = self.session()
            original_request, methods = wire.request, []
            def request(method, params=None, timeout=15, diagnostic_role=None):
                methods.append(method)
                if method == 'emulator.status':
                    return {}
                return original_request(method, params, timeout)
            wire.request = request
            valid = {'sample_state': 'SAMPLED', 'first_role': 'CONTROL',
                'first_phase': 'PRODUCER_REPLY_VALIDATION', 'request_admission_sequence': None,
                'failure_observation_sequence': 1, 'sequence_saturated': False,
                'source_branch': 'UNAVAILABLE', 'source_failure': 'UNAVAILABLE',
                'source_request_admission_sequence': None, 'source_failure_observation_sequence': None,
                'receive_side': 'UNAVAILABLE', 'event_source_first_phase': 'UNAVAILABLE',
                'event_source_request_admission_sequence': None,
                'event_source_failure_observation_sequence': None,
               'first_outer_ok': 'UNAVAILABLE', 'first_outer_result': 'UNAVAILABLE',
               'first_outer_error': 'UNAVAILABLE'}
            if diagnostic == 'WORKING':
                wire.record_local_failure = mock.Mock()
                wire.diagnostic_snapshot = mock.Mock(return_value=valid)
            elif diagnostic == 'THROWING':
                wire.record_local_failure = mock.Mock(side_effect=RuntimeError('R156_PRIVATE'))
                wire.diagnostic_snapshot = mock.Mock(side_effect=RuntimeError('R156_PRIVATE'))
            def cleanup():
                self.assertTrue(session._finished['lease_released'])
                self.assertTrue(session._finished['native_tree_exit_verified'])
                return True
            result = r.run_session(s, record(), set(),
                pins=[dict(path=__file__, sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())],
                caps=s.Caps(.8, .5, .2, .2, .1, .5, 1, 1), watchdog_id='host-fixture',
                gate=session, gated=True, cleanup=cleanup)
            self.assertEqual(methods.count('emulator.status'), 1)
            self.assertEqual(methods[:1], ['emulator.status'])
            self.assertTrue(result['automatic_emulator_teardown_verified'])
            self.assertEqual(set(wire.clients), wire.closed)
            return result
        baseline = execute('WORKING')
        self.assertEqual(baseline['transport_first_failure_diagnostics']['first_role'], 'CONTROL')
        self.assertEqual(baseline['transport_first_failure_diagnostics']['first_phase'],
                         'PRODUCER_REPLY_VALIDATION')
        for diagnostic in ('MISSING', 'THROWING'):
            with self.subTest(diagnostic=diagnostic):
                result = execute(diagnostic)
                self.assertEqual(result['transport_first_failure_diagnostics']['sample_state'], 'SNAPSHOT_GAP')
                for field in ('cause', 'eligible_for_oracle', 'shutdown_verified', 'cleanup_failures'):
                    self.assertEqual(result['supervisor'][field], baseline['supervisor'][field])
                for field in ('automatic_emulator_teardown_verified', 'all_three_original_runtime_cases_passed',
                              'host_cleanup_status', 'runtime_result_analysis_performed'):
                    self.assertEqual(result.get(field), baseline.get(field))

    def test_r156c_close_and_cancel_keep_retained_adb_roles_at_response_identity(self):
        for action, method in (('settle', 'emulator.adb.close'), ('cancel', 'emulator.adb.cancel')):
            for role in ('OTHER_ADB', 'EVENT_SOURCE', 'INSTRUMENTATION'):
                with self.subTest(action=action, role=role):
                    transport, writes, _, _ = self.transport(
                        lambda value: dict(id='foreign', ok=True, result={}))
                    session, _ = self.session(transport)
                    command = g.command_type(s, session)(session, 'manual', max_bytes=65536,
                        on_line=(lambda line: None) if role == 'EVENT_SOURCE' else None,
                        diagnostic_role=role)
                    if action == 'settle':
                        command.done.set()
                        self.assertFalse(command.settle(.2))
                    else:
                        self.assertFalse(command.cancel(.2))
                    snapshot = transport.diagnostic_snapshot()
                    self.assertEqual((snapshot['first_role'], snapshot['first_phase'],
                                      snapshot['request_admission_sequence'], snapshot['failure_observation_sequence']),
                                     (role, 'RESPONSE_IDENTITY', 1, 1))
                    self.assertEqual(writes, [dict(id='1', method=method, params={'command_id': 'manual'})])
                    self.assertTrue(session.uncertain and transport.uncertain)
                    self.assertEqual(command.cancelled, action == 'cancel')

    def test_r156_actual_receive_rejection_categories(self):
        cases = ((b'', 'EOF'), (b'x', 'FRAME_VIOLATION'),
                 (b'x' * (1024 * 1024 + 1), 'FRAME_VIOLATION'),
                 (b'not-json\n', 'JSON_OR_ENVELOPE'), (b'[]\n', 'JSON_OR_ENVELOPE'),
                 (b'{"x":1,"x":2}\n', 'JSON_OR_ENVELOPE'),
                 (OSError('R156_PRIVATE'), 'OTHER'))
        for raw, expected in cases:
            with self.subTest(category=expected):
                transport, writes, out, _ = self.transport()
                out.put(raw)
                self.assertTrue(transport.process.stdout.closed.wait(1))
                with self.assertRaises(ValueError):
                    transport.request('emulator.adb.read', {}, .1, diagnostic_role='EVENT_SOURCE')
                snap = transport.diagnostic_snapshot()
                self.assertEqual((snap['first_phase'], snap['receive_side']), ('RESPONSE_WAIT', expected))
                self.assertEqual(len(writes), 1)
        transport, _, out, _ = self.transport()
        out.put(b'{}\n')
        out.put(b'{}\n')
        self.assertTrue(transport.process.stdout.closed.wait(1))
        with self.assertRaises(ValueError):
            transport.request('emulator.status', {}, .1)
        self.assertEqual(transport.diagnostic_snapshot()['receive_side'], 'REPEATED_FRAME')
        for raw, expected in ((b'private\n', 'STDERR_SCHEMA_OR_LIMIT'),
                              (b'x' * 4097, 'STDERR_SCHEMA_OR_LIMIT'),
                              (OSError('R156_PRIVATE'), 'STDERR_READ_FAILURE')):
            transport, _, _, err = self.transport()
            err.put(raw)
            self.assertTrue(transport.process.stderr.closed.wait(1))
            with self.assertRaises(ValueError):
                transport.request('emulator.status', {}, .1)
            self.assertEqual(transport.diagnostic_snapshot()['receive_side'], expected)

    def test_r156_first_roles_and_propagation_keep_original_wire(self):
        for role, method in (('SDK_OUTPUT', 'emulator.output.read'),
                             ('EVENT_SOURCE', 'emulator.adb.read'),
                             ('INSTRUMENTATION', 'emulator.adb.read')):
            transport, writes, _, _ = self.transport(lambda value: dict(id='foreign', ok=True, result={}))
            session, _ = self.session(transport)
            with self.assertRaises(ValueError):
                session.request(method, {'fixed': 7}, .2, diagnostic_role=role)
            first = transport.diagnostic_snapshot()
            with self.assertRaises(ValueError):
                session.request('emulator.status', {}, .2)
            self.assertEqual(transport.diagnostic_snapshot(), first)
            self.assertEqual((first['first_role'], first['first_phase'],
                              first['request_admission_sequence'], first['failure_observation_sequence']),
                             (role, 'RESPONSE_IDENTITY', 1, 1))
            self.assertEqual(writes, [dict(id='1', method=method, params={'fixed': 7})])
            self.assertTrue(session.uncertain and transport.uncertain)

    def test_r156_original_request_rejection_phases(self):
        for phase in ('PRE_EXISTING_UNCERTAINTY', 'INVALID_EXHAUSTED_BUDGET',
                      'SERIAL_ACQUISITION', 'PRE_WRITE_DEADLINE', 'WRITE', 'FLUSH',
                      'RESPONSE_WAIT', 'RESPONSE_IDENTITY', 'OUTER_REPLY_VALIDATION'):
            with self.subTest(phase=phase):
                reply = (lambda value: dict(id='foreign', ok=True, result={})) if phase == 'RESPONSE_IDENTITY' else (
                    lambda value: dict(id=value['id'], ok=False, result={})) if phase == 'OUTER_REPLY_VALIDATION' else None
                transport, writes, _, _ = self.transport(reply)
                timeout = .001
                if phase == 'PRE_EXISTING_UNCERTAINTY': transport.uncertain = True
                if phase == 'INVALID_EXHAUSTED_BUDGET': timeout = 0
                if phase == 'SERIAL_ACQUISITION':
                    # Original failed acquisition may race another request poisoning the channel.
                    def acquire(**kwargs):
                        transport.uncertain = True
                        return False
                    transport._serial = SimpleNamespace(acquire=acquire)
                if phase == 'WRITE': transport.process.stdin.write = mock.Mock(side_effect=OSError('R156_PRIVATE'))
                if phase == 'FLUSH': transport.process.stdin.flush = mock.Mock(side_effect=OSError('R156_PRIVATE'))
                with contextlib.ExitStack() as stack:
                    if phase == 'PRE_WRITE_DEADLINE':
                        stack.enter_context(mock.patch.object(g.time, 'monotonic', side_effect=[0, 1]))
                    with self.assertRaises(ValueError):
                        transport.request('emulator.status', {}, timeout)
                snap = transport.diagnostic_snapshot()
                self.assertEqual((snap['first_role'], snap['first_phase']), ('CONTROL', phase))
                self.assertEqual(snap['request_admission_sequence'],
                                 None if phase in ('PRE_EXISTING_UNCERTAINTY', 'INVALID_EXHAUSTED_BUDGET',
                                                   'SERIAL_ACQUISITION', 'PRE_WRITE_DEADLINE') else 1)
                self.assertEqual(len(writes), 0 if snap['request_admission_sequence'] is None or phase == 'WRITE' else 1)
                self.assertTrue(transport.uncertain)

    def test_r156_session_original_guard_rejections_are_observed_without_dispatch(self):
        for timeout, uncertain, expected in ((1, True, 'PRE_EXISTING_UNCERTAINTY'),
                                             (True, False, 'INVALID_EXHAUSTED_BUDGET'),
                                             (float('nan'), False, 'INVALID_EXHAUSTED_BUDGET'),
                                             (0, False, 'INVALID_EXHAUSTED_BUDGET')):
            transport, writes, _, _ = self.transport(self.reply)
            session, _ = self.session(transport)
            session.uncertain = uncertain
            with self.assertRaises(ValueError):
                session.request('emulator.status', timeout=timeout)
            snap = transport.diagnostic_snapshot()
            self.assertEqual((snap['first_role'], snap['first_phase'], snap['request_admission_sequence']),
                             ('CONTROL', expected, None))
            self.assertEqual(writes, [])
            self.assertEqual(session.uncertain, uncertain)
            self.assertFalse(transport.uncertain)

    def test_r156_synchronized_admission_and_failure_order_are_not_conflated(self):
        transport, writes, out, _ = self.transport()
        entered, release, rejected = threading.Event(), threading.Event(), threading.Event()
        original = transport.process.stdin.write
        def write(raw):
            original(raw)
            entered.set()
            self.assertTrue(release.wait(1))
        transport.process.stdin.write = write
        errors = []
        def first():
            try: transport.request('emulator.output.read', {}, 2)
            except ValueError: errors.append('FIRST_REJECTED')
        worker = threading.Thread(target=first)
        worker.start()
        try:
            self.assertTrue(entered.wait(1))
            with self.assertRaises(ValueError):
                transport.request('emulator.adb.read', {}, 0, diagnostic_role='EVENT_SOURCE')
            rejected.set()
            snap = transport.diagnostic_snapshot()
            self.assertEqual((snap['first_role'], snap['first_phase'], snap['request_admission_sequence'],
                              snap['failure_observation_sequence']),
                             ('EVENT_SOURCE', 'INVALID_EXHAUSTED_BUDGET', None, 1))
        finally:
            release.set()
            out.put(b'')
            worker.join(2)
        self.assertFalse(worker.is_alive())
        self.assertEqual(errors, ['FIRST_REJECTED'])
        self.assertEqual(transport.diagnostic_snapshot(), snap)
        self.assertEqual(len(writes), 1)

    def test_r156_source_association_does_not_guess_latest_overlapping_event(self):
        transport, _, _, _ = self.transport(self.reply)
        transport.request('emulator.adb.read', {}, .2, diagnostic_role='EVENT_SOURCE')
        transport.request('emulator.adb.read', {}, .2, diagnostic_role='EVENT_SOURCE')
        transport.record_source_failure('SOURCE_PUMP_EXCEPTION', 'PUMP_CALLBACK_FAILURE')
        snap = transport.diagnostic_snapshot()
        self.assertIsNone(snap['source_request_admission_sequence'], 'SHARED_LATEST_IS_NOT_EXACT_SOURCE_ASSOCIATION')
        self.assertIsNone(snap['request_admission_sequence'])

    def test_r156_source_branches_keep_feed_listener_done_order_even_with_broken_hooks(self):
        for branch in ('UNKNOWN_TERMINAL_REASON', 'SOURCE_PUMP_EXCEPTION'):
            for hook in ('WORKING', 'MISSING', 'THROWING'):
                with self.subTest(branch=branch, hook=hook):
                    transport, _, _, _ = self.transport(self.reply)
                    session, _ = self.session(transport)
                    if hook == 'MISSING': session.record_source_failure = None
                    elif hook == 'THROWING':
                        session.record_source_failure = mock.Mock(side_effect=RuntimeError('R156_PRIVATE'))
                    source = g.command_type(s, session)(session, 'manual', max_bytes=65536,
                        on_line=lambda line: None, diagnostic_role='EVENT_SOURCE')
                    order = []
                    source.subscribe_feed_loss(lambda: order.append(('FEED', source.failure, source.done.is_set())))
                    source.subscribe(lambda: order.append(('LISTENER', source.failure, source.done.is_set())))
                    escaped = False
                    try:
                        if branch == 'UNKNOWN_TERMINAL_REASON':
                            value = dict(command_id='manual', **frame(reason='R156_PRIVATE'))
                            value['stdout']['eof'] = False  # Isolate terminal branch from earlier EOF route.
                            source.ingest(value)
                        else:
                            session.request = mock.Mock(side_effect=ValueError('R156_PRIVATE'))
                            source._observe()
                    except BaseException:
                        escaped = True
                    self.assertFalse(escaped, 'DIAGNOSTIC_SUPPRESSED_ORIGINAL_PUMP')
                    self.assertEqual(order, [('FEED', 'CHILD_UNCONFIRMED', False),
                        ('LISTENER', 'CHILD_UNCONFIRMED', branch == 'UNKNOWN_TERMINAL_REASON')])
                    self.assertTrue(source.feed_lost.is_set())
                    self.assertEqual(source.done.is_set(), branch == 'UNKNOWN_TERMINAL_REASON')
                    if branch == 'UNKNOWN_TERMINAL_REASON':
                        self.assertFalse(source._discarded, 'DIAGNOSTIC_HOOK_MUST_NOT_FORCE_ERROR_DRAIN')
                    if hook == 'WORKING':
                        snap = transport.diagnostic_snapshot()
                        self.assertEqual((snap['source_branch'], snap['source_failure']), (branch, 'CHILD_UNCONFIRMED'))
                        self.assertEqual(snap['first_phase'], 'PRODUCER_REPLY_VALIDATION' if branch == 'UNKNOWN_TERMINAL_REASON'
                                         else 'PUMP_CALLBACK_FAILURE')

    def test_r156_source_branch_survives_prior_sdk_failure_and_is_immutable(self):
        transport, _, _, _ = self.transport(lambda value: dict(id='wrong', ok=True, result={}))
        with self.assertRaises(ValueError): transport.request('emulator.output.read', {}, .2)
        first = transport.diagnostic_snapshot()
        session, _ = self.session(transport)
        source = g.command_type(s, session)(session, 'manual', max_bytes=65536,
            on_line=lambda line: None, diagnostic_role='EVENT_SOURCE')
        source._observe()
        snap = transport.diagnostic_snapshot()
        self.assertEqual((snap['first_role'], snap['first_phase']), ('SDK_OUTPUT', 'RESPONSE_IDENTITY'))
        self.assertEqual(snap['source_branch'], 'SOURCE_PUMP_EXCEPTION')
        self.assertGreater(snap['source_failure_observation_sequence'], first['failure_observation_sequence'])
        self.assertIsNone(snap['source_request_admission_sequence'])
        transport.record_source_failure('UNKNOWN_TERMINAL_REASON', 'PRODUCER_REPLY_VALIDATION')
        self.assertEqual(transport.diagnostic_snapshot(), snap)

    def test_r156_local_sdk_and_producer_decode_origins_precede_later_propagation(self):
        for role in ('SDK_OUTPUT', 'EVENT_SOURCE', 'INSTRUMENTATION'):
            with self.subTest(role=role):
                transport, writes, _, _ = self.transport(lambda value: dict(id=value['id'], ok=True, result={}))
                session, _ = self.session(transport)
                if role == 'SDK_OUTPUT':
                    with self.assertRaises(ValueError): session.drain_sdk(.2)
                    self.assertTrue(session._sdk_gap)
                else:
                    value = session.request('emulator.adb.read', {}, .2, diagnostic_role=role)
                    cmd = g.command_type(s, session)(session, 'manual', max_bytes=65536,
                        on_line=(lambda line: None) if role == 'EVENT_SOURCE' else None, diagnostic_role=role)
                    cmd.ingest(value)
                    self.assertEqual(cmd.failure, 'STREAM_FAILED')
                    self.assertFalse(cmd.done.is_set())
                first = transport.diagnostic_snapshot()
                self.assertEqual((first['first_role'], first['first_phase']), (role, 'PRODUCER_REPLY_VALIDATION'))
                self.assertIsNone(first['request_admission_sequence'])
                self.assertFalse(session.uncertain or transport.uncertain, 'LOCAL_DECODE_MUST_NOT_CHANGE_UNCERTAINTY_POLICY')
                session.uncertain = True
                with self.assertRaises(ValueError): session.request('emulator.status', timeout=.2)
                self.assertEqual(transport.diagnostic_snapshot(), first)
                self.assertEqual(len(writes), 1)

    def test_r156_first_event_role_origin_survives_earlier_sdk_and_later_pump_branch(self):
        responses = [dict(stdout=stream(error=True), stderr=stream())]
        def reply(value):
            if responses: return dict(id=value['id'], ok=True, result=responses.pop())
            return dict(id='wrong', ok=True, result={})
        transport, writes, _, _ = self.transport(reply)
        session, _ = self.session(transport)
        with self.assertRaises(ValueError): session.drain_sdk(.2)
        source = g.command_type(s, session)(session, 'manual', max_bytes=65536,
            on_line=lambda line: None, diagnostic_role='EVENT_SOURCE')
        source._observe()
        snap = transport.diagnostic_snapshot()
        self.assertEqual((snap['first_role'], snap['first_phase']), ('SDK_OUTPUT', 'PRODUCER_REPLY_VALIDATION'))
        self.assertEqual((snap.get('event_source_first_phase'),
                          snap.get('event_source_request_admission_sequence'),
                          snap.get('event_source_failure_observation_sequence')),
                         ('RESPONSE_IDENTITY', 2, 2), 'FIRST_EVENT_ROLE_ORIGIN_MISSING')
        self.assertEqual(snap['source_branch'], 'SOURCE_PUMP_EXCEPTION')
        self.assertIsNone(snap['source_request_admission_sequence'])
        self.assertEqual(snap['source_failure_observation_sequence'], 3)
        self.assertEqual(len(writes), 2)
        self.assertFalse(source.done.is_set())
        self.assertTrue(source.feed_lost.is_set())

    def test_r156_spawn_producer_rejection_keeps_role_without_poisoning_successful_wire(self):
        for role in ('EVENT_SOURCE', 'INSTRUMENTATION', 'OTHER_ADB'):
            with self.subTest(role=role):
                transport, writes, _, _ = self.transport(lambda value: dict(id=value['id'], ok=True,
                    result=dict(not_dispatched=True, state='complete')))
                session, _ = self.session(transport)
                with self.assertRaises(ValueError):
                    g.command_type(s, session).spawn(['gate-adb', '-s', r.SERIAL, 'get-state'],
                        timeout=.2, max_bytes=65536, diagnostic_role=role)
                snap = transport.diagnostic_snapshot()
                self.assertEqual((snap['first_role'], snap['first_phase']), (role, 'PRODUCER_REPLY_VALIDATION'))
                self.assertFalse(session.uncertain or transport.uncertain)
                self.assertEqual(len(writes), 1)
                self.assertEqual(session.clients, [])

    def test_r156_callback_origin_is_not_mislabelled_as_producer_or_later_uncertainty(self):
        transport, _, _, _ = self.transport(self.reply)
        session, _ = self.session(transport)
        def callback(line): raise RuntimeError('R156_PRIVATE')
        source = g.command_type(s, session)(session, 'manual', max_bytes=65536,
            on_line=callback, diagnostic_role='EVENT_SOURCE')
        source.ingest(dict(command_id='manual', **frame('running', b'line\n')))
        self.assertEqual(source.failure, 'STREAM_FAILED')
        self.assertTrue(source.feed_lost.is_set())
        self.assertFalse(source.done.is_set())
        snap = transport.diagnostic_snapshot()
        self.assertEqual((snap['first_role'], snap['first_phase']), ('EVENT_SOURCE', 'PUMP_CALLBACK_FAILURE'))
        self.assertEqual(snap['source_branch'], 'UNAVAILABLE')  # ingest caught it; not the pump CHILD branch.

    def test_r156_unknown_terminal_hostile_reason_keeps_original_failure_decisions(self):
        class Reason:
            def __eq__(self, other): raise ValueError('R156_PRIVATE')
            def __str__(self): raise AssertionError('HOSTILE_STR')
            def __repr__(self): raise AssertionError('HOSTILE_REPR')
        class Alias(str): pass
        for reason, expected in (([], 'CHILD_UNCONFIRMED'), (True, 'CHILD_UNCONFIRMED'),
                                 (Reason(), 'STREAM_FAILED'), (Alias('completed'), None)):
            transport, _, _, _ = self.transport(self.reply)
            session, _ = self.session(transport)
            cmd = g.command_type(s, session)(session, 'manual', max_bytes=65536,
                on_line=lambda line: None, diagnostic_role='EVENT_SOURCE')
            value = dict(command_id='manual', **frame(reason=reason))
            value['stdout']['eof'] = False
            # Cancel suppresses later normal feed EOF only for the completed alias.
            if expected is None: cmd.cancelled = True
            cmd.ingest(value)
            self.assertEqual(cmd.failure, expected)
            self.assertTrue(cmd.done.is_set())
            self.assertNotIn('R156_PRIVATE', json.dumps(transport.diagnostic_snapshot()))

    def test_r156_control_and_unknown_roles_never_invoke_hostile_conversion(self):
        class Hostile:
            def __eq__(self, other): raise AssertionError('HOSTILE_EQUALITY')
            def __hash__(self): raise AssertionError('HOSTILE_HASH')
            def __str__(self): raise AssertionError('HOSTILE_STR')
            def __repr__(self): raise AssertionError('HOSTILE_REPR')
        class Alias(str): pass
        for role in (Hostile(), Alias('EVENT_SOURCE'), True, [], 'R156_PRIVATE'):
            transport, writes, _, _ = self.transport(lambda value: dict(id='wrong', ok=True, result={}))
            with self.assertRaises(ValueError):
                transport.request('emulator.status', {}, .2, diagnostic_role=role)
            self.assertEqual(transport.diagnostic_snapshot()['first_role'], 'OTHER')
            self.assertEqual(writes[0]['method'], 'emulator.status')
        transport, writes, _, _ = self.transport(self.reply)
        for branch, phase, seq in ((Hostile(), 'OTHER', None),
                (Alias('SOURCE_PUMP_EXCEPTION'), 'OTHER', None),
                ('SOURCE_PUMP_EXCEPTION', Hostile(), None),
                ('SOURCE_PUMP_EXCEPTION', 'PUMP_CALLBACK_FAILURE', True),
                ('SOURCE_PUMP_EXCEPTION', 'PUMP_CALLBACK_FAILURE', 2 ** 31)):
            escaped = False
            try: transport.record_source_failure(branch, phase, seq)
            except BaseException: escaped = True
            self.assertFalse(escaped, 'HOSTILE_DIAGNOSTIC_ESCAPED')
            self.assertEqual(transport.diagnostic_snapshot()['source_branch'], 'UNAVAILABLE')
        self.assertFalse(transport.uncertain)
        self.assertEqual(writes, [])

    def test_r156_saturation_and_detached_snapshot_do_not_wrap_or_mutate(self):
        transport, _, _, _ = self.transport(lambda value: dict(id='wrong', ok=True, result={}))
        transport._diagnostic_request_sequence = 2 ** 31 - 1
        transport._diagnostic_failure_sequence = 2 ** 31 - 1
        with self.assertRaises(ValueError):
            transport.request('emulator.adb.read', {}, .2, diagnostic_role='INSTRUMENTATION')
        snap = transport.diagnostic_snapshot()
        self.assertEqual((snap['request_admission_sequence'], snap['failure_observation_sequence'],
                          snap['sequence_saturated']), (2 ** 31 - 1, 2 ** 31 - 1, True))
        snap['first_role'] = 'R156_PRIVATE'
        self.assertEqual(transport.diagnostic_snapshot()['first_role'], 'INSTRUMENTATION')


def closed_run(files, evidence=None, only=None):
    """Finite unittest labels/counts only; never emit/persist test traceback strings."""
    suite = unittest.TestSuite()
    pins = []
    for filename in files:
        module = load('closed_' + filename.replace('-', '_').replace('.', '_'), filename)
        tests = unittest.defaultTestLoader.loadTestsFromModule(module)
        if only:
            tests = unittest.TestSuite(unittest.defaultTestLoader.loadTestsFromName(only, module))
        suite.addTests(tests)
        path = ROOT / 'scripts' / filename
        pins.append(dict(path=str(path), sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
    result = unittest.TextTestRunner(stream=io.StringIO(), verbosity=0).run(suite)
    value = dict(tests=result.testsRun, failures=len(result.failures), errors=len(result.errors),
        skipped=len(result.skipped), failure_labels=[test.id() for test, _ in result.failures],
        error_labels=[test.id() for test, _ in result.errors], source_pins=pins)
    if evidence:
        Path(evidence).write_text(json.dumps(value, indent=2), encoding='utf-8')
    print(json.dumps(value))
    return value


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('--evidence')
    parser.add_argument('files', nargs='*', default=['test-task407-runtime-gate.py'])
    args = parser.parse_args()
    result = closed_run(args.files or ['test-task407-runtime-gate.py'], args.evidence)
    raise SystemExit(bool(result['failures'] or result['errors']))
