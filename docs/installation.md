# Installation

NPC 1.0.0 is published to GitHub Packages. It introduces the redesigned typed API;
0.2.0 consumers should follow the [migration guide](migration.md).

## Consume the published packages

GitHub Packages requires authentication even for public Maven artifacts. Store
`github.user` and a classic token with `read:packages` in your local
`~/.gradle/gradle.properties` or CI secrets. Source builds themselves resolve only
Maven Central and need no Grounds token.

```kotlin
repositories {
    mavenCentral()
    val npcArtifacts = maven {
        url = uri("https://maven.pkg.github.com/groundsgg/library-npc")
        credentials {
            username = providers.gradleProperty("github.user").orNull
            password = providers.gradleProperty("github.token").orNull
        }
    }
    exclusiveContent {
        forRepositories(npcArtifacts)
        filter {
            includeModule("gg.grounds", "npc-core")
            includeModule("gg.grounds", "npc-minestom")
        }
    }
}

dependencies {
    implementation("gg.grounds:npc-minestom:1.0.0")
    implementation("net.minestom:minestom:2026.10.07-26.2")
}
```

`npc-minestom` exposes core transitively and uses compile-only Minestom, so your
host supplies its supported server version. Use Java 25 for the supported host.
For platform-independent numeric definitions and sampling alone, use
`gg.grounds:npc-core:1.0.0` instead. See [compatibility](compatibility.md).

## Build a local Maven publication

With JDK 25 in the matching source checkout:

```sh
./gradlew check apiCheck dokkaGenerate publishAllPublicationsToReviewRepository
```

The independent consumers and example resolve the resulting `.evidence/maven`
repository and the version from `version.txt`:

```sh
./gradlew -p fixtures/java-consumer run -PnpcRepository="file:$PWD/.evidence/maven"
./gradlew -p fixtures/kotlin-consumer run -PnpcRepository="file:$PWD/.evidence/maven"
./gradlew -p examples/standalone-minestom run -PnpcRepository="file:$PWD/.evidence/maven"
```

An explicit `-PnpcVersion=1.0.0` also allows copying these independent projects
outside the checkout, where no producer `version.txt` exists. To run against the
published registry, select
`-PnpcRepository=https://maven.pkg.github.com/groundsgg/library-npc` and configure
the credentials above. Local publication does not publish a package or create a tag.

The current Java/Kotlin examples use the 1.0.0 API. Use the older release-tag
fixtures when consuming 0.2.0. Anonymous Maven retrieval and Maven Central
publication are not configured.
