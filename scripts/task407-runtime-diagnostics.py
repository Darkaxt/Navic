"""Bounded, observational host diagnostics; no I/O, workers, timers or controls.

SOURCE CONTRACT: feed ONLY the original owned SDK emulator stdout/stderr, and
close BEFORE app installation. Format recognition is not source authentication.
Never feed guest/app/logcat/reader output or arbitrary exceptions to this module.
Only SDK level-prefixed lines and PANIC lines qualify; unknown formats are omitted.
The sanitizer drops recognized credentials/reader/guest/stack material, replaces
recognized paths, URLs, emails, identifiers and current environment identity, then
bounds messages. It is NOT a general arbitrary-log privacy guarantee: an arbitrary
unlabelled book title/person name cannot reliably be distinguished from host prose.
The preinstall source boundary is therefore mandatory, not relaxed by sanitization.
Raw incomplete lines exist only in bounded RAM and never appear in snapshots.
Timestamps describe HOST-observed complete-line arrival, not emitter chronology.
Diagnostics and omissions never imply readiness, admission, success or failure.
"""
from collections import deque
import os
import re
import threading
import time


_HOST = re.compile(r'^(?:emulator: )?(INFO|DEBUG|WARNING|ERROR|FATAL|VERBOSE)\s*(?:\||:)\s*(.*)$')
_PANIC = re.compile(r'^(?:emulator: )?PANIC:\s*(.*)$')
_CREDENTIAL = re.compile(
    r'credential|password|passwd|secret|token|\bauth|api[ _-]?key|'
    r'public[ _-]?key|private[ _-]?key|-----BEGIN', re.IGNORECASE)
_PROTECTED = re.compile(
    r'\b(?:guest|kernel|logcat|reader|book\w*|epub|bitmap|stack|traceback)\b|'
    r'linux version|epubcfi|\bcfis?\b|\bhref\b|\buser[ _-]?ids?\b|'
    r'^\s*at\s+|\bexception\b|\bbacktrace\b|'
    r'\b(?:account(?:[ _-]?name)?|user(?:[ _-]?name)?)\s*[:=]', re.IGNORECASE)
# Each expression sees at most 4096 bytes decoded once, never an unbounded stream.
_REDACTIONS = (
    (re.compile(r'\b[a-zA-Z][a-zA-Z0-9+.-]{1,31}://[^\s<>"\']+|\bwww\.[^\s<>"\']+', re.IGNORECASE), '[URL]'),
    (re.compile(r'(?<![A-Za-z0-9.!#$%&\x27*+/=?^_`{|}~-])[A-Za-z0-9.!#$%&\x27*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b'), '[EMAIL]'),
    (re.compile(r'"(?:[A-Za-z]:[\\/]|\\\\|/)[^"\r\n]*"|\x27(?:[A-Za-z]:[\\/]|\\\\|/)[^\x27\r\n]*\x27'), '[PATH]'),
    # Unquoted Windows paths may contain spaces. Conservatively consume through
    # the next explicit delimiter rather than leak trailing path components.
    (re.compile(r'[A-Za-z]:[\\/][^<>"\x27;|,\r\n]*|\\\\[^<>"\x27;|,\r\n]+'), '[PATH]'),
    (re.compile(r'(?<![\w:/])/(?:[^\s<>"\x27;|,]+)'), '[PATH]'),
    (re.compile(r'\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b', re.IGNORECASE), '[ID]'),
    (re.compile(r'\b(?:0x)?[0-9a-f]{16,}\b', re.IGNORECASE), '[ID]'),
    (re.compile(r'(?<![A-Za-z0-9+/_=-])[A-Za-z0-9+/_=-]{32,}(?![A-Za-z0-9+/_=-])'), '[ID]'),
)
_CONTROL = re.compile(r'[\x00-\x08\x0b-\x1f\x7f-\x9f]')


class _Elapsed:
    def __init__(self, clock):
        self.clock = clock
        self.anchor = clock()
        self.last = 0
        self.clamped = False

    def milliseconds(self):
        value = int((self.clock() - self.anchor) * 1000)
        if value < self.last:
            self.clamped = True
            value = self.last
        self.last = value
        return value


class EmulatorStartupLog:
    """feed(0, bytes) = stdout; feed(1, bytes) = stderr. close() is idempotent.

    128 first + 128 latest accepted records; <=1024 chars/message. Raw partials
    <=4096 bytes/stream; oversized lines are wholly omitted, never suffix-parsed.
    After 4MiB combined input, retention stops and raw partials are cleared; feed
    continues without taking any emulator action. All mutation uses one lock.
    """
    INPUT_CAP = 4 * 1024 * 1024
    PARTIAL_CAP = 4096

    def __init__(self, clock=time.monotonic):
        self._lock = threading.Lock()
        self._elapsed = _Elapsed(clock)
        self._first = []
        self._latest = deque(maxlen=128)
        self._partials = [bytearray(), bytearray()]
        self._overlong = [False, False]
        self._closed = False
        self._capped = False
        self._truncated = 0
        self._counts = dict(received_bytes=0, parsed_lines=0, accepted_lines=0,
            omitted_unknown_lines=0, omitted_sensitive_lines=0,
            omitted_overlong_lines=0, omitted_cap_bytes=0,
            omitted_after_close_bytes=0, redacted_lines=0)
        # Environment reads only: no home expansion, user database or filesystem.
        identities = {os.environ.get(key, '') for key in
                      ('USERNAME', 'USER', 'USERPROFILE', 'HOME')}
        self._identities = tuple(re.compile(
            (r'(?<!\w)' + re.escape(value) + r'(?!\w)') if len(value) < 3
            else re.escape(value), re.IGNORECASE)
            for value in sorted(identities, key=len, reverse=True) if value)

    def feed(self, index, chunk):
        """Consume original owned pipe bytes. Reject invalid argument shapes only."""
        if type(index) is not int or index not in (0, 1) or type(chunk) is not bytes:
            raise ValueError('STARTUP_LOG_INPUT_INVALID')
        with self._lock:
            previous = self._counts['received_bytes']
            self._counts['received_bytes'] += len(chunk)
            if self._closed:
                self._counts['omitted_after_close_bytes'] += len(chunk)
                return
            limit = min(len(chunk), max(0, self.INPUT_CAP - previous))
            self._counts['omitted_cap_bytes'] += len(chunk) - limit
            if not limit:
                return
            arrival = self._elapsed.milliseconds()
            cursor = 0
            while cursor < limit:
                newline = chunk.find(b'\n', cursor, limit)
                end = limit if newline < 0 else newline
                partial = self._partials[index]
                if not self._overlong[index]:
                    if len(partial) + end - cursor > self.PARTIAL_CAP:
                        partial.clear()
                        self._overlong[index] = True
                    else:
                        partial.extend(chunk[cursor:end])
                if newline < 0:
                    break
                self._finish_line(index, arrival)
                cursor = newline + 1
            if previous + limit >= self.INPUT_CAP:
                self._capped = True
                for partial in self._partials:
                    partial.clear()
                self._overlong = [False, False]

    def _finish_line(self, index, arrival):
        self._counts['parsed_lines'] += 1
        partial = self._partials[index]
        if self._overlong[index]:
            self._counts['omitted_overlong_lines'] += 1
        else:
            line = partial.decode('utf-8', errors='replace')
            if line.endswith('\r'):
                line = line[:-1]
            self._retain(index, line, arrival)
        partial.clear()
        self._overlong[index] = False

    def _retain(self, index, line, arrival):
        match = _HOST.fullmatch(line)
        if match is not None:
            severity, message = match.groups()
        else:
            match = _PANIC.fullmatch(line)
            if match is None:
                self._counts['omitted_unknown_lines'] += 1
                return
            severity, message = 'PANIC', match.group(1)
        if _CREDENTIAL.search(message) or _PROTECTED.search(message):
            self._counts['omitted_sensitive_lines'] += 1
            return
        sanitized = message
        for identity in self._identities:
            sanitized = identity.sub('[IDENTITY]', sanitized)
        for expression, replacement in _REDACTIONS:
            sanitized = expression.sub(replacement, sanitized)
        sanitized = _CONTROL.sub(' ', sanitized)
        if sanitized != message:
            self._counts['redacted_lines'] += 1
        if len(sanitized) > 1024:
            sanitized = sanitized[:1024]
            self._truncated += 1
        sequence = self._counts['accepted_lines']
        self._counts['accepted_lines'] += 1
        record = dict(sequence=sequence, elapsed_ms=arrival,
            stream=('stdout', 'stderr')[index], severity=severity, message=sanitized)
        if len(self._first) < 128:
            self._first.append(record)
        else:
            self._latest.append(record)

    def close(self):
        """Caller must close before install. Flush final partials and deny new input."""
        with self._lock:
            if self._closed:
                return
            self._closed = True
            try:
                arrival = self._elapsed.milliseconds()
                for index in (0, 1):
                    if self._partials[index] or self._overlong[index]:
                        self._finish_line(index, arrival)
            finally:
                for partial in self._partials:
                    partial.clear()
                self._overlong = [False, False]
                # The mandatory source cutoff survives every diagnostic failure.
                self._identities = ()

    def snapshot(self):
        """Detached safe structure only; no raw stdout/stderr or partial text."""
        with self._lock:
            records = [dict(record) for record in self._first]
            records.extend(dict(record) for record in self._latest)
            return dict(profile='PREINSTALL_OWNED_SDK_HOST_LOG_V1',
                time_basis='HOST_OBSERVED_LINE_ARRIVAL', records=records,
                counts=dict(self._counts),
                dropped_records=max(0, self._counts['accepted_lines'] - len(records)),
                truncated_records=self._truncated, partial_bytes=[len(p) for p in self._partials],
                input_cap_reached=self._capped, closed=self._closed,
                clock_clamped=self._elapsed.clamped)


PHASES = frozenset(('SELECTED_ABSENCE', 'OWNED_LAUNCH', 'WAIT_FOR_DEVICE',
    'OWNED_AVD_IDENTITY', 'SYS_BOOT_COMPLETED', 'BOOT_HANDOFF', 'INSTALL', 'CURSOR',
    'OBSERVER_ADMISSION', 'INSTRUMENTATION', 'TEARDOWN', 'SESSION'))
EVENTS = frozenset(('START', 'END', 'FAIL'))
OUTCOMES = frozenset(('NONE', 'PASSED', 'BLOCKED', 'FAILED', 'CANCELLED', 'UNCONFIRMED',
    'SELECTED_ABSENCE_UNCONFIRMED', 'DEVICE_WAIT_UNCONFIRMED', 'OWNED_AVD_UNCONFIRMED',
    'UI_READY_UNCONFIRMED', 'BOOT_HANDOFF_REJECTED', 'INSTALL_UNCONFIRMED',
    'CURSOR_COMMAND_UNCONFIRMED', 'OBSERVER_ADMISSION_REJECTED',
    'INSTRUMENTATION_ADMISSION_REJECTED', 'GUARDIAN_ALREADY_TERMINAL',
    'PREFLIGHT_COMMAND_INCOMPLETE', 'PREFLIGHT_CLIENT_UNSETTLED', 'UNEXPECTED_EXECUTION',
    'PREPARATION_CAP', 'INSTRUMENTATION_CAP', 'OUTPUT_CAP', 'COMMAND_CAP', 'STREAM_FAILED'))
METRICS = frozenset(('available_memory_bytes', 'free_disk_bytes', 'owned_output_bytes',
    'user_cpu_ms', 'kernel_cpu_ms', 'active_processes', 'total_processes', 'page_faults',
    'peak_job_memory_bytes'))


class PhaseTrace:
    """Fixed observational phase events, never admission/control decisions.

    record(phase, START/END/FAIL, outcome='NONE', metrics=None). Metrics are already
    safe caller-supplied nonnegative integers <=2**63-1 under the fixed METRICS
    names; no resource probes occur here. Repeated START anchors the newest attempt;
    an unmatched END/FAIL has unknown duration. First 256 records retained; later
    records counted as dropped. Phase anchors have fixed PHASES cardinality.
    """
    def __init__(self, clock=time.monotonic):
        self._lock = threading.Lock()
        self._elapsed = _Elapsed(clock)
        self._records = []
        self._starts = {}
        self._count = 0

    def record(self, phase, event, *, outcome='NONE', metrics=None):
        if (type(phase) is not str or phase not in PHASES or
                type(event) is not str or event not in EVENTS or
                type(outcome) is not str or outcome not in OUTCOMES):
            raise ValueError('PHASE_TRACE_LABEL_INVALID')
        if metrics is None:
            metrics = {}
        if type(metrics) is not dict or len(metrics) > len(METRICS):
            raise ValueError('PHASE_TRACE_METRICS_INVALID')
        for key, value in metrics.items():
            if (type(key) is not str or key not in METRICS or type(value) is not int
                    or not 0 <= value <= 2 ** 63 - 1):
                raise ValueError('PHASE_TRACE_METRICS_INVALID')
        safe_metrics = dict(metrics)
        with self._lock:
            elapsed = self._elapsed.milliseconds()
            duration = None
            if event == 'START':
                self._starts[phase] = elapsed
            else:
                start = self._starts.pop(phase, None)
                if start is not None:
                    duration = elapsed - start
            sequence = self._count
            self._count += 1
            if len(self._records) < 256:
                self._records.append(dict(sequence=sequence, phase=phase, event=event,
                    elapsed_ms=elapsed, duration_ms=duration, outcome=outcome,
                    metrics=safe_metrics))

    def snapshot(self):
        with self._lock:
            return dict(profile='HOST_PHASE_TRACE_V1', time_basis='HOST_OBSERVED_EVENT_ARRIVAL',
                records=[dict(record, metrics=dict(record['metrics'])) for record in self._records],
                dropped_records=self._count - len(self._records),
                clock_clamped=self._elapsed.clamped)
