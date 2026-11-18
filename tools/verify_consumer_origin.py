#!/usr/bin/env python3
"""Check selected-registry isolation and actual consumer-loaded publication bytes."""
import argparse
import json
from pathlib import Path
import subprocess
import tempfile
from consumer_origins import verify_origins

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", default=(ROOT / "version.txt").read_text().strip())
    parser.add_argument("--repository", type=Path, default=ROOT / ".evidence/maven")
    parser.add_argument("--manifest", type=Path, default=ROOT / ".evidence/public-artifacts.json")
    parser.add_argument("--log", type=Path, action="append", default=[])
    parser.add_argument("--modules", default="npc-core,npc-minestom", help="Exact expected loaded module group")
    args = parser.parse_args()
    version = args.version
    repository = args.repository.resolve()
    if args.log:
        manifest = json.loads(args.manifest.read_text())
        if manifest["version"] != version:
            raise ValueError("Manifest does not describe the selected version")
        for log in args.log:
            verify_origins(manifest, log.read_text().splitlines(), tuple(args.modules.split(",")))
            print("NPC_CONSUMER_ORIGINS_OK " + str(log))
        return
    with tempfile.TemporaryDirectory(prefix='npc-consumer-origin-') as temporary:
        directory = Path(temporary)
        selected = directory / 'selected'
        selected.mkdir()
        injected = directory / 'fallback.gradle'
        fallback = repository.as_uri()
        injected.write_text("gradle.projectsEvaluated {\n"
                            "  gradle.rootProject.repositories.maven {\n"
                            f"    url = uri('{fallback}')\n"
                            "  }\n}\n")
        result = subprocess.run([str(ROOT / 'gradlew'), '-p', 'fixtures/java-consumer',
                                 'run', '--offline', '-PnpcVersion=' + version,
                                 '-PnpcRepository=' + selected.as_uri(),
                                 '--init-script', str(injected)], cwd=ROOT,
                                text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        if result.returncode == 0:
            raise AssertionError('NPC resolved from a fallback registry instead of the selected empty registry')
        if 'Could not find gg.grounds:npc-minestom:' + version not in result.stdout:
            raise AssertionError('Origin test failed for an unrelated reason:\n' + result.stdout)
    print('Selected NPC repository cannot be bypassed by a populated fallback')

    with tempfile.TemporaryDirectory(prefix='npc-credential-origin-') as temporary:
        init = Path(temporary) / 'credentials.gradle'
        init.write_text("gradle.projectsEvaluated {\n"
                        "  def repo = gradle.rootProject.repositories.getByName('NpcArtifacts')\n"
                        "  def canonical = repo.url.toString() == 'https://maven.pkg.github.com/groundsgg/library-npc'\n"
                        "  assert repo.credentials.username == (canonical ? 'probe-user' : null)\n"
                        "  assert repo.credentials.password == (canonical ? 'probe-password' : null)\n"
                        "}\n")
        for repository in ['https://maven.pkg.github.com/groundsgg/library-npc',
                           'https://maven.pkg.github.com/groundsgg/another-repo',
                           'https://maven.pkg.github.com:8443/groundsgg/library-npc',
                           'http://maven.pkg.github.com/groundsgg/library-npc']:
            result = subprocess.run([str(ROOT / 'gradlew'), '-p', 'fixtures/java-consumer',
                                     'help', '--offline', '--init-script', str(init),
                                     '-PnpcRepository=' + repository, '-Pgithub.user=probe-user',
                                     '-Pgithub.token=probe-password'], cwd=ROOT,
                                    text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
            if result.returncode:
                raise AssertionError('Credential origin configuration failed for ' + repository + ':\n' + result.stdout)
    print('Only the exact canonical HTTPS registry receives synthetic test credentials')


if __name__ == "__main__":
    main()
