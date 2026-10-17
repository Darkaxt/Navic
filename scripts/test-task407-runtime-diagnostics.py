"""Host-only synthetic fixtures. Reports contain test IDs and exception TYPES only."""
import importlib.util
import io
import json
import os
from pathlib import Path
import sys
from types import SimpleNamespace
import unittest
from unittest import mock

PATH = Path(__file__).with_name('task407-runtime-diagnostics.py')
if PATH.exists():
    SPEC = importlib.util.spec_from_file_location('task407_runtime_diagnostics', PATH)
    diagnostics = importlib.util.module_from_spec(SPEC)
    SPEC.loader.exec_module(diagnostics)
else:
    diagnostics = SimpleNamespace()


class Clock:
    def __init__(self):
        self.now = 100.0
    def __call__(self):
        return self.now


class StartupLogTests(unittest.TestCase):
    def create(self):
        cls = getattr(diagnostics, 'EmulatorStartupLog', None)
        self.assertTrue(callable(cls), 'API_MISSING')
        self.clock = Clock()
        return cls(clock=self.clock)

    def test_host_error_full_message_survives_not_just_markers(self):
        log = self.create()
        self.clock.now += 1.25
        log.feed(1, b'ERROR | Failed to initialize WHPX: insufficient available memory\n')
        row = log.snapshot()['records'][0]
        self.assertEqual(row, dict(sequence=0, elapsed_ms=1250, stream='stderr',
            severity='ERROR', message='Failed to initialize WHPX: insufficient available memory'))
        self.assertEqual(log.snapshot()['time_basis'], 'HOST_OBSERVED_LINE_ARRIVAL')

    def test_fragments_utf8_replacement_crlf_streams_and_close(self):
        log = self.create()
        log.feed(0, b'INFO | Starting caf\xc3')
        log.feed(1, b'PANIC: Failed to allocate memory\r')
        self.assertEqual(log.snapshot()['records'], [])
        log.feed(0, b'\xa9 backend\r')
        log.feed(1, b'\n')
        log.feed(0, b'\nDEBUG | Invalid encoding \xff\nINFO | Final partial')
        log.close()
        rows = log.snapshot()['records']
        self.assertEqual([r['stream'] for r in rows], ['stderr', 'stdout', 'stdout', 'stdout'])
        self.assertEqual([r['message'] for r in rows], ['Failed to allocate memory',
            'Starting caf\u00e9 backend', 'Invalid encoding \ufffd', 'Final partial'])
        self.assertEqual(rows[0]['severity'], 'PANIC')
        self.assertEqual([r['sequence'] for r in rows], list(range(4)))
        self.assertTrue(log.snapshot()['closed'])
        log.feed(0, b'ERROR | Ignored after close\n')
        self.assertEqual(log.snapshot()['records'], rows)

    def test_source_grammar_accepts_only_sdk_levels_or_panic(self):
        log = self.create()
        for level in ('INFO', 'DEBUG', 'WARNING', 'ERROR', 'FATAL', 'VERBOSE'):
            log.feed(0, (level + '    | Emulator backend status\n').encode())
        log.feed(0, b'emulator: ERROR: Failed to initialize graphics\nPANIC: Out of memory\n')
        self.assertEqual(len(log.snapshot()['records']), 8)

    def test_guest_kernel_logcat_reader_and_stack_content_rejected(self):
        log = self.create()
        rejected = [b'[ 0.012] Linux version private\n',
            b'10-17 13:00:00 123 456 I Navic: private\n', b'init: private\n',
            b'unknown private source\n', b'INFO | Linux version private\n',
            b'INFO | guest kernel: private\n', b'INFO | logcat: private\n',
            b'INFO | reader book title=private\n', b'INFO | href=private\n',
            b'INFO | epubcfi(/6/2)\n', b'ERROR | Traceback (most recent call last)\n',
            b'ERROR | at private.Class.method(private.java:12)\n',
            b'INFO | bitmap private\n', b'INFO | userId=private\n']
        for line in rejected:
            log.feed(0, line)
        self.assertEqual(log.snapshot()['records'], [])
        counts = log.snapshot()['counts']
        self.assertEqual(counts['parsed_lines'], len(rejected))
        self.assertGreater(counts['omitted_unknown_lines'], 0)
        self.assertGreater(counts['omitted_sensitive_lines'], 0)

    def test_credentials_tokens_auth_and_keys_drop_whole_line(self):
        log = self.create()
        for marker in ('credential', 'token', 'auth', 'public-key', 'privatekey',
                       'password', 'secret', 'authorization', 'api_key', 'PUBLIC KEY'):
            log.feed(0, ('INFO | ' + marker + '=PRIVACY_CANARY_CREDENTIAL\n').encode())
        self.assertEqual(log.snapshot()['records'], [])
        self.assertEqual(log.snapshot()['counts']['omitted_sensitive_lines'], 10)

    def test_redacts_paths_urls_emails_ids_and_current_home_without_raw_retention(self):
        environment = {'USERNAME': 'SyntheticPersonCanary', 'USERPROFILE': 'C:\\Users\\SyntheticPersonCanary',
                       'HOME': '/home/SyntheticPersonCanary'}
        with mock.patch.dict(os.environ, environment, clear=True):
            log = self.create()
        lines = [
            'ERROR | Failed to open "C:\\Private Folder\\PRIVACY_PATH_CANARY\\file.ini"; retry disabled',
            'ERROR | Failed to open /home/PRIVACY_UNIX_CANARY/file.ini; retry disabled',
            'WARNING | Connect https://PRIVACY_URL_CANARY.invalid/path?q=x failed',
            'INFO | Contact PRIVACY_EMAIL_CANARY@example.invalid unavailable',
            'DEBUG | Device 12345678-1234-1234-1234-123456789abc unavailable',
            'INFO | Value aabbccddeeff00112233445566778899 unavailable',
            'INFO | Value QWxwaGFCZXRhR2FtbWFEZWx0YUVwc2lsb25DYW5hcnk= unavailable',
            'INFO | SyntheticPersonCanary startup unavailable',
            'ERROR | Failed to open C:/Private/PRIVACY_FORWARD_CANARY/file.ini; retry disabled',
            'ERROR | Failed to open "\\\\server\\PRIVACY_UNC_CANARY\\file.ini"; retry disabled',
        ]
        for line in lines:
            log.feed(0, (line + '\n').encode())
        snapshot = log.snapshot()
        self.assertEqual(len(snapshot['records']), len(lines))
        retained = json.dumps(snapshot)
        for private in ('PRIVACY_PATH_CANARY', 'PRIVACY_UNIX_CANARY', 'PRIVACY_URL_CANARY',
                        'PRIVACY_EMAIL_CANARY', '12345678-1234', 'aabbccddeeff',
                        'QWxwaGFCZXRh', 'SyntheticPersonCanary', 'PRIVACY_FORWARD_CANARY',
                        'PRIVACY_UNC_CANARY'):
            self.assertNotIn(private, retained)
        self.assertIn('Failed to open', retained)
        self.assertIn('retry disabled', retained)
        self.assertEqual(snapshot['counts']['redacted_lines'], len(lines))

    def test_unquoted_space_paths_and_reader_identifier_keys_do_not_leak(self):
        log = self.create()
        log.feed(0, b'ERROR | Failed opening C:\\Private Folder\\PRIVACY_SPACE_CANARY\\file.ini; retry disabled\n')
        row = log.snapshot()['records'][0]
        self.assertNotIn('PRIVACY_SPACE_CANARY', row['message'])
        self.assertIn('retry disabled', row['message'])
        for message in ('bookId=PRIVACY_BOOK_CANARY', 'userIds=PRIVACY_USER_CANARY',
                        'book_title=PRIVACY_TITLE_CANARY', 'cfis=PRIVACY_CFI_CANARY'):
            log.feed(0, ('INFO | ' + message + '\n').encode())
        self.assertEqual(len(log.snapshot()['records']), 1)

    def test_short_current_username_is_redacted(self):
        with mock.patch.dict(os.environ, {'USERNAME': 'xy'}, clear=True):
            log = self.create()
        log.feed(0, b'INFO | User xy startup unavailable\n')
        self.assertNotIn(' xy ', log.snapshot()['records'][0]['message'])

    def test_unknown_source_does_not_get_sanitized_into_accepted_host_record(self):
        log = self.create()
        log.feed(0, b'PRIVACY_UNKNOWN_CANARY https://private.invalid\n')
        self.assertEqual(log.snapshot()['records'], [])
        self.assertEqual(log.snapshot()['counts']['omitted_unknown_lines'], 1)

    def test_first_and_latest_records_bounded_and_omissions_accounted(self):
        log = self.create()
        for index in range(600):
            log.feed(0, ('INFO | Backend observation ' + str(index) + '\n').encode())
        snapshot = log.snapshot()
        rows = snapshot['records']
        self.assertEqual(len(rows), 256)
        self.assertEqual([r['sequence'] for r in rows], list(range(128)) + list(range(472, 600)))
        self.assertEqual(snapshot['counts']['accepted_lines'], 600)
        self.assertEqual(snapshot['dropped_records'], 344)
        rows[0]['message'] = 'mutation'
        self.assertNotEqual(log.snapshot()['records'][0]['message'], 'mutation')

    def test_message_and_partial_limits_do_not_emit_suffix_of_overlong_line(self):
        log = self.create()
        log.feed(0, b'INFO | ' + b'a ' * 1000 + b'\n')
        self.assertEqual(len(log.snapshot()['records'][0]['message']), 1024)
        self.assertEqual(log.snapshot()['truncated_records'], 1)
        log.feed(0, b'INFO | ' + b'x' * 4096)
        log.feed(0, b'ERROR | PRIVATE_OVERFLOW_CANARY\nINFO | Recovery retained\n')
        snapshot = log.snapshot()
        self.assertEqual(snapshot['counts']['omitted_overlong_lines'], 1)
        self.assertEqual(snapshot['records'][-1]['message'], 'Recovery retained')
        self.assertNotIn('PRIVATE_OVERFLOW_CANARY', json.dumps(snapshot))
        self.assertLessEqual(snapshot['partial_bytes'][0], 4096)

    def test_four_mib_cap_stops_retention_without_exception_or_emulator_control(self):
        log = self.create()
        log.feed(0, b'x' * (4 * 1024 * 1024))
        log.feed(0, b'\nERROR | Not retained after cap\n')
        snapshot = log.snapshot()
        self.assertTrue(snapshot['input_cap_reached'])
        self.assertEqual(snapshot['records'], [])
        self.assertEqual(snapshot['partial_bytes'], [0, 0])
        self.assertGreater(snapshot['counts']['omitted_cap_bytes'], 0)

    def test_each_byte_fragment_matches_whole_feed(self):
        complete = self.create()
        fragmented = self.create()
        data = 'INFO | Caf\u00e9 backend\r\nERROR | Failed to initialize\n'.encode()
        complete.feed(0, data)
        for byte in data:
            fragmented.feed(0, bytes([byte]))
        self.assertEqual(complete.snapshot()['records'], fragmented.snapshot()['records'])

    def test_invalid_arguments_rejected_without_stringification(self):
        log = self.create()
        for index, chunk in ((2, b''), (True, b''), (0, 'private'), (0, bytearray(b'private'))):
            with self.assertRaises(ValueError):
                log.feed(index, chunk)

    def test_control_characters_are_not_persisted(self):
        log = self.create()
        log.feed(0, b'ERROR | Failed\x00\x1b[31m backend\n')
        self.assertNotIn('\x00', json.dumps(log.snapshot(), ensure_ascii=False))
        self.assertNotIn('\x1b', log.snapshot()['records'][0]['message'])

    def test_clock_rollback_is_observational_only(self):
        log = self.create()
        self.clock.now = 101
        log.feed(0, b'INFO | First\n')
        self.clock.now = 99
        log.feed(0, b'INFO | Second\n')
        self.assertEqual([r['elapsed_ms'] for r in log.snapshot()['records']], [1000, 1000])
        self.assertTrue(log.snapshot()['clock_clamped'])

    def test_no_io_thread_timer_sleep_or_policy_calls(self):
        cls = getattr(diagnostics, 'EmulatorStartupLog', None)
        self.assertTrue(callable(cls), 'API_MISSING')
        with mock.patch('builtins.open', side_effect=AssertionError), \
             mock.patch('io.open', side_effect=AssertionError), \
             mock.patch('threading.Thread.start', side_effect=AssertionError), \
             mock.patch('threading.Timer.start', side_effect=AssertionError), \
             mock.patch('time.sleep', side_effect=AssertionError):
            log = cls(clock=lambda: 100)
            log.feed(0, b'ERROR | Failed to initialize backend\n')
            log.snapshot()
            log.close()


class PhaseTraceTests(unittest.TestCase):
    def create(self):
        cls = getattr(diagnostics, 'PhaseTrace', None)
        self.assertTrue(callable(cls), 'API_MISSING')
        self.clock = Clock()
        return cls(clock=self.clock)

    def test_fixed_phase_timing_duration_and_numeric_resource_metrics(self):
        trace = self.create()
        trace.record('INSTALL', 'START')
        self.clock.now += 1.25
        trace.record('INSTALL', 'END', outcome='PASSED', metrics={'available_memory_bytes': 1024})
        rows = trace.snapshot()['records']
        self.assertEqual(rows[0], dict(sequence=0, phase='INSTALL', event='START',
            elapsed_ms=0, duration_ms=None, outcome='NONE', metrics={}))
        self.assertEqual(rows[1], dict(sequence=1, phase='INSTALL', event='END',
            elapsed_ms=1250, duration_ms=1250, outcome='PASSED',
            metrics={'available_memory_bytes': 1024}))

    def test_all_fixed_phases_and_fail_codes_accepted(self):
        trace = self.create()
        phases = ('SELECTED_ABSENCE', 'OWNED_LAUNCH', 'WAIT_FOR_DEVICE', 'OWNED_AVD_IDENTITY',
            'SYS_BOOT_COMPLETED', 'BOOT_HANDOFF', 'INSTALL', 'CURSOR', 'OBSERVER_ADMISSION',
            'INSTRUMENTATION', 'TEARDOWN', 'SESSION')
        for phase in phases:
            trace.record(phase, 'START')
            trace.record(phase, 'FAIL', outcome='UNEXPECTED_EXECUTION')
        self.assertEqual(len(trace.snapshot()['records']), 24)

    def test_owned_command_safe_numeric_resource_keys_are_accepted(self):
        trace = self.create()
        metrics = dict(user_cpu_ms=3, kernel_cpu_ms=5, active_processes=1,
            total_processes=2, page_faults=10, peak_job_memory_bytes=1024)
        trace.record('OWNED_LAUNCH', 'END', metrics=metrics)
        self.assertEqual(trace.snapshot()['records'][0]['metrics'], metrics)

    def test_arbitrary_labels_outcomes_metrics_and_nonfinite_numbers_never_persist(self):
        trace = self.create()
        cases = [ ('PRIVATE_PHASE', 'START', {}), ('INSTALL', 'PRIVATE_EVENT', {}),
            ('INSTALL', 'FAIL', {'outcome': 'PRIVATE_OUTCOME'}),
            ('INSTALL', 'FAIL', {'outcome': ['PRIVATE_OUTCOME']}),
            ('INSTALL', 'END', {'metrics': {'PRIVATE_RESOURCE': 2}}),
            ('INSTALL', 'END', {'metrics': {'available_memory_bytes': 'PRIVATE_VALUE'}}),
            ('INSTALL', 'END', {'metrics': {'available_memory_bytes': True}}),
            ('INSTALL', 'END', {'metrics': {'available_memory_bytes': float('nan')}}),
            ('INSTALL', 'END', {'metrics': {'available_memory_bytes': -1}}),
            ('INSTALL', 'END', {'metrics': {'available_memory_bytes': 2 ** 64}}),
        ]
        for phase, event, kwargs in cases:
            with self.assertRaises(ValueError):
                trace.record(phase, event, **kwargs)
        self.assertEqual(trace.snapshot()['records'], [])

    def test_records_bounded_duration_unpaired_is_unknown_and_snapshot_detached(self):
        trace = self.create()
        trace.record('SESSION', 'END')
        self.assertIsNone(trace.snapshot()['records'][0]['duration_ms'])
        for _ in range(300):
            trace.record('SYS_BOOT_COMPLETED', 'START')
        snapshot = trace.snapshot()
        self.assertEqual(len(snapshot['records']), 256)
        self.assertEqual(snapshot['dropped_records'], 45)
        snapshot['records'][0]['metrics']['available_memory_bytes'] = 55
        self.assertEqual(trace.snapshot()['records'][0]['metrics'], {})

    def test_repeated_start_does_not_create_an_unbounded_state_table(self):
        trace = self.create()
        trace.record('SYS_BOOT_COMPLETED', 'START')
        self.clock.now += 1
        trace.record('SYS_BOOT_COMPLETED', 'START')
        self.clock.now += 1
        trace.record('SYS_BOOT_COMPLETED', 'END', outcome='PASSED')
        self.assertEqual(trace.snapshot()['records'][-1]['duration_ms'], 1000)
        trace.record('SYS_BOOT_COMPLETED', 'END')
        self.assertIsNone(trace.snapshot()['records'][-1]['duration_ms'])


class SafeResult(unittest.TestResult):
    """Never format exceptions or traceback/frame/stack objects, including subtests."""
    def __init__(self):
        super().__init__()
        self.safe_failures = []
        self.safe_errors = []
        self.passed = []
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
