# Compatibility

| Target | Local evidence |
| --- | --- |
| Core | JDK 25; Kotlin 2.2.20; Java/Kotlin JVM 24 bytecode; 14 tests |
| Public adapter | Minestom 2026.10.07-26.2; Java 25 runtime; 117 renderer tests and standalone smoke |
| Grounds adapter | Compiles/tests pass against 2026.11.08-26.3-grounds; standalone startup/move/close smoke passes |
| Java/Kotlin consumers | Separate Gradle projects resolving the local Maven candidate; both run |
| Numeric content | Existing schema 1; original synthetic fixtures only |
| Scene/Lobby integration | Updated local suites pass; integration PRs open; licensed-content/multiplayer/transfer gates remain |
| Real 26.3 client | Neutral two-part demo and commands confirmed by user; private models and multiplayer still pending |
| Distribution | GitHub Packages selected by owner; authenticated retrieval required; anonymous Maven not configured |

The public build requires Maven Central and JDK 25, with no Grounds credentials.
Java 25 is required by the Minestom dependency even though the extracted Kotlin
compiler emits JVM 24 bytecode. The explicit public pin is the established probe
baseline, not a claim that a public 26.3 release is unavailable.

Run the private compatibility profile with a complete existing fork classpath:

```sh
./gradlew :npc-minestom:test -PgroundsClasspathFile=/absolute/path/to/classpath.local --write-locks
```

The file contains a platform-separated list of local JAR paths. It is not checked
in, downloaded or embedded in publication metadata. Its separate lockfile is local
under `.evidence`. This profile proves compilation and server-side tests, not client
visual parity, two-player behaviour or cross-proxy transfer acceptance.

Known follow-ups from the independent review: custom shared and private sampling intervals
above 500 ms retain a 500 ms interpolation cap; the original 200/250/500 ms
Grounds profile is verified. The synchronous parity-test tick driver can block
after its timeout, and the Lobby operator README still mentions the old substituted
content module. These are recorded minor follow-ups; they do not change release
or client acceptance status.


N7 verification additionally covers two simulated private participants plus a
common observer through the independent Java demo, public and Grounds 26.3
startup/sampling/move/close, same-UUID reconnect, instance changes and visibility
reentry. These are server-side checks. Actual simultaneous client visual/sound,
licensed-content and cross-backend/proxy transfer acceptance remain pending.

Unchanged poses emitted zero additional packets in 0/1/8/32-group tests and world
player enumeration stayed constant. Measured allocations on the test thread were
approximately 2/14/55 KB per tick for 1/8/32 two-part groups and 336/533 KB for
20/32 fifty-part groups. The latter 32-group case explicitly raised the budget to
1600 parts. These are observations of the fixture, not production capacity promises.

The user confirmed neutral two-client private visual/sound isolation and common
restoration on Grounds26.3. Licensed-content and real proxy/backend transfer gates
remain open. The final review added12 lifecycle/timing/movement regressions.
