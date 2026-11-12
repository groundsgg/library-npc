package gg.grounds.npc.minestom;

import net.minestom.server.entity.Player;

final class JavaPresentationConsumer {
    static NpcPresentationStart prepare(NpcHandle parent, Player player) {
        return NpcPresentations.prepare(parent, new NpcPresentationOptions(player));
    }
}
