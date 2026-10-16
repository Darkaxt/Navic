"""Host-only caller contracts; no SDK, emulator, ADB or Android process is run."""
import importlib.util
import json
from pathlib import Path
import shlex
import hashlib
import sys
import io
import dataclasses
import threading
import time
from unittest import mock
from contextlib import redirect_stdout
from types import SimpleNamespace
import unittest

PATH = Path(__file__).with_name('task407-runtime-runner.py')
if PATH.exists():
    spec = importlib.util.spec_from_file_location('task407_runtime_runner', PATH)
    runner = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(runner)
else:
    runner = SimpleNamespace()

PACKAGE = 'paige.navic.task407.runtime'
CLASS = 'paige.navic.ui.screens.reader.ReaderNormalProductionStartupRuntimeTest'
METHODS = (
    'originalRootRequiresExecutableOriginalPassiveProducerBeforeBootstrapDisplay',
    'originalRootPublishesBootstrapThenTwoImmediateSettledTurns',
    'cancelOriginalPendingBootstrapThenCloseReturnsExactPhysicalOwners',
)
LABEL = 'E407.Observation.PersistenceDescriptorUnavailable'


def transcript(methods=METHODS):
    lines = []
    for name in methods:
        for code in (1, 0):
            lines.extend(('INSTRUMENTATION_STATUS: class=' + CLASS,
                          'INSTRUMENTATION_STATUS: test=' + name,
                          'INSTRUMENTATION_STATUS_CODE: ' + str(code)))
    lines.append('INSTRUMENTATION_CODE: -1')
    return '\n'.join(lines)


class Observer:
    def __init__(self):
        self.armed = True
        self.failed_count = 0
        self.events = []

    def failed(self):
        self.failed_count += 1

    def am_anr(self, tag, payload, **context):
        self.events.append((tag, payload, context))


class CallerTests(unittest.TestCase):
    def api(self, name):
        value = getattr(runner, name, None)
        self.assertTrue(callable(value), 'Missing caller API: ' + name)
        return value

    def test_genuine_guest_cursor_is_integer_nanoseconds_not_float(self):
        parse = self.api('parse_cursor')
        self.assertEqual(parse(b'1760000000.123456789\r\n'), 1760000000123456789)
        for value in (b'0.000000000\n', b'1.2\n', b'1e9\n',
                      b'1760000000.123456789\nextra', b'nan\n'):
            with self.subTest(value=value), self.assertRaises(ValueError):
                parse(value)

    def test_events_source_uses_selected_nonpty_shell_and_actual_cursor_minus_one_ns(self):
        command = self.api('observer_argv')('adb.exe', 1760000000123456789)
        self.assertEqual(command[:5], ['adb.exe', '-s', 'emulator-5554', 'shell', '-T'])
        remote = shlex.split(command[5])
        self.assertEqual(remote, ['logcat', '-b', 'events', '-v', 'threadtime,epoch,nsec',
                                 '-T', '1760000000.123456788', '-s', '-e',
                                 r'^\[[^,]*,[^,]*,paige\.navic\.task407\.runtime,',
                                 '-m', '1', 'am_anr:I'])
        self.assertNotIn('--pid', remote)
        self.assertNotIn('--uid', remote)

    def test_decoder_passes_only_fresh_valid_own_event_in_memory(self):
        consume = self.api('consume_event')
        observer = Observer()
        payload = '[0,123,' + PACKAGE + ',-1,PRIVATE_REASON,[nested]]'
        # The real supervisor owns ABI validation; this predicate records delegation.
        checked = []
        def own(tag, value, package):
            checked.append((tag, value, package))
            return True
        consume(observer, ('1760000000.123456790  1000  1001 I am_anr  : ' + payload).encode(),
                epoch='host-fixture', after_ns=1760000000123456789, is_own=own)
        self.assertEqual(checked, [('am_anr', payload, PACKAGE)])
        self.assertEqual(observer.failed_count, 0)
        self.assertEqual(observer.events, [('am_anr', payload,
                         {'timestamp_ns': 1760000000123456790, 'epoch': 'host-fixture'})])

    def test_stale_equal_malformed_or_unattributable_candidate_stops_unknown(self):
        consume = self.api('consume_event')
        own = lambda *args: True
        for line, validator in (
            (b'1760000000.123456788 1000 1001 I am_anr: [0,123,x,0,private]', own),
            (b'1760000000.123456789 1000 1001 I am_anr: [0,123,x,0,private]', own),
            (b'unrecognized private payload', own),
            (b'1760000000.123456790 1000 1001 I am_anr: malformed', lambda *args: False),
            (b'1760000000.123456790 1000 1001 I other_tag: private', own),
        ):
            with self.subTest(line=line):
                observer = Observer()
                consume(observer, line, epoch='host-fixture',
                        after_ns=1760000000123456789, is_own=validator)
                self.assertEqual(observer.failed_count, 1)
                self.assertEqual(observer.events, [])

    def test_candidate_before_arm_fails_closed_instead_of_being_dropped(self):
        observer = Observer()
        observer.armed = False
        self.api('consume_event')(observer,
            b'1760000000.123456790 1000 1001 I am_anr: [0,123,own,0,private]',
            epoch='host-fixture', after_ns=1760000000123456789, is_own=lambda *args: True)
        self.assertEqual(observer.failed_count, 1)
        self.assertEqual(observer.events, [])

    def test_exact_events_banner_is_not_candidate_or_coverage_proof(self):
        observer = Observer()
        self.api('consume_event')(observer, b'--------- beginning of events',
                                  epoch='host-fixture', after_ns=1, is_own=lambda *args: False)
        self.assertEqual(observer.failed_count, 0)
        self.assertEqual(observer.events, [])

    def test_lifecycle_adapter_has_only_selected_owned_console_operations(self):
        argv = self.api('lifecycle_argv')
        self.assertEqual(argv('adb.exe', 'identity'),
                         ['adb.exe', '-s', 'emulator-5554', 'emu', 'avd', 'name'])
        self.assertEqual(argv('adb.exe', 'snapshot_delete', 'default_boot')[-5:],
                         ['emu', 'avd', 'snapshot', 'del', 'default_boot'])
        for action, argument in (('install', None), ('kill-server', None),
                                 ('snapshot_delete', '../other'), ('identity', 'other')):
            with self.subTest(action=action), self.assertRaises(ValueError):
                argv('adb.exe', action, argument)

    def test_exact_original_three_natural_terminals_are_required(self):
        project = self.api('project_instrumentation')
        passed = self.api('original_three_passed')
        good = project(transcript(), {LABEL})
        self.assertTrue(passed(good, exit_code=0, source_drift=False))
        for text in (transcript(METHODS[:2]), transcript(METHODS + METHODS[:1]),
                     transcript().replace('STATUS_CODE: 0', 'STATUS_CODE: -2', 1),
                     transcript().replace(CLASS, 'other.class'),
                     transcript() + '\nINSTRUMENTATION_FAILED: PRIVATE_REASON',
                     transcript().replace('INSTRUMENTATION_CODE: -1', '')):
            with self.subTest(text=text):
                self.assertFalse(passed(project(text, {LABEL}), exit_code=0, source_drift=False))
        self.assertFalse(passed(good, exit_code=1, source_drift=False))
        self.assertFalse(passed(good, exit_code=0, source_drift=True))

    def test_only_source_qualified_labels_and_fixed_original_names_are_projected(self):
        project = self.api('project_instrumentation')
        text = '\n'.join(('INSTRUMENTATION_STATUS: class=' + CLASS,
                          'INSTRUMENTATION_STATUS: test=' + METHODS[0],
                          'INSTRUMENTATION_STATUS_CODE: 1',
                          'INSTRUMENTATION_STATUS: stream=' + LABEL + ' PRIVATE_REASON https://private.invalid',
                          'INSTRUMENTATION_STATUS: stream=E407.Unknown.PrivateCanary',
                          'INSTRUMENTATION_STATUS_CODE: 2',
                          'INSTRUMENTATION_STATUS: test=PRIVATE_METHOD',
                          'INSTRUMENTATION_STATUS_CODE: -2'))
        result = project(text, {LABEL})
        safe = json.dumps(result)
        for private in ('PRIVATE_REASON', 'private.invalid', 'PrivateCanary', 'PRIVATE_METHOD'):
            self.assertNotIn(private, safe)
        self.assertEqual(result['fixed_labels_by_original_method'][METHODS[0]], [LABEL])
        self.assertEqual(result['events'][-1]['method'], 'UNRECOGNIZED_TEST')

    def test_fixed_preflight_reason_is_type_safe_and_allowlisted(self):
        fixed = self.api('fixed_preflight_reason')
        cases = ((ValueError(), 'UNEXPECTED_EXECUTION'),
                 (ValueError('PRIVATE_READINESS_CANARY'), 'UNEXPECTED_EXECUTION'),
                 (ValueError(['PRIVATE_READINESS_CANARY']), 'UNEXPECTED_EXECUTION'),
                 (ValueError({'private': 'PRIVATE_READINESS_CANARY'}), 'UNEXPECTED_EXECUTION'),
                 (ValueError('PREFLIGHT_COMMAND_INCOMPLETE'), 'PREFLIGHT_COMMAND_INCOMPLETE'))
        for error, expected in cases:
            with self.subTest(payload=type(error.args[0]).__name__ if error.args else 'none'):
                self.assertEqual(fixed(error), expected)


WITNESS_KEY = 'E407.StartupWitness.V1'


def startup_witness(method=METHODS[0]):
    return {'version': 1, 'method': method,
            'anchor': 'OBSERVATION_INSTALL_ATTEMPT',
            'records': [
                {'sequence': 0, 'elapsed_ms': 0, 'event': 'OBSERVATION_INSTALL_ATTEMPT'},
                {'sequence': 1, 'elapsed_ms': 10, 'event': 'LOGGER_BRIDGE_READY'},
                {'sequence': 2, 'elapsed_ms': 10, 'event': 'SAMPLE_ENGINE_INITIALIZATION_COMPLETED'},
                {'sequence': 3, 'elapsed_ms': 10001, 'event': 'SAMPLE_TERMINAL_MATERIAL_TIMEOUT'},
                {'sequence': 4, 'elapsed_ms': 10002, 'event': 'LOGGER_OPEN_PUBLICATION_DISPATCHED'}],
            'dropped_records': 0, 'clock_clamped': False,
            'observer_continuity': 'UNPROVEN'}


def witness_status(value, *, duplicate=False, code=2, extra=()):
    wire = json.dumps(value, separators=(',', ':')) if isinstance(value, dict) else value
    lines = ['INSTRUMENTATION_STATUS: ' + WITNESS_KEY + '=' + wire]
    if duplicate:
        lines += lines[:]
    return '\n'.join(lines + list(extra) + ['INSTRUMENTATION_STATUS_CODE: ' + str(code)])


def with_witness(value, method=METHODS[0], **kwargs):
    return '\n'.join(('INSTRUMENTATION_STATUS: class=' + CLASS,
                      'INSTRUMENTATION_STATUS: test=' + method,
                      'INSTRUMENTATION_STATUS_CODE: 1', witness_status(value, **kwargs)))


class StartupWitnessProjectionTests(unittest.TestCase):
    """Real projector, RAM-only closed fixtures; diagnostics are never test oracles."""
    def project(self, text, *, qualified=True):
        return runner.project_instrumentation(text, {LABEL, WITNESS_KEY} if qualified else {LABEL})

    def retained(self, result):
        return result.get('startup_witness_by_original_method', {})

    def rejected(self, text, *, qualified=True):
        result = self.project(text, qualified=qualified)
        self.assertEqual(self.retained(result), {})
        self.assertGreater(result.get('startup_witness_rejection_count', 0), 0)
        self.assertNotIn('R123_PRIVATE_CANARY', json.dumps(result))
        return result

    def test_valid_source_qualified_timeline_retains_arrival_order_and_sample_distinction(self):
        value = startup_witness()
        result = self.project(with_witness(value))
        self.assertEqual(self.retained(result), {METHODS[0]: value})
        self.assertEqual(result['startup_witness_rejection_count'], 0)
        self.assertEqual(len(result['events']), 1, 'Diagnostic status is not a test terminal')
        self.assertEqual(result['fixed_labels'], [], 'Witness key is not a legacy cumulative label')

    def test_valid_fixture_timelines_are_attributed_separately_without_sorting(self):
        first, second = startup_witness(), startup_witness(METHODS[1])
        second['records'][1]['event'] = 'LOGGER_CURRENT_DESTINATION_QUERY_DISPATCHED'
        result = self.project(with_witness(first) + '\n' + with_witness(second, METHODS[1]))
        self.assertEqual(self.retained(result), {METHODS[0]: first, METHODS[1]: second})

    def test_unqualified_wrong_class_wrong_method_and_unstarted_timeline_rejected(self):
        value = startup_witness()
        self.rejected(with_witness(value), qualified=False)
        self.rejected(with_witness(value).replace('class=' + CLASS, 'class=other.class'))
        self.rejected(with_witness(startup_witness(METHODS[1])))
        self.rejected(witness_status(value))
        self.rejected(with_witness(value, extra=('INSTRUMENTATION_STATUS: class=other.class',)))
        self.rejected(with_witness(value, extra=('INSTRUMENTATION_STATUS: test=' + METHODS[1],)))
        # A foreign framework start must not inherit the previously active fixture.
        started = with_witness(value).split('INSTRUMENTATION_STATUS: ' + WITNESS_KEY, 1)[0]
        foreign_start = '\n'.join(('INSTRUMENTATION_STATUS: class=other.class',
                                   'INSTRUMENTATION_STATUS: test=' + METHODS[1],
                                   'INSTRUMENTATION_STATUS_CODE: 1'))
        self.rejected(started + foreign_start + '\n' + witness_status(value))
        # Even fixed names cannot qualify a status-less/free-text JSON projection.
        unparsed = self.project(started + 'INSTRUMENTATION_STATUS: stream=' + json.dumps(value))
        self.assertEqual(self.retained(unparsed), {})
        self.rejected(with_witness(value, extra=('INSTRUMENTATION_STATUS: private=R123_PRIVATE_CANARY',)))

    def test_duplicate_json_keys_status_keys_and_per_method_timeline_invalidate_history(self):
        value = startup_witness()
        self.rejected(with_witness(value, duplicate=True))
        wire = json.dumps(value).replace('"version": 1', '"version": 1, "version": 1')
        self.rejected(with_witness(wire))
        self.rejected(with_witness(value) + '\n' + witness_status(value))
        self.rejected(with_witness(value, extra=('INSTRUMENTATION_STATUS: class=' + CLASS,
                                                'INSTRUMENTATION_STATUS: class=' + CLASS)))
        self.rejected(with_witness(value).rsplit('INSTRUMENTATION_STATUS_CODE: 2', 1)[0])
        duplicate_event = startup_witness()
        duplicate_event['records'][2]['event'] = duplicate_event['records'][1]['event']
        self.rejected(with_witness(duplicate_event))

    def test_malformed_private_extra_fields_and_unbounded_scalars_are_rejected(self):
        base = startup_witness()
        cases = []
        for key, bad in (('version', True), ('version', 2), ('method', 'R123_PRIVATE_CANARY'),
                         ('anchor', 'R123_PRIVATE_CANARY'), ('dropped_records', -1),
                         ('dropped_records', 65536), ('dropped_records', True),
                         ('clock_clamped', 0), ('observer_continuity', 'CONTINUOUS'),
                         ('records', []), ('records', 'R123_PRIVATE_CANARY')):
            value = json.loads(json.dumps(base))
            value[key] = bad
            cases.append(value)
        value = dict(base, private='R123_PRIVATE_CANARY')
        cases.append(value)
        for bad in (dict(sequence=1, elapsed_ms=0, event='OBSERVATION_INSTALL_ATTEMPT'),
                    dict(sequence=0, elapsed_ms=1, event='OBSERVATION_INSTALL_ATTEMPT'),
                    dict(sequence=0, elapsed_ms=0, event='R123_PRIVATE_CANARY')):
            value = json.loads(json.dumps(base))
            value['records'][0] = bad
            cases.append(value)
        for field, bad in (('sequence', True), ('elapsed_ms', -1), ('elapsed_ms', 180001),
                           ('elapsed_ms', 1.5), ('event', 'R123_PRIVATE_CANARY'),
                           ('private', 'R123_PRIVATE_CANARY')):
            value = json.loads(json.dumps(base))
            value['records'][1][field] = bad
            cases.append(value)
        for value in cases:
            with self.subTest(case=cases.index(value)):
                self.rejected(with_witness(value))
        for wire in ('{', '[]', 'null', 'R123_PRIVATE_CANARY', ' ' * 16385):
            self.rejected(with_witness(wire))

    def test_sequence_elapsed_regression_and_record_overflow_rejected(self):
        for field, bad in (('sequence', 8), ('elapsed_ms', 9)):
            value = startup_witness()
            value['records'][2][field] = bad
            self.rejected(with_witness(value))
        value = startup_witness()
        value['records'] = [value['records'][0]] + [
            dict(sequence=index, elapsed_ms=index, event='LOGGER_BRIDGE_READY')
            for index in range(1, 65)]
        self.rejected(with_witness(value))

    def test_bounded_truncation_gap_and_clamped_clock_are_retained_not_hidden(self):
        value = startup_witness()
        value.update(dropped_records=65535, clock_clamped=True, observer_continuity='GAP_OBSERVED')
        # Every source-closed event can occur once; the recorder retains first occurrences only.
        value['records'] = [value['records'][0]] + [
            dict(sequence=index, elapsed_ms=180000, event=event)
            for index, event in enumerate(sorted(runner._STARTUP_WITNESS_EVENTS -
                {'OBSERVATION_INSTALL_ATTEMPT'}), start=1)]
        result = self.project(with_witness(value))
        self.assertEqual(self.retained(result), {METHODS[0]: value})

    def test_diagnostics_preserve_original_failure_and_pass_oracles_and_legacy_labels(self):
        for failure in (False, True):
            text = transcript()
            if failure:
                text = text.replace('STATUS_CODE: 0', 'STATUS_CODE: -2', 1)
            marker = 'INSTRUMENTATION_STATUS_CODE: 1'
            # Real sendStatus(2) is emitted inside the currently started original method.
            augmented = text.replace(marker, marker + '\n' + witness_status(startup_witness(),
                extra=('INSTRUMENTATION_STATUS: stream=' + LABEL,)), 1)
            before, after = self.project(text), self.project(augmented)
            self.assertEqual(before['events'], after['events'])
            self.assertEqual(before['terminal_codes'], after['terminal_codes'])
            self.assertEqual(after['fixed_labels_by_original_method'][METHODS[0]], [LABEL])
            self.assertEqual(runner.original_three_passed(after, exit_code=0, source_drift=False), not failure)
            self.assertEqual(self.retained(after), {METHODS[0]: startup_witness()})
        self.assertFalse(runner.original_three_passed(self.project(with_witness(startup_witness())),
                                                    exit_code=0, source_drift=False))


def load_supervisor():
    spec = importlib.util.spec_from_file_location('caller_host_supervisor',
        Path(__file__).with_name('task407-runtime-supervisor.py'))
    value = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = value
    spec.loader.exec_module(value)
    return value


def host_record():
    # RAM-only synthetic consent data for an injected HOST fixture, not authority.
    return {'kind': 'task407-owned-emulator-runtime-session',
            'serial': 'emulator-5554', 'avd': 'NavicReaderLab',
            'scope': 'original-three-with-independent-supervision',
            'user_session_authorized': True, 'peer_coordination_complete': True,
            'no_snapshot_save': True, 'preserve_app_data': True, 'clear_logcat': False,
            'watchdog_id': 'host-fixture', 'adb': 'fixture-adb.exe',
            'emulator': 'fixture-emulator.exe', 'apk_path': 'fixture-only.apk'}


class HostDevice:
    """Real guardian/owned Windows job, synthetic selected-device replies only."""
    def __init__(self, *, boot=b'1\n', prearm_event=False, hang_install=False,
                 clean_absence=True, readiness=None, boot_replies=None, before_reply=None,
                 install_reply=None, preparation_wait=None, synthetic_owned=False,
                 guardian_gate=None):
        self.real = load_supervisor()
        self.calls = []
        self.call_times = []
        self.boot_replies = iter(boot_replies) if boot_replies is not None else None
        self.boot_clients = []
        self.before_reply = before_reply
        self.install_reply = install_reply
        self.preparation_wait = preparation_wait
        self.boot = boot
        self.prearm_event = prearm_event
        self.hang_install = hang_install
        self.clean_absence = clean_absence
        self.readiness = readiness or {}
        self.readiness_used = set()
        self.install = self.launch = None
        self.supervisor_created = False
        self.instrument_seen = False
        self.guardian = None
        self.taken_after_shutdown = False
        outer = self
        real = self.real

        def readiness_reply(step, default):
            if ((step in outer.readiness_used and step != 'SYS_BOOT_COMPLETED')
                    or step not in outer.readiness):
                return default
            outer.readiness_used.add(step)
            value = outer.readiness[step]
            if isinstance(value, BaseException):
                raise value
            if value == 'pending':
                return real.Command.pending()
            if value == 'unsettled':
                child = real.Command.completed(0)
                child.settle = lambda timeout: False
                return child
            if isinstance(value, tuple):
                return real.Command.completed(*value)
            return value

        class Commands:
            @staticmethod
            def spawn(argv, **kwargs):
                outer.calls.append((tuple(argv), kwargs))
                outer.call_times.append(time.monotonic())
                if outer.before_reply is not None:
                    outer.before_reply(outer, argv)
                if argv[0] == 'fixture-emulator.exe':
                    if synthetic_owned:
                        outer.launch = real.Command.pending()
                        stopped = threading.Event()
                        def wait(timeout):
                            if not stopped.is_set():
                                raise real.subprocess.TimeoutExpired('HOST_FIXTURE', timeout)
                            return 0
                        outer.launch.process = SimpleNamespace(wait=wait,
                            poll=lambda: 0 if stopped.is_set() else None)
                        def stop_tree(timeout):
                            stopped.set()
                            outer.launch.returncode = 0
                            outer.launch._finish()
                            return True
                        outer.launch.stop_tree = stop_tree
                        return outer.launch
                    outer.launch = real.Command.spawn(
                        [sys.executable, '-B', '-c',
                         'import threading; threading.Event().wait(10)'],
                        timeout=5, max_bytes=65536, tree_owned=True)
                    return outer.launch
                if argv[:3] != ['fixture-adb.exe', '-s', 'emulator-5554']:
                    raise AssertionError('Fixture received an unselected command')
                args = argv[3:]
                if args == ['get-state']:
                    if not outer.supervisor_created or outer.clean_absence:
                        return real.Command.completed(1, b'',
                            b"error: device 'emulator-5554' not found\n")
                    return real.Command.completed(1, b'', b'offline\n')
                if not outer.supervisor_created:
                    raise AssertionError('Preflight began before guardian ownership')
                if args == ['wait-for-device']:
                    return readiness_reply('WAIT_FOR_DEVICE', real.Command.completed(0))
                if args == ['wait-for-disconnect']:
                    return real.Command.completed(0)
                if args == ['emu', 'avd', 'name']:
                    return readiness_reply('OWNED_AVD_IDENTITY',
                                           real.Command.completed(0, b'NavicReaderLab\r\r\nOK\r\r\n'))
                if args == ['emu', 'avd', 'snapshot', 'list']:
                    return real.Command.completed(0, b'There are no snapshots available.\nOK\n')
                if args == ['emu', 'kill']:
                    return real.Command.completed(0, b'OK: killing emulator, bye bye\n')
                if args[:3] == ['install', '-r', '-t']:
                    reply = outer.install_reply
                    if callable(reply):
                        reply = reply(outer)
                    if isinstance(reply, BaseException):
                        raise reply
                    outer.install = (real.Command.completed(*reply) if isinstance(reply, tuple)
                        else reply if reply is not None else
                        real.Command.pending() if outer.hang_install else
                        real.Command.completed(0, b'Success\n'))
                    return outer.install
                if args[:2] == ['shell', '-T']:
                    remote = shlex.split(args[2])
                    if remote == ['getprop', 'sys.boot_completed']:
                        reply = (next(outer.boot_replies) if outer.boot_replies is not None
                                 else outer.boot)
                        if isinstance(reply, BaseException):
                            raise reply
                        if callable(reply):
                            reply = reply(outer)
                        child = (real.Command.completed(0, reply) if isinstance(reply, bytes)
                                 else reply)
                        child = readiness_reply('SYS_BOOT_COMPLETED', child)
                        outer.boot_clients.append(child)
                        return child
                    if remote == ['toybox', 'date', '+%s.%N']:
                        return real.Command.completed(0, b'1760000000.123456789\n')
                    if remote[0] == 'logcat':
                        if outer.prearm_event:
                            kwargs['on_line'](b'1760000000.123456790 1000 1001 I am_anr: '
                                b'[0,123,paige.navic.task407.runtime,0,private]')
                        # Explicitly synthetic HOST source, never used by normal CLI.
                        return real.Command.pending()
                    if remote[:2] == ['am', 'instrument']:
                        if not outer.guardian._observer.armed:
                            raise AssertionError('Instrumentation before source admission')
                        outer.instrument_seen = True
                        return real.Command.completed(0, transcript().encode())
                raise AssertionError('Unexpected fixture command')

        class Guardian(real.Supervisor):
            def _guard(self):
                if guardian_gate is not None:
                    guardian_gate.wait(3)
                super()._guard()

            def wait(self, timeout):
                if not hasattr(outer, 'final_wait_budget'):
                    outer.final_wait_budget = timeout
                if guardian_gate is not None:
                    guardian_gate.set()
                return super().wait(timeout)

            def __init__(self, **kwargs):
                super().__init__(**kwargs)
                outer.supervisor_created = True
                outer.guardian = self

            def wait_preparation(self, timeout, command=None):
                original = super().wait_preparation
                if outer.preparation_wait is not None:
                    return outer.preparation_wait(outer, timeout, command, original)
                return original(timeout, command)

            def take_instrumentation_output(self):
                outer.taken_after_shutdown = bool(self.wait(0).shutdown_verified
                                                 and outer.launch.settle(.1))
                return super().take_instrumentation_output()

        self.module = SimpleNamespace(Command=Commands, Supervisor=Guardian,
            Observer=real.Observer, OwnedProcess=real.OwnedProcess,
            LaunchContract=real.LaunchContract, is_own_am_anr=real.is_own_am_anr)
        self.caps = real.Caps(instrumentation=.5, command=.1, process_wait=.05,
                             stop=.7, settle=.5, snapshot_budget=.2, preparation=.5,
                             postboot_preparation=.5)

    def cleanup(self):
        # Exact fixture-owned handle/job only; never SDK/ADB/process-name cleanup.
        if self.guardian is not None:
            self.guardian.abort()
            self.guardian.wait(3)
        if self.launch is not None:
            self.launch.stop_tree(1)


class PipelineTests(unittest.TestCase):
    def api(self, name):
        value = getattr(runner, name, None)
        self.assertTrue(callable(value), 'Missing caller API: ' + name)
        return value

    def execute(self, device, record=None):
        record = host_record() if record is None else record
        pins = [{'path': __file__, 'sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}]
        return self.api('run_session')(device.module, record, {LABEL}, pins=pins,
                                      caps=device.caps, watchdog_id='host-fixture')

    def test_missing_session_authority_blocks_before_any_selected_command(self):
        validate = self.api('validate_session')
        for field, value in (('user_session_authorized', False),
                             ('peer_coordination_complete', False),
                             ('serial', 'physical-device'), ('avd', 'OtherAvd'),
                             ('no_snapshot_save', False), ('clear_logcat', True),
                             ('watchdog_id', 'other-watchdog')):
            with self.subTest(field=field):
                record = host_record()
                record[field] = value
                with self.assertRaises(ValueError):
                    validate(record, 'host-fixture')
        self.assertIsNone(validate(host_record(), 'host-fixture'))

    def test_guardian_precedes_readiness_source_admission_precedes_original_instrumentation(self):
        device = HostDevice()
        self.addCleanup(device.cleanup)
        result = self.execute(device)
        self.assertTrue(result['automatic_emulator_teardown_verified'])
        self.assertTrue(device.taken_after_shutdown)
        self.assertTrue(result['all_three_original_runtime_cases_passed'])
        self.assertTrue(result['host_snapshot_residue_cleanup_required'])
        instrument = [call for call in device.calls if call[0][0] == 'fixture-adb.exe'
                      and call[0][3:5] == ('shell', '-T')
                      and shlex.split(call[0][5])[:2] == ['am', 'instrument']]
        self.assertEqual(len(instrument), 1)
        self.assertEqual(shlex.split(instrument[0][0][5]),
                         ['am', 'instrument', '-w', '-r', '-e', 'class', CLASS,
                          PACKAGE + '/paige.navic.ui.screens.reader.Task407RuntimeRunner'])
        self.assertTrue(all(call[1].get('tree_owned', False) is False
                            for call in device.calls if call[0][0] == 'fixture-adb.exe'))
        for argv, options in device.calls:
            self.assertGreater(options['timeout'], 0)
            self.assertLessEqual(options['max_bytes'], 4 * 1024 * 1024)
        self.assertNotIn('private', json.dumps(result))

    def test_ui_not_ready_aborts_without_install_or_tests_and_still_stops(self):
        device = HostDevice(boot=b'0\n')
        self.addCleanup(device.cleanup)
        result = self.execute(device)
        self.assertTrue(result['automatic_emulator_teardown_verified'])
        self.assertFalse(device.instrument_seen)
        self.assertIsNone(device.install)
        self.assertFalse(result['runtime_result_analysis_performed'])
        self.assertNotIn('passed_test_count', result)

    def test_each_readiness_step_reports_only_fixed_metadata_and_stops_before_install(self):
        cases = (
            ('WAIT_FOR_DEVICE', (1, b'', b''), 'DEVICE_WAIT_UNCONFIRMED'),
            ('OWNED_AVD_IDENTITY', (0, b'OtherLab\nOK\n', b''), 'OWNED_AVD_UNCONFIRMED'),
            ('SYS_BOOT_COMPLETED', (0, b'0\n', b''), 'UI_READY_UNCONFIRMED'),
            ('WAIT_FOR_DEVICE', 'pending', 'PREFLIGHT_COMMAND_INCOMPLETE'),
            ('OWNED_AVD_IDENTITY', 'pending', 'PREFLIGHT_COMMAND_INCOMPLETE'),
            ('SYS_BOOT_COMPLETED', 'pending', 'PREFLIGHT_COMMAND_INCOMPLETE'),
            ('WAIT_FOR_DEVICE', RuntimeError('PRIVATE_READINESS_CANARY'), 'UNEXPECTED_EXECUTION'),
            ('OWNED_AVD_IDENTITY', RuntimeError('PRIVATE_READINESS_CANARY'), 'UNEXPECTED_EXECUTION'),
            ('SYS_BOOT_COMPLETED', RuntimeError('PRIVATE_READINESS_CANARY'), 'UNEXPECTED_EXECUTION'),
        )
        for step, reply, reason in cases:
            with self.subTest(step=step, reply=type(reply).__name__):
                device = HostDevice(readiness={step: reply})
                self.addCleanup(device.cleanup)
                result = self.execute(device)
                self.assertEqual(result['fixed_preflight_failure'], 'UI_READY_BLOCKED')
                self.assertEqual(result['fixed_readiness_step'], step)
                self.assertEqual(result['fixed_preflight_failure_reason'], reason)
                self.assertTrue(result['automatic_emulator_teardown_verified'])
                self.assertFalse(device.instrument_seen)
                self.assertIsNone(device.install)
                self.assertNotIn('PRIVATE_READINESS_CANARY', json.dumps(result))

    def test_unhashable_readiness_exception_still_aborts_and_returns_fixed_metadata(self):
        device = HostDevice(readiness={'WAIT_FOR_DEVICE': ValueError(['PRIVATE_READINESS_CANARY'])})
        self.addCleanup(device.cleanup)
        result = self.execute(device)
        self.assertEqual(result['fixed_preflight_failure'], 'UI_READY_BLOCKED')
        self.assertEqual(result['fixed_readiness_step'], 'WAIT_FOR_DEVICE')
        self.assertEqual(result['fixed_preflight_failure_reason'], 'UNEXPECTED_EXECUTION')
        self.assertTrue(result['automatic_emulator_teardown_verified'])
        self.assertFalse(device.instrument_seen)
        self.assertIsNone(device.install)
        self.assertNotIn('PRIVATE_READINESS_CANARY', json.dumps(result))

    def test_unsettled_readiness_client_is_diagnostic_and_never_clean(self):
        device = HostDevice(readiness={'OWNED_AVD_IDENTITY': 'unsettled'})
        self.addCleanup(device.cleanup)
        result = self.execute(device)
        self.assertEqual(result['fixed_preflight_failure'], 'UI_READY_BLOCKED')
        self.assertEqual(result['fixed_readiness_step'], 'OWNED_AVD_IDENTITY')
        self.assertEqual(result['fixed_preflight_failure_reason'], 'PREFLIGHT_CLIENT_UNSETTLED')
        self.assertFalse(result['preflight_clients_settled'])
        self.assertFalse(result['automatic_emulator_teardown_verified'])
        self.assertFalse(result['runtime_result_analysis_performed'])
        self.assertFalse(device.instrument_seen)
        self.assertIsNone(device.install)

    def test_prearm_candidate_cannot_be_lost_or_admit_instrumentation(self):
        device = HostDevice(prearm_event=True)
        self.addCleanup(device.cleanup)
        result = self.execute(device)
        self.assertFalse(device.instrument_seen)
        self.assertFalse(result['runtime_result_analysis_performed'])
        self.assertTrue(result['automatic_emulator_teardown_verified'])

    def test_pending_preflight_is_bounded_and_cannot_escape_guardian(self):
        device = HostDevice(hang_install=True)
        self.addCleanup(device.cleanup)
        result = self.execute(device)
        self.assertTrue(device.install.cancelled)
        self.assertFalse(device.instrument_seen)
        self.assertFalse(result['runtime_result_analysis_performed'])
        self.assertTrue(result['automatic_emulator_teardown_verified'])

    def test_unclean_shutdown_never_releases_instrumentation_for_analysis(self):
        device = HostDevice(clean_absence=False)
        self.addCleanup(device.cleanup)
        result = self.execute(device)
        self.assertFalse(result['automatic_emulator_teardown_verified'])
        self.assertEqual(result['supervisor']['status'], 'BLOCKED')
        self.assertTrue(device.launch.settle(.1), 'Fallback may stop only the owned tree, never relabel the report')
        self.assertFalse(result['runtime_result_analysis_performed'])
        self.assertFalse(result['all_three_original_runtime_cases_passed'])
        self.assertFalse(device.taken_after_shutdown)
        self.assertNotIn('passed_test_count', result)


    def boot_calls(self, device):
        return [(at, options) for (argv, options), at in zip(device.calls, device.call_times)
                if argv[3:5] == ('shell', '-T')
                and shlex.split(argv[5]) == ['getprop', 'sys.boot_completed']]

    def assert_no_reader_work(self, device, result):
        self.assertIsNone(device.install)
        self.assertFalse(device.instrument_seen)
        self.assertFalse(device.guardian._observer.armed)
        self.assertFalse(result['runtime_result_analysis_performed'])
        self.assertNotIn('passed_test_count', result)

    def test_r117_pending_boot_property_reaches_exact_ready_with_finite_backoff(self):
        device = HostDevice(boot_replies=[b'\n', b'0\r\n', b'1\n'])
        device.caps = dataclasses.replace(device.caps, preparation=1)
        self.addCleanup(device.cleanup)
        result = self.execute(device)
        self.assertTrue(result['all_three_original_runtime_cases_passed'])
        probes = self.boot_calls(device)
        self.assertEqual(len(probes), 3)
        self.assertTrue(all(later[0] - earlier[0] >= .20
                            for earlier, later in zip(probes, probes[1:])))
        self.assertTrue(all(client.settle(.1) and client.buffered_bytes == 0
                            for client in device.boot_clients))

    def test_r117_perpetual_pending_uses_original_preparation_deadline(self):
        device = HostDevice(boot=b'0\n')
        self.addCleanup(device.cleanup)
        result = self.execute(device)
        self.assert_no_reader_work(device, result)
        self.assertEqual(result['supervisor']['cause'], 'PREPARATION_CAP')
        self.assertTrue(result['automatic_emulator_teardown_verified'])
        probes = self.boot_calls(device)
        self.assertGreaterEqual(len(probes), 2)
        self.assertLessEqual(len(probes), 3)
        self.assertTrue(all(at < device.guardian._preparation_deadline for at, _ in probes))
        self.assertTrue(all(options['timeout'] <= min(device.caps.command,
            device.guardian._preparation_deadline - at) + .01 for at, options in probes))

    def test_r117_property_error_private_payload_and_unexpected_framing_fail_closed(self):
        for reply in (b'PRIVATE_READINESS_CANARY\n', b' 1\n', b'1\nextra\n', b'2\n',
                      b'0\n0\n', RuntimeError('PRIVATE_READINESS_CANARY'),
                      ValueError(['PRIVATE_READINESS_CANARY'])):
            with self.subTest(reply=type(reply).__name__):
                device = HostDevice(boot_replies=[reply, b'1\n'])
                self.addCleanup(device.cleanup)
                result = self.execute(device)
                self.assert_no_reader_work(device, result)
                self.assertEqual(len(self.boot_calls(device)), 1)
                self.assertNotIn('PRIVATE_READINESS_CANARY', json.dumps(result))
        for code, out, err in ((1, b'0\n', b''), (0, b'0\n', b'private')):
            device = HostDevice(readiness={'SYS_BOOT_COMPLETED': (code, out, err)})
            self.addCleanup(device.cleanup)
            result = self.execute(device)
            self.assert_no_reader_work(device, result)
            self.assertEqual(len(self.boot_calls(device)), 1)

    def test_r117_abort_during_pending_property_interrupts_before_next_probe(self):
        def abort(device):
            device.guardian.abort()
            return b'0\n'
        device = HostDevice(boot_replies=[abort, b'1\n'])
        self.addCleanup(device.cleanup)
        result = self.execute(device)
        self.assert_no_reader_work(device, result)
        self.assertEqual(result['supervisor']['cause'], 'ABORTED')
        self.assertEqual(len(self.boot_calls(device)), 1)
        self.assertTrue(result['automatic_emulator_teardown_verified'])

    def test_r117_expired_original_preparation_blocks_each_remaining_dispatch(self):
        # Force the real admission deadline past, without waiting for guardian
        # scheduling. No new deadline may be synthesized at phase boundaries.
        for target in ('wait-for-device', 'identity', 'boot', 'install', 'cursor'):
            with self.subTest(target=target):
                def expire(device, argv):
                    args = argv[3:]
                    remote = shlex.split(args[2]) if args[:2] == ['shell', '-T'] else []
                    matched = ((target == 'wait-for-device' and args == ['wait-for-device'])
                        or (target == 'identity' and args == ['emu', 'avd', 'name'])
                        or (target == 'boot' and remote == ['getprop', 'sys.boot_completed'])
                        or (target == 'install' and args[:1] == ['install'])
                        or (target == 'cursor' and remote == ['toybox', 'date', '+%s.%N']))
                    if matched:
                        device.guardian._preparation_deadline = time.monotonic() - .01
                        device.guardian._notify()
                device = HostDevice(before_reply=expire)
                self.addCleanup(device.cleanup)
                result = self.execute(device)
                self.assertFalse(device.instrument_seen)
                self.assertFalse(device.guardian._observer.armed)
                self.assertFalse(result['runtime_result_analysis_performed'])
                if target in ('wait-for-device', 'identity', 'boot'):
                    self.assertIsNone(device.install)
                self.assertEqual(result['supervisor']['cause'], 'PREPARATION_CAP')

    def test_r117_late_completed_command_cannot_reset_its_own_cap(self):
        clock = [100.0]
        def late(device, argv):
            if argv[3:5] == ['shell', '-T'] and shlex.split(argv[5]) == ['getprop', 'sys.boot_completed']:
                clock[0] += device.caps.command + .01
        device = HostDevice(before_reply=late)
        self.addCleanup(device.cleanup)
        # Advance only the caller's command hook, but share the launch anchor
        # with the real guardian. The per-command expiry (.11) stays strictly
        # inside the original preparation (.5), isolating that protected cap.
        clock_api = SimpleNamespace(monotonic=lambda: clock[0])
        with mock.patch.object(runner, 'time', clock_api), \
             mock.patch.object(device.real, 'time', clock_api):
            result = self.execute(device)
        self.assert_no_reader_work(device, result)
        self.assertEqual(result['fixed_preflight_failure_reason'], 'PREFLIGHT_COMMAND_INCOMPLETE')

    def test_r117_external_abort_interrupts_live_pending_boot_command(self):
        timers = []
        def pending(device):
            child = device.real.Command.pending()
            timer = threading.Timer(.03, device.guardian.abort)
            timers.append(timer)
            timer.start()
            return child
        device = HostDevice(boot_replies=[pending])
        device.caps = dataclasses.replace(device.caps, command=.4, preparation=1)
        self.addCleanup(device.cleanup)
        started = time.monotonic()
        result = self.execute(device)
        for timer in timers:
            timer.join(.5)
            self.assertFalse(timer.is_alive())
        self.assert_no_reader_work(device, result)
        self.assertEqual(result['supervisor']['cause'], 'ABORTED')
        self.assertTrue(device.boot_clients[0].cancelled)
        self.assertLess(time.monotonic() - started, .4)

    def test_r117_unsettled_or_cancelled_property_client_never_retries_or_admits(self):
        for kind in ('unsettled', 'cancelled', 'failed'):
            with self.subTest(kind=kind):
                device = HostDevice()
                child = device.real.Command.completed(0, b'0\n')
                if kind == 'unsettled':
                    child.settle = lambda timeout: False
                elif kind == 'cancelled':
                    child.cancelled = True
                else:
                    child.failure = 'OUTPUT_CAP'
                device.boot_replies = iter([child, b'1\n'])
                self.addCleanup(device.cleanup)
                result = self.execute(device)
                self.assert_no_reader_work(device, result)
                self.assertEqual(len(self.boot_calls(device)), 1)
                self.assertFalse(result['all_three_original_runtime_cases_passed'])


class PreflightBoundaryTests(unittest.TestCase):
    """Prospective V1 metadata; caller clock/hooks only, no new timed races."""
    api = PipelineTests.api
    execute = PipelineTests.execute
    def boundary(self, result):
        value = result.get('failed_preflight_command')
        self.assertIsInstance(value, dict, 'R119_BOUNDARY_MISSING')
        self.assertEqual(result.get('preflight_diagnostics_profile'),
                         'PREFLIGHT_COMMAND_BOUNDARY_V1')
        self.assertEqual(set(value), {'before_cleanup', 'cleanup_cancel_requested',
                                     'cleanup_cancel_settlement'})
        return value, value['before_cleanup']

    def run_install(self, reply=None, *, wait=None, before=None, clock=None):
        device = HostDevice(install_reply=reply, preparation_wait=wait, before_reply=before)
        self.addCleanup(device.cleanup)
        if clock is None:
            result = self.execute(device)
        else:
            # The caller now passes its original launch anchor to the guardian;
            # both must observe this same explicit clock in boundary fixtures.
            clock_api = SimpleNamespace(monotonic=lambda: clock[0])
            with mock.patch.object(runner, 'time', clock_api), \
                 mock.patch.object(device.real, 'time', clock_api):
                result = self.execute(device)
        return device, result

    def rejected(self, device, result, reason='PREFLIGHT_COMMAND_INCOMPLETE', *, clean=True):
        self.assertEqual(result['fixed_preflight_failure'], 'INSTALL_BLOCKED')
        self.assertEqual(result['fixed_preflight_failure_reason'], reason)
        self.assertFalse(device.instrument_seen)
        self.assertFalse(device.guardian._observer.armed)
        self.assertFalse(result['runtime_result_analysis_performed'])
        self.assertNotIn('completed_test_count', result)
        self.assertEqual(result['automatic_emulator_teardown_verified'], clean)
        self.assertEqual(result['preflight_clients_settled'], clean)
        installs = [(argv, options) for argv, options in device.calls if argv[3:4] == ('install',)]
        self.assertEqual(len(installs), 1)
        self.assertEqual(installs[0][0], ('fixture-adb.exe', '-s', 'emulator-5554',
                                         'install', '-r', '-t', 'fixture-only.apk'))
        self.assertEqual(installs[0][1]['max_bytes'], 65536)
        self.assertFalse(installs[0][1]['tree_owned'])
        self.assertGreater(installs[0][1]['timeout'], 0)
        self.assertLessEqual(installs[0][1]['timeout'], device.caps.command)
        self.assertEqual(len([argv for argv, _ in device.calls if argv[3:5] == ('shell', '-T')
                              and shlex.split(argv[5])[0] in ('logcat', 'am')]), 0)

    def test_r119_pending_wait_freezes_original_state_before_existing_cancel(self):
        def pending(device):
            return device.real.Command.pending()
        def wait(device, timeout, child, original):
            return False if child is device.install else original(timeout, child)
        device, result = self.run_install(pending, wait=wait)
        row, before = self.boundary(result)
        self.rejected(device, result)
        self.assertEqual(before['role'], 'INSTALL')
        self.assertEqual(before['boundary'], 'WAIT_REJECTED')
        self.assertEqual(before['wait_result'], 'INTERRUPTED_OR_EXPIRED')
        self.assertFalse(before['child_done'])
        self.assertEqual(before['child_failure'], 'NONE')
        self.assertFalse(before['child_cancelled'])
        self.assertEqual(before['returncode_class'], 'UNSET')
        self.assertEqual(before['settlement'], 'NOT_REACHED')
        self.assertTrue(row['cleanup_cancel_requested'])
        self.assertEqual(row['cleanup_cancel_settlement'], 'PASSED')
        self.assertTrue(device.install.cancelled)
        self.assertEqual(device.install.failure, 'CANCELLED')
        self.assertEqual(device.install.buffered_bytes, 0)
        retained = json.dumps(row, sort_keys=True)
        device.install.failure = {'private': 'R119_PRIVATE_CANARY'}
        device.install.returncode = 912345
        self.assertEqual(json.dumps(row, sort_keys=True), retained)

    def test_r119_fixed_failed_cancelled_and_untrusted_failure_states(self):
        failures = ('OUTPUT_CAP', 'STREAM_FAILED', 'COMMAND_CAP', 'CHILD_UNCONFIRMED',
                    'TREE_UNCONFIRMED', 'STREAM_UNCONFIRMED', 'CANCELLED', 'FEED_EOF')
        cases = [(value, False, value) for value in failures]
        cases += [(None, True, 'NONE'), ('R119_PRIVATE_CANARY', False, 'OTHER'),
                  (['R119_PRIVATE_CANARY'], False, 'OTHER'),
                  ({'private': 'R119_PRIVATE_CANARY'}, False, 'OTHER')]
        for failure, cancelled, expected in cases:
            with self.subTest(failure_class=expected, cancelled=cancelled):
                def reply(device):
                    child = device.real.Command.completed(0, b'R119_PRIVATE_CANARY\n')
                    child.failure, child.cancelled = failure, cancelled
                    return child
                device, result = self.run_install(reply)
                row, before = self.boundary(result)
                self.rejected(device, result,
                              clean=expected not in ('CHILD_UNCONFIRMED', 'TREE_UNCONFIRMED'))
                self.assertTrue(before['child_done'])
                self.assertEqual(before['child_failure'], expected)
                self.assertEqual(before['child_cancelled'], cancelled)
                self.assertEqual(before['returncode_class'], 'ZERO')
                self.assertEqual(before['wait_result'], 'COMPLETED')
                self.assertFalse(row['cleanup_cancel_requested'])
                self.assertEqual(row['cleanup_cancel_settlement'], 'NOT_REQUESTED')
                self.assertNotIn('R119_PRIVATE_CANARY', json.dumps(result))
                self.assertEqual(device.install.buffered_bytes, 0)

    def test_r119_late_natural_completion_and_late_settlement_are_distinct(self):
        for stage, expected in (('completion', 'WAIT_REJECTED'),
                                ('settlement', 'POST_SETTLE_DEADLINE')):
            with self.subTest(stage=stage):
                clock = [100.0]
                def reply(device):
                    child = device.real.Command.completed(0, b'Success\n')
                    if stage == 'completion':
                        clock[0] += device.caps.command + .01
                    else:
                        def settle(timeout):
                            clock[0] += device.caps.command + .01
                            return True
                        child.settle = settle
                    return child
                device, result = self.run_install(reply, clock=clock)
                _, before = self.boundary(result)
                self.rejected(device, result)
                self.assertEqual(before['boundary'], expected)
                self.assertTrue(before['deadline_reached'])
                self.assertTrue(before['child_done'])
                self.assertEqual(before['child_failure'], 'NONE')
                self.assertFalse(before['child_cancelled'])
                self.assertEqual(before['settlement'],
                                 'NOT_REACHED' if stage == 'completion' else 'PASSED')
                self.assertGreaterEqual(before['elapsed_ms'], 100)
                self.assertLessEqual(before['effective_budget_ms'], 100)

    def test_r119_false_settlement_then_finally_settled_does_not_erase_rejection(self):
        observed = []
        def reply(device):
            child = device.real.Command.completed(0, b'Success\n')
            def settle(timeout):
                observed.append(timeout)
                return len(observed) != 1
            child.settle = settle
            return child
        device, result = self.run_install(reply)
        _, before = self.boundary(result)
        self.rejected(device, result, 'PREFLIGHT_CLIENT_UNSETTLED')
        self.assertEqual(before['boundary'], 'SETTLE_REJECTED')
        self.assertEqual(before['settlement'], 'FAILED')
        self.assertTrue(result['preflight_clients_settled'])
        self.assertEqual(len(observed), 2, 'Only original command/final aggregate settlement calls')

    def test_r119_preparation_interruption_and_post_settle_unavailable(self):
        for stage in ('wait', 'post_settle'):
            with self.subTest(stage=stage):
                def reply(device):
                    child = device.real.Command.completed(0, b'Success\n')
                    if stage == 'post_settle':
                        def settle(timeout):
                            device.guardian.preparation_remaining = lambda: 0
                            return True
                        child.settle = settle
                    return child
                def wait(device, timeout, child, original):
                    if child is device.install and stage == 'wait':
                        device.guardian.abort()
                        return False
                    return original(timeout, child)
                device, result = self.run_install(reply, wait=wait)
                _, before = self.boundary(result)
                self.rejected(device, result, 'PREFLIGHT_COMMAND_INCOMPLETE' if stage == 'wait'
                              else 'GUARDIAN_ALREADY_TERMINAL')
                self.assertEqual(before['boundary'], 'WAIT_REJECTED' if stage == 'wait'
                                 else 'POST_SETTLE_PREPARATION')
                if stage == 'post_settle':
                    self.assertEqual(before['preparation'], 'UNAVAILABLE')
                else:
                    self.assertEqual(before['wait_result'], 'INTERRUPTED_OR_EXPIRED')
                    self.assertEqual(result['supervisor']['cause'], 'ABORTED')

    def test_r119_logical_install_rejections_project_only_reply_flags(self):
        cases = ((912345, b'Success\nR119_PRIVATE_CANARY', b'', 'NONZERO', False, True),
                 (0, b'Success\n', b'R119_PRIVATE_CANARY', 'ZERO', True, True),
                 (0, b' Success\nR119_PRIVATE_CANARY', b'', 'ZERO', False, False),
                 (None, b'Success\n', b'', 'UNSET', False, True),
                 (['R119_PRIVATE_CANARY'], b'Success\n', b'', 'INVALID', False, True))
        for code, out, err, code_class, stderr, success in cases:
            with self.subTest(code_class=code_class, stderr=stderr, success=success):
                device, result = self.run_install((code, out, err))
                row, before = self.boundary(result)
                self.rejected(device, result, 'INSTALL_UNCONFIRMED')
                self.assertEqual(before['boundary'], 'INSTALL_CHECK_REJECTED')
                self.assertEqual(before['install_reply_check'], 'REJECTED')
                self.assertEqual(before['returncode_class'], code_class)
                self.assertEqual(before['install_stderr_nonempty'], stderr)
                self.assertEqual(before['install_exact_success_line'], success)
                self.assertEqual(before['child_failure'], 'NONE')
                self.assertFalse(row['cleanup_cancel_requested'])
                serialized = json.dumps(result)
                self.assertNotIn('R119_PRIVATE_CANARY', serialized)
                self.assertNotIn('912345', serialized)
                self.assertEqual(device.install.buffered_bytes, 0)

    def test_r119_success_and_outer_failure_never_retain_prior_successful_command(self):
        device, result = self.run_install((0, b'Success\n', b''))
        self.assertTrue(result['all_three_original_runtime_cases_passed'])
        self.assertIn('failed_preflight_command', result, 'R119_SUCCESS_PROJECTION_MISSING')
        self.assertIsNone(result['failed_preflight_command'])
        self.assertEqual(result['completed_test_count'], 3)
        self.assertEqual(result['passed_test_count'], 3)
        def fail_after_boot(device, argv):
            if argv[3:4] == ['install']:
                raise RuntimeError('R119_PRIVATE_CANARY')
        device, result = self.run_install(before=fail_after_boot)
        _, before = self.boundary(result)
        self.assertEqual(before['role'], 'INSTALL')
        self.assertEqual(before['boundary'], 'SPAWN')
        self.assertEqual(before['wait_result'], 'NOT_REACHED')
        self.assertEqual(before['child_failure'], 'NOT_OBSERVED')
        self.assertNotIn('R119_PRIVATE_CANARY', json.dumps(result))
        # A logical non-install rejection must not inherit the earlier command's snapshot.
        device = HostDevice(readiness={'OWNED_AVD_IDENTITY': (0, b'wrong', b'')})
        self.addCleanup(device.cleanup)
        result = self.execute(device)
        self.assertIsNone(result['failed_preflight_command'])

    def test_r119_snapshot_frozen_with_r121_prospective_operational_caps(self):
        snapshot_type = getattr(runner, '_PreflightBoundary', None)
        self.assertTrue(dataclasses.is_dataclass(snapshot_type), 'R119_IMMUTABLE_SNAPSHOT_MISSING')
        self.assertTrue(snapshot_type.__dataclass_params__.frozen)
        captured = []
        root, seal, reviews = ProvenanceTests().metadata()
        record = host_record()
        record.update(seal_path='fixture-seal', seal_sha256='a' * 64,
                      reviews_path='fixture-reviews', reviews_sha256='b' * 64)
        def run(supervision, contract, labels, **kwargs):
            captured.append(dataclasses.asdict(kwargs['caps']))
            raise ValueError('fixture stop before dispatch')
        # CLI import is real and inert. All external execution/output creation blocked.
        with mock.patch.object(runner, 'read_checked_json', side_effect=[record, seal, reviews]), \
             mock.patch.object(runner, 'validate_candidate'), \
             mock.patch.object(runner, 'pins_match', return_value=True), \
             mock.patch.object(runner, 'subprocess') as subprocess_mock, \
             mock.patch.object(runner.os, 'environ', {'WATCHDOG_ID': 'host-fixture'}), \
             mock.patch.object(runner.Path, 'open', mock.mock_open()), \
             mock.patch.object(runner, 'run_session', side_effect=run), redirect_stdout(io.StringIO()):
            subprocess_mock.run.side_effect = [SimpleNamespace(returncode=0, stdout=b'a' * 40),
                                               SimpleNamespace(returncode=0, stdout=b'')]
            runner.main(['--contract', 'fixture', '--contract-sha256', 'c' * 64,
                         '--output', 'fixture-never-created'])
        # R121 prospectively reserves postboot45; V1 snapshots and protected caps stay fixed.
        self.assertEqual(captured, [{'command': 15, 'preparation': 180, 'instrumentation': 420,
                                    'process_wait': 5, 'stop': 5, 'settle': 2,
                                    'snapshot_budget': 15, 'postboot_preparation': 45}])
        def clip_original_remainder(device, argv):
            if argv[3:5] == ['shell', '-T'] and shlex.split(argv[5]) == ['getprop', 'sys.boot_completed']:
                original = device.guardian.preparation_remaining
                device.guardian.preparation_remaining = lambda: min(.031, original())
        device, result = self.run_install((1, b'', b''), before=clip_original_remainder)
        _, before = self.boundary(result)
        self.rejected(device, result, 'INSTALL_UNCONFIRMED')
        install_options = next(options for argv, options in device.calls if argv[3:4] == ('install',))
        self.assertLessEqual(install_options['timeout'], .031)
        self.assertEqual(before['effective_budget_ms'], int(install_options['timeout'] * 1000))


class ReservedPreparationTests(unittest.TestCase):
    """R121 prospective profile; one explicit clock shared by caller and guardian."""
    api = PipelineTests.api
    execute = PipelineTests.execute

    def device(self, **kwargs):
        clock = [100.0]
        device = HostDevice(synthetic_owned=True, guardian_gate=threading.Event(), **kwargs)
        values = dict(preparation=180, command=15, instrumentation=420,
                      process_wait=5, stop=5, settle=2, snapshot_budget=15)
        # The starvation assertion also executes on the original production source.
        if 'postboot_preparation' in device.real.Caps.__dataclass_fields__:
            values['postboot_preparation'] = 45
        device.caps = device.real.Caps(**values)
        self.addCleanup(device.cleanup)
        return device, clock

    def run_clocked(self, device, clock):
        clock_api = SimpleNamespace(monotonic=lambda: clock[0])
        with mock.patch.object(runner, 'time', clock_api), \
             mock.patch.object(device.real, 'time', clock_api):
            return self.execute(device)

    def install_calls(self, device):
        return [(argv, opts) for argv, opts in device.calls if argv[3:4] == ('install',)]

    def test_r121_slow_valid_boot_reserves_full_install_then_original_oracle(self):
        def boot(device, argv):
            if argv[3:] == ['wait-for-device']:
                clock[0] = 265.655
        def install(device):
            clock[0] += 14.8
            return device.real.Command.completed(0, b'Success\nR121_PRIVATE_CANARY')
        device, clock = self.device(before_reply=boot, install_reply=install)
        result = self.run_clocked(device, clock)
        calls = self.install_calls(device)
        self.assertEqual(len(calls), 1)
        self.assertEqual(calls[0][1]['timeout'], 15, 'Cold boot must not consume install reserve')
        self.assertEqual(calls[0][0][3:], ('install', '-r', '-t', 'fixture-only.apk'))
        self.assertFalse(calls[0][1]['tree_owned'])
        self.assertTrue(result['all_three_original_runtime_cases_passed'])
        self.assertEqual((result['completed_test_count'], result['passed_test_count']), (3, 3))
        self.assertEqual(result.get('operational_phase_profile'), 'BOOT_THEN_POSTBOOT_RESERVATION_V1')
        observation = result.get('install_observation')
        self.assertIsInstance(observation, dict, 'Actual safe successful install observation required')
        self.assertIn(observation['elapsed_ms'], (14799, 14800))
        self.assertEqual(observation['returncode_class'], 'ZERO')
        self.assertEqual(observation['reply_check'], 'PASSED')
        self.assertFalse(observation['stderr_nonempty'])
        self.assertTrue(observation['exact_success_line'])
        self.assertEqual(observation['preparation']['phase'], 'POSTBOOT')
        self.assertNotIn('R121_PRIVATE_CANARY', json.dumps(result))
        self.assertEqual(device.guardian._preparation_ceiling, 325)
        self.assertAlmostEqual(device.guardian._preparation_deadline, 310.655)

    def test_r121_owned_and_final_wait_include_only_finite_reserve(self):
        device, clock = self.device()
        result = self.run_clocked(device, clock)
        launch = next(opts for argv, opts in device.calls if argv[0] == 'fixture-emulator.exe')
        # Prospective correction of the obsolete +5 cushion: include each
        # standalone command's cancellation and the final snapshot cancellation.
        cleanup = 2 * 2 + 4 * (15 + 2) + (15 + 2) + 2 * 5 + 5
        self.assertEqual(launch['timeout'], 180 + 45 + 420 + cleanup)
        self.assertEqual(device.final_wait_budget, launch['timeout'])
        self.assertTrue(result['all_three_original_runtime_cases_passed'])
        instrument = next(opts for argv, opts in device.calls if argv[3:5] == ('shell', '-T')
                          and shlex.split(argv[5])[:2] == ['am', 'instrument'])
        self.assertEqual(instrument['timeout'], 420)

    def test_r121_install_at_or_after_own_fifteen_never_admits(self):
        for elapsed in (15, 15.001):
            with self.subTest(elapsed=elapsed):
                def install(device):
                    clock[0] += elapsed
                    return device.real.Command.completed(0, b'Success\n')
                device, clock = self.device(install_reply=install)
                result = self.run_clocked(device, clock)
                self.assertFalse(device.instrument_seen)
                self.assertFalse(result['runtime_result_analysis_performed'])
                before = result['failed_preflight_command']['before_cleanup']
                self.assertTrue(before['deadline_reached'])
                self.assertEqual(before['boundary'], 'WAIT_REJECTED')
                self.assertEqual(len(self.install_calls(device)), 1)
                observation = result.get('install_observation')
                self.assertIsInstance(observation, dict, 'New elapsed observation must not rewrite V1')
                self.assertGreaterEqual(observation['elapsed_ms'], 15000)
                self.assertEqual(observation['reply_check'], 'NOT_REACHED')

    def test_r121_spawn_and_settle_share_install_deadline(self):
        for settle_elapsed, passes in ((.999, True), (1, False), (1.001, False)):
            with self.subTest(settle_elapsed=settle_elapsed):
                def install(device):
                    clock[0] += 14
                    child = device.real.Command.completed(0, b'Success\n')
                    first = [True]
                    def settle(timeout):
                        if first[0]:
                            first[0] = False
                            self.assertLessEqual(timeout, 1)
                            clock[0] += settle_elapsed
                        return True
                    child.settle = settle
                    return child
                device, clock = self.device(install_reply=install)
                result = self.run_clocked(device, clock)
                self.assertEqual(result['all_three_original_runtime_cases_passed'], passes)
                self.assertEqual(len(self.install_calls(device)), 1)
                if not passes:
                    before = result['failed_preflight_command']['before_cleanup']
                    self.assertEqual(before['boundary'], 'POST_SETTLE_DEADLINE')
                    self.assertEqual(before['settlement'], 'PASSED')

    def test_r121_failed_wait_adds_elapsed_without_backfilling_v1(self):
        def install(device):
            return device.real.Command.pending()
        def wait(device, timeout, child, original):
            if child is device.install:
                clock[0] += 3
                device.guardian.abort()
                return False
            return original(timeout, child)
        device, clock = self.device(install_reply=install, preparation_wait=wait)
        result = self.run_clocked(device, clock)
        before = result['failed_preflight_command']['before_cleanup']
        self.assertIsNone(before['elapsed_ms'])
        self.assertEqual(before['deadline_reached'], 'NOT_CHECKED')
        self.assertFalse(before['child_done'])
        self.assertFalse(before['child_cancelled'])
        observation = result.get('install_observation')
        self.assertIsInstance(observation, dict, 'Separate prospective failed-wait timing required')
        self.assertEqual(observation['elapsed_ms'], 3000)
        self.assertEqual(observation['returncode_class'], 'UNSET')
        self.assertEqual(observation['reply_check'], 'NOT_REACHED')
        self.assertIsNone(observation['stderr_nonempty'])
        self.assertIsNone(observation['exact_success_line'])
        self.assertTrue(result['failed_preflight_command']['cleanup_cancel_requested'])
        self.assertFalse(result['runtime_result_analysis_performed'])

    def test_r121_postboot_remainder_clips_install_without_renewal(self):
        device, clock = self.device()
        self.assertTrue(hasattr(device.real.Supervisor, 'complete_boot'), 'Readiness handoff required')
        original = device.module.Supervisor.complete_boot
        def handoff(guardian):
            accepted = original(guardian)
            clock[0] += 44
            return accepted
        device.module.Supervisor.complete_boot = handoff
        result = self.run_clocked(device, clock)
        self.assertEqual(self.install_calls(device)[0][1]['timeout'], 1)
        self.assertEqual(device.guardian._preparation_deadline, 145)
        self.assertTrue(result['all_three_original_runtime_cases_passed'])

    def test_r121_session_requires_explicit_reservation_before_dispatch(self):
        device = HostDevice(synthetic_owned=True)
        device.caps = dataclasses.replace(device.caps, postboot_preparation=0)
        self.addCleanup(device.cleanup)
        with self.assertRaises(ValueError):
            self.execute(device)
        self.assertEqual(device.calls, [])


class AccountingDevice:
    """Virtual timed commands around REAL caller, guardian, work and teardown.

    No process/thread runs. The guardian's timed wait evaluates its actual terminal
    publication time against the supplied deadline, rather than granting a mock
    success. Only clocks, blocking primitives and external command adapters change.
    """
    def __init__(self, *, prefix=14.9, hung=False, launch_gap=0, final_settle=0,
                 unsettled_prefix=False):
        self.real = load_supervisor()
        self.unsettled_prefix = unsettled_prefix
        self.now = 0.0
        self.prefix, self.hung = prefix, hung
        self.launch_gap, self.final_settle = launch_gap, final_settle
        self.calls, self.clients, self.cancel_calls, self.settle_calls = [], [], [], []
        self.guardian = self.launch = None
        self.guarding = False
        self.guard_finished_at = self.final_wait_at = self.final_wait_budget = None
        self.caps = self.real.Caps(preparation=180, postboot_preparation=45,
            instrumentation=420, command=15, process_wait=5, stop=5, settle=2, snapshot_budget=15)
        outer, real = self, self.real

        class Done:
            def __init__(self, at):
                self.at = at
            def is_set(self):
                return self.at is not None and outer.now >= self.at
            def set(self):
                self.at = outer.now
            def wait(self, timeout):
                if self.is_set():
                    return True
                if self.at is not None and self.at <= outer.now + timeout:
                    outer.now = self.at
                    return True
                outer.now += timeout
                return False

        self.Done = Done

        class NoThread:
            def __init__(self, **kwargs):
                pass
            def start(self):
                pass

        self.threads = SimpleNamespace(Thread=NoThread, Event=threading.Event,
            Condition=threading.Condition, Lock=threading.Lock, RLock=threading.RLock)

        def bounded(call, timeout, late=None):
            before = outer.now
            try:
                value = call()
            except BaseException:
                return False, None
            return (True, value) if outer.now - before <= timeout + 1e-9 else (False, None)
        self.bounded = bounded

        class Commands:
            @staticmethod
            def spawn(argv, **kwargs):
                args = argv[3:]
                outer.calls.append((tuple(argv), kwargs, outer.now))
                if argv[0] == 'fixture-emulator.exe':
                    outer.launch_at = outer.now
                    outer.now += outer.launch_gap
                    child = outer.child('owned', at=None)
                    # Command._reap starts its original timeout AFTER spawn; this
                    # is no earlier than the caller's conservative launch anchor.
                    child.timeout_anchor = outer.now
                    child.deadline = outer.now + kwargs['timeout']
                    stops = [0]
                    def wait(timeout):
                        if not child.done.wait(timeout):
                            raise real.subprocess.TimeoutExpired('HOST_FIXTURE', timeout)
                        return 0
                    def stop_tree(timeout):
                        stops[0] += 1
                        outer.now += timeout if outer.hung else 4.9
                        if outer.hung and stops[0] == 1:
                            return False
                        child.done.set()
                        return True
                    child.process = SimpleNamespace(wait=wait, poll=lambda: 0 if child.done.is_set() else None)
                    child.stop_tree = stop_tree
                    outer.launch = child
                    return child
                if args == ['get-state'] and outer.guardian is None:
                    return outer.child('prefix', reply=(1, b'', b"error: device 'emulator-5554' not found"),
                                       at=None if outer.unsettled_prefix else outer.now + outer.prefix,
                                       preflight=True)
                if not outer.guarding:
                    if args == ['wait-for-device']:
                        outer.now = outer.launch_at + 179.9
                        return outer.child('wait', preflight=True)
                    if args == ['emu', 'avd', 'name']:
                        return outer.child('preflight_identity', reply=(0, b'NavicReaderLab\nOK\n', b''), preflight=True)
                    if args[:1] == ['install']:
                        return outer.child('install', reply=(0, b'Success\n', b''), at=outer.now + 14.9, preflight=True)
                    if args[:2] == ['shell', '-T']:
                        remote = shlex.split(args[2])
                        if remote == ['getprop', 'sys.boot_completed']:
                            return outer.child('boot', reply=(0, b'1\n', b''), preflight=True)
                        if remote == ['toybox', 'date', '+%s.%N']:
                            return outer.child('cursor', reply=(0, b'1760000000.123456789\n', b''),
                                               at=outer.now + 14.9, preflight=True)
                        if remote[0] == 'logcat':
                            outer.now += 15.1
                            return outer.child('observer', at=None)
                        if remote[:2] == ['am', 'instrument']:
                            outer.instrument = outer.child('instrument', reply=(0, transcript().encode(), b''),
                                at=None if outer.hung else outer.now + 419.9)
                            return outer.instrument
                if args == ['emu', 'avd', 'name']:
                    return outer.child('identity', reply=(0, b'NavicReaderLab\nOK\n', b''), at=outer.now + 14.9)
                if args == ['emu', 'avd', 'snapshot', 'list']:
                    kind, reply, delay = 'snapshot', (0, b'There are no snapshots available.\nOK\n', b''), 7.45
                elif args == ['emu', 'kill']:
                    kind, reply, delay = 'console', (0, b'OK: killing emulator, bye bye\n', b''), 14.9
                elif args == ['wait-for-disconnect']:
                    kind, reply, delay = 'disconnect', (0, b'', b''), 14.9
                elif args == ['get-state']:
                    kind, reply, delay = 'absence', (1, b'', b"error: device 'emulator-5554' not found"), 14.9
                else:
                    raise AssertionError('Unexpected accounting fixture command')
                return outer.child(kind, reply=reply, at=None if outer.hung else outer.now + delay)

        class Guardian(real.Supervisor):
            def __init__(self, **kwargs):
                super().__init__(**kwargs)
                outer.guardian = self
                self._condition.wait = lambda timeout: outer.advance_work(timeout)
            def wait(self, timeout):
                if self._finished.is_set():
                    return super().wait(0)
                outer.final_wait_at, outer.final_wait_budget = outer.now, timeout
                deadline = outer.now + timeout
                outer.guarding = True
                self._guard()
                outer.guard_finished_at = outer.now
                return self._report if outer.now <= deadline else None

        self.module = SimpleNamespace(Command=Commands, Supervisor=Guardian,
            Observer=real.Observer, OwnedProcess=real.OwnedProcess,
            LaunchContract=real.LaunchContract, is_own_am_anr=real.is_own_am_anr)

    def advance_work(self, timeout):
        child = (getattr(self, 'instrument', None) if self.guarding
                 else getattr(self, 'waiting_preflight', None))
        if child is not None and child.done.at is not None:
            self.now = min(self.now + timeout, child.done.at)
        else:
            self.now += timeout

    def child(self, kind, *, reply=(0, b'', b''), at='now', preflight=False):
        child = self.real.Command.completed(*reply)
        child.done = self.Done(self.now if at == 'now' else at)
        def settle(timeout):
            self.settle_calls.append((kind, timeout, self.now))
            if preflight and self.guarding:
                self.now += min(timeout, self.final_settle)
            return child.done.wait(timeout)
        def cancel(timeout):
            self.cancel_calls.append((kind, timeout, self.now))
            self.now += timeout if kind == 'prefix' and self.unsettled_prefix else min(timeout, 1.9)
            child.cancelled = True
            child.failure = child.failure or 'CANCELLED'
            child.discard()
            if kind == 'prefix' and self.unsettled_prefix:
                return False
            child.done.set()
            return True
        child.settle, child.cancel = settle, cancel
        if preflight:
            self.clients.append(child)
            self.waiting_preflight = child
        return child

    def execute(self):
        clock = SimpleNamespace(monotonic=lambda: self.now)
        pins = [{'path': __file__, 'sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}]
        with mock.patch.object(runner, 'time', clock), mock.patch.object(self.real, 'time', clock), \
             mock.patch.object(self.real, 'threading', self.threads), \
             mock.patch.object(self.real, '_bounded', self.bounded):
            return runner.run_session(self.module, host_record(), {LABEL}, pins=pins,
                                      caps=self.caps, watchdog_id='host-fixture')


class AccountingClockTests(unittest.TestCase):
    def assert_local_caps(self, device):
        launches = [options for argv, options, _ in device.calls if argv[0] == 'fixture-emulator.exe']
        self.assertEqual(len(launches), 1)
        self.assertEqual(launches[0]['timeout'], 749)
        installs, instruments = [], []
        for argv, options, _ in device.calls:
            if argv[0] == 'fixture-emulator.exe':
                continue
            self.assertFalse(options['tree_owned'])
            args = list(argv[3:])
            if args[:1] == ['install']:
                installs.append(options)
                self.assertEqual(args, ['install', '-r', '-t', 'fixture-only.apk'])
            elif args[:2] == ['shell', '-T'] and shlex.split(args[2])[:2] == ['am', 'instrument']:
                instruments.append(options)
            elif args == ['wait-for-device']:
                self.assertLessEqual(options['timeout'], 180)
            elif args[:2] != ['shell', '-T'] or shlex.split(args[2])[0] != 'logcat':
                self.assertLessEqual(options['timeout'], 15)
        self.assertEqual(len(installs), 1)
        self.assertEqual(installs[0]['timeout'], 15)
        self.assertEqual(len(instruments), 1)
        self.assertEqual(instruments[0]['timeout'], 420)
        self.assertTrue(all(cap == 2 for _, cap, _ in device.cancel_calls))

    def test_r121_spec1_nonzero_prefix_near_limit_clean_terminal_keeps_original_output(self):
        device = AccountingDevice()
        result = device.execute()
        self.assertAlmostEqual(device.guard_finished_at, 745.9)
        self.assert_local_caps(device)
        self.assertTrue(result['all_three_original_runtime_cases_passed'], 'R121_SPEC1_PREFIX_TRUNCATED_TERMINAL')
        self.assertEqual(result['supervisor']['status'], 'NATURAL_COMPLETION')
        self.assertEqual(result['passed_test_count'], 3)
        self.assertLess(device.guard_finished_at, device.launch.deadline)
        self.assertAlmostEqual(device.final_wait_at + device.final_wait_budget,
                               device.launch_at + 749)
        self.assertEqual(device.guardian._preparation_ceiling, device.launch_at + 225)

    def test_r121_spec2_hung_lifecycle_cancellation_tails_reach_truthful_terminal(self):
        device = AccountingDevice(prefix=0, hung=True)
        result = device.execute()
        self.assertAlmostEqual(device.guard_finished_at, 746.1)
        self.assert_local_caps(device)
        self.assertIsNotNone(result['supervisor'], 'R121_SPEC2_CANCELLATION_TAILS_TRUNCATED')
        self.assertEqual(result['supervisor']['status'], 'BLOCKED')
        self.assertEqual(result['supervisor']['cause'], 'INSTRUMENTATION_CAP')
        self.assertFalse(result['runtime_result_analysis_performed'])
        self.assertFalse(result['automatic_emulator_teardown_verified'])
        self.assertNotIn('passed_test_count', result)
        self.assertEqual([kind for kind, _, _ in device.cancel_calls],
                         ['instrument', 'observer', 'snapshot', 'console', 'disconnect', 'absence'])
        self.assertEqual(result['supervisor']['cleanup_failures'],
            ('SNAPSHOTS_UNCONFIRMED', 'CONSOLE_STOP_UNCONFIRMED', 'ORIGINAL_TREE_STOP',
             'ORIGINAL_TERMINAL_UNCONFIRMED', 'DISCONNECT_UNCONFIRMED', 'ABSENCE_UNCONFIRMED'))
        self.assertLess(device.guard_finished_at, device.launch.deadline)
        with self.assertRaises(device.real.Blocked):
            device.guardian.take_instrumentation_output()

    def test_r121_owned_guardian_final_wait_share_launch_not_prefix_or_constructor(self):
        device = AccountingDevice(launch_gap=2)
        result = device.execute()
        self.assertEqual(device.guardian._preparation_started, device.launch_at,
                         'Guardian must not restart the boot clock after owned spawn')
        self.assertEqual(device.guardian._preparation_ceiling, device.launch_at + 225)
        self.assertAlmostEqual(device.final_wait_at + device.final_wait_budget, device.launch_at + 749)
        self.assertEqual(device.launch.deadline, device.launch_at + 2 + 749)
        self.assertTrue(result['all_three_original_runtime_cases_passed'])
        self.assertFalse(device.guardian.complete_boot())

    def test_r121_caller_fallback_and_actual_client_settlement_are_separate_finite_tail(self):
        device = AccountingDevice(prefix=14.9, hung=True, final_settle=2)
        result = device.execute()
        self.assertIsNotNone(result['supervisor'], 'Caller needs the truthful blocked guardian report')
        final_calls = [(kind, cap, at) for kind, cap, at in device.settle_calls if at >= device.guard_finished_at]
        self.assertEqual(len(final_calls), len(device.clients))
        self.assertTrue(all(cap == 2 for _, cap, _ in final_calls))
        self.assertAlmostEqual(device.now - device.guard_finished_at, 5 + 2 * len(device.clients))
        self.assertLessEqual(device.now, device.launch_at + 749 + 5 + 4 * len(device.clients))
        self.assertTrue(result['preflight_clients_settled'])
        self.assertFalse(result['runtime_result_analysis_performed'])


    def test_r121_failed_prefix_has_only_original_cancel_and_final_client_obligations(self):
        device = AccountingDevice(unsettled_prefix=True)
        result = device.execute()
        self.assertEqual(device.now, 15 + 2 + 2 + 2)
        self.assertIsNone(device.launch)
        self.assertIsNone(device.guardian)
        self.assertEqual(len(device.calls), 1)
        self.assertEqual(device.cancel_calls, [('prefix', 2, 15), ('prefix', 2, 17)])
        self.assertEqual(device.settle_calls, [('prefix', 2, 19)])
        self.assertFalse(result['preflight_clients_settled'])
        self.assertFalse(result['runtime_result_analysis_performed'])
        self.assertEqual(result['fixed_preflight_failure'], 'SELECTED_ABSENCE_BLOCKED')
        self.assertEqual(result['failed_preflight_command']['cleanup_cancel_settlement'], 'FAILED')


class ProvenanceTests(unittest.TestCase):
    def api(self, name):
        value = getattr(runner, name, None)
        self.assertTrue(callable(value), 'Missing caller API: ' + name)
        return value

    def metadata(self):
        root = Path(__file__).resolve().parents[1]
        files = ('scripts/task407-runtime-supervisor.py', 'scripts/test-task407-runtime-supervisor.py',
                 'scripts/task407-runtime-runner.py', 'scripts/test-task407-runtime-runner.py')
        rows = [{'path': str(root / name), 'sha256': 'a' * 64} for name in files]
        seal = {'kind': 'full-lower-qualified-pushed-source-emulator-only-apk-seal',
                'base_source_commit': 'a' * 40, 'source_pins': rows,
                'runtime_accepted': False, 'original_timeout_and_acceptance_oracles_changed': False,
                'apk': {'path': 'fixture-only.apk', 'sha256': 'b' * 64, 'package': PACKAGE,
                        'production_signed': False}}
        reviews = {'kind': 'ordered-runtime-supervision-reviews-not-android-proof',
                   'ordered': True, 'spec': 'PASS', 'quality': 'PASS', 'pins': rows.copy()}
        return root, seal, reviews

    def test_current_qualification_must_cover_all_four_exact_host_scripts(self):
        validate = self.api('validate_candidate')
        root, seal, reviews = self.metadata()
        self.assertIsNone(validate(seal, reviews, root))
        seal['source_pins'] = seal['source_pins'][:-1]
        with self.assertRaises(ValueError):
            validate(seal, reviews, root)

    def test_failed_unordered_stale_or_wrong_apk_metadata_is_rejected(self):
        validate = self.api('validate_candidate')
        for mutation in ('failed', 'unordered', 'stale', 'changed_oracle', 'wrong_package'):
            with self.subTest(mutation=mutation):
                root, seal, reviews = self.metadata()
                if mutation == 'failed':
                    reviews['quality'] = 'BLOCKED'
                elif mutation == 'unordered':
                    reviews['ordered'] = False
                elif mutation == 'stale':
                    reviews['pins'] = [dict(row) for row in reviews['pins']]
                    reviews['pins'][0]['sha256'] = 'c' * 64
                elif mutation == 'changed_oracle':
                    seal['original_timeout_and_acceptance_oracles_changed'] = True
                else:
                    seal['apk']['package'] = 'other.package'
                with self.assertRaises(ValueError):
                    validate(seal, reviews, root)

    def test_cli_bad_contract_fails_before_output_creation_or_external_work(self):
        main = self.api('main')
        output = Path(__file__).with_suffix('.must-not-be-created')
        self.assertFalse(output.exists())
        stream = io.StringIO()
        with redirect_stdout(stream):
            code = main(['--contract', __file__, '--contract-sha256', '0' * 64,
                         '--output', str(output)])
        self.assertEqual(code, 2)
        self.assertFalse(output.exists())
        self.assertEqual(json.loads(stream.getvalue())['status'], 'BLOCKED')
        self.assertNotIn(__file__, stream.getvalue())

    def test_checked_json_rejects_mismatch_before_parsing_or_android_work(self):
        read = self.api('read_checked_json')
        with self.assertRaises(ValueError):
            read(Path(__file__), '0' * 64)
        manifest = Path(__file__).resolve().parents[1] / 'composeApp/src/androidMain/assets/reader/vendor/manifest.json'
        digest = hashlib.sha256(manifest.read_bytes()).hexdigest()
        self.assertIsInstance(read(manifest, digest), dict)


if __name__ == '__main__':
    unittest.main()
