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
import threading
from collections import deque
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


def _project_host_command(command):
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


def project_failed_wait_host(owned, failed_client):
    """FAILED_WAIT_HOST_DIAGNOSTICS_V1: independent scalars, never an oracle.

    Native/job/reader observations are neither atomic history nor proof of guest
    liveness, complete containment history or stream visibility. No raw data leaves
    the retained Commands; unavailable/busy/closed state is valid unknown.
    """
    return {'sample_state': 'SAMPLED', 'owned': _project_host_command(owned),
            'failed_client': _project_host_command(failed_client)}


def _empty_pre_teardown_host_diagnostic(state):
    return {'sample_state': state, 'owned': None, 'instrumentation': None}


def project_pre_teardown_host(owned, instrumentation):
    """PRE_TEARDOWN_OWNED_HOST_DIAGNOSTICS_V1: one retained-command snapshot only."""
    return {'sample_state': 'SAMPLED', 'owned': _project_host_command(owned),
            'instrumentation': (_project_host_command(instrumentation)
                                if instrumentation is not None else None)}


def _empty_terminal_observer_diagnostics(state):
    return {'sample_state': state, 'first_route': 'UNAVAILABLE',
            'armed': None, 'unavailable': None, 'own_anr': None,
            'source_present': None, 'source_is_event_source': 'UNAVAILABLE',
            'source_done': 'UNAVAILABLE', 'source_feed_lost': 'UNAVAILABLE',
            'source_cancelled': 'UNAVAILABLE', 'source_failure': 'UNAVAILABLE'}


def _empty_transport_first_failure_diagnostics(state):
    return {'sample_state': state, 'first_role': 'UNAVAILABLE', 'first_phase': 'UNAVAILABLE',
            'request_admission_sequence': None, 'failure_observation_sequence': None,
            'sequence_saturated': False, 'source_branch': 'UNAVAILABLE',
            'source_failure': 'UNAVAILABLE', 'source_request_admission_sequence': None,
            'source_failure_observation_sequence': None, 'receive_side': 'UNAVAILABLE',
            'event_source_first_phase': 'UNAVAILABLE', 'event_source_request_admission_sequence': None,
            'event_source_failure_observation_sequence': None}


def _valid_transport_first_failure_diagnostics(value):
    expected = _empty_transport_first_failure_diagnostics('SAMPLED')
    phases = ('PRE_EXISTING_UNCERTAINTY', 'INVALID_EXHAUSTED_BUDGET', 'SERIAL_ACQUISITION',
              'PRE_WRITE_DEADLINE', 'WRITE', 'FLUSH', 'RESPONSE_WAIT', 'RESPONSE_IDENTITY',
              'OUTER_REPLY_VALIDATION', 'PRODUCER_REPLY_VALIDATION', 'PUMP_CALLBACK_FAILURE',
              'OTHER', 'UNAVAILABLE')
    if (type(value) is not dict or any(type(key) is not str for key in value)
            or set(value) != set(expected)
            or type(value.get('sample_state')) is not str or value['sample_state'] != 'SAMPLED'
            or type(value.get('first_role')) is not str or value['first_role'] not in (
                'SDK_OUTPUT', 'EVENT_SOURCE', 'INSTRUMENTATION', 'OTHER_ADB', 'CONTROL',
                'OTHER', 'UNAVAILABLE')
            or type(value.get('first_phase')) is not str or value['first_phase'] not in phases
            or type(value.get('event_source_first_phase')) is not str or value['event_source_first_phase'] not in phases
            or type(value.get('sequence_saturated')) is not bool
            or type(value.get('source_branch')) is not str or value['source_branch'] not in (
                'UNKNOWN_TERMINAL_REASON', 'SOURCE_PUMP_EXCEPTION', 'UNAVAILABLE')
            or type(value.get('source_failure')) is not str or value['source_failure'] not in (
                'CHILD_UNCONFIRMED', 'UNAVAILABLE')
            or type(value.get('receive_side')) is not str or value['receive_side'] not in (
                'EOF', 'FRAME_VIOLATION', 'JSON_OR_ENVELOPE', 'REPEATED_FRAME',
                'STDERR_SCHEMA_OR_LIMIT', 'STDERR_READ_FAILURE', 'OTHER', 'UNAVAILABLE')):
        return False
    for name in ('request_admission_sequence', 'failure_observation_sequence',
                 'source_request_admission_sequence', 'source_failure_observation_sequence',
                 'event_source_request_admission_sequence', 'event_source_failure_observation_sequence'):
        if value[name] is not None and (type(value[name]) is not int or not 0 < value[name] <= 2 ** 31 - 1):
            return False
    first_present = value['first_role'] != 'UNAVAILABLE'
    source_present = value['source_branch'] != 'UNAVAILABLE'
    event_present = value['event_source_first_phase'] != 'UNAVAILABLE'
    return ((first_present == (value['first_phase'] != 'UNAVAILABLE'))
            and (first_present == (value['failure_observation_sequence'] is not None))
            and (first_present or value['request_admission_sequence'] is None)
            and (source_present == (value['source_failure'] != 'UNAVAILABLE'))
            and (source_present == (value['source_failure_observation_sequence'] is not None))
            and (source_present or value['source_request_admission_sequence'] is None)
            and (event_present == (value['event_source_failure_observation_sequence'] is not None))
            and (event_present or value['event_source_request_admission_sequence'] is None)
            and (value['receive_side'] == 'UNAVAILABLE' or value['first_phase'] == 'RESPONSE_WAIT'))


def _valid_terminal_observer_diagnostics(value):
    if (type(value) is not dict or any(type(key) is not str for key in value)
            or set(value) != set(_empty_terminal_observer_diagnostics('SAMPLED'))):
        return False
    if (type(value['sample_state']) is not str or value['sample_state'] != 'SAMPLED'
            or type(value['first_route']) is not str or value['first_route'] not in (
                'NOT_OBSERVED', 'SOURCE_SIGNAL', 'EVENT_DECODE_REJECTED',
                'EVENT_FRAME_REJECTED', 'EVENT_ASSIGNMENT_REJECTED', 'OTHER_UNAVAILABLE')):
        return False
    if any(type(value[name]) is not bool for name in ('armed', 'unavailable', 'own_anr', 'source_present')):
        return False
    for name in ('source_is_event_source', 'source_done', 'source_feed_lost', 'source_cancelled'):
        field = value[name]
        if not (field is None or type(field) is bool or type(field) is str and field == 'UNAVAILABLE'):
            return False
    failure = value['source_failure']
    return failure is None or type(failure) is str and failure in (
        'NONE', 'OTHER', 'UNAVAILABLE', 'OUTPUT_CAP', 'FEED_EOF', 'STREAM_FAILED',
        'COMMAND_CAP', 'CHILD_UNCONFIRMED', 'TREE_UNCONFIRMED', 'STREAM_UNCONFIRMED',
        'CANCELLED', 'VM_EXITED')


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
    stage = 'EVENT_DECODE_REJECTED'
    try:
        text = line.decode('utf-8', errors='strict')
        stage = 'EVENT_FRAME_REJECTED'
        match = re.fullmatch(r'([1-9][0-9]{0,9})\.([0-9]{9})\s+'
                            r'[1-9][0-9]{0,9}\s+[1-9][0-9]{0,9}\s+'
                            r'I\s+am_anr\s*: (.*)', text, re.DOTALL)
        if match is None:
            raise ValueError()
        stage = 'EVENT_ASSIGNMENT_REJECTED'
        timestamp = int(match[1]) * 1_000_000_000 + int(match[2])
        payload = match[3]
        if (timestamp <= after_ns or not is_own('am_anr', payload, PACKAGE)
                or not observer.armed):
            raise ValueError()
        observer.am_anr('am_anr', payload, timestamp_ns=timestamp, epoch=epoch)
    except (ValueError, UnicodeError):
        observer.failed(stage)


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


_READER_PHASE_KEY = 'E407.ReaderPhases.V1'
_READER_PHASE_PATTERN = re.compile(
    r'reader-webview-phase source=(Engine|PassiveRaster) '
    r'phase=(RuntimeLoad|Initialization|Ready|Query|Commit|PixelCopy|Retirement|Cleanup) '
    r'state=(Started|Completed|Rejected|Failed|Cancelled|Retired) '
    r'reason=(None|RendererGone|RuntimeReadyPollBudget|CommitPollBudget|WebViewError|HttpError|'
    r'Cancelled|CleanupThrew|PixelCopyResult|PixelCopySubmissionThrew|CommitUnavailable|'
    r'JavascriptSubmissionThrew|OperationIdInvalid|OperationFailed|ReceiptRejected|Destroyed|'
    r'Paused|NotReady|GeometryMismatch|UnsupportedPlatform|StaleVisualState|ViewSizeMismatch|'
    r'WindowBounds|BitmapAllocation|QueryRejected|QueryFailed|RuntimeRetired|SubmissionThrew|'
    r'RuntimeLoadThrew|PublicationReplaced|Closed|SubmissionRejected) '
    r'elapsedMs=([0-9]{1,19})(?: code=(-?[0-9]{1,10}))?'
    r'(?: mainFrame=(true|false))?(?: didCrash=(true|false))?'
    r'(?: priorityAtExit=(-?[0-9]{1,10}))?')


_READER_STAGE_FACTS = frozenset((
    'Intent', 'FoliateDestinationCommitted', 'SettlementAcknowledged', 'MaterialBindingAllocated',
    'ViewportProfileReplaced', 'RasterProgress', 'RasterProven', 'RasterDeferred', 'RasterFailed',
    'ResourceObserved', 'DeckReserved', 'DeckOwned', 'DeckPrepared', 'DeckRejected', 'ResourceReleased',
    'RendererCapacityAvailable', 'HostAvailable', 'PaginationProfileReady', 'RendererGenerationReady',
    'FrameTargetDeclared', 'FrameTargetPrepared', 'FrameTargetPreparationRejected', 'PreparedFrame',
    'OwnerAndInputPublicationApplied', 'OwnerAndInputPublicationRejected', 'CoverPostDraw',
    'WebViewExposure', 'VisibilityChanged', 'ResourceLost', 'DeadlineExpired', 'CommandRejected',
    'SemanticPortContractViolated', 'ReleaseCommandRejected', 'ReleaseCommandThrew',
    'ReleasePortContractViolated', 'OwnedWorkCancellationCompleted', 'ReleaseLedgerAdmissionRejected',
    'Retry', 'PublicationReplaced', 'PublicationClosed',
))
_READER_STAGE_CLASSIFICATIONS = frozenset((
    'CurrentTransition', 'Untagged', 'StaleTransition', 'NoActiveTransition',
))
_READER_STAGE_OUTCOMES = frozenset(('Succeeded', 'Failed', 'Cancelled', 'Deferred'))
_READER_STAGE_FAILURES = frozenset((
    'MaterialTimeout', 'CoverCommitTimeout', 'PageEntryTimeout', 'SettlementTimeout',
    'LiveExposureTimeout', 'NativeHandbackTimeout', 'ExternalRelocationTimeout',
    'ReflowTimeout', 'RestoreTimeout', 'RendererRecoveryTimeout', 'CloseDrainTimeout',
    'ActivationPrerequisiteMissing', 'InventoryIncomplete', 'InvalidLegacyResource',
    'LegacyDrainFailed', 'AmbiguousPredecessor', 'MaterialAllocationRejected',
    'AtomicPublicationRejected', 'PortRejected', 'StaleProof',
))
_READER_STAGE_CANCELLATIONS = frozenset((
    'Superseded', 'VisibilityLost', 'ReflowStarted', 'RendererLost',
    'PublicationReplaced', 'PublicationClosed', 'UserCancelled',
))
_READER_STAGE_STATES = frozenset((
    'Started', 'Completed', 'Rejected', 'Failed', 'Cancelled', 'Retired',
))
_READER_STAGE_REASONS = frozenset((
    'None', 'Stale', 'Closed', 'Cancelled', 'DiskCapacity', 'EncodeIdentityReleasing',
    'CapacityReached', 'PortRejected', 'CommandThrew', 'NotReady',
))
_READER_STAGE_FIELDS = {
    'MaterialAllocation': (),
    'InstalledPreparation': (),
    'PublicationWrite': ('current', 'persisted'),
    'PublicationAcceptance': ('applied',),
    'TargetProgress': ('completedCount', 'requiredCount'),
    'InstalledProgress': (),
    'RasterReady': ('completedCount', 'requiredCount'),
    'CoordinatorFact': ('fact', 'classification'),
    'DeadlineProgress': (),
    'DisplayPublication': (),
    'ShellPublication': (),
    'TurnTerminal': (),
}


def _valid_reader_stage(message):
    if not message.isascii():
        return False
    words = message.split(' ')
    if len(words) < 5 or words[0] != 'reader-stage':
        return False
    pairs = []
    for word in words[1:]:
        key, separator, value = word.partition('=')
        if not separator or not key or not value or '=' in value:
            return False
        pairs.append((key, value))
    required = ('phase', 'state', 'reason', 'elapsedMs')
    if tuple(key for key, _ in pairs[:4]) != required:
        return False
    phase, state, reason, elapsed = (value for _, value in pairs[:4])
    if (phase not in _READER_STAGE_FIELDS or state not in _READER_STAGE_STATES
            or reason not in _READER_STAGE_REASONS or not elapsed.isdecimal()
            or int(elapsed) > 2**63 - 1):
        return False
    extra = pairs[4:]
    expected = _READER_STAGE_FIELDS[phase]
    if phase not in ('CoordinatorFact',) and tuple(key for key, _ in extra) != expected:
        return False
    if phase in ('TargetProgress', 'RasterReady'):
        completed, required_count = (value for _, value in extra)
        return (completed.isdecimal() and required_count.isdecimal()
                and 0 <= int(completed) <= int(required_count) <= 2**31 - 1)
    if phase != 'CoordinatorFact':
        return all(value in ('true', 'false') for _, value in extra)
    if len(extra) < 2 or tuple(key for key, _ in extra[:2]) != expected:
        return False
    if (extra[0][1] not in _READER_STAGE_FACTS
            or extra[1][1] not in _READER_STAGE_CLASSIFICATIONS):
        return False
    index, outcome = 2, None
    if index < len(extra) and extra[index][0] == 'applied':
        if extra[index][1] not in ('true', 'false'):
            return False
        index += 1
    if index < len(extra) and extra[index][0] == 'outcome':
        outcome = extra[index][1]
        if outcome not in _READER_STAGE_OUTCOMES:
            return False
        index += 1
    if index < len(extra) and extra[index][0] == 'failure':
        if outcome != 'Failed' or extra[index][1] not in _READER_STAGE_FAILURES:
            return False
        index += 1
    if index < len(extra) and extra[index][0] == 'cancellation':
        if outcome != 'Cancelled' or extra[index][1] not in _READER_STAGE_CANCELLATIONS:
            return False
        index += 1
    return index == len(extra)


def _valid_reader_phase(message):
    if type(message) is not str or len(message) > 512:
        return False
    if message.startswith('reader-stage'):
        return _valid_reader_stage(message)
    match = _READER_PHASE_PATTERN.fullmatch(message)
    return (match is not None and int(match[5]) <= 2**63 - 1
            and all(number is None or -2**31 <= int(number) < 2**31
                    for number in (match[6], match[9])))


class _ReaderPhaseStream:
    """Diagnostic-only source-declared method, NOT framework continuity or an oracle.

    Original instrument stdout reader only. First128/latest128 records, <=1024
    partial bytes and 4MiB input. Whole overlong lines are omitted, never suffix
    parsed. Only the explicit fixed-field transport and closed formatter grammar
    survive. No worker, timer, control, raw output or exception is retained.
    """
    INPUT_CAP = 4 * 1024 * 1024
    PARTIAL_CAP = 1024
    PREFIX = b'INSTRUMENTATION_STATUS: E407.ReaderPhase.V1='
    PROCESS_CRASH_LINE = b'INSTRUMENTATION_RESULT: shortMsg=Process crashed.'

    def __init__(self, *, qualified, clock=time.monotonic):
        self._lock = threading.Lock()
        self._qualified = qualified is True
        self._clock = clock
        self._anchor = clock()
        self._elapsed = 0
        self._clock_clamped = False
        self._first = []
        self._latest = deque(maxlen=128)
        self._partial = bytearray()
        self._overlong = False
        self._framework_process_crash = False
        self._counts = dict(received_bytes=0, ignored_stderr_bytes=0,
            accepted_records=0, rejected_phase_lines=0, omitted_overlong_lines=0,
            ignored_lines=0, omitted_cap_bytes=0)

    def feed(self, index, chunk):
        if type(index) is not int or index not in (0, 1) or type(chunk) is not bytes:
            raise ValueError('READER_PHASE_INPUT_INVALID')
        with self._lock:
            previous = self._counts['received_bytes']
            self._counts['received_bytes'] += len(chunk)
            if index == 1:
                self._counts['ignored_stderr_bytes'] += len(chunk)
                if previous + len(chunk) >= self.INPUT_CAP:
                    self._partial.clear()
                    self._overlong = False
                return
            limit = min(len(chunk), max(0, self.INPUT_CAP - previous))
            self._counts['omitted_cap_bytes'] += len(chunk) - limit
            if not limit:
                return
            arrival = max(0, int((self._clock() - self._anchor) * 1000))
            if arrival < self._elapsed:
                arrival = self._elapsed
                self._clock_clamped = True
            self._elapsed = arrival
            cursor = 0
            while cursor < limit:
                newline = chunk.find(b'\n', cursor, limit)
                end = limit if newline < 0 else newline
                if not self._overlong:
                    if len(self._partial) + end - cursor > self.PARTIAL_CAP:
                        self._partial.clear()
                        self._overlong = True
                    else:
                        self._partial.extend(chunk[cursor:end])
                if newline < 0:
                    break
                if self._overlong:
                    self._counts['omitted_overlong_lines'] += 1
                else:
                    self._retain(bytes(self._partial).rstrip(b'\r'), arrival)
                self._partial.clear()
                self._overlong = False
                cursor = newline + 1
            if previous + limit >= self.INPUT_CAP:
                self._partial.clear()
                self._overlong = False

    def _retain(self, line, arrival):
        if line == self.PROCESS_CRASH_LINE:
            self._framework_process_crash = True
            return
        if not self._qualified:
            self._counts['ignored_lines'] += 1
            return
        if not line.startswith(self.PREFIX):
            self._counts['ignored_lines'] += 1
            return
        try:
            method, separator, message = line[len(self.PREFIX):].decode('ascii').partition('|')
            valid = separator and method in METHODS and _valid_reader_phase(message)
        except UnicodeError:
            valid = False
        if not valid:
            self._counts['rejected_phase_lines'] += 1
            return
        sequence = self._counts['accepted_records']
        self._counts['accepted_records'] += 1
        row = dict(sequence=sequence, elapsed_ms=arrival, method=method, message=message)
        if len(self._first) < 128:
            self._first.append(row)
        else:
            self._latest.append(row)

    def snapshot(self):
        with self._lock:
            records = [dict(row) for row in self._first]
            records.extend(dict(row) for row in self._latest)
            return dict(profile='FAILURE_INDEPENDENT_READER_PHASE_V1',
                attribution='SOURCE_DECLARED_METHOD_NOT_FRAMEWORK_CONTINUITY',
                time_basis='HOST_OBSERVED_LINE_ARRIVAL', source_qualified=self._qualified,
                records=records, counts=dict(self._counts),
                dropped_records=self._counts['accepted_records'] - len(records),
                partial_bytes=len(self._partial),
                input_cap_reached=self._counts['received_bytes'] >= self.INPUT_CAP,
                framework_process_crash=('FRAMEWORK_REPORTED_PROCESS_CRASH'
                                         if self._framework_process_crash else 'UNKNOWN'),
                clock_clamped=self._clock_clamped, acceptance_evidence=False)


def _project_reader_phases(text, labels):
    """A bounded explicit fixture field; no raw logcat or stream is a message source."""
    active, block, duplicate = None, {}, False
    retained, invalid, rejections = {}, set(), 0

    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError()
            result[key] = value
        return result

    for line in text.splitlines():
        if line.startswith('INSTRUMENTATION_STATUS: '):
            key, sep, value = line[len('INSTRUMENTATION_STATUS: '):].partition('=')
            duplicate |= not sep or key in block
            block[key] = value if len(value) <= 65536 else None
        elif re.fullmatch(r'INSTRUMENTATION_STATUS_CODE: -?\d+', line):
            code = int(line.rsplit(' ', 1)[1])
            if _READER_PHASE_KEY in block:
                try:
                    if (duplicate or code != 2 or _READER_PHASE_KEY not in labels or active is None
                            or active in retained or active in invalid
                            or set(block) - {'class', 'test', 'stream', _READER_PHASE_KEY}
                            or ('class' in block and block['class'] != CLASS)
                            or ('test' in block and block['test'] != active)):
                        raise ValueError()
                    value = json.loads(block[_READER_PHASE_KEY], object_pairs_hook=unique)
                    if (type(value) is not dict or set(value) != {'version', 'method', 'records', 'dropped_records'}
                            or type(value['version']) is not int or value['version'] != 1
                            or value['method'] != active or type(value['records']) is not list
                            or len(value['records']) > 128 or type(value['dropped_records']) is not int
                            or not 0 <= value['dropped_records'] <= 65535):
                        raise ValueError()
                    for message in value['records']:
                        if not _valid_reader_phase(message):
                            raise ValueError()
                    retained[active] = value
                except (ValueError, TypeError, RecursionError):
                    rejections = min(65535, rejections + 1)
                    if active is not None:
                        retained.pop(active, None)
                        invalid.add(active)
            if code == 1:
                active = block.get('test') if block.get('class') == CLASS and block.get('test') in METHODS else None
            elif code != 2:
                active = None
            block, duplicate = {}, False
    if _READER_PHASE_KEY in block:
        rejections = min(65535, rejections + 1)
        retained.pop(active, None)
    return {'reader_phases_by_original_method': retained, 'reader_phase_rejection_count': rejections}


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
            if value in (_STARTUP_WITNESS_KEY, _READER_PHASE_KEY, 'E407.ReaderPhase.V1') or value not in labels:
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
            'startup_witness_rejection_count': witness_rejections,
            **_project_reader_phases(text, labels)}


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


_GATE = None
IMMUTABLE_APK_SHA256 = '65cc8d619c03aba32043eb77603c6254fc61aac792e9581b8993bd9b00a8c517'
IMMUTABLE_SEAL_SHA256 = '93dc372cde3d0acaf51e0908f595443306f024be9ba6406774d0ad7d8bcdfa0a'
HOST_MIGRATION_PATHS = frozenset({
    'scripts/task407-runtime-runner.py', 'scripts/task407-runtime-supervisor.py',
    'scripts/test-task407-runtime-runner.py', 'scripts/test-task407-runtime-supervisor.py',
    'scripts/task407-runtime-gate.py', 'scripts/test-task407-runtime-gate.py',
    'docs/superpowers/specs/2026-10-04-reader-fresh-session-checkpoint-amendment.md',
})


def _runtime_gate():
    global _GATE
    if _GATE is None:
        spec = importlib.util.spec_from_file_location('task407_runtime_gate',
            Path(__file__).with_name('task407-runtime-gate.py'))
        module = importlib.util.module_from_spec(spec)
        sys.modules[spec.name] = module
        spec.loader.exec_module(module)
        _GATE = module
    return _GATE


def validate_live_session(record, watchdog_id):
    """Prospective gate contract; historical peer-ACK contracts are test-only."""
    expected = {'kind': 'task407-gated-emulator-runtime-session-v1',
        'serial': SERIAL, 'avd': AVD, 'scope': 'original-three-with-independent-supervision',
        'user_session_authorized': True, 'no_snapshot_save': True,
        'preserve_app_data': True, 'clear_logcat': False}
    if (type(record) is not dict or any(type(record.get(k)) is not type(v) or record[k] != v
            for k, v in expected.items()) or 'peer_coordination_complete' in record
            or type(watchdog_id) is not str or not re.fullmatch(r'[A-Za-z0-9_-]{1,128}', watchdog_id)
            or record.get('watchdog_id') != watchdog_id
            or type(record.get('apk_path')) is not str or not record['apk_path']):
        raise ValueError('SESSION_AUTHORITY_UNCONFIRMED')
    gate = record.get('gate')
    runtime = _runtime_gate()
    fields = {'helper': runtime.HELPER, 'release': runtime.RELEASE, 'version': '0.16.1',
        'emulator_profile': 'reader-diagnostics', 'component_version': '37.2.12',
        'scratch_root': runtime.SCRATCH, 'owner_task': 'Validate original reader runtime cases',
        'thread_name': 'Navic'}
    if (type(gate) is not dict or any(gate.get(k) != v for k, v in fields.items())
            or type(gate.get('expected_owner')) is not dict
            or set(gate['expected_owner']) != {'system', 'session_sha256', 'thread_name'}
            or any(type(v) is not str or not v for v in gate['expected_owner'].values())
            or not re.fullmatch(r'[0-9a-f]{64}', gate['expected_owner']['session_sha256'])
            or gate['expected_owner']['thread_name'] != 'Navic'
            or type(gate.get('binding')) is not dict):
        raise ValueError('GATE_PROFILE_UNCONFIRMED')
    binding = gate['binding']
    if (set(binding) != {'avd', 'serial', 'port', 'avd_directory', 'avd_home', 'sdk_root', 'emulator_root'}
            or binding['avd'] != AVD or binding['serial'] != SERIAL
            or type(binding['port']) is not int or binding['port'] != 5554
            or any(type(binding[k]) is not str or not Path(binding[k]).is_absolute()
                   for k in ('avd_directory', 'avd_home', 'sdk_root', 'emulator_root'))
            or not isinstance(gate.get('pins'), list) or not gate['pins']):
        raise ValueError('GATE_BINDING_UNCONFIRMED')


def validate_host_qualification(seal, receipt, root, seal_sha256):
    """Immutable Android qualification + narrowly admitted NEW host qualification.

    MAIN supplies the actual ordered SPEC→MAIN→QUALITY→MAIN receipt after review
    and publication. Changed host/doc pins never rewrite historical seal evidence.
    All other original pins (including diagnostics) remain mandatory unchanged.
    """
    if (seal_sha256 != IMMUTABLE_SEAL_SHA256
            or seal.get('kind') != 'full-lower-qualified-pushed-source-emulator-only-apk-seal'
            or seal.get('original_timeout_and_acceptance_oracles_changed') is not False
            or seal.get('runtime_accepted') is not False
            or not re.fullmatch(r'[0-9a-f]{40}', seal.get('base_source_commit', ''))
            or seal.get('apk', {}).get('sha256') != IMMUTABLE_APK_SHA256
            or seal['apk'].get('package') != PACKAGE or seal['apk'].get('production_signed') is not False
            or receipt.get('kind') != 'task407-gate-host-qualification-v1'
            or receipt.get('seal_sha256') != seal_sha256
            or receipt.get('apk_sha256') != IMMUTABLE_APK_SHA256
            or receipt.get('ordered') is not True or receipt.get('runtime_accepted') is not False
            or any(receipt.get(key) != 'PASS' for key in (
                'spec', 'main_after_spec', 'quality', 'main_after_quality', 'host_tests'))
            or not re.fullmatch(r'[0-9a-f]{40}', receipt.get('source_commit', ''))):
        raise ValueError('HOST_QUALIFICATION_UNCONFIRMED')
    root = Path(root).resolve()
    def indexed(rows):
        result = {}
        for row in rows:
            path = Path(row['path'])
            path = (path if path.is_absolute() else root / path).resolve()
            if path in result or not re.fullmatch(r'[0-9a-f]{64}', row['sha256']):
                raise ValueError('CANDIDATE_PIN_INVALID')
            result[path] = row['sha256']
        return result
    original, overrides = indexed(seal['source_pins']), indexed(receipt['pins'])
    admitted = {root / name for name in HOST_MIGRATION_PATHS}
    unaffected = {root / 'scripts' / name for name in (
        'task407-runtime-diagnostics.py', 'test-task407-runtime-diagnostics.py')}
    if (set(overrides) != admitted or not unaffected <= original.keys()
            or not {root / 'scripts' / name for name in (
                'task407-runtime-runner.py', 'task407-runtime-supervisor.py',
                'test-task407-runtime-runner.py', 'test-task407-runtime-supervisor.py')} <= original.keys()):
        raise ValueError('HOST_OVERRIDE_SCOPE_INVALID')
    original.update(overrides)
    return [dict(path=str(path), sha256=digest) for path, digest in original.items()], receipt['source_commit']


def run_gated_session(supervision, record, labels, *, pins, caps, watchdog_id, gate=None, cleanup=None):
    validate_live_session(record, watchdog_id)
    runtime = _runtime_gate()
    if cleanup is None:
        cleanup = (runtime.prepare_runtime_cleanup(record) if gate is None
                   else lambda: runtime.cleanup_runtime_scratch(record))
    return run_session(supervision, record, labels, pins=pins, caps=caps,
                       watchdog_id=watchdog_id, gate=gate, gated=True, cleanup=cleanup)


_DIAGNOSTICS = None


def _runtime_diagnostics():
    global _DIAGNOSTICS
    if _DIAGNOSTICS is None:
        spec = importlib.util.spec_from_file_location('task407_runtime_diagnostics',
            Path(__file__).with_name('task407-runtime-diagnostics.py'))
        module = importlib.util.module_from_spec(spec)
        sys.modules[spec.name] = module
        spec.loader.exec_module(module)
        _DIAGNOSTICS = module
    return _DIAGNOSTICS


def run_session(supervision, record, labels, *, pins, caps, watchdog_id, gate=None, gated=False, cleanup=None):
    """One guarded attempt; SDK dispatch is explicit, selected and bounded.

    The normal CLI supplies the real supervision module. Host orchestration tests
    inject SDK replies, retaining real guardian/job behavior; those replies never
    constitute Android or authority evidence. The gated path consumes verified
    transactional scratch cleanup after VM supervision, before taking outcome bytes.
    Historical direct host fixtures retain their non-live API only.
    """
    (validate_live_session if gated else validate_session)(record, watchdog_id)
    if caps.postboot_preparation <= 0:
        raise ValueError('POSTBOOT_RESERVATION_REQUIRED')
    if not pins_match(pins):
        raise ValueError('SOURCE_PINS_CHANGED')
    s, adb = supervision, 'gate-adb' if gated else record['adb']
    command_class = s.Command
    runtime_gate = _runtime_gate() if gated else None
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
    diagnostic_errors = 0
    startup_log = phase_trace = reader_live = diagnostics = None
    try:
        reader_live = _ReaderPhaseStream(qualified='E407.ReaderPhase.V1' in labels)
    except BaseException:
        diagnostic_errors += 1
    try:
        diagnostics = _runtime_diagnostics()
        startup_log = diagnostics.EmulatorStartupLog()
        phase_trace = diagnostics.PhaseTrace()
    except BaseException:
        diagnostic_errors += 1

    def trace(name, event, outcome='NONE'):
        nonlocal diagnostic_errors
        if phase_trace is None:
            return
        try:
            metrics = {}
            if owned is not None and name in ('OWNED_LAUNCH', 'WAIT_FOR_DEVICE',
                    'SYS_BOOT_COMPLETED', 'BOOT_HANDOFF', 'TEARDOWN'):
                usage = owned.snapshot_resource_usage()
                if usage.get('state') == 'SAMPLED':
                    metrics = {key: value for key, value in usage.items() if key != 'state'}
            phase_trace.record(name, event, outcome=outcome, metrics=metrics)
        except BaseException:
            diagnostic_errors += 1

    def close_startup_log():
        nonlocal diagnostic_errors
        if startup_log is not None:
            try:
                startup_log.close()
            except BaseException:
                diagnostic_errors += 1

    def sample_host_memory():
        nonlocal diagnostic_errors
        try:
            sample = diagnostics.host_memory_snapshot() if diagnostics is not None else None
            if (type(sample) is not dict or set(sample) not in ({'sample_state'},
                    {'sample_state', 'available_physical_bytes', 'available_commit_bytes'})
                    or sample.get('sample_state') not in ('SAMPLED', 'UNAVAILABLE')
                    or (sample.get('sample_state') == 'SAMPLED' and
                        any(type(sample[key]) is not int or not 0 <= sample[key] <= 2 ** 63 - 1
                            for key in ('available_physical_bytes', 'available_commit_bytes')))):
                raise ValueError('HOST_MEMORY_SAMPLE_INVALID')
            return dict(sample)
        except BaseException:
            diagnostic_errors += 1
            return {'sample_state': 'UNAVAILABLE'}

    trace('SESSION', 'START')
    result = {'kind': 'original-three-supervised-runtime-not-pair-or-performance-acceptance',
              'parent_status': 'IN_PROGRESS', 'watchdog_id': watchdog_id,
              # Per-process diagnostic request; this does not establish filesystem report state.
              'launch_transport_profile': 'CRASH_REPORTING_DISABLED_STARTUP_ISOLATION_V1',
              'host_crash_reporting': 'DISABLED_FOR_THIS_DIAGNOSTIC_PROCESS',
              'existing_crash_reports_action': 'NO_UPLOAD_OR_DELETION_REQUESTED',
              'phase_trace_scope': 'HOST_CALLER_ENVELOPES_TEARDOWN_INCLUDES_GUARDIAN_WAIT',
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
              'pre_teardown_host_diagnostics_profile': 'PRE_TEARDOWN_OWNED_HOST_DIAGNOSTICS_V1',
              'pre_teardown_host_diagnostics': _empty_pre_teardown_host_diagnostic('NOT_REACHED'),
              'terminal_observer_diagnostics_profile': 'TERMINAL_OBSERVER_SOURCE_STATE_V1',
              'terminal_observer_diagnostics_scope': 'PRE_TEARDOWN_AFTER_TERMINAL_DECISION',
              'terminal_observer_diagnostics': _empty_terminal_observer_diagnostics('NOT_REACHED'),
              'transport_first_failure_diagnostics_profile': 'FIRST_FAILURE_TRANSPORT_DIAGNOSTICS_V1',
              'transport_first_failure_diagnostics_scope': 'PRE_TEARDOWN_AFTER_TERMINAL_DECISION',
              'transport_first_failure_diagnostics': _empty_transport_first_failure_diagnostics('NOT_REACHED'),
              'host_memory_observations_profile': 'HOST_MEMORY_HEADROOM_V1',
              'host_memory_observations': {'initial_session': {'sample_state': 'UNAVAILABLE'},
                                           'pre_teardown': {'sample_state': 'NOT_REACHED'}},
              'owned_job_engine_path_observation_profile': 'OWNED_JOB_ENGINE_PATH_V1',
              'owned_job_engine_path_observation': 'NOT_SAMPLED',
              'owned_engine_window_topology_profile': 'OWNED_ENGINE_WINDOW_TOPOLOGY_V1',
              'owned_engine_window_topology': 'NOT_SAMPLED',
              'owned_launch_diagnostics_profile': 'OWNED_LAUNCH_DIAGNOSTICS_V4',
              'owned_launch_diagnostics': _empty_owned_launch_diagnostic('OWNED_LAUNCH_ABSENT')}
    result['host_memory_observations']['initial_session'] = sample_host_memory()

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

        if role != 'SYS_BOOT_COMPLETED':
            trace(role, 'START')
        try:
            if guardian is not None:
                timeout = min(timeout, prepare())
            dispatched_at = time.monotonic()
            deadline = dispatched_at + timeout
            budget_ms = max(0, int(timeout * 1000))
            boundary = 'SPAWN'
            spawn_options = dict(timeout=timeout, max_bytes=65536, tree_owned=False)
            if gated:
                spawn_options['diagnostic_role'] = ('INSTRUMENTATION' if role == 'INSTRUMENTATION'
                                                     else 'OTHER_ADB')
            child = command_class.spawn([adb, '-s', SERIAL, *arguments], **spawn_options)
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
            if role != 'SYS_BOOT_COMPLETED':
                trace(role, 'END')
            return reply
        except BaseException as command_error:
            if role != 'SYS_BOOT_COMPLETED':
                trace(role, 'FAIL', fixed_preflight_reason(command_error))
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
        if not gated:
            code, out, err = command('SELECTED_ABSENCE', ['get-state'], caps.command)
            if not (code != 0 and not out.strip() and
                    err.strip() == ("error: device '" + SERIAL + "' not found").encode('ascii')):
                raise ValueError('SELECTED_ABSENCE_UNCONFIRMED')
        phase = 'OWNED_LAUNCH'
        trace('OWNED_LAUNCH', 'START')
        if gated:
            gate = gate if gate is not None else runtime_gate.GateSession.launch(record)
            launch_started = gate.launch_started
            owned = runtime_gate.OwnedGate(gate)
            command_class = runtime_gate.command_type(s, gate)
            gate._sdk_observer = startup_log.feed if startup_log is not None else None
        else:
            launch_started = time.monotonic()
            owned = s.Command.spawn([record['emulator'], '-avd', AVD, '-port', '5554',
                                     '-no-snapshot', '-no-boot-anim', '-gpu', 'swiftshader',
                                     '-show-kernel', '-verbose', '-crash-report-mode', 'disabled'],
                                    timeout=session_budget, max_bytes=4 * 1024 * 1024, tree_owned=True,
                                    observe_output=startup_log.feed if startup_log is not None else None)
        guardian_deadline = launch_started + session_budget
        trace('OWNED_LAUNCH', 'END')
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

        def observe_pre_teardown():
            # Guardian calls this after its original terminal decision and before any
            # instrument/observer/console/tree teardown action. It never retries.
            result['host_memory_observations']['pre_teardown'] = sample_host_memory()
            result['pre_teardown_host_diagnostics'] = _empty_pre_teardown_host_diagnostic('SNAPSHOT_GAP')
            result['terminal_observer_diagnostics'] = _empty_terminal_observer_diagnostics('SNAPSHOT_GAP')
            result['transport_first_failure_diagnostics'] = _empty_transport_first_failure_diagnostics('SNAPSHOT_GAP')
            try:
                snapshot = observer.diagnostic_snapshot()
                if _valid_terminal_observer_diagnostics(snapshot):
                    # Detached fixed values preserve this seam against later teardown mutation.
                    result['terminal_observer_diagnostics'] = dict(snapshot)
            except BaseException:
                pass
            if gated:
                try:
                    snapshot = owned.snapshot_transport_diagnostics()
                    if _valid_transport_first_failure_diagnostics(snapshot):
                        # Detached fixed values preserve this seam against later teardown mutation.
                        result['transport_first_failure_diagnostics'] = dict(snapshot)
                except BaseException:
                    pass
            try:
                result['pre_teardown_host_diagnostics'] = project_pre_teardown_host(owned, instrument)
                if gated:
                    result['owned_launch_diagnostics'] = project_owned_launch(owned)
            except BaseException:
                pass

        anchor = owned if gated else s.OwnedProcess(original, stop_original_tree)
        guardian_options = {'gate_lifecycle': gate} if gated else {}
        guardian = s.Supervisor(owned=anchor, commands=LifecycleCommands(),
            observer=observer, avd=AVD, serial=SERIAL,
            contract=s.LaunchContract(watchdog_id=watchdog_id, no_snapshot_save=True),
            caps=caps, launch_started=launch_started, pre_teardown_observation=observe_pre_teardown,
            **guardian_options)
        if gated:
            result.update(launch_transport_profile='COORDINATOR_0_16_1_READER_DIAGNOSTICS_37_2_12_EOF_V1',
                launch_anchor_profile='ACQUIRED_NATIVE_CREATION_FILETIME_TO_MONOTONIC_V1',
                environment_equivalence='CHANGED_HOST_PROFILE_NOT_OLD_ENVIRONMENT_QUALIFICATION',
                phase_trace_scope='HOST_CALLER_ENVELOPES_INCLUDE_GATE_QUEUE_NOT_VM_EXECUTION',
                lifecycle_proof='GATE_NATIVE_TREE_LEASE_HOST_STORAGE_V1',
                peer_coordination='NOT_ASSERTED_LIVE_ORIGINAL_CHAT_LEASE_INSTEAD')
            gate.verify_live(min(caps.command, require_preparation()))
            gate.start_sdk_observation(caps.command)
            while not gate.console_ready(min(caps.command, require_preparation())):
                gate.drain_sdk(min(caps.command, require_preparation()))
                if not guardian.wait_preparation(.25):
                    raise ValueError('UI_READY_UNCONFIRMED')
            gate.drain_sdk(min(caps.command, require_preparation()))
        phase = 'UI_READY'
        readiness_step = 'WAIT_FOR_DEVICE'
        code, out, err = command('WAIT_FOR_DEVICE', ['wait-for-device'], caps.preparation)
        if code != 0 or err:
            raise ValueError('DEVICE_WAIT_UNCONFIRMED')
        readiness_step = 'OWNED_AVD_IDENTITY'
        if gated:
            # Live gate target/native-job proof replaces forbidden ADB emu identity;
            # this never manufactures the historical console response.
            if not gate.console_ready(min(caps.command, require_preparation())):
                raise ValueError('OWNED_AVD_UNCONFIRMED')
        else:
            code, out, err = command('OWNED_AVD_IDENTITY', ['emu', 'avd', 'name'], caps.command)
            if code != 0 or err or [v for v in out.splitlines() if v] != [AVD.encode(), b'OK']:
                raise ValueError('OWNED_AVD_UNCONFIRMED')
        readiness_step = 'SYS_BOOT_COMPLETED'
        trace('SYS_BOOT_COMPLETED', 'START')
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
        trace('SYS_BOOT_COMPLETED', 'END')
        trace('BOOT_HANDOFF', 'START')
        handoff = guardian.complete_boot()
        trace('BOOT_HANDOFF', 'END' if handoff else 'FAIL')
        result['boot_handoff_observation'] = {
            'accepted': handoff, **guardian.preparation_observation()}
        if not handoff:
            raise ValueError('BOOT_HANDOFF_REJECTED')
        readiness_step = None
        # Host SDK source boundary ends before any app installation or execution.
        if gated:
            drain_deadline = time.monotonic() + min(caps.command, require_preparation())
            while gate.drain_sdk(max(0, drain_deadline - time.monotonic())):
                require_preparation()
                if time.monotonic() >= drain_deadline:
                    raise ValueError('PREFLIGHT_COMMAND_INCOMPLETE')
        close_startup_log()
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
        trace('OBSERVER_ADMISSION', 'START')
        code, out, err = shell('CURSOR', ['toybox', 'date', '+%s.%N'], caps.command)
        if code != 0 or err:
            raise ValueError('CURSOR_COMMAND_UNCONFIRMED')
        cursor = parse_cursor(out)
        epoch = watchdog_id  # Attempt identity only; never used as a guest clock.
        require_preparation()
        source_options = dict(timeout=session_budget, max_bytes=65536, tree_owned=False,
            on_line=lambda line: consume_event(observer, line, epoch=epoch,
                                               after_ns=cursor, is_own=s.is_own_am_anr))
        if gated:
            source_options['diagnostic_role'] = 'EVENT_SOURCE'
        source = command_class.spawn(observer_argv(adb, cursor), **source_options)
        require_preparation()
        if not observer.arm(epoch=epoch, after_ns=cursor, source=source):
            raise ValueError('OBSERVER_ADMISSION_REJECTED')
        trace('OBSERVER_ADMISSION', 'END')
        phase = 'INSTRUMENTATION'
        trace('INSTRUMENTATION', 'START')
        require_preparation()
        instrument_options = dict(timeout=caps.instrumentation, max_bytes=4 * 1024 * 1024,
            tree_owned=False, observe_output=reader_live.feed if reader_live is not None else None)
        if gated:
            instrument_options['diagnostic_role'] = 'INSTRUMENTATION'
        instrument = command_class.spawn(shell_argv(adb,
            ['am', 'instrument', '-w', '-r', '-e', 'class', CLASS, COMPONENT]), **instrument_options)
        attached = guardian.attach_instrumentation(instrument)
        if not attached:
            raise ValueError('INSTRUMENTATION_ADMISSION_REJECTED')
    except BaseException as error:
        failure = phase + '_BLOCKED'
        failure_reason = fixed_preflight_reason(error)
        if readiness_step == 'SYS_BOOT_COMPLETED':
            trace('SYS_BOOT_COMPLETED', 'FAIL', failure_reason)
        elif phase in ('OWNED_LAUNCH', 'INSTALL', 'OBSERVER_ADMISSION', 'INSTRUMENTATION'):
            trace(phase, 'FAIL', failure_reason)
        if readiness_step not in _READINESS_STEPS:
            readiness_step = None
        if guardian is not None and guardian.preparation_remaining() > 0:
            guardian.abort()
    finally:
        close_startup_log()
        trace('TEARDOWN', 'START')
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
    trace('TEARDOWN', 'END' if clean else 'FAIL')
    if attached:
        trace('INSTRUMENTATION', 'END' if report is not None and report.eligible_for_oracle else 'FAIL')
    trace('SESSION', 'END' if clean and failure is None else 'FAIL')
    for key, collector in (('emulator_startup_log', startup_log),
                           ('phase_trace', phase_trace), ('reader_live_phases', reader_live)):
        try:
            result[key] = collector.snapshot() if collector is not None else None
        except BaseException:
            result[key] = None
            diagnostic_errors += 1
    result['diagnostic_errors'] = diagnostic_errors + sum(
        getattr(child, 'diagnostic_output_errors', 0) for child in (owned, instrument)
        if child is not None)
    if failed_boundary is not None:
        result['failed_preflight_command'] = {'before_cleanup': asdict(failed_boundary),
                                               **failed_cleanup}
    result.update({'fixed_preflight_failure': failure,
                   'fixed_readiness_step': readiness_step,
                   'fixed_preflight_failure_reason': failure_reason,
                   'automatic_emulator_teardown_verified': clean,
                   'preflight_clients_settled': clients_settled,
                   'supervisor': asdict(report) if report is not None else None})
    if gated and gate is not None:
        result['gate_transport_observations'] = {
            'proof_scope': 'LIVE_LOCAL_OWNERSHIP_NOT_USER_AUTHORIZATION',
            'sdk_output_bytes': gate._sdk_total, 'sdk_output_gap': gate._sdk_gap,
            'sdk_diagnostic_error_count': gate.diagnostic_output_errors,
            'sdk_diagnostic_gap': gate.diagnostic_output_errors != 0,
            'observer_intentionally_cancelled': bool(source is not None and source.cancelled),
            'uncertain_rpc': bool(gate.uncertain),
            'original_keeper_recovery_required': not bool(report is not None and report.lease_released),
            'native_sdk_exit_success': (report.native_exit_code == 0
                                        if report is not None and report.native_exit_code is not None else None),
        }
        if owned is not None:
            owned.discard()
    cleanup_complete = not gated  # Historical direct fixtures are not the live CLI.
    if gated:
        result['host_cleanup_status'] = 'NOT_ATTEMPTED'
        if clean:
            # The original guardian/native/lease/wrapper consumers have finished.
            # Keep original bounded instrument bytes in RAM, untouched, while the
            # separately managed installed cleanup consumes its genuine transaction.
            try:
                cleanup_complete = cleanup is not None and cleanup() is True
            except BaseException:
                cleanup_complete = False
            result['host_snapshot_residue_cleanup_required'] = not cleanup_complete
            result['host_cleanup_status'] = 'VERIFIED' if cleanup_complete else 'UNCONFIRMED'
        if not cleanup_complete and instrument is not None:
            instrument.discard()  # No persistent bytes or deferred oracle replay.
    if clean and report.eligible_for_oracle and cleanup_complete:
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
    this caller. The optional diagnostic implementation is qualified alongside
    the original supervisor/runner and their tests, not just the original four inputs.
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
        'task407-runtime-runner.py', 'test-task407-runtime-runner.py',
        'task407-runtime-diagnostics.py', 'test-task407-runtime-diagnostics.py')}

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
        validate_live_session(record, watchdog)
        root = Path(__file__).resolve().parents[1]
        seal = read_checked_json(record['seal_path'], record['seal_sha256'])
        receipt = read_checked_json(record['host_qualification_path'], record['host_qualification_sha256'])
        pins, source_commit = validate_host_qualification(seal, receipt, root, record['seal_sha256'])
        if Path(record['apk_path']).resolve() != Path(seal['apk']['path']).resolve():
            raise ValueError('CANDIDATE_APK_PATH_CHANGED')
        runtime = _runtime_gate()
        runtime_paths = {Path(row['path']).resolve() for row in record['gate']['pins']}
        if (len(runtime_paths) != len(record['gate']['pins'])
                or runtime_paths != {Path(path).resolve() for path in runtime.RUNTIME_PATHS}):
            raise ValueError('GATE_RUNTIME_PINS_INCOMPLETE')
        active_path = Path('C:/Users/darka/AppData/Local/Tools/llm-coordinator/active.json')
        active_bytes = active_path.read_bytes()
        active = json.loads(active_bytes)
        if active.get('release') != runtime.RELEASE or active.get('version') != '0.16.1':
            raise ValueError('GATE_ACTIVE_RELEASE_CHANGED')
        component = json.loads((Path(runtime.RELEASE_ROOT) / 'integration/emulator-components.json').read_bytes())
        selected = component['profiles']['reader-diagnostics']
        if (selected.get('version') != '37.2.12'
                or Path(selected['root']).resolve() != Path(record['gate']['binding']['emulator_root']).resolve()):
            raise ValueError('GATE_COMPONENT_CHANGED')
        pins += record['gate']['pins'] + [
            {'path': str(active_path), 'sha256': hashlib.sha256(active_bytes).hexdigest()},
            {'path': seal['apk']['path'], 'sha256': seal['apk']['sha256']}]
        if not pins_match(pins):
            raise ValueError('CANDIDATE_BYTES_CHANGED')
        for command, expected in ((['rev-parse', 'HEAD'], source_commit),
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
            result = run_gated_session(supervision, record, labels, pins=pins,
                                       caps=caps, watchdog_id=watchdog)
            result.update({'source_commit': source_commit,
                           'android_qualification_source_commit': seal['base_source_commit'],
                           'apk_sha256': seal['apk']['sha256'], 'profile': asdict(caps),
                           'seal_sha256': record['seal_sha256'],
                           'host_qualification_sha256': record['host_qualification_sha256']})
            json.dump(result, output, indent=2)
        shutdown = result.get('supervisor') or {}
        native_code, workflow_code = shutdown.get('native_exit_code'), shutdown.get('workflow_exit_code')
        sdk_ok = native_code == 0 and workflow_code == 0 and shutdown.get('command_failures') is False
        cleanup_ok = result.get('host_snapshot_residue_cleanup_required') is False
        print(json.dumps({'status': ('HOST_RUNTIME_CLOSED' if sdk_ok and cleanup_ok
                                    else 'SDK_WORKFLOW_FAILED' if not sdk_ok else 'HOST_CLEANUP_UNCONFIRMED'),
                          'automatic_emulator_teardown_verified': result['automatic_emulator_teardown_verified'],
                          'all_three_original_runtime_cases_passed': result['all_three_original_runtime_cases_passed'],
                          'native_exit_code': native_code, 'workflow_exit_code': workflow_code,
                          'shutdown_disposition': shutdown.get('shutdown_disposition', 'UNAVAILABLE'),
                          'parent_status': 'IN_PROGRESS'}))
        # Independent app results cannot erase a nonzero SDK/workflow outcome.
        # Missing/failed cleanup cannot permit an oracle or successful CLI outcome.
        return 0 if result['all_three_original_runtime_cases_passed'] and sdk_ok and cleanup_ok else 1
    except Exception:
        print(json.dumps({'status': 'BLOCKED', 'failure': 'CALLER_INPUT_OR_EXECUTION_BLOCKED',
                          'parent_status': 'IN_PROGRESS'}))
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
