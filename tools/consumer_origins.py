"""Verify consumer-loaded binaries against the freshly staged publication bytes."""
from pathlib import Path
import hashlib

MODULES = ("npc-core", "npc-minestom")


def verify_origins(manifest, lines):
    origins = {}
    for line in lines:
        if not line.startswith("NPC_ORIGIN "):
            continue
        _, module, location = line.strip().split(" ", 2)
        if module not in MODULES:
            raise ValueError("Unexpected NPC origin module: " + module)
        path = Path(location)
        if not path.is_absolute() or not path.is_file() or path.suffix != ".jar":
            raise ValueError("Origin must be an actual absolute Maven JAR: " + location)
        expected = manifest["modules"][module]["sha256"]
        if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
            raise ValueError("Stale or foreign consumer JAR: " + str(path))
        origins[module] = str(path)
    if set(origins) != set(MODULES):
        raise ValueError("Missing actual NPC module origins: " + ", ".join(sorted(set(MODULES) - set(origins))))
    return origins
