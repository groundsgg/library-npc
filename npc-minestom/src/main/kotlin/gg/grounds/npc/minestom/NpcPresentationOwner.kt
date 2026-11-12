package gg.grounds.npc.minestom

import java.util.IdentityHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import net.minestom.server.entity.Player
import net.minestom.server.event.EventListener
import net.minestom.server.event.instance.RemoveEntityFromInstanceEvent

internal interface NpcPresentationCopyControl {
    fun inheritViewerAdmissionNow(player: Player)

    fun viewerAdmissionNow(player: Player): Boolean?

    fun renderPresentationNow(frame: NpcSampleFrame)

    fun applyPersonalVisualNow(player: Player, state: NpcViewerState)

    fun submitPresentation(action: (AnimatedNpcHandle) -> Unit): CompletionStage<Void>
}

internal data class NpcPresentationCopy(
    val start: NpcStart,
    val handle: NpcHandle,
    val control: NpcPresentationCopyControl,
)

/** Reservations, cleanup and suppression are owned by one concrete common actor. */
internal class NpcPresentationOwner(
    val request: NpcSpawnRequest,
    val engine: AnimatedNpcHandle,
    private val limits: NpcPresentationLimits,
    private val current: () -> Boolean,
    private val guard: (Runnable) -> Boolean,
    private val copies:
        (NpcPresentationOptions, NpcPublicationGuard, () -> Boolean) -> NpcPresentationCopy,
) {
    private val lock = Any()
    private val reservations = IdentityHashMap<Player, NpcPersonalPresentation>()
    private val owned = HashSet<NpcPersonalPresentation>()
    private val failures = ArrayList<Throwable>()
    private var additionalParts = 0
    @Volatile private var closing = false

    // One listener per parent with personal ownership, never one per display part.
    private val departures =
        EventListener.of(RemoveEntityFromInstanceEvent::class.java) { event ->
            val personal = synchronized(lock) { reservations[event.entity] }
            personal?.connectionDeparted()
        }

    init {
        request.instance.eventNode().addListener(departures)
    }

    fun isCurrent() = !closing && current()

    fun valid(player: Player) =
        isCurrent() && player.isOnline && !player.isRemoved && player.instance === request.instance

    fun runIfCurrent(operation: Runnable): Boolean =
        if (!isCurrent()) false else guard(Runnable { if (isCurrent()) operation.run() })

    fun prepare(options: NpcPresentationOptions): NpcPresentationStart {
        if (!valid(options.player))
            return rejectedPresentation(
                "NPC_PRESENTATION_PARENT_GONE",
                "Parent or viewer is unavailable",
            )
        if (!engine.canPresentTo(options.player))
            return rejectedPresentation(
                "NPC_PRESENTATION_OUT_OF_RANGE",
                "Viewer is outside the parent admission range",
            )
        if (options.idleClip != null && options.idleClip !in request.model.animations)
            return rejectedPresentation(
                "NPC_PRESENTATION_UNKNOWN_CLIP",
                "Unknown private idle clip",
            )
        val personal =
            synchronized(lock) {
                when {
                    closing || !current() -> null
                    reservations.containsKey(options.player) ->
                        return rejectedPresentation(
                            "NPC_PRESENTATION_CONFLICT",
                            "This connection already owns a presentation",
                        )
                    reservations.size >= limits.maxPresentations ||
                        request.model.parts.size > limits.maxAdditionalParts - additionalParts ->
                        return rejectedPresentation(
                            "NPC_PRESENTATION_CAPACITY",
                            "Personal presentation budget exhausted",
                        )
                    else ->
                        NpcPersonalPresentation(this, options).also {
                            reservations[options.player] = it
                            owned += it
                            additionalParts += request.model.parts.size
                        }
                }
            } ?: return rejectedPresentation("NPC_PRESENTATION_PARENT_GONE", "Parent is closing")
        personal.closed.whenComplete { _, failure ->
            synchronized(lock) {
                owned.remove(personal)
                if (failure != null) failures += failure
            }
        }
        personal.begin()
        return personal
    }

    fun makeCopy(
        options: NpcPresentationOptions,
        publication: NpcPublicationGuard,
        valid: () -> Boolean,
    ) = copies(options, publication, valid)

    /** Called after private entity removal and before completion of its owned close stage. */
    fun release(personal: NpcPersonalPresentation, failure: Throwable?): Throwable? {
        var error = failure
        fun record(problem: Throwable) {
            if (error == null) error = problem
            else if (error !== problem) error!!.addSuppressed(problem)
        }
        try {
            if (
                !personal.hasDeparted() && engine.ownsSuppression(personal.options.player, personal)
            ) {
                runIfCurrent(
                    Runnable {
                        engine.releaseSuppression(
                            personal.options.player,
                            personal,
                            true,
                            personal.restorationAdmission(),
                        )
                    }
                )
            }
        } catch (problem: Throwable) {
            record(problem)
        } finally {
            try {
                engine.releaseSuppression(personal.options.player, personal, false)
            } catch (problem: Throwable) {
                record(problem)
            }
            synchronized(lock) {
                if (reservations[personal.options.player] === personal) {
                    reservations.remove(personal.options.player)
                    additionalParts -= request.model.parts.size
                }
            }
        }
        return error
    }

    fun followTransform(): CompletionStage<Void> {
        val targets = synchronized(lock) { owned.toList() }
        return CompletableFuture.allOf(
            *targets.map { it.followTransform().toCompletableFuture() }.toTypedArray()
        )
    }

    fun followViewerState(player: Player): CompletionStage<Void> {
        val target = synchronized(lock) { reservations[player] }
        return target?.followViewerState() ?: CompletableFuture.completedFuture(null)
    }

    fun closeAll(): CompletionStage<Void> {
        val pending =
            synchronized(lock) {
                closing = true
                owned.toList()
            }
        request.instance.eventNode().removeListener(departures)
        pending.forEach { it.cancel() }
        val result = CompletableFuture<Void>()
        CompletableFuture.allOf(*pending.map { it.closed }.toTypedArray()).whenComplete { _, failure
            ->
            val problems = synchronized(lock) { failures.toList() }
            val problem = problems.firstOrNull() ?: failure
            if (problem == null) result.complete(null) else result.completeExceptionally(problem)
        }
        return result.minimalCompletionStage()
    }
}
