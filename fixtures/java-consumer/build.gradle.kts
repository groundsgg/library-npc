plugins {
    java
    application
}

repositories {
    providers.gradleProperty("npcRepository").orNull?.let { location ->
        val npcArtifacts = maven {
            name = "NpcArtifacts"
            url = uri(location)
            if (url.toString() == "https://maven.pkg.github.com/groundsgg/library-npc") {
                credentials {
                    username =
                        providers.gradleProperty("github.user").orNull
                            ?: System.getenv("GITHUB_ACTOR")
                    password =
                        providers.gradleProperty("github.token").orNull
                            ?: System.getenv("GITHUB_TOKEN")
                }
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
    mavenCentral()
}

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

dependencies {
    implementation(
        "gg.grounds:npc-minestom:${providers.gradleProperty("npcVersion").getOrElse("0.1.0-SNAPSHOT")}"
    )
    implementation("net.minestom:minestom:2026.10.07-26.2")
}

application { mainClass = "consumer.JavaConsumer" }
