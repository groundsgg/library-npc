# NPC library

Standalone numeric NPC evaluation and Minestom rendering extracted from Grounds.
The public model API has no Scene, lobby, catalogue, database or Minestom dependency.

`npc-core` provides the neutral evaluator. `npc-minestom` owns hidden preparation,
guarded publication, animation, head look, viewer scale/glow and asynchronous
cleanup. Scene integration is a separate optional module. No external release has
been published.

## Build

Use JDK 25:

```sh
./gradlew check
```

Kotlin 2.2.20 and Java emit matching JVM 24 bytecode, preserving the existing
source baseline while running the build and tests on JDK 25. Dependencies resolve
from Maven Central; no Grounds credentials are needed.

## Decode and sample from Java

```java
NpcDecodeResult decoded = NpcModelJson.decode(bytes);
if (decoded instanceof NpcDecodeResult.Success success) {
    NpcPose pose = NpcPoseSampler.sample(success.getModel(), "wave", 0.5);
    double[] matrix = pose.getParts().getFirst().getValues();
}
```

Model coordinates and clip durations are in the authored numeric format; sample
time is nonnegative seconds. Head look is additive model-local XYZ rotation in
degrees and affects only the named head subtree. The Minestom-facing mesh
correction is deliberately outside `npc-core`.

Definitions loaded from JSON and constructed directly use the same graph/clip
invariants. Decoder failures contain structured problems; failed definitions are
never returned as partially usable models. Unknown clips and invalid sample times
are rejected. Pose matrices and definition collections cannot mutate cached state.

Input JSON keeps numeric schema version 1. Resource identifiers are namespaced and
may use the host's own namespace. The host supplies all model bytes and validates
them against its own asset catalogue. There is no implicit resource discovery.

## Provenance and content

See [source provenance](docs/source-provenance.md), `LICENSE` and `NOTICE`.
No purchased model, animation, texture or generated product resource is included.
The library is licensed under MIT; see the [licensing decision](docs/license-decision.md).
Source provenance and third-party dependency notices remain separate.

## Standalone server

See [installation](docs/installation.md), [API](docs/api.md), [compatibility](docs/compatibility.md),
[migration](docs/migration.md) and [candidate publication](docs/publishing.md).
The independent application in `examples/standalone-minestom` uses the public API,
an original two-bone model and a neutral demo resourcepack. The server offers its original demo pack automatically on localhost:25574.
`runGroundsProfile` uses an explicit local 26.3 classpath and matching pack profile. The automated
`--smoke` mode checks startup, animation, chunk movement and complete removal.
No Scene, service credentials or purchased model data is required.
