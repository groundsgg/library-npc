#!/usr/bin/env python3
"""Audit built public NPC binaries, sources and dependency metadata."""
import argparse
import hashlib
import json
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

# Match the built binaries to the selected local Maven publication, never a stale filename.
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--version", default=(ROOT / "version.txt").read_text().strip())
parser.add_argument("--repository", type=Path, default=ROOT / ".evidence/maven")
args = parser.parse_args()
modules = {}
for module in ("npc-core", "npc-minestom"):
    binary = ROOT / module / "build/libs" / (module + "-" + args.version + ".jar")
    digest = hashlib.sha256(binary.read_bytes()).hexdigest()
    directory = args.repository.resolve() / "gg/grounds" / module / args.version
    metadata = directory / "maven-metadata.xml"
    if metadata.exists():
        versions = ET.parse(metadata).findall("./versioning/snapshotVersions/snapshotVersion")
        value = next(entry.findtext("value") for entry in versions
                     if entry.findtext("extension") == "jar" and not entry.findtext("classifier"))
        published = directory / (module + "-" + value + ".jar")
    else:
        published = directory / binary.name
    if hashlib.sha256(published.read_bytes()).hexdigest() != digest:
        raise ValueError("Selected Maven publication differs from fresh build: " + module)
    modules[module] = {"sha256": digest, "publication": str(published)}
manifest = ROOT / ".evidence/public-artifacts.json"
manifest.parent.mkdir(exist_ok=True)
manifest.write_text(json.dumps({"version": args.version, "modules": modules}, indent=2) + "\n")
print("PUBLIC_ARTIFACTS_OK " + str(manifest))
