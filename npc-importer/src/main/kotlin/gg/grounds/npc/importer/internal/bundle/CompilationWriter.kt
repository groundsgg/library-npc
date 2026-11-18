package gg.grounds.npc.importer.internal.bundle

import gg.grounds.npc.importer.internal.*
import gg.grounds.npc.importer.model.NpcImportOutput
import java.nio.file.*
import java.util.Comparator
import java.util.zip.*

internal object CompilationWriter {
    fun write(plan: CompilationPlan, output: Path): NpcImportOutput {
        val target = output.toAbsolutePath().normalize()
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS))
            fail(
                target.toString(),
                "$",
                "Output already exists; choose a new directory",
                "NPC_OUTPUT_EXISTS",
            )
        Files.createDirectories(target.parent)
        val staging = Files.createTempDirectory(target.parent, ".npc-import-")
        try {
            val server = zip(plan.server)
            val pack = zip(plan.pack)
            Files.write(staging.resolve("server-content.zip"), server)
            Files.write(staging.resolve("resourcepack.zip"), pack)
            Files.createDirectory(staging.resolve("pack-fragment"))
            for ((name, bytes) in plan.fragment) {
                val file = staging.resolve("pack-fragment").resolve(name)
                Files.createDirectories(file.parent)
                Files.write(file, bytes)
            }
            val manifest =
                ImportJson.objectOf(
                    "schemaVersion" to 1,
                    "contentId" to plan.contentId,
                    "compilerVersion" to ImporterVersion.value,
                    "target" to plan.target,
                    "serverContent" to
                        ImportJson.objectOf(
                            "file" to "server-content.zip",
                            "sha256" to ImportJson.hash(server),
                        ),
                    "resourcePack" to
                        ImportJson.objectOf(
                            "file" to "resourcepack.zip",
                            "sha256" to ImportJson.hash(pack),
                        ),
                    "basePackId" to plan.baseId,
                )
            Files.write(staging.resolve("manifest.json"), ImportJson.bytes(manifest))
            Files.move(staging, target)
        } catch (e: Exception) {
            try {
                if (Files.exists(staging))
                    Files.walk(staging).use {
                        it.sorted(Comparator.reverseOrder()).forEach { p -> Files.delete(p) }
                    }
            } catch (cleanup: Exception) {
                e.addSuppressed(cleanup)
            }
            throw e
        }
        return NpcImportOutput(
            target.resolve("server-content.zip"),
            target.resolve("resourcepack.zip"),
            target.resolve("pack-fragment"),
            target.resolve("manifest.json"),
            plan.contentId,
        )
    }

    private fun zip(files: Map<String, ByteArray>): ByteArray =
        java.io
            .ByteArrayOutputStream()
            .also { bytes ->
                ZipOutputStream(bytes).use { zip ->
                    for ((name, data) in files.toSortedMap()) {
                        zip.putNextEntry(ZipEntry(name).apply { time = 0L })
                        zip.write(data)
                        zip.closeEntry()
                    }
                }
            }
            .toByteArray()
}
