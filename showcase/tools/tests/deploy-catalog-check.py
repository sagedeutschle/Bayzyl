"""The deployment layer must replace the public catalog without copying private state."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import unittest


class CatalogLayerTest(unittest.TestCase):
    def test_public_catalog_is_packaged_exactly(self):
        default = Path(__file__).resolve().parents[1] / 'deploy/push-overlay.py'
        packager = Path(os.environ.get('DEPLOY_PACKAGER', default))
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'site').mkdir()
            (root / 'site/index.html').write_text('<!doctype html><title>fixture</title>')
            (root / 'server.js').write_text('// fixture')
            (root / 'data').mkdir()
            payload = json.dumps({'schemaVersion': 1, 'projects': [{'id': 'fixture'}]}).encode()
            (root / 'data/projects.json').write_bytes(payload)
            (root / 'data/unrelated-state.json').write_text('must not ship')
            env = {**os.environ, 'DRY_RUN': '1'}
            env.pop('FLY_API_TOKEN', None)
            result = subprocess.run([sys.executable, str(packager), str(root), 'fixture'],
                                    env=env, capture_output=True, text=True, timeout=30)
            self.assertEqual(result.returncode, 0, result.stderr)
            with tarfile.open(root / 'layer.tar.gz') as layer:
                self.assertEqual([n for n in layer.getnames() if n.startswith('app/data/')],
                                 ['app/data/projects.json'])
                self.assertEqual(layer.extractfile('app/data/projects.json').read(), payload)


if __name__ == '__main__':
    unittest.main()
