package gg.grounds.npc.minestom.spi

import net.minestom.server.item.ItemStack

/**
 * Application-owned mapping of namespaced part model IDs to item meshes. The library performs no
 * resource discovery.
 */
public fun interface NpcMeshResolver {
    /**
     * Resolve a namespaced part model ID synchronously during preparation. Return its ItemStack;
     * throwing rejects preparation and drains allocated parts. No callback runs under the NPC
     * ownership monitor.
     */
    public fun resolve(modelKey: String): ItemStack
}
