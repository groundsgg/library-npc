// Extracted from Grounds minestom-lobby, commit 020a370ff414f85bb6ac495785605a8789ffbb19.
package gg.grounds.npc.minestom

import gg.grounds.npc.core.*
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.math.abs
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.instance.Instance
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule

internal class AnimatedNpcHandle(
    private val lock: Any,
    private val model: NpcModel,
    private val displays: List<NpcItemDisplay>,
    private var transform: NpcRenderTransform,
    private val effects: NpcSoundSink,
    private val config: NpcRenderConfig,
    private val audience: NpcRenderAudience = NpcRenderAudience.Shared,
) : NpcEngineHandle {
    private val movements = HashSet<java.util.concurrent.CompletableFuture<Void>>()
    private val movementTasks = HashSet<Task>()
    private val externalMovements = HashSet<java.util.concurrent.CompletableFuture<Void>>()
    private val movementFailures = ArrayList<Throwable>()
    private val closedCleanup = java.util.concurrent.CompletableFuture<Void>()

    private data class SoundEmission(
        val instance: Instance,
        val origin: NpcVector3,
        val sound: NpcClipSound,
    )

    private val pendingSounds = ArrayList<SoundEmission>()
    private val displayPoses = NpcDisplayPoses(model)
    private var closed = false
    private var published = false
    private var inheritedAdmission: Player? = null
    private val policy = NpcRenderPolicy(config)
    private var revision = 0L
    private var requested: String? = null
    private var elapsed = 0L
    private var lastRendered = Long.MIN_VALUE
    private var variationStarted: Long? = null
    private var nextVariation = config.variationDelayMillis
    private val viewerVisuals = java.util.IdentityHashMap<Player, Pair<Double, Boolean>>()
    private var viewerIntervals = emptyMap<UUID, NpcViewerRate>()
    private var viewerPlayers = emptyMap<UUID, Player>()
    private val nextViewerUpdate = mutableMapOf<NpcViewerRate, Long>()
    private var viewerGroups = emptyMap<NpcViewerRate, List<Player>>()
    private var renderInterval = 500L
    private var headLook = NpcVector3(0.0, 0.0, 0.0)
    private var pendingHeadLook = headLook
    private var sampledClip: String? = null
    private var sampledSeconds = 0.0
    private var externalFrame: NpcSampleFrame? = null

    private data class Suppression(val lease: Any, val previousRate: NpcViewerRate?)

    private var suppressed: java.util.IdentityHashMap<Player, Suppression>? = null
    private var previousPose: List<NpcMatrix>? = null
    private var previousTransform: NpcRenderTransform? = null

    override val headLookOrigin: NpcVector3?
        get() =
            model.headBone?.let { bone ->
                // Imported bones face -Z; the model-facing correction makes Scene forward +Z.
                NpcVector3(-bone.origin.x, bone.origin.y + 0.15, -bone.origin.z)
            }

    override fun applyHeadLook(yawDegrees: Double, pitchDegrees: Double) {
        update {
            if (closed) return
            // Conjugating by the model's Y180 facing correction reverses the pitch axis.
            val next = NpcVector3(-pitchDegrees, yawDegrees, 0.0)
            if (pendingHeadLook == next) return
            pendingHeadLook = next
            if (requested == null) advance(elapsed, true)
        }
    }

    init {
        displays.forEach { display ->
            display.viewerState = { player -> sendViewerState(display, player) }
            display.visualState = { player -> viewerVisuals[player] ?: DEFAULT_NPC_VISUAL }
        }
        render(null, 0.0)
        refreshViewers()
    }

    private val visibilityTask =
        displays
            .first()
            .instance
            .scheduler()
            .buildTask { update { if (!closed) refreshViewers() } }
            .repeat(TaskSchedule.tick(4))
            .schedule()

    fun publishVisible() {
        update {
            if (!closed) {
                published = true
                try {
                    refreshViewers()
                } finally {
                    inheritedAdmission = null
                }
            }
        }
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

    fun currentTransform(): NpcRenderTransform = synchronized(lock) { transform }

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

    fun sampleFrame(): NpcSampleFrame =
        synchronized(lock) {
            NpcSampleFrame(sampledClip, sampledSeconds, headLook)
        }

    fun refreshAudience() = update { if (!closed) refreshViewers() }

    fun renderFrame(frame: NpcSampleFrame) = update {
        if (closed) return
        require(frame.seconds.isFinite() && frame.seconds >= 0)
        require(frame.clipId == null || frame.clipId in model.animations)
        externalFrame = frame
        headLook = frame.headLook
        pendingHeadLook = frame.headLook
        sampledClip = frame.clipId
        sampledSeconds = frame.seconds
        refreshViewers()
        if (viewerIntervals.isNotEmpty()) {
            render(frame.clipId, frame.seconds)
            flushViewerUpdates(elapsed, true)
        }
    }

    fun snapshot(): NpcAnchorSnapshot =
        synchronized(lock) { NpcAnchorSnapshot(transform.root, headLookOrigin) }

    private fun refreshViewers(force: Boolean = false) {
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
            advance(elapsed, true, false, false)
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

    fun drainMovements(): java.util.concurrent.CompletionStage<Void> =
        if (synchronized(lock) { closed }) closedCleanup.minimalCompletionStage()
        else movementDrain()

    private fun movementDrain(): java.util.concurrent.CompletionStage<Void> {
        val (pending, failure) =
            synchronized(lock) { movements.toList() to movementFailures.firstOrNull() }
        return java.util.concurrent.CompletableFuture.allOf(*pending.toTypedArray()).thenCompose {
            if (failure == null) java.util.concurrent.CompletableFuture.completedFuture<Void>(null)
            else java.util.concurrent.CompletableFuture.failedFuture<Void>(failure)
        }
    }

    override fun applyTransform(
        transform: NpcRenderTransform
    ): java.util.concurrent.CompletionStage<Void> {
        val result = java.util.concurrent.CompletableFuture<Void>()
        val token = update {
            if (closed)
                return java.util.concurrent.CompletableFuture.failedFuture(
                    IllegalStateException("NPC closed")
                )
            val revision = ++this.revision
            if (transform.root.position == this.transform.root.position) {
                this.transform = transform
                if (requested == null) advance(elapsed, true)
                return java.util.concurrent.CompletableFuture.completedFuture(null)
            }
            movements += result
            externalMovements += result
            revision
        }
        result.whenComplete { _, failure ->
            synchronized(lock) {
                movements.remove(result)
                externalMovements.remove(result)
                if (failure != null) movementFailures += failure
            }
        }
        val anchor = transform.root.position
        if (synchronized(lock) { closed || token != revision }) {
            result.complete(null)
            return result.minimalCompletionStage()
        }
        try {
            displays
                .first()
                .instance
                .loadOptionalChunk(Pos(anchor.x, anchor.y, anchor.z))
                .whenComplete { chunk, error ->
                    if (error != null) result.completeExceptionally(error)
                    else if (chunk == null)
                        result.completeExceptionally(
                            IllegalStateException("NPC destination chunk unavailable")
                        )
                    else
                        queueMovement(
                            result,
                            token,
                            {
                                val moved =
                                    ArrayList<java.util.concurrent.CompletableFuture<Void>>()
                                for (display in displays) {
                                    if (synchronized(lock) { closed || token != revision }) break
                                    try {
                                        moved += display.teleport(Pos(anchor.x, anchor.y, anchor.z))
                                    } catch (problem: Throwable) {
                                        moved += CompletableFuture.failedFuture<Void>(problem)
                                        break
                                    }
                                }
                                java.util.concurrent.CompletableFuture.allOf(*moved.toTypedArray())
                            },
                            foreign = true,
                        ) {
                            queueMovement(
                                result,
                                token,
                                {
                                    this.transform = transform
                                    advance(elapsed, true)
                                    java.util.concurrent.CompletableFuture.completedFuture(null)
                                },
                            ) {
                                result.complete(null)
                            }
                        }
                }
        } catch (error: Throwable) {
            result.completeExceptionally(error)
        }
        return result.minimalCompletionStage()
    }

    /**
     * Every continuation that can touch entities or their metadata returns to the instance owner.
     */
    private fun queueMovement(
        result: java.util.concurrent.CompletableFuture<Void>,
        token: Long,
        action: () -> java.util.concurrent.CompletionStage<Void>,
        foreign: Boolean = false,
        after: () -> Unit,
    ) {
        var skipped = false
        var failure: Throwable? = null
        synchronized(lock) {
            // Atomically hand completed I/O ownership to a cancellable owner continuation.
            externalMovements.remove(result)
            if (closed || token != revision || result.isDone) skipped = true
            else
                try {
                    val holder = arrayOfNulls<Task>(1)
                    val task =
                        displays
                            .first()
                            .instance
                            .scheduler()
                            .buildTask {
                                val accepted =
                                    synchronized(lock) {
                                        movementTasks.remove(holder[0])
                                        if (closed || token != revision || result.isDone) false
                                        else {
                                            externalMovements += result
                                            true
                                        }
                                    }
                                if (!accepted) result.complete(null)
                                else
                                    try {
                                        // Teleports invoke host event listeners: reserve under
                                        // lock, execute outside.
                                        val stage =
                                            if (foreign) action()
                                            else
                                                update {
                                                    if (
                                                        closed || token != revision || result.isDone
                                                    )
                                                        null
                                                    else action()
                                                }
                                        if (stage == null) result.complete(null)
                                        else
                                            stage.whenComplete { _, error ->
                                                if (error != null)
                                                    result.completeExceptionally(error)
                                                else after()
                                            }
                                    } catch (error: Throwable) {
                                        result.completeExceptionally(error)
                                    }
                            }
                            .delay(TaskSchedule.tick(1))
                            .schedule()
                    holder[0] = task
                    movementTasks += task
                } catch (error: Throwable) {
                    failure = error
                }
        }
        if (skipped) result.complete(null)
        if (failure != null) result.completeExceptionally(failure!!)
    }

    override fun startAnimation(animation: String, elapsedMillis: Long) {
        update {
            if (closed) return
            val clip = requireNotNull(model.animations[animation]) { "Unknown NPC clip $animation" }
            requested = animation
            elapsed = elapsedMillis
            variationStarted = null
            nextVariation = elapsedMillis + config.variationDelayMillis
            if (elapsedMillis == 0L) playSound(animation)
            advance(elapsedMillis, true)
        }
    }

    override fun stopAnimation(animation: String?) {
        update {
            if (closed || (animation != null && requested != animation)) return
            requested = null
            variationStarted = null
            commitHeadLook()
            render(null, 0.0)
            flushViewerUpdates(elapsed, true)
        }
    }

    override fun advanceAnimation(elapsedMillis: Long) {
        update { if (!closed) advance(elapsedMillis, false) }
    }

    private fun advance(
        time: Long,
        force: Boolean,
        updateViewers: Boolean = true,
        flush: Boolean = true,
    ) {
        val seeking = time < elapsed
        if (seeking && variationStarted?.let { time < it } == true) {
            variationStarted = null
            nextVariation = time + config.variationDelayMillis
        }
        elapsed = time
        if (updateViewers) refreshViewers()
        val personal = externalFrame
        if (personal != null) {
            headLook = personal.headLook
            sampledClip = personal.clipId
            sampledSeconds = personal.seconds
            if (viewerIntervals.isNotEmpty()) {
                render(personal.clipId, personal.seconds, !flush)
                if (flush) flushViewerUpdates(time, force || seeking)
            }
            return
        }
        if (viewerIntervals.isEmpty()) {
            commitHeadLook()
            sampledClip = requested
            sampledSeconds = time / 1000.0
            val clip = requested?.let(model.animations::get)
            if (
                clip != null &&
                    clip.loop == "once" &&
                    sampledSeconds >= clip.length &&
                    config.idleClip in model.animations
            ) {
                sampledClip = config.idleClip
                sampledSeconds -= clip.length
            }
            val variation = variationStarted
            if (
                sampledClip == config.idleClip &&
                    variation != null &&
                    config.variationClip in model.animations
            ) {
                val age = (time - variation) / 1000.0
                if (age in 0.0..<model.animations.getValue(config.variationClip!!).length) {
                    sampledClip = config.variationClip
                    sampledSeconds = age
                }
            }
            return
        }
        val interval = renderInterval
        if (
            !force &&
                !seeking &&
                lastRendered != Long.MIN_VALUE &&
                time >= lastRendered &&
                time - lastRendered < interval
        ) {
            if (flush) flushViewerUpdates(time, seeking)
            return
        }
        lastRendered = time
        commitHeadLook()
        val name = requested
        val clip = name?.let(model.animations::get)
        var displayed = name
        var seconds = time / 1000.0
        if (
            clip != null &&
                clip.loop == "once" &&
                seconds >= clip.length &&
                config.idleClip != null &&
                config.idleClip in model.animations
        ) {
            displayed = config.idleClip
            seconds -= clip.length
        }
        if (
            config.idleClip != null &&
                displayed == config.idleClip &&
                config.variationClip != null &&
                config.variationClip in model.animations
        ) {
            val variation = variationStarted
            if (variation != null) {
                val age = (time - variation) / 1000.0
                if (age < model.animations.getValue(config.variationClip!!).length) {
                    displayed = config.variationClip
                    seconds = age
                } else {
                    variationStarted = null
                    nextVariation = time + config.variationDelayMillis
                }
            } else if (time >= nextVariation) {
                nextVariation = time + config.variationDelayMillis
                val anchor = transform.root.position
                val near =
                    displays.first().instance.players.any { p ->
                        val pos = p.position
                        (pos.x() - anchor.x) * (pos.x() - anchor.x) +
                            (pos.y() - anchor.y) * (pos.y() - anchor.y) +
                            (pos.z() - anchor.z) * (pos.z() - anchor.z) <=
                            config.variationMinDistance * config.variationMinDistance
                    }
                if (!near) {
                    variationStarted = time
                    displayed = config.variationClip
                    seconds = 0.0
                    playSound(config.variationClip!!)
                }
            }
        }
        val beforeSingleDeadline =
            nextViewerUpdate.size == 1 && time < nextViewerUpdate.values.first()
        render(displayed, seconds, !flush || beforeSingleDeadline)
        if (flush) flushViewerUpdates(time, force || seeking)
    }

    private fun flushViewerUpdates(time: Long, force: Boolean) {
        if (nextViewerUpdate.values.none { force || time >= it }) return
        for ((rate, deadline) in nextViewerUpdate) {
            if (!force && time < deadline) continue
            val recipients = viewerGroups[rate].orEmpty()
            displays.forEach { display -> display.flushUpdates(rate, recipients) }
            nextViewerUpdate[rate] = time + rate.interval(config)
        }
    }

    private fun playSound(name: String) {
        if (audience !== NpcRenderAudience.Shared) return
        model.sounds[name]?.let {
            pendingSounds += SoundEmission(displays.first().instance, transform.root.position, it)
        }
    }

    private inline fun <T> update(action: () -> T): T {
        try {
            return synchronized(lock) { action() }
        } finally {
            dispatchSounds()
        }
    }

    private fun dispatchSounds() {
        // Nested updates leave delivery to the outermost mutation after its monitor is released.
        if (Thread.holdsLock(lock)) return
        val emissions =
            synchronized(lock) {
                if (closed) {
                    pendingSounds.clear()
                    return
                }
                if (pendingSounds.isEmpty()) return
                pendingSounds.toList().also { pendingSounds.clear() }
            }
        emissions.forEach { effects.playNear(it.instance, it.origin, it.sound) }
    }

    private fun commitHeadLook() {
        if (
            abs(pendingHeadLook.x - headLook.x) >= 2.0 || abs(pendingHeadLook.y - headLook.y) >= 2.0
        )
            headLook = pendingHeadLook
    }

    private fun render(name: String?, seconds: Double, deferUpdates: Boolean = false) {
        val pose = displayPoses.sample(name, seconds, headLook)
        sampledClip = name
        sampledSeconds = seconds
        val unchangedRoot = previousTransform == transform
        val previous = previousPose
        val duration = (renderInterval / 50).toInt()
        displays.forEachIndexed { index, display ->
            if (unchangedRoot && previous != null && previous[index].contentEquals(pose[index]))
                return@forEachIndexed
            // Keep entry preparation queued until existing viewers' deadlines.
            display.deferUpdates = deferUpdates
            try {
                display.editEntityMeta(ItemDisplayMeta::class.java) { meta ->
                    if (
                        DisplayTransform.fromModel(transform, NPC_FACING * pose[index])
                            .apply(meta, transform.root.position)
                    ) {
                        meta.setTransformationInterpolationDuration(duration)
                        meta.setTransformationInterpolationStartDelta(0)
                    }
                }
            } finally {
                display.deferUpdates = false
            }
        }
        previousPose = pose
        previousTransform = transform
    }

    override fun applyViewerState(player: Player, state: NpcViewerState) {
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

    override fun clearViewerState(player: Player) {
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

    override fun close() {
        val pending =
            synchronized(lock) {
                if (closed) return
                closed = true
                revision++
                visibilityTask.cancel()
                movementTasks.forEach(Task::cancel)
                movementTasks.clear()
                pendingSounds.clear()
                viewerVisuals.clear()
                suppressed = null
                inheritedAdmission = null
                viewerIntervals = emptyMap()
                viewerPlayers = emptyMap()
                viewerGroups = emptyMap()
                nextViewerUpdate.clear()
                movements.filter { it !in externalMovements }
            }
        // Queued owner work needs no further tick; accepted I/O/events must actually settle.
        pending.forEach { it.complete(null) }
        movementDrain().whenComplete { _, movementFailure ->
            var failure = movementFailure
            displays.forEach { display ->
                try {
                    synchronized(lock) {
                        display.viewerState = null
                        display.visualState = null
                        display.clearUpdates()
                    }
                    display.remove()
                } catch (problem: Throwable) {
                    if (failure == null) failure = problem
                    else if (failure !== problem) failure!!.addSuppressed(problem)
                }
            }
            if (failure == null) closedCleanup.complete(null)
            else closedCleanup.completeExceptionally(failure!!)
        }
    }
}

private val NPC_FACING = NpcMatrix.rotation(NpcVector3(0.0, 180.0, 0.0))
