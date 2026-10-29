"""Pinned Coordinator JSON-lines transport; no SDK calls on import or direct fallback.

Opaque ticket/client/native identities and exact stream bytes exist in RAM only.
An uncertain RPC poisons this retained channel: it is never replayed or ended.
The live keeper is deliberately retained for original-chat recovery in that case.
"""
import base64
import binascii
import hashlib
import json
import math
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import threading
import time

HELPER = 'C:/Users/darka/.claude/skills/android-emulator-gate/scripts/invoke_native_gate.py'
RELEASE = 'df52528334e1391b'
RELEASE_ROOT = 'C:/Users/darka/AppData/Local/Tools/llm-coordinator/releases/' + RELEASE
SCRATCH = 'D:/Temp/navic-task407-gated-runtime/r146'
MODULES = ('tools_cli.py', 'workflows.py', 'native_process.py', 'android_emulator.py',
           'emulator_adb.py', 'android_cli.py', 'android_cli_worker.py', 'emulator_state.py',
           'workflow_pipe.py', 'cua_mcp.py')
QUEUE_SOURCE = RELEASE_ROOT + '/engines/agent_toolbelt_gradle_build_gate/queue.py'
RUNTIME_PATHS = (HELPER, *(RELEASE_ROOT + '/src/llm_coordinator/' + name for name in MODULES),
                 RELEASE_ROOT + '/integration/android-cli.json',
                 RELEASE_ROOT + '/integration/emulator-components.json', QUEUE_SOURCE)
PROFILE = 'reader-diagnostics'
PROOF = 'GATE_NATIVE_TREE_LEASE_HOST_STORAGE_V1'


def blocked():
    return ValueError('GATE_TRANSPORT_UNCONFIRMED')


def launch_anchor(created_ticks, *, now_wall=None, now_mono=None):
    """Creation FILETIME excludes FIFO wait and includes already-elapsed VM work.

    This explicit prospective profile uses the acquired native creation timestamp,
    not receipt time. Wall/monotonic sampling uncertainty is fail-closed for future
    timestamps; the boot cap is never restarted after verification/readiness.
    """
    wall = time.time() if now_wall is None else now_wall
    mono = time.monotonic() if now_mono is None else now_mono
    if type(created_ticks) is not int or created_ticks <= 0:
        raise blocked()
    age = wall - (created_ticks - 116444736000000000) / 10_000_000
    if not math.isfinite(age) or age < 0:
        raise blocked()
    return mono - age


def validate_lease(proof, record, ticket):
    expected = record['gate']
    if (type(proof) is not dict or proof.get('kind') != 'coordinator-emulator-lease'
            or proof.get('ticket_id') != ticket or proof.get('emulator') != expected['binding']
            or proof.get('launch_profile') != PROFILE or proof.get('no_snapshot') is not True
            or type(proof.get('capacity')) is not int or proof['capacity'] != 2
            or type(proof.get('owner')) is not dict
            or proof['owner'].get('system') != expected['expected_owner']['system']
            or proof['owner'].get('thread_name') != expected['expected_owner']['thread_name']
            or type(proof['owner'].get('session_id')) is not str or not proof['owner']['session_id']
            or hashlib.sha256(proof['owner']['session_id'].encode('utf-8')).hexdigest()
               != expected['expected_owner']['session_sha256']
            or type(proof.get('native_processes')) is not list or not proof['native_processes']
            or any(type(pid) is not int or pid <= 0 for pid in proof['native_processes'])
            or len(set(proof['native_processes'])) != len(proof['native_processes'])):
        raise blocked()


_TRANSPORT_DIAGNOSTIC_ROLES = frozenset((
    'SDK_OUTPUT', 'EVENT_SOURCE', 'INSTRUMENTATION', 'OTHER_ADB', 'CONTROL', 'OTHER'))
_TRANSPORT_DIAGNOSTIC_PHASES = frozenset((
    'PRE_EXISTING_UNCERTAINTY', 'INVALID_EXHAUSTED_BUDGET', 'SERIAL_ACQUISITION',
    'PRE_WRITE_DEADLINE', 'WRITE', 'FLUSH', 'RESPONSE_WAIT', 'RESPONSE_IDENTITY',
    'OUTER_REPLY_VALIDATION', 'PRODUCER_REPLY_VALIDATION', 'PUMP_CALLBACK_FAILURE', 'OTHER'))
_TRANSPORT_RECEIVE_SIDES = frozenset((
    'EOF', 'FRAME_VIOLATION', 'JSON_OR_ENVELOPE', 'REPEATED_FRAME',
    'STDERR_SCHEMA_OR_LIMIT', 'STDERR_READ_FAILURE', 'OTHER'))
_TRANSPORT_SOURCE_BRANCHES = frozenset((
    'UNKNOWN_TERMINAL_REASON', 'SOURCE_PUMP_EXCEPTION'))
_TRANSPORT_DIAGNOSTIC_CAP = 2 ** 31 - 1


def _classify_first_outer_reply(value):
    """Return only the closed, privacy-safe structural reply projection."""
    missing = object()
    ok, result, error = (value.get(name, missing) for name in ('ok', 'result', 'error'))
    ok_class = ('MISSING' if ok is missing else 'TRUE' if ok is True else
                'FALSE' if ok is False else 'OTHER')
    result_class = 'MISSING' if result is missing else 'DICT' if type(result) is dict else 'OTHER'
    if error is missing:
        error_class = 'MISSING'
    elif type(error) is not str:
        error_class = 'NONSTRING'
    elif error.startswith('[WinError 5] '):
        error_class = 'REMOTE_WIN32_5'
    elif error.startswith('[WinError 32] '):
        error_class = 'REMOTE_WIN32_32'
    elif error.startswith('[Errno 13] '):
        error_class = 'REMOTE_ERRNO_13'
    elif error == 'Read cap must be 1 to 1048576 bytes per stream':
        error_class = 'REMOTE_READ_CAP_REJECTED'
    elif error == 'Native requests require a method and parameter object':
        error_class = 'REMOTE_REQUEST_SCHEMA_REJECTED'
    else:
        error_class = 'REMOTE_OTHER'
    return ok_class, result_class, error_class


def _transport_role(value, method):
    if value is not None:
        return value if type(value) is str and value in _TRANSPORT_DIAGNOSTIC_ROLES else 'OTHER'
    if type(method) is not str:
        return 'OTHER'
    # Exact internal wire operations only; ADB client roles require caller context.
    if method == 'emulator.output.read':
        return 'SDK_OUTPUT'
    if method in ('emulator.status', 'emulator.request-stop', 'emulator.wait-exit', 'workflow.end'):
        return 'CONTROL'
    return 'OTHER'


def _diagnostic_call(target, name, *args):
    """Optional evidence must never suppress the original action or exception."""
    try:
        method = getattr(target, name, None)
        if callable(method):
            return method(*args)
    except BaseException:
        pass


class JsonLineTransport:
    """One original helper process, one serialized RPC, bounded RAM, no replay.

    Only nonblocking installed RPCs run during work. wait-exit is used once during
    teardown; an uncertain wait retains the original keeper and prevents end.
    """
    def __init__(self, process):
        self.process = process
        self._condition = threading.Condition()
        self._serial = threading.Lock()
        self._frames = []
        self._failed = False
        self._eof = False
        self._counter = 0
        self.uncertain = False
        self._diagnostic_request_sequence = 0
        self._diagnostic_failure_sequence = 0
        self._diagnostic_sequence_saturated = False
        self._diagnostic_first = None
        self._diagnostic_first_outer = ('UNAVAILABLE', 'UNAVAILABLE', 'UNAVAILABLE')
        self._diagnostic_source = None
        self._diagnostic_event_first = None
        self._diagnostic_receive_side = 'UNAVAILABLE'
        self._diagnostic_rejection_receive_side = 'UNAVAILABLE'
        self._readers = [threading.Thread(target=self._read, daemon=True),
                         threading.Thread(target=self._drain_errors, daemon=True)]
        for reader in self._readers:
            reader.start()

    def _next_diagnostic_sequence(self):
        if self._diagnostic_request_sequence >= _TRANSPORT_DIAGNOSTIC_CAP:
            self._diagnostic_sequence_saturated = True
            return _TRANSPORT_DIAGNOSTIC_CAP
        self._diagnostic_request_sequence += 1
        return self._diagnostic_request_sequence

    def _record_diagnostic_failure(self, role, phase, request_sequence=None, outer=None):
        if self._diagnostic_failure_sequence >= _TRANSPORT_DIAGNOSTIC_CAP:
            self._diagnostic_sequence_saturated = True
            observation_sequence = _TRANSPORT_DIAGNOSTIC_CAP
        else:
            self._diagnostic_failure_sequence += 1
            observation_sequence = self._diagnostic_failure_sequence
        if self._diagnostic_first is None:
            self._diagnostic_first = (role, phase, request_sequence, observation_sequence,
                self._diagnostic_rejection_receive_side if phase == 'RESPONSE_WAIT' else 'UNAVAILABLE')
            if phase == 'OUTER_REPLY_VALIDATION' and type(outer) is tuple and len(outer) == 3:
                self._diagnostic_first_outer = outer
        if role == 'EVENT_SOURCE' and self._diagnostic_event_first is None:
            # Earliest event-ROLE failure, distinct from a later source-branch notification.
            self._diagnostic_event_first = (phase, request_sequence, observation_sequence)
        return observation_sequence

    def record_local_failure(self, role, phase):
        if (type(role) is not str or role not in _TRANSPORT_DIAGNOSTIC_ROLES
                or type(phase) is not str or phase not in _TRANSPORT_DIAGNOSTIC_PHASES):
            return
        with self._condition:
            # Local validation/pump failures cannot safely borrow the last wire admission.
            self._record_diagnostic_failure(role, phase)

    def record_source_failure(self, branch, phase, request_sequence=None):
        if (type(branch) is not str or branch not in _TRANSPORT_SOURCE_BRANCHES
                or type(phase) is not str or phase not in _TRANSPORT_DIAGNOSTIC_PHASES
                or request_sequence is not None and (type(request_sequence) is not int
                    or not 0 < request_sequence <= _TRANSPORT_DIAGNOSTIC_CAP)):
            return
        with self._condition:
            # No shared latest-event request: overlapping clients make that attribution false.
            observation_sequence = self._record_diagnostic_failure('EVENT_SOURCE', phase, request_sequence)
            if self._diagnostic_source is None:
                self._diagnostic_source = (branch, request_sequence, observation_sequence)

    def diagnostic_snapshot(self):
        with self._condition:
            first = self._diagnostic_first
            outer = self._diagnostic_first_outer
            source = self._diagnostic_source
            event = self._diagnostic_event_first
            return dict(sample_state='SAMPLED',
                first_role=first[0] if first is not None else 'UNAVAILABLE',
                first_phase=first[1] if first is not None else 'UNAVAILABLE',
                request_admission_sequence=first[2] if first is not None else None,
                failure_observation_sequence=first[3] if first is not None else None,
                sequence_saturated=bool(self._diagnostic_sequence_saturated),
                first_outer_ok=outer[0], first_outer_result=outer[1], first_outer_error=outer[2],
                source_branch=source[0] if source is not None else 'UNAVAILABLE',
                source_failure='CHILD_UNCONFIRMED' if source is not None else 'UNAVAILABLE',
                source_request_admission_sequence=source[1] if source is not None else None,
                source_failure_observation_sequence=source[2] if source is not None else None,
                receive_side=first[4] if first is not None else 'UNAVAILABLE',
                event_source_first_phase=event[0] if event is not None else 'UNAVAILABLE',
                event_source_request_admission_sequence=event[1] if event is not None else None,
                event_source_failure_observation_sequence=event[2] if event is not None else None)

    def _read(self):
        receive_side = 'EOF'
        try:
            while True:
                raw = self.process.stdout.readline(1024 * 1024 + 1)
                if not raw:
                    break
                if len(raw) > 1024 * 1024 or not raw.endswith(b'\n'):
                    receive_side = 'FRAME_VIOLATION'
                    raise blocked()
                try:
                    def unique(pairs):
                        result = {}
                        for key, value in pairs:
                            if key in result:
                                raise blocked()
                            result[key] = value
                        return result
                    value = json.loads(raw, object_pairs_hook=unique)
                    if type(value) is not dict:
                        raise blocked()
                except BaseException:
                    receive_side = 'JSON_OR_ENVELOPE'
                    raise
                with self._condition:
                    if self._frames:
                        receive_side = 'REPEATED_FRAME'
                        raise blocked()
                    self._frames.append(value)
                    self._condition.notify_all()
        except BaseException:
            with self._condition:
                self._failed = True
                if receive_side == 'EOF':
                    receive_side = 'OTHER'
                self._diagnostic_receive_side = receive_side if receive_side in _TRANSPORT_RECEIVE_SIDES else 'OTHER'
        finally:
            with self._condition:
                self._eof = True
                if self._diagnostic_receive_side == 'UNAVAILABLE':
                    self._diagnostic_receive_side = 'EOF'
                self._condition.notify_all()
            self.process.stdout.close()

    @staticmethod
    def _queued_notice(raw):
        def unique(pairs):
            result = {}
            for key, value in pairs:
                if key in result:
                    raise blocked()
                result[key] = value
            return result
        try:
            value = json.loads(raw, object_pairs_hook=unique)
            return (type(value) is dict and set(value) == {'state', 'ticket', 'ticket_id'}
                    and value['state'] == 'queued' and type(value['ticket']) is int
                    and value['ticket'] > 0 and type(value['ticket_id']) is str
                    and re.fullmatch('[0-9a-f]{32}', value['ticket_id']) is not None)
        except BaseException:
            return False

    def _drain_errors(self):
        receive_side = 'STDERR_SCHEMA_OR_LIMIT'
        try:
            # Only the installed FIFO queue's exact diagnostic schema is nonfatal.
            # All bytes remain bounded and in RAM; queued never becomes authority.
            partial = bytearray()
            while True:
                try:
                    fragment = self.process.stderr.read(4096)
                except BaseException:
                    receive_side = 'STDERR_READ_FAILURE'
                    raise
                if not fragment:
                    if partial:
                        raise blocked()
                    break
                if type(fragment) is not bytes:
                    raise blocked()
                partial.extend(fragment)
                while b'\n' in partial:
                    end = partial.index(b'\n')
                    if end > 4096 or not self._queued_notice(bytes(partial[:end])):
                        raise blocked()
                    del partial[:end + 1]
                if len(partial) > 4096:
                    raise blocked()
        except BaseException:
            with self._condition:
                self._failed = True
                self._diagnostic_receive_side = receive_side
                self._condition.notify_all()
        finally:
            self.process.stderr.close()

    def _next(self, deadline):
        with self._condition:
            while not self._frames and not self._failed and not self._eof:
                remaining = None if deadline is None else deadline - time.monotonic()
                if remaining is not None and remaining <= 0:
                    self._diagnostic_rejection_receive_side = 'UNAVAILABLE'
                    raise blocked()
                self._condition.wait(remaining)
            if self._failed or not self._frames:
                self._diagnostic_rejection_receive_side = self._diagnostic_receive_side
                raise blocked()
            return self._frames.pop(0)

    def acquire(self):
        # The original queued operation stays alive; no queue-age execution cap.
        try:
            value = self._next(None)
            if value.get('state') != 'acquired':
                raise blocked()
            return value
        except BaseException:
            self.uncertain = True
            raise blocked() from None

    def request(self, method, params=None, timeout=15, diagnostic_role=None):
        role = _transport_role(diagnostic_role, method)
        deadline = time.monotonic() + timeout
        request_sequence = None
        outer = None
        # Split only the original short-circuit guards, preserving reads/check order.
        phase = 'OTHER'
        if self.uncertain:
            phase = 'PRE_EXISTING_UNCERTAINTY'
        elif timeout <= 0:
            phase = 'INVALID_EXHAUSTED_BUDGET'
        elif not self._serial.acquire(timeout=timeout):
            phase = 'SERIAL_ACQUISITION'
        if phase != 'OTHER':
            with self._condition:
                self._record_diagnostic_failure(role, phase)
            self.uncertain = True
            raise blocked()
        try:
            if self.uncertain:
                phase = 'PRE_EXISTING_UNCERTAINTY'
                raise blocked()
            if time.monotonic() >= deadline:
                phase = 'PRE_WRITE_DEADLINE'
                raise blocked()
            with self._condition:
                request_sequence = self._next_diagnostic_sequence()
                self._diagnostic_rejection_receive_side = 'UNAVAILABLE'
            self._counter += 1
            identity = str(self._counter)
            payload = dict(id=identity, method=method, params=params or {})
            phase = 'WRITE'
            self.process.stdin.write((json.dumps(payload) + '\n').encode('utf-8'))
            phase = 'FLUSH'
            self.process.stdin.flush()
            phase = 'RESPONSE_WAIT'
            value = self._next(deadline)
            phase = 'RESPONSE_IDENTITY'
            if value.get('id') != identity:
                raise blocked()
            if method == 'workflow.end':
                return value  # Terminal frame is not the normal result wrapper.
            phase = 'OUTER_REPLY_VALIDATION'
            if value.get('ok') is not True or type(value.get('result')) is not dict:
                try:
                    outer = _classify_first_outer_reply(value)
                except BaseException:
                    pass
                raise blocked()
            return value['result']
        except BaseException:
            with self._condition:
                self._record_diagnostic_failure(role, phase, request_sequence, outer)
            self.uncertain = True
            raise blocked() from None
        finally:
            self._serial.release()

    def settle_wrapper(self, timeout):
        deadline = time.monotonic() + timeout
        try:
            self.process.wait(timeout=max(0, deadline - time.monotonic()))
            self.process.stdin.close()
            for reader in self._readers:
                reader.join(max(0, deadline - time.monotonic()))
            return not self._failed and not any(reader.is_alive() for reader in self._readers)
        except BaseException:
            return False


def inspect_snapshot_storage(directory, timeout):
    """Postexit only: exact owned AVD paths, no deletion/reparse traversal.

    Empty/missing snapshots directory and absence of every root RAM-image member
    are required. Any inaccessible/reparse ancestor, entry, or unknown state fails.
    A nonempty snapshots directory (including metadata) is deliberately rejected.
    """
    deadline = time.monotonic() + timeout
    try:
        root = Path(directory)
        if not root.is_absolute() or timeout <= 0:
            return False
        for ancestor in (*reversed(root.parents), root):
            info = os.lstat(ancestor)
            if (getattr(info, 'st_file_attributes', 0) & 0x400
                    or stat.S_ISLNK(info.st_mode) or not stat.S_ISDIR(info.st_mode)):
                return False
        with os.scandir(root) as entries:
            for entry in entries:
                if time.monotonic() >= deadline:
                    return False
                name = entry.name.casefold()
                if name == 'snapshots':
                    info = entry.stat(follow_symlinks=False)
                    if (getattr(info, 'st_file_attributes', 0) & 0x400
                            or not stat.S_ISDIR(info.st_mode) or entry.is_symlink()):
                        return False
                    with os.scandir(root / entry.name) as snapshots:
                        if next(snapshots, None) is not None:
                            return False
                # RAM images have appeared under both root and snapshots. Any
                # matching member, even zero length, is residue, not absence.
                elif (name.startswith(('snapshot.', 'snapshots.', 'snapstorage',
                                       'ram.', 'ram_', 'quickboot.ram'))
                      or name == 'ram' or name.endswith('.ram') or '.ram.' in name):
                    return False
        return time.monotonic() < deadline
    except BaseException:
        return False


class _CapturedClient:
    """Retained exact helper process/readers, bounded RAM, no settlement by kill.

    Verification has its original deadline and a separate existing cancellation
    allowance at closure. Cleanup management waits for the installed operation's
    genuine terminal state: no expiry, watchdog, automatic retry or deletion timer.
    """
    def __init__(self, process, owners, *, cap):
        self.process = process
        self.outputs = [bytearray(), bytearray()]
        self.failed = threading.Event()
        self.settled = False
        self._cap = cap
        self._kill_requested = False
        self._kill_lock = threading.Lock()
        self._readers = []
        self._registered = threading.Event()
        self._registration_complete = False
        owners.append(self)  # Retain before native creation or reader startup.
        if process is not None:
            self.start_readers()

    def start_readers(self):
        try:
            for index, pipe in enumerate((self.process.stdout, self.process.stderr)):
                reader = threading.Thread(target=self._read, args=(index, pipe), daemon=True)
                self._readers.append(reader)  # Retain BEFORE start, never after it.
                reader.start()
            self._registration_complete = True
        finally:
            self._registered.set()

    def _read(self, index, pipe):
        try:
            while chunk := pipe.read(4096):
                if len(self.outputs[index]) + len(chunk) > self._cap:
                    self.failed.set()
                    # Drain/discard overflow; never block output or mistake a
                    # cancelled/overflowing helper for a settled helper.
                elif not self.failed.is_set():
                    self.outputs[index].extend(chunk)
        except BaseException:
            self.failed.set()
        finally:
            try:
                pipe.close()
            except BaseException:
                self.failed.set()

    def settle(self, timeout=None):
        if self.settled:
            return True
        deadline = None if timeout is None else time.monotonic() + max(0, timeout)
        def remaining():
            return None if deadline is None else max(0, deadline - time.monotonic())
        try:
            if (not self._registered.wait(remaining()) or not self._registration_complete
                    or len(self._readers) != 2):
                return False
            if type(self.process.wait(timeout=remaining())) is not int:
                return False
            for reader in self._readers:
                reader.join(remaining())
            self.settled = (type(self.process.returncode) is int
                            and not any(reader.is_alive() for reader in self._readers))
        except BaseException:
            return False
        return self.settled

    def request_kill(self):
        with self._kill_lock:
            if self.process is not None and not self._kill_requested and not self.settled:
                self._kill_requested = True
                try:
                    self.process.kill()  # Exact read-only verification client only.
                except BaseException:
                    pass  # A failed request still requires actual wait/join proof.

    def cancel(self, timeout):
        deadline = time.monotonic() + timeout
        self.request_kill()
        return self.settle(max(0, deadline - time.monotonic()))


class GateSession:
    def __init__(self, record, *, transport, acquired, verify, wrapper_settle,
                 storage_check=inspect_snapshot_storage, launch_started=None):
        self.record, self.transport = record, transport
        self.verify, self.wrapper_settle, self.storage_check = verify, wrapper_settle, storage_check
        self.clients = []
        self.uncertain = False
        self._finished = None
        self._finish_lock = threading.Lock()
        self._sdk_lock = threading.Lock()
        self._sdk_observer = None
        self.diagnostic_output_errors = 0
        self.verification_clients = []
        self._verification_condition = threading.Condition()
        self._verification_active = 0
        self._verification_closing = False
        self._verification_settle_deadline = None
        self._sdk_total = 0
        self._sdk_received = [0, 0]
        self._sdk_stop = threading.Event()
        self._sdk_worker = None
        self._sdk_gap = False
        self._sdk_buffers = [bytearray(), bytearray()]
        self.native_exited = threading.Event()
        if (type(acquired) is not dict or acquired.get('state') != 'acquired'
                or acquired.get('tool') != 'android-emulator'
                or type(acquired.get('ticket_id')) is not str or not acquired['ticket_id']
                or type(acquired.get('engine')) is not dict
                or any(type(acquired['engine'].get(k)) is not int or acquired['engine'][k] <= 0
                       for k in ('pid', 'created_ticks'))):
            self.uncertain = True
            raise blocked()
        self.ticket = acquired['ticket_id']
        self.engine = acquired['engine']
        self.launch_started = (launch_anchor(self.engine['created_ticks'])
                               if launch_started is None else launch_started)
        self.returncode = None

    @classmethod
    def launch(cls, record):
        gate = record['gate']
        args = [sys.executable, '-B', HELPER, 'run', '--tool', 'android-emulator',
            '--avd', record['avd'], '--serial', record['serial'], '--scratch-root', SCRATCH,
            '--emulator-profile', PROFILE, '--owner-task', gate['owner_task'],
            '--thread-name', gate['thread_name'], '--sdk-root', gate['binding']['sdk_root'],
            '--avd-home', gate['binding']['avd_home']]
        environment = dict(os.environ, PYTHONDONTWRITEBYTECODE='1', TEMP=SCRATCH, TMP=SCRATCH, TMPDIR=SCRATCH)
        process = subprocess.Popen(args, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, env=environment, bufsize=0)
        transport = JsonLineTransport(process)
        acquired = transport.acquire()

        def verify(timeout=15):
            deadline = time.monotonic() + timeout
            # Supported separate original-chat invocation, not metadata impersonation.
            command = [sys.executable, '-B', HELPER, 'verify-lease', '--tool', 'android-emulator',
                '--avd', record['avd'], '--serial', record['serial'], '--resume-ticket', acquired['ticket_id'],
                '--emulator-profile', PROFILE, '--thread-name', gate['thread_name'],
                '--sdk-root', gate['binding']['sdk_root'], '--avd-home', gate['binding']['avd_home']]
            # No explicit emulator-root: new run verifies the installed pinned
            # profile component; verify-lease resumes its actual original binding.
            # Bounded RAM capture, never subprocess.run's unbounded capture_output.
            # Admit before uncertain native creation, but never hold this short
            # publication lock across Popen/reader startup or process settlement.
            with session._verification_condition:
                if session._verification_closing or time.monotonic() >= deadline:
                    raise blocked()
                session._verification_active += 1
            client = None
            try:
                # Prepare/retain the capture owner BEFORE Popen. Constructor
                # interruption cannot orphan a process that already exists.
                client = _CapturedClient(None, session.verification_clients, cap=65536)
                client.process = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE, env=environment, bufsize=0)
                client.start_readers()
                with session._verification_condition:
                    if session._verification_closing:
                        raise blocked()
                if (not client.settle(max(0, deadline - time.monotonic())) or client.process.returncode != 0
                        or client.outputs[1] or client.failed.is_set()):
                    raise blocked()
                return json.loads(client.outputs[0])
            except BaseException:
                # Constructor failures still retain the actual process and every
                # constructed reader. A partially registered client is not settled.
                if client is not None:
                    client.request_kill()
                raise blocked() from None
            finally:
                with session._verification_condition:
                    closing = session._verification_closing
                    settle_deadline = session._verification_settle_deadline
                if closing and client is not None:
                    client.cancel(max(0, settle_deadline - time.monotonic()))
                with session._verification_condition:
                    session._verification_active -= 1
                    session._verification_condition.notify_all()
        session = cls(record, transport=transport, acquired=acquired,
                      verify=verify, wrapper_settle=transport.settle_wrapper)
        return session

    def request(self, method, params=None, timeout=15, diagnostic_role=None):
        role = _transport_role(diagnostic_role, method)
        if self.uncertain:
            _diagnostic_call(self.transport, 'record_local_failure', role, 'PRE_EXISTING_UNCERTAINTY')
            raise blocked()
        if (type(timeout) not in (int, float) or not math.isfinite(timeout) or timeout <= 0):
            _diagnostic_call(self.transport, 'record_local_failure', role, 'INVALID_EXHAUSTED_BUDGET')
            raise blocked()
        try:
            # Preserve optional-role compatibility with original injected transports.
            try:
                supports_role = callable(getattr(self.transport, 'diagnostic_snapshot', None))
            except BaseException:
                supports_role = False
            if supports_role:
                return self.transport.request(method, params or {}, timeout, diagnostic_role=role)
            return self.transport.request(method, params or {}, timeout)
        except BaseException:
            self.uncertain = True
            raise blocked() from None

    def record_local_failure(self, role, phase):
        _diagnostic_call(self.transport, 'record_local_failure', role, phase)

    def record_source_failure(self, branch, phase, request_sequence=None):
        _diagnostic_call(self.transport, 'record_source_failure', branch, phase, request_sequence)

    def transport_diagnostic_snapshot(self):
        snapshot = getattr(self.transport, 'diagnostic_snapshot', None)
        if not callable(snapshot):
            raise blocked()
        return snapshot()

    def verify_live(self, timeout):
        # Helper enforces current original-chat identity. Its finite read-only
        # verification is inside the launch-based boot budget, never a new phase.
        if timeout <= 0:
            raise blocked()
        deadline = time.monotonic() + timeout
        proof = self.verify(timeout)
        validate_lease(proof, self.record, self.ticket)
        state = self.request('emulator.status', timeout=max(0, deadline - time.monotonic()))
        self.validate_status(state)
        if not set(proof['native_processes']) & set(state['native_processes']):
            raise blocked()

    def validate_status(self, state):
        if (type(state) is not dict or state.get('emulator') != self.record['gate']['binding']
                or state.get('no_snapshot') is not True or state.get('launch_profile') != PROFILE
                or type(state.get('native_processes')) is not list
                or any(type(pid) is not int or pid <= 0 for pid in state['native_processes'])
                or type(state.get('console_owned')) is not bool):
            _diagnostic_call(self, 'record_local_failure', 'CONTROL', 'PRODUCER_REPLY_VALIDATION')
            raise blocked()

    def console_ready(self, timeout):
        state = self.request('emulator.status', timeout=timeout)
        self.validate_status(state)
        if not state['native_processes']:
            raise blocked()
        return state['console_owned']

    def start_sdk_observation(self, command_cap):
        """Drain SDK output concurrently with wait-for-device, never reader progress."""
        if self._sdk_worker is not None:
            raise blocked()
        def observe():
            try:
                while not self._sdk_stop.is_set():
                    self.drain_sdk(command_cap)
                    self._sdk_stop.wait(.01)
            except BaseException:
                self._sdk_gap = True
        self._sdk_worker = threading.Thread(target=observe, daemon=True)
        self._sdk_worker.start()

    def drain_sdk(self, timeout=15):
        deadline = time.monotonic() + timeout
        if timeout <= 0 or not self._sdk_lock.acquire(timeout=timeout):
            self._sdk_gap = True
            raise blocked()
        try:
            phase = 'OTHER'
            try:
                value = self.request('emulator.output.read', dict(max_bytes=65536),
                                     max(0, deadline - time.monotonic()), diagnostic_role='SDK_OUTPUT')
                phase = 'PRODUCER_REPLY_VALIDATION'
                for index, name in enumerate(('stdout', 'stderr')):
                    data, remaining = decode_stream(value[name], 65536)
                    self._sdk_received[index] += len(data)
                    if value[name]['bytes'] != self._sdk_received[index] + remaining:
                        raise blocked()
                    self._sdk_total += len(data)
                    if self._sdk_total > 4 * 1024 * 1024:
                        raise blocked()
                    self._sdk_buffers[index].extend(data)
                    if self._sdk_observer is not None:
                        try:
                            self._sdk_observer(index, data)
                        except BaseException:
                            # Non-authoritative diagnostics cannot become a wire gap
                            # or interrupt the other stream's cumulative accounting.
                            self.diagnostic_output_errors = min(65535, self.diagnostic_output_errors + 1)
                            self._sdk_observer = None
                return any(value[name]['buffered_bytes'] for name in ('stdout', 'stderr'))
            except BaseException:
                if phase == 'PRODUCER_REPLY_VALIDATION':
                    _diagnostic_call(self, 'record_local_failure', 'SDK_OUTPUT', phase)
                self._sdk_gap = True
                raise blocked() from None
        finally:
            self._sdk_lock.release()

    def finish(self, caps):
        with self._finish_lock:
            if self._finished is not None:
                return self._finished
            result = dict(native_tree_exit_verified=False, lease_released=False, snapshots_empty=False,
                native_exit_code=None, workflow_exit_code=None, shutdown_disposition='UNAVAILABLE',
                command_failures=None, sdk_output_complete=False, wrapper_settled=False,
                verification_clients_settled=False, cleanup_failures=[])
            self._finished = result  # No replay even if a bounded caller expires.
            try:
                # A separate read-only verification helper cannot poison the keeper
                # channel or prevent independent safe owned native stop/release.
                with self._verification_condition:
                    self._verification_closing = True
                    verification_deadline = time.monotonic() + caps.settle
                    self._verification_settle_deadline = verification_deadline
                for client in self.verification_clients:
                    client.request_kill()
                with self._verification_condition:
                    while self._verification_active:
                        remaining = verification_deadline - time.monotonic()
                        if remaining <= 0:
                            break
                        self._verification_condition.wait(remaining)
                    publication_complete = self._verification_active == 0
                for client in self.verification_clients:
                    if not client.settled:
                        client.cancel(max(0, verification_deadline - time.monotonic()))
                result['verification_clients_settled'] = (publication_complete
                    and all(client.settled for client in self.verification_clients))
                if not result['verification_clients_settled']:
                    result['cleanup_failures'].append('VERIFICATION_CLIENT_UNSETTLED')
                self._sdk_stop.set()
                if self._sdk_worker is not None:
                    self._sdk_worker.join(caps.settle)
                    if self._sdk_worker.is_alive():
                        self.uncertain = True
                        raise blocked()
                for client in self.clients:
                    if not client.done.is_set() and not client.cancel(caps.settle):
                        raise blocked()
                    if not client.settle(caps.settle):
                        raise blocked()
                if self.uncertain:
                    raise blocked()
                stopped = self.request('emulator.request-stop', timeout=caps.stop)
                if stopped.get('state') not in ('stop_requested', 'native_exited') or stopped.get('not_dispatched'):
                    raise blocked()
                exited = self.request('emulator.wait-exit', timeout=caps.process_wait)
                if (exited.get('state') != 'native_exited' or exited.get('exit_verified') is not True
                        or type(exited.get('exit_code')) is not int
                        or exited.get('lease_retained_for_inspection') is not True
                        or exited.get('sdk_output_available') is not True):
                    raise blocked()
                result.update(native_tree_exit_verified=True, native_exit_code=exited['exit_code'])
                self.returncode = exited['exit_code']
                self.native_exited.set()
                deadline = time.monotonic() + caps.command
                try:
                    while self.drain_sdk(max(0, deadline - time.monotonic())):
                        if time.monotonic() >= deadline:
                            raise blocked()
                except BaseException:
                    self._sdk_gap = True
                    result['cleanup_failures'].append('SDK_OUTPUT_UNCONFIRMED')
                result['sdk_output_complete'] = not self._sdk_gap
                # A capture gap disqualifies evidence, but after verified native
                # exit cannot skip safe release. An uncertain RPC still forbids end.
                terminal = self.request('workflow.end', timeout=max(0, deadline - time.monotonic()))
                if (terminal.get('state') != 'released' or terminal.get('ticket_id') != self.ticket
                        or terminal.get('native_tree_exit_verified') is not True
                        or terminal.get('app_completion') != 'not_inferred'
                        or terminal.get('native_exit_code') != self.returncode
                        or type(terminal.get('workflow_exit_code')) is not int
                        or type(terminal.get('command_failures')) is not bool
                        or type(terminal.get('ok')) is not bool
                        or terminal.get('ok') != (terminal['workflow_exit_code'] == 0)
                        or terminal.get('shutdown_disposition') != (
                            'native_exit_success' if self.returncode == 0 else 'native_exit_failure')):
                    raise blocked()
                result.update(lease_released=True, workflow_exit_code=terminal['workflow_exit_code'],
                    shutdown_disposition=terminal['shutdown_disposition'], command_failures=terminal['command_failures'])
                result['wrapper_settled'] = self.wrapper_settle(caps.settle) is True
                if not result['wrapper_settled']:
                    result['cleanup_failures'].append('GATE_WRAPPER_UNSETTLED')
                process = getattr(self.transport, 'process', None)
                if process is not None and ((process.returncode & 0xffffffff) !=
                                             (terminal['workflow_exit_code'] & 0xffffffff)):
                    result['cleanup_failures'].append('GATE_WRAPPER_EXIT_MISMATCH')
                result['snapshots_empty'] = self.storage_check(
                    self.record['gate']['binding']['avd_directory'], caps.snapshot_budget) is True
                if not result['snapshots_empty']:
                    result['cleanup_failures'].append('SNAPSHOTS_UNCONFIRMED')
            except BaseException:
                result['cleanup_failures'].append('GATE_CLOSURE_UNCONFIRMED')
            return result


def decode_stream(row, cap):
    if (type(row) is not dict or row.get('storage') != 'bounded_memory'
            or type(row.get('base64')) is not str or len(row['base64']) > ((cap + 2) // 3) * 4
            or any(type(row.get(key)) is not int or row[key] < 0
                   for key in ('returned_bytes', 'bytes', 'buffered_bytes', 'dropped_bytes'))
            or type(row.get('eof')) is not bool or type(row.get('reader_complete')) is not bool
            or type(row.get('read_error')) is not bool or row['read_error']
            or row['dropped_bytes'] != 0 or row.get('truncated') is not False):
        raise blocked()
    try:
        data = base64.b64decode(row['base64'], validate=True)
    except (ValueError, binascii.Error):
        raise blocked() from None
    if len(data) != row['returned_bytes'] or len(data) > cap or row['bytes'] < len(data) + row['buffered_bytes']:
        raise blocked()
    return data, row['buffered_bytes']


CLEANUP_HELPER = 'C:/Users/darka/.claude/skills/transactional-cleanup/scripts/invoke_transactional_cleanup.py'
CLEANUP_ACTIVE = 'C:/Users/darka/AppData/Local/Tools/transactional-cleanup/active.json'
CLEANUP_SOURCE = ('C:/Users/darka/AppData/Local/Tools/transactional-cleanup/releases/'
                  'add2469e175569e6e96533b8e9f961fb/src')
_CLEANUP_CLIENTS = []  # Interrupted management clients remain retained, never replayed.


def cleanup_installation_valid():
    """Pin actual installed wrapper, routing and every activated module, not a receipt."""
    try:
        active = Path(CLEANUP_ACTIVE).read_bytes()
        if (hashlib.sha256(active).hexdigest() !=
                'cba2ed4931bd7f0c79eb68462a1da21f4346978592991ef1a7cc2e62f77f0959'
                or hashlib.sha256(Path(CLEANUP_HELPER).read_bytes()).hexdigest() !=
                '66f8114c197870b0d1f713a9cad1e74391e575d7e8bea15542412d21b35f3f52'):
            return False
        metadata = json.loads(active)
        return (Path(metadata['source']) == Path(CLEANUP_SOURCE)
                and all(hashlib.sha256((Path(CLEANUP_SOURCE) / path).read_bytes()).hexdigest() == digest
                        for path, digest in metadata['hashes'].items()))
    except BaseException:
        return False


def _cleanup_invoke(arguments):
    # After all VM clients/wrapper/lease consumers exit. The pinned helper uses
    # only its durable installed state, not tempfile. A non-created descendant
    # routes every child temp variable without protecting the deletion ROOT as
    # the helper's TEMP scan_root. If a temp member appears, exact absence fails.
    temporary = str(Path(SCRATCH) / 'cleanup-client-temp')
    local = 'C:/Users/darka/AppData/Local'
    # The installed wrapper routes through LOCALAPPDATA and can select an older
    # runtime for a legacy JSON ID. Bind the actual active/state location and
    # reject that unqualified legacy route before executing ANY helper command.
    for flag in ('--transaction', '--ticket'):
        if flag in arguments:
            identity = arguments[arguments.index(flag) + 1]
            if (not re.fullmatch('[0-9a-f]{32}|[0-9a-f]{64}', identity)
                    or os.path.lexists(Path(local) / 'Tools/transactional-cleanup/state' / (identity + '.json'))):
                raise blocked()
    environment = dict(os.environ, PYTHONDONTWRITEBYTECODE='1', GIT_OPTIONAL_LOCKS='0',
                       LOCALAPPDATA=local, TEMP=temporary, TMP=temporary, TMPDIR=temporary)
    environment.pop('AGENT_TOOLBELT_HOME', None)
    process = subprocess.Popen([sys.executable, '-B', CLEANUP_HELPER, *arguments],
        stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        env=environment, bufsize=0)
    client = _CapturedClient(process, _CLEANUP_CLIENTS, cap=4 * 1024 * 1024)
    try:
        # Installed cleanup operations have no cancellation timeout. This is
        # management AFTER original VM supervision, not an extension of its caps.
        if (not client.settle() or process.returncode != 0
                or client.outputs[1] or client.failed.is_set()):
            raise blocked()
        def unique(pairs):
            result = {}
            for key, value in pairs:
                if key in result:
                    raise blocked()
                result[key] = value
            return result
        value = json.loads(client.outputs[0], object_pairs_hook=unique)
        if type(value) is not dict:
            raise blocked()
        return value
    finally:
        if client.settled:
            client.outputs = [bytearray(), bytearray()]
            _CLEANUP_CLIENTS.remove(client)


def exact_scratch_absent(path):
    """Independent exact-root absence, rejecting inaccessible/reparse ancestry."""
    try:
        root = Path(path)
        if root != Path(SCRATCH):
            return False
        for ancestor in reversed(root.parents):
            info = os.lstat(ancestor)
            if (getattr(info, 'st_file_attributes', 0) & 0x400
                    or not stat.S_ISDIR(info.st_mode) or stat.S_ISLNK(info.st_mode)):
                return False
        try:
            os.lstat(root)
        except FileNotFoundError:
            return True
        return False
    except BaseException:
        return False


def _cleanup_bound(value, operation, transaction):
    coverage = value.get('discovery_coverage', {}) if type(value) is dict else {}
    return (type(value) is dict and value.get('ok') is True
        and value.get('operation') == operation and value.get('transaction_id') == transaction
        and value.get('failure_kind') is None and value.get('errors') == [] and value.get('warnings') == []
        and type(value.get('workspace')) is str
        and Path(value['workspace']) == Path(__file__).resolve().parents[1]
        and type(coverage) is dict and coverage.get('explicit_registration') is True
        and coverage.get('complete_host_coverage') is False
        and coverage.get('workspace') is False
        and type(coverage.get('known_or_explicit_roots')) is list
        and [Path(path) for path in coverage['known_or_explicit_roots']] == [Path(SCRATCH)]
        and all(type(value.get(key)) is int and value[key] >= 0
                for key in ('candidate_count', 'candidate_bytes', 'excluded_count', 'deleted_bytes')))


def _cleanup_open(record, invoke, installation_check):
    transaction = record.get('cleanup_transaction')
    if (type(transaction) is not str or not re.fullmatch('[0-9a-f]{32}', transaction)
            or installation_check() is not True):
        raise blocked()
    status = invoke(['status', '--transaction', transaction])
    if (not _cleanup_bound(status, 'status', transaction) or status.get('state') != 'open'
            or status.get('ticket_id') is not None or status.get('ticket_state') is not None
            or status.get('detailed_state_retained') is not True
            or status.get('progress', {}).get('operation') is not None):
        raise blocked()
    return transaction


def prepare_runtime_cleanup(record):
    # Live admission checks MAIN's genuine current pre-registered transaction
    # before VM creation. No begin/register or synthetic cleanup boolean here.
    _cleanup_open(record, _cleanup_invoke, cleanup_installation_valid)
    return lambda: cleanup_runtime_scratch(record)


def _scratch_candidate(item):
    try:
        root, path = Path(SCRATCH), Path(item['path'])
        return (path.is_absolute() and (path == root or root in path.parents)
            and '..' not in path.parts and item.get('decision') == 'candidate'
            and item.get('reason') == 'attributed_generated_output'
            and item.get('kind') == 'temporary' and type(item.get('evidence')) is str and bool(item['evidence'])
            and item.get('discovery_source') == 'explicit_registration'
            and item.get('git_status') == 'not_tracked_or_outside_repository'
            and item.get('preexisting') is False and item.get('reparse') is False
            and item.get('filesystem') in ('NTFS', 'ReFS')
            and type(item.get('directory')) is bool
            and (item['directory'] or type(item.get('links')) is int and item['links'] == 1)
            and all(item.get(key) is False for key in (
                'allow_hardlinks', 'allow_leaf_reparse', 'allow_disposable_repository'))
            and type(item.get('identity')) is str and bool(item['identity'])
            and type(item.get('size')) is int and item['size'] >= 0)
    except BaseException:
        return False


def cleanup_runtime_scratch(record, *, invoke=_cleanup_invoke, absent=exact_scratch_absent,
                            installation_check=cleanup_installation_valid):
    """Consume genuine v2 review -> all pages -> separate ticket -> apply -> verify.

    IDs/manifest rows are RAM-only. Any exclusion is fully inspected but prevents
    ticketing this narrowly attributed transaction. No retries, fallback deletion,
    fabricated receipt, Gradle reservation or VM budget renewal is permitted.
    """
    try:
        transaction = _cleanup_open(record, invoke, installation_check)
        review = invoke(['review', '--transaction', transaction])
        if not _cleanup_bound(review, 'review', transaction) or review.get('state') != 'reviewed':
            return False
        digest = review.get('manifest_sha256')
        if type(digest) is not str or not re.fullmatch('[0-9a-f]{64}', digest):
            return False
        total = review['candidate_count'] + review['excluded_count']
        offset, candidates, excluded, size = 0, 0, 0, 0
        valid, seen = True, set()
        while True:
            page = invoke(['inspect', '--transaction', transaction, '--offset', str(offset), '--limit', '1000'])
            if (not _cleanup_bound(page, 'inspect', transaction) or page.get('state') != 'reviewed'
                    or type(page.get('total_items')) is not int or page['total_items'] != total
                    or type(page.get('offset')) is not int or page['offset'] != offset
                    or type(page.get('limit')) is not int or page['limit'] != 1000
                    or any(page[key] != review[key] for key in (
                        'candidate_count', 'candidate_bytes', 'excluded_count', 'deleted_bytes'))
                    or type(page.get('page_items')) is not list
                    or len(page['page_items']) != min(1000, total - offset)):
                return False
            for item in page['page_items']:
                if type(item) is not dict or type(item.get('path')) is not str:
                    valid = False
                    continue
                path = Path(item['path'])
                valid = valid and path not in seen
                seen.add(path)
                if item.get('decision') == 'excluded':
                    excluded += 1
                    valid = False  # Inspect every exclusion/page before rejecting.
                elif item.get('decision') == 'candidate':
                    candidates += 1
                    safe = _scratch_candidate(item)
                    valid = safe and valid
                    if safe and not item['directory']:
                        size += item['size']
                else:
                    valid = False
            offset += len(page['page_items'])
            if offset == total:
                if page.get('next_offset') is not None:
                    return False
                break
            if type(page.get('next_offset')) is not int or page['next_offset'] != offset:
                return False
        if (not valid or candidates != review['candidate_count'] or excluded != review['excluded_count']
                or size != review['candidate_bytes']):
            return False
        ticket = invoke(['ticket', '--transaction', transaction, '--manifest-sha256', digest])
        if (not _cleanup_bound(ticket, 'ticket', transaction) or ticket.get('state') != 'ticketed'
                or ticket.get('ticket_state') != 'issued' or ticket.get('manifest_sha256') != digest
                or type(ticket.get('ticket_id')) is not str or not re.fullmatch('[0-9a-f]{64}', ticket['ticket_id'])
                or ticket['candidate_count'] != candidates or ticket['excluded_count'] != 0):
            return False
        identity = ticket['ticket_id']
        applied = invoke(['apply', '--ticket', identity])  # Separate helper invocation after review.
        if (not _cleanup_bound(applied, 'apply', transaction) or applied.get('dry_run') is not False
                or applied.get('state') != 'applied' or applied.get('ticket_state') != 'applied'
                or applied.get('ticket_id') != identity):
            return False
        final = invoke(['status', '--transaction', transaction])
        counts = final.get('result_counts', {})
        if (not _cleanup_bound(final, 'status', transaction) or final.get('state') != 'applied'
                or final.get('ticket_state') != 'applied' or final.get('ticket_id') != identity
                or final.get('detailed_state_retained') is not False
                or final.get('progress', {}).get('operation') is not None
                or final['candidate_count'] != candidates or final['excluded_count'] != 0
                or type(counts) is not dict or set(counts) - {'deleted', 'already_missing'}
                or any(type(n) is not int or n < 0 for n in counts.values())
                or sum(counts.values()) != candidates):
            return False
        return absent(SCRATCH) is True
    except BaseException:
        return False


def command_type(supervision, session):
    class GateCommand(supervision.Command):
        """Existing Command protocol, with installed client/output-complete fences."""
        def __init__(self, gate, command_id, *, max_bytes, on_line=None, observe_output=None,
                     diagnostic_role='OTHER_ADB'):
            super().__init__()
            self.gate, self.command_id = gate, command_id
            self._max_bytes, self._on_line, self._observe_output = max_bytes, on_line, observe_output
            self._diagnostic_role = _transport_role(diagnostic_role, 'emulator.adb.read')
            self._pending = bytearray()
            self._stdout_eof_before_cancel = False
            self._stdout_eof_settled = False
            self._total = 0
            self._received = [0, 0]
            self._closed = False
            self._closing_lock = threading.Lock()
            self._wake = threading.Event()
            self._worker = None
            # is_event_source must not mistake a gate command for pending().
            self.process = self
            session.clients.append(self)

        @property
        def is_event_source(self):
            return self._on_line is not None

        @classmethod
        def spawn(cls, argv, *, timeout, max_bytes, on_line=None, tree_owned=False, observe_output=None,
                  diagnostic_role='OTHER_ADB'):
            if (tree_owned or argv[:3] != ['gate-adb', '-s', 'emulator-5554']
                    or len(argv) < 4 or timeout <= 0 or not 0 < max_bytes <= 4 * 1024 * 1024):
                raise blocked()
            role = _transport_role(diagnostic_role, 'emulator.adb.start')
            result = session.request('emulator.adb.start', dict(arguments=argv[3:], max_bytes=max_bytes,
                timeout_seconds=timeout, timeout_reason='Original Task407 command budget'),
                timeout=min(15, timeout), diagnostic_role=role)
            try:
                if (result.get('not_dispatched') or type(result.get('command_id')) is not str
                        or not result['command_id'] or result.get('state') not in ('running', 'draining', 'complete')):
                    raise blocked()  # Explicit non-dispatch never becomes an automatic replay.
            except BaseException:
                _diagnostic_call(session, 'record_local_failure', role, 'PRODUCER_REPLY_VALIDATION')
                raise
            value = cls(session, result['command_id'], max_bytes=max_bytes,
                        on_line=on_line, observe_output=observe_output, diagnostic_role=role)
            value._worker = threading.Thread(target=value._observe, daemon=True)
            value._worker.start()
            return value

        def ingest(self, value):
            phase = 'PRODUCER_REPLY_VALIDATION'
            try:
                if (value.get('command_id') != self.command_id
                        or value.get('state') not in ('running', 'draining', 'complete')
                        or value.get('guest_completion') != 'not_inferred'
                        or value.get('error') is not None):
                    raise blocked()
                remaining = []
                for index, name in enumerate(('stdout', 'stderr')):
                    data, buffered = decode_stream(value[name], 65536)
                    if index == 0 and self._on_line is not None and value[name]['eof']:
                        with self._lock:
                            if not self.cancelled:
                                self._stdout_eof_before_cancel = True
                    self._received[index] += len(data)
                    if value[name]['bytes'] != self._received[index] + buffered:
                        raise blocked()  # Missing/repeated fragments are an evidence gap.
                    remaining.append(buffered)
                    self._total += len(data)
                    if self._total > self._max_bytes:
                        self.failure = 'OUTPUT_CAP'
                        raise blocked()
                    if self._observe_output is not None:
                        try:
                            self._observe_output(index, data)
                        except BaseException:
                            self.diagnostic_output_errors += 1
                            self._observe_output = None
                    if index == 0 and self._on_line is not None:
                        for piece in data.splitlines(keepends=True):
                            self._pending.extend(piece)
                            if len(self._pending) > self._max_bytes:
                                raise blocked()
                            if self._pending.endswith(b'\n'):
                                line = bytes(self._pending[:-1]).rstrip(b'\r')
                                self._pending.clear()
                                phase = 'PUMP_CALLBACK_FAILURE'
                                self._on_line(line)
                                phase = 'PRODUCER_REPLY_VALIDATION'
                    elif not self._discarded:
                        with self._lock:
                            self._buffers[index].extend(data)
                if (self._on_line is not None and value['stdout']['eof']
                        and not remaining[0] and not self._stdout_eof_settled):
                    # Public EOF is independent of client/stderr completion. Drain
                    # every buffered fragment and original line callback FIRST.
                    # reader_complete is only reader settlement, never inferred EOF.
                    with self._lock:
                        self._stdout_eof_settled = True
                        lost = self._stdout_eof_before_cancel or not self.cancelled
                        callbacks = ()
                        if lost:
                            if self.failure in (None, 'CANCELLED'):
                                self.failure = 'STREAM_FAILED' if self._pending else 'FEED_EOF'
                            self._pending.clear()
                            self.feed_lost.set()
                            callbacks = tuple(self._feed_listeners)
                    for callback in callbacks:
                        callback()
                if value['state'] == 'complete' and not any(remaining):
                    if value.get('exit_verified') is not True or type(value.get('exit_code')) is not int:
                        raise blocked()
                    self.returncode = value['exit_code']
                    reason = value.get('reason')
                    if reason == 'deadline_expired':
                        self.failure = self.failure or 'COMMAND_CAP'
                    elif reason == 'cancelled':
                        self.cancelled = True
                        self.failure = self.failure or 'CANCELLED'
                    elif reason == 'vm_exited':
                        # Installed observe has settled this exact client/readers.
                        # VM loss fails work, even with exit 0, not physical proof.
                        self.failure = self.failure or 'VM_EXITED'
                    elif reason not in ('completed', 'failed'):
                        self.failure = self.failure or 'CHILD_UNCONFIRMED'
                        if self._on_line is not None:
                            _diagnostic_call(session, 'record_source_failure', 'UNKNOWN_TERMINAL_REASON',
                                             'PRODUCER_REPLY_VALIDATION')
                    if self._on_line is not None and not self.cancelled:
                        if self._pending:
                            self.failure = self.failure or 'STREAM_FAILED'
                        self._lose_feed()
                    self._finish()
            except BaseException:
                _diagnostic_call(session, 'record_local_failure', self._diagnostic_role, phase)
                # Intentional cancellation cannot erase a genuine failed drain,
                # including one after stdout EOF was already observed buffered.
                if self.failure in (None, 'CANCELLED'):
                    self.failure = 'STREAM_FAILED'
                if self._on_line is not None:
                    self._lose_feed()
                # A capture gap rejects the oracle, not independent client/output
                # exit proof. Close may discard unusable RAM after real completion.
                if (type(value) is dict and value.get('command_id') == self.command_id
                        and value.get('state') == 'complete'
                        and value.get('guest_completion') == 'not_inferred'
                        and value.get('exit_verified') is True
                        and type(value.get('exit_code')) is int):
                    self.returncode = value['exit_code']
                    self.discard()
                    self._pending.clear()
                    self._finish()
                    return
                # Unsettled/foreign/uncertain clients still require exact cancellation.
                with self._lock:
                    callbacks = tuple(self._listeners)
                for callback in callbacks:
                    callback()

        def _observe(self):
            try:
                while not self.done.is_set():
                    value = session.request('emulator.adb.read', dict(command_id=self.command_id, max_bytes=65536),
                                            15, diagnostic_role=self._diagnostic_role)
                    self.ingest(value)
                    # Cadence observes transport only; it cannot renew any cap.
                    if not self.done.is_set():
                        self._wake.wait(.01)
                        self._wake.clear()
            except BaseException:
                self.failure = self.failure or 'CHILD_UNCONFIRMED'
                if self._on_line is not None:
                    _diagnostic_call(session, 'record_source_failure', 'SOURCE_PUMP_EXCEPTION', 'PUMP_CALLBACK_FAILURE')
                self._lose_feed() if self._on_line is not None else None
                with self._lock:
                    callbacks = tuple(self._listeners)
                for callback in callbacks:
                    callback()

        def settle(self, timeout):
            deadline = time.monotonic() + timeout
            if not self.done.wait(max(0, deadline - time.monotonic())):
                return False
            if self._worker is not None and self._worker is not threading.current_thread():
                self._worker.join(max(0, deadline - time.monotonic()))
                if self._worker.is_alive():
                    return False
            if not self._closing_lock.acquire(timeout=max(0, deadline - time.monotonic())):
                return False
            try:
                if not self._closed:
                    try:
                        value = session.request('emulator.adb.close', dict(command_id=self.command_id),
                                                max(0, deadline - time.monotonic()),
                                                diagnostic_role=self._diagnostic_role)
                        self._closed = value.get('command_id') == self.command_id and value.get('closed') is True
                    except BaseException:
                        return False
                return self._closed and self.failure not in ('CHILD_UNCONFIRMED', 'TREE_UNCONFIRMED', 'STREAM_UNCONFIRMED')
            finally:
                self._closing_lock.release()

        def cancel(self, timeout):
            deadline = time.monotonic() + timeout
            if not self._cancel_lock.acquire(timeout=max(0, deadline - time.monotonic())):
                return False
            try:
                if not self.done.is_set() and not self.cancelled:
                    with self._lock:
                        self.cancelled = True
                        self.failure = self.failure or 'CANCELLED'
                    self.discard()
                    try:
                        session.request('emulator.adb.cancel', dict(command_id=self.command_id),
                                        max(0, deadline - time.monotonic()),
                                        diagnostic_role=self._diagnostic_role)
                        self._wake.set()
                    except BaseException:
                        return False
            finally:
                self._cancel_lock.release()
            return self.settle(max(0, deadline - time.monotonic()))

        def snapshot_host_state(self):
            return dict(native_root_state='UNAVAILABLE', job_active_class='UNAVAILABLE',
                        stdout_reader_alive=None, stderr_reader_alive=None)
    return GateCommand


class OwnedGate:
    """Original retained session anchor, not an SDK process/console imitation."""
    def __init__(self, session):
        self.session = self.anchor = session
        self.process = session
        self.done = session.native_exited
        self.cancelled = False

    @property
    def diagnostic_output_errors(self):
        return self.session.diagnostic_output_errors

    @property
    def failure(self):
        return 'STREAM_FAILED' if self.session._sdk_gap else None

    @property
    def returncode(self):
        return self.session.returncode

    def wait(self, timeout):
        return self.session.native_exited.wait(timeout)

    def stop_tree(self, timeout):
        # Fallback cannot replay stop/end or acquire another lease. The guardian
        # alone performs protocol teardown; uncertainty retains this exact keeper.
        return self.session.native_exited.is_set()

    def snapshot_output(self):
        if not self.session._sdk_lock.acquire(blocking=False):
            raise blocked()
        try:
            return (*map(bytes, self.session._sdk_buffers), False)
        finally:
            self.session._sdk_lock.release()

    def snapshot_resource_usage(self):
        return {'state': 'UNAVAILABLE'}

    def snapshot_transport_diagnostics(self):
        return self.session.transport_diagnostic_snapshot()

    def snapshot_host_state(self):
        return dict(native_root_state='UNAVAILABLE', job_active_class='UNAVAILABLE',
                    stdout_reader_alive=None, stderr_reader_alive=None)

    def discard(self):
        if self.session._sdk_lock.acquire(blocking=False):
            try:
                self.session._sdk_buffers = [bytearray(), bytearray()]
            finally:
                self.session._sdk_lock.release()
