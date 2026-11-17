import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.20" apply false
    id("org.jetbrains.dokka") version "2.2.0"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.2"
}

group = "gg.grounds"

version =
    providers
        .gradleProperty("versionOverride")
        .orElse(
            providers.fileContents(layout.projectDirectory.file("version.txt")).asText.map {
                it.trim()
            }
        )
        .get()

repositories { mavenCentral() }

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.dokka")
    group = rootProject.group
    version = rootProject.version
    repositories { mavenCentral() }
    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(25)
        explicitApi()
    }
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_24
        targetCompatibility = JavaVersion.VERSION_24
        withSourcesJar()
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_24)
    }
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
    tasks.withType<org.gradle.jvm.tasks.Jar>().configureEach {
        from(rootProject.file("LICENSE")) { into("META-INF") }
        from(rootProject.file("NOTICE")) { into("META-INF") }
    }
    dependencyLocking { lockAllConfigurations() }
    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
                pom {
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/license/mit")
                            distribution.set("repo")
                        }
                    }
                }
            }
        }
        repositories {
            maven {
                name = "Review"
                url = rootProject.layout.projectDirectory.dir(".evidence/maven").asFile.toURI()
            }
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/groundsgg/library-npc")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}

val kotlinFormatter =
    configurations.create("kotlinFormatter") {
        attributes.attribute(
            org.gradle.api.attributes.Bundling.BUNDLING_ATTRIBUTE,
            objects.named(org.gradle.api.attributes.Bundling.SHADOWED),
        )
    }

dependencies { add(kotlinFormatter.name, "com.facebook:ktfmt:0.63") }

tasks.register<JavaExec>("formatKotlin") {
    classpath = kotlinFormatter
    mainClass.set("com.facebook.ktfmt.cli.Main")
    doFirst {
        args =
            listOf("--kotlinlang-style") +
                fileTree(projectDir) {
                        include("**/*.kt", "**/*.kts")
                        exclude(".evidence/**", ".gradle/**", ".kotlin/**", "**/build/**")
                    }
                    .files
                    .sortedBy { it.path }
                    .map { it.absolutePath }
    }
}

val javaFormatter =
    configurations.create("javaFormatter") {
        attributes.attribute(
            org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE,
            objects.named(org.gradle.api.attributes.Usage.JAVA_RUNTIME),
        )
        attributes.attribute(
            org.gradle.api.attributes.java.TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
            objects.named(org.gradle.api.attributes.java.TargetJvmEnvironment.STANDARD_JVM),
        )
    }

dependencies { add(javaFormatter.name, "com.google.googlejavaformat:google-java-format:1.35.0") }

tasks.register<JavaExec>("formatJava") {
    classpath = javaFormatter
    mainClass.set("com.google.googlejavaformat.java.Main")
    jvmArgs(
        listOf("api", "file", "parser", "tree", "util", "code").map {
            "--add-exports=jdk.compiler/com.sun.tools.javac.$it=ALL-UNNAMED"
        }
    )
    doFirst {
        args =
            listOf("--replace") +
                fileTree(projectDir) {
                        include("**/*.java")
                        exclude(".evidence/**", ".superpowers/**", ".gradle/**", "**/build/**")
                    }
                    .files
                    .sortedBy { it.path }
                    .map { it.absolutePath }
    }
}

subprojects {
    extensions.configure<org.jetbrains.dokka.gradle.DokkaExtension> {
        dokkaPublications.named("html") { failOnWarning.set(true) }
        dokkaSourceSets.configureEach {
            reportUndocumented.set(true)
            perPackageOption {
                matchingRegex.set(".*\\.internal(?:\\..*)?")
                suppress.set(true)
            }
        }
    }
}

dependencies {
    dokka(project(":npc-core"))
    dokka(project(":npc-minestom"))
}

// BCV still requests ASM 9.6, which cannot read our established JVM-24 bytecode.
subprojects {
    configurations
        .matching { it.name.startsWith("bcv-rt-jvm-cp") }
        .configureEach {
            resolutionStrategy.eachDependency {
                if (requested.group == "org.ow2.asm") useVersion("9.9.1")
            }
        }
}
