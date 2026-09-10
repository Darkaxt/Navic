"""Portable synthetic/source contracts; these are NOT Kotlin/Native execution."""
import ast
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / 'scripts/native_host_gate.py'
BASE = 'b9ab62ca4a8775cf7993d59ad45919b8f10a7068'
SUITE = 'paige.navic.reader.ReaderRelocationMonitorTest'
CASES = (
    'recursiveAcquisitionPreservesReturnValue',
    'nestedExceptionPreservesIdentityAndUnlocksForOtherThreads',
    'contendingThreadsCannotLoseUpdates',
    'separateInstancesDoNotShareAGlobalLock',
)


def load_runner(test):
    test.assertTrue(RUNNER.is_file(), 'Task359 runner is missing (synthetic RED)')
    spec = importlib.util.spec_from_file_location('native_host_gate', RUNNER)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def listing():
    return SUITE + '.\n' + ''.join('  ' + name + '\n' for name in CASES)


def passing():
    lines = ['Starting testing', 'Starting iteration: 1', 'Starting test suite: ' + SUITE]
    for name in CASES:
        lines += [f'Starting test case: {name} ({SUITE})', f'Passed: {name} ({SUITE})']
    lines += ['Test suite finished: ' + SUITE, 'Iteration finished: 1', 'Testing finished']
    return '\n'.join(lines)


def evaluate_guard(expr, values):
    """Tiny allowlisted boolean AST interpreter, never Python eval."""
    expr = expr.removeprefix('${{').removesuffix('}}').strip()
    for key, value in values.items():
        expr = expr.replace(key, repr(value))
    expr = re.sub(r'!(?!=)', ' not ', expr.replace('&&', ' and ').replace('||', ' or ')).strip()
    def visit(node):
        if isinstance(node, ast.Constant):
            return node.value
        if isinstance(node, ast.BoolOp):
            return (all if isinstance(node.op, ast.And) else any)(visit(v) for v in node.values)
        if isinstance(node, ast.UnaryOp) and isinstance(node.op, ast.Not):
            return not visit(node.operand)
        if isinstance(node, ast.Compare) and len(node.ops) == 1 and isinstance(node.ops[0], ast.Eq):
            return visit(node.left) == visit(node.comparators[0])
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Name) and len(node.args) == 2:
            a, b = map(visit, node.args)
            if node.func.id == 'startsWith':
                return a.startswith(b)
            if node.func.id == 'contains':
                return b in a
        raise AssertionError('Unsupported workflow predicate')
    return bool(visit(ast.parse(expr, mode='eval').body))


class RunnerContracts(unittest.TestCase):
    def test_original_five_inputs_and_four_declared_tests(self):
        gate = load_runner(self)
        self.assertEqual(5, len(gate.SOURCES))
        self.assertEqual(3, len(gate.COMMON_SOURCES))
        self.assertEqual(set(CASES), set(gate.CASES))
        for name in gate.SOURCES:
            current = (ROOT / name).read_bytes()
            old = subprocess.check_output(['git', '-C', str(ROOT), 'show', f'{BASE}:{name}'])
            self.assertEqual(hashlib.sha256(old).digest(), hashlib.sha256(current).digest())

    def test_exact_discovery(self):
        gate = load_runner(self)
        self.assertEqual(4, gate.parse_listing(listing()))
        for bad in ('', listing() + '  extra\n', listing() + '  ' + CASES[0], listing().replace(CASES[0], 'unknown'), listing().replace(SUITE, 'OtherSuite')):
            with self.subTest(), self.assertRaises(gate.GateError):
                gate.parse_listing(bad)

    def test_exact_execution(self):
        gate = load_runner(self)
        self.assertEqual({'executed': 4, 'passed': 4, 'skipped': 0, 'failed': 0, 'errors': 0}, gate.parse_execution(passing()))
        for bad in ('', passing().replace('Passed:', 'Ignore:', 1), passing().replace('Passed:', 'Failed:', 1), passing().replace('Testing finished', ''), passing() + '\nPassed: ' + CASES[0] + f' ({SUITE})', passing().replace('Starting iteration: 1', 'Starting iteration: 2'), passing().replace(CASES[0], 'unknown')):
            with self.subTest(), self.assertRaises(gate.GateError):
                gate.parse_execution(bad)

    def test_unknown_output_never_persisted(self):
        gate = load_runner(self)
        marker = 'SYNTHETIC_PRIVATE_SENTINEL'
        self.assertEqual(4, gate.parse_listing(listing() + '\n' + marker))
        self.assertEqual(4, gate.parse_execution(passing() + '\n' + marker)['passed'])
        safe = gate.diagnostics(f'e: /tmp/ReaderRelocationMonitor.kt:12:3: Unresolved reference {marker}\n{marker}')
        self.assertNotIn(marker, json.dumps(safe))
        self.assertEqual([{'file': 'ReaderRelocationMonitor.kt', 'line': 12, 'column': 3, 'category': 'unresolved_reference'}], safe)

    def test_compiler_version_is_exact(self):
        gate = load_runner(self)
        self.assertEqual('2.4.0', gate.parse_version('info: kotlinc-native 2.4.0 (JRE 21)'))
        for bad in ('2.4.0', 'info: kotlinc-native 2.3.0 (JRE 21)', 'info: kotlinc-native 2.4.0-dev-1 (JRE 21)'):
            with self.assertRaises(gate.GateError):
                gate.parse_version(bad)

    def test_task_must_really_execute(self):
        gate = load_runner(self)
        task = ':composeApp:compileKotlinIosSimulatorArm64'
        gate.check_task_output('> Task ' + task)
        for suffix in (' SKIPPED', ' FROM-CACHE', ' UP-TO-DATE', ' NO-SOURCE'):
            with self.assertRaises(gate.GateError):
                gate.check_task_output('> Task ' + task + suffix)
        with self.assertRaises(gate.GateError):
            gate.check_task_output('BUILD SUCCESSFUL')

    def test_external_unique_workspace(self):
        gate = load_runner(self)
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / 'checkout'
            root.mkdir()
            with self.assertRaises(gate.GateError):
                gate.make_workspace(root, root)
            a = gate.make_workspace(root, Path(temp))
            b = gate.make_workspace(root, Path(temp))
            self.assertNotEqual(a, b)
            self.assertFalse(a.is_relative_to(root))

    def test_probe_commands_use_original_files_no_overlay(self):
        gate = load_runner(self)
        for target, kind in (('ios_simulator_arm64', 'library'), ('macos_arm64', 'program')):
            cmd = gate.probe_command(Path('/compiler/bin/kotlinc-native'), ROOT, Path('/external/probe'), target)
            self.assertIn(target, cmd)
            self.assertIn(kind, cmd)
            self.assertEqual(target == 'macos_arm64', '-generate-test-runner' in cmd)
            self.assertEqual([str(ROOT / p) for p in gate.SOURCES], cmd[-5:])
            common = next(x for x in cmd if x.startswith('-Xcommon-sources='))
            self.assertEqual('-Xcommon-sources=' + ','.join(str(ROOT / p) for p in gate.COMMON_SOURCES), common)

    def test_subprocess_failure_and_timeout_do_not_echo_output(self):
        gate = load_runner(self)
        with tempfile.TemporaryDirectory() as temp:
            receipt = {'commands': {}}
            run = gate.Commands(Path(temp), receipt, lambda: None)
            with self.assertRaises(gate.GateError):
                run.run('synthetic_failure', [sys.executable, '-c', "print('SYNTHETIC_PRIVATE_SENTINEL'); raise SystemExit(7)"], 5)
            self.assertEqual(7, receipt['commands']['synthetic_failure']['exit_code'])
            self.assertNotIn('SYNTHETIC_PRIVATE_SENTINEL', json.dumps(receipt))
            with self.assertRaises(gate.GateError):
                run.run('synthetic_timeout', [sys.executable, '-c', 'import time; time.sleep(5)'], 0.1)
            self.assertEqual('timeout', receipt['commands']['synthetic_timeout']['status'])

    def test_klib_requires_real_target_and_compiler_version(self):
        gate = load_runner(self)
        import zipfile
        self.assertTrue(hasattr(gate, 'verify_klib'), 'Klib target provenance missing (synthetic RED)')
        with tempfile.TemporaryDirectory() as temp:
            klib = Path(temp) / 'probe.klib'
            for target, version, accepted in (('ios_simulator_arm64', '2.4.0', True), ('ios_arm64', '2.4.0', False), ('ios_simulator_arm64', '2.3.0', False)):
                with zipfile.ZipFile(klib, 'w') as archive:
                    archive.writestr('default/manifest', f'native_targets={target}\ncompiler_version={version}\n')
                if accepted:
                    self.assertEqual({'target': target, 'compiler_version': version}, gate.verify_klib(klib, target))
                else:
                    with self.assertRaises(gate.GateError):
                        gate.verify_klib(klib, 'ios_simulator_arm64')

    def test_app_compile_is_full_target_with_external_outputs(self):
        gate = load_runner(self)
        cmd = gate.app_command(ROOT, Path('/external/work'))
        for token in (':composeApp:compileKotlinIosSimulatorArm64', '--rerun-tasks', '--no-build-cache', '--no-configuration-cache', '--no-daemon', '--project-cache-dir', ':composeApp:exportLibraryDefinitions'):
            self.assertIn(token, cmd)
        for token in ('setSource(', 'exclude(', 'include(', 'compileTestKotlin', 'sourceSets'):
            self.assertNotIn(token, gate.INIT)
        for token in ('t.sources.files', 't.konanHome.get()', 't.outputFile.get()', 't.doFirst', 't.doLast', 'p.layout.buildDirectory.set'):
            self.assertIn(token, gate.INIT)

    def test_interruption_retains_metadata_without_output(self):
        gate = load_runner(self)
        from unittest.mock import patch
        receipt = {'commands': {}}
        run = gate.Commands(ROOT, receipt, lambda: None)
        with patch.object(gate.subprocess, 'Popen', side_effect=InterruptedError):
            with self.assertRaises(InterruptedError):
                run.run('synthetic_interrupt', ['unused'], 5)
        self.assertEqual('interrupted', receipt['commands']['synthetic_interrupt']['status'])

    def test_failed_setup_retains_receipt_without_native_claim(self):
        gate = load_runner(self)
        from unittest.mock import patch
        import contextlib
        import io
        with tempfile.TemporaryDirectory() as temp:
            with patch.dict(gate.os.environ, {'RUNNER_TEMP': temp}), patch.object(gate, 'run_gate', side_effect=gate.GateError('requires_macos_arm64')), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(1, gate.main())
            receipt = json.loads((Path(temp) / 'task359-receipt/receipt.json').read_text())
            self.assertEqual('failed', receipt['overall'])
            self.assertEqual({'not_run'}, set(receipt['stages'].values()))
            self.assertIn('native_queue_suites', receipt['omissions'])
            self.assertNotIn('tests', receipt)

    def test_snapshot_detects_content_head_and_index(self):
        gate = load_runner(self)
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / 'a.kt').write_text('first')
            (root / 'fixture-index').write_bytes(b'original-index')
            replies = {('rev-parse', 'HEAD'): b'a' * 40,
                       ('ls-files', '-z'): b'a.kt\0',
                       ('rev-parse', '--git-path', 'index'): b'fixture-index'}
            with patch.object(gate, 'git', side_effect=lambda _root, *args: replies[args]):
                before = gate.snapshot(root, originals=())
                (root / 'a.kt').write_text('second')
                self.assertNotEqual(before['tracked_sha256'], gate.snapshot(root, originals=())['tracked_sha256'])
                (root / 'fixture-index').write_bytes(b'changed-index')
                self.assertNotEqual(before['index_sha256'], gate.snapshot(root, originals=())['index_sha256'])
                replies[('rev-parse', 'HEAD')] = b'b' * 40
                self.assertNotEqual(before['head'], gate.snapshot(root, originals=())['head'])


class WorkflowContracts(unittest.TestCase):
    def setUp(self):
        self.text = (ROOT / '.github/workflows/build.yml').read_bytes().decode('utf-8')

    def test_dispatch_input_and_guard_missing_red(self):
        self.assertTrue('      native_host_only:\n' in self.text, 'Task359 dispatch input missing (synthetic RED)')
        self.assertTrue("    if: ${{ !(github.event_name == 'workflow_dispatch' && inputs.native_host_only) }}" in self.text)
        self.assertTrue("github.event_name == 'workflow_dispatch' && inputs.native_host_only && startsWith(github.ref, 'refs/heads/')" in self.text)

    def test_host_job_bounded_read_only_receipt_only(self):
        self.assertTrue('  native-host:\n' in self.text, 'Task359 host job missing (synthetic RED)')
        host = self.text.split('  native-host:\n', 1)[1].split('\n  build-apk:', 1)[0]
        for token in ('runs-on: macos-26', 'timeout-minutes: 45', 'contents: read', 'persist-credentials: false', 'python3 -B scripts/native_host_gate.py', 'if: always()', '${{ runner.temp }}/task359-receipt/receipt.json'):
            self.assertTrue(token in host)
        for token in ('secrets.', 'contents: write', 'xcodebuild', 'simctl', 'continue-on-error', 'path: |'):
            self.assertFalse(token in host)

    def test_baseline_workflow_bytes_unchanged_outside_scoped_delta(self):
        gate = load_runner(self)
        base = subprocess.check_output(['git', '-C', str(ROOT), 'show', BASE + ':.github/workflows/build.yml']).decode()
        restored = re.sub(r'      native_host_only:\n(?:        [^\n]*\n){4}', '', self.text, count=1)
        restored = re.sub(r'  native-host:\n.*?(?=  build-apk:\n)', '', restored, count=1, flags=re.S)
        restored = restored.replace("    if: ${{ !(github.event_name == 'workflow_dispatch' && inputs.native_host_only) }}\n", '', 1)
        old = "(startsWith(github.ref, 'refs/tags/v') && !contains(github.ref_name, '-')) || (github.event_name == 'workflow_dispatch' && inputs.build_ios)"
        new = "!(github.event_name == 'workflow_dispatch' && inputs.native_host_only) && (" + old + ")"
        restored = restored.replace('    if: ${{ ' + new + ' }}', '    if: ${{ ' + old + ' }}', 1)
        self.assertEqual(hashlib.sha256(base.encode()).hexdigest(), hashlib.sha256(restored.encode()).hexdigest())

    def test_event_matrix(self):
        load_runner(self)
        guards = {}
        for job in ('native-host', 'build-apk', 'build-ipa'):
            match = re.search(r'  ' + job + r':\n(?:    [^\n]*\n)*?    if: (.*)', self.text)
            self.assertIsNotNone(match)
            guards[job] = match[1]
        for event in ('push', 'pull_request', 'workflow_dispatch'):
            for ref in ('refs/heads/fix/test', 'refs/heads/master', 'refs/tags/v1.0.0', 'refs/tags/v1.0.0-rc1'):
                for native in (False, True):
                    for ios in (False, True):
                        values = {'github.event_name': event, 'github.ref_name': ref.rsplit('/', 1)[-1], 'github.ref': ref, 'inputs.native_host_only': native, 'inputs.build_ios': ios}
                        active = event == 'workflow_dispatch' and native
                        self.assertEqual(active and ref.startswith('refs/heads/'), evaluate_guard(guards['native-host'], values))
                        self.assertEqual(not active, evaluate_guard(guards['build-apk'], values))
                        old_ipa = (ref.startswith('refs/tags/v') and '-' not in ref.rsplit('/', 1)[-1]) or (event == 'workflow_dispatch' and ios)
                        self.assertEqual(not active and old_ipa, evaluate_guard(guards['build-ipa'], values))


if __name__ == '__main__':
    # Failure stacks/output stay in memory, including synthetic privacy fixtures.
    import io
    result = unittest.TextTestRunner(stream=io.StringIO()).run(unittest.defaultTestLoader.loadTestsFromModule(sys.modules[__name__]))
    print(f'Host synthetic/source contracts: tests={result.testsRun} failures={len(result.failures)} errors={len(result.errors)} skipped={len(result.skipped)}')
    for test, _ in result.failures + result.errors:
        print('Failed contract: ' + test.id())
    sys.exit(not result.wasSuccessful())
