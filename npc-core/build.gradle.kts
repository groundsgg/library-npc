dependencies {
    implementation("com.google.code.gson:gson:2.13.2")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Keep the allocation budget independent of when the VM finishes C2 compilation.
val allocationCheck by
    tasks.registering(Test::class) {
        description = "Verify pose allocation limits with only the first JIT tier enabled"
        group = "verification"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        filter { includeTestsMatching("gg.grounds.npc.core.NpcSamplingTest") }
        jvmArgs("-XX:TieredStopAtLevel=1")
    }

tasks.check { dependsOn(allocationCheck) }
