"""Host-only bounded lifecycle supervision; importing this module starts nothing.

Integration contract (not Android authority):
* MAIN supplies its genuine launch-contract watchdog, the exact already-owned
  emulator serial/AVD, and proof that snapshot saving was disabled at launch.
* ``owned`` retains the ORIGINAL Popen/OS handle as ``anchor``. Its wait(timeout)
  returns root terminal/not-terminal. stop_tree(timeout) MUST stop AND prove the
  anchored whole tree settled, even when its root has already exited. Use the
  original private job/equivalent, not PID/name lookup. TerminateJobObject or
  CloseHandle acceptance alone is not proof. Command.stop_tree provides this for
  its genuinely owned Windows tree; no process is acquired by this API.
* ``commands.start(action, argument, timeout, max_bytes)`` immediately publishes a
  Command for an already-selected lifecycle operation. Allowed actions are only
  identity, snapshot_list/delete, console_stop, disconnect, absence. The adapter
  selects the exact serial; it must not boot/install/instrument or clear anything.
  start must publish ownership before doing external work. A late returned Command
  is cancelled too; a hidden/unpublished child cannot be supervised by any API.
* Create Observer(package) explicitly UNARMED, then Supervisor(..., instrument=None)
  immediately after ownership, BEFORE boot/readiness/install or blocking work.
  The real caller supplies launch_started captured before owned spawn; omitted
  anchors preserve construction-time behavior for generic host integrations.
  With a declared postboot reserve, complete_boot() admits exactly one successful
  readiness handoff before boot expiry into a launch-ceiling-bounded second phase.
  Generic zero-reserve host fixtures retain one original preparation deadline.
* MAIN obtains a REAL observed device cursor plus corresponding live source and
  calls observer.arm(epoch=..., after_ns=..., source=...) once. Device clock/cursor
  acquisition, event command/framing and source provenance belong in the caller;
  never substitute host time, sentinels or retained events with a new timestamp.
  This fence is operational freshness, not a perfect-currentness/reader oracle.
  Arming cannot reset preparation or replace the configured boot handoff. Only
  armed, live-source admission before the active preparation deadline permits
  attach_instrumentation. Initial/unadmitted/late work
  is rejected/stopped. The separate host instrumentation cap starts on admission;
  reader production deadlines/oracles remain unchanged.
* Command.spawn(on_line=decoder) streams bounded live lines without retaining
  stdout. The decoder is pure/nonblocking and feeds exact am_anr payloads into
  observer.am_anr. Unexpected stdout EOF signals loss immediately even if the
  source process hangs; controlled stop is distinct. Source failure/EOF fails
  closed. Command.pending is an explicitly synthetic source for HOST tests only,
  never a substitute for MAIN's real bound source. No reason/raw line/PID/package
  is returned in reports.
* The daemon guardian needs no wait()/console cooperation. MAIN's genuine watchdog
  must also cover the host process itself; Python threads cannot survive host
  termination or a native extension indefinitely holding the GIL.

After wait(), only NATURAL_COMPLETION + eligible_for_oracle permits the caller to
consume bounded in-memory instrumentation via take_instrumentation_output and run
its ORIGINAL three-method oracle. This module cannot report cases passed/failed.
UNKNOWN aborts and BLOCKED cleanup are not 0/3 failures. No raw files are written.
"""
from dataclasses import dataclass
import math
import os
import re
import subprocess
import threading
import time


class Blocked(Exception):
    def __init__(self):
        super().__init__('Runtime supervision blocked')


@dataclass(frozen=True)
class LaunchContract:
    watchdog_id: str
    no_snapshot_save: bool


@dataclass(frozen=True)
class Caps:
    instrumentation: float
    command: float
    process_wait: float
    stop: float
    settle: float
    snapshot_budget: float
    preparation: float = 60.0
    postboot_preparation: float = 0.0

    def __post_init__(self):
        if (any(type(v) not in (int, float) or not math.isfinite(v)
                or (v < 0 if name == 'postboot_preparation' else v <= 0)
                for name, v in self.__dict__.items())
                or not math.isfinite(self.session_budget)):
            raise Blocked()

    @property
    def teardown_budget(self):
        """Sequential _teardown upper bound, including _command's finally tails.

        Four standalone command budgets: identity, console stop, disconnect and
        strict absence (all its retries share one deadline). Snapshot operations
        share their own transaction deadline; only its last command can overrun
        into cancellation. Instrument/source stop and both original-root waits
        are independent obligations. No arbitrary scheduling cushion is added.
        """
        return (2 * self.settle + 4 * (self.command + self.settle)
                + self.snapshot_budget + self.settle
                + 2 * self.process_wait + self.stop)

    @property
    def session_budget(self):
        """Launch -> guardian terminal; excludes selected-absence/caller cleanup."""
        return (self.preparation + self.postboot_preparation
                + self.instrumentation + self.teardown_budget)


@dataclass(frozen=True)
class Report:
    status: str
    cause: str
    shutdown_verified: bool
    eligible_for_oracle: bool
    original_terminal: bool
    snapshots_empty: bool
    selected_disconnect: bool
    strict_absence: bool
    cleanup_failures: tuple
    instrumentation_exit_code: int | None


def is_own_am_anr(tag, payload, package):
    """Pure classifier for raw am_anr event payload, never returns captured data."""
    if not isinstance(package, str) or not re.fullmatch(r'[A-Za-z_]\w*(?:\.[A-Za-z_]\w*)+', package, re.ASCII):
        return False
    if tag != 'am_anr' or not isinstance(payload, str) or len(payload) > 16384:
        return False
    # AOSP first four fields are scalar ABI, not a reason-text grammar. The
    # remaining reason is opaque and may itself contain commas/brackets/newlines.
    match = re.fullmatch(r'\[([0-9]{1,10}),([0-9]{1,10}),([A-Za-z0-9_.]+),(-?[0-9]{1,10}),(.*)\]',
                         payload, re.DOTALL)
    if not match or match.group(3) != package:
        return False
    user, pid, flags = int(match.group(1)), int(match.group(2)), int(match.group(4))
    return 0 <= user <= 2147483647 and 1 <= pid <= 2147483647 and -2147483648 <= flags <= 2147483647


class OwnedProcess:
    """Retain original Popen plus the caller's launch-owned exact tree stopper.

    The stopper receives this same Popen, never a newly looked-up process. It must
    use the original job/OS handle, not reconstruct authority from its numeric PID.
    """
    def __init__(self, original, stop_original_tree):
        self.anchor = original
        self._stop_original_tree = stop_original_tree

    def wait(self, timeout):
        try:
            self.anchor.wait(timeout=timeout)
            return True
        except subprocess.TimeoutExpired:
            return False

    def stop_tree(self, timeout):
        return self._stop_original_tree(self.anchor, timeout)


class Observer:
    """UNARMED during preflight; MAIN arms once from an observed guest cursor."""
    def __init__(self, package):
        if (not isinstance(package, str)
                or not re.fullmatch(r'[A-Za-z_]\w*(?:\.[A-Za-z_]\w*)+', package, re.ASCII)):
            raise Blocked()
        self._package = package
        self._epoch = self._after_ns = None
        self._lock = threading.RLock()
        self._listeners = []
        self.armed = False
        self.own_anr = False
        self.unavailable = False
        self.source = None
        self._stopping = False

    def subscribe(self, notify):
        with self._lock:
            self._listeners.append(notify)

    def _notify(self):
        with self._lock:
            callbacks = tuple(self._listeners)
        for callback in callbacks:
            callback()

    def _admitted_locked(self):
        source = self.source
        return (self.armed and not self._stopping and not self.unavailable and not self.own_anr
                and source is not None and source.is_event_source and not source.done.is_set()
                and not source.feed_lost.is_set() and not source.cancelled and not source.failure)

    def arm(self, *, epoch, after_ns, source):
        """One-shot caller admission; no host-clock/sentinel freshness is invented.

        MAIN supplies a REAL observed device cursor and the corresponding live
        bound source. This is an operational fence, not a perfect-currentness
        oracle. Arming does not change Supervisor's original preparation deadline.
        """
        with self._lock:
            accepted = (not self.armed and not self._stopping and not self.unavailable
                        and isinstance(epoch, str) and bool(epoch)
                        and type(after_ns) is int and after_ns > 0
                        and isinstance(source, Command) and source.is_event_source and not source.done.is_set()
                        and not source.feed_lost.is_set() and not source.cancelled and not source.failure)
            if accepted:
                self._epoch, self._after_ns, self.source = epoch, after_ns, source
                self.armed = True
        if not accepted:
            if isinstance(source, Command):
                _bounded(lambda: source.cancel(.5), .5)
                source.discard()
            return False
        source.subscribe(self.failed)
        source.subscribe_feed_loss(self.failed)
        self._notify()
        return True

    def am_anr(self, tag, payload, *, timestamp_ns, epoch):
        with self._lock:
            own = (self.armed and epoch == self._epoch and type(timestamp_ns) is int
                   and timestamp_ns > self._after_ns
                   and is_own_am_anr(tag, payload, self._package))
            if own:
                self.own_anr = True
        if own:
            self._notify()

    def failed(self):
        with self._lock:
            if not self._stopping:
                self.unavailable = True
        self._notify()

    def stop(self, timeout):
        with self._lock:
            if self.source is not None and (self.source.done.is_set() or self.source.feed_lost.is_set()):
                self.unavailable = True
            self._stopping = True
            source = self.source
        if source is None:
            return True
        stopped = source.cancel(timeout)
        if source.failure not in (None, 'CANCELLED'):
            with self._lock:
                self.unavailable = True
        source.discard()
        return stopped

    def closed(self):
        self.failed()


class _WindowsJob:
    """Private per-command job; no image-name or PID-based termination."""
    def __init__(self, process):
        import ctypes
        from ctypes import wintypes
        self._ctypes = ctypes
        self._lock = threading.RLock()
        self._empty_confirmed = False
        self._kernel = ctypes.WinDLL('kernel32', use_last_error=True)
        kernel = self._kernel
        kernel.CreateJobObjectW.argtypes = [ctypes.c_void_p, wintypes.LPCWSTR]
        kernel.CreateJobObjectW.restype = wintypes.HANDLE
        kernel.AssignProcessToJobObject.argtypes = [wintypes.HANDLE, wintypes.HANDLE]
        kernel.AssignProcessToJobObject.restype = wintypes.BOOL
        kernel.TerminateJobObject.argtypes = [wintypes.HANDLE, wintypes.UINT]
        kernel.TerminateJobObject.restype = wintypes.BOOL
        kernel.CloseHandle.argtypes = [wintypes.HANDLE]
        kernel.CloseHandle.restype = wintypes.BOOL
        self._handle = kernel.CreateJobObjectW(None, None)
        if not self._handle:
            raise Blocked()

        class BasicLimits(ctypes.Structure):
            _fields_ = [('process_time', ctypes.c_longlong), ('job_time', ctypes.c_longlong),
                        ('flags', wintypes.DWORD), ('min_ws', ctypes.c_size_t),
                        ('max_ws', ctypes.c_size_t), ('active_limit', wintypes.DWORD),
                        ('affinity', ctypes.c_size_t), ('priority', wintypes.DWORD),
                        ('scheduling', wintypes.DWORD)]

        class ExtendedLimits(ctypes.Structure):
            _fields_ = [('basic', BasicLimits), ('io', ctypes.c_ulonglong * 6),
                        ('process_memory', ctypes.c_size_t), ('job_memory', ctypes.c_size_t),
                        ('peak_process', ctypes.c_size_t), ('peak_job', ctypes.c_size_t)]

        kernel.SetInformationJobObject.argtypes = [wintypes.HANDLE, ctypes.c_int,
                                                   ctypes.c_void_p, wintypes.DWORD]
        kernel.SetInformationJobObject.restype = wintypes.BOOL
        limits = ExtendedLimits()
        limits.basic.flags = 0x2000  # JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE, owned trees only.
        configured = kernel.SetInformationJobObject(self._handle, 9, ctypes.byref(limits), ctypes.sizeof(limits))
        kernel.CreateIoCompletionPort.argtypes = [wintypes.HANDLE, wintypes.HANDLE, ctypes.c_size_t, wintypes.DWORD]
        kernel.CreateIoCompletionPort.restype = wintypes.HANDLE
        kernel.GetQueuedCompletionStatus.argtypes = [wintypes.HANDLE, ctypes.POINTER(wintypes.DWORD),
                                                     ctypes.POINTER(ctypes.c_size_t),
                                                     ctypes.POINTER(ctypes.c_void_p), wintypes.DWORD]
        kernel.GetQueuedCompletionStatus.restype = wintypes.BOOL
        kernel.QueryInformationJobObject.argtypes = [wintypes.HANDLE, ctypes.c_int, ctypes.c_void_p,
                                                      wintypes.DWORD, ctypes.c_void_p]
        kernel.QueryInformationJobObject.restype = wintypes.BOOL
        self._port = kernel.CreateIoCompletionPort(wintypes.HANDLE(-1), None, 0, 1)

        class CompletionPort(ctypes.Structure):
            _fields_ = [('key', ctypes.c_void_p), ('port', wintypes.HANDLE)]

        association = CompletionPort(1, self._port)
        associated = (self._port and kernel.SetInformationJobObject(
            self._handle, 7, ctypes.byref(association), ctypes.sizeof(association)))
        if not configured or not associated or not kernel.AssignProcessToJobObject(self._handle, int(process._handle)):
            if self._port:
                kernel.CloseHandle(self._port)
            kernel.CloseHandle(self._handle)
            self._handle = self._port = None
            raise Blocked()

    def resume(self, process):
        # Popen closes the initial thread handle. Resume only the retained process
        # handle after assigning its still-suspended process to this private job.
        ntdll = self._ctypes.WinDLL('ntdll')
        resume = ntdll.NtResumeProcess
        resume.argtypes = [self._ctypes.c_void_p]
        resume.restype = self._ctypes.c_long
        if resume(int(process._handle)) < 0:
            raise Blocked()

    def stop(self):
        with self._lock:
            return self._empty_confirmed if self._handle is None else bool(self._kernel.TerminateJobObject(self._handle, 1))

    def active_count(self):
        """Handle-scoped accounting proof, never a PID/image enumeration."""
        ctypes = self._ctypes
        class Accounting(ctypes.Structure):
            _fields_ = [('times', ctypes.c_longlong * 4), ('page_faults', ctypes.c_ulong),
                        ('total', ctypes.c_ulong), ('active', ctypes.c_ulong), ('terminated', ctypes.c_ulong)]
        with self._lock:
            info = Accounting()
            if not self._handle or not self._kernel.QueryInformationJobObject(
                    self._handle, 1, ctypes.byref(info), ctypes.sizeof(info), None):
                raise Blocked()
            return int(info.active)

    def active_count_class(self):
        """Best-effort scalar only; never wait behind concurrent job teardown."""
        if not self._lock.acquire(blocking=False):
            return 'UNAVAILABLE'
        try:
            if not self._handle:
                return 'UNAVAILABLE'
            count = self.active_count()
            if type(count) is not int or count < 0:
                return 'UNAVAILABLE'
            return 'ZERO' if count == 0 else 'ONE' if count == 1 else 'MULTIPLE'
        except BaseException:
            return 'UNAVAILABLE'
        finally:
            self._lock.release()

    def stop_and_settle(self, timeout):
        """Terminate only this private job, then prove zero contained processes.

        A single initial count can already prove absence; otherwise block on job
        completion events, not polling. Terminate/close success alone is NOT proof.
        """
        ctypes = self._ctypes
        with self._lock:
            if self._handle is None:
                return self._empty_confirmed
            if not self.stop():
                return False
            if self.active_count() == 0:
                self._empty_confirmed = True
                return True
            deadline = time.monotonic() + timeout
            while True:
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    return False
                message = ctypes.c_ulong()
                key = ctypes.c_size_t()
                opaque = ctypes.c_void_p()
                if not self._kernel.GetQueuedCompletionStatus(
                        self._port, ctypes.byref(message), ctypes.byref(key), ctypes.byref(opaque),
                        min(0xfffffffe, max(1, math.ceil(remaining * 1000)))):
                    return False
                if message.value == 4 and key.value == 1:  # JOB_OBJECT_MSG_ACTIVE_PROCESS_ZERO
                    if self.active_count() == 0:
                        self._empty_confirmed = True
                        return True

    def close(self):
        with self._lock:
            if self._handle:
                if not self._kernel.CloseHandle(self._handle):
                    return False
                self._handle = None
            if self._port:
                if not self._kernel.CloseHandle(self._port):
                    return False
                self._port = None
            return True


class Command:
    """Bounded in-memory command; two readers + one finite process waiter.

    spawn is for explicit caller commands only, never invoked on import. Default
    client-only cancellation kills the original process, NEVER its descendants:
    e.g. an ADB client's auto-started shared server is NOT task-owned. A client
    whose shared descendant retains its pipes may fail settlement; that is BLOCKED,
    not permission to kill that descendant. Use tree_owned=True ONLY when the
    complete tree is genuinely owned (emulator/host fixtures). This concrete owned
    backend is Windows-only: private job assignment occurs while suspended, before
    descendants can spawn; settlement requires zero active-count proof, waiting on
    job completion events when the initial count is nonzero.
    Unsupported owned-tree backends fail closed rather than using PID/group kills.
    on_line streams bounded stdout lines to a pure nonblocking decoder without
    retaining them; stderr and ordinary command output share a fixed byte budget.
    """
    def __init__(self):
        self.done = threading.Event()
        self.feed_lost = threading.Event()
        self.process = None
        self.returncode = None
        self.failure = None
        self.cancelled = False
        self._lock = threading.Lock()
        self._cancel_lock = threading.Lock()
        self._listeners = []
        self._feed_listeners = []
        self._buffers = [bytearray(), bytearray()]
        self._readers = []
        self._job = None
        self._max_bytes = 65536
        self._discarded = False
        self._on_line = None
        self._tree_owned = False
        self._tree_settled = True

    @classmethod
    def pending(cls):
        return cls()

    @classmethod
    def completed(cls, returncode, stdout=b'', stderr=b''):
        value = cls()
        value.returncode = returncode
        if len(stdout) + len(stderr) > value._max_bytes:
            value.failure = 'OUTPUT_CAP'
        else:
            value._buffers = [bytearray(stdout), bytearray(stderr)]
        value._finish()
        return value

    @classmethod
    def spawn(cls, argv, *, timeout, max_bytes, on_line=None, tree_owned=False):
        if (not argv or isinstance(argv, (str, bytes)) or not all(isinstance(v, str) for v in argv)
                or type(timeout) not in (int, float) or not math.isfinite(timeout) or timeout <= 0
                or type(max_bytes) is not int or not 0 < max_bytes <= 4 * 1024 * 1024
                or (on_line is not None and not callable(on_line)) or type(tree_owned) is not bool
                or (tree_owned and os.name != 'nt')):
            raise Blocked()
        value = cls()
        value._max_bytes = max_bytes
        value._on_line = on_line
        value._tree_owned = tree_owned
        value._tree_settled = not tree_owned
        try:
            value.process = subprocess.Popen(argv, stdin=subprocess.DEVNULL,
                                             stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                             start_new_session=tree_owned and os.name != 'nt', bufsize=0,
                                             creationflags=0x00000004 if tree_owned and os.name == 'nt' else 0)
            if tree_owned and os.name == 'nt':
                value._job = _WindowsJob(value.process)
                value._job.resume(value.process)
        except BaseException:
            if value.process is not None:
                value.process.kill()
                try:
                    value.process.wait(timeout=.5)
                except BaseException:
                    pass
                for pipe in (value.process.stdout, value.process.stderr):
                    pipe.close()
            if value._job is not None:
                value._job.stop()
                value._job.close()
            raise Blocked() from None
        for index, pipe in enumerate((value.process.stdout, value.process.stderr)):
            reader = threading.Thread(target=value._read, args=(index, pipe), daemon=True)
            value._readers.append(reader)
            reader.start()
        threading.Thread(target=value._reap, args=(timeout,), daemon=True).start()
        return value

    def subscribe(self, notify):
        with self._lock:
            self._listeners.append(notify)
        if self.done.is_set():
            notify()

    def subscribe_feed_loss(self, notify):
        with self._lock:
            self._feed_listeners.append(notify)
        if self.feed_lost.is_set():
            notify()

    def _lose_feed(self):
        with self._lock:
            if self.cancelled:
                return
            self.failure = self.failure or 'FEED_EOF'
            self.feed_lost.set()
            callbacks = tuple(self._feed_listeners)
        for callback in callbacks:
            callback()

    def _finish(self):
        self.done.set()
        with self._lock:
            callbacks = tuple(self._listeners)
        for callback in callbacks:
            callback()

    @property
    def is_event_source(self):
        # pending() is an explicitly synthetic/in-memory source for host fixtures.
        return self.process is None or self._on_line is not None

    @property
    def buffered_bytes(self):
        with self._lock:
            return sum(map(len, self._buffers))

    def _stop(self):
        if self.process is None:
            return True
        try:
            if not self._tree_owned:
                # Client-only mode: a descendant may be a shared ADB server.
                # Popen retains the original Windows process handle.
                self.process.kill()
                return True
            if self._job is not None:
                return self._job.stop()
            return False  # No unproven PID/group fallback for an owned tree.
        except ProcessLookupError:
            return True
        except BaseException:
            return False

    def _read(self, index, pipe):
        pending = bytearray()
        try:
            while True:
                chunk = pipe.read(min(4096, self._max_bytes + 1))
                if not chunk:
                    if pending and not self.cancelled:
                        raise Blocked()  # A truncated event is observer failure.
                    if index == 0 and self._on_line is not None:
                        self._lose_feed()  # Immediate loss, even if process remains live.
                    break
                if index == 0 and self._on_line is not None:
                    # Stream callbacks are pure bounded decoders; no raw stdout
                    # is retained. Callback failure/EOF fails the bound source.
                    for piece in chunk.splitlines(keepends=True):
                        if len(pending) + len(piece) > self._max_bytes:
                            raise Blocked()
                        pending.extend(piece)
                        if pending.endswith(b'\n'):
                            line = bytes(pending[:-1])
                            pending.clear()
                            self._on_line(line[:-1] if line.endswith(b'\r') else line)
                    continue
                with self._lock:
                    remaining = self._max_bytes - sum(map(len, self._buffers))
                    if len(chunk) > remaining:
                        self.failure = 'OUTPUT_CAP'
                        overflow = True
                    elif not self._discarded:
                        self._buffers[index].extend(chunk)
                        overflow = False
                    else:
                        overflow = False
                if overflow:
                    self._stop()
                    break
        except BaseException:
            with self._lock:
                self.failure = 'STREAM_FAILED'
            if self._on_line is not None:
                self._lose_feed()
            self._stop()
        finally:
            pipe.close()

    def _reap(self, timeout):
        try:
            self.returncode = self.process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            self.failure = 'COMMAND_CAP'
            self._stop()
            try:
                self.returncode = self.process.wait(timeout=.5)
            except BaseException:
                self.failure = 'CHILD_UNCONFIRMED'
        except BaseException:
            self.failure = 'CHILD_UNCONFIRMED'
            self._stop()
        # Root exit is not tree settlement. Owned Windows jobs require a zero
        # handle-scoped active count, waiting on job completion events if needed.
        if self._job is not None:
            try:
                self._tree_settled = self._job.stop_and_settle(.5)
            except BaseException:
                self._tree_settled = False
            if not self._tree_settled:
                self.failure = 'TREE_UNCONFIRMED'
        elif not self._stop():
            self.failure = 'CHILD_UNCONFIRMED'
        deadline = time.monotonic() + .5
        for reader in self._readers:
            reader.join(timeout=max(0, deadline - time.monotonic()))
        if any(reader.is_alive() for reader in self._readers):
            self.failure = 'STREAM_UNCONFIRMED'
        if self._job is not None:
            self._job.close()
        self._finish()

    def cancel(self, timeout):
        with self._cancel_lock:
            if not self.done.is_set():
                with self._lock:
                    self.cancelled = True
                    self.failure = self.failure or 'CANCELLED'
                self.discard()
                if self.process is None:
                    self._finish()
                    return True
                self._stop()
        return self.settle(timeout)

    def stop_tree(self, timeout):
        """Exact owned-tree stopper suitable for OwnedProcess's launch adapter."""
        return self._tree_owned and self.cancel(timeout)

    def settle(self, timeout):
        if not self.done.wait(timeout):
            return False
        return ((self.process is None or self.process.poll() is not None)
                and not any(reader.is_alive() for reader in self._readers)
                and self._tree_settled
                and self.failure not in ('CHILD_UNCONFIRMED', 'TREE_UNCONFIRMED'))

    def snapshot_host_state(self):
        """Independent read-only scalars, not history, guest liveness or readiness.

        Query only retained original handles. Busy/closed/unavailable state stays
        unknown; this cannot renew any phase or repair concurrent guardian teardown.
        """
        native = 'UNAVAILABLE'
        readers = (None, None)
        real_process = isinstance(self.process, subprocess.Popen)
        if os.name == 'nt' and real_process:
            try:
                import _winapi
                handle = self.process._handle
                if handle is not None:
                    state = _winapi.WaitForSingleObject(int(handle), 0)
                    if type(state) is int:
                        native = 'TERMINAL' if state == 0 else 'RUNNING' if state == 258 else 'UNAVAILABLE'
            except BaseException:
                pass
        if real_process and len(self._readers) == 2 and all(
                isinstance(reader, threading.Thread) for reader in self._readers):
            readers = tuple(reader.is_alive() for reader in self._readers)
        job = 'NOT_OWNED'
        if self._tree_owned:
            job = self._job.active_count_class() if isinstance(self._job, _WindowsJob) else 'UNAVAILABLE'
        return {'native_root_state': native, 'job_active_class': job,
                'stdout_reader_alive': readers[0], 'stderr_reader_alive': readers[1]}

    def snapshot_output(self):
        """Non-draining immutable copies plus explicit discard state, under one lock.

        This observes only the currently retained bounded buffers. It neither
        resets their shared output cap nor proves stream completeness/chronology.
        """
        with self._lock:
            return bytes(self._buffers[0]), bytes(self._buffers[1]), self._discarded

    def take_output(self):
        with self._lock:
            result = tuple(bytes(v) for v in self._buffers)
            self._buffers = [bytearray(), bytearray()]
        return result

    def discard(self):
        with self._lock:
            self._discarded = True
            self._buffers = [bytearray(), bytearray()]


def _bounded(call, timeout, late=None):
    """Bound even a defective adapter call; late published children are stopped.

    At most one daemon worker per bounded lifecycle step, never an unbounded join.
    Adapter start/stop must still obey the ownership/publication contract above.
    """
    completed = threading.Event()
    lock = threading.Lock()
    box = {'expired': False, 'ok': False, 'value': None}

    def worker():
        try:
            value = call()
        except BaseException:
            completed.set()
            return
        with lock:
            expired = box['expired']
            if not expired:
                box['ok'], box['value'] = True, value
                completed.set()
        if expired and late is not None:
            try:
                late(value)
            except BaseException:
                pass
        completed.set()

    threading.Thread(target=worker, daemon=True).start()
    completed.wait(timeout)
    with lock:
        if not completed.is_set():
            box['expired'] = True
            return False, None
        return box['ok'], box['value']


def _snapshots(raw):
    text = raw.decode('ascii', errors='strict')
    lines = [v.strip() for v in text.splitlines() if v.strip()]
    if not lines or lines[-1] != 'OK':
        raise Blocked()
    recognized, names = False, []
    for line in lines[:-1]:
        if line == 'List of snapshots present on all disks:' or re.fullmatch(r'There (?:is|are) no snapshots? available\.?', line):
            recognized = True
        elif line.startswith('ID') and 'TAG' in line:
            continue
        elif line.startswith('-- '):
            parts = line.split()
            if (len(parts) < 2 or not re.fullmatch(r'[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}', parts[1])
                    or '..' in parts[1]):
                raise Blocked()
            names.append(parts[1])
        else:
            raise Blocked()
    if not recognized or len(names) > 16 or len(set(names)) != len(names):
        raise Blocked()
    return names


def _console_stop_ack(raw):
    lines = [line for line in raw.splitlines() if line]
    return lines in ([b'OK: killing emulator, bye bye'],
                     [b'OK: killing emulator, bye bye', b'OK'])


class Supervisor:
    """Independent single guardian; no Android command defaults or normal CLI."""
    def __init__(self, *, owned, commands, observer, avd, serial, contract, caps, instrument=None,
                 launch_started=None):
        now = time.monotonic()
        if launch_started is None:
            launch_started = now  # Generic host users retain their construction anchor.
        if (type(launch_started) not in (int, float) or not math.isfinite(launch_started)
                or launch_started > now):
            raise Blocked()
        self._owned, self._commands, self._instrument = owned, commands, instrument
        self._observer, self._avd, self._serial = observer, avd, serial
        self._contract, self._caps = contract, caps
        self._condition = threading.Condition()
        self._finished = threading.Event()
        self._aborted = False
        self._accepting = True
        # The real caller captures this BEFORE owned spawn. Construction/adapter
        # latency may consume boot time, never restart its launch-bounded ceiling.
        self._phase_started = self._preparation_started = launch_started
        self._preparation_deadline = self._preparation_started + caps.preparation
        self._preparation_ceiling = self._preparation_deadline + caps.postboot_preparation
        self._boot_completed_at = None
        with self._observer._lock:
            self._initial_not_admitted = instrument is not None and (
                caps.postboot_preparation > 0 or not self._observer._admitted_locked())
        self._report = None
        self._output_taken = False
        if self._instrument is not None:
            self._instrument.subscribe(self._notify)
        self._observer.subscribe(self._notify)
        threading.Thread(target=self._guard, daemon=True).start()

    def preparation_remaining(self):
        """Admission clips to the active launch-bounded phase, never renews it."""
        with self._condition, self._observer._lock:
            source = self._observer.source
            if (not self._accepting or self._aborted or self._instrument is not None
                    or self._observer.own_anr or self._observer.unavailable
                    or source is not None and (source.done.is_set() or source.feed_lost.is_set()
                                              or source.cancelled or source.failure)):
                return 0
            return max(0, self._preparation_deadline - time.monotonic())

    def complete_boot(self):
        """One successful readiness handoff; expiry wins even before guard scheduling.

        The reserve is declared at launch. Only this transition may replace the
        active boot deadline; it cannot move the original total ceiling.
        """
        with self._condition, self._observer._lock:
            if (self._caps.postboot_preparation <= 0 or self._boot_completed_at is not None
                    or self.preparation_remaining() <= 0):
                return False
            now = time.monotonic()
            if now >= self._preparation_deadline:
                return False
            self._boot_completed_at = now
            self._preparation_deadline = min(self._preparation_ceiling,
                                             now + self._caps.postboot_preparation)
            self._condition.notify_all()
            return True

    def preparation_observation(self):
        """Fixed duration metadata only; never admission, native/guest liveness or proof."""
        with self._condition:
            now = time.monotonic()
            postboot = self._boot_completed_at is not None
            start = self._boot_completed_at if postboot else self._preparation_started
            budget = self._caps.postboot_preparation if postboot else self._caps.preparation
            return {'phase': 'POSTBOOT' if postboot else 'BOOT',
                    'phase_budget_ms': int(budget * 1000),
                    'phase_elapsed_ms': max(0, int((now - start) * 1000)),
                    'phase_remaining_ms': max(0, int((self._preparation_deadline - now) * 1000)),
                    'total_budget_ms': int((self._caps.preparation + self._caps.postboot_preparation) * 1000)}

    def wait_preparation(self, timeout, command=None):
        """Interruptible bounded command/condition wait inside original preparation.

        A command publishes its completion notification before the wait. With no
        command this is finite readiness backoff, not another lifecycle clock.
        Abort, observer loss, terminal guardian or original cap interrupts either.
        """
        deadline = min(self._preparation_deadline, time.monotonic() + timeout)
        if command is not None:
            command.subscribe(self._notify)
        with self._condition:
            while self.preparation_remaining() > 0:
                if command is not None and command.done.is_set():
                    return True
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    return command is None
                self._condition.wait(remaining)
            self._condition.notify_all()
            return False

    def attach_instrumentation(self, command):
        """Transition preparation -> instrumentation exactly once.

        Late/duplicate work is stopped, never attached to a completed lifecycle.
        Create the Command with its own runtime bound before handing it over.
        """
        with self._condition, self._observer._lock:
            now = time.monotonic()
            accepted = (self._accepting and self._instrument is None and not self._aborted
                        and (self._caps.postboot_preparation == 0 or self._boot_completed_at is not None)
                        and now < self._preparation_deadline
                        and self._observer._admitted_locked())
            if accepted:
                self._instrument = command
                self._phase_started = now
                command.subscribe(self._notify)
                self._condition.notify_all()
        if not accepted:
            _bounded(lambda: command.cancel(self._caps.settle), self._caps.settle)
            command.discard()
        return accepted

    def _notify(self):
        with self._condition:
            self._condition.notify_all()

    def abort(self):
        with self._condition:
            self._aborted = True
            self._condition.notify_all()

    def wait(self, timeout):
        return self._report if self._finished.wait(timeout) else None

    def _command(self, action, argument=None, timeout=None):
        cap = self._caps.command if timeout is None else min(timeout, self._caps.command)
        if cap <= 0:
            raise Blocked()
        deadline = time.monotonic() + cap
        ok, command = _bounded(lambda: self._commands.start(action, argument, cap, 65536), cap,
                               lambda child: child.cancel(self._caps.settle))
        if not ok or not isinstance(command, Command):
            raise Blocked()
        try:
            if not command.done.wait(max(0, deadline - time.monotonic())):
                raise Blocked()
            if command.failure or command.cancelled or command.buffered_bytes > 65536:
                raise Blocked()
            if (not command.settle(min(self._caps.settle, max(0, deadline - time.monotonic())))
                    or time.monotonic() >= deadline):
                raise Blocked()
            return command.returncode, *command.take_output()
        finally:
            if not command.done.is_set():
                _bounded(lambda: command.cancel(self._caps.settle), self._caps.settle)
            command.discard()

    def _work(self):
        contract = self._contract
        if (not isinstance(contract, LaunchContract) or contract.no_snapshot_save is not True
                or not isinstance(contract.watchdog_id, str)
                or not re.fullmatch(r'[A-Za-z0-9_-]{1,128}', contract.watchdog_id)
                or not isinstance(self._avd, str) or not re.fullmatch(r'[A-Za-z0-9_.-]{1,128}', self._avd)
                or not isinstance(self._serial, str) or not re.fullmatch(r'emulator-[0-9]{4,5}', self._serial)
                or getattr(self._owned, 'anchor', None) is None):
            return 'CONTRACT_INVALID'
        if self._initial_not_admitted:
            return 'OBSERVER_NOT_ADMITTED'
        with self._condition:
            while True:
                # Terminal observer state wins even if a work completion races it.
                if self._observer.own_anr:
                    return 'OWN_ANR'
                source = self._observer.source
                if (self._observer.unavailable or source is not None and
                        (source.feed_lost.is_set() or source.done.is_set() or source.cancelled or source.failure)):
                    return 'OBSERVER_FAILED'
                if self._aborted:
                    return 'ABORTED'
                preparing = self._instrument is None
                if not preparing and self._instrument.cancelled:
                    return 'INSTRUMENT_CANCELLED'
                if not preparing and self._instrument.done.is_set():
                    return 'INSTRUMENT_FAILED' if self._instrument.failure else 'NATURAL_COMPLETION'
                deadline = self._preparation_deadline if preparing else self._phase_started + self._caps.instrumentation
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    return 'PREPARATION_CAP' if preparing else 'INSTRUMENTATION_CAP'
                self._condition.wait(remaining)

    def _teardown(self):
        caps = self._caps
        failures = []
        if not isinstance(self._contract, LaunchContract) or self._contract.no_snapshot_save is not True:
            failures.append('NO_SAVE_UNCONFIRMED')
        snapshots_empty = disconnect = absence = terminal = False

        def step(name, call, timeout):
            ok, value = _bounded(call, timeout)
            if not ok or value is not True:
                failures.append(name)
                return False
            return True

        # Work and event source stop independently, before console cleanup.
        if self._instrument is not None:
            step('INSTRUMENT_STOP', lambda: self._instrument.cancel(caps.settle)
                 if not self._instrument.done.is_set() else self._instrument.settle(caps.settle), caps.settle)
        step('OBSERVER_STOP', lambda: self._observer.stop(caps.settle), caps.settle)

        owned_identity = False
        try:
            code, out, err = self._command('identity')
            owned_identity = (code == 0 and not err and
                              [v for v in out.decode('ascii').splitlines() if v != ''] == [self._avd, 'OK'])
        except BaseException:
            pass
        if not owned_identity:
            failures.append('IDENTITY_UNCONFIRMED')
        if owned_identity:
            deadline = time.monotonic() + caps.snapshot_budget
            try:
                def snapshot_command(action, argument=None):
                    code, out, err = self._command(action, argument, timeout=deadline - time.monotonic())
                    if code != 0 or err:
                        raise Blocked()
                    return out
                names = _snapshots(snapshot_command('snapshot_list'))
                for name in names:
                    reply = snapshot_command('snapshot_delete', name)
                    if [v for v in reply.splitlines() if v] != [b'OK']:
                        raise Blocked()
                snapshots_empty = not _snapshots(snapshot_command('snapshot_list'))
                if not snapshots_empty:
                    raise Blocked()
            except BaseException:
                failures.append('SNAPSHOTS_UNCONFIRMED')
            try:
                code, out, err = self._command('console_stop')
                if code != 0 or err or not _console_stop_ack(out):
                    raise Blocked()
            except BaseException:
                failures.append('CONSOLE_STOP_UNCONFIRMED')

        # Even failed identity/snapshots/console handling cannot skip original tree.
        _bounded(lambda: self._owned.wait(caps.process_wait), caps.process_wait)
        # The original launcher may already have exited with live descendants.
        # stop_tree's True MUST mean confirmed anchored tree settlement, not merely
        # an accepted TerminateJobObject/CloseHandle/console request.
        step('ORIGINAL_TREE_STOP', lambda: self._owned.stop_tree(caps.stop), caps.stop)
        ok, value = _bounded(lambda: self._owned.wait(caps.process_wait), caps.process_wait)
        terminal = ok and value is True
        if not terminal:
            failures.append('ORIGINAL_TERMINAL_UNCONFIRMED')
        try:
            code, _, err = self._command('disconnect')
            disconnect = code == 0 and not err
        except BaseException:
            pass
        if not disconnect:
            failures.append('DISCONNECT_UNCONFIRMED')
        # Disconnect can describe an offline transport. Observe the independent
        # strict condition within ONE existing absence-command budget; retries and
        # settlement never renew it. Nonstrict replies are pending, never proof.
        deadline = time.monotonic() + caps.command
        retry = threading.Event()
        try:
            while time.monotonic() < deadline:
                code, out, err = self._command('absence', timeout=deadline - time.monotonic())
                absence = (code != 0 and not out.strip() and
                           err.strip() == ("error: device '" + self._serial + "' not found").encode('ascii'))
                if time.monotonic() >= deadline:
                    absence = False
                    break
                if absence:
                    break
                # Fixed finite condition backoff, bounded by the SAME budget.
                retry.wait(min(.25, max(0, deadline - time.monotonic())))
        except BaseException:
            pass
        if not absence:
            failures.append('ABSENCE_UNCONFIRMED')
        return terminal, snapshots_empty, disconnect, absence, tuple(failures)

    def _guard(self):
        cause = 'SUPERVISION_FAILED'
        try:
            cause = self._work()
        except BaseException:
            pass
        with self._condition:
            self._accepting = False
        try:
            terminal, snapshots, disconnect, absence, failures = self._teardown()
        except BaseException:
            terminal = snapshots = disconnect = absence = False
            failures = ('TEARDOWN_FAILED',)
        clean = all((terminal, snapshots, disconnect, absence)) and not failures
        if cause == 'NATURAL_COMPLETION':
            if self._observer.own_anr:
                cause = 'OWN_ANR'
            elif self._observer.unavailable:
                cause = 'OBSERVER_FAILED'
            elif self._instrument.cancelled:
                cause = 'INSTRUMENT_CANCELLED'
            elif self._aborted:
                cause = 'ABORTED'
        natural = cause == 'NATURAL_COMPLETION'
        status = 'NATURAL_COMPLETION' if clean and natural else ('UNKNOWN' if clean else 'BLOCKED')
        if cause in ('CONTRACT_INVALID', 'OBSERVER_NOT_ADMITTED'):
            status = 'BLOCKED'
        eligible = clean and natural
        if not eligible and self._instrument is not None:
            self._instrument.discard()
        self._report = Report(status, cause, clean, eligible, terminal, snapshots, disconnect, absence,
                              failures, self._instrument.returncode if eligible else None)
        self._finished.set()

    def take_instrumentation_output(self):
        if (not self._finished.is_set() or not self._report.eligible_for_oracle
                or self._output_taken):
            raise Blocked()
        self._output_taken = True
        return self._instrument.take_output()
