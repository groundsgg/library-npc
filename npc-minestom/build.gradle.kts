import java.io.File

configurations.configureEach {
    attributes.attribute(
        org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
        25,
    )
}

// Local compatibility profile only; file dependencies never enter published metadata.
val groundsClasspathFile = providers.gradleProperty("groundsClasspathFile")
val groundsClasspath = groundsClasspathFile.map { path ->
    file(path).readText().trim().split(File.pathSeparator)
}

if (groundsClasspathFile.isPresent) {
    dependencyLocking {
        lockFile.set(rootProject.layout.projectDirectory.file(".evidence/grounds-gradle.lockfile"))
    }
}

dependencies {
    api(project(":npc-core"))
    if (groundsClasspathFile.isPresent) {
        compileOnly(files(groundsClasspath))
        testImplementation(files(groundsClasspath))
    } else {
        compileOnly("net.minestom:minestom:2026.10.07-26.2")
        testImplementation("net.minestom:minestom:2026.10.07-26.2")
    }
    testImplementation("net.minestom:testing:2026.10.07-26.2") {
        if (groundsClasspathFile.isPresent) exclude(group = "net.minestom", module = "minestom")
    }
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

tasks.test { systemProperty("minestom.inside-test", "true") }
