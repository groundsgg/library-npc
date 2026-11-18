import java.io.File

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
                includeModule("gg.grounds", "npc-importer")
                includeModule("gg.grounds", "npc-cli")
            }
        }
    }
    mavenCentral()
}

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

dependencies {
    implementation(
        "gg.grounds:npc-minestom:${providers.gradleProperty("npcVersion").orElse(providers.fileContents(layout.projectDirectory.file("../../version.txt")).asText.map { it.trim() }).get()}"
    )
    implementation(
        "gg.grounds:npc-importer:${providers.gradleProperty("npcVersion").orElse(providers.fileContents(layout.projectDirectory.file("../../version.txt")).asText.map { it.trim() }).get()}"
    )
    implementation("net.minestom:minestom:2026.10.07-26.2")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application { mainClass = "example.NpcDemo" }

tasks.withType<JavaExec>().configureEach {
    systemProperty("java.net.preferIPv4Stack", "true")
    providers.gradleProperty("npcPort").orNull?.let { systemProperty("npc.port", it) }
    providers.gradleProperty("npcPackPort").orNull?.let { systemProperty("npc.packPort", it) }
}

tasks.register<Zip>("demoPack") {
    from("pack")
    archiveFileName = "npc-demo.zip"
    destinationDirectory = layout.buildDirectory.dir("pack")
}

val demoPack = tasks.named<Zip>("demoPack")

tasks.named<JavaExec>("run") {
    dependsOn(demoPack)
    doFirst { systemProperty("npc.demoPack", demoPack.get().archiveFile.get().asFile.absolutePath) }
}

val groundsClasspathFile = providers.gradleProperty("groundsClasspathFile")

tasks.register<Zip>("groundsDemoPack") {
    from("pack") { exclude("pack.mcmeta") }
    from("pack-grounds")
    archiveFileName = "npc-demo-26.3.zip"
    destinationDirectory = layout.buildDirectory.dir("pack")
}

tasks.register<JavaExec>("runGroundsProfile") {
    dependsOn(tasks.classes, tasks.named("groundsDemoPack"))
    mainClass = "example.NpcDemo"
    doFirst {
        require(groundsClasspathFile.isPresent) {
            "Supply the complete local Grounds 26.3 classpath file"
        }
        val local = file(groundsClasspathFile.get()).readText().trim().split(File.pathSeparator)
        classpath =
            sourceSets.main.get().output +
                files(local) +
                configurations.runtimeClasspath.get().filter {
                    it.name.startsWith("npc-") || it.name.startsWith("kotlin-stdlib")
                }
        systemProperty(
            "npc.demoPack",
            tasks.named<Zip>("groundsDemoPack").get().archiveFile.get().asFile.absolutePath,
        )
    }
}

tasks.test { useJUnitPlatform() }

// Interactive entrypoints are separate from synthetic smoke fixtures.
tasks.register<JavaExec>("runPersonalDemo") {
    dependsOn(tasks.classes, demoPack)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("example.PersonalNpcDemo")
    doFirst { systemProperty("npc.demoPack", demoPack.get().archiveFile.get().asFile.absolutePath) }
}

val smokeSources =
    sourceSets.create("smoke") {
        compileClasspath += sourceSets.main.get().output + configurations.runtimeClasspath.get()
        runtimeClasspath += sourceSets.main.get().output + configurations.runtimeClasspath.get()
    }

tasks.register<JavaExec>("smoke") {
    dependsOn(smokeSources.classesTaskName, demoPack)
    classpath = smokeSources.runtimeClasspath
    mainClass.set("example.NpcDemoSmoke")
    doFirst { systemProperty("npc.demoPack", demoPack.get().archiveFile.get().asFile.absolutePath) }
}

tasks.register<JavaExec>("smokeGroundsProfile") {
    dependsOn(smokeSources.classesTaskName, tasks.named("groundsDemoPack"))
    mainClass.set("example.NpcDemoSmoke")
    doFirst {
        require(groundsClasspathFile.isPresent) {
            "Supply the complete local Grounds 26.3 classpath file"
        }
        val local = file(groundsClasspathFile.get()).readText().trim().split(File.pathSeparator)
        classpath =
            sourceSets.main.get().output +
                smokeSources.output +
                files(local) +
                configurations.runtimeClasspath.get().filter {
                    it.name.startsWith("npc-") || it.name.startsWith("kotlin-stdlib")
                }
        systemProperty(
            "npc.demoPack",
            tasks.named<Zip>("groundsDemoPack").get().archiveFile.get().asFile.absolutePath,
        )
    }
}

tasks.test {
    systemProperty("npc.importSources", layout.projectDirectory.dir("models").asFile.absolutePath)
    systemProperty("npc.importBase", layout.projectDirectory.dir("base-pack").asFile.absolutePath)
}

val npcCompiler = configurations.create("npcCompiler")

dependencies {
    add(
        npcCompiler.name,
        "gg.grounds:npc-cli:${providers.gradleProperty("npcVersion").orElse(providers.fileContents(layout.projectDirectory.file("../../version.txt")).asText.map { it.trim() }).get()}",
    )
}

fun compileImportedExample(name: String, target: String) =
    tasks.register<JavaExec>(name) {
        classpath = npcCompiler
        mainClass.set("gg.grounds.npc.cli.NpcCli")
        val output = layout.buildDirectory.dir("imported/$target")
        inputs.dir(layout.projectDirectory.dir("models"))
        inputs.dir(layout.projectDirectory.dir("base-pack"))
        inputs.property("target", target)
        outputs.dir(output)
        doFirst {
            delete(output)
            args(
                "compile",
                "--input",
                layout.projectDirectory.dir("models").asFile.absolutePath,
                "--namespace",
                "npc_demo",
                "--target",
                target,
                "--base-pack",
                layout.projectDirectory.dir("base-pack").asFile.absolutePath,
                "--config",
                layout.projectDirectory.file("models/import.json").asFile.absolutePath,
                "--output",
                output.get().asFile.absolutePath,
            )
        }
    }

val compileDemoNpc = compileImportedExample("compileDemoNpc", "26.2")
val compileGroundsDemoNpc = compileImportedExample("compileGroundsDemoNpc", "26.3")

fun importedExample(name: String, grounds: Boolean, prepared: Boolean, smoke: Boolean) {
    tasks.register<JavaExec>(name) {
        dependsOn(if (smoke) smokeSources.classesTaskName else tasks.classes)
        if (prepared) dependsOn(if (grounds) compileGroundsDemoNpc else compileDemoNpc)
        mainClass.set(if (smoke) "example.ImportedNpcDemoSmoke" else "example.ImportedNpcDemo")
        classpath =
            if (smoke) smokeSources.runtimeClasspath else sourceSets.main.get().runtimeClasspath
        systemProperty("npc.importTarget", if (grounds) "26.3" else "26.2")
        systemProperty(
            "npc.importSources",
            layout.projectDirectory.dir("models").asFile.absolutePath,
        )
        systemProperty(
            "npc.importBase",
            layout.projectDirectory.dir("base-pack").asFile.absolutePath,
        )
        if (prepared)
            systemProperty(
                "npc.precompiledContent",
                layout.buildDirectory
                    .dir("imported/${if (grounds) "26.3" else "26.2"}")
                    .get()
                    .asFile
                    .absolutePath,
            )
        if (grounds)
            doFirst {
                require(groundsClasspathFile.isPresent) {
                    "Supply the complete Grounds 26.3 classpath file"
                }
                val local =
                    file(groundsClasspathFile.get()).readText().trim().split(File.pathSeparator)
                classpath =
                    sourceSets.main.get().output +
                        (if (smoke) smokeSources.output else files()) +
                        files(local) +
                        configurations.runtimeClasspath.get().filter {
                            it.name.startsWith("npc-") || it.name.startsWith("kotlin-stdlib")
                        }
            }
    }
}

importedExample("runImportedDemo", false, false, false)

importedExample("runPrecompiledDemo", false, true, false)

importedExample("runImportedGroundsDemo", true, false, false)

importedExample("runPrecompiledGroundsDemo", true, true, false)

importedExample("smokeImported", false, false, true)

importedExample("smokePrecompiled", false, true, true)

importedExample("smokeImportedGrounds", true, false, true)

importedExample("smokePrecompiledGrounds", true, true, true)
