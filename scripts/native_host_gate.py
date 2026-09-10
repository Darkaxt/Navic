#!/usr/bin/env python3
"""Task359: full iOS app compilation + original-file Native monitor host bench.

No simulator is launched. Only receipt.json is publishable; subprocess output is
held in memory and discarded after structural parsing. Kotlin 2.4.0 logger
contract: kotlin-native/runtime/src/main/kotlin/kotlin/native/internal/test/
TestLogger.kt (SimpleTestLogger/BaseTestLogger) and TestSuite.kt at v2.4.0.
"""
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import signal
import subprocess
import sys
import tempfile
import time
import zipfile

PREFIX = 'composeApp/src/'
SOURCES = tuple(PREFIX + p for p in (
    'commonMain/kotlin/paige/navic/reader/ReaderRelocationMonitor.kt',
    'iosMain/kotlin/paige/navic/reader/ReaderRelocationMonitor.ios.kt',
    'commonTest/kotlin/paige/navic/reader/ReaderQueueTestWorkers.kt',
    'iosTest/kotlin/paige/navic/reader/ReaderQueueTestWorkers.ios.kt',
    'commonTest/kotlin/paige/navic/reader/ReaderRelocationMonitorTest.kt',
))
COMMON_SOURCES = tuple(p for p in SOURCES if '/common' in p)
SUITE = 'paige.navic.reader.ReaderRelocationMonitorTest'
CASES = (
    'recursiveAcquisitionPreservesReturnValue',
    'nestedExceptionPreservesIdentityAndUnlocksForOtherThreads',
    'contendingThreadsCannotLoseUpdates',
    'separateInstancesDoNotShareAGlobalLock',
)
TASK = ':composeApp:compileKotlinIosSimulatorArm64'
STAGES = ('native_app_compile', 'ios_probe_compile', 'macos_probe_compile', 'native4testexecution')
VERSION = '2.4.0'


class GateError(Exception):
    """Messages are fixed categories, never raw compiler/test output."""


def require(condition, category):
    if not condition:
        raise GateError(category)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def parse_listing(output):
    # BaseTestLogger prints suite.name + '.', then two-space testCase.name.
    suites, names = [], []
    for line in output.splitlines():
        if re.fullmatch(r'[A-Za-z_][\w.]*\.', line):
            suites.append(line)
        elif line.startswith('  '):
            names.append(line[2:])
    require(suites == [SUITE + '.'] and len(names) == 4 and set(names) == set(CASES), 'discovery_mismatch')
    return len(names)


def parse_execution(output):
    # SIMPLE uses "$name ($suite)". Unknown lines are dropped, but every
    # recognized lifecycle record must occur exactly once in the right order.
    prefixes = ('Starting testing', 'Testing finished', 'Starting iteration:',
                'Iteration finished:', 'Starting test suite:', 'Test suite finished:',
                'Test suite ignored:', 'Starting test case:', 'Passed:', 'Ignore:', 'Failed:')
    records = [line for line in output.splitlines() if line.startswith(prefixes)]
    require(records[:3] == ['Starting testing', 'Starting iteration: 1', 'Starting test suite: ' + SUITE], 'execution_mismatch')
    require(records[-3:] == ['Test suite finished: ' + SUITE, 'Iteration finished: 1', 'Testing finished'], 'execution_mismatch')
    middle = records[3:-3]
    require(len(middle) == 8, 'execution_mismatch')
    names = []
    for start, passed in zip(middle[::2], middle[1::2]):
        name = next((case for case in CASES if start == f'Starting test case: {case} ({SUITE})'), None)
        require(name is not None and passed == f'Passed: {name} ({SUITE})', 'execution_mismatch')
        names.append(name)
    require(len(set(names)) == 4, 'execution_mismatch')
    return dict(executed=4, passed=4, skipped=0, failed=0, errors=0)


def parse_version(output):
    versions = re.findall(r'(?m)^(?:info: )?kotlinc-native ([^\s]+)(?:\s|$)', output)
    require(versions == [VERSION], 'compiler_version_mismatch')
    return VERSION


def verify_klib(artifact, target):
    """Validate the emitted Native ABI, not just the requested command target."""
    if artifact.is_dir():
        manifest = (artifact / 'default/manifest').read_text(encoding='utf-8')
    else:
        with zipfile.ZipFile(artifact) as archive:
            manifest = archive.read('default/manifest').decode('utf-8')
    targets = re.findall(r'(?m)^native_targets=(.*)$', manifest)
    versions = re.findall(r'(?m)^compiler_version=(.*)$', manifest)
    require(targets == [target] and versions == [VERSION], 'klib_provenance_mismatch')
    return dict(target=target, compiler_version=VERSION)


def check_task_output(output):
    records = [line for line in output.splitlines() if line.startswith('> Task ' + TASK)]
    require(records == ['> Task ' + TASK], 'app_task_not_executed')


def diagnostics(output):
    """Retain only original public filenames, coordinates and fixed categories."""
    names = {Path(p).name for p in SOURCES}
    categories = (('unresolved reference', 'unresolved_reference'),
                  ('actual', 'expect_actual'), ('type mismatch', 'type_mismatch'),
                  ('error', 'compiler_error'), ('warning', 'compiler_warning'))
    result = []
    for line in output.splitlines():
        match = re.search(r'([A-Za-z][A-Za-z0-9.]*\.kt):(\d+):(\d+):', line)
        if not match or match[1] not in names:
            continue
        category = next((value for key, value in categories if key in line.lower()), 'compiler_diagnostic')
        item = dict(file=match[1], line=int(match[2]), column=int(match[3]), category=category)
        if item not in result:
            result.append(item)
        if len(result) == 32:
            break
    return result


def git(root, *args):
    result = subprocess.run(['git', '-C', str(root), *args], capture_output=True, timeout=30,
                            env={**os.environ, 'GIT_OPTIONAL_LOCKS': '0'})
    require(result.returncode == 0, 'git_identity_failed')
    return result.stdout


def snapshot(root, originals=SOURCES):
    head = git(root, 'rev-parse', 'HEAD').decode().strip()
    require(re.fullmatch(r'[0-9a-f]{40}', head), 'invalid_head')
    paths = sorted(p.decode('utf-8') for p in git(root, 'ls-files', '-z').split(b'\0') if p)
    digest = hashlib.sha256()
    for name in paths:
        path = root / name
        require(path.is_file(), 'tracked_file_missing')
        digest.update(name.encode() + b'\0' + hashlib.sha256(path.read_bytes()).digest())
    index = Path(git(root, 'rev-parse', '--git-path', 'index').decode().strip())
    if not index.is_absolute():
        index = root / index
    return dict(head=head, tracked_count=len(paths), tracked_sha256=digest.hexdigest(),
                index_sha256=sha(index.read_bytes()),
                original_sha256={p: sha((root / p).read_bytes()) for p in originals})


def make_workspace(root, runner_temp):
    root, runner_temp = root.resolve(), runner_temp.resolve()
    require(runner_temp.is_dir() and not runner_temp.is_relative_to(root), 'workspace_inside_checkout')
    return Path(tempfile.mkdtemp(prefix='task359-native-', dir=runner_temp))


def probe_command(compiler, root, output, target):
    require(target in ('ios_simulator_arm64', 'macos_arm64'), 'invalid_target')
    cmd = [str(compiler), '-target', target, '-produce', 'program' if target == 'macos_arm64' else 'library',
           '-Xmulti-platform', '-Xexpect-actual-classes',
           '-Xcommon-sources=' + ','.join(str(root / p) for p in COMMON_SOURCES), '-output', str(output)]
    if target == 'macos_arm64':
        cmd.append('-generate-test-runner')
    return cmd + [str(root / p) for p in SOURCES]


class Commands:
    def __init__(self, root, receipt, save):
        self.root, self.receipt, self.save = root, receipt, save
        self.deadline = time.monotonic() + 2400

    def run(self, name, cmd, timeout, env=None):
        started = time.monotonic()
        timeout = min(timeout, self.deadline - started)
        require(timeout > 0, 'gate_deadline')
        entry = dict(status='running', timeout_seconds=timeout)
        self.receipt['commands'][name] = entry
        self.save()
        proc = None
        try:
            proc = subprocess.Popen(cmd, cwd=self.root, env=env, stdout=subprocess.PIPE,
                                    stderr=subprocess.STDOUT, start_new_session=os.name == 'posix')
            output, _ = proc.communicate(timeout=timeout)
            text = output.decode('utf-8', errors='replace')
            entry.update(exit_code=proc.returncode, diagnostics=diagnostics(text))
            entry['status'] = 'passed' if proc.returncode == 0 else 'failed'
            require(proc.returncode == 0, 'command_failed')
            return text
        except subprocess.TimeoutExpired:
            entry['status'] = 'timeout'
            raise GateError('command_timeout') from None
        except (KeyboardInterrupt, InterruptedError):
            entry['status'] = 'interrupted'
            raise
        finally:
            if proc is not None and proc.poll() is None:
                if os.name == 'posix':
                    os.killpg(proc.pid, signal.SIGKILL)
                else:
                    proc.kill()
                proc.communicate()
                entry['exit_code'] = proc.returncode
            if entry['status'] == 'running':
                entry['status'] = 'failed'
            entry['elapsed_seconds'] = round(time.monotonic() - started, 3)
            self.save()


# No source-set changes: all original app Kotlin files must be present in the
# real task's compiler inputs. Gradle 2.4.0 KotlinNativeCompile exposes sources,
# konanHome and outputFile. Only build/output locations are redirected.
INIT = r'''
import groovy.json.JsonOutput
import java.security.MessageDigest

def out = new File(System.getenv('TASK359_WORK'))
gradle.beforeProject { p ->
    p.layout.buildDirectory.set(new File(out, 'build/' + (p.path == ':' ? 'root' : p.path.substring(1).replace(':', '/'))))
}
gradle.projectsEvaluated {
    def p = gradle.rootProject.project(':composeApp')
    def t = p.tasks.getByName('compileKotlinIosSimulatorArm64')
    def expected = new File(out, 'app-membership.txt').readLines('UTF-8')
    t.doFirst {
        def actual = t.sources.files.collect { it.canonicalPath }.toSet()
        if (expected.isEmpty() || !expected.every { actual.contains(new File(it).canonicalPath) }) {
            throw new GradleException('task359_original_membership_missing')
        }
        new File(out, 'app-started.json').text = JsonOutput.toJson([
            source_count: expected.size(),
            membership_sha256: MessageDigest.getInstance('SHA-256').digest(expected.join('\n').getBytes('UTF-8')).encodeHex().toString(),
            konan_home: new File(t.konanHome.get()).canonicalPath,
            output_file: t.outputFile.get().canonicalPath
        ])
    }
    t.doLast {
        new File(out, 'app-completed').text = 'completed'
    }
}
'''


def app_command(root, work):
    return [str(root / 'gradlew'), TASK, '-x', ':composeApp:exportLibraryDefinitions',
            '--init-script', str(work / 'native-host.init.gradle'),
            '--project-cache-dir', str(work / 'project-cache'), '--rerun-tasks', '--no-build-cache',
            '--no-configuration-cache', '--console=plain', '--no-daemon',
            '-Pkotlin.compiler.execution.strategy=in-process', '-Pkotlin.incremental=false']


def run_gate(root, work, receipt, save):
    before = snapshot(root)
    receipt['before'] = before
    save()
    try:
        require(platform.system() == 'Darwin' and platform.machine() == 'arm64', 'requires_macos_arm64')
        require(os.environ.get('GITHUB_EVENT_NAME') == 'workflow_dispatch' and os.environ.get('GITHUB_REF', '').startswith('refs/heads/'), 'requires_branch_dispatch')
        require(before['head'] == os.environ.get('GITHUB_SHA'), 'checkout_sha_mismatch')
        require(not git(root, 'diff', '--name-only') and not git(root, 'diff', '--cached', '--name-only'), 'checkout_not_clean')
        catalog = (root / 'gradle/libs.versions.toml').read_text()
        require(re.search(r'(?m)^kotlin\s*=\s*"2\.4\.0"\s*$', catalog), 'catalog_version_mismatch')
        receipt['host'] = dict(os='macos', architecture='arm64', kotlin_catalog=VERSION)
        commands = Commands(root, receipt, save)
        for sdk in ('iphonesimulator', 'macosx'):
            sdk_path = Path(commands.run(sdk + '_path', ['xcrun', '--sdk', sdk, '--show-sdk-path'], 30).strip())
            sdk_version = commands.run(sdk + '_version', ['xcrun', '--sdk', sdk, '--show-sdk-version'], 30).strip()
            require(sdk_path.is_dir() and re.fullmatch(r'\d+(?:\.\d+){1,2}', sdk_version), 'sdk_invalid')
            receipt['host'][sdk] = dict(version=sdk_version, path_sha256=sha(str(sdk_path).encode()))
        xcode = commands.run('xcode_version', ['xcodebuild', '-version'], 30)
        match = re.fullmatch(r'Xcode (\d+(?:\.\d+){1,2})\nBuild version ([A-Za-z0-9]+)\s*', xcode)
        require(match is not None, 'xcode_version_invalid')
        receipt['host']['xcode'] = dict(version=match[1], build=match[2])
        jdk = commands.run('jdk_version', ['java', '-version'], 30)
        require(re.search(r'version "21(?:\.|\")', jdk), 'jdk_version_invalid')
        receipt['host']['jdk_major'] = 21
        tracked = git(root, 'ls-files', '-z').decode().split('\0')
        main_roots = tuple(PREFIX + p + '/' for p in ('commonMain', 'iosMain', 'iosSimulatorArm64', 'nativeMain', 'appleMain'))
        members = sorted(str((root / p).resolve()) for p in tracked if p.startswith(main_roots) and p.endswith('.kt'))
        require(all(str((root / p).resolve()) in members for p in SOURCES[:2]), 'monitor_app_membership_missing')
        (work / 'app-membership.txt').write_text('\n'.join(members), encoding='utf-8')
        (work / 'native-host.init.gradle').write_text(INIT, encoding='utf-8')
        receipt['stages']['native_app_compile'] = 'running'
        env = {**os.environ, 'TASK359_WORK': str(work)}
        output = commands.run('native_app_compile', app_command(root, work), 1800, env)
        check_task_output(output)
        require((work / 'app-completed').read_text() == 'completed', 'app_completion_missing')
        proof = json.loads((work / 'app-started.json').read_text())
        require(proof['source_count'] == len(members) and proof['membership_sha256'] == sha('\n'.join(members).encode()), 'app_membership_mismatch')
        artifact = Path(proof['output_file']).resolve()
        require(artifact.is_relative_to(work) and artifact.exists(), 'app_output_missing')
        receipt['app_klib'] = verify_klib(artifact, 'ios_simulator_arm64')
        receipt['app_membership'] = dict(original_kotlin_count=len(members), original_membership_sha256=proof['membership_sha256'], original_inputs_verified=True)
        receipt['stages']['native_app_compile'] = 'passed'
        save()
        home = Path(proof['konan_home']).resolve()
        compiler = home / 'bin/kotlinc-native'
        require(compiler.is_file() and home.name == 'kotlin-native-prebuilt-macos-aarch64-' + VERSION, 'compiler_distribution_mismatch')
        compiler_version = parse_version(commands.run('compiler_version', [str(compiler), '-version'], 30))
        receipt['compiler'] = dict(version=compiler_version, distribution=home.name, launcher_sha256=sha(compiler.read_bytes()), path_sha256=sha(str(home).encode()))
        for target, stage, basename, suffix in (
            ('ios_simulator_arm64', 'ios_probe_compile', 'reader-monitor-ios', '.klib'),
            ('macos_arm64', 'macos_probe_compile', 'reader-monitor', '.kexe'),
        ):
            receipt['stages'][stage] = 'running'
            commands.run(stage, probe_command(compiler, root, work / basename, target), 240)
            artifact = work / (basename + suffix)
            require(artifact.is_file() and artifact.stat().st_size > 0, 'probe_output_missing')
            receipt[stage] = dict(target=target, artifact_sha256=sha(artifact.read_bytes()), original_source_count=5)
            if target == 'ios_simulator_arm64':
                receipt[stage]['klib'] = verify_klib(artifact, target)
            receipt['stages'][stage] = 'passed'
            save()
        executable = work / 'reader-monitor.kexe'
        architecture = commands.run('executable_architecture', ['lipo', '-archs', str(executable)], 15).strip()
        require(architecture == 'arm64', 'executable_architecture_mismatch')
        receipt['stages']['native4testexecution'] = 'running'
        discovered = parse_listing(commands.run('native_test_discovery', [str(executable), '--ktest_list_tests'], 15))
        receipt['tests'] = dict(discovered=discovered)
        save()
        counts = parse_execution(commands.run('native_test_execution', [str(executable), '--ktest_logger=SIMPLE'], 60))
        receipt['tests'].update(counts, suite=SUITE, cases=list(CASES), architecture=architecture)
        receipt['stages']['native4testexecution'] = 'passed'
    finally:
        after = snapshot(root)
        receipt['after'] = after
        receipt['source_integrity'] = 'passed' if before == after else 'failed'
        save()
        require(before == after, 'source_integrity_mismatch')


def main():
    root = Path(__file__).resolve().parents[1]
    receipt = dict(schema=1, task='Task359', overall='incomplete',
                   stages={stage: 'not_run' for stage in STAGES}, commands={},
                   coverage='native_monitor_host_bench_not_runtime_acceptance',
                   omissions=['ios_simulator_execution', 'ios_device_execution', 'native_queue_suites',
                              'whole_project_native_tests_Task360_28_java_import_files',
                              'whole_app_runtime', 'ios_runtime', 'signing_archive_release',
                              'acknowledgements_regeneration'],
                   qualifications=['committed_acknowledgements_consumed_exportLibraryDefinitions_excluded',
                                   'project_source_sets_unmodified', 'existing_jvm_queue_gate_is_separate'])
    receipt_path = None
    def save():
        if receipt_path is not None:
            temporary = receipt_path.with_suffix('.tmp')
            temporary.write_text(json.dumps(receipt, indent=2, sort_keys=True) + '\n', encoding='utf-8')
            temporary.replace(receipt_path)
    def interrupt(_signum, _frame):
        raise InterruptedError()
    signal.signal(signal.SIGTERM, interrupt)
    try:
        temp = Path(os.environ['RUNNER_TEMP']).resolve()
        work = make_workspace(root, temp)
        receipt_dir = temp / 'task359-receipt'
        receipt_dir.mkdir(exist_ok=False)
        receipt_path = receipt_dir / 'receipt.json'
        save()
        run_gate(root, work, receipt, save)
        require(all(value == 'passed' for value in receipt['stages'].values()) and receipt.get('source_integrity') == 'passed', 'incomplete_gate')
        receipt['overall'] = 'passed'
        return_code = 0
    except (KeyboardInterrupt, InterruptedError):
        receipt['overall'] = 'interrupted'
        receipt['failure_category'] = 'interrupted'
        return_code = 130
    except Exception as exc:
        receipt['overall'] = 'failed'
        # GateError messages are internal fixed categories. Never serialize other
        # exception messages, command arguments, output, environment, or stacks.
        receipt['failure_category'] = str(exc) if isinstance(exc, GateError) else 'host_gate_internal_error'
        return_code = 1
    finally:
        for stage, status in receipt['stages'].items():
            if status == 'running':
                receipt['stages'][stage] = receipt['overall']
        save()
    print('Task359 native host gate: ' + receipt['overall'])
    return return_code


if __name__ == '__main__':
    sys.exit(main())
