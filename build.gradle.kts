import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { kotlin("jvm") version "2.2.20" apply false }

group = "gg.grounds"

version = providers.gradleProperty("versionOverride").orElse("0.1.0-SNAPSHOT").get()

repositories { mavenCentral() }

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    group = rootProject.group
    version = rootProject.version
    repositories { mavenCentral() }
    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(25)
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
