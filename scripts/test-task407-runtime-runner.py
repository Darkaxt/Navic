"""Host-only caller contracts; no SDK, emulator, ADB or Android process is run."""
import importlib.util
import json
from pathlib import Path
import shlex
import hashlib
import sys
import io
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
                 clean_absence=True):
        self.real = load_supervisor()
        self.calls = []
        self.boot = boot
        self.prearm_event = prearm_event
        self.hang_install = hang_install
        self.clean_absence = clean_absence
        self.install = self.launch = None
        self.supervisor_created = False
        self.instrument_seen = False
        self.guardian = None
        self.taken_after_shutdown = False
        outer = self
        real = self.real

        class Commands:
            @staticmethod
            def spawn(argv, **kwargs):
                outer.calls.append((tuple(argv), kwargs))
                if argv[0] == 'fixture-emulator.exe':
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
                if args in (['wait-for-device'], ['wait-for-disconnect']):
                    return real.Command.completed(0)
                if args == ['emu', 'avd', 'name']:
                    return real.Command.completed(0, b'NavicReaderLab\r\r\nOK\r\r\n')
                if args == ['emu', 'avd', 'snapshot', 'list']:
                    return real.Command.completed(0, b'There are no snapshots available.\nOK\n')
                if args == ['emu', 'kill']:
                    return real.Command.completed(0, b'OK\n')
                if args[:3] == ['install', '-r', '-t']:
                    outer.install = (real.Command.pending() if outer.hang_install else
                                     real.Command.completed(0, b'Success\n'))
                    return outer.install
                if args[:2] == ['shell', '-T']:
                    remote = shlex.split(args[2])
                    if remote == ['getprop', 'sys.boot_completed']:
                        return real.Command.completed(0, outer.boot)
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
            def __init__(self, **kwargs):
                super().__init__(**kwargs)
                outer.supervisor_created = True
                outer.guardian = self

            def take_instrumentation_output(self):
                outer.taken_after_shutdown = bool(self.wait(0).shutdown_verified
                                                 and outer.launch.settle(.1))
                return super().take_instrumentation_output()

        self.module = SimpleNamespace(Command=Commands, Supervisor=Guardian,
            Observer=real.Observer, OwnedProcess=real.OwnedProcess,
            LaunchContract=real.LaunchContract, is_own_am_anr=real.is_own_am_anr)
        self.caps = real.Caps(instrumentation=.5, command=.1, process_wait=.05,
                             stop=.7, settle=.5, snapshot_budget=.2, preparation=.5)

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
        self.assertFalse(result['runtime_result_analysis_performed'])
        self.assertFalse(result['all_three_original_runtime_cases_passed'])
        self.assertFalse(device.taken_after_shutdown)
        self.assertNotIn('passed_test_count', result)


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
