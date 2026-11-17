"""Exercise the public artifact audit against controlled binary/publication inputs."""
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

SOURCE = Path(__file__).resolve().parents[1]
MODULES = {'npc-core': 'core', 'npc-minestom': 'minestom', 'npc-importer': 'importer', 'npc-cli': 'cli'}


class PublicArtifactsTest(unittest.TestCase):
    def fixture(self, root):
        (root / 'tools').mkdir()
        shutil.copyfile(SOURCE / 'tools/verify_public_artifacts.py', root / 'tools/verify_public_artifacts.py')
        (root / 'version.txt').write_text('1.2.3\n')
        for name in ('LICENSE', 'NOTICE'):
            shutil.copyfile(SOURCE / name, root / name)
        own = {}
        for module, package in MODULES.items():
            entries = {'META-INF/MANIFEST.MF': b'Manifest-Version: 1.0\n',
                       'META-INF/LICENSE': (root / 'LICENSE').read_bytes(),
                       'META-INF/NOTICE': (root / 'NOTICE').read_bytes(),
                       f'gg/grounds/npc/{package}/Fixture.class': module.encode()}
            if module == 'npc-importer':
                entries['gg/grounds/npc/importer/version.properties'] = b'version=1.2.3\n'
            own.update({k: v for k, v in entries.items() if k.startswith('gg/')})
            libs = root / module / 'build/libs'
            libs.mkdir(parents=True)
            self.jar(libs / f'{module}-1.2.3.jar', entries)
            source_entries = {k: v for k, v in entries.items() if k.startswith('META-INF/')}
            source_entries[f'gg/grounds/npc/{package}/Fixture.kt'] = b'// Original source\n'
            if module == 'npc-importer':
                source_entries['gg/grounds/npc/importer/version.properties'] = b'version=1.2.3\n'
            self.jar(libs / f'{module}-1.2.3-sources.jar', source_entries)
            publication = root / module / 'build/publications/mavenJava'
            publication.mkdir(parents=True)
            (publication / 'pom-default.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><licenses><license><name>MIT License</name><url>https://opensource.org/license/mit</url></license></licenses></project>')
            (publication / 'module.json').write_text('{}')
            repository = root / '.evidence/maven/gg/grounds' / module / '1.2.3'
            repository.mkdir(parents=True)
            shutil.copyfile(libs / f'{module}-1.2.3.jar', repository / f'{module}-1.2.3.jar')
        # The standalone importer intentionally contains core/importer/CLI, never Minestom.
        own = {k: v for k, v in own.items() if '/minestom/' not in k}
        own.update({'META-INF/MANIFEST.MF': b'Manifest-Version: 1.0\nMain-Class: gg.grounds.npc.cli.NpcCli\n',
                    'META-INF/LICENSE': (root / 'LICENSE').read_bytes(),
                    'META-INF/NOTICE': (root / 'NOTICE').read_bytes()})
        self.jar(root / 'npc-cli/build/libs/npc-cli-1.2.3-all.jar', own)

    @staticmethod
    def jar(path, entries):
        with zipfile.ZipFile(path, 'w') as archive:
            for name, data in entries.items():
                archive.writestr(name, data)

    @staticmethod
    def run_audit(root):
        return subprocess.run([sys.executable, str(root / 'tools/verify_public_artifacts.py')], text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)

    def test_audits_all_four_selected_publications(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            result = self.run_audit(root)
            self.assertEqual(0, result.returncode, result.stdout)
            manifest = json.loads((root / '.evidence/public-artifacts.json').read_text())
            self.assertEqual(set(MODULES), set(manifest['modules']))

    def test_importer_cannot_publish_model_payload(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            with zipfile.ZipFile(root / 'npc-importer/build/libs/npc-importer-1.2.3.jar', 'a') as archive:
                archive.writestr('gg/grounds/npc/importer/private-model.json', '{"purchased":true}')
            result = self.run_audit(root)
            self.assertNotEqual(0, result.returncode)
            self.assertIn('Unexpected public artifact payload', result.stdout)

    def test_executable_cannot_embed_stale_own_bytecode(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            path = root / 'npc-cli/build/libs/npc-cli-1.2.3-all.jar'
            with zipfile.ZipFile(path) as archive:
                entries = {name: archive.read(name) for name in archive.namelist()}
            entries['gg/grounds/npc/core/Fixture.class'] = b'stale bytes'
            self.jar(path, entries)
            result = self.run_audit(root)
            self.assertNotEqual(0, result.returncode)

    def test_executable_rejects_unrecognized_metadata_payload(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            with zipfile.ZipFile(root / 'npc-cli/build/libs/npc-cli-1.2.3-all.jar', 'a') as archive:
                archive.writestr('META-INF/private-model.json', '{"purchased":true}')
            result = self.run_audit(root)
            self.assertNotEqual(0, result.returncode)


if __name__ == '__main__':
    unittest.main()
