# Candidate and external publication

No external artifact has been published. The local Review repository is only an
integration candidate, not proof that anonymous consumers can download the OSS
library. No GitHub release, tag or production activation is part of this build.

```sh
./gradlew check publishAllPublicationsToReviewRepository
python3 tools/verify_public_artifacts.py
./gradlew -p fixtures/java-consumer run -PnpcRepository=file:///absolute/path/to/library-npc/.evidence/maven
./gradlew -p fixtures/kotlin-consumer run -PnpcRepository=file:///absolute/path/to/library-npc/.evidence/maven
./gradlew -p examples/standalone-minestom run --args=--smoke -PnpcRepository=file:///absolute/path/to/library-npc/.evidence/maven
```

The project owner selected MIT for the extracted library on 2026-11-12; see
[licensing decision](license-decision.md). Binary/source JARs include the MIT
licence and provenance notice, and the POM declares MIT. Third-party dependencies
keep their own licences.

Before external publication: settle project/coordinate names and repository/version;
inspect JAR/POM/module metadata and complete the remaining integration acceptance.
The independent review is complete and the neutral 26.3 demo was accepted by the
user; private-content, multiplayer and transfer acceptance remain open. Obtain the
explicit publication decision for the concrete candidate.

After release, rerun both consumer projects with only the public repository and
empty caches, without file-Maven or composite substitution. Record exact artifact
digests and anonymous retrieval evidence before calling the OSS release usable.
