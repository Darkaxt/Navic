"""Host-only executable regressions. No Android command is present or generated."""
import dataclasses
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import threading
import time
import unittest
from unittest import mock
from contextlib import contextmanager
from types import SimpleNamespace

SOURCE = Path(__file__).with_name('task407-runtime-supervisor.py')
SPEC = importlib.util.spec_from_file_location('task407_runtime_supervisor', SOURCE)
S = None
if SOURCE.exists():
    S = importlib.util.module_from_spec(SPEC)
    sys.modules[SPEC.name] = S
    SPEC.loader.exec_module(S)

PRIVATE = 'PRIVATE_PAYLOAD https://private.invalid/book epubcfi(/6/42) PRIVATE_ID'
PACKAGE = 'example.owned.runtime'
AVD = 'ExactOwnedLab'
SERIAL = 'emulator-5598'


class FakeOwned:
    """Retains an original object, never resolves a PID by name."""
    def __init__(self):
        self.anchor = object()
        self.stopped = threading.Event()
        self.waits = []
        self.kills = 0

    def wait(self, timeout):
        self.waits.append(timeout)
        return self.stopped.wait(timeout)

    def stop_tree(self, timeout):
        self.kills += 1
        self.stopped.set()
        return True


class FixtureAdapter:
    def __init__(self, owned, overrides=None):
        self.owned = owned
        self.overrides = overrides or {}
        self.calls = []
        self.commands = []
        self.snapshots = ['owned_checkpoint']

    def start(self, action, argument, timeout, max_bytes):
        self.calls.append((action, argument))
        if action in self.overrides:
            value = self.overrides[action]
            if isinstance(value, BaseException):
                raise value
            if callable(value):
                return value(timeout, max_bytes)
            return S.Command.completed(*value)
        if action == 'identity':
            return S.Command.completed(0, (AVD + '\r\r\nOK\r\r\n').encode())
        if action == 'snapshot_list':
            names = ''.join('-- ' + name + ' 0\n' for name in self.snapshots)
            return S.Command.completed(0, ('List of snapshots present on all disks:\n' + names + 'OK\n').encode())
        if action == 'snapshot_delete':
            self.snapshots.remove(argument)
            return S.Command.completed(0, b'OK\n')
        if action == 'console_stop':
            self.owned.stopped.set()
            return S.Command.completed(0, b'OK: killing emulator, bye bye\n')
        if action == 'disconnect':
            return S.Command.completed(0)
        if action == 'absence':
            return S.Command.completed(1, b'', ("error: device '" + SERIAL + "' not found").encode())
        raise AssertionError('Unexpected host fixture action')


class HostSignal:
    """Exact host-fixture event: readiness first, release only after admission."""
    def __enter__(self):
        import ctypes
        import uuid
        from ctypes import wintypes
        self.kernel = ctypes.WinDLL('kernel32', use_last_error=True)
        self.kernel.CreateEventW.argtypes = [ctypes.c_void_p, wintypes.BOOL, wintypes.BOOL, wintypes.LPCWSTR]
        self.kernel.CreateEventW.restype = wintypes.HANDLE
        self.kernel.SetEvent.argtypes = [wintypes.HANDLE]
        self.kernel.CloseHandle.argtypes = [wintypes.HANDLE]
        name = 'task407-host-' + uuid.uuid4().hex
        self.handle = self.kernel.CreateEventW(None, True, False, name)
        if not self.handle:
            raise AssertionError('Host fixture event unavailable')
        self.child_wait = (
            'import ctypes; from ctypes import wintypes; k=ctypes.WinDLL("kernel32"); '
            'k.OpenEventW.argtypes=[wintypes.DWORD,wintypes.BOOL,wintypes.LPCWSTR]; '
            'k.OpenEventW.restype=wintypes.HANDLE; '
            'k.WaitForSingleObject.argtypes=[wintypes.HANDLE,wintypes.DWORD]; '
            'k.CloseHandle.argtypes=[wintypes.HANDLE]; '
            'h=k.OpenEventW(0x100000,False,' + repr(name) + '); '
            'assert h; code=k.WaitForSingleObject(h,2000); k.CloseHandle(h); assert code==0; ')
        return self

    def release(self):
        self.kernel.SetEvent(self.handle)

    def __exit__(self, *unused):
        self.release()
        self.kernel.CloseHandle(self.handle)


class RuntimeDiagnosticTests(unittest.TestCase):
    def test_r139_output_observer_sees_both_streams_without_consuming_original_output(self):
        chunks = []
        try:
            child = S.Command.spawn([sys.executable, '-B', '-c',
                'import os; os.write(1,b"INFO | boot-start\\n"); os.write(2,b"ERROR | boot-failed\\n")'],
                timeout=3, max_bytes=4096, tree_owned=True,
                observe_output=lambda index, chunk: chunks.append((index, chunk)))
        except TypeError:
            self.fail('Bounded diagnostic output observer is missing')
        try:
            self.assertTrue(child.settle(3))
            self.assertEqual(child.returncode, 0)
            self.assertIsNone(child.failure)
            self.assertEqual(child.take_output(), (b'INFO | boot-start\n', b'ERROR | boot-failed\n'))
            self.assertEqual(b''.join(data for index, data in chunks if index == 0), b'INFO | boot-start\n')
            self.assertEqual(b''.join(data for index, data in chunks if index == 1), b'ERROR | boot-failed\n')
            self.assertEqual(child.diagnostic_output_errors, 0)
        finally:
            child.cancel(.5)

    def test_r139_diagnostic_callback_failure_cannot_change_command_outcome(self):
        def broken(index, chunk):
            raise RuntimeError('PRIVATE_DIAGNOSTIC_CANARY')
        try:
            child = S.Command.spawn([sys.executable, '-B', '-c', 'print("HOST_LOG",flush=True)'],
                timeout=3, max_bytes=4096, tree_owned=True, observe_output=broken)
        except TypeError:
            self.fail('Non-authoritative diagnostic output observer is missing')
        try:
            self.assertTrue(child.settle(3))
            self.assertEqual(child.returncode, 0)
            self.assertIsNone(child.failure)
            self.assertEqual(child.take_output(), (b'HOST_LOG\r\n', b''))
            self.assertEqual(child.diagnostic_output_errors, 1)
        finally:
            child.cancel(.5)

    def test_r139_original_owned_job_reports_cpu_memory_and_process_counts(self):
        self.assertTrue(callable(getattr(S.Command, 'snapshot_resource_usage', None)),
                        'Owned job resource observation is missing')
        with HostSignal() as gate:
            child = S.Command.spawn([sys.executable, '-B', '-c', gate.child_wait],
                timeout=3, max_bytes=4096, tree_owned=True)
            try:
                usage = child.snapshot_resource_usage()
                self.assertEqual(usage['state'], 'SAMPLED')
                self.assertEqual(set(usage), {'state', 'user_cpu_ms', 'kernel_cpu_ms',
                    'active_processes', 'total_processes', 'page_faults', 'peak_job_memory_bytes'})
                for name, value in usage.items():
                    if name != 'state':
                        self.assertIs(type(value), int)
                        self.assertGreaterEqual(value, 0)
                self.assertGreaterEqual(usage['active_processes'], 1)
                self.assertGreaterEqual(usage['total_processes'], usage['active_processes'])
                self.assertGreater(usage['peak_job_memory_bytes'], 0)
            finally:
                gate.release()
                child.cancel(.5)

    def test_r139_synthetic_and_closed_resource_samples_are_explicit_unknown(self):
        self.assertTrue(callable(getattr(S.Command, 'snapshot_resource_usage', None)),
                        'Explicit unknown resource observation is missing')
        self.assertEqual(S.Command.pending().snapshot_resource_usage(), {'state': 'UNAVAILABLE'})
        child = S.Command.spawn([sys.executable, '-B', '-c', 'pass'],
            timeout=3, max_bytes=4096, tree_owned=True)
        try:
            self.assertTrue(child.settle(3))
            self.assertEqual(child.snapshot_resource_usage(), {'state': 'UNAVAILABLE'})
        finally:
            child.cancel(.5)


class SupervisorTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(S, 'Missing bounded runtime supervision API')
        self.live = []

    def tearDown(self):
        for command in self.live:
            command.cancel(0.5)
            self.assertTrue(command.settle(0.5), 'Host fixture did not terminate')

    def command(self, source, timeout=3, max_bytes=4096):
        command = S.Command.spawn([sys.executable, '-B', '-c', source], timeout=timeout,
                                  max_bytes=max_bytes, tree_owned=True)
        self.live.append(command)
        return command

    def observer(self, source=None, epoch='fresh-host-fixture'):
        observer = S.Observer(PACKAGE)
        self.assertTrue(observer.arm(epoch=epoch, after_ns=100,
                                     source=source if source is not None else S.Command.pending()))
        return observer

    def event_source(self, gate, body, receive=lambda line: None):
        ready = threading.Event()
        def decode(line):
            if line == b'HOST_READY':
                ready.set()
            else:
                receive(line)
        source = S.Command.spawn([sys.executable, '-B', '-c',
                                  'print("HOST_READY", flush=True); ' + gate.child_wait + body],
                                 timeout=3, max_bytes=4096, on_line=decode)
        self.live.append(source)
        self.assertTrue(ready.wait(2), 'Host event source did not become ready')
        return source

    def context(self, instrument=None, adapter_changes=None, caps=None, contract=None, observer=None,
                pre_teardown_observation=None):
        owned = FakeOwned()
        adapter = FixtureAdapter(owned, adapter_changes)
        observer = observer if observer is not None else self.observer()
        instrument = instrument if instrument is not None else S.Command.pending()
        supervisor = S.Supervisor(owned=owned, commands=adapter, instrument=instrument,
                                  observer=observer, avd=AVD, serial=SERIAL,
                                  contract=contract or S.LaunchContract(watchdog_id='6cca814c', no_snapshot_save=True),
                                  caps=caps or S.Caps(instrumentation=.15, command=.06, process_wait=.02,
                                                      stop=.1, settle=.3, snapshot_budget=.2),
                                  pre_teardown_observation=pre_teardown_observation)
        return supervisor, owned, adapter, observer, instrument

    def report(self, supervisor):
        result = supervisor.wait(3)
        self.assertIsNotNone(result, 'Supervisor exceeded host fixture bound')
        return result

    def test_r144_pre_teardown_observation_runs_before_original_cleanup(self):
        observed = []
        instrument = S.Command.pending()
        def capture():
            observed.append((owned.stopped.is_set(), instrument.cancelled, list(adapter.calls)))
        supervisor, owned, adapter, _, _ = self.context(
            instrument, pre_teardown_observation=capture)
        instrument._finish()
        report = self.report(supervisor)
        self.assertEqual(observed, [(False, False, [])])
        self.assertTrue(report.shutdown_verified)
        self.assertTrue(owned.stopped.is_set())
        self.assertIn(('console_stop', None), adapter.calls)

    def test_r144_pre_teardown_observation_exception_keeps_full_cleanup(self):
        supervisor, owned, adapter, _, _ = self.context(
            S.Command.completed(0),
            pre_teardown_observation=lambda: (_ for _ in ()).throw(RuntimeError(PRIVATE)))
        report = self.report(supervisor)
        self.assertTrue(owned.stopped.is_set())
        self.assertIn(('console_stop', None), adapter.calls)
        self.assertIn(('disconnect', None), adapter.calls)
        self.assertTrue(report.shutdown_verified)

    def test_real_never_returning_host_child_is_cancelled(self):
        child = self.command('import threading; threading.Event().wait()')
        before = time.monotonic()
        supervisor, owned, _, _, _ = self.context(child)
        report = self.report(supervisor)
        self.assertLess(time.monotonic() - before, 2)
        self.assertEqual((report.status, report.cause), ('UNKNOWN', 'INSTRUMENTATION_CAP'))
        self.assertTrue(report.shutdown_verified)
        self.assertIsNotNone(child.process.poll())
        self.assertTrue(owned.stopped.is_set())
        self.assertFalse(report.eligible_for_oracle)

    def test_exact_current_own_anr_cancels_instrumentation(self):
        supervisor, _, _, observer, instrument = self.context()
        observer.am_anr('am_anr', '[0,42,' + PACKAGE + ',0,' + PRIVATE + ']',
                        timestamp_ns=101, epoch='fresh-host-fixture')
        report = self.report(supervisor)
        self.assertEqual(report.cause, 'OWN_ANR')
        self.assertEqual(report.status, 'UNKNOWN')
        self.assertTrue(instrument.cancelled)
        self.assertNotIn('PRIVATE', json.dumps(dataclasses.asdict(report)))

    def test_observer_eof_and_failure_are_not_success(self):
        for method in ('closed', 'failed'):
            with self.subTest(method=method):
                supervisor, _, _, observer, instrument = self.context()
                getattr(observer, method)()
                report = self.report(supervisor)
                self.assertEqual(report.cause, 'OBSERVER_FAILED')
                self.assertFalse(report.eligible_for_oracle)
                self.assertTrue(instrument.cancelled)

    def test_stale_foreign_substring_and_malformed_events_never_prove_anr(self):
        for tag, payload, stamp, epoch in (
            ('am_anr', '[0,42,' + PACKAGE + ',0,x]', 100, 'fresh-host-fixture'),
            ('am_anr', '[0,42,' + PACKAGE + ',0,x]', 101, 'historical'),
            ('am_anr', '[0,42,' + PACKAGE + '.other,0,x]', 101, 'fresh-host-fixture'),
            ('am_anr', '[0,42,other.' + PACKAGE + ',0,x]', 101, 'fresh-host-fixture'),
            ('am_anr', '[0,42,' + PACKAGE + ':remote,0,x]', 101, 'fresh-host-fixture'),
            ('other_tag', '[0,42,' + PACKAGE + ',0,x]', 101, 'fresh-host-fixture'),
            ('am_anr', '[0,42,' + PACKAGE + ',x,x]', 101, 'fresh-host-fixture'),
            ('am_anr', '[0,42,' + PACKAGE + ',0,x]\nPRIVATE', 101, 'fresh-host-fixture'),
        ):
            with self.subTest(tag=tag, stamp=stamp):
                observer = self.observer()
                observer.am_anr(tag, payload, timestamp_ns=stamp, epoch=epoch)
                self.assertFalse(observer.own_anr)

    def test_classifier_returns_only_boolean_and_does_not_export_reason(self):
        self.assertIs(S.is_own_am_anr('am_anr', '[0,42,' + PACKAGE + ',0,' + PRIVATE + ']', PACKAGE), True)
        self.assertIs(S.is_own_am_anr('am_anr', '[0,42,foreign.app,0,' + PRIVATE + ']', PACKAGE), False)
        self.assertIs(S.is_own_am_anr('am_anr', '[0,42,' + PACKAGE + ',0,unclosed', PACKAGE), False)

    def test_each_teardown_failure_keeps_independent_stop_and_absence(self):
        for phase in ('identity', 'snapshot_list', 'snapshot_delete', 'console_stop', 'disconnect'):
            with self.subTest(phase=phase):
                supervisor, owned, adapter, _, _ = self.context(
                    S.Command.completed(0), {phase: RuntimeError(PRIVATE)})
                report = self.report(supervisor)
                self.assertEqual(report.status, 'BLOCKED')
                self.assertTrue(owned.stopped.is_set())
                self.assertIn(('absence', None), adapter.calls)
                self.assertIn(('disconnect', None), adapter.calls)
                self.assertNotIn('PRIVATE', json.dumps(dataclasses.asdict(report)))

    def test_console_stop_accepts_only_documented_kill_reply_grammar(self):
        accepted = (b'OK: killing emulator, bye bye\n',
                    b'OK: killing emulator, bye bye\r\nOK\r\n')
        rejected = ((0, b'OK\n'), (0, b''), (0, b'KO\n'),
                    (0, b'OK: killing emulator, bye bye\nextra\n'),
                    (1, b'OK: killing emulator, bye bye\n'),
                    (0, b'OK: killing emulator, bye bye\n', b'private canary'))
        for reply in accepted:
            with self.subTest(reply=reply):
                supervisor, _, _, _, _ = self.context(
                    S.Command.completed(0), {'console_stop': (0, reply)})
                self.assertTrue(self.report(supervisor).shutdown_verified)
        for reply in rejected:
            with self.subTest(reply=reply):
                supervisor, _, _, _, _ = self.context(S.Command.completed(0), {'console_stop': reply})
                report = self.report(supervisor)
                self.assertFalse(report.shutdown_verified)
                self.assertIn('CONSOLE_STOP_UNCONFIRMED', report.cleanup_failures)

    def test_hung_cleanup_command_has_its_own_cap(self):
        def hanging(timeout, max_bytes):
            child = self.command('import threading; threading.Event().wait()', timeout=timeout, max_bytes=max_bytes)
            return child
        supervisor, owned, adapter, _, _ = self.context(S.Command.completed(0), {'identity': hanging})
        report = self.report(supervisor)
        self.assertEqual(report.status, 'BLOCKED')
        self.assertEqual(owned.kills, 1)
        self.assertIn(('absence', None), adapter.calls)

    def test_identity_rejects_name_only_duplicate_padding_and_foreign(self):
        for identity in (AVD + '\n', AVD + '\nOK\nOK\n', 'OtherLab\nOK\n',
                         ' ' + AVD + '\nOK\n', AVD + '\nPRIVATE\nOK\n'):
            with self.subTest(identity=identity):
                supervisor, owned, adapter, _, _ = self.context(
                    S.Command.completed(0), {'identity': (0, identity.encode())})
                report = self.report(supervisor)
                self.assertEqual(report.status, 'BLOCKED')
                self.assertEqual(owned.kills, 1)
                self.assertFalse(any(action in ('snapshot_list', 'snapshot_delete', 'console_stop') for action, _ in adapter.calls))

    def test_invalid_snapshot_name_is_never_deleted(self):
        reply = b'List of snapshots present on all disks:\n-- ../PRIVATE 0\nOK\n'
        supervisor, owned, adapter, _, _ = self.context(S.Command.completed(0), {'snapshot_list': (0, reply)})
        report = self.report(supervisor)
        self.assertEqual(report.status, 'BLOCKED')
        self.assertFalse(any(action == 'snapshot_delete' for action, _ in adapter.calls))
        self.assertTrue(owned.stopped.is_set())

    def test_disconnect_and_strict_absence_are_independent(self):
        supervisor, _, adapter, _, _ = self.context(S.Command.completed(0), {'disconnect': (7,)})
        report = self.report(supervisor)
        self.assertTrue(report.strict_absence)
        self.assertFalse(report.selected_disconnect)
        self.assertIn(('absence', None), adapter.calls)
        self.assertEqual(report.status, 'BLOCKED')

    def test_generic_offline_or_transport_error_is_not_strict_absence(self):
        for error in (b'error: device offline', b'error: no devices/emulators found', b'PRIVATE'):
            supervisor, _, _, _, _ = self.context(S.Command.completed(0), {'absence': (1, b'', error)})
            report = self.report(supervisor)
            self.assertTrue(report.selected_disconnect)
            self.assertFalse(report.strict_absence)
            self.assertFalse(report.eligible_for_oracle)
            self.assertEqual(report.status, 'BLOCKED')

    def test_clean_natural_completion_does_not_replace_three_method_oracle(self):
        # Deliberately not a reader verdict: method oracle belongs in the caller.
        command = S.Command.completed(0, b'HOST_FIXED_PROJECTION')
        supervisor, _, adapter, _, _ = self.context(command)
        report = self.report(supervisor)
        self.assertEqual(report.status, 'NATURAL_COMPLETION')
        self.assertTrue(report.eligible_for_oracle)
        self.assertEqual(supervisor.take_instrumentation_output(), (b'HOST_FIXED_PROJECTION', b''))
        self.assertNotIn('passed_test_count', dataclasses.asdict(report))
        self.assertEqual([action for action, _ in adapter.calls].count('snapshot_list'), 2)
        self.assertEqual([argument for action, argument in adapter.calls if action == 'snapshot_delete'], ['owned_checkpoint'])

    def test_aborted_and_unclean_output_is_discarded(self):
        for changes, expected in ((None, 'UNKNOWN'), ({'absence': (0, b'device')}, 'BLOCKED')):
            supervisor, _, _, _, instrument = self.context(adapter_changes=changes)
            supervisor.abort()
            report = self.report(supervisor)
            self.assertEqual(report.status, expected)
            with self.assertRaises(S.Blocked):
                supervisor.take_instrumentation_output()
            self.assertEqual(instrument.take_output(), (b'', b''))

    def test_output_flood_is_bounded_and_exact_child_stops(self):
        child = self.command('import os; os.write(1, b"x" * 1000000); import threading; threading.Event().wait()', max_bytes=1024)
        supervisor, _, _, _, _ = self.context(child)
        report = self.report(supervisor)
        self.assertEqual(report.status, 'UNKNOWN')
        self.assertFalse(report.eligible_for_oracle)
        self.assertLessEqual(child.buffered_bytes, 1024)
        self.assertIsNotNone(child.process.poll())

    def test_no_save_and_watchdog_are_supplied_not_invented(self):
        for contract in (S.LaunchContract(watchdog_id='', no_snapshot_save=True),
                         S.LaunchContract(watchdog_id='6cca814c', no_snapshot_save=False)):
            supervisor, owned, _, _, _ = self.context(contract=contract)
            report = self.report(supervisor)
            self.assertEqual(report.status, 'BLOCKED')
            self.assertFalse(report.eligible_for_oracle)
            self.assertTrue(owned.stopped.is_set())
            if not contract.no_snapshot_save:
                self.assertFalse(report.shutdown_verified)

    def test_guardian_completes_without_caller_waiting(self):
        supervisor, owned, _, _, _ = self.context()
        self.assertTrue(owned.stopped.wait(2))
        self.assertTrue(self.report(supervisor).shutdown_verified)

    @unittest.skipUnless(sys.platform == 'win32', 'Windows owned-job lifecycle')
    def test_owned_windows_job_closure_stops_exact_child(self):
        child = self.command('import threading; threading.Event().wait()')
        self.live.remove(child)
        try:
            child._job.close()
            self.assertTrue(child.done.wait(.5), 'Owned job closure did not stop its host fixture')
            self.assertIsNotNone(child.process.poll())
            self.assertFalse(child.settle(.1), 'Closed handle alone cannot prove descendant settlement')
        finally:
            if child.process.poll() is None:
                child.process.kill()  # Original retained fixture handle only.
            child.process.wait(timeout=.5)
            self.assertTrue(child.done.wait(1))

    def test_client_only_command_never_acquires_or_terminates_descendant_job(self):
        import inspect
        from unittest import mock
        self.assertIn('tree_owned', inspect.signature(S.Command.spawn).parameters,
                      'Exact-tree cancellation must require explicit ownership')
        with mock.patch.object(S, '_WindowsJob', side_effect=AssertionError('Shared descendant is not owned')):
            client = S.Command.spawn([sys.executable, '-B', '-c',
                                      'import threading; threading.Event().wait()'],
                                     timeout=2, max_bytes=1024, tree_owned=False)
            self.live.append(client)
            self.assertTrue(client.cancel(.5))
            self.assertIsNone(client._job)
            self.assertIsNotNone(client.process.poll())

    def test_live_host_observer_line_cancels_instrument_without_raw_retention(self):
        # The prior full SPEC run intermittently hit INSTRUMENTATION_CAP here.
        # Establish event readiness/admission BEFORE starting the unchanged host
        # instrumentation cap; do not race cold source startup against that cap.
        observer = S.Observer(PACKAGE)
        def receive(line):
            tag, stamp, payload = line.decode('ascii').split('|', 2)
            observer.am_anr(tag, payload, timestamp_ns=int(stamp), epoch='fresh-host-fixture')
        line = 'am_anr|101|[0,42,' + PACKAGE + ',0,' + PRIVATE + ']'
        with HostSignal() as gate:
            source = self.event_source(gate, 'print(' + repr(line) +
                                       ', flush=True); import threading; threading.Event().wait()', receive)
            self.assertTrue(observer.arm(epoch='fresh-host-fixture', after_ns=100, source=source))
            target = self.command('import threading; threading.Event().wait()')
            supervisor, _, _, _, _ = self.context(target, observer=observer,
                caps=S.Caps(instrumentation=2, command=.06, process_wait=.02, stop=.1, settle=.5, snapshot_budget=.2))
            gate.release()
            report = self.report(supervisor)
        self.assertEqual(report.cause, 'OWN_ANR')
        self.assertTrue(source.cancelled)
        self.assertEqual(source.take_output(), (b'', b''))
        self.assertNotIn('PRIVATE', json.dumps(dataclasses.asdict(report)))

    def test_event_reader_failure_aborts_instead_of_becoming_anr_proof(self):
        observer = S.Observer(PACKAGE)
        def broken(line):
            raise RuntimeError(PRIVATE)
        with HostSignal() as gate:
            source = self.event_source(gate, 'print("HOST_EVENT", flush=True); import threading; threading.Event().wait()', broken)
            self.assertTrue(observer.arm(epoch='fresh-host-fixture', after_ns=100, source=source))
            target = self.command('import threading; threading.Event().wait()')
            supervisor, _, _, _, _ = self.context(target, observer=observer,
                caps=S.Caps(instrumentation=2, command=.06, process_wait=.02, stop=.1, settle=.5, snapshot_budget=.2))
            gate.release()
            report = self.report(supervisor)
        self.assertEqual(report.cause, 'OBSERVER_FAILED')
        self.assertFalse(report.eligible_for_oracle)
        self.assertNotIn('PRIVATE', json.dumps(dataclasses.asdict(report)))

    def preparing(self):
        self.assertTrue('preparation' in S.Caps.__dataclass_fields__, 'Missing independent preflight host cap')
        owned = FakeOwned()
        adapter = FixtureAdapter(owned)
        observer = S.Observer(PACKAGE)
        supervisor = S.Supervisor(owned=owned, commands=adapter, observer=observer,
                                  avd=AVD, serial=SERIAL, contract=S.LaunchContract('6cca814c', True),
                                  caps=S.Caps(.15, .06, .02, .1, .3, .2, preparation=.08))
        return supervisor, owned, observer

    def test_preflight_hang_is_guarded_before_instrumentation_exists(self):
        supervisor, owned, _ = self.preparing()
        self.assertTrue(owned.stopped.wait(2))
        report = self.report(supervisor)
        self.assertEqual((report.status, report.cause), ('UNKNOWN', 'PREPARATION_CAP'))
        self.assertTrue(report.shutdown_verified)

    def test_late_instrument_attachment_is_rejected_and_cancelled(self):
        supervisor, _, _ = self.preparing()
        self.report(supervisor)
        child = self.command('import threading; threading.Event().wait()')
        self.assertFalse(supervisor.attach_instrumentation(child))
        self.assertTrue(child.cancelled)
        self.assertIsNotNone(child.process.poll())

    def test_attached_instrument_uses_its_independent_cap(self):
        supervisor, _, observer = self.preparing()
        self.assertTrue(observer.arm(epoch='fresh-host-fixture', after_ns=100, source=S.Command.pending()))
        child = S.Command.completed(0, b'HOST_ONLY')
        self.assertTrue(supervisor.attach_instrumentation(child))
        self.assertTrue(self.report(supervisor).eligible_for_oracle)
        self.assertEqual(supervisor.take_instrumentation_output(), (b'HOST_ONLY', b''))

    def test_anr_observed_during_teardown_withholds_natural_result(self):
        gate = threading.Event()
        box = {}
        def identity(timeout, max_bytes):
            self.assertTrue(gate.wait(.5))
            box['observer'].am_anr('am_anr', '[0,42,' + PACKAGE + ',0,' + PRIVATE + ']',
                                   timestamp_ns=101, epoch='fresh-host-fixture')
            return S.Command.completed(0, (AVD + '\nOK\n').encode())
        supervisor, _, _, observer, _ = self.context(S.Command.completed(0), {'identity': identity})
        box['observer'] = observer
        gate.set()
        report = self.report(supervisor)
        self.assertEqual((report.status, report.cause), ('UNKNOWN', 'OWN_ANR'))
        self.assertFalse(report.eligible_for_oracle)

    def test_bound_observer_command_eof_cancels_real_instrument(self):
        observer = S.Observer(PACKAGE)
        with HostSignal() as gate:
            source = self.event_source(gate, 'pass')
            self.assertTrue(observer.arm(epoch='fresh-host-fixture', after_ns=100, source=source))
            child = self.command('import threading; threading.Event().wait()')
            supervisor, _, _, _, _ = self.context(child, observer=observer,
                caps=S.Caps(instrumentation=2, command=.06, process_wait=.02, stop=.1, settle=.5, snapshot_budget=.2))
            gate.release()
            report = self.report(supervisor)
        self.assertEqual(report.cause, 'OBSERVER_FAILED')
        self.assertIsNotNone(child.process.poll())

    def test_bound_observer_is_stopped_without_inventing_observer_failure(self):
        source = S.Command.spawn([sys.executable, '-B', '-c', 'import threading; threading.Event().wait()'],
                                 timeout=3, max_bytes=1024, on_line=lambda line: None)
        self.live.append(source)
        observer = self.observer(source)
        owned = FakeOwned()
        supervisor = S.Supervisor(owned=owned, commands=FixtureAdapter(owned),
                                  instrument=S.Command.completed(0), observer=observer,
                                  avd=AVD, serial=SERIAL,
                                  contract=S.LaunchContract('6cca814c', True),
                                  caps=S.Caps(.15, .06, .02, .1, .3, .2))
        report = self.report(supervisor)
        self.assertTrue(report.eligible_for_oracle)
        self.assertFalse(observer.unavailable)
        self.assertIsNotNone(source.process.poll())

    def test_real_command_deadline_operates_without_supervisor(self):
        child = self.command('import threading; threading.Event().wait()', timeout=.06)
        self.assertTrue(child.settle(2))
        self.assertEqual(child.failure, 'COMMAND_CAP')
        self.assertIsNotNone(child.process.poll())

    def test_late_published_cleanup_child_is_cancelled(self):
        release = threading.Event()
        published = threading.Event()
        box = {}
        def delayed(timeout, max_bytes):
            release.wait(1)
            child = self.command('import threading; threading.Event().wait()', timeout=1)
            box['child'] = child
            published.set()
            return child
        supervisor, _, _, _, _ = self.context(S.Command.completed(0), {'identity': delayed})
        self.assertEqual(self.report(supervisor).status, 'BLOCKED')
        release.set()
        self.assertTrue(published.wait(1))
        self.assertTrue(box['child'].done.wait(2))
        self.assertTrue(box['child'].cancelled)
        self.assertIsNotNone(box['child'].process.poll())

    def test_unresponsive_owned_wait_is_bounded_and_does_not_skip_disconnect(self):
        release = threading.Event()
        owned = FakeOwned()
        owned.wait = lambda timeout: release.wait(2)
        adapter = FixtureAdapter(owned)
        supervisor = S.Supervisor(owned=owned, commands=adapter, instrument=S.Command.completed(0),
                                  observer=self.observer(epoch='fresh'),
                                  avd=AVD, serial=SERIAL,
                                  contract=S.LaunchContract('6cca814c', True),
                                  caps=S.Caps(.15, .06, .02, .1, .3, .2))
        try:
            report = self.report(supervisor)
            self.assertEqual(report.status, 'BLOCKED')
            self.assertEqual(owned.kills, 1)
            self.assertIn(('disconnect', None), adapter.calls)
            self.assertIn(('absence', None), adapter.calls)
        finally:
            release.set()

    def test_unrelated_host_child_is_not_cancelled(self):
        foreign = self.command('import threading; threading.Event().wait()')
        target = self.command('import threading; threading.Event().wait()')
        supervisor, _, _, _, _ = self.context(target)
        self.assertEqual(self.report(supervisor).status, 'UNKNOWN')
        self.assertIsNone(foreign.process.poll())
        self.assertFalse(foreign.cancelled)

    def test_original_popen_wrapper_uses_retained_handle_not_a_pid_lookup(self):
        self.assertTrue(hasattr(S, 'OwnedProcess'), 'Missing original Popen ownership adapter')
        child = self.command('import threading; threading.Event().wait()')
        wrapper = S.OwnedProcess(child.process, lambda original, timeout: child.cancel(timeout))
        self.assertIs(wrapper.anchor, child.process)
        self.assertFalse(wrapper.wait(.001))
        self.assertTrue(wrapper.stop_tree(.5))
        self.assertTrue(wrapper.wait(.5))

    def require_arm_api(self):
        self.assertTrue(hasattr(S.Observer, 'arm'), 'Missing explicit UNARMED preparation and one-shot admission')

    def test_unarmed_preparation_is_bounded_and_cannot_admit_instrumentation(self):
        self.require_arm_api()
        observer = S.Observer(PACKAGE)
        owned = FakeOwned()
        supervisor = S.Supervisor(owned=owned, commands=FixtureAdapter(owned), observer=observer,
                                  avd=AVD, serial=SERIAL, contract=S.LaunchContract('5f2009d4', True),
                                  caps=S.Caps(.2, .06, .02, .2, .5, .2, preparation=.06))
        rejected = S.Command.pending()
        self.assertFalse(supervisor.attach_instrumentation(rejected))
        self.assertTrue(rejected.cancelled)
        self.assertEqual(self.report(supervisor).cause, 'PREPARATION_CAP')
        self.assertFalse(observer.armed)

    def test_arming_requires_live_source_once_and_does_not_create_a_clock(self):
        self.require_arm_api()
        observer = S.Observer(PACKAGE)
        self.assertFalse(observer.arm(epoch='device-observation', after_ns=100, source=None))
        self.assertFalse(observer.armed)
        source = S.Command.pending()
        self.assertTrue(observer.arm(epoch='device-observation', after_ns=100, source=source))
        replacement = S.Command.pending()
        self.assertFalse(observer.arm(epoch='replacement', after_ns=200, source=replacement))
        self.assertTrue(replacement.cancelled)
        self.assertIs(observer.source, source)
        observer.stop(.2)

    def test_initial_instrument_without_armed_source_is_cancelled_not_eligible(self):
        self.require_arm_api()
        owned = FakeOwned()
        instrument = S.Command.pending()
        supervisor = S.Supervisor(owned=owned, commands=FixtureAdapter(owned), observer=S.Observer(PACKAGE),
                                  instrument=instrument, avd=AVD, serial=SERIAL,
                                  contract=S.LaunchContract('5f2009d4', True), caps=S.Caps(.2, .06, .02, .2, .5, .2))
        report = self.report(supervisor)
        self.assertTrue(instrument.cancelled)
        self.assertFalse(report.eligible_for_oracle)
        self.assertEqual(report.cause, 'OBSERVER_NOT_ADMITTED')

    def test_real_source_stdout_eof_is_loss_even_when_source_process_hangs(self):
        observer = S.Observer(PACKAGE)
        with HostSignal() as gate:
            source = self.event_source(gate, 'import os, threading; os.close(1); threading.Event().wait()')
            self.assertTrue(observer.arm(epoch='fresh-host-fixture', after_ns=100, source=source))
            supervisor, _, _, _, _ = self.context(observer=observer, caps=S.Caps(1, .06, .02, .2, .5, .2))
            gate.release()
            report = self.report(supervisor)
        self.assertEqual(report.cause, 'OBSERVER_FAILED')
        self.assertFalse(report.eligible_for_oracle)
        self.assertTrue(source.feed_lost.is_set())
        self.assertIsNotNone(source.process.poll())

    def test_real_running_instrument_cancel_never_becomes_natural_completion(self):
        child = self.command('print("FIXED_HOST_DATA", flush=True); import threading; threading.Event().wait()')
        supervisor, _, _, _, _ = self.context(child, caps=S.Caps(2, .06, .02, .2, .5, .2))
        self.assertTrue(child.cancel(.5))
        report = self.report(supervisor)
        self.assertFalse(report.eligible_for_oracle)
        self.assertNotEqual(report.cause, 'NATURAL_COMPLETION')
        with self.assertRaises(S.Blocked):
            supervisor.take_instrumentation_output()

    def test_exited_original_root_still_requires_owned_tree_settlement(self):
        owned = FakeOwned()
        owned.stopped.set()
        supervisor = S.Supervisor(owned=owned, commands=FixtureAdapter(owned),
                                  observer=self.observer(epoch='fresh'),
                                  instrument=S.Command.completed(0), avd=AVD, serial=SERIAL,
                                  contract=S.LaunchContract('5f2009d4', True), caps=S.Caps(.2, .06, .02, .2, .5, .2))
        self.assertTrue(self.report(supervisor).shutdown_verified)
        self.assertEqual(owned.kills, 1, 'Root termination is not owned-tree settlement')

    def test_root_terminal_without_tree_settlement_stays_blocked_after_strict_absence(self):
        owned = FakeOwned()
        owned.stopped.set()
        owned.stop_tree = lambda timeout: False
        adapter = FixtureAdapter(owned)
        supervisor = S.Supervisor(owned=owned, commands=adapter, instrument=S.Command.completed(0),
                                  observer=self.observer(epoch='fresh'), avd=AVD, serial=SERIAL,
                                  contract=S.LaunchContract('6cca814c', True),
                                  caps=S.Caps(.2, .06, .02, .2, .5, .2))
        report = self.report(supervisor)
        self.assertTrue(report.original_terminal)
        self.assertTrue(report.strict_absence)
        self.assertIn('ORIGINAL_TREE_STOP', report.cleanup_failures)
        self.assertEqual(report.status, 'BLOCKED')

    def test_am_anr_scalar_abi_accepts_signed_flags_and_opaque_reason(self):
        for user, pid, flags, reason in ((0, 1, -1, PRIVATE),
                                         (2147483647, 2147483647, -2147483648, 'reason,[nested]\n' + PRIVATE),
                                         (0, 1, 2147483647, 'reason,with,commas')):
            payload = '[' + str(user) + ',' + str(pid) + ',' + PACKAGE + ',' + str(flags) + ',' + reason + ']'
            self.assertIs(S.is_own_am_anr('am_anr', payload, PACKAGE), True)

    def test_am_anr_scalar_abi_rejects_out_of_range_and_malformed_prefix(self):
        for user, pid, flags in (('-1', '1', '0'), ('2147483648', '1', '0'),
                                 ('0', '0', '0'), ('0', '2147483648', '0'),
                                 ('0', '1', '-2147483649'), ('0', '1', '2147483648'),
                                 ('0', '1', '+1'), ('0', '1', '1.0')):
            payload = '[' + user + ',' + pid + ',' + PACKAGE + ',' + flags + ',' + PRIVATE + ']'
            self.assertIs(S.is_own_am_anr('am_anr', payload, PACKAGE), False)

    def test_expired_preparation_cannot_win_guardian_scheduling_race(self):
        from unittest import mock
        release = threading.Event()
        class HeldSupervisor(S.Supervisor):
            def _guard(self):
                release.wait(2)
                super()._guard()
        owned = FakeOwned()
        observer = S.Observer(PACKAGE)
        clock = [100.0]
        with mock.patch.object(S.time, 'monotonic', side_effect=lambda: clock[0]):
            supervisor = HeldSupervisor(owned=owned, commands=FixtureAdapter(owned), observer=observer,
                                         avd=AVD, serial=SERIAL, contract=S.LaunchContract('5f2009d4', True),
                                         caps=S.Caps(.2, .06, .02, .2, .5, .2, preparation=.1))
            clock[0] = 100.05
            self.assertTrue(observer.arm(epoch='observed-device-cursor', after_ns=100, source=S.Command.pending()))
            clock[0] = 100.2
            command = S.Command.pending()
            accepted = supervisor.attach_instrumentation(command)
        release.set()
        self.assertFalse(accepted, 'Attachment must compare the original preparation deadline')
        self.assertTrue(command.cancelled)
        self.report(supervisor)

    @unittest.skipUnless(sys.platform == 'win32', 'Windows exact owned-tree settlement')
    def test_actual_exited_parent_live_owned_child_is_settled_by_private_job(self):
        self.assertTrue(hasattr(S._WindowsJob, 'stop_and_settle'), 'Missing event-driven private-job settlement')
        fixture = ('import subprocess, sys; subprocess.Popen([sys.executable, "-B", "-c", '
                   '"import threading; threading.Event().wait()"], '
                   'stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)')
        original = subprocess.Popen([sys.executable, '-B', '-c', fixture],
                                    stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                    creationflags=0x00000004)
        job = None
        try:
            job = S._WindowsJob(original)
            job.resume(original)
            original.wait(timeout=2)
            self.assertGreater(job.active_count(), 0, 'Fixture must have a live owned descendant after root exit')
            owned = S.OwnedProcess(original, lambda same_original, timeout: job.stop_and_settle(timeout))
            adapter = FixtureAdapter(FakeOwned(), {'console_stop': (0, b'OK: killing emulator, bye bye\n')})
            supervisor = S.Supervisor(owned=owned, commands=adapter,
                                      observer=self.observer(epoch='fresh'),
                                      instrument=S.Command.completed(0), avd=AVD, serial=SERIAL,
                                      contract=S.LaunchContract('5f2009d4', True), caps=S.Caps(.2, .06, .02, 1, 1, .2))
            report = self.report(supervisor)
            self.assertTrue(report.shutdown_verified)
            self.assertEqual(job.active_count(), 0)
            command_tree = self.command(fixture)
            self.assertTrue(command_tree.settle(2))
            self.assertTrue(command_tree._tree_settled)
            self.assertIsNone(command_tree.failure)
        finally:
            if job is not None:
                self.assertTrue(job.stop_and_settle(2), 'Exact host tree fixture did not settle')
                job.close()
            else:
                original.kill()
                original.wait(timeout=1)

    def test_r117_absence_offline_then_not_found_within_one_original_budget(self):
        calls = []
        def absence(timeout, max_bytes):
            calls.append((time.monotonic(), timeout))
            return S.Command.completed(1, b'',
                b'error: device offline\n' if len(calls) == 1 else
                ("error: device '" + SERIAL + "' not found\n").encode())
        supervisor, _, _, _, _ = self.context(S.Command.completed(0), {'absence': absence},
            caps=S.Caps(.15, .4, .02, .1, .3, .2))
        report = self.report(supervisor)
        self.assertTrue(report.eligible_for_oracle)
        self.assertTrue(report.strict_absence)
        self.assertEqual(len(calls), 2)
        self.assertGreaterEqual(calls[1][0] - calls[0][0], .20)
        self.assertLess(calls[1][1], calls[0][1] - .20)
        self.assertLess(calls[-1][0] - calls[0][0], .4)

    def test_r117_perpetual_nonstrict_absence_is_bounded_not_busy_and_ineligible(self):
        for reply in ((1, b'', b'error: device offline\n'), (0, b'device\n', b'')):
            with self.subTest(code=reply[0]):
                calls = []
                def absence(timeout, max_bytes):
                    calls.append((time.monotonic(), timeout))
                    return S.Command.completed(*reply)
                supervisor, _, _, _, _ = self.context(S.Command.completed(0), {'absence': absence},
                    caps=S.Caps(.15, .4, .02, .1, .3, .2))
                report = self.report(supervisor)
                self.assertFalse(report.strict_absence)
                self.assertFalse(report.eligible_for_oracle)
                self.assertEqual(report.cleanup_failures, ('ABSENCE_UNCONFIRMED',))
                self.assertEqual(len(calls), 2)
                self.assertGreaterEqual(calls[1][0] - calls[0][0], .20)
                self.assertLess(calls[1][1], calls[0][1])
                with self.assertRaises(S.Blocked):
                    supervisor.take_instrumentation_output()

    def test_r117_failed_cancelled_or_unsettled_absence_cannot_become_natural(self):
        for kind in ('failed', 'cancelled', 'unsettled'):
            with self.subTest(kind=kind):
                children = []
                def absence(timeout, max_bytes):
                    child = S.Command.completed(1, b'',
                        ("error: device '" + SERIAL + "' not found").encode())
                    if kind == 'failed':
                        child.failure = 'OUTPUT_CAP'
                    elif kind == 'cancelled':
                        child.cancelled = True
                    else:
                        child.settle = lambda timeout: False
                    children.append(child)
                    return child
                supervisor, _, _, _, _ = self.context(S.Command.completed(0), {'absence': absence})
                report = self.report(supervisor)
                self.assertFalse(report.shutdown_verified)
                self.assertFalse(report.eligible_for_oracle)
                self.assertFalse(report.strict_absence)
                self.assertEqual(len(children), 1)
                self.assertEqual(children[0].buffered_bytes, 0)

    def test_r117_absence_deadline_cannot_be_reset_by_late_not_found_reply(self):
        release = threading.Event()
        returned = threading.Event()
        children = []
        def absence(timeout, max_bytes):
            release.wait(1)
            child = S.Command.completed(1, b'',
                ("error: device '" + SERIAL + "' not found").encode())
            children.append(child)
            returned.set()
            return child
        supervisor, _, _, _, _ = self.context(S.Command.completed(0), {'absence': absence})
        try:
            report = self.report(supervisor)
            self.assertFalse(report.strict_absence)
            self.assertFalse(report.eligible_for_oracle)
        finally:
            release.set()
            self.assertTrue(returned.wait(1))
        self.assertEqual(len(children), 1)

    def test_r117_later_strict_absence_never_waives_original_cleanup_conjuncts(self):
        for failed in ('snapshot_list', 'console_stop', 'disconnect', 'private_tree'):
            with self.subTest(failed=failed):
                owned = FakeOwned()
                if failed == 'private_tree':
                    owned.stop_tree = lambda timeout: False
                count = [0]
                def absence(timeout, max_bytes):
                    count[0] += 1
                    return S.Command.completed(1, b'', b'error: device offline' if count[0] == 1 else
                        ("error: device '" + SERIAL + "' not found").encode())
                changes = {'absence': absence}
                if failed != 'private_tree':
                    changes[failed] = RuntimeError(PRIVATE)
                adapter = FixtureAdapter(owned, changes)
                supervisor = S.Supervisor(owned=owned, commands=adapter, instrument=S.Command.completed(0),
                    observer=self.observer(), avd=AVD, serial=SERIAL,
                    contract=S.LaunchContract('6cca814c', True),
                    caps=S.Caps(.15, .4, .02, .1, .3, .2))
                report = self.report(supervisor)
                self.assertTrue(report.strict_absence)
                self.assertFalse(report.shutdown_verified)
                self.assertFalse(report.eligible_for_oracle)
                self.assertEqual(report.status, 'BLOCKED')

    def test_r117_abort_still_withholds_oracle_after_pending_absence_converges(self):
        release = threading.Event()
        entered = threading.Event()
        count = [0]
        def absence(timeout, max_bytes):
            count[0] += 1
            if count[0] == 1:
                entered.set()
                release.wait(.5)
                return S.Command.completed(1, b'', b'error: device offline')
            return S.Command.completed(1, b'', ("error: device '" + SERIAL + "' not found").encode())
        supervisor, _, _, _, _ = self.context(S.Command.completed(0), {'absence': absence},
            caps=S.Caps(.15, .5, .02, .1, .3, .2))
        self.assertTrue(entered.wait(1))
        supervisor.abort()
        release.set()
        report = self.report(supervisor)
        self.assertTrue(report.shutdown_verified)
        self.assertEqual(report.cause, 'ABORTED')
        self.assertFalse(report.eligible_for_oracle)

    def test_failed_original_tree_stop_still_disconnects_and_checks_absence(self):
        owned = FakeOwned()
        owned.stop_tree = lambda timeout: False
        adapter = FixtureAdapter(owned, {'console_stop': RuntimeError(PRIVATE)})
        supervisor = S.Supervisor(owned=owned, commands=adapter, instrument=S.Command.completed(0),
                                  observer=self.observer(epoch='fresh'),
                                  avd=AVD, serial=SERIAL,
                                  contract=S.LaunchContract('6cca814c', True),
                                  caps=S.Caps(instrumentation=.1, command=.04, process_wait=.01,
                                              stop=.04, settle=.1, snapshot_budget=.1))
        report = self.report(supervisor)
        self.assertEqual(report.status, 'BLOCKED')
        self.assertFalse(report.original_terminal)
        self.assertIn(('disconnect', None), adapter.calls)
        self.assertIn(('absence', None), adapter.calls)


class OutputSnapshotTests(unittest.TestCase):
    """Read-only RAM snapshots; no SDK or host child is needed for this API."""
    def snapshot(self, command):
        method = getattr(command, 'snapshot_output', None)
        self.assertTrue(callable(method), 'R124_SNAPSHOT_API_MISSING')
        return method()

    def test_r124_snapshot_is_immutable_copy_non_draining_and_uses_buffer_lock(self):
        child = S.Command.completed(0, b'first', b'second')
        lock = mock.MagicMock(wraps=child._lock)
        child._lock = lock
        observed = self.snapshot(child)
        self.assertEqual(observed, (b'first', b'second', False))
        self.assertIs(type(observed), tuple)
        self.assertTrue(all(type(value) is bytes for value in observed[:2]))
        lock.__enter__.assert_called_once()
        lock.__exit__.assert_called_once()
        child._buffers[0].extend(b'-later')
        self.assertEqual(observed, (b'first', b'second', False))
        self.assertEqual(child.buffered_bytes, 17)
        self.assertEqual(child.take_output(), (b'first-later', b'second'))

    def test_r124_empty_taken_and_discarded_buffers_are_explicitly_distinct(self):
        child = S.Command.pending()
        self.assertEqual(self.snapshot(child), (b'', b'', False))
        child._buffers[0].extend(b'private')
        original = self.snapshot(child)
        self.assertEqual(child.take_output(), (b'private', b''))
        self.assertEqual(self.snapshot(child), (b'', b'', False))
        child.discard()
        self.assertEqual(self.snapshot(child), (b'', b'', True))
        self.assertEqual(original, (b'private', b'', False))

    def test_r124_snapshot_does_not_reset_shared_output_cap_or_command_state(self):
        import io
        child = S.Command.pending()
        child._max_bytes = 8
        child._read(0, io.BytesIO(b'123456'))
        self.assertEqual(self.snapshot(child), (b'123456', b'', False))
        self.assertFalse(child.done.is_set())
        self.assertFalse(child.cancelled)
        child._read(1, io.BytesIO(b'abc'))
        self.assertEqual(child.failure, 'OUTPUT_CAP')
        self.assertEqual(child._max_bytes, 8)
        self.assertEqual(child.buffered_bytes, 6)
        self.assertEqual(self.snapshot(child), (b'123456', b'', False))

    def test_r124_original_cancel_still_discards_after_a_retained_snapshot(self):
        child = S.Command.pending()
        child._buffers = [bytearray(b'launch'), bytearray(b'error')]
        observed = self.snapshot(child)
        self.assertTrue(child.cancel(.1))
        self.assertEqual(observed, (b'launch', b'error', False))
        self.assertEqual(self.snapshot(child), (b'', b'', True))
        self.assertTrue(child.cancelled)
        self.assertEqual(child.failure, 'CANCELLED')


class RetainedHostStateTests(unittest.TestCase):
    """Closed independent scalars from original Windows handles, never readiness."""
    KEYS = {'native_root_state', 'job_active_class',
            'stdout_reader_alive', 'stderr_reader_alive'}

    def snapshot(self, child):
        api = getattr(child, 'snapshot_host_state', None)
        self.assertTrue(callable(api), 'R125_HOST_STATE_API_MISSING')
        row = api()
        self.assertEqual(set(row), self.KEYS)
        self.assertIn(row['native_root_state'], ('RUNNING', 'TERMINAL', 'UNAVAILABLE'))
        self.assertIn(row['job_active_class'], ('ZERO', 'ONE', 'MULTIPLE', 'UNAVAILABLE', 'NOT_OWNED'))
        for key in ('stdout_reader_alive', 'stderr_reader_alive'):
            self.assertTrue(row[key] is None or type(row[key]) is bool)
        return row

    def job_class(self, job):
        api = getattr(job, 'active_count_class', None)
        self.assertTrue(callable(api), 'R125_JOB_CLASS_API_MISSING')
        return api()

    def test_r125_original_native_handle_query_zero_does_not_poll_or_write_bookkeeping(self):
        self.assertTrue(callable(getattr(S.Command, 'snapshot_host_state', None)),
                        'R125_HOST_STATE_API_MISSING')
        import _winapi
        original = subprocess.Popen([sys.executable, '-B', '-c',
            'import threading; threading.Event().wait(4)'],
            stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            creationflags=0x00000004)
        child = S.Command.pending()
        child.process = original
        try:
            state = dict(child.__dict__)
            with mock.patch.object(original, 'poll', side_effect=AssertionError('R125_NO_POLL')), \
                 mock.patch.object(_winapi, 'WaitForSingleObject', wraps=_winapi.WaitForSingleObject) as query:
                self.assertEqual(self.snapshot(child)['native_root_state'], 'RUNNING')
                query.assert_called_once_with(int(original._handle), 0)
            self.assertEqual(child.__dict__, state)
            self.assertIsNone(original.returncode)
            original.kill()
            self.assertEqual(_winapi.WaitForSingleObject(int(original._handle), 2000), 0)
            with mock.patch.object(original, 'poll', side_effect=AssertionError('R125_NO_POLL')):
                self.assertEqual(self.snapshot(child)['native_root_state'], 'TERMINAL')
            self.assertIsNone(original.returncode, 'Native observation must not reap the original')
            self.assertEqual(child.__dict__, state)
        finally:
            original.kill()
            original.wait(timeout=2)

    def test_r125_native_unknown_failures_nonwindows_and_synthetic_stay_closed(self):
        self.assertTrue(callable(getattr(S.Command, 'snapshot_host_state', None)),
                        'R125_HOST_STATE_API_MISSING')
        import _winapi
        original = subprocess.Popen([sys.executable, '-B', '-c', 'pass'],
            stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            creationflags=0x00000004)
        child = S.Command.pending()
        child.process = original
        try:
            for value in (0xffffffff, 1, None, True, 'R125_PRIVATE_CANARY'):
                with mock.patch.object(_winapi, 'WaitForSingleObject', return_value=value):
                    self.assertEqual(self.snapshot(child)['native_root_state'], 'UNAVAILABLE')
            for error in (OSError('R125_PRIVATE_CANARY'), KeyboardInterrupt()):
                with mock.patch.object(_winapi, 'WaitForSingleObject', side_effect=error):
                    self.assertEqual(self.snapshot(child)['native_root_state'], 'UNAVAILABLE')
            with mock.patch.object(S, 'os', SimpleNamespace(name='posix')), \
                 mock.patch.object(_winapi, 'WaitForSingleObject') as query:
                self.assertEqual(self.snapshot(child)['native_root_state'], 'UNAVAILABLE')
                query.assert_not_called()
            retained = original._handle
            try:
                original._handle = None
                self.assertEqual(self.snapshot(child)['native_root_state'], 'UNAVAILABLE')
            finally:
                original._handle = retained
            child.process = SimpleNamespace(_handle=123456, poll=lambda: 0)
            self.assertEqual(self.snapshot(child)['native_root_state'], 'UNAVAILABLE')
            self.assertNotIn('123456', json.dumps(self.snapshot(child)))
            for synthetic in (S.Command.pending(), S.Command.completed(0)):
                self.assertEqual(self.snapshot(synthetic), dict(native_root_state='UNAVAILABLE',
                    job_active_class='NOT_OWNED', stdout_reader_alive=None, stderr_reader_alive=None))
        finally:
            original.kill()
            original.wait(timeout=2)

    def test_r125_private_job_nonblocking_busy_closed_errors_and_count_classes(self):
        job = object.__new__(S._WindowsJob)
        job._lock = threading.RLock()
        job._handle = object()
        for count, expected in ((0, 'ZERO'), (1, 'ONE'), (2, 'MULTIPLE'), (999, 'MULTIPLE'),
                                (True, 'UNAVAILABLE'), (-1, 'UNAVAILABLE'),
                                (1.0, 'UNAVAILABLE'), (None, 'UNAVAILABLE'),
                                ('R125_PRIVATE_CANARY', 'UNAVAILABLE')):
            with mock.patch.object(job, 'active_count', return_value=count):
                self.assertEqual(self.job_class(job), expected)
        for error in (OSError('R125_PRIVATE_CANARY'), KeyboardInterrupt()):
            with mock.patch.object(job, 'active_count', side_effect=error):
                self.assertEqual(self.job_class(job), 'UNAVAILABLE')
            self.assertTrue(job._lock.acquire(blocking=False))
            job._lock.release()
        job._handle = None
        with mock.patch.object(job, 'active_count') as query:
            self.assertEqual(self.job_class(job), 'UNAVAILABLE')
            query.assert_not_called()
        job._handle = object()
        held, release = threading.Event(), threading.Event()
        def hold():
            with job._lock:
                held.set()
                release.wait(2)
        owner = threading.Thread(target=hold)
        owner.start()
        try:
            self.assertTrue(held.wait(1))
            with mock.patch.object(job, 'active_count') as query:
                self.assertEqual(self.job_class(job), 'UNAVAILABLE')
                query.assert_not_called()
        finally:
            release.set()
            owner.join(2)
            self.assertFalse(owner.is_alive())
        lock = mock.Mock()
        lock.acquire.return_value = False
        job._lock = lock
        self.assertEqual(self.job_class(job), 'UNAVAILABLE')
        lock.acquire.assert_called_once_with(blocking=False)
        lock.release.assert_not_called()

    def test_r125_real_owned_job_one_multiple_zero_and_live_reader_pair(self):
        self.assertTrue(callable(getattr(S.Command, 'snapshot_host_state', None)),
                        'R125_HOST_STATE_API_MISSING')
        ready = threading.Event()
        source = ('import subprocess, sys, threading; '
                  'subprocess.Popen([sys.executable,"-B","-c",'
                  '"import threading; threading.Event().wait(4)"], '
                  'stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL); '
                  'print("HOST_READY",flush=True); threading.Event().wait(4)')
        child = S.Command.spawn([sys.executable, '-B', '-c', source], timeout=5,
            max_bytes=1024, tree_owned=True, on_line=lambda line: ready.set() if line == b'HOST_READY' else None)
        try:
            self.assertTrue(ready.wait(2))
            row = self.snapshot(child)
            self.assertEqual(row, dict(native_root_state='RUNNING', job_active_class='MULTIPLE',
                                      stdout_reader_alive=True, stderr_reader_alive=True))
            self.assertTrue(child.cancel(2))
            self.assertTrue(child.settle(1))
            row = self.snapshot(child)
            self.assertEqual(row['native_root_state'], 'TERMINAL')
            self.assertEqual(row['job_active_class'], 'UNAVAILABLE', 'Closed job is not historical zero proof')
            self.assertIs(row['stdout_reader_alive'], False)
            self.assertIs(row['stderr_reader_alive'], False)
        finally:
            self.assertTrue(child.cancel(2))
        original = subprocess.Popen([sys.executable, '-B', '-c', 'pass'],
            stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            creationflags=0x00000004)
        job = None
        try:
            job = S._WindowsJob(original)
            self.assertEqual(self.job_class(job), 'ONE')
            self.assertTrue(job.stop_and_settle(2))
            self.assertEqual(self.job_class(job), 'ZERO')
        finally:
            if job is not None:
                job.stop_and_settle(2)
                job.close()
            original.kill()
            original.wait(timeout=2)

    def test_r125_reader_pair_requires_actual_process_and_two_real_threads(self):
        self.assertTrue(callable(getattr(S.Command, 'snapshot_host_state', None)),
                        'R125_HOST_STATE_API_MISSING')
        child = S.Command.spawn([sys.executable, '-B', '-c',
            'import threading; threading.Event().wait(4)'], timeout=5, max_bytes=1024)
        try:
            self.assertEqual(self.snapshot(child)['job_active_class'], 'NOT_OWNED')
            pair = child._readers
            for readers in ([], pair[:1], pair + pair[:1], [object(), object()]):
                child._readers = readers
                row = self.snapshot(child)
                self.assertIsNone(row['stdout_reader_alive'])
                self.assertIsNone(row['stderr_reader_alive'])
            child._readers = pair
            child._tree_owned = True
            self.assertEqual(self.snapshot(child)['job_active_class'], 'UNAVAILABLE')
            child._tree_owned = False
        finally:
            child._readers = pair
            child._tree_owned = False
            self.assertTrue(child.cancel(2))


class OwnedJobExpectedImageTests(unittest.TestCase):
    """Bounded private-job image observations; never identity/readiness proof."""
    RESULTS = {'PATH_MATCH_OBSERVED', 'NO_PATH_MATCH_IN_COMPLETE_LIST', 'UNAVAILABLE'}

    def api(self, job):
        value = getattr(job, 'snapshot_expected_image', None)
        self.assertTrue(callable(value), 'R136_EXPECTED_IMAGE_API_MISSING')
        return value

    def fake_job(self, *, assigned=1, listed=1, pids=(41,), images=None, memberships=None, image_ok=True):
        import ctypes
        closed = []
        images = images or {41: r'C:\\fixture\\engine.exe'}
        memberships = iter(memberships if memberships is not None else (True,) * (2 * len(pids)))
        def query_job(handle, info_class, info_ptr, size, unused):
            info = info_ptr._obj
            info.NumberOfAssignedProcesses = assigned
            info.NumberOfProcessIdsInList = listed
            for index, pid in enumerate(pids[:16]):
                info.ProcessIdList[index] = pid
            return True
        def open_process(access, inherit, pid):
            self.assertEqual(access, 0x1000)
            self.assertFalse(inherit)
            return pid + 1000
        def in_job(handle, original_job, result_ptr):
            result_ptr._obj.value = int(next(memberships))
            return True
        def image(handle, flags, buffer, size_ptr):
            pid = handle - 1000
            if not image_ok:
                return False
            buffer.value = images[pid]
            size_ptr._obj.value = len(images[pid])
            return True
        kernel = SimpleNamespace(QueryInformationJobObject=query_job, OpenProcess=open_process,
                                 IsProcessInJob=in_job, QueryFullProcessImageNameW=image,
                                 CloseHandle=lambda handle: closed.append(handle) or True)
        job = object.__new__(S._WindowsJob)
        job._ctypes, job._lock, job._handle, job._kernel = ctypes, threading.RLock(), 73, kernel
        return job, closed

    def test_r136_api_is_closed_fixed_enum_and_no_raw_exports(self):
        job, closed = self.fake_job()
        result = self.api(job)(r'C:\\fixture\\engine.exe')
        self.assertEqual(result, 'PATH_MATCH_OBSERVED')
        self.assertIn(result, self.RESULTS)
        self.assertEqual(closed, [1041])
        self.assertNotIn('fixture', result)
        self.assertNotIn('41', result)

    def test_r136_complete_inspected_nonmatch_is_distinct_from_invalid_or_incomplete_samples(self):
        job, _ = self.fake_job()
        self.assertEqual(self.api(job)(r'D:\\other\\engine.exe'), 'NO_PATH_MATCH_IN_COMPLETE_LIST')
        for assigned, listed, pids in ((17, 17, (41,)), (1, 2, (41,)), (2, 2, (41, 41)),
                                       (1, 1, (0,)), (1, 1, (-1,))):
            with self.subTest(assigned=assigned, listed=listed, pids=pids):
                invalid, closed = self.fake_job(assigned=assigned, listed=listed, pids=pids)
                self.assertEqual(self.api(invalid)(r'C:\\fixture\\engine.exe'), 'UNAVAILABLE')
                self.assertEqual(closed, [])
        for path in ('relative.exe', r'\\relative', 'C:\\bad\x00path'):
            self.assertEqual(self.api(job)(path), 'UNAVAILABLE')

    def test_r136_membership_and_image_failures_fail_closed_and_close_every_opened_handle(self):
        foreign, closed = self.fake_job(memberships=(False,))
        self.assertEqual(self.api(foreign)(r'C:\\fixture\\engine.exe'), 'UNAVAILABLE')
        self.assertEqual(closed, [1041])
        image_failure, closed = self.fake_job(image_ok=False)
        self.assertEqual(self.api(image_failure)(r'C:\\fixture\\engine.exe'), 'UNAVAILABLE')
        self.assertEqual(closed, [1041])
        job, closed = self.fake_job()
        held, release = threading.Event(), threading.Event()
        def holder():
            with job._lock:
                held.set()
                release.wait(2)
        thread = threading.Thread(target=holder)
        thread.start()
        try:
            self.assertTrue(held.wait(1))
            self.assertEqual(self.api(job)(r'C:\\fixture\\engine.exe'), 'UNAVAILABLE')
            self.assertEqual(closed, [])
        finally:
            release.set()
            thread.join(2)

    def test_r136_command_delegates_only_genuine_retained_owned_job(self):
        value = S.Command.pending()
        self.assertTrue(callable(getattr(value, 'snapshot_expected_image', None)),
                        'R136_COMMAND_EXPECTED_IMAGE_API_MISSING')
        self.assertEqual(value.snapshot_expected_image(r'C:\\fixture\\engine.exe'), 'UNAVAILABLE')
        value._tree_owned, value._job = True, object()
        self.assertEqual(value.snapshot_expected_image(r'C:\\fixture\\engine.exe'), 'UNAVAILABLE')

    @unittest.skipUnless(sys.platform == 'win32', 'Windows private-job image control')
    def test_r136_actual_owned_python_image_match_nonmatch_closed_and_cleanup(self):
        import ctypes
        from ctypes import wintypes
        original = subprocess.Popen([sys.executable, '-B', '-c',
            'import threading; threading.Event().wait(4)'], stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, creationflags=0x00000004)
        job = None
        try:
            job = S._WindowsJob(original)
            job.resume(original)
            # The child is launched from sys.executable. Capture its native spelling
            # only in this control's RAM: app aliases can differ lexically from
            # sys.executable, and production deliberately never resolves either.
            image = ctypes.create_unicode_buffer(32768)
            length = wintypes.DWORD(32768)
            self.assertTrue(job._kernel.QueryFullProcessImageNameW(
                original._handle, 0, image, ctypes.byref(length)))
            self.assertEqual(self.api(job)(image.value), 'PATH_MATCH_OBSERVED')
            self.assertEqual(self.api(job)(r'C:\\r136\\different-image.exe'),
                             'NO_PATH_MATCH_IN_COMPLETE_LIST')
            self.assertTrue(job.stop_and_settle(2))
            self.assertTrue(job.close())
            self.assertEqual(self.api(job)(image.value), 'UNAVAILABLE')
            original.wait(timeout=2)
        finally:
            if job is not None:
                job.stop_and_settle(2)
                job.close()
            if original.poll() is None:
                original.kill()
                original.wait(timeout=2)


    # R138 extends this existing fixture; no inherited test executions are added.
    TOPOLOGIES = ('MODAL_COMPATIBLE_TOPOLOGY', 'VISIBLE_OWNED_WINDOW_NO_MODAL_TOPOLOGY',
                  'ONLY_HIDDEN_OWNED_WINDOWS',
                  'NO_OWNED_TOP_LEVEL_WINDOW_IN_COMPLETE_DESKTOP_SCAN')

    def topology_api(self, value):
        api = getattr(value, 'snapshot_owned_engine_window_topology', None)
        self.assertTrue(callable(api), 'R138_TOPOLOGY_API_MISSING')
        return api

    def topology_job(self, *, pids=(41,), images=None, windows=None, assigned=None, listed=None):
        import ctypes
        images = {pid: r'C:\fixture\engine.exe' for pid in pids} if images is None else images
        state = SimpleNamespace(pids=pids, images=images, windows={} if windows is None else windows,
                                closed=[], opened=[], callbacks=0, live=258, now=100.0)
        def query(handle, kind, pointer, size, unused):
            self.assertEqual((handle, kind), (73, 3))
            info = pointer._obj
            self.assertEqual(size, ctypes.sizeof(info))
            self.assertEqual(len(info.ProcessIdList), 16)
            info.NumberOfAssignedProcesses = len(state.pids) if assigned is None else assigned
            info.NumberOfProcessIdsInList = len(state.pids) if listed is None else listed
            for index, pid in enumerate(state.pids[:16]):
                info.ProcessIdList[index] = pid
            return True
        def open_process(access, inherit, pid):
            self.assertEqual(access, 0x101000, 'Query-limited plus synchronize ONLY')
            self.assertFalse(inherit)
            state.opened.append(pid + 1000)
            return pid + 1000
        def membership(handle, job_handle, pointer):
            self.assertEqual(job_handle, 73)
            pointer._obj.value = 1
            return True
        def image(handle, flags, buffer, pointer):
            self.assertEqual(flags, 0)
            buffer.value = state.images[handle - 1000]
            pointer._obj.value = len(buffer.value)
            return True
        def wait(handle, timeout):
            self.assertEqual(timeout, 0, 'No blocking wait under the original lock')
            return state.live
        def pid(hwnd, pointer):
            pointer._obj.value = state.windows[hwnd][0]
            return 91
        def enum(callback, parameter):
            self.assertEqual(parameter, 0)
            for hwnd in tuple(state.windows):
                state.callbacks += 1
                if not callback(hwnd, parameter):
                    return False
            return True
        def own_state(hwnd, field):
            self.assertEqual(state.windows[hwnd][0], 41, 'No unowned HWND state query')
            return state.windows[hwnd][field]
        user = SimpleNamespace(
            EnumWindows=mock.Mock(side_effect=enum),
            GetWindowThreadProcessId=mock.Mock(side_effect=pid),
            IsWindowVisible=mock.Mock(side_effect=lambda hwnd: own_state(hwnd, 1)),
            IsWindowEnabled=mock.Mock(side_effect=lambda hwnd: own_state(hwnd, 2)),
            GetWindow=mock.Mock(side_effect=lambda hwnd, command:
                own_state(hwnd, 3) if command == 4 else self.fail('GW_OWNER only')))
        kernel = SimpleNamespace(QueryInformationJobObject=mock.Mock(side_effect=query),
            OpenProcess=mock.Mock(side_effect=open_process), IsProcessInJob=mock.Mock(side_effect=membership),
            QueryFullProcessImageNameW=mock.Mock(side_effect=image),
            WaitForSingleObject=mock.Mock(side_effect=wait),
            CloseHandle=mock.Mock(side_effect=lambda h: state.closed.append(h) or True))
        job = object.__new__(S._WindowsJob)
        job._ctypes, job._lock, job._handle, job._kernel = ctypes, threading.RLock(), 73, kernel
        return job, state, user

    def topology_sample(self, job, state, user, path=r'C:\fixture\engine.exe'):
        with mock.patch.object(job._ctypes, 'WinDLL', return_value=user), \
             mock.patch.object(S, 'time', SimpleNamespace(monotonic=lambda: state.now)):
            value = self.topology_api(job)(path)
        self.assertIn(value, self.TOPOLOGIES + ('UNAVAILABLE',))
        self.assertNotIn('fixture', value)
        self.assertNotIn('R138_PRIVATE', value)
        return value

    def test_r138_fixed_native_abi_and_query_only_retained_handle_finally(self):
        import ctypes
        from ctypes import wintypes
        windows = {101: (41, True, False, 0), 102: (41, True, True, 101),
                   901: (99, True, True, 0)}
        job, state, user = self.topology_job(windows=windows)
        self.assertEqual(self.topology_sample(job, state, user), self.TOPOLOGIES[0])
        self.assertEqual(state.opened, [1041])
        self.assertEqual(state.closed, [1041])
        self.assertEqual(job._handle, 73)
        user.EnumWindows.assert_called_once()
        self.assertEqual(user.EnumWindows.restype, wintypes.BOOL)
        callback_type, parameter_type = user.EnumWindows.argtypes
        self.assertEqual(callback_type._restype_, wintypes.BOOL)
        self.assertEqual(callback_type._argtypes_, (wintypes.HWND, wintypes.LPARAM))
        self.assertEqual(parameter_type, wintypes.LPARAM)
        for function, args, restype in (
                (user.GetWindowThreadProcessId, [wintypes.HWND, ctypes.POINTER(wintypes.DWORD)], wintypes.DWORD),
                (user.IsWindowVisible, [wintypes.HWND], wintypes.BOOL),
                (user.IsWindowEnabled, [wintypes.HWND], wintypes.BOOL),
                (user.GetWindow, [wintypes.HWND, wintypes.UINT], wintypes.HWND)):
            self.assertEqual(function.argtypes, args)
            self.assertEqual(function.restype, restype)
        self.assertTrue(all(call.args[1] == 0 for call in job._kernel.WaitForSingleObject.call_args_list))
        self.assertGreaterEqual(job._kernel.WaitForSingleObject.call_count, 2)
        self.assertEqual(job._kernel.QueryInformationJobObject.call_count, 2)

    def test_r138_complete_topology_controls_hidden_visible_modal_and_no_window(self):
        controls = (
            ({101: (41, True, False, 0), 102: (41, True, True, 101)}, self.TOPOLOGIES[0]),
            ({101: (41, True, True, 0), 102: (41, True, True, 101)}, self.TOPOLOGIES[1]),
            ({101: (41, False, True, 0)}, self.TOPOLOGIES[2]),
            ({901: (99, True, True, 0)}, self.TOPOLOGIES[3]),
            ({}, self.TOPOLOGIES[3]))
        for windows, expected in controls:
            with self.subTest(topology=expected):
                job, state, user = self.topology_job(windows=windows)
                self.assertEqual(self.topology_sample(job, state, user), expected)
                self.assertEqual(state.closed, state.opened)

    def test_r138_foreign_owner_and_unowned_hwnd_receive_pid_filter_only(self):
        job, state, user = self.topology_job(windows={101: (41, True, True, 901),
                                                     901: (99, True, False, 0)})
        self.assertEqual(self.topology_sample(job, state, user), self.TOPOLOGIES[1])
        for function in (user.IsWindowVisible, user.IsWindowEnabled, user.GetWindow):
            self.assertTrue(all(call.args[0] != 901 for call in function.call_args_list))
        self.assertEqual(state.opened, [1041], 'No process opens from HWND ownership filters')
        self.assertGreaterEqual(user.GetWindowThreadProcessId.call_count, 3)

    def test_r138_complete_member_inspection_missing_multiple_and_nonmatching_engines(self):
        for pids, images, expected in (
                ((), {}, 'UNAVAILABLE'),
                ((41,), {41: r'C:\other\different.exe'}, 'UNAVAILABLE'),
                ((41, 42), {41: r'C:\fixture\engine.exe', 42: r'C:\fixture\engine.exe'}, 'UNAVAILABLE'),
                ((42, 41), {42: r'C:\other\different.exe', 41: r'C:\fixture\engine.exe'}, self.TOPOLOGIES[3])):
            with self.subTest(members=len(pids), topology=expected):
                job, state, user = self.topology_job(pids=pids, images=images)
                self.assertEqual(self.topology_sample(job, state, user), expected)
                self.assertCountEqual(state.closed, state.opened)
                self.assertEqual(len(state.closed), len(set(state.closed)))

    def test_r138_malformed_incomplete_native_lists_never_open_processes(self):
        for assigned, listed, pids in ((17, 17, (41,)), (2, 1, (41,)), (1, 2, (41,)),
                                      (2, 2, (41, 41)), (1, 1, (0,)), (1, 1, (2**32,))):
            job, state, user = self.topology_job(pids=pids, assigned=assigned, listed=listed)
            self.assertEqual(self.topology_sample(job, state, user), 'UNAVAILABLE')
            self.assertEqual(state.opened, [])
            user.EnumWindows.assert_not_called()

    def test_r138_uninspectable_member_or_partial_image_invalidates_even_an_earlier_match(self):
        self.assertTrue(callable(getattr(S._WindowsJob, 'snapshot_owned_engine_window_topology', None)))
        for failure in ('open', 'membership', 'image', 'image_length', 'image_path'):
            job, state, user = self.topology_job(pids=(41, 42), images={
                41: r'C:\fixture\engine.exe', 42: r'C:\other\different.exe'})
            if failure == 'open':
                job._kernel.OpenProcess.side_effect = lambda access, inherit, pid: (
                    state.opened.append(pid + 1000) or pid + 1000) if pid == 41 else 0
            elif failure == 'membership':
                job._kernel.IsProcessInJob.side_effect = lambda h, j, p: False if h == 1042 else (
                    setattr(p._obj, 'value', 1) or True)
            else:
                original = job._kernel.QueryFullProcessImageNameW.side_effect
                def image(h, flags, buffer, pointer):
                    if h == 1042:
                        if failure == 'image':
                            return False
                        buffer.value = 'relative.exe' if failure == 'image_path' else r'C:\other\different.exe'
                        pointer._obj.value = 32768 if failure == 'image_length' else len(buffer.value)
                        return True
                    return original(h, flags, buffer, pointer)
                job._kernel.QueryFullProcessImageNameW.side_effect = image
            self.assertEqual(self.topology_sample(job, state, user), 'UNAVAILABLE')
            self.assertCountEqual(state.closed, state.opened)
            user.EnumWindows.assert_not_called()

    def test_r138_liveness_membership_image_and_job_list_races_are_unavailable(self):
        self.assertTrue(callable(getattr(S._WindowsJob, 'snapshot_owned_engine_window_topology', None)))
        for race in ('terminal', 'wait_failed', 'member', 'image', 'members_changed', 'job_closed'):
            job, state, user = self.topology_job(windows={101: (41, True, True, 0)})
            original = user.EnumWindows.side_effect
            def enum(callback, parameter):
                outcome = original(callback, parameter)
                if race == 'terminal':
                    state.live = 0
                elif race == 'wait_failed':
                    state.live = 0xffffffff
                elif race == 'member':
                    job._kernel.IsProcessInJob.return_value = False
                    job._kernel.IsProcessInJob.side_effect = None
                elif race == 'image':
                    state.images[41] = r'C:\other\different.exe'
                elif race == 'members_changed':
                    state.pids = (41, 42)
                else:
                    job._handle = None
                return outcome
            user.EnumWindows.side_effect = enum
            self.assertEqual(self.topology_sample(job, state, user), 'UNAVAILABLE')
            self.assertEqual(state.closed, [1041])

    def test_r138_terminal_unknown_liveness_and_pre_enum_image_races_fail_closed(self):
        self.assertTrue(callable(getattr(S._WindowsJob, 'snapshot_owned_engine_window_topology', None)))
        for live in (0, 0xffffffff, 1):
            job, state, user = self.topology_job()
            state.live = live
            self.assertEqual(self.topology_sample(job, state, user), 'UNAVAILABLE')
            self.assertEqual(state.closed, [1041])
            user.EnumWindows.assert_not_called()
        job, state, user = self.topology_job()
        original = job._kernel.QueryFullProcessImageNameW.side_effect
        def change(h, flags, buffer, pointer):
            value = original(h, flags, buffer, pointer)
            state.images[41] = r'C:\other\different.exe'
            return value
        job._kernel.QueryFullProcessImageNameW.side_effect = change
        self.assertEqual(self.topology_sample(job, state, user), 'UNAVAILABLE')
        user.EnumWindows.assert_not_called()

    def test_r138_hwnd_pid_owner_pid_and_state_races_fail_closed(self):
        self.assertTrue(callable(getattr(S._WindowsJob, 'snapshot_owned_engine_window_topology', None)))
        for race in ('hwnd', 'owner_pid', 'owner_link', 'visible', 'enabled', 'owner_enabled'):
            job, state, user = self.topology_job(windows={101: (41, True, False, 0),
                102: (41, True, True, 101), 901: (99, True, True, 0)})
            original = user.EnumWindows.side_effect
            def enum(callback, parameter):
                outcome = original(callback, parameter)
                if race == 'hwnd':
                    state.windows[102] = (99, True, True, 101)
                elif race == 'owner_pid':
                    state.windows[101] = (99, True, False, 0)
                elif race == 'owner_link':
                    state.windows[102] = (41, True, True, 901)
                elif race == 'visible':
                    state.windows[102] = (41, False, True, 101)
                elif race == 'enabled':
                    state.windows[102] = (41, True, False, 101)
                else:
                    state.windows[101] = (41, True, True, 0)
                return outcome
            user.EnumWindows.side_effect = enum
            self.assertEqual(self.topology_sample(job, state, user), 'UNAVAILABLE')
            self.assertEqual(state.closed, [1041])

    def test_r138_callback_and_engine_window_caps_have_no_second_pass(self):
        self.assertTrue(callable(getattr(S._WindowsJob, 'snapshot_owned_engine_window_topology', None)))
        # R138b prospective fitness revision: only the total callback count moves
        # to 4096; engine-window16 and local50ms bounds remain unchanged.
        for count, pid, expected in ((16, 41, self.TOPOLOGIES[2]), (17, 41, 'UNAVAILABLE'),
                                      (4095, 99, self.TOPOLOGIES[3]), (4096, 99, 'UNAVAILABLE'),
                                      (4200, 99, 'UNAVAILABLE')):
            job, state, user = self.topology_job(windows={100 + i: (pid, False, True, 0)
                                                        for i in range(count)})
            self.assertEqual(self.topology_sample(job, state, user), expected)
            self.assertLessEqual(state.callbacks, 4096)
            self.assertLessEqual(user.IsWindowVisible.call_count, 32)
            user.EnumWindows.assert_called_once()
            self.assertEqual(state.closed, [1041])

    def test_r138b_complete_300_unowned_windows_remains_discriminating_and_pid_filter_only(self):
        job, state, user = self.topology_job(windows={100 + index: (99, False, False, 0)
                                                    for index in range(300)})
        self.assertEqual(self.topology_sample(job, state, user), self.TOPOLOGIES[3])
        self.assertEqual(state.callbacks, 300)
        self.assertEqual(user.GetWindowThreadProcessId.call_count, 300)
        user.EnumWindows.assert_called_once()
        for function in (user.IsWindowVisible, user.IsWindowEnabled, user.GetWindow):
            function.assert_not_called()
        self.assertEqual(state.opened, [1041])
        self.assertEqual(state.closed, [1041])
        self.assertEqual(state.now, 100.0, 'The fake local deadline is not renewed or increased')

    def test_r138_local_deadline_covers_callback_and_post_enum_continuations(self):
        self.assertTrue(callable(getattr(S._WindowsJob, 'snapshot_owned_engine_window_topology', None)))
        for stage in ('before_enum', 'callback', 'after_enum', 'continuation'):
            job, state, user = self.topology_job(windows={101: (41, True, True, 0)})
            if stage == 'before_enum':
                original = job._kernel.OpenProcess.side_effect
                def slow_open(*args):
                    state.now += .051
                    return original(*args)
                job._kernel.OpenProcess.side_effect = slow_open
            elif stage == 'callback':
                original = user.GetWindowThreadProcessId.side_effect
                def slow_pid(*args):
                    state.now += .051
                    return original(*args)
                user.GetWindowThreadProcessId.side_effect = slow_pid
            elif stage == 'after_enum':
                original = user.EnumWindows.side_effect
                def slow_enum(*args):
                    value = original(*args)
                    state.now += .051
                    return value
                user.EnumWindows.side_effect = slow_enum
            else:
                original = user.IsWindowVisible.side_effect
                calls = []
                def slow_visible(*args):
                    calls.append(1)
                    if len(calls) == 2:
                        state.now += .051
                    return original(*args)
                user.IsWindowVisible.side_effect = slow_visible
            self.assertEqual(self.topology_sample(job, state, user), 'UNAVAILABLE')
            self.assertEqual(state.closed, [1041])
            self.assertLessEqual(user.EnumWindows.call_count, 1)

    def test_r138_native_errors_exceptions_and_close_failure_never_escape_or_export(self):
        self.assertTrue(callable(getattr(S._WindowsJob, 'snapshot_owned_engine_window_topology', None)))
        for name in ('QueryInformationJobObject', 'OpenProcess', 'IsProcessInJob',
                     'QueryFullProcessImageNameW', 'WaitForSingleObject', 'CloseHandle',
                     'EnumWindows', 'GetWindowThreadProcessId', 'IsWindowVisible',
                     'IsWindowEnabled', 'GetWindow'):
            job, state, user = self.topology_job(windows={101: (41, True, True, 0)})
            function = getattr(job._kernel if hasattr(job._kernel, name) else user, name)
            function.side_effect = OSError('R138_PRIVATE_NATIVE_ERROR pid=987654 hwnd=123456')
            self.assertEqual(self.topology_sample(job, state, user), 'UNAVAILABLE')
            if name not in ('QueryInformationJobObject', 'OpenProcess'):
                self.assertEqual(job._kernel.CloseHandle.call_count, 1)
        for name in ('EnumWindows', 'GetWindowThreadProcessId', 'CloseHandle'):
            job, state, user = self.topology_job(windows={101: (41, True, True, 0)})
            function = getattr(job._kernel if hasattr(job._kernel, name) else user, name)
            function.side_effect = None
            function.return_value = 0
            self.assertEqual(self.topology_sample(job, state, user), 'UNAVAILABLE')
        job, state, user = self.topology_job()
        for error in (KeyboardInterrupt(), SystemExit()):
            with mock.patch.object(job._kernel, 'QueryInformationJobObject', side_effect=error):
                self.assertEqual(self.topology_sample(job, state, user), 'UNAVAILABLE')

    def test_r138_false_boolean_and_null_owner_with_native_error_are_unavailable(self):
        self.assertTrue(callable(getattr(S._WindowsJob, 'snapshot_owned_engine_window_topology', None)))
        import ctypes
        for name in ('IsWindowVisible', 'IsWindowEnabled', 'GetWindow'):
            job, state, user = self.topology_job(windows={101: (41, True, True, 0)})
            def bad(*args):
                ctypes.set_last_error(5)
                return 0
            getattr(user, name).side_effect = bad
            self.assertEqual(self.topology_sample(job, state, user), 'UNAVAILABLE')
            self.assertEqual(state.closed, [1041])
        ctypes.set_last_error(0)

    def test_r138_nonblocking_original_lock_closed_job_and_lexical_path_only(self):
        self.assertTrue(callable(getattr(S._WindowsJob, 'snapshot_owned_engine_window_topology', None)))
        job, state, user = self.topology_job()
        lock = mock.Mock()
        lock.acquire.return_value = False
        job._lock = lock
        self.assertEqual(self.topology_sample(job, state, user), 'UNAVAILABLE')
        lock.acquire.assert_called_once_with(blocking=False)
        lock.release.assert_not_called()
        self.assertEqual(state.opened, [])
        job._lock = mock.Mock(wraps=threading.RLock())
        self.assertEqual(self.topology_sample(job, state, user), self.TOPOLOGIES[3])
        job._lock.acquire.assert_called_once_with(blocking=False)
        job._lock.release.assert_called_once_with()
        for path in ('relative.exe', '', None, 'C:\\bad\x00path'):
            self.assertEqual(self.topology_sample(job, state, user, path), 'UNAVAILABLE')
        job._handle = None
        self.assertEqual(self.topology_sample(job, state, user), 'UNAVAILABLE')

    def test_r138_command_retains_owned_job_and_whitelists_fixed_topology_enum(self):
        child = S.Command.pending()
        api = self.topology_api(child)
        self.assertEqual(api(r'C:\fixture\engine.exe'), 'UNAVAILABLE')
        child._tree_owned, child._job = True, object()
        self.assertEqual(api(r'C:\fixture\engine.exe'), 'UNAVAILABLE')
        job, state, user = self.topology_job()
        child._job = job
        for value in self.TOPOLOGIES + ('UNAVAILABLE', 'NOT_SAMPLED', 'R138_PRIVATE', None, [], {}):
            with mock.patch.object(job, 'snapshot_owned_engine_window_topology', create=True, return_value=value):
                self.assertEqual(api(r'C:\fixture\engine.exe'),
                    value if type(value) is str and value in self.TOPOLOGIES else 'UNAVAILABLE')
        with mock.patch.object(job, 'snapshot_owned_engine_window_topology', create=True,
                               side_effect=KeyboardInterrupt()):
            self.assertEqual(api(r'C:\fixture\engine.exe'), 'UNAVAILABLE')

    class CallerDesktopFitnessAssertion(AssertionError):
        """Fixed failure type only; no native error, state or identifier payload."""

    def test_r138_one_disposable_real_ctypes_window_profile_and_full_settlement(self):
        self.assertTrue(callable(getattr(S._WindowsJob, 'snapshot_owned_engine_window_topology', None)),
                        'R138_TOPOLOGY_API_MISSING')
        import ctypes
        from ctypes import wintypes
        user = ctypes.WinDLL('user32', use_last_error=True)
        kernel = ctypes.WinDLL('kernel32', use_last_error=True)
        kernel.GetCurrentThreadId.argtypes = []
        kernel.GetCurrentThreadId.restype = wintypes.DWORD
        user.GetThreadDesktop.argtypes = [wintypes.DWORD]
        user.GetThreadDesktop.restype = wintypes.HANDLE
        user.CreateDesktopW.argtypes = [wintypes.LPCWSTR, wintypes.LPCWSTR, ctypes.c_void_p,
                                       wintypes.DWORD, wintypes.DWORD, ctypes.c_void_p]
        user.CreateDesktopW.restype = wintypes.HANDLE
        user.SetThreadDesktop.argtypes = [wintypes.HANDLE]
        user.SetThreadDesktop.restype = wintypes.BOOL
        user.CloseDesktop.argtypes = [wintypes.HANDLE]
        user.CloseDesktop.restype = wintypes.BOOL
        user.CreateWindowExW.argtypes = [wintypes.DWORD, wintypes.LPCWSTR, wintypes.LPCWSTR,
            wintypes.DWORD, ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_int,
            wintypes.HWND, wintypes.HMENU, wintypes.HINSTANCE, ctypes.c_void_p]
        user.CreateWindowExW.restype = wintypes.HWND
        user.DestroyWindow.argtypes = [wintypes.HWND]
        user.DestroyWindow.restype = wintypes.BOOL
        user.IsWindow.argtypes = [wintypes.HWND]
        user.IsWindow.restype = wintypes.BOOL
        for name in ('IsWindowVisible', 'IsWindowEnabled'):
            getattr(user, name).argtypes = [wintypes.HWND]
            getattr(user, name).restype = wintypes.BOOL
        user.GetWindow.argtypes = [wintypes.HWND, wintypes.UINT]
        user.GetWindow.restype = wintypes.HWND
        user.GetWindowThreadProcessId.argtypes = [wintypes.HWND, ctypes.POINTER(wintypes.DWORD)]
        user.GetWindowThreadProcessId.restype = wintypes.DWORD
        callback = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
        user.EnumWindows.argtypes = [callback, wintypes.LPARAM]
        user.EnumWindows.restype = wintypes.BOOL
        # Keep the synthetic ABI controls independent of unrelated interactive
        # desktop size. Production still scans its caller's desktop and fails
        # closed at 4096; never switch the displayed desktop or relax local50ms.
        import uuid
        desktop_name = 'task407-r138-' + uuid.uuid4().hex
        original_desktop = user.GetThreadDesktop(kernel.GetCurrentThreadId())
        self.assertTrue(original_desktop)
        desktop = user.CreateDesktopW(desktop_name, None, None, 0, 0x00c3, None)
        self.assertTrue(desktop)
        attached = False
        canary = child = None
        windows_seen = set()
        events = {name: threading.Event() for name in ('MODAL', 'VISIBLE', 'HIDDEN', 'NONE')}
        def receive(line):
            for name, event in events.items():
                if line == ('R138_' + name).encode():
                    event.set()
                    return
            self.fail('Unexpected synthetic profile marker')
        try:
            # Start the same exact job/child while the caller remains on its
            # borrowed original desktop. The child explicitly attaches privately.
            with HostSignal() as modal, HostSignal() as visible, HostSignal() as hidden, HostSignal() as empty:
                code = '''import ctypes
from ctypes import wintypes
u=ctypes.WinDLL('user32',use_last_error=True)
u.CreateWindowExW.argtypes=[wintypes.DWORD,wintypes.LPCWSTR,wintypes.LPCWSTR,wintypes.DWORD,ctypes.c_int,ctypes.c_int,ctypes.c_int,ctypes.c_int,wintypes.HWND,wintypes.HMENU,wintypes.HINSTANCE,ctypes.c_void_p]
u.CreateWindowExW.restype=wintypes.HWND
u.EnableWindow.argtypes=[wintypes.HWND,wintypes.BOOL]
u.ShowWindow.argtypes=[wintypes.HWND,ctypes.c_int]
u.DestroyWindow.argtypes=[wintypes.HWND]
u.DestroyWindow.restype=wintypes.BOOL
u.OpenDesktopW.argtypes=[wintypes.LPCWSTR,wintypes.DWORD,wintypes.BOOL,wintypes.DWORD]
u.OpenDesktopW.restype=wintypes.HANDLE
u.GetThreadDesktop.argtypes=[wintypes.DWORD]
u.GetThreadDesktop.restype=wintypes.HANDLE
u.SetThreadDesktop.argtypes=[wintypes.HANDLE]
u.SetThreadDesktop.restype=wintypes.BOOL
u.CloseDesktop.argtypes=[wintypes.HANDLE]
u.CloseDesktop.restype=wintypes.BOOL
k=ctypes.WinDLL('kernel32',use_last_error=True)
k.GetCurrentThreadId.argtypes=[]
k.GetCurrentThreadId.restype=wintypes.DWORD
original=u.GetThreadDesktop(k.GetCurrentThreadId())
desktop=u.OpenDesktopW(R138_DESKTOP_NAME,0,False,0x00c3)
assert desktop
attached=False
owner=popup=None
try:
    assert u.SetThreadDesktop(desktop); attached=True
    owner=u.CreateWindowExW(0,'STATIC','R138_PRIVATE_OWNER',0x98000000,0,0,20,20,None,None,None,None)
    assert owner
    popup=u.CreateWindowExW(0,'STATIC','R138_PRIVATE_POPUP',0x90000000,0,0,20,20,owner,None,None,None)
    assert popup
    print('R138_MODAL',flush=True)
    GATE_MODAL
    u.EnableWindow(owner,True)
    print('R138_VISIBLE',flush=True)
    GATE_VISIBLE
    u.ShowWindow(popup,0); u.ShowWindow(owner,0)
    print('R138_HIDDEN',flush=True)
    GATE_HIDDEN
    assert u.DestroyWindow(popup); popup=None
    assert u.DestroyWindow(owner); owner=None
    print('R138_NONE',flush=True)
    GATE_NONE
finally:
    try:
        if popup: assert u.DestroyWindow(popup)
        if owner: assert u.DestroyWindow(owner)
    finally:
        try:
            if attached: assert u.SetThreadDesktop(original)
        finally:
            assert u.CloseDesktop(desktop)
'''
                code = code.replace('R138_DESKTOP_NAME', repr(desktop_name))
                for token, gate in (('MODAL', modal), ('VISIBLE', visible), ('HIDDEN', hidden), ('NONE', empty)):
                    code = code.replace('GATE_' + token, gate.child_wait)
                child = S.Command.spawn([sys.executable, '-B', '-c', code], timeout=12,
                    max_bytes=1024, tree_owned=True, on_line=receive)
                image = ctypes.create_unicode_buffer(32768)
                length = wintypes.DWORD(32768)
                self.assertTrue(child._job._kernel.QueryFullProcessImageNameW(
                    child.process._handle, 0, image, ctypes.byref(length)))
                actual_windll = ctypes.WinDLL
                watched = SimpleNamespace(**{name: mock.Mock(wraps=getattr(user, name)) for name in (
                    'EnumWindows', 'GetWindowThreadProcessId', 'IsWindowVisible', 'IsWindowEnabled', 'GetWindow')})
                def filter_pid(hwnd, pointer):
                    value = user.GetWindowThreadProcessId(hwnd, pointer)
                    if pointer._obj.value == child.process.pid:
                        windows_seen.add(hwnd)
                    return value
                watched.GetWindowThreadProcessId.side_effect = filter_pid
                def load(name, **kwargs):
                    return watched if name == 'user32' else actual_windll(name, **kwargs)
                self.assertTrue(events['MODAL'].wait(2), 'Synthetic native phase not ready')
                # Current-host fitness, not emulator/Qt equivalence or a guarantee
                # for a later desktop. Only the production fixed enum is asserted.
                with mock.patch.object(ctypes, 'WinDLL', side_effect=load):
                    if self.topology_api(child)(image.value) != self.TOPOLOGIES[3]:
                        raise self.CallerDesktopFitnessAssertion()
                self.assertTrue(user.SetThreadDesktop(desktop))
                attached = True
                canary = user.CreateWindowExW(0, 'STATIC', 'R138_PRIVATE_UNOWNED_TITLE',
                                             0x80000000, 0, 0, 20, 20, None, None, None, None)
                self.assertTrue(canary)
                for name, expected, gate in zip(('MODAL', 'VISIBLE', 'HIDDEN', 'NONE'),
                                               self.TOPOLOGIES, (modal, visible, hidden, empty)):
                    self.assertTrue(events[name].wait(2), 'Synthetic native phase not ready')
                    with mock.patch.object(ctypes, 'WinDLL', side_effect=load):
                        self.assertEqual(self.topology_api(child)(image.value), expected)
                    gate.release()
                for function in (watched.IsWindowVisible, watched.IsWindowEnabled, watched.GetWindow):
                    self.assertTrue(all(call.args[0] != canary for call in function.call_args_list))
                self.assertTrue(any(call.args[0] == canary for call in watched.GetWindowThreadProcessId.call_args_list))
                self.assertEqual(watched.EnumWindows.call_count, 5)
                self.assertTrue(child.settle(3))
                self.assertEqual(child.returncode, 0)
                self.assertTrue(child._tree_settled)
                self.assertTrue(all(not reader.is_alive() for reader in child._readers))
                self.assertEqual(child.take_output(), (b'', b''))
                self.assertIsNone(child._job._handle)
                self.assertIsNone(child._job._port)
        finally:
            # Nest cleanup so a failed settlement assertion cannot skip owned
            # windows, restoration of the borrowed desktop, or CloseDesktop.
            try:
                if child is not None:
                    self.assertTrue(child.cancel(3))
                    self.assertTrue(child.settle(1))
                    self.assertIsNotNone(child.process.poll())
                    self.assertTrue(all(not reader.is_alive() for reader in child._readers))
            finally:
                try:
                    if canary:
                        self.assertTrue(user.DestroyWindow(canary))
                        self.assertFalse(user.IsWindow(canary))
                    self.assertTrue(all(not user.IsWindow(hwnd) for hwnd in windows_seen))
                finally:
                    try:
                        if attached:
                            self.assertTrue(user.SetThreadDesktop(original_desktop))
                    finally:
                        self.assertTrue(user.CloseDesktop(desktop))


class ReservedPhaseTests(unittest.TestCase):
    """R121 phase races use explicit clocks and synchronization, not timed luck."""
    @contextmanager
    def preparing(self, *, initial=False):
        clock = [100.0]
        release = threading.Event()
        sleeping = threading.Event()
        owned = FakeOwned()
        adapter = FixtureAdapter(owned)
        observer = S.Observer(PACKAGE)
        if initial:
            self.assertTrue(observer.arm(epoch='host', after_ns=100, source=S.Command.pending()))
        class HeldSupervisor(S.Supervisor):
            def _guard(self):
                release.wait(3)
                super()._guard()
        values = dict(preparation=180, instrumentation=420, command=15,
                      process_wait=.01, stop=5, settle=2, snapshot_budget=15)
        if 'postboot_preparation' in S.Caps.__dataclass_fields__:
            values['postboot_preparation'] = 45
        with mock.patch.object(S, 'time', SimpleNamespace(monotonic=lambda: clock[0])):
            guardian = HeldSupervisor(owned=owned, commands=adapter, observer=observer,
                avd=AVD, serial=SERIAL, contract=S.LaunchContract('host', True),
                caps=S.Caps(**values), instrument=S.Command.completed(0) if initial else None)
            original_wait = guardian._condition.wait
            def condition_wait(timeout):
                sleeping.set()
                return original_wait(timeout)
            guardian._condition.wait = condition_wait
            try:
                yield guardian, owned, observer, clock, release, sleeping
            finally:
                guardian.abort()
                release.set()
                self.assertTrue(guardian._finished.wait(3), 'Synchronized guardian fixture must settle')

    def handoff(self, guardian):
        value = getattr(guardian, 'complete_boot', None)
        self.assertTrue(callable(value), 'Atomic one-shot boot handoff is required')
        return value

    def test_r121_handoff_is_one_shot_launch_fixed_and_observations_do_not_renew(self):
        with self.preparing() as (guardian, _, observer, clock, _, _):
            self.assertEqual(guardian._preparation_deadline, 280)
            clock[0] = 265.655
            self.assertTrue(self.handoff(guardian)())
            self.assertAlmostEqual(guardian._preparation_deadline, 310.655)
            self.assertEqual(guardian._preparation_ceiling, 325)
            clock[0] += 1
            self.assertFalse(self.handoff(guardian)())
            self.assertAlmostEqual(guardian.preparation_remaining(), 44)
            self.assertTrue(guardian.wait_preparation(1, S.Command.completed(0)))
            self.assertTrue(observer.arm(epoch='host', after_ns=100, source=S.Command.pending()))
            observe = getattr(guardian, 'preparation_observation', None)
            self.assertTrue(callable(observe), 'Fixed operational timing observation required')
            row = observe()
            self.assertEqual(row['phase'], 'POSTBOOT')
            self.assertEqual(row['phase_budget_ms'], 45000)
            self.assertEqual(row['phase_elapsed_ms'], 1000)
            self.assertEqual(row['phase_remaining_ms'], 44000)
            self.assertEqual(row['total_budget_ms'], 225000)
            self.assertAlmostEqual(guardian._preparation_deadline, 310.655)
            self.assertEqual(guardian._preparation_ceiling, 325)

    def test_r121_at_or_after_boot_expiry_rejects_even_before_guardian_runs(self):
        for at in (280, 280.001):
            with self.subTest(at=at), self.preparing() as (guardian, _, _, clock, _, _):
                clock[0] = at
                self.assertFalse(self.handoff(guardian)())
                self.assertEqual(guardian._preparation_deadline, 280)
                self.assertEqual(guardian.preparation_remaining(), 0)

    def test_r121_abort_observer_terminal_and_stopped_guardian_cannot_handoff(self):
        for state in ('abort', 'observer_failed', 'own_anr', 'source_failed', 'terminal'):
            with self.subTest(state=state), self.preparing() as (guardian, _, observer, _, _, _):
                if state == 'abort':
                    guardian.abort()
                elif state == 'observer_failed':
                    observer.failed()
                elif state in ('own_anr', 'source_failed'):
                    source = S.Command.pending()
                    self.assertTrue(observer.arm(epoch='host', after_ns=100, source=source))
                    if state == 'own_anr':
                        observer.am_anr('am_anr', '[0,42,' + PACKAGE + ',0,opaque]',
                                        timestamp_ns=101, epoch='host')
                    else:
                        source.failure = 'STREAM_FAILED'
                else:
                    with guardian._condition:
                        guardian._accepting = False
                self.assertFalse(self.handoff(guardian)())
                self.assertEqual(guardian._preparation_deadline, 280)

    def test_r121_configured_attachment_requires_handoff_even_with_live_observer(self):
        with self.preparing() as (guardian, _, observer, _, _, _):
            self.assertTrue(observer.arm(epoch='host', after_ns=100, source=S.Command.pending()))
            child = S.Command.pending()
            self.assertFalse(guardian.attach_instrumentation(child), 'Arming must not bypass boot handoff')
            self.assertTrue(child.cancelled)
            self.assertEqual(guardian._preparation_deadline, 280)

    def test_r121_configured_initial_instrument_cannot_bypass_handoff(self):
        with self.preparing(initial=True) as (guardian, _, _, _, release, _):
            release.set()
            self.assertTrue(guardian._finished.wait(3))
            report = guardian.wait(0)
            self.assertFalse(report.eligible_for_oracle, 'Initial instrument must not bypass phase admission')
            self.assertEqual(report.cause, 'OBSERVER_NOT_ADMITTED')

    def test_r121_guardian_autonomously_enforces_boot_and_postboot(self):
        for phase in ('BOOT', 'POSTBOOT'):
            with self.subTest(phase=phase), self.preparing() as (guardian, owned, _, clock, release, sleeping):
                if phase == 'POSTBOOT':
                    clock[0] = 265.655
                    self.assertTrue(self.handoff(guardian)())
                else:
                    self.assertTrue(hasattr(guardian, 'complete_boot'), 'Explicit phase contract required')
                release.set()
                self.assertTrue(sleeping.wait(2), 'Guardian must independently wait on active deadline')
                with guardian._condition:
                    clock[0] = guardian._preparation_deadline
                    guardian._condition.notify_all()
                # No caller wait_preparation/wait is used to drive expiry or teardown.
                self.assertTrue(guardian._finished.wait(3))
                self.assertTrue(owned.stopped.is_set())
                report = guardian.wait(0)
                self.assertEqual(report.cause, 'PREPARATION_CAP')
                self.assertTrue(report.shutdown_verified)
                self.assertFalse(report.eligible_for_oracle)
                self.assertFalse(self.handoff(guardian)())

    def test_r121_postboot_expiry_rejects_attachment_and_duplicate_without_scheduling(self):
        with self.preparing() as (guardian, _, observer, clock, _, _):
            clock[0] = 279.999
            self.assertTrue(self.handoff(guardian)())
            self.assertLessEqual(guardian._preparation_deadline, 325)
            self.assertTrue(observer.arm(epoch='host', after_ns=100, source=S.Command.pending()))
            clock[0] = guardian._preparation_deadline
            child = S.Command.pending()
            self.assertFalse(self.handoff(guardian)())
            self.assertFalse(guardian.attach_instrumentation(child))
            self.assertTrue(child.cancelled)
            self.assertEqual(guardian.preparation_remaining(), 0)

    def test_r121_handoff_does_not_change_instrumentation_or_terminal_precedence(self):
        for terminal, expected in (('natural', 'NATURAL_COMPLETION'), ('anr', 'OWN_ANR'),
                                   ('lost', 'OBSERVER_FAILED'), ('abort', 'ABORTED')):
            with self.subTest(terminal=terminal), self.preparing() as (guardian, _, observer, clock, release, _):
                self.assertTrue(self.handoff(guardian)())
                self.assertTrue(observer.arm(epoch='host', after_ns=100, source=S.Command.pending()))
                child = S.Command.completed(0, b'HOST_FIXED')
                clock[0] = 144.999
                self.assertTrue(guardian.attach_instrumentation(child))
                self.assertEqual(guardian._caps.instrumentation, 420)
                self.assertFalse(self.handoff(guardian)())
                if terminal == 'anr':
                    observer.am_anr('am_anr', '[0,42,' + PACKAGE + ',0,opaque]', timestamp_ns=101, epoch='host')
                elif terminal == 'lost':
                    observer.failed()
                elif terminal == 'abort':
                    guardian.abort()
                release.set()
                self.assertTrue(guardian._finished.wait(3))
                report = guardian.wait(0)
                self.assertEqual(report.cause, expected)
                self.assertEqual(report.eligible_for_oracle, terminal == 'natural')
                if terminal != 'natural':
                    with self.assertRaises(S.Blocked):
                        guardian.take_instrumentation_output()

    def test_r121_reservation_is_finite_and_generic_zero_default_is_preserved(self):
        self.assertIn('postboot_preparation', S.Caps.__dataclass_fields__, 'Finite declared reserve missing')
        self.assertEqual(S.Caps(420, 15, 5, 5, 2, 15).postboot_preparation, 0)
        for invalid in (-1, float('inf'), float('nan'), True, '45'):
            with self.subTest(kind=type(invalid).__name__), self.assertRaises(S.Blocked):
                S.Caps(420, 15, 5, 5, 2, 15, preparation=180, postboot_preparation=invalid)


class AccountingEnvelopeTests(unittest.TestCase):
    def test_r121_outer_envelope_counts_each_existing_cancellation_tail(self):
        caps = S.Caps(420, 15, 5, 5, 2, 15, preparation=180, postboot_preparation=45)
        self.assertTrue(hasattr(caps, 'teardown_budget'), 'Explicit actual-path teardown envelope required')
        # instrument + observer; identity/console/disconnect/absence, each with
        # timeout+finally cancellation; one snapshot transaction+last cancellation;
        # original wait + tree stop + independent terminal wait. No cushion.
        self.assertEqual(caps.teardown_budget, 2 * 2 + 4 * (15 + 2) + (15 + 2) + 2 * 5 + 5)
        self.assertEqual(caps.session_budget, 180 + 45 + 420 + caps.teardown_budget)
        self.assertEqual(caps.session_budget, 749)
        self.assertEqual(dataclasses.asdict(caps), dict(instrumentation=420, command=15,
            process_wait=5, stop=5, settle=2, snapshot_budget=15,
            preparation=180, postboot_preparation=45))

    def test_r121_explicit_launch_anchor_rejects_unusable_clocks_before_guardian_start(self):
        caps = S.Caps(420, 15, 5, 5, 2, 15, preparation=180, postboot_preparation=45)
        with mock.patch.object(S, 'time', SimpleNamespace(monotonic=lambda: 100)), \
             mock.patch.object(S.threading, 'Thread') as thread:
            for invalid in (101, float('inf'), float('nan'), True, '99'):
                with self.subTest(kind=type(invalid).__name__), self.assertRaises(S.Blocked):
                    S.Supervisor(owned=FakeOwned(), commands=None, observer=S.Observer(PACKAGE),
                        avd=AVD, serial=SERIAL, contract=S.LaunchContract('host', True),
                        caps=caps, launch_started=invalid)
            thread.assert_not_called()

    def test_r121_derived_envelope_must_be_finite_without_relaxing_local_caps(self):
        with self.assertRaises(S.Blocked):
            S.Caps(1e308, 1e308, 5, 5, 2, 15, preparation=180, postboot_preparation=45)


class SafeResult(unittest.TestResult):
    """Finite test projection; never format raw assertion payloads or tracebacks."""
    def __init__(self):
        super().__init__()
        self.safe_failures, self.safe_errors, self.passed = [], [], []
    def addSuccess(self, test):
        self.passed.append(test.id())
    def addFailure(self, test, error):
        self.safe_failures.append({'id': test.id(), 'exception_type': error[0].__name__})
    def addError(self, test, error):
        self.safe_errors.append({'id': test.id(), 'exception_type': error[0].__name__})
    def addSubTest(self, test, subtest, error):
        if error is not None:
            self.safe_failures.append({'id': test.id(), 'exception_type': error[0].__name__})
    def wasSuccessful(self):
        return not self.safe_failures and not self.safe_errors


if __name__ == '__main__':
    result = SafeResult()
    unittest.defaultTestLoader.loadTestsFromModule(sys.modules[__name__]).run(result)
    report = {'tests_run': result.testsRun, 'passed': result.passed,
              'failures': result.safe_failures, 'errors': result.safe_errors,
              'success': result.wasSuccessful()}
    sys.stdout.write(json.dumps(report, sort_keys=True) + '\n')
    sys.exit(0 if result.wasSuccessful() else 1)
