import json
import os
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ComposeTest(unittest.TestCase):
    def config(self, filename):
        result = subprocess.run(
            ['docker', 'compose', '-f', str(ROOT / filename), 'config', '--format', 'json'],
            env={**os.environ, 'K6_IMAGE': 'test-cloud-image', 'TARGET': 'dev',
                 'BASE_URL': 'https://dev.test', 'TESTID': 'compose-219'},
            capture_output=True, text=True, check=True,
        )
        return json.loads(result.stdout)

    def test_local_mount_and_cloud_image_are_separate(self):
        image = (ROOT / 'Dockerfile.cloud').read_text().split('FROM ')[1].splitlines()[0]
        self.assertIn('@sha256:', image)
        local = self.config('docker-compose.yml')
        for name in ('k6', 'k6-all'):
            service = local['services'][name]
            self.assertNotIn('build', service)
            self.assertEqual(service['image'], image)
            self.assertTrue(any(v['source'] == str(ROOT) and v['target'] == '/scripts'
                                for v in service['volumes']))
        cloud = self.config('compose.dev.yml')['services']['k6']
        self.assertEqual(cloud['image'], 'test-cloud-image')
        self.assertNotIn('build', cloud)
        self.assertFalse(cloud.get('volumes'))
        self.assertEqual(cloud['environment']['BASE_URL'], 'https://dev.test')
        self.assertEqual(cloud['environment']['TESTID'], 'compose-219')
