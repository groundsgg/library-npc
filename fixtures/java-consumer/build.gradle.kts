plugins {
    java
    application
}

repositories {
    providers.gradleProperty("npcRepository").orNull?.let { maven { url = uri(it) } }
    mavenCentral()
}

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

dependencies {
    implementation("gg.grounds:npc-minestom:0.1.0-SNAPSHOT")
    implementation("net.minestom:minestom:2026.10.07-26.2")
}

application { mainClass = "consumer.JavaConsumer" }
