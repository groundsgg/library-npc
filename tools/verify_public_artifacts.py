#!/usr/bin/env python3
"""Audit selected public NPC artifacts and CLI bytecode against exact local Maven publications."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MODULES = {"npc-core": "core", "npc-minestom": "minestom", "npc-importer": "importer", "npc-cli": "cli"}
LEGAL = ("META-INF/LICENSE", "META-INF/NOTICE")
VERSION_RESOURCE = "gg/grounds/npc/importer/version.properties"


def archive_entries(path):
    with zipfile.ZipFile(path) as archive:
        names = [entry.filename for entry in archive.infolist() if not entry.is_dir()]
        assert len(names) == len(set(names)), f"Duplicate archive entries: {path}"
        return {name: archive.read(name) for name in names}


def legal_entries(entries, path):
    for name in LEGAL:
        assert entries.get(name) == (ROOT / name.split('/')[-1]).read_bytes(), f"Missing/changed {name}: {path}"


def audit_module(module, package, version):
    selected = {}
    for classifier in ("", "-sources"):
        path = ROOT / module / "build/libs" / f"{module}-{version}{classifier}.jar"
        entries = archive_entries(path)
        legal_entries(entries, path)
        prefix = f"gg/grounds/npc/{package}/"
        for name, data in entries.items():
            metadata = name in (*LEGAL, "META-INF/MANIFEST.MF") or (name.startswith("META-INF/") and name.endswith(".kotlin_module"))
            code = name.startswith(prefix) and name.endswith((".kt", ".java") if classifier else ".class")
            generated = module == "npc-importer" and name == VERSION_RESOURCE and data == f"version={version}\n".encode()
            assert metadata or code or generated, f"Unexpected public artifact payload: {path}: {name}"
        if not classifier:
            selected = entries
    publication = ROOT / module / "build/publications/mavenJava"
    for path in (publication / "pom-default.xml", publication / "module.json"):
        text = path.read_text()
        assert all(value not in text for value in ("resourcepacks", "scene-minestom", "minestom-lobby", "/home/lukas", "github.token")), path
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    licenses = ET.parse(publication / "pom-default.xml").findall("./m:licenses/m:license", namespace)
    assert len(licenses) == 1 and licenses[0].findtext("m:name", namespaces=namespace) == "MIT License", module
    assert licenses[0].findtext("m:url", namespaces=namespace) == "https://opensource.org/license/mit", module
    assert (ROOT / "LICENSE").read_text().startswith("MIT License\n")
    print(f"{module}: selected MIT binary/source payload and dependency metadata verified")
    return selected


def audit_executable(version, binaries):
    path = ROOT / "npc-cli/build/libs" / f"npc-cli-{version}-all.jar"
    entries = archive_entries(path)
    legal_entries(entries, path)
    expected = {name: data for module in ("npc-core", "npc-importer", "npc-cli")
                for name, data in binaries[module].items() if name.startswith("gg/grounds/npc/")}
    actual = {name: data for name, data in entries.items() if name.startswith("gg/grounds/npc/")}
    assert actual == expected, "Executable NPC bytecode/resources differ from selected binaries"
    for name in entries:
        dependency = name.startswith(("kotlin/", "com/google/gson/", "com/google/errorprone/annotations/", "org/jetbrains/annotations/", "org/intellij/lang/annotations/")) and name.endswith((".class", ".kotlin_builtins"))
        metadata = name in (*LEGAL, "META-INF/MANIFEST.MF", "META-INF/proguard/gson.pro") or (name.startswith("META-INF/") and name.endswith(".kotlin_module"))
        metadata = metadata or (name.startswith("META-INF/licenses/") and name.split('/')[-1].upper().startswith(("LICENSE", "NOTICE", "COPYING")))
        metadata = metadata or (name.startswith(("META-INF/maven/com.google.code.gson/gson/", "META-INF/maven/com.google.errorprone/error_prone_annotations/", "META-INF/maven/org.jetbrains/annotations/")) and name.endswith(("/pom.xml", "/pom.properties")))
        metadata = metadata or name.startswith("META-INF/services/")
        assert name in expected or dependency or metadata, f"Unexpected executable payload: {name}"
        assert not name.startswith("net/minestom/"), "CLI must remain platform independent"
    print("npc-cli executable: own bytecode matches selected publications; no Minestom or model payload")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", default=(ROOT / "version.txt").read_text().strip())
    parser.add_argument("--repository", type=Path, default=ROOT / ".evidence/maven")
    args = parser.parse_args()
    binaries = {module: audit_module(module, package, args.version) for module, package in MODULES.items()}
    audit_executable(args.version, binaries)
    modules = {}
    for module in MODULES:
        binary = ROOT / module / "build/libs" / f"{module}-{args.version}.jar"
        digest = hashlib.sha256(binary.read_bytes()).hexdigest()
        directory = args.repository.resolve() / "gg/grounds" / module / args.version
        metadata = directory / "maven-metadata.xml"
        if metadata.exists():
            versions = ET.parse(metadata).findall("./versioning/snapshotVersions/snapshotVersion")
            value = next(entry.findtext("value") for entry in versions if entry.findtext("extension") == "jar" and not entry.findtext("classifier"))
            published = directory / f"{module}-{value}.jar"
        else:
            published = directory / binary.name
        if hashlib.sha256(published.read_bytes()).hexdigest() != digest:
            raise ValueError("Selected Maven publication differs from fresh build: " + module)
        modules[module] = {"sha256": digest, "publication": str(published)}
    manifest = ROOT / ".evidence/public-artifacts.json"
    manifest.parent.mkdir(exist_ok=True)
    manifest.write_text(json.dumps({"version": args.version, "modules": modules}, indent=2) + "\n")
    print("PUBLIC_ARTIFACTS_OK " + str(manifest))


if __name__ == "__main__":
    main()
