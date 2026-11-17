dependencies {
    api(project(":npc-core"))
    implementation("com.google.code.gson:gson:2.13.2")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val importerVersionDirectory = layout.buildDirectory.dir("generated/importer-version")
val generateImporterVersion by tasks.registering {
    inputs.property("importerVersion", project.version.toString())
    outputs.dir(importerVersionDirectory)
    doLast {
        val file =
            importerVersionDirectory.get().file("gg/grounds/npc/importer/version.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=${project.version}\n")
    }
}

sourceSets.main { resources.srcDir(importerVersionDirectory) }

tasks.processResources { dependsOn(generateImporterVersion) }
