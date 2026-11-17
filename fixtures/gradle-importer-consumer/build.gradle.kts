plugins {
    java
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

val npcCompiler = configurations.create("npcCompiler")

dependencies {
    implementation("gg.grounds:npc-core:${npcVersion.get()}")
    implementation("gg.grounds:npc-importer:${npcVersion.get()}")
    implementation("gg.grounds:npc-cli:${npcVersion.get()}")
    add(npcCompiler.name, "gg.grounds:npc-cli:${npcVersion.get()}")
}

val compiledContent = layout.buildDirectory.dir("npc")
val compileNpcModels =
    tasks.register<JavaExec>("compileNpcModels") {
        classpath = npcCompiler
        mainClass.set("gg.grounds.npc.cli.NpcCli")
        javaLauncher.set(
            javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) }
        )
        inputs.dir(layout.projectDirectory.dir("models"))
        inputs.property("npcVersion", npcVersion)
        inputs.property("target", "26.3")
        inputs.property("namespace", "example")
        outputs.dir(compiledContent)
        doFirst {
            // This task owns this generated build directory; the CLI itself never overwrites
            // outputs.
            delete(compiledContent)
            args(
                "compile",
                "--input",
                layout.projectDirectory.dir("models").asFile.absolutePath,
                "--namespace",
                "example",
                "--target",
                "26.3",
                "--output",
                compiledContent.get().asFile.absolutePath,
            )
        }
    }

application { mainClass.set("consumer.GradleImportConsumer") }

tasks.named<JavaExec>("run") {
    dependsOn(compileNpcModels)
    args(
        compiledContent.get().asFile.absolutePath,
        layout.projectDirectory.dir("models").asFile.absolutePath,
    )
}
