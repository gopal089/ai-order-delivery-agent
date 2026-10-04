"""Offline safety regression contracts. actionlint separately validates YAML syntax."""
from pathlib import Path
import re
import os
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = (ROOT / '.github/workflows/ci.yml').read_text()


class CiSafetyContracts(unittest.TestCase):
    def test_actions_are_commit_pinned(self):
        actions = re.findall(r'uses:\s+(\S+)', WORKFLOW)
        self.assertEqual(len(actions), 7)
        for action in actions:
            self.assertRegex(action, r'^actions/[a-z-]+@[0-9a-f]{40}$')

    def test_verification_only_not_cloud_deployment(self):
        for forbidden in ('pull_request_target:', 'id-token:', 'packages:', 'configure-aws-credentials',
                          'aws login', 'docker push', 'ecr ', 'ecs ', 'environment: production'):
            self.assertNotIn(forbidden, WORKFLOW)
        self.assertIn('contents: read', WORKFLOW)
        self.assertEqual(WORKFLOW.count('persist-credentials: false'), 4)

    def test_all_existing_test_layers_remain(self):
        for command in ('evaluation/report.py --run --scope full', 'evaluation/report.py --run --scope baseline',
                        './gradlew bootJar --console=plain', 'node --test tests/*.test.mjs',
                        'npm run build', 'npm run lint', 'npm run typecheck',
                        'infrastructure/tests/*.test.mjs', "-p 'test_*.py'"):
            self.assertIn(command, WORKFLOW)
        self.assertIn('directory: [web, extension]', WORKFLOW)

    def test_no_hidden_test_failure(self):
        self.assertNotIn('continue-on-error', WORKFLOW)
        self.assertNotIn('|| true', WORKFLOW)
        self.assertIn('needs: [contracts-and-secrets, backend, clients]', WORKFLOW)

    def test_artifacts_are_safe_allowlist(self):
        self.assertIn('backend/build/reports/evaluation/full.json', WORKFLOW)
        self.assertIn('backend/build/reports/evaluation/baseline.json', WORKFLOW)
        self.assertNotIn('path: backend/build', WORKFLOW)
        self.assertNotIn('.gradle.log', WORKFLOW)
        self.assertIn('retention-days: 7', WORKFLOW)

    def test_ephemeral_secrets_masked_before_export(self):
        script = (ROOT / '.github/scripts/prepare-local-env.sh').read_text()
        self.assertIn('openssl rand -hex 32', script)
        self.assertIn('openssl rand -base64 48', script)
        self.assertLess(script.index('::add-mask::'), script.index('>> "$GITHUB_ENV"'))
        self.assertIn('test "${GITHUB_ACTIONS:-}" = true', script)

    def test_generated_configuration_is_consistent_and_rotates(self):
        script = ROOT / '.github/scripts/prepare-local-env.sh'
        generated = []
        with tempfile.TemporaryDirectory() as directory:
            for index in range(2):
                target = Path(directory) / str(index)
                result = subprocess.run(['bash', str(script)], env={**os.environ, 'GITHUB_ACTIONS': 'true',
                                        'GITHUB_ENV': str(target)}, capture_output=True, text=True)
                self.assertEqual(result.returncode, 0)
                values = dict(line.split('=', 1) for line in target.read_text().splitlines())
                # Never include generated values in assertion diagnostics.
                self.assertTrue(values['POSTGRES_PASSWORD'] == values['DATABASE_PASSWORD'])
                self.assertTrue(len(values['POSTGRES_PASSWORD']) == 64)
                self.assertTrue(len(values['AUTH_ACCESS_TOKEN_SIGNING_KEY']) == 64)
                self.assertTrue(result.stdout == '::add-mask::' + values['POSTGRES_PASSWORD'] + '\n' +
                                '::add-mask::' + values['AUTH_ACCESS_TOKEN_SIGNING_KEY'] + '\n')
                generated.append(values)
        self.assertTrue(generated[0]['POSTGRES_PASSWORD'] != generated[1]['POSTGRES_PASSWORD'])
        self.assertTrue(generated[0]['AUTH_ACCESS_TOKEN_SIGNING_KEY'] != generated[1]['AUTH_ACCESS_TOKEN_SIGNING_KEY'])

    def test_secret_setup_cannot_run_outside_github_actions(self):
        result = subprocess.run(['bash', str(ROOT / '.github/scripts/prepare-local-env.sh')],
                                env={**os.environ, 'GITHUB_ACTIONS': 'false'}, capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout, '')

    def test_docker_checks_not_release_artifacts(self):
        self.assertIn('--build-arg ALLOW_LOCAL_API=true', WORKFLOW)
        self.assertIn('ci-$REVISION', WORKFLOW)
        self.assertIn('.github/scripts/container_smoke.py', WORKFLOW)
        for directory in ('backend', 'web'):
            dockerfile = (ROOT / directory / 'Dockerfile').read_text()
            self.assertEqual(dockerfile.count('FROM '), 2)
            self.assertIn('USER ', dockerfile)
            self.assertNotIn('AUTH_ACCESS_TOKEN_SIGNING_KEY=', dockerfile)


if __name__ == '__main__':
    unittest.main()
