#!/usr/bin/env python3
"""An empty selected registry must not be bypassed via another Maven repository."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='npc-consumer-origin-') as temporary:
    directory = Path(temporary)
    selected = directory / 'selected'
    selected.mkdir()
    injected = directory / 'fallback.gradle'
    fallback = (ROOT / '.evidence/maven').as_uri()
    injected.write_text("gradle.projectsEvaluated {\n"
                        "  gradle.rootProject.repositories.maven {\n"
                        f"    url = uri('{fallback}')\n"
                        "  }\n}\n")
    result = subprocess.run([str(ROOT / 'gradlew'), '-p', 'fixtures/java-consumer',
                             'run', '--offline', '-PnpcVersion=0.1.0-SNAPSHOT',
                             '-PnpcRepository=' + selected.as_uri(),
                             '--init-script', str(injected)], cwd=ROOT,
                            text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if result.returncode == 0:
        raise AssertionError('NPC resolved from a fallback registry instead of the selected empty registry')
    if 'Could not find gg.grounds:npc-minestom:0.1.0-SNAPSHOT' not in result.stdout:
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
