"""Task407 selected-emulator caller; import performs no SDK or Android work.

The caller is not device permission. MAIN must obtain real thread/session consent
before its first ADB command. Only the unchanged original-three runtime oracle is
supported; neither host tests nor an observer's silence supplies reader acceptance.
Raw command data stays bounded in memory and is never written or forwarded.
"""
import re
import shlex
import hashlib
import time
from dataclasses import asdict, dataclass, replace
from pathlib import Path
import argparse
import importlib.util
import json
import os
import subprocess
import sys

SERIAL = 'emulator-5554'
AVD = 'NavicReaderLab'
PACKAGE = 'paige.navic.task407.runtime'
CLASS = 'paige.navic.ui.screens.reader.ReaderNormalProductionStartupRuntimeTest'
METHODS = (
    'originalRootRequiresExecutableOriginalPassiveProducerBeforeBootstrapDisplay',
    'originalRootPublishesBootstrapThenTwoImmediateSettledTurns',
    'cancelOriginalPendingBootstrapThenCloseReturnsExactPhysicalOwners',
)
COMPONENT = PACKAGE + '/paige.navic.ui.screens.reader.Task407RuntimeRunner'

_READINESS_STEPS = frozenset(('WAIT_FOR_DEVICE', 'OWNED_AVD_IDENTITY', 'SYS_BOOT_COMPLETED'))
_FIXED_PREFLIGHT_REASONS = frozenset((
    'SELECTED_ABSENCE_UNCONFIRMED', 'DEVICE_WAIT_UNCONFIRMED', 'OWNED_AVD_UNCONFIRMED',
    'UI_READY_UNCONFIRMED', 'BOOT_HANDOFF_REJECTED', 'INSTALL_UNCONFIRMED', 'CURSOR_COMMAND_UNCONFIRMED',
    'OBSERVER_ADMISSION_REJECTED', 'INSTRUMENTATION_ADMISSION_REJECTED',
    'GUARDIAN_ALREADY_TERMINAL', 'PREFLIGHT_COMMAND_INCOMPLETE', 'PREFLIGHT_CLIENT_UNSETTLED',
    'UNEXPECTED_EXECUTION',
))


_FIXED_COMMAND_FAILURES = frozenset((
    'OUTPUT_CAP', 'STREAM_FAILED', 'COMMAND_CAP', 'CHILD_UNCONFIRMED',
    'TREE_UNCONFIRMED', 'STREAM_UNCONFIRMED', 'CANCELLED', 'FEED_EOF',
))


def _command_failure(value):
    if value is None:
        return 'NONE'
    return value if type(value) is str and value in _FIXED_COMMAND_FAILURES else 'OTHER'


def _code_class(value):
    if value is None:
        return 'UNSET'
    if type(value) is not int:
        return 'INVALID'
    return 'ZERO' if value == 0 else 'NONZERO'


# Exact case-sensitive byte substrings, independently searched in each retained
# stream. Enum names mean TEXT HIT ONLY, not root cause, readiness or proof.
_OWNED_LAUNCH_TEXT_HITS = (
    (b'PANIC:', 'PANIC_COLON_TEXT_HIT'),
    (b'ERROR', 'ERROR_TEXT_HIT'),
    (b'FATAL', 'FATAL_TEXT_HIT'),
    (b'Not enough disk space', 'NOT_ENOUGH_DISK_SPACE_TEXT_HIT'),
    (b'Not enough memory', 'NOT_ENOUGH_MEMORY_TEXT_HIT'),
    (b'Running multiple emulators with the same AVD',
     'RUNNING_MULTIPLE_EMULATORS_WITH_THE_SAME_AVD_TEXT_HIT'),
    (b'WHPX', 'WHPX_TEXT_HIT'),
    (b'Failed to initialize', 'FAILED_TO_INITIALIZE_TEXT_HIT'),
    (b'Boot completed in ', 'BOOT_COMPLETED_IN_TEXT_HIT'),
    (b'Increasing screen off timeout, logcat buffer size to 2M.', 'POST_BOOT_SETTINGS_TEXT_HIT'),
    (b'Linux version ', 'LINUX_VERSION_TEXT_HIT'),
    (b'Kernel panic - not syncing:', 'KERNEL_PANIC_NOT_SYNCING_TEXT_HIT'),
    (b'init first stage started!', 'ANDROID_INIT_FIRST_STAGE_TEXT_HIT'),
    (b'Guest Driver: Auto (ext controls)', 'GUEST_DRIVER_AUTO_TEXT_HIT'),
    (b'Initializing gfxstream backend', 'GFXSTREAM_INIT_TEXT_HIT'),
    (b'GPU Vendor=[', 'GPU_VENDOR_TEXT_HIT'),
    (b'Windows Hypervisor Platform accelerator is operational', 'WHPX_OPERATIONAL_TEXT_HIT'),
)


def _empty_owned_launch_diagnostic(state):
    return {'sample_state': state, 'command_done': None,
            'command_failure': 'NOT_OBSERVED', 'command_cancelled': None,
            'returncode_class': 'UNSET', 'stdout_bytes': None, 'stderr_bytes': None,
            'buffer_discarded': None, 'diagnostic_gap': True,
            'text_hits': [], 'text_hit_class': 'UNKNOWN'}


def project_owned_launch(command):
    """OWNED_LAUNCH_DIAGNOSTICS_V4: bounded RAM -> closed observational metadata.

    At most the existing combined 4MiB buffer cap is searched, without decoding or
    exporting raw bytes, paths, PIDs, versions, panic reasons, stacks or payloads.
    Fixed-order unique labels are unauthenticated text hits, not emitter/identity
    proof; zero hits mean UNKNOWN. State reads are independent observations, not
    an atomic process history. Even diagnostic_gap=False makes no stdout/stderr
    ordering, chronology, completeness, cause, continuing progress or readiness
    claim. The original stopper-entry sample is not proof of occurrence within
    the boot cap. The changed show-kernel profile can perturb boot/timing and
    exhaust the shared cap; installed launcher/engine/guest source equivalence
    remains UNKNOWN. The host additions are default/conditional source-backed
    installed vocabulary only, not installed-source equivalence, emitter identity,
    stage-order or success/readiness proof. Vendor suffixes are never exported;
    coexisting hits are independent and every nonhit remains UNKNOWN.
    """
    out, err, discarded = command.snapshot_output()
    if (type(out) is not bytes or type(err) is not bytes or type(discarded) is not bool
            or len(out) + len(err) > 4 * 1024 * 1024):
        raise ValueError('OWNED_LAUNCH_SNAPSHOT_INVALID')
    failure = _command_failure(command.failure)
    cancelled = bool(command.cancelled)
    hits = [label for literal, label in _OWNED_LAUNCH_TEXT_HITS
            if literal in out or literal in err]
    return {'sample_state': 'SAMPLED', 'command_done': bool(command.done.is_set()),
            'command_failure': failure, 'command_cancelled': cancelled,
            'returncode_class': _code_class(command.returncode),
            'stdout_bytes': len(out), 'stderr_bytes': len(err),
            'buffer_discarded': discarded,
            'diagnostic_gap': discarded or cancelled or failure != 'NONE',
            'text_hits': hits, 'text_hit_class': 'TEXT_HITS' if hits else 'UNKNOWN'}


def _empty_failed_wait_host_diagnostic(state):
    return {'sample_state': state, 'owned': None, 'failed_client': None}


def project_failed_wait_host(owned, failed_client):
    """FAILED_WAIT_HOST_DIAGNOSTICS_V1: independent scalars, never an oracle.

    Native/job/reader observations are neither atomic history nor proof of guest
    liveness, complete containment history or stream visibility. No raw data leaves
    the retained Commands; unavailable/busy/closed state is valid unknown.
    """
    def project(command):
        state = command.snapshot_host_state()
        if (type(state) is not dict or set(state) != {
                'native_root_state', 'job_active_class', 'stdout_reader_alive', 'stderr_reader_alive'}
                or type(state['native_root_state']) is not str
                or state['native_root_state'] not in ('RUNNING', 'TERMINAL', 'UNAVAILABLE')
                or type(state['job_active_class']) is not str
                or state['job_active_class'] not in ('ZERO', 'ONE', 'MULTIPLE', 'UNAVAILABLE', 'NOT_OWNED')
                or any(value is not None and type(value) is not bool
                       for value in (state['stdout_reader_alive'], state['stderr_reader_alive']))):
            raise ValueError('FAILED_WAIT_HOST_SNAPSHOT_INVALID')
        return {'command_done': bool(command.done.is_set()),
                'command_failure': _command_failure(command.failure),
                'command_cancelled': bool(command.cancelled),
                'returncode_class': _code_class(command.returncode), **state}
    return {'sample_state': 'SAMPLED', 'owned': project(owned), 'failed_client': project(failed_client)}


@dataclass(frozen=True)
class _PreflightBoundary:
    """Independent observations, not process liveness or a first-cause history.

    Only fixed literals, booleans and bounded duration counts enter this snapshot.
    A missing duration/deadline observation means the original predicate short-
    circuited; diagnostics never add a clock/admission call to fill that gap.
    """
    role: str
    boundary: str
    wait_result: str
    child_done: object
    child_failure: str
    child_cancelled: object
    returncode_class: str
    deadline_reached: object
    preparation: str
    settlement: str
    effective_budget_ms: object
    elapsed_ms: object
    install_reply_check: str = 'NOT_REACHED'
    install_stderr_nonempty: object = None
    install_exact_success_line: object = None


def fixed_preflight_reason(error):
    value = error.args[0] if type(error) is ValueError and len(error.args) == 1 else None
    return value if type(value) is str and value in _FIXED_PREFLIGHT_REASONS else 'UNEXPECTED_EXECUTION'



def parse_cursor(raw):
    match = re.fullmatch(rb'([1-9][0-9]{0,9})\.([0-9]{9})(?:\r?\n)?', raw)
    if match is None:
        raise ValueError('GUEST_CURSOR_INVALID')
    return int(match[1]) * 1_000_000_000 + int(match[2])


def shell_argv(adb, arguments):
    return [adb, '-s', SERIAL, 'shell', '-T', shlex.join(arguments)]


def observer_argv(adb, cursor_ns):
    if type(cursor_ns) is not int or cursor_ns <= 1:
        raise ValueError('GUEST_CURSOR_INVALID')
    seconds, nanos = divmod(cursor_ns - 1, 1_000_000_000)
    return shell_argv(adb, ['logcat', '-b', 'events', '-v', 'threadtime,epoch,nsec',
                            '-T', f'{seconds}.{nanos:09d}', '-s', '-e',
                            r'^\[[^,]*,[^,]*,paige\.navic\.task407\.runtime,',
                            '-m', '1', 'am_anr:I'])


def consume_event(observer, line, *, epoch, after_ns, is_own):
    """A conservative operational stop signal, never a cause/currentness oracle.

    Reason/scalars are used only in memory. Clock ambiguity, malformed framing,
    unexpected tags and unassignable candidates stop UNKNOWN rather than being
    ignored or restarting the source. An event arriving before admission also
    stops UNKNOWN; it cannot vanish in the source-start/arm scheduling window.
    """
    if line == b'--------- beginning of events':
        return
    try:
        text = line.decode('utf-8', errors='strict')
        match = re.fullmatch(r'([1-9][0-9]{0,9})\.([0-9]{9})\s+'
                            r'[1-9][0-9]{0,9}\s+[1-9][0-9]{0,9}\s+'
                            r'I\s+am_anr\s*: (.*)', text, re.DOTALL)
        if match is None:
            raise ValueError()
        timestamp = int(match[1]) * 1_000_000_000 + int(match[2])
        payload = match[3]
        if (timestamp <= after_ns or not is_own('am_anr', payload, PACKAGE)
                or not observer.armed):
            raise ValueError()
        observer.am_anr('am_anr', payload, timestamp_ns=timestamp, epoch=epoch)
    except (ValueError, UnicodeError):
        observer.failed()


def lifecycle_argv(adb, action, argument=None):
    commands = {
        'identity': ['emu', 'avd', 'name'],
        'snapshot_list': ['emu', 'avd', 'snapshot', 'list'],
        'console_stop': ['emu', 'kill'],
        'disconnect': ['wait-for-disconnect'],
        'absence': ['get-state'],
    }
    if action == 'snapshot_delete':
        if (not isinstance(argument, str)
                or not re.fullmatch(r'[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}', argument)
                or '..' in argument):
            raise ValueError('SNAPSHOT_NAME_INVALID')
        arguments = ['emu', 'avd', 'snapshot', 'del', argument]
    elif action in commands and argument is None:
        arguments = commands[action]
    else:
        raise ValueError('LIFECYCLE_ACTION_INVALID')
    return [adb, '-s', SERIAL, *arguments]


_STARTUP_WITNESS_KEY = 'E407.StartupWitness.V1'
_STARTUP_WITNESS_EVENTS = frozenset((
    'OBSERVATION_INSTALL_ATTEMPT', 'FIXTURE_MOUNT_ENTERED',
    'LOGGER_CONSOLE_MODULE_LOADED', 'LOGGER_BRIDGE_READY', 'LOGGER_BRIDGE_PUBLICATION_READY',
    'LOGGER_BRIDGE_COMMAND_ACK', 'LOGGER_BRIDGE_COMMAND_FAILED',
    'LOGGER_CURRENT_DESTINATION_RESOLVED', 'LOGGER_OPEN_PUBLICATION_DISPATCHED',
    'LOGGER_CURRENT_DESTINATION_QUERY_DISPATCHED',
    'SAMPLE_ROOT_PRESENT', 'SAMPLE_COORDINATOR_PRESENT', 'SAMPLE_MATERIAL_PRESENT',
    'SAMPLE_ENGINE_PRESENT', 'SAMPLE_ENGINE_INITIALIZATION_COMPLETED', 'SAMPLE_ENGINE_ENTRYPOINT',
    'SAMPLE_ENGINE_ATTACHED', 'SAMPLE_ENGINE_SIZED', 'SAMPLE_ENGINE_PROGRESS_COMPLETE',
    'SAMPLE_ENGINE_RETIRED', 'SAMPLE_CONSTRUCTION_MOUNTED', 'SAMPLE_CONSTRUCTION_EXECUTOR_BOUND',
    'SAMPLE_CONSTRUCTION_WAITING', 'SAMPLE_CONSTRUCTION_STARTING', 'SAMPLE_CONSTRUCTION_QUERY',
    'SAMPLE_CONSTRUCTION_RETIRED', 'SAMPLE_INITIAL_SEMANTIC_INGRESS',
    'SAMPLE_TRANSITION_REGISTERED', 'SAMPLE_TRANSITION_ACTIVE', 'SAMPLE_RASTER_WORK_REGISTERED',
    'SAMPLE_RASTER_PREPARING', 'SAMPLE_RASTER_READY', 'SAMPLE_RASTER_FAILED',
    'SAMPLE_PASSIVE_RUNTIME_READY', 'SAMPLE_TERMINAL_MATERIAL_TIMEOUT',
))


def _project_startup_witness(wire, active):
    """Only this explicit Bundle field is decoded; arbitrary streams are not JSON sources."""
    def unique_object(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError()
            result[key] = value
        return result

    def bounded_integer(value, maximum):
        return type(value) is int and 0 <= value <= maximum

    if len(wire) > 16384:
        raise ValueError()
    value = json.loads(wire, object_pairs_hook=unique_object)
    if (type(value) is not dict or set(value) != {
            'version', 'method', 'anchor', 'records', 'dropped_records',
            'clock_clamped', 'observer_continuity'}
            or type(value['version']) is not int or value['version'] != 1
            or type(value['method']) is not str or value['method'] != active
            or active not in METHODS or value['anchor'] != 'OBSERVATION_INSTALL_ATTEMPT'
            or not bounded_integer(value['dropped_records'], 65535)
            or type(value['clock_clamped']) is not bool
            or value['observer_continuity'] not in ('UNPROVEN', 'GAP_OBSERVED')
            or type(value['records']) is not list or not 1 <= len(value['records']) <= 64):
        raise ValueError()
    previous_ms = 0
    seen_events = set()
    for sequence, record in enumerate(value['records']):
        if (type(record) is not dict or set(record) != {'sequence', 'elapsed_ms', 'event'}
                or not bounded_integer(record['sequence'], 63) or record['sequence'] != sequence
                or not bounded_integer(record['elapsed_ms'], 180000)
                or record['elapsed_ms'] < previous_ms
                or type(record['event']) is not str or record['event'] not in _STARTUP_WITNESS_EVENTS
                or record['event'] in seen_events):
            raise ValueError()
        seen_events.add(record['event'])
        previous_ms = record['elapsed_ms']
    if value['records'][0] != {
            'sequence': 0, 'elapsed_ms': 0, 'event': 'OBSERVATION_INSTALL_ATTEMPT'}:
        raise ValueError()
    return value


def project_instrumentation(text, labels):
    """Closed failure diagnostics are independent of unchanged original status/pass oracles.

    Arrival order is retained, never sorted into an asserted first-cause history. The
    explicit source-literal key must be qualified by the caller's Kotlin-source scan.
    An invalid/duplicate history invalidates that active fixture's entire projection.
    """
    pending, events, fixed, terminal, by_method = {}, [], [], [], {}
    witnesses, invalid_witness_methods = {}, set()
    witness_values = []
    witness_rejections = 0
    witness_prefix = 'INSTRUMENTATION_STATUS: ' + _STARTUP_WITNESS_KEY + '='
    active = witness_active = None
    witness_status_keys = set()
    witness_block_invalid = False
    framework_failure = False
    for line in text.splitlines():
        if line.startswith('INSTRUMENTATION_STATUS: '):
            key, separator, _ = line[len('INSTRUMENTATION_STATUS: '):].partition('=')
            if not separator or key not in ('class', 'test', 'stream', _STARTUP_WITNESS_KEY):
                witness_block_invalid = True
            elif key in witness_status_keys:
                witness_block_invalid = True
            else:
                witness_status_keys.add(key)
        if line.startswith(witness_prefix):
            # Retain at most one bounded in-memory value, plus a duplicate sentinel.
            if len(witness_values) < 2:
                witness_values.append(line[len(witness_prefix):] if len(line) <= 16450 else None)
            continue
        for value in re.findall(r'\b[ACE]407\.[A-Za-z0-9.]+\b', line):
            if value == _STARTUP_WITNESS_KEY or value not in labels:
                continue
            if value not in fixed:
                fixed.append(value)
            if active is not None and value not in by_method[active]:
                by_method[active].append(value)
        if line.startswith('INSTRUMENTATION_STATUS: class='):
            pending['class_valid'] = line.split('=', 1)[1] == CLASS
        elif line.startswith('INSTRUMENTATION_STATUS: test='):
            name = line.split('=', 1)[1]
            pending['method'] = name if name in METHODS else 'UNRECOGNIZED_TEST'
        elif re.fullmatch(r'INSTRUMENTATION_STATUS_CODE: -?\d+', line):
            code = int(line.rsplit(' ', 1)[1])
            diagnostic_status = bool(witness_values) and code == 2
            if witness_values:
                try:
                    if (code != 2 or _STARTUP_WITNESS_KEY not in labels or witness_block_invalid
                            or len(witness_values) != 1 or witness_values[0] is None
                            or witness_active is None or witness_active in witnesses or witness_active in invalid_witness_methods
                            or ('class_valid' in pending and not pending['class_valid'])
                            or ('method' in pending and pending['method'] != witness_active)):
                        raise ValueError()
                    witnesses[witness_active] = _project_startup_witness(witness_values[0], witness_active)
                except (ValueError, TypeError, RecursionError):
                    witness_rejections = min(65535, witness_rejections + 1)
                    if witness_active is not None:
                        witnesses.pop(witness_active, None)
                        invalid_witness_methods.add(witness_active)
            if 'method' in pending and not diagnostic_status:
                events.append({'method': pending['method'],
                               'class_valid': pending.get('class_valid', False),
                               'status_code': code})
                if code == 1 and pending['method'] in METHODS and pending.get('class_valid'):
                    active = pending['method']
                    by_method.setdefault(active, [])
                elif code != 1:
                    active = None
            if code == 1:
                witness_active = (pending.get('method') if pending.get('class_valid')
                                  and pending.get('method') in METHODS else None)
            elif not diagnostic_status and ('method' in pending or code in (-2, -1, 0)):
                witness_active = None
            pending = {}
            witness_values = []
            witness_status_keys = set()
            witness_block_invalid = False
        elif re.fullmatch(r'INSTRUMENTATION_CODE: -?\d+', line):
            terminal.append(int(line.rsplit(' ', 1)[1]))
        if line.startswith(('INSTRUMENTATION_FAILED:', 'INSTRUMENTATION_ABORTED:')):
            framework_failure = True
    if witness_values:
        witness_rejections = min(65535, witness_rejections + 1)
        if witness_active is not None:
            witnesses.pop(witness_active, None)
    return {'events': events, 'fixed_labels': fixed,
            'fixed_labels_by_original_method': by_method, 'terminal_codes': terminal,
            'framework_failure_reported': framework_failure,
            'startup_witness_by_original_method': witnesses,
            'startup_witness_rejection_count': witness_rejections}


def original_three_passed(projected, *, exit_code, source_drift):
    terminals = [row for row in projected['events'] if row['status_code'] != 1]
    passed = [row['method'] for row in terminals
              if row['status_code'] == 0 and row['class_valid']]
    return (exit_code == 0 and not source_drift
            and projected['terminal_codes'] == [-1]
            and not projected['framework_failure_reported']
            and len(terminals) == len(METHODS) and sorted(passed) == sorted(METHODS))


def validate_session(record, watchdog_id):
    # This records a prior real user decision; the file itself cannot grant it.
    expected = {'kind': 'task407-owned-emulator-runtime-session',
                'serial': SERIAL, 'avd': AVD,
                'scope': 'original-three-with-independent-supervision',
                'user_session_authorized': True, 'peer_coordination_complete': True,
                'no_snapshot_save': True, 'preserve_app_data': True, 'clear_logcat': False}
    if (not isinstance(record, dict)
            or any(type(record.get(key)) is not type(value) or record[key] != value
                   for key, value in expected.items())
            or not isinstance(watchdog_id, str)
            or not re.fullmatch(r'[A-Za-z0-9_-]{1,128}', watchdog_id)
            or record.get('watchdog_id') != watchdog_id):
        raise ValueError('SESSION_AUTHORITY_UNCONFIRMED')
    for key in ('adb', 'emulator', 'apk_path'):
        if not isinstance(record.get(key), str) or not record[key]:
            raise ValueError('SESSION_INPUT_INVALID')


def pins_match(pins):
    return bool(pins) and all(Path(row['path']).is_file() and
        hashlib.sha256(Path(row['path']).read_bytes()).hexdigest() == row['sha256']
        for row in pins)


def run_session(supervision, record, labels, *, pins, caps, watchdog_id):
    """One guarded attempt; SDK dispatch is explicit, selected and bounded.

    The normal CLI supplies the real supervision module. Host orchestration tests
    inject SDK replies, retaining real guardian/job behavior; those replies never
    constitute Android or authority evidence. Host RAM residue cleanup remains a
    separate mandatory installed transactional-cleanup step after terminal return.
    """
    validate_session(record, watchdog_id)
    if caps.postboot_preparation <= 0:
        raise ValueError('POSTBOOT_RESERVATION_REQUIRED')
    if not pins_match(pins):
        raise ValueError('SOURCE_PINS_CHANGED')
    s, adb = supervision, record['adb']
    clients = []
    owned = guardian = observer = source = instrument = None
    attached = False
    phase = 'SELECTED_ABSENCE'
    readiness_step = None
    failure = None
    failure_reason = None
    report = None
    failed_boundary = failed_child = install_boundary = None
    failed_cleanup = None
    owned_launch_sampled = False
    failed_wait_host_sampled = False
    result = {'kind': 'original-three-supervised-runtime-not-pair-or-performance-acceptance',
              'parent_status': 'IN_PROGRESS', 'watchdog_id': watchdog_id,
              'launch_transport_profile': 'SHOW_KERNEL_NO_DELAY_ADB_ARGV_V1',
              'automatic_emulator_teardown_verified': False,
              'all_three_original_runtime_cases_passed': False,
              'runtime_result_analysis_performed': False,
              'host_snapshot_residue_cleanup_required': True,
              'raw_stdout_stderr_retained': False,
              'configured_pair_wordsync_accepted': False, 'performance_accepted': False,
              'production_release_accepted': False, 'anr_cause_proven': False,
              'fixed_readiness_step': None, 'fixed_preflight_failure_reason': None,
              'preflight_diagnostics_profile': 'PREFLIGHT_COMMAND_BOUNDARY_V1',
              'operational_phase_profile': 'BOOT_THEN_POSTBOOT_RESERVATION_V1',
              'boot_handoff_observation': None, 'install_observation': None,
              'failed_preflight_command': None,
              'failed_wait_host_diagnostics_profile': 'FAILED_WAIT_HOST_DIAGNOSTICS_V1',
              'failed_wait_host_diagnostics': _empty_failed_wait_host_diagnostic('NOT_REACHED'),
              'owned_job_engine_path_observation_profile': 'OWNED_JOB_ENGINE_PATH_V1',
              'owned_job_engine_path_observation': 'NOT_SAMPLED',
              'owned_engine_window_topology_profile': 'OWNED_ENGINE_WINDOW_TOPOLOGY_V1',
              'owned_engine_window_topology': 'NOT_SAMPLED',
              'owned_launch_diagnostics_profile': 'OWNED_LAUNCH_DIAGNOSTICS_V4',
              'owned_launch_diagnostics': _empty_owned_launch_diagnostic('OWNED_LAUNCH_ABSENT')}

    def require_preparation():
        remaining = guardian.preparation_remaining()
        if remaining <= 0:
            raise ValueError('GUARDIAN_ALREADY_TERMINAL')
        return remaining

    def cancel_client(client):
        # Observe only the cancellation already required by the original finally.
        cleanup = failed_cleanup if client is failed_child else None
        if cleanup is not None:
            cleanup['cleanup_cancel_requested'] = True
            cleanup['cleanup_cancel_settlement'] = 'NOT_RETURNED'
        settled = client.cancel(caps.settle)
        if cleanup is not None:
            cleanup['cleanup_cancel_settlement'] = 'PASSED' if settled else 'FAILED'

    def command(role, arguments, timeout):
        nonlocal failed_boundary, failed_child, failed_cleanup, install_boundary, failed_wait_host_sampled
        child = None
        boundary, wait_result, settlement = 'ADMISSION', 'NOT_REACHED', 'NOT_REACHED'
        preparation = 'NOT_APPLICABLE' if guardian is None else 'NOT_CHECKED'
        budget_ms = elapsed_ms = None
        dispatched_at = None
        deadline_reached = 'NOT_CHECKED'

        def observe_install():
            if role == 'INSTALL':
                # Prospective independent clock read; never backfill V1's short-
                # circuited predicates or interpret this duration as liveness.
                result['install_observation'] = {
                    'elapsed_ms': (max(0, int((time.monotonic() - dispatched_at) * 1000))
                                   if dispatched_at is not None else None),
                    'returncode_class': _code_class(child.returncode) if child is not None else 'UNSET',
                    'reply_check': 'NOT_REACHED', 'stderr_nonempty': None,
                    'exact_success_line': None,
                    'preparation': guardian.preparation_observation()}

        def prepare():
            nonlocal preparation
            try:
                remaining = require_preparation()
            except BaseException:
                preparation = 'UNAVAILABLE'
                raise
            preparation = 'AVAILABLE'
            return remaining

        def snapshot():
            return _PreflightBoundary(role, boundary, wait_result,
                bool(child.done.is_set()) if child is not None else 'NOT_OBSERVED',
                _command_failure(child.failure) if child is not None else 'NOT_OBSERVED',
                bool(child.cancelled) if child is not None else 'NOT_OBSERVED',
                _code_class(child.returncode) if child is not None else 'UNSET',
                deadline_reached, preparation, settlement, budget_ms, elapsed_ms)

        try:
            if guardian is not None:
                timeout = min(timeout, prepare())
            dispatched_at = time.monotonic()
            deadline = dispatched_at + timeout
            budget_ms = max(0, int(timeout * 1000))
            boundary = 'SPAWN'
            child = s.Command.spawn([adb, '-s', SERIAL, *arguments],
                                    timeout=timeout, max_bytes=65536, tree_owned=False)
            clients.append(child)
            boundary = 'WAIT_REJECTED'
            remaining = max(0, deadline - time.monotonic())
            completed = (child.done.wait(remaining) if guardian is None else
                         guardian.wait_preparation(remaining, child))
            wait_result = 'COMPLETED' if completed else 'INTERRUPTED_OR_EXPIRED'
            # Preserve the original short circuit and its existing clock reads.
            if not completed or child.failure or child.cancelled:
                raise ValueError('PREFLIGHT_COMMAND_INCOMPLETE')
            observed_at = time.monotonic()
            deadline_reached = observed_at >= deadline
            elapsed_ms = max(0, int((observed_at - dispatched_at) * 1000))
            if deadline_reached:
                raise ValueError('PREFLIGHT_COMMAND_INCOMPLETE')
            settle = min(caps.settle, max(0, deadline - time.monotonic()))
            boundary = 'SETTLE_PREPARATION'
            if guardian is not None:
                settle = min(settle, prepare())
            boundary = 'SETTLE_REJECTED'
            settled = child.settle(settle)
            settlement = 'PASSED' if settled else 'FAILED'
            if not settled:
                raise ValueError('PREFLIGHT_CLIENT_UNSETTLED')
            boundary = 'POST_SETTLE_DEADLINE'
            observed_at = time.monotonic()
            deadline_reached = observed_at >= deadline
            elapsed_ms = max(0, int((observed_at - dispatched_at) * 1000))
            if deadline_reached:
                raise ValueError('PREFLIGHT_COMMAND_INCOMPLETE')
            boundary = 'POST_SETTLE_PREPARATION'
            if guardian is not None:
                prepare()
            boundary = 'REPLY'
            reply = child.returncode, *child.take_output()
            if role == 'INSTALL':
                install_boundary = snapshot()
                observe_install()
            return reply
        except BaseException:
            # Freeze BEFORE finally may cancel the child or discard its bytes.
            failed_boundary, failed_child = snapshot(), child
            observe_install()
            failed_cleanup = {'cleanup_cancel_requested': False,
                              'cleanup_cancel_settlement': 'NOT_REQUESTED'}
            if (role == 'WAIT_FOR_DEVICE' and not failed_wait_host_sampled
                    and isinstance(owned, s.Command) and isinstance(child, s.Command)):
                failed_wait_host_sampled = True
                # One job-scoped path observation is diagnostic-only: it neither
                # admits readiness nor proves image history, byte identity or progress.
                try:
                    observed = owned.snapshot_expected_image(str(
                        Path(record['emulator']).parent / 'qemu' / 'windows-x86_64' /
                        'qemu-system-x86_64.exe'))
                    result['owned_job_engine_path_observation'] = (
                        observed if observed in ('PATH_MATCH_OBSERVED',
                                                'NO_PATH_MATCH_IN_COMPLETE_LIST') else 'UNAVAILABLE')
                except BaseException:
                    result['owned_job_engine_path_observation'] = 'UNAVAILABLE'
                # Payload-free calling-desktop topology only; no consent identity,
                # root cause, responsiveness, engine progress or readiness follows.
                try:
                    topology = owned.snapshot_owned_engine_window_topology(str(
                        Path(record['emulator']).parent / 'qemu' / 'windows-x86_64' /
                        'qemu-system-x86_64.exe'))
                    result['owned_engine_window_topology'] = (
                        topology if type(topology) is str and topology in (
                            'MODAL_COMPATIBLE_TOPOLOGY', 'VISIBLE_OWNED_WINDOW_NO_MODAL_TOPOLOGY',
                            'ONLY_HIDDEN_OWNED_WINDOWS',
                            'NO_OWNED_TOP_LEVEL_WINDOW_IN_COMPLETE_DESKTOP_SCAN') else 'UNAVAILABLE')
                except BaseException:
                    result['owned_engine_window_topology'] = 'UNAVAILABLE'
                result['failed_wait_host_diagnostics'] = _empty_failed_wait_host_diagnostic('SNAPSHOT_GAP')
                try:
                    result['failed_wait_host_diagnostics'] = project_failed_wait_host(owned, child)
                except BaseException:
                    pass  # Never replace the primary failure or skip original cleanup.
            raise
        finally:
            if child is not None:
                if not child.done.is_set():
                    cancel_client(child)
                child.discard()

    def shell(role, arguments, timeout):
        return command(role, shell_argv(adb, arguments)[3:], timeout)

    class LifecycleCommands:
        def start(self, action, argument, timeout, max_bytes):
            return s.Command.spawn(lifecycle_argv(adb, action, argument),
                                   timeout=timeout, max_bytes=max_bytes, tree_owned=False)

    # Only launch -> guardian terminal belongs to the original owned Command.
    # Selected absence precedes ownership: <=command including successful settle,
    # or <=command+settle for its failed-command cancellation tail. It must never
    # be subtracted from the guardian's independent launch-based wait.
    session_budget = caps.session_budget
    try:
        code, out, err = command('SELECTED_ABSENCE', ['get-state'], caps.command)
        if not (code != 0 and not out.strip() and
                err.strip() == ("error: device '" + SERIAL + "' not found").encode('ascii')):
            raise ValueError('SELECTED_ABSENCE_UNCONFIRMED')
        phase = 'OWNED_LAUNCH'
        launch_started = time.monotonic()
        guardian_deadline = launch_started + session_budget
        owned = s.Command.spawn([record['emulator'], '-avd', AVD, '-port', '5554',
                                 '-no-snapshot', '-no-boot-anim', '-gpu', 'swiftshader', '-show-kernel'],
                                timeout=session_budget, max_bytes=4 * 1024 * 1024, tree_owned=True)
        result['owned_launch_diagnostics'] = _empty_owned_launch_diagnostic('STOPPER_NOT_REACHED')
        original = owned.process
        observer = s.Observer(PACKAGE)

        def stop_original_tree(same, timeout):
            nonlocal owned_launch_sampled
            if same is original and not owned_launch_sampled:
                # Only the first entry to the existing retained-original stopper;
                # freeze before its original cancel can discard the launch bytes.
                # The caller's direct fallback remains unchanged and never resamples.
                owned_launch_sampled = True
                try:
                    result['owned_launch_diagnostics'] = _empty_owned_launch_diagnostic('SNAPSHOT_GAP')
                    result['owned_launch_diagnostics'] = project_owned_launch(owned)
                except BaseException:
                    pass  # Diagnostic failure cannot skip the original owned stop.
            return same is original and owned.stop_tree(timeout)

        anchor = s.OwnedProcess(original, stop_original_tree)
        guardian = s.Supervisor(owned=anchor, commands=LifecycleCommands(),
            observer=observer, avd=AVD, serial=SERIAL,
            contract=s.LaunchContract(watchdog_id=watchdog_id, no_snapshot_save=True),
            caps=caps, launch_started=launch_started)
        phase = 'UI_READY'
        readiness_step = 'WAIT_FOR_DEVICE'
        code, out, err = command('WAIT_FOR_DEVICE', ['wait-for-device'], caps.preparation)
        if code != 0 or err:
            raise ValueError('DEVICE_WAIT_UNCONFIRMED')
        readiness_step = 'OWNED_AVD_IDENTITY'
        code, out, err = command('OWNED_AVD_IDENTITY', ['emu', 'avd', 'name'], caps.command)
        if code != 0 or err or [v for v in out.splitlines() if v] != [AVD.encode(), b'OK']:
            raise ValueError('OWNED_AVD_UNCONFIRMED')
        readiness_step = 'SYS_BOOT_COMPLETED'
        while True:
            code, out, err = shell('SYS_BOOT_COMPLETED', ['getprop', 'sys.boot_completed'], caps.command)
            if code != 0 or err or type(out) is not bytes:
                raise ValueError('UI_READY_UNCONFIRMED')
            if out in (b'1', b'1\n', b'1\r\n'):
                break
            if out not in (b'', b'\n', b'\r\n', b'0', b'0\n', b'0\r\n'):
                raise ValueError('UI_READY_UNCONFIRMED')
            # Known pending values alone admit another observation, with finite
            # condition backoff inside the guardian's unchanged original cap.
            if not guardian.wait_preparation(.25):
                raise ValueError('UI_READY_UNCONFIRMED')
        handoff = guardian.complete_boot()
        result['boot_handoff_observation'] = {
            'accepted': handoff, **guardian.preparation_observation()}
        if not handoff:
            raise ValueError('BOOT_HANDOFF_REJECTED')
        readiness_step = None
        phase = 'INSTALL'
        code, out, err = command('INSTALL', ['install', '-r', '-t', record['apk_path']], caps.command)
        install_ok = code == 0 and not err and b'Success' in out.splitlines()
        result['install_observation'].update({
            'reply_check': 'PASSED' if install_ok else 'REJECTED',
            'stderr_nonempty': bool(err), 'exact_success_line': b'Success' in out.splitlines()})
        if not install_ok:
            # Classify only the reply already in memory; this is not a specific
            # package-manager verdict. Never project its code or stream contents.
            failed_boundary = replace(install_boundary, boundary='INSTALL_CHECK_REJECTED',
                returncode_class=_code_class(code), install_reply_check='REJECTED',
                install_stderr_nonempty=bool(err),
                install_exact_success_line=b'Success' in out.splitlines())
            failed_cleanup = {'cleanup_cancel_requested': False,
                              'cleanup_cancel_settlement': 'NOT_REQUESTED'}
            raise ValueError('INSTALL_UNCONFIRMED')
        phase = 'OBSERVER_ADMISSION'
        code, out, err = shell('CURSOR', ['toybox', 'date', '+%s.%N'], caps.command)
        if code != 0 or err:
            raise ValueError('CURSOR_COMMAND_UNCONFIRMED')
        cursor = parse_cursor(out)
        epoch = watchdog_id  # Attempt identity only; never used as a guest clock.
        require_preparation()
        source = s.Command.spawn(observer_argv(adb, cursor), timeout=session_budget,
            max_bytes=65536, tree_owned=False,
            on_line=lambda line: consume_event(observer, line, epoch=epoch,
                                               after_ns=cursor, is_own=s.is_own_am_anr))
        require_preparation()
        if not observer.arm(epoch=epoch, after_ns=cursor, source=source):
            raise ValueError('OBSERVER_ADMISSION_REJECTED')
        phase = 'INSTRUMENTATION'
        require_preparation()
        instrument = s.Command.spawn(shell_argv(adb,
            ['am', 'instrument', '-w', '-r', '-e', 'class', CLASS, COMPONENT]),
            timeout=caps.instrumentation, max_bytes=4 * 1024 * 1024, tree_owned=False)
        attached = guardian.attach_instrumentation(instrument)
        if not attached:
            raise ValueError('INSTRUMENTATION_ADMISSION_REJECTED')
    except BaseException as error:
        failure = phase + '_BLOCKED'
        failure_reason = fixed_preflight_reason(error)
        if readiness_step not in _READINESS_STEPS:
            readiness_step = None
        if guardian is not None and guardian.preparation_remaining() > 0:
            guardian.abort()
    finally:
        # Caller-only obligations are NOT another owned/guardian lifetime: at
        # most two unadmitted clients cancel here (2*settle), then fallback stop
        # (stop), N retained preflight cancellations (N*settle), and the final
        # aggregate settlement (<=N*settle, short-circuiting on failure). N is the
        # actual clients list, including selected absence and every boot probe.
        # Thus this finally/final-settlement tail is <=2*settle+stop+2*N*settle.
        # Before finally there can also be one failed command cancellation
        # (settle), a rejected observer arm (.5, its existing fixed bound), or a
        # rejected instrument attachment (settle). Conservatively summing those
        # actual call sites gives caller-only <=4*settle+.5+stop+2*N*settle,
        # separately from the initial selected command and launch session. None
        # renews a phase or the owned timeout, or steals time from guardian.wait.
        if source is not None and (observer is None or observer.source is not source):
            source.cancel(caps.settle)
            source.discard()
        if instrument is not None and not attached:
            instrument.cancel(caps.settle)
            instrument.discard()
        if guardian is not None:
            # Same pre-spawn anchor as the guardian. Command._reap starts its
            # unchanged timeout later, so it cannot expire before this envelope;
            # neither a nonzero prefix nor repeated waits renew this deadline.
            report = guardian.wait(max(0, guardian_deadline - time.monotonic()))
        if owned is not None and (report is None or not report.shutdown_verified):
            # Original retained job only. This fallback cannot relabel a failed
            # guardian report or manufacture snapshot/disconnect/absence proof.
            owned.stop_tree(caps.stop)
            owned.discard()
        for client in clients:
            if not client.done.is_set():
                cancel_client(client)
            client.discard()
    clients_settled = all(client.settle(caps.settle) for client in clients)
    clean = bool(report is not None and report.shutdown_verified and clients_settled)
    if failed_boundary is not None:
        result['failed_preflight_command'] = {'before_cleanup': asdict(failed_boundary),
                                               **failed_cleanup}
    result.update({'fixed_preflight_failure': failure,
                   'fixed_readiness_step': readiness_step,
                   'fixed_preflight_failure_reason': failure_reason,
                   'automatic_emulator_teardown_verified': clean,
                   'preflight_clients_settled': clients_settled,
                   'supervisor': asdict(report) if report is not None else None})
    if clean and report.eligible_for_oracle:
        out, err = guardian.take_instrumentation_output()
        projected = project_instrumentation(out.decode('utf-8', errors='replace'), labels)
        del out, err
        drift = not pins_match(pins)
        terminals = [row for row in projected['events'] if row['status_code'] != 1]
        result.update(projected)
        result.update({'runtime_result_analysis_performed': True, 'source_drift': drift,
            'completed_test_count': len(terminals),
            'passed_test_count': sum(row['status_code'] == 0 and row['class_valid'] for row in terminals),
            'all_three_original_runtime_cases_passed': original_three_passed(projected,
                exit_code=report.instrumentation_exit_code, source_drift=drift)})
    return result


def read_checked_json(path, expected_sha256):
    if not isinstance(expected_sha256, str) or not re.fullmatch(r'[0-9a-f]{64}', expected_sha256):
        raise ValueError('METADATA_PIN_INVALID')
    path = Path(path)
    if path.stat().st_size > 2 * 1024 * 1024:
        raise ValueError('METADATA_CAP')
    raw = path.read_bytes()
    if hashlib.sha256(raw).hexdigest() != expected_sha256:
        raise ValueError('METADATA_PIN_CHANGED')
    value = json.loads(raw)
    if not isinstance(value, dict):
        raise ValueError('METADATA_TYPE_INVALID')
    return value


def validate_candidate(seal, reviews, root):
    """Check the pinned seal/review linkage, not manufacture new qualification.

    MAIN creates the seal only from actual current full gates. Its recorded hash
    is admitted independently in the reviewed session contract, not generated by
    this caller. Historical r108 pins alone cannot cover these four new inputs.
    """
    if (seal.get('kind') != 'full-lower-qualified-pushed-source-emulator-only-apk-seal'
            or seal.get('original_timeout_and_acceptance_oracles_changed') is not False
            or seal.get('runtime_accepted') is not False
            or not re.fullmatch(r'[0-9a-f]{40}', seal.get('base_source_commit', ''))
            or reviews.get('kind') != 'ordered-runtime-supervision-reviews-not-android-proof'
            or reviews.get('ordered') is not True
            or reviews.get('spec') != 'PASS' or reviews.get('quality') != 'PASS'):
        raise ValueError('CANDIDATE_PREREQUISITE_INVALID')
    apk = seal['apk']
    if (apk.get('package') != PACKAGE or apk.get('production_signed') is not False
            or not re.fullmatch(r'[0-9a-f]{64}', apk.get('sha256', ''))):
        raise ValueError('CANDIDATE_APK_INVALID')
    root = Path(root).resolve()
    required = {root / 'scripts' / name for name in (
        'task407-runtime-supervisor.py', 'test-task407-runtime-supervisor.py',
        'task407-runtime-runner.py', 'test-task407-runtime-runner.py')}

    def indexed(rows):
        result = {}
        for row in rows:
            path = Path(row['path'])
            path = (path if path.is_absolute() else root / path).resolve()
            if path in result or not re.fullmatch(r'[0-9a-f]{64}', row['sha256']):
                raise ValueError('CANDIDATE_PIN_INVALID')
            result[path] = row['sha256']
        return result

    source, reviewed = indexed(seal['source_pins']), indexed(reviews['pins'])
    if (not required <= source.keys() or reviewed.keys() != required
            or any(source[path] != reviewed[path] for path in required)):
        raise ValueError('CURRENT_SUPERVISION_UNQUALIFIED')


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--contract', required=True,
                        help='MAIN record of genuine current thread/session permission and pinned inputs')
    parser.add_argument('--contract-sha256', required=True)
    parser.add_argument('--output', required=True, help='New fixed-metadata-only JSON file; never overwritten')
    args = parser.parse_args(argv)
    try:
        record = read_checked_json(args.contract, args.contract_sha256)
        watchdog = os.environ.get('WATCHDOG_ID')
        validate_session(record, watchdog)
        root = Path(__file__).resolve().parents[1]
        seal = read_checked_json(record['seal_path'], record['seal_sha256'])
        reviews = read_checked_json(record['reviews_path'], record['reviews_sha256'])
        validate_candidate(seal, reviews, root)
        if Path(record['apk_path']).resolve() != Path(seal['apk']['path']).resolve():
            raise ValueError('CANDIDATE_APK_PATH_CHANGED')
        pins = seal['source_pins'] + [{'path': seal['apk']['path'], 'sha256': seal['apk']['sha256']}]
        if not pins_match(pins):
            raise ValueError('CANDIDATE_BYTES_CHANGED')
        for command, expected in ((['rev-parse', 'HEAD'], seal['base_source_commit']),
                                  (['status', '--porcelain', '--untracked-files=no'], '')):
            checked = subprocess.run(['git', '-C', str(root), *command],
                                     capture_output=True, timeout=15, check=False)
            if checked.returncode != 0 or checked.stdout.decode('ascii').strip() != expected:
                raise ValueError('PUBLISHED_CHECKOUT_CHANGED')
        labels = set()
        for path in sorted((root / 'composeApp/src/androidDeviceTest').rglob('*.kt')):
            labels.update(re.findall(r'"([ACE]407\.[A-Za-z0-9.]+)"', path.read_text(encoding='utf-8')))
        module_spec = importlib.util.spec_from_file_location('task407_runtime_supervision',
            root / 'scripts/task407-runtime-supervisor.py')
        supervision = importlib.util.module_from_spec(module_spec)
        sys.modules[module_spec.name] = supervision
        module_spec.loader.exec_module(supervision)
        # New prospective operational profile: boot180 then one reserved postboot45
        # (install15 + cursor15 + finite admission envelope15), total ceiling225.
        # This is allocation, not measured install latency or historical equivalence.
        # Protected reader/test waits and instrumentation420 are unchanged.
        caps = supervision.Caps(preparation=180, postboot_preparation=45,
                                instrumentation=420, command=15,
                                process_wait=5, stop=5, settle=2, snapshot_budget=15)
        with Path(args.output).open('x', encoding='utf-8') as output:
            result = run_session(supervision, record, labels, pins=pins,
                                 caps=caps, watchdog_id=watchdog)
            result.update({'source_commit': seal['base_source_commit'],
                           'apk_sha256': seal['apk']['sha256'], 'profile': asdict(caps),
                           'seal_sha256': record['seal_sha256'],
                           'reviews_sha256': record['reviews_sha256']})
            json.dump(result, output, indent=2)
        print(json.dumps({'status': 'HOST_SNAPSHOT_CLEANUP_REQUIRED',
                          'automatic_emulator_teardown_verified': result['automatic_emulator_teardown_verified'],
                          'all_three_original_runtime_cases_passed': result['all_three_original_runtime_cases_passed'],
                          'parent_status': 'IN_PROGRESS'}))
        # Even a passing Android oracle is not final acceptance until MAIN performs
        # installed transactional host-residue cleanup and verifies empty snapshots.
        return 0 if result['all_three_original_runtime_cases_passed'] else 1
    except Exception:
        print(json.dumps({'status': 'BLOCKED', 'failure': 'CALLER_INPUT_OR_EXECUTION_BLOCKED',
                          'parent_status': 'IN_PROGRESS'}))
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
