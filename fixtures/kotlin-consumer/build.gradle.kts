plugins {
    kotlin("jvm") version "2.2.20"
    application
}

repositories {
    providers.gradleProperty("npcRepository").orNull?.let { maven { url = uri(it) } }
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

dependencies { implementation("gg.grounds:npc-core:0.1.0-SNAPSHOT") }

application { mainClass = "consumer.KotlinConsumerKt" }
