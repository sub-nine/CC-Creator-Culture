"""Run the workflow's input and release resolution shell against real JSON outputs."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import textwrap

workflow = Path('.github/workflows/aws-deploy.yml').read_text()
head, previous = 'a' * 40, 'b' * 40


def step(name):
    body = workflow.split(f'      - name: {name}\n', 1)[1].split('\n      - ', 1)[0]
    return textwrap.dedent(body.split('        run: |\n', 1)[1])


with tempfile.TemporaryDirectory() as directory:
    root = Path(directory)
    terraform = root / 'terraform'
    terraform.write_text('#!/bin/sh\nprintf "%s\\n" "$TEST_STATE"\n')
    terraform.chmod(0o700)
    env = dict(os.environ, PATH=f'{root}:{os.environ["PATH"]}', HEAD_SHA=head,
               TF_DIR=directory, GITHUB_ENV=str(root / 'env'), AWS_REGION='test',
               STATE_BUCKET='test', RUNTIME_ROLE='test', SEED_RUN_ID='', CONFIRM='')
    state = json.dumps({'release_sha': {'value': previous}, 'app_running': {'value': False}})
    cases = [
        ('plan', '', '{}', head, 'false'),
        ('plan', 'v0.2.0', '{}', head, 'true'),
        ('plan', 'v0.2.0', state, head, 'true'),
        ('plan', '', state, previous, 'false'),
        ('deploy', 'v0.2.0', state, head, 'true'),
        ('start', '', state, previous, 'true'),
        ('stop', '', state, previous, 'false'),
        ('start', '', '{}', None, None),
        ('stop', '', '{}', None, None),
    ]
    for action, release, current, sha, running in cases:
        (root / 'env').write_text('')
        result = subprocess.run(['bash', '-c', step('Resolve release_sha and app_running')],
                                env=dict(env, ACTION=action, RELEASE_REF=release, TEST_STATE=current),
                                capture_output=True, text=True)
        if sha is None:
            assert result.returncode == 65, result.stderr
        else:
            assert result.returncode == 0, result.stderr
            assert (root / 'env').read_text() == f'RELEASE_SHA={sha}\nAPP_RUNNING={running}\n'
    for action, release, valid in [('plan', '', True), ('plan', 'v0.2.0', True),
                                    ('deploy', 'v0.2.0', True), ('deploy', '', False),
                                    ('plan', 'dev', False), ('stop', 'v0.2.0', False)]:
        result = subprocess.run(['bash', '-c', step('Validate inputs')],
                                env=dict(env, ACTION=action, RELEASE_REF=release), capture_output=True)
        assert (result.returncode == 0) == valid, (action, release, result.stderr)
print('AWS deployment input and release resolution checks passed')
