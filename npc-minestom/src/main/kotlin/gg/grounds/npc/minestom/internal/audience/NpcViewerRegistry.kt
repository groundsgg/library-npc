package gg.grounds.npc.minestom.internal.audience

import gg.grounds.npc.minestom.config.NpcRenderConfig
import gg.grounds.npc.minestom.internal.rendering.DEFAULT_NPC_VISUAL
import gg.grounds.npc.minestom.internal.rendering.NpcItemDisplay
import gg.grounds.npc.minestom.internal.rendering.NpcRenderAudience
import gg.grounds.npc.minestom.transform.NpcRenderTransform
import gg.grounds.npc.minestom.viewer.NpcViewerState

import gg.grounds.npc.core.animation.NpcLoopMode
import gg.grounds.npc.core.math.NpcMatrix
import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcClipSound
import gg.grounds.npc.core.model.NpcModel
import gg.grounds.npc.core.validation.NpcProblem
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlin.math.abs
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.instance.Instance
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule

/** Connection-owned admission, visibility cohorts, metadata overrides and suppression leases. */
internal class NpcViewerRegistry(
    private val lock: Any,
    private val displays: List<NpcItemDisplay>,
    private val config: NpcRenderConfig,
    private val audience: NpcRenderAudience,
    private val currentTransform: () -> NpcRenderTransform,
    private val isClosed: () -> Boolean,
    private val isPublished: () -> Boolean,
    private val clock: () -> Long,
    private val beforeAudienceChange: () -> Unit,
    private val afterUpdate: () -> Unit,
) {
    private val closed get() = isClosed()
    private val published get() = isPublished()
    private val transform get() = currentTransform()
    private val elapsed get() = clock()
    private val policy = NpcRenderPolicy(config)
    private var inheritedAdmission: Player? = null
    private val viewerVisuals = java.util.IdentityHashMap<Player, Pair<Double, Boolean>>()
    private var viewerIntervals = emptyMap<UUID, NpcViewerRate>()
    private var viewerPlayers = emptyMap<UUID, Player>()
    private val nextViewerUpdate = mutableMapOf<NpcViewerRate, Long>()
    private var viewerGroups = emptyMap<NpcViewerRate, List<Player>>()
    var renderInterval = 500L
    private data class Suppression(val lease: Any, val previousRate: NpcViewerRate?)

    private var suppressed: java.util.IdentityHashMap<Player, Suppression>? = null

    init {
        displays.forEach { display ->
            display.viewerState = { player -> sendViewerState(display, player) }
            display.visualState = { player -> viewerVisuals[player] ?: DEFAULT_NPC_VISUAL }
        }
    }
    fun clearInheritedAdmission() { inheritedAdmission = null }
    fun hasViewers(): Boolean = viewerIntervals.isNotEmpty()
    fun deferUpdate(time: Long): Boolean = nextViewerUpdate.size == 1 && time < nextViewerUpdate.values.first()
    fun clear() {
        viewerVisuals.clear(); suppressed = null; inheritedAdmission = null
        viewerIntervals = emptyMap(); viewerPlayers = emptyMap(); viewerGroups = emptyMap(); nextViewerUpdate.clear()
    }
    fun inheritViewerAdmission(player: Player) =
        synchronized(lock) {
            if (!closed && !published) inheritedAdmission = player
        }

    fun viewerAdmission(player: Player): Boolean? =
        synchronized(lock) {
            if (closed || !published) null
            else
                policy.canView(
                    player,
                    displays.first().instance,
                    transform.root.position,
                    retaining(player),
                )
        }

    private fun retaining(player: Player) =
        viewerPlayers[player.uuid] === player || inheritedAdmission === player

    fun viewerStateFor(player: Player): NpcViewerState =
        synchronized(lock) {
            val state = viewerVisuals[player] ?: DEFAULT_NPC_VISUAL
            NpcViewerState(state.first, state.second)
        }

    fun canPresentTo(player: Player): Boolean =
        synchronized(lock) {
            !closed &&
                published &&
                policy.canView(
                    player,
                    displays.first().instance,
                    transform.root.position,
                    retaining(player),
                )
        }

    fun suppressViewer(player: Player, lease: Any, current: () -> Boolean): Boolean = update {
        if (closed || !published || !current()) return false
        val hidden =
            suppressed ?: java.util.IdentityHashMap<Player, Suppression>().also { suppressed = it }
        val previous = hidden[player]
        if (previous != null) return previous.lease === lease
        val rate = viewerIntervals[player.uuid].takeIf { viewerPlayers[player.uuid] === player }
        hidden[player] = Suppression(lease, rate)
        refreshViewers(true)
        true
    }

    fun ownsSuppression(player: Player, lease: Any): Boolean =
        synchronized(lock) {
            suppressed?.get(player)?.lease === lease
        }

    fun releaseSuppression(
        player: Player,
        lease: Any,
        restore: Boolean,
        admission: Boolean? = null,
    ) = update {
        val hidden = suppressed ?: return
        val previous = hidden[player] ?: return
        if (previous.lease !== lease) return
        hidden.remove(player)
        if (hidden.isEmpty()) suppressed = null
        if (!closed && restore) {
            if (
                (admission ?: (previous.previousRate != null)) &&
                    player.isOnline &&
                    player.instance === displays.first().instance
            ) {
                viewerIntervals =
                    viewerIntervals +
                        (player.uuid to
                            policy.viewerRate(
                                policy.distanceSquared(
                                    player,
                                    displays.first().instance,
                                    transform.root.position,
                                ),
                                previous.previousRate,
                            ))
                viewerPlayers = viewerPlayers + (player.uuid to player)
            }
            refreshViewers(true)
        }
    }

    private fun accepts(player: Player) =
        audience.accepts(player) && suppressed?.containsKey(player) != true

    fun refreshViewers(force: Boolean = false) {
        if (!published) return
        // Overrides belong to the actual connection, not a future login with the same UUID.
        viewerVisuals.keys.removeIf { it.isRemoved || it.instance !== displays.first().instance }
        val instance = displays.first().instance
        val anchor = transform.root.position
        if (!force && viewersUnchanged(instance, anchor)) return
        var playersChanged = false
        val admittedPlayers = linkedMapOf<UUID, Player>()
        var fastestInterval = 500L
        val next = buildMap {
            audience.candidates(instance).forEach { player ->
                val distance = policy.distanceSquared(player, instance, anchor)
                if (
                    accepts(player) &&
                        policy.canView(
                            player,
                            instance,
                            anchor,
                            retaining(player),
                        )
                ) {
                    if (viewerPlayers[player.uuid] !== player) playersChanged = true
                    val previous =
                        viewerIntervals[player.uuid].takeIf {
                            viewerPlayers[player.uuid] === player
                        }
                    val rate = policy.viewerRate(distance, previous)
                    admittedPlayers[player.uuid] = player
                    put(player.uuid, rate)
                    fastestInterval = minOf(fastestInterval, rate.interval(config))
                }
            }
        }
        renderInterval = fastestInterval
        if (!force && next == viewerIntervals && !playersChanged) return
        val previous = viewerIntervals
        val resuming = previous.isEmpty() && next.isNotEmpty()
        viewerIntervals = next
        viewerPlayers = admittedPlayers
        viewerGroups = viewerPlayers.values.groupBy { next.getValue(it.uuid) }
        val intervals = next.values.toSet()
        nextViewerUpdate.keys.retainAll(intervals)
        intervals.forEach { nextViewerUpdate.putIfAbsent(it, elapsed + it.interval(config)) }
        displays.forEach { it.configureUpdates(intervals) }
        // Refresh the shared target before snapshots without forcing unrelated cohorts to send.
        if (force || resuming || next.any { (id, interval) -> previous[id] != interval })
            beforeAudienceChange()
        displays.forEach { display ->
            // Minestom's iterator already owns a stable per-display snapshot. Copying the
            // collection with toList() also resolves every player ID during size() calls.
            val previousViewers = display.viewers.iterator()
            display.updateViewableRule { player ->
                accepts(player) &&
                    player.instance === instance &&
                    player.uuid in viewerIntervals &&
                    (viewerPlayers[player.uuid] === player ||
                        policy.canView(player, instance, anchor, false))
            }
            previousViewers.forEach { player ->
                val interval = next[player.uuid]
                if (
                    interval != null &&
                        previous[player.uuid] != interval &&
                        player in display.viewers
                )
                    display.sendPoseState(player, (interval.interval(config) / 50).toInt())
            }
        }
    }

    private fun viewersUnchanged(instance: Instance, anchor: NpcVector3): Boolean {
        var admitted = 0
        var fastestInterval = 500L
        for (player in audience.candidates(instance)) {
            val previous =
                viewerIntervals[player.uuid].takeIf { viewerPlayers[player.uuid] === player }
            val distance = policy.distanceSquared(player, instance, anchor)
            if (accepts(player) && policy.canView(player, instance, anchor, retaining(player))) {
                if (
                    previous == null ||
                        viewerPlayers[player.uuid] !== player ||
                        policy.viewerRate(distance, previous) != previous
                )
                    return false
                admitted++
                fastestInterval = minOf(fastestInterval, previous.interval(config))
            }
        }
        // A weak iteration can see both sides of a reconnect; also repair its minimum.
        return admitted == viewerIntervals.size && fastestInterval == renderInterval
    }

    fun flushViewerUpdates(time: Long, force: Boolean) {
        if (nextViewerUpdate.values.none { force || time >= it }) return
        for ((rate, deadline) in nextViewerUpdate) {
            if (!force && time < deadline) continue
            val recipients = viewerGroups[rate].orEmpty()
            displays.forEach { display -> display.flushUpdates(rate, recipients) }
            nextViewerUpdate[rate] = time + rate.interval(config)
        }
    }

    fun applyViewerState(player: Player, state: NpcViewerState) {
        update {
            if (closed) return
            val previous = viewerVisuals[player]?.first ?: 1.0
            if (state.scaleMultiplier == 1.0 && !state.highlighted) viewerVisuals.remove(player)
            else viewerVisuals[player] = state.scaleMultiplier to state.highlighted
            refreshViewers()
            displays.forEach {
                if (player in it.viewers)
                    if (previous != state.scaleMultiplier)
                        it.sendPoseState(player, viewerDuration(player))
                    else it.sendVisualFlags(player)
            }
        }
    }

    fun clearViewerState(player: Player) {
        update {
            if (!closed) {
                val changed = viewerVisuals.remove(player)?.first?.let { it != 1.0 } ?: false
                displays.forEach {
                    if (player in it.viewers) {
                        if (changed) it.sendPoseState(player, viewerDuration(player))
                        else it.sendVisualFlags(player)
                    }
                }
            }
        }
    }

    private fun viewerDuration(player: Player): Int {
        val previous = viewerIntervals[player.uuid].takeIf { viewerPlayers[player.uuid] === player }
        val rate =
            previous
                ?: policy.viewerRate(
                    policy.distanceSquared(
                        player,
                        displays.first().instance,
                        transform.root.position,
                    ),
                    null,
                )
        return (rate.interval(config) / 50).toInt()
    }

    private fun sendViewerState(display: NpcItemDisplay, player: Player) {
        if (closed) return
        display.sendPoseState(player, viewerDuration(player))
    }


    private inline fun <T> update(action: () -> T): T {
        try { return synchronized(lock) { action() } } finally { afterUpdate() }
    }
}
