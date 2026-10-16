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
            return S.Command.completed(0, b'OK\n')
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

    def context(self, instrument=None, adapter_changes=None, caps=None, contract=None, observer=None):
        owned = FakeOwned()
        adapter = FixtureAdapter(owned, adapter_changes)
        observer = observer if observer is not None else self.observer()
        instrument = instrument if instrument is not None else S.Command.pending()
        supervisor = S.Supervisor(owned=owned, commands=adapter, instrument=instrument,
                                  observer=observer, avd=AVD, serial=SERIAL,
                                  contract=contract or S.LaunchContract(watchdog_id='6cca814c', no_snapshot_save=True),
                                  caps=caps or S.Caps(instrumentation=.15, command=.06, process_wait=.02,
                                                      stop=.1, settle=.3, snapshot_budget=.2))
        return supervisor, owned, adapter, observer, instrument

    def report(self, supervisor):
        result = supervisor.wait(3)
        self.assertIsNotNone(result, 'Supervisor exceeded host fixture bound')
        return result

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
            self.assertEqual(self.report(supervisor).status, 'BLOCKED')

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
            adapter = FixtureAdapter(FakeOwned(), {'console_stop': (0, b'OK\n')})
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


if __name__ == '__main__':
    unittest.main(verbosity=2)
