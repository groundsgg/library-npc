# Releases and candidate publication

Releases follow the other Grounds libraries: Release Please owns versions and
Git tags, and the shared Grounds Gradle publishing workflow publishes tagged
artifacts to GitHub Packages. Never create release tags or edit generated release
PRs by hand. The current release is0.2.0, including personal presentations;
only a successful publishing workflow makes its artifacts available.

GitHub Packages requires a GitHub token even for public packages. This is the
project owner's chosen initial distribution method; anonymous retrieval is no
longer a release gate. See [installation](installation.md). Source builds and the
public CI still resolve only Maven Central without Grounds credentials.

For a local integration candidate:

```sh
./gradlew check publishAllPublicationsToReviewRepository
python3 tools/verify_public_artifacts.py
./gradlew -p fixtures/java-consumer run -PnpcRepository=file:///absolute/path/to/library-npc/.evidence/maven
./gradlew -p fixtures/kotlin-consumer run -PnpcRepository=file:///absolute/path/to/library-npc/.evidence/maven
./gradlew -p examples/standalone-minestom test run --args=--smoke -PnpcRepository=file:///absolute/path/to/library-npc/.evidence/maven
```

The project owner selected MIT on 2026-11-12; see [licensing decision](license-decision.md).
Binary/source JARs include MIT and provenance notices; POM metadata declares MIT.
Third-party dependencies retain their licences. The independent implementation
review and neutral26.3 two-client private rendering/sound/restoration acceptance
are complete. Licensed content and actual proxy/server transfer acceptance remain
required for product integration.

After package publication, the release workflow runs both separate consumer
projects against GitHub Packages using only its read-scoped GITHUB_TOKEN and the
released version. The standalone smoke also resolves the released artifacts.
Record artifact digests and successful authenticated package retrieval before
calling that release usable. Do not infer Stage/Production approval from a library
release.
