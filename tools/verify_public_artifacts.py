#!/usr/bin/env python3
"""Audit built public NPC binaries, sources and dependency metadata."""
from pathlib import Path
import zipfile
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
for module in ('npc-core', 'npc-minestom'):
    archives = sorted((ROOT / module / 'build/libs').glob('*.jar'))
    assert archives, f'Build {module} first'
    prefix = 'gg/grounds/npc/' + ('core/' if module == 'npc-core' else 'minestom/')
    for archive in archives:
        with zipfile.ZipFile(archive) as jar:
            names = [entry.filename for entry in jar.infolist() if not entry.is_dir()]
            assert len(names) == len(set(names)), f'Duplicate archive entries: {archive}'
            for legal in ('LICENSE', 'NOTICE'):
                entry = 'META-INF/' + legal
                assert entry in names, f'Missing {entry}: {archive}'
                assert jar.read(entry) == (ROOT / legal).read_bytes(), f'Changed {entry}: {archive}'
            for name in names:
                metadata = name in ('META-INF/MANIFEST.MF', 'META-INF/LICENSE', 'META-INF/NOTICE') or (name.startswith('META-INF/') and name.endswith('.kotlin_module'))
                code = name.startswith(prefix) and name.endswith(('.kt', '.java') if archive.name.endswith('-sources.jar') else '.class')
                assert metadata or code, f'Unexpected public artifact payload: {archive}: {name}'
    publication = ROOT / module / 'build/publications/mavenJava'
    for path in (publication / 'pom-default.xml', publication / 'module.json'):
        text = path.read_text()
        assert all(value not in text for value in ('resourcepacks', 'scene-minestom', 'minestom-lobby', '/home/lukas', 'github.token')), path
    pom = ET.parse(publication / 'pom-default.xml')
    namespace = {'m': 'http://maven.apache.org/POM/4.0.0'}
    licences = pom.findall('./m:licenses/m:license', namespace)
    assert len(licences) == 1, f'Expected one library licence: {module}'
    assert licences[0].findtext('m:name', namespaces=namespace) == 'MIT License', module
    assert licences[0].findtext('m:url', namespaces=namespace) == 'https://opensource.org/license/mit', module
    assert (ROOT / 'LICENSE').read_text().startswith('MIT License\n'), 'Library licence must be MIT'
    print(f'{module}: MIT binary/source provenance, payload isolation and dependency metadata verified')
