# Installation

The current source is the breaking 0.3.0 API-review candidate. Build it locally with JDK 25:

```sh
./gradlew check apiCheck dokkaGenerate publishAllPublicationsToReviewRepository
```

Use the resulting `.evidence/maven` repository and version from `version.txt` for independent consumer builds. See the repository README and examples. This does not publish to GitHub or create a release. The previously published 0.2.0 package has the older API; use its release-tag documentation until a coordinated new release is available.

For published packages, the repository configuration remains:

Releases use GitHub Packages, matching the other Grounds libraries. The last published API is 0.2.0.

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
    implementation("gg.grounds:npc-core:0.2.0")
    implementation("gg.grounds:npc-minestom:0.2.0")
    // Supply the Minestom version supported by your server separately.
}
```

`npc-core` is platform independent. `npc-minestom` deliberately uses compile-only
Minestom, so it does not replace the host's server dependency. Java 25 is required
for the supported Minestom baseline; see [compatibility](compatibility.md).
The current Java/Kotlin consumers and standalone example use the redesigned API.
Run them with `-PnpcVersion=0.3.0-api-review-SNAPSHOT -PnpcRepository=file:///ABSOLUTE/PATH/.evidence/maven`
after building the local candidate. An explicit version also allows copying these independent
projects outside this checkout. Without an override they read the producer's `version.txt`.
For the published 0.2.0 API, use the fixtures and documentation from its release tag.
Local candidate overrides are development tools and are not release coordinates.

An anonymous Maven endpoint and Maven Central publication are not configured.
This is an explicit initial choice to follow the other Grounds libraries.
