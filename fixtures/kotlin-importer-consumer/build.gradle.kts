plugins {
    kotlin("jvm") version "2.2.20"
    application
}

val npcVersion = providers.gradleProperty("npcVersion")

repositories {
    val artifactRepository = maven {
        url = uri(providers.gradleProperty("npcRepository").get())
        if (url.toString() == "https://maven.pkg.github.com/groundsgg/library-npc") {
            credentials {
                username =
                    providers.gradleProperty("github.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password =
                    providers.gradleProperty("github.token").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
    exclusiveContent {
        forRepositories(artifactRepository)
        filter {
            for (module in listOf("npc-core", "npc-importer", "npc-cli")) includeModule(
                "gg.grounds",
                module,
            )
        }
    }
    mavenCentral()
}

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

dependencies {
    implementation("gg.grounds:npc-importer:${npcVersion.get()}")
}

kotlin {
    jvmToolchain(25)
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
}

java {
    sourceCompatibility = JavaVersion.VERSION_24
    targetCompatibility = JavaVersion.VERSION_24
}

application { mainClass.set("consumer.ImportConsumerKt") }

val contentOutput = layout.buildDirectory.dir("npc")

tasks.named<JavaExec>("run") {
    doFirst {
        delete(contentOutput)
        args(
            layout.projectDirectory.dir("models").asFile.absolutePath,
            contentOutput.get().asFile.absolutePath,
        )
    }
}
