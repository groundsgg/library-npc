import java.util.zip.ZipFile

plugins { application }

dependencies {
    implementation(project(":npc-importer"))
    implementation("com.google.code.gson:gson:2.13.2")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application { mainClass.set("gg.grounds.npc.cli.NpcCli") }

tasks.test {
    systemProperty("cliRuntimeClasspath", sourceSets.main.get().runtimeClasspath.asPath)
    systemProperty(
        "cliExecutable",
        layout.buildDirectory
            .file("libs/npc-cli-${project.version}-all.jar")
            .get()
            .asFile
            .absolutePath,
    )
}

// Assemble dependencies explicitly so differing duplicate classes fail instead of being hidden.
// Preserve license provenance and combine service provider declarations in deterministic order.
val executableDependencies = layout.buildDirectory.dir("generated/executable-dependencies")
val prepareExecutableDependencies by tasks.registering {
    inputs.files(configurations.runtimeClasspath)
    outputs.dir(executableDependencies)
    doLast {
        val destination = executableDependencies.get().asFile
        delete(destination)
        val entries = sortedMapOf<String, ByteArray>()
        val services = sortedMapOf<String, LinkedHashSet<String>>()
        for (artifact in configurations.runtimeClasspath.get().sortedBy { it.name }) {
            ZipFile(artifact).use { archive ->
                val iterator = archive.entries()
                while (iterator.hasMoreElements()) {
                    val entry = iterator.nextElement()
                    if (entry.isDirectory) continue
                    val original = entry.name
                    if (
                        original == "META-INF/MANIFEST.MF" ||
                            original == "META-INF/INDEX.LIST" ||
                            original.endsWith("module-info.class") ||
                            original.matches(
                                Regex("META-INF/[^/]+\\.(SF|RSA|DSA)", RegexOption.IGNORE_CASE)
                            )
                    )
                        continue
                    val bytes = archive.getInputStream(entry).use { it.readAllBytes() }
                    if (original.startsWith("META-INF/services/")) {
                        val providers = services.getOrPut(original) { linkedSetOf() }
                        bytes
                            .toString(Charsets.UTF_8)
                            .lineSequence()
                            .map { it.substringBefore('#').trim() }
                            .filter { it.isNotEmpty() }
                            .forEach { providers.add(it) }
                        continue
                    }
                    val leaf = original.substringAfterLast('/').uppercase()
                    val name =
                        if (
                            leaf.startsWith("LICENSE") ||
                                leaf.startsWith("NOTICE") ||
                                leaf.startsWith("COPYING")
                        )
                            "META-INF/licenses/${artifact.name}/$original"
                        else original
                    val previous = entries[name]
                    check(previous == null || previous.contentEquals(bytes)) {
                        "Differing executable dependency entry: $name (${artifact.name})"
                    }
                    entries[name] = bytes
                }
            }
        }
        for ((name, providers) in services) entries[name] =
            (providers.joinToString("\n") + "\n").toByteArray()
        for ((name, bytes) in entries) {
            val output = destination.resolve(name)
            output.parentFile.mkdirs()
            output.writeBytes(bytes)
        }
    }
}
val executableJar by
    tasks.registering(Jar::class) {
        dependsOn(tasks.classes, prepareExecutableDependencies)
        archiveClassifier.set("all")
        duplicatesStrategy = DuplicatesStrategy.FAIL
        manifest.attributes(
            "Main-Class" to application.mainClass.get(),
            "Multi-Release" to "true",
            "Implementation-Version" to project.version.toString(),
        )
        from(sourceSets.main.get().output)
        from(executableDependencies)
    }

tasks.assemble { dependsOn(executableJar) }

tasks.test { dependsOn(executableJar) }
