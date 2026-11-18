package consumer

import gg.grounds.npc.core.io.content.NpcContentLoader
import gg.grounds.npc.importer.NpcModelImporter
import gg.grounds.npc.importer.config.NpcImportOptions
import gg.grounds.npc.importer.config.NpcMinecraftTarget
import java.nio.file.Path

fun main(args: Array<String>) {
    val options =
        NpcImportOptions.builder(Path.of(args[0]), "example", NpcMinecraftTarget.MINECRAFT_26_3)
            .build()
    check(NpcModelImporter.validate(options).isEmpty())
    val output = NpcModelImporter.compile(options, Path.of(args[1]))
    val content = NpcContentLoader.load(output.serverContent)
    content.verifyResourcePack(output.resourcePack)
    check(content.models.getValue("example:npc/cube").parts.size == 1)
    println("NPC_CONTENT ${content.contentId}")
    for ((name, type) in
        listOf(
            "npc-core" to NpcContentLoader::class.java,
            "npc-importer" to NpcModelImporter::class.java,
        )) {
        println("NPC_ORIGIN $name ${Path.of(type.protectionDomain.codeSource.location.toURI())}")
    }
}
