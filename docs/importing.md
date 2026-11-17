# Import Blockbench models

Use `npc-importer` in an application or `npc-cli` before deployment. Both paths use the same compiler and produce matching server data and client assets. Restart to adopt changed models.

The importer is new in the current source tree. The published NPC 1.0.0 release contains the numeric and rendering APIs, but does not contain these importer/CLI modules. Use all modules from the same importer-capable build; source-build commands below create a separate local version.

## Build the standalone tool

Use JDK 25. From the library checkout:

```sh
NPC_VERSION="$(cat version.txt)-local-SNAPSHOT"
./gradlew -PversionOverride="$NPC_VERSION" :npc-cli:executableJar :npc-cli:assembleDist publishAllPublicationsToReviewRepository
java -jar "npc-cli/build/libs/npc-cli-$NPC_VERSION-all.jar" --help
```

The `-all.jar` includes its runtime dependencies. Application ZIP/TAR downloads in `npc-cli/build/distributions` provide Windows and Unix launch scripts. The normal Maven artifact `gg.grounds:npc-cli` can instead be resolved on a Gradle `JavaExec` classpath. Neither form requires Minestom.

## Validate and compile

Save a Generic Model project as `.bbmodel`. Keep local texture files inside the input directory, or embed them in Blockbench. An input can be one file or a directory with subdirectories.

```sh
java -jar "npc-cli/build/libs/npc-cli-$NPC_VERSION-all.jar" validate \
  --input models/ --namespace my_plugin --target 26.3

java -jar "npc-cli/build/libs/npc-cli-$NPC_VERSION-all.jar" compile \
  --input models/ --namespace my_plugin --target 26.3 --output build/npc-content/
```

`models/town/merchant.bbmodel` becomes `my_plugin:npc/town/merchant`. Paths are normalized to lowercase; collisions and unsupported resource-name characters are errors. A configuration file can assign explicit IDs. The target is required: `26.2` selects resource format 88.0, and `26.3` selects 97.1. Target selection covers metadata and the tested generated asset format; it does not convert arbitrary existing packs between Minecraft versions.

Compilation requires a new output directory. It publishes these files only after the whole input and pack contract pass validation:

| Output               | Purpose                                                              |
| -------------------- | -------------------------------------------------------------------- |
| `server-content.zip` | Validated numeric model definitions and a versioned bundle contract. |
| `resourcepack.zip`   | Complete client pack, optionally including a base pack.              |
| `pack-fragment/`     | Generated client assets for your existing pack composer.             |
| `manifest.json`      | Content identity, target, compiler version and final archive hashes. |

The content ID describes the generated models and client contributions. Output paths and unrelated base-pack files do not change it. The final ZIP hashes identify the actual delivered files. Keep the bundle and pack together in your deployment. A server restart loads a new content snapshot; the library does not host or send packs for you.

## Merge an existing resourcepack

```sh
java -jar "npc-cli/build/libs/npc-cli-$NPC_VERSION-all.jar" compile \
  --input models/ --namespace my_plugin --target 26.3 \
  --base-pack existing-pack.zip --output build/combined-npc-content/
```

The base can also be a directory containing `pack.mcmeta`. Its declared compatibility must include the selected target. The result narrows the compatibility declaration to that target and preserves unrelated metadata, the base description and icon. `--description "My NPC pack"` explicitly replaces the description.

Foreign files retain their bytes. Identical files at the same path are deduplicated. Different files at the same path are a conflict; choose a different namespace or fix the input. Sound registries merge disjoint event IDs and accept repeated IDs only when their definitions are equivalent. Overlay files are retained; metadata keeps overlays active for the selected target and narrows their ranges to that profile. Obsolete legacy compatibility fields are removed from the target-specific result. Active overlays or filters that change the generated NPC contributions are rejected with a path-specific diagnostic. The base pack is never modified.

## Load prepared content in Java or Kotlin

Your runtime needs `npc-core`; rendering additionally uses `npc-minestom` and your own Minestom dependency. Loading prepared content does not need the importer or CLI.

```java
import gg.grounds.npc.core.io.content.NpcContentLoader;
import java.nio.file.Path;

var content = NpcContentLoader.load(Path.of("npc-content/server-content.zip"));
content.verifyResourcePack(Path.of("npc-content/resourcepack.zip"));
var model = content.getModels().get("my_plugin:npc/town/merchant");
if (model == null) throw new IllegalStateException("Required merchant model is missing");
// Pass model to NpcSpawnRequest.builder(instance, model, transform).
```

```kotlin
import gg.grounds.npc.core.io.content.NpcContentLoader
import java.nio.file.Path

val content = NpcContentLoader.load(Path.of("npc-content/server-content.zip"))
content.verifyResourcePack(Path.of("npc-content/resourcepack.zip"))
val model = requireNotNull(content.models["my_plugin:npc/town/merchant"])
```

The loader also accepts an extracted server-content directory or a bounded in-memory resource map. It validates the contract, content ID, model hashes and every model before returning an immutable lookup. Pack verification accepts unrelated files and additional sound events while checking the generated NPC contributions. It does not establish that a connected player has accepted the pack; your host owns that readiness boundary.

## Import at application startup

Add the matching `gg.grounds:npc-importer` version. Before accepting players, invoke the same pipeline directly:

```java
import gg.grounds.npc.importer.NpcModelImporter;
import gg.grounds.npc.importer.config.NpcImportOptions;
import gg.grounds.npc.importer.config.NpcMinecraftTarget;
import gg.grounds.npc.core.io.content.NpcContentLoader;
import java.nio.file.Files;
import java.nio.file.Path;

var work = Files.createTempDirectory("npc-startup-");
var options = NpcImportOptions.builder(
    Path.of("models"), "my_plugin", NpcMinecraftTarget.MINECRAFT_26_3).build();
var output = NpcModelImporter.compile(options, work.resolve("content"));
var content = NpcContentLoader.load(output.getServerContent());
content.verifyResourcePack(output.getResourcePack());
// Retain output.getResourcePack() for your pack host until shutdown.
// Your application owns and removes work after its pack host and NPCs are closed.
```

Import is synchronous. Run it during bootstrap or on an application-owned worker, rather than blocking an instance tick. The same loaded `NpcModel` definitions can create multiple NPC instances. Retain one content snapshot for the server run. Source edits have no effect until the next import/restart.

## Run the CLI from another Gradle project

The complete independent project is [`fixtures/gradle-importer-consumer`](../fixtures/gradle-importer-consumer). It resolves the normal CLI artifact, declares model inputs and generated outputs, and verifies the result with the separately resolved core. It needs no parent `version.txt` or producer sources.

```sh
./gradlew -p fixtures/gradle-importer-consumer run \
  -PnpcVersion="$NPC_VERSION" -PnpcRepository="file:$PWD/.evidence/maven"
```

Its `compileNpcModels` task owns only `build/npc`: on a changed input it removes that generated directory before running the CLI. The CLI itself never overwrites an existing output. In your project, pin the tool version and declare configuration/base-pack files as inputs too when you use them. For GitHub Packages, use the authentication and exclusive repository selection in [installation](installation.md).

## Explicit IDs and sounds

Pass `--config import.json`, or use `NpcImportOptions.Builder.config(path)`:

```json
{
  "schemaVersion": 1,
  "models": { "town/merchant.bbmodel": "my_plugin:npc/merchant" },
  "sounds": [
    {
      "model": "my_plugin:npc/merchant",
      "clip": "wave",
      "key": "my_plugin:npc/merchant/wave",
      "file": "sounds/wave.ogg",
      "volume": 0.8,
      "pitch": 1.0,
      "radius": 12.0
    }
  ]
}
```

Model mapping keys and sound file paths are relative to the selected input directory. A sound binding must name an existing model and clip. Local OGG files become pack assets and sound events. To reference an existing/Vanilla sound event, replace `file` with `"external": true`; your host or Minecraft supplies that event. The original example uses the external event `minecraft:entity.villager.yes`. Actual sound delivery still requires the appropriate NPC sound sink.

## Supported source features

| Feature                  | First importer version                                                                            |
| ------------------------ | ------------------------------------------------------------------------------------------------- |
| Generic/free `.bbmodel`  | 5.0 and legacy 4.10. Legacy position-X and rotation-X/Y conventions are normalized.               |
| Geometry                 | Cubes, bone hierarchy, pivots, rotations, reflection and explicit per-face UVs.                   |
| Textures                 | Embedded PNG or bounded local relative PNG; maximum 4096 pixels per dimension.                    |
| Animation                | Numeric position/rotation/scale, `linear`, `step`, `catmullrom`; `once`, `loop`, `hold`.          |
| Bounds                   | Authored hitbox groups, otherwise the visible rest-pose bounds. Bounds must have positive extent. |
| Other source types       | `.ajmodel`, Bedrock exports, meshes and scripts are not implemented.                              |
| Other animation features | Molang expressions, Bezier/global/quaternion tracks and unsupported channels produce diagnostics. |

Native model cubes must fit their supported element range. The core's existing bone, part, clip and key limits remain in force. Unsupported features fail the entire compilation; they are not silently dropped.

## Diagnose a failed build

Use `--json` for machine-readable diagnostics and `--debug` for technical failure details. Normal failures include a code, source and field path. `validate` emits no content files.

| Exit | Meaning                                                         |
| ---- | --------------------------------------------------------------- |
| `0`  | Validation/compilation succeeded.                               |
| `2`  | Unknown/missing option, namespace or target.                    |
| `3`  | Invalid/unsupported content, merge conflict or existing output. |
| `4`  | Filesystem read/write failure.                                  |
| `1`  | Unexpected internal failure.                                    |

For an output conflict, choose a fresh destination or remove only the output your build owns. For invisible geometry, check the model ID, pack verification, the client's pack acceptance and the selected Minecraft profile. Run the [original import example](../examples/standalone-minestom/README.md#import-an-original-blockbench-model) before investigating application-specific integration.

Pack range decoding follows Mojang's [pack metadata specification](https://www.minecraft.net/en-us/article/minecraft-snapshot-25w31a): an upper major-only value, such as `97` or `[97]`, includes all minor versions of that major. An explicit `[97, 0]` upper bound excludes 97.1.
