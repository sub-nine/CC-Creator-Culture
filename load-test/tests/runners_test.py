import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
CONFIG = dict(region='ap-northeast-2', cluster='cc-test', task_definition='task-definition',
              subnet_ids=['subnet-1', 'subnet-2'], security_group_id='sg-k6',
              base_url='https://gateway.test', embedding_url='http://embedding:8000',
              prometheus_url='http://prometheus:9090/api/v1/write')
ARN = 'arn:aws:ecs:ap-northeast-2:123456789012:task/cc-test/test'
STUB = '''#!/usr/bin/env python3
import json, os, sys
from pathlib import Path
name = Path(sys.argv[0]).name
args = sys.argv[1:]
with open(os.environ['CALLS'], 'a') as out:
    out.write(json.dumps([name, args]) + '\\n')
if name == 'terraform':
    print(os.environ['RUNNER_CONFIG'])
elif name == 'aws':
    op = args[3]
    mode = os.environ.get('MODE', 'ok')
    if op == 'run-task':
        print(json.dumps({'tasks': [{'taskArn': os.environ['TASK_ARN']}], 'failures': []} if mode != 'run-fail' else {'tasks': [], 'failures': [{'reason': 'DENIED'}]}))
    elif op == 'describe-tasks':
        if mode == 'describe-fail':
            print(json.dumps({'tasks': [], 'failures': [{'reason': 'MISSING'}]}))
        else:
            print(json.dumps({'tasks': [{'lastStatus': 'RUNNING' if mode == 'running' else 'STOPPED', 'containers': [{'name': 'k6', 'exitCode': 3 if mode == 'exit-fail' else 0}]}], 'failures': []}))
    elif op == 'stop-task':
        print('{}')
    else:
        sys.exit(99)
'''


class RunnersTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        for name in ('terraform', 'aws', 'docker'):
            file = self.directory / name
            file.write_text(STUB)
            file.chmod(0o755)
        self.log = self.directory / 'calls'
        self.env = {**os.environ, 'PATH': f'{self.directory}:{os.environ["PATH"]}',
                    'CALLS': str(self.log), 'RUNNER_CONFIG': json.dumps(CONFIG), 'TASK_ARN': ARN,
                    'TESTID': 'test-219', 'K6_IMAGE': 'test-only-image'}
        for key in ('BASE_URL', 'TARGET', 'SMOKE_URL', 'EMBED_URL', 'ENV_FILE', 'MODE'):
            self.env.pop(key, None)

    def run_script(self, name, *args, **env):
        self.log.write_text('')
        return subprocess.run(['sh', str(ROOT / 'scripts' / name), *args],
                              env={**self.env, **env}, capture_output=True, text=True, timeout=8)

    def calls(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()]

    def test_dev_preserves_arguments_and_spaced_env_path(self):
        envfile = self.directory / 'release env.file'
        envfile.touch()
        result = self.run_script('run-dev.sh', 'scenarios/smoke.js', '--quiet',
                                 SMOKE_URL='http://embedding/docs', ENV_FILE=str(envfile))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.calls(), [['docker', ['compose', '--env-file', str(envfile),
            '-f', 'load-test/compose.dev.yml', 'run', '--rm', 'k6', 'run', '--out',
            'experimental-prometheus-rw', '--tag', 'testid=test-219', 'scenarios/smoke.js', '--quiet']]])

    def test_dev_invalid_input_never_calls_docker(self):
        for args in [[], ['lib/auth.js'], ['scenarios/../config/index.js'],
                     ['scenarios/missing.js'], ['scenarios/smoke.js'],
                     ['scenarios/product-service/get-product-detail.js']]:
            with self.subTest(args=args):
                self.assertNotEqual(self.run_script('run-dev.sh', *args).returncode, 0)
                self.assertEqual(self.calls(), [])

    def test_aws_passes_safe_json_and_checks_exit(self):
        result = self.run_script('run-aws.sh', 'run', 'scenarios/smoke.js', '--quiet')
        self.assertEqual(result.returncode, 0, result.stderr)
        args = next(args for name, args in self.calls() if name == 'aws' and args[3] == 'run-task')
        overrides = json.loads(args[args.index('--overrides') + 1])['containerOverrides'][0]
        self.assertEqual(overrides['command'][-2:], ['scenarios/smoke.js', '--quiet'])
        env = {x['name']: x['value'] for x in overrides['environment']}
        self.assertEqual(env['TARGET'], 'prod')
        self.assertEqual(env['TESTID'], 'test-219')
        self.assertEqual(env['SMOKE_URL'], 'http://embedding:8000/docs')
        self.assertEqual(env['EMBED_URL'], 'http://embedding:8000/embed')
        network = json.loads(args[args.index('--network-configuration') + 1])['awsvpcConfiguration']
        self.assertEqual(network['securityGroups'], ['sg-k6'])
        self.assertEqual(network['assignPublicIp'], 'DISABLED')
        self.assertIn(ARN, result.stdout)

    def test_aws_invalid_input_and_failures(self):
        for env in [{'RUNNER_CONFIG': '{}'}, {'WAIT_TIMEOUT': 'bad'},
                    {'MODE': 'run-fail'}, {'MODE': 'describe-fail'}, {'MODE': 'exit-fail'}]:
            with self.subTest(env=env):
                result = self.run_script('run-aws.sh', 'run', 'scenarios/smoke.js', **env)
                self.assertNotEqual(result.returncode, 0, result.stdout)
        result = self.run_script('run-aws.sh', 'run', 'scenarios/../config/index.js')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(any(name == 'aws' for name, args in self.calls()))

    def test_aws_timeout_and_explicit_stop(self):
        result = self.run_script('run-aws.sh', 'run', 'scenarios/smoke.js',
                                 MODE='running', WAIT_TIMEOUT='1', POLL_INTERVAL='1')
        self.assertEqual(result.returncode, 3, result.stderr)
        self.assertFalse(any('stop-task' in args for name, args in self.calls()))
        result = self.run_script('run-aws.sh', 'stop', ARN)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(any('stop-task' in args for name, args in self.calls()))


if __name__ == '__main__':
    unittest.main()
