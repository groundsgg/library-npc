# Installation

Releases use GitHub Packages, matching the other Grounds libraries. The first
release is prepared through Release Please; do not assume a version exists until
the release publishing workflow succeeds.

GitHub Packages requires authentication even when the repository and package are
public. A personal access token (classic) with `read:packages` is required for
external consumers. Do not commit it; use a local Gradle property or secret.
Source builds themselves resolve only Maven Central and need no Grounds token.

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
    implementation("gg.grounds:npc-core:0.1.0")
    implementation("gg.grounds:npc-minestom:0.1.0")
    // Supply the Minestom version supported by your server separately.
}
```

`npc-core` is platform independent. `npc-minestom` deliberately uses compile-only
Minestom, so it does not replace the host's server dependency. Java 25 is required
for the supported Minestom baseline; see [compatibility](compatibility.md).
The independent Java/Kotlin consumers and standalone example can use a published
version through `-PnpcVersion=0.1.0 -PnpcRepository=...` once that release exists.

An anonymous Maven endpoint and Maven Central publication are not configured.
This is an explicit initial choice to follow the other Grounds libraries.
