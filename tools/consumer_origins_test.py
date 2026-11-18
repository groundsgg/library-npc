import hashlib
from pathlib import Path
import tempfile
import unittest
from consumer_origins import verify_origins


class ConsumerOriginsTest(unittest.TestCase):
    def test_matching_names_do_not_hide_stale_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'npc-core-0.3.0-api-review-SNAPSHOT.jar'
            path.write_bytes(b'old candidate')
            manifest = {'modules': {'npc-core': {'sha256': hashlib.sha256(b'new candidate').hexdigest()},
                                    'npc-minestom': {'sha256': hashlib.sha256(b'new candidate').hexdigest()}}}
            with self.assertRaises(ValueError):
                verify_origins(manifest, [f'NPC_ORIGIN npc-core {path}'])

    def test_missing_adapter_origin_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'npc-core.jar'
            path.write_bytes(b'core')
            manifest = {'modules': {'npc-core': {'sha256': hashlib.sha256(b'core').hexdigest()},
                                    'npc-minestom': {'sha256': hashlib.sha256(b'adapter').hexdigest()}}}
            with self.assertRaises(ValueError):
                verify_origins(manifest, [f'NPC_ORIGIN npc-core {path}'])

    def test_both_actual_artifacts_are_required_and_hashed(self):
        with tempfile.TemporaryDirectory() as directory:
            manifest = {'modules': {}}
            lines = []
            for module, payload in [('npc-core', b'core'), ('npc-minestom', b'adapter')]:
                path = Path(directory) / (module + '.jar')
                path.write_bytes(payload)
                manifest['modules'][module] = {'sha256': hashlib.sha256(payload).hexdigest()}
                lines.append(f'NPC_ORIGIN {module} {path}')
            self.assertEqual({'npc-core', 'npc-minestom'}, set(verify_origins(manifest, lines)))

    def test_importer_and_cli_origins_are_required_when_selected(self):
        with tempfile.TemporaryDirectory() as directory:
            selected = ('npc-core', 'npc-importer', 'npc-cli')
            manifest = {'modules': {}}
            lines = []
            for module in selected:
                path = Path(directory) / (module + '.jar')
                payload = module.encode()
                path.write_bytes(payload)
                manifest['modules'][module] = {'sha256': hashlib.sha256(payload).hexdigest()}
                lines.append(f'NPC_ORIGIN {module} {path}')
            self.assertEqual(set(selected), set(verify_origins(manifest, lines, required_modules=selected)))
            with self.assertRaises(ValueError):
                verify_origins(manifest, lines[:-1], required_modules=selected)
            (Path(directory) / 'npc-importer.jar').write_bytes(b'stale')
            with self.assertRaises(ValueError):
                verify_origins(manifest, lines, required_modules=selected)


if __name__ == '__main__':
    unittest.main()
