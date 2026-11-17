# NPC library

Reusable numeric NPC models and Minestom rendering for Java and Kotlin. `npc-core` validates and samples immutable authored definitions; `npc-minestom` renders shared NPCs and optional connection-specific presentations. Core has no Minestom, Scene, Lobby or Grounds-service dependency.

The released **1.0.0** API is available from GitHub Packages. It breaks source and JVM compatibility with 0.2.0; existing callers need the [migration guide](docs/migration.md). See [installation](docs/installation.md) for authenticated Maven consumption or build the source locally below.

## Build and use

Use JDK 25 and the wrapper. Kotlin 2.2.20 and Java produce JVM-24 library bytecode; the supported Minestom runtime requires Java 25.

```sh
./gradlew check apiCheck dokkaGenerate publishAllPublicationsToReviewRepository
python3 tools/verify_public_artifacts.py
./gradlew -p fixtures/java-consumer run -PnpcRepository="file:$PWD/.evidence/maven"
./gradlew -p fixtures/kotlin-consumer run -PnpcRepository="file:$PWD/.evidence/maven"
```

The independent builds read `version.txt` by default. `-PnpcVersion=...` selects a different library and `-PversionOverride=...` changes the root publication version. No Grounds token is needed for source builds or the local candidate repository.

`examples/` contains runnable host applications for learning the API. `fixtures/` contains independent Java and Kotlin consumer projects that verify the published Maven artifacts can be reused. `tools/` contains maintenance checks for artifact contents, licensing and the actual origin of resolved dependencies.

Start with the [shared Java example](examples/standalone-minestom/src/main/java/example/NpcDemo.java), then the [personal example](examples/standalone-minestom/src/main/java/example/PersonalNpcDemo.java). [Example instructions](examples/standalone-minestom/README.md) describe ports, commands, both runtime profiles and separate smoke tasks.

## Numeric API

```java
import gg.grounds.npc.core.io.NpcDecodeResult;
import gg.grounds.npc.core.io.NpcModelJson;
import gg.grounds.npc.core.animation.NpcPoseSampler;

var decoded = NpcModelJson.decode(bytes);
if (decoded instanceof NpcDecodeResult.Success success) {
    var pose = NpcPoseSampler.sample(success.getModel(), "wave", 0.5);
    double[] matrix = pose.getParts().getFirst().getValues();
}
```

Core sampling uses finite nonnegative **seconds**. Models keep schema 1 and namespaced authored IDs. The typed animation API uses `NpcLoopMode`, `NpcInterpolation` and `NpcBoneTrack`; JSON wire names are unchanged. Model/pose collections are immutable, and matrix arrays are defensive snapshots.

## Renderer ownership

Create `NpcSpawnRequest.builder(instance, model, transform).build()` and retain `NpcRenderer.prepare(request)` immediately. Observe `ready()`, then publish the hidden handle. Shared `playClip`/`advanceClipTo` use explicit absolute **milliseconds**, driven by the host. `handle.preparePresentation(options)` creates optional private clip/head channels with their own monotonic clock.

Always observe command stages and include `cancel()`/`closeAsync()` in the host shutdown drain. Handles and viewer overrides belong to concrete backend/Player connections; reconnects and proxy/backend transfers require new ownership. See [API contracts](docs/api.md).

## Reference and maintenance

KDoc is available in the IDE. `./gradlew dokkaGenerate` writes the aggregated reference to `build/dokka/html`; undocumented supported entrypoints fail the documentation build. Internal packages are implementation details. Kotlin `internal` is not a JVM access-control mechanism for Java.

`apiCheck` compares the supported Kotlin/JVM API with checked-in snapshots. Intentional changes require reviewing the `apiDump` diff and migrating consumers. `formatKotlin` and `formatJava` format the library and examples.

See [installation](docs/installation.md), [compatibility](docs/compatibility.md), [migration](docs/migration.md) and [publishing](docs/publishing.md). The library is MIT; [source provenance](docs/source-provenance.md), `LICENSE` and `NOTICE` explain the original extraction and dependency notices. No purchased models, animations or textures are published.
