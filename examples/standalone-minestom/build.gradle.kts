import java.io.File

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
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application { mainClass = "example.NpcDemo" }

tasks.withType<JavaExec>().configureEach { systemProperty("java.net.preferIPv4Stack", "true") }

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
