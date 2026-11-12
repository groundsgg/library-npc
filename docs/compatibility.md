# Compatibility

| Target | Local evidence |
| --- | --- |
| Core | JDK 25; Kotlin 2.2.20; Java/Kotlin JVM 24 bytecode; 14 tests |
| Public adapter | Minestom 2026.10.07-26.2; Java 25 runtime; 74 renderer tests and standalone smoke |
| Grounds adapter | Compiles/tests pass against 2026.11.08-26.3-grounds; standalone startup/move/close smoke passes |
| Java/Kotlin consumers | Separate Gradle projects resolving the local Maven candidate; both run |
| Numeric content | Existing schema 1; original synthetic fixtures only |
| Scene/Lobby integration | Local draft suites pass; release/client acceptance still pending |
| Real 26.3 client | Visual acceptance still pending |
| Anonymous public download | Not released; no claim of public availability |

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

Known follow-ups from the independent review: custom shared sampling intervals
above 500 ms remain capped at 500 ms; the original 200/250/500 ms
Grounds profile is verified. The synchronous parity-test tick driver can block
after its timeout, and the Lobby operator README still mentions the old substituted
content module. These are recorded minor follow-ups; they do not change release
or client acceptance status.
