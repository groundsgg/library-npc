package gg.grounds.npc.importer.internal.blockbench

import gg.grounds.npc.core.animation.NpcPoseSampler
import gg.grounds.npc.core.io.*
import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcModel
import gg.grounds.npc.importer.internal.*
import java.nio.file.*

internal data class BlockbenchImport(
    val model: NpcModel,
    val modelJson: ByteArray,
    val resources: Map<String, ByteArray>,
)

internal object BlockbenchReader {
    fun read(
        source: Path,
        id: String,
        inputRoot: Path,
        budget: ImportBudget = ImportBudget(),
    ): BlockbenchImport {
        val path = source.toAbsolutePath().normalize()
        val context = ImportContext(source.toString())
        if (Files.isSymbolicLink(path))
            fail(context.source, "$", "Symlink model input is unsupported")
        val bytes = Files.newInputStream(path).use { it.readNBytes(16 * 1024 * 1024 + 1) }
        val root = context.parse(bytes)
        return context.at("$") {
            require(
                id.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")) &&
                    id.length <= 256 &&
                    id.substringAfter(':').split('/').none {
                        it == "." || it == ".." || it.isEmpty()
                    }
            ) {
                "Invalid namespaced model ID"
            }
            val meta = root.getAsJsonObject("meta")
            val version = meta["format_version"].asString
            require(version in listOf("5.0", "4.10")) {
                "Unsupported Blockbench format version $version"
            }
            require(meta["model_format"].asString in listOf("free", "generic")) {
                "Only generic/free Blockbench projects are supported"
            }
            val resources = linkedMapOf<String, ByteArray>()
            val textures =
                BlockbenchTextures(
                        context,
                        path,
                        inputRoot.toAbsolutePath().normalize(),
                        id,
                        budget,
                    )
                    .read(root, resources)
            val geometry = BlockbenchGeometry(context, id, root, textures, resources, budget)
            geometry.read()
            val bones = geometry.bones.map { it.asJsonObject["id"].asString }.toSet()
            val animations = BlockbenchAnimations.read(root, bones, version == "4.10", context)
            val json =
                ImportJson.objectOf(
                    "schemaVersion" to 1,
                    "asset" to id,
                    "bounds" to
                        (geometry.authoredBounds()
                            ?: ImportJson.objectOf(
                                "center" to listOf(0.0, 0.0, 0.0),
                                "size" to listOf(1.0, 1.0, 1.0),
                            )),
                    "bones" to geometry.bones,
                    "parts" to geometry.parts,
                    "animations" to animations,
                    "sounds" to emptyMap<String, Any>(),
                )
            fun encodedModel(): ByteArray =
                ImportJson.bytes(json).also {
                    if (it.size > 2 * 1024 * 1024)
                        fail(
                            context.source,
                            "$",
                            "Compiled model exceeds 2 MiB",
                            "NPC_IMPORT_LIMIT",
                        )
                }
            fun decode(): NpcModel {
                val decoded = NpcModelJson.decode(encodedModel())
                if (decoded is NpcDecodeResult.Success) return decoded.model
                val problem = (decoded as NpcDecodeResult.Failure).problems.first()
                fail(context.source, problem.path, problem.message)
            }
            var model = decode()
            if (geometry.authoredBounds() == null) {
                val points = mutableListOf<NpcVector3>()
                for ((i, matrix) in NpcPoseSampler.sample(model, null, 0.0).parts.withIndex()) {
                    val half = geometry.halfSizes[i]
                    for (x in listOf(-half[0], half[0])) for (y in
                        listOf(-half[1], half[1])) for (z in listOf(-half[2], half[2])) points.add(
                        matrix.point(NpcVector3(x, y, z))
                    )
                }
                val axes = listOf(points.map { it.x }, points.map { it.y }, points.map { it.z })
                json.add(
                    "bounds",
                    ImportJson.objectOf(
                        "center" to axes.map { (it.min() + it.max()) / 2 },
                        "size" to axes.map { it.max() - it.min() },
                    ),
                )
                model = decode()
            }
            BlockbenchImport(model, encodedModel(), resources)
        }
    }
}
