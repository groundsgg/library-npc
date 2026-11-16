package gg.grounds.npc.minestom.spi

import net.minestom.server.item.ItemStack

fun interface NpcMeshResolver {
    fun resolve(modelKey: String): ItemStack
}
