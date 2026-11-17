package gg.grounds.npc.cli

import gg.grounds.npc.core.io.content.NpcContentLoader
import java.nio.file.*
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class NpcCliTest {
    @TempDir lateinit var temp: Path

    private data class Run(val exit: Int, val output: String, val error: String)

    private fun run(vararg args: String, jar: Boolean = false): Run {
        val prefix =
            if (jar) listOf("-jar", System.getProperty("cliExecutable"))
            else
                listOf(
                    "-cp",
                    System.getProperty("cliRuntimeClasspath"),
                    "gg.grounds.npc.cli.NpcCli",
                )
        val out = temp.resolve("stdout")
        val err = temp.resolve("stderr")
        val process =
            ProcessBuilder(
                    listOf(Path.of(System.getProperty("java.home"), "bin", "java").toString()) +
                        prefix +
                        args
                )
                .redirectOutput(out.toFile())
                .redirectError(err.toFile())
                .start()
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "CLI process timed out")
        return Run(process.exitValue(), Files.readString(out), Files.readString(err))
    }

    private fun source(): Path =
        Files.copy(
            Path.of(javaClass.getResource("/models/cube.bbmodel")!!.toURI()),
            temp.resolve("cube.bbmodel"),
            StandardCopyOption.REPLACE_EXISTING,
        )

    private fun options(input: Path) =
        arrayOf("--input", input.toString(), "--namespace", "demo", "--target", "26.3")

    @Test
    fun executableJarIncludesDependenciesAndDocumentsItsCommands() {
        val result = run("--help", jar = true)
        assertEquals(0, result.exit, result.error)
        assertTrue(result.output.contains("compile"))
        assertTrue(result.output.contains("validate"))
        JarFile(System.getProperty("cliExecutable")).use { jar ->
            assertNotNull(jar.getJarEntry("gg/grounds/npc/importer/NpcModelImporter.class"))
            assertTrue(jar.entries().asSequence().none { it.name.startsWith("net/minestom/") })
        }
    }

    @Test
    fun compilesARealPackAndRuntimeBundleThroughTheActualEntrypoint() {
        val input = source()
        val output = temp.resolve("compiled")
        val result = run("compile", *options(input), "--output", output.toString(), jar = true)
        assertEquals(0, result.exit, result.error)
        val content = NpcContentLoader.load(output.resolve("server-content.zip"))
        content.verifyResourcePack(output.resolve("resourcepack.zip"))
        assertEquals(setOf("demo:npc/cube"), content.models.keys)
    }

    @Test
    fun validationUsesNoOutputAndReturnsUsefulJsonDiagnostics() {
        val input = source()
        val result = run("validate", *options(input), "--json")
        assertEquals(0, result.exit, result.error)
        assertTrue(result.output.contains("valid"))
        assertFalse(Files.exists(temp.resolve("compiled")))
        Files.writeString(input, "{")
        val invalid = run("validate", *options(input), "--json")
        assertEquals(3, invalid.exit)
        assertTrue(invalid.error.contains("code"))
        assertTrue(invalid.error.contains("source"))
        assertFalse(invalid.error.contains("at gg.grounds"))
    }

    @Test
    fun separatesUsageFilesystemAndContentFailures() {
        assertEquals(2, run("compile", "--unknown").exit)
        assertEquals(2, run("validate", "--input").exit)
        assertEquals(2, run("validate", *options(source()), "--target", "99.0").exit)
        assertEquals(4, run("validate", *options(temp.resolve("missing.bbmodel"))).exit)
    }

    @Test
    fun configurationErrorsAreBoundedAuthoredFieldDiagnostics() {
        val input = source()
        val config = temp.resolve("import.json")
        val cases =
            listOf(
                "{\"schemaVersion\":1,\"models\":[]}" to "$.models",
                "{\"schemaVersion\":1.5}" to "$.schemaVersion",
                "{\"schemaVersion\":\"1\"}" to "$.schemaVersion",
                "{\"schemaVersion\":1,\"models\":{\"cube.bbmodel\":17}}" to "$.models.cube.bbmodel",
                "{\"schemaVersion\":1,\"sounds\":{}}" to "$.sounds",
                ("{\"schemaVersion\":1}" + " ".repeat(4 * 1024 * 1024)) to "$",
            )
        for ((json, field) in cases) {
            Files.writeString(config, json)
            val result = run("validate", *options(input), "--config", config.toString(), "--json")
            assertEquals(3, result.exit, result.error)
            assertTrue(result.error.contains(config.toString()), result.error)
            assertTrue(result.error.contains(field), result.error)
            assertFalse(result.error.contains("NPC_CLI_INTERNAL"), result.error)
        }
    }

    @Test
    fun leavesExistingOutputUntouchedOnFailure() {
        val input = source()
        val output = Files.createDirectory(temp.resolve("existing"))
        Files.writeString(output.resolve("keep"), "original")
        val result = run("compile", *options(input), "--output", output.toString())
        assertEquals(3, result.exit)
        assertEquals("original", Files.readString(output.resolve("keep")))
    }
}
