plugins {
    kotlin("jvm") version "2.2.20"
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

kotlin {
    jvmToolchain(25)
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
}

java {
    sourceCompatibility = JavaVersion.VERSION_24
    targetCompatibility = JavaVersion.VERSION_24
}

dependencies {
    implementation(
        "gg.grounds:npc-minestom:${providers.gradleProperty("npcVersion").getOrElse("0.1.0-SNAPSHOT")}"
    )
    implementation("net.minestom:minestom:2026.10.07-26.2")
}

configurations.configureEach {
    attributes.attribute(
        org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
        25,
    )
}

application { mainClass = "consumer.KotlinConsumerKt" }
