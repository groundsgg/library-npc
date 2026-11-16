package gg.grounds.npc.minestom;

import gg.grounds.npc.minestom.presentation.NpcPresentationOptions;
import gg.grounds.npc.minestom.presentation.NpcPresentationStart;

import net.minestom.server.entity.Player;

final class JavaPresentationConsumer {
    static NpcPresentationStart prepare(NpcHandle parent, Player player) {
        return parent.preparePresentation(NpcPresentationOptions.builder(player).build());
    }
}
