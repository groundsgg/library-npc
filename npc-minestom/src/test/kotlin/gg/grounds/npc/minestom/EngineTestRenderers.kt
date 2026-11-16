package gg.grounds.npc.minestom

import gg.grounds.npc.minestom.config.NpcRenderConfig
import gg.grounds.npc.minestom.internal.lifecycle.NpcEngineHandle
import gg.grounds.npc.minestom.transform.NpcRenderTransform
import gg.grounds.npc.minestom.viewer.NpcViewerState

import gg.grounds.npc.core.io.NpcDecodeResult
import gg.grounds.npc.core.io.NpcModelJson
import gg.grounds.npc.core.math.NpcTransform
import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcBounds
import gg.grounds.npc.core.model.NpcModel

import java.util.concurrent.*
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance

// Mechanical names for the original synthetic parity fixtures, confined to test sources.
internal typealias Vec3 = NpcVector3

internal typealias SceneRenderTransform = NpcRenderTransform

internal typealias SceneViewerVisualState = NpcViewerState

internal typealias HeadLookRenderedAssetHandle = NpcEngineHandle

internal fun LocalId(value: String) = value

internal fun AssetKey(value: String) = value

internal fun EulerRotation(yaw: Double, pitch: Double, roll: Double) = NpcVector3(pitch, yaw, roll)

internal fun Transform(position: NpcVector3, rotation: NpcVector3, scale: NpcVector3) =
    NpcTransform(position, rotation, scale)

internal enum class AssetKind {
    NPC_BODY,
    PROP,
}

internal data class SceneAssetRenderContext(
    val instance: Instance,
    val element: String,
    val part: String?,
    val asset: String,
    val transform: NpcRenderTransform,
)

internal fun parseTestModel(json: String) =
    (NpcModelJson.decode(json.toByteArray()) as NpcDecodeResult.Success).model

/** Runs the real public queued API with a synchronous test tick driver. No alternate engine. */
internal class EngineTestRenderers(private val models: Map<String, NpcModel>) {
    fun rendererFor(asset: String, kind: AssetKind): Factory? =
        if (kind == AssetKind.NPC_BODY && asset in models) Factory(models.getValue(asset)) else null

    inner class Factory(private val model: NpcModel) {
        fun create(context: SceneAssetRenderContext): CompletableFuture<NpcEngineHandle> {
            val start =
                NpcRenderer.prepare(
                    NpcSpawnRequest.builder(context.instance, model, context.transform).config(NpcRenderConfig.builder().idleClip("idle").variationClip("idle2").build()).build()
                )
            val result = TickFuture<NpcEngineHandle>(context.instance)
            start.ready().whenComplete { ready, error ->
                if (error != null) result.completeExceptionally(error)
                else if (ready is NpcPrepareResult.Ready) {
                    ready.handle.publish().whenComplete { _, failure ->
                        if (failure != null) result.completeExceptionally(failure)
                        else result.complete(SynchronousHandle(ready.handle, context.instance))
                    }
                } else result.completeExceptionally(IllegalStateException(ready.toString()))
            }
            return result
        }
    }
}

internal class TickFuture<T>(private val instance: Instance) : CompletableFuture<T>() {
    override fun join(): T {
        drive(5, TimeUnit.SECONDS)
        return super.join()
    }

    override fun get(): T {
        drive(5, TimeUnit.SECONDS)
        return super.get()
    }

    override fun get(timeout: Long, unit: TimeUnit): T {
        drive(timeout, unit)
        return super.get(0, TimeUnit.NANOSECONDS)
    }

    private fun drive(timeout: Long, unit: TimeUnit) {
        val deadline = System.nanoTime() + unit.toNanos(timeout)
        while (!isDone && System.nanoTime() < deadline) {
            instance.tick(0)
            Thread.yield()
        }
    }
}

private class SynchronousHandle(private val handle: NpcHandle, private val instance: Instance) :
    NpcEngineHandle {
    private var closed = false
    override val headLookOrigin
        get() = handle.snapshot().lookOrigin

    private fun wait(stage: CompletionStage<Void>) {
        // The legacy void contract ignores commands after close; the public future rejects them.
        if (closed) return
        val ticking = TickFuture<Void>(instance)
        stage.whenComplete { _, error ->
            if (error == null) ticking.complete(null) else ticking.completeExceptionally(error)
        }
        ticking.join()
    }

    override fun applyTransform(transform: NpcRenderTransform): CompletionStage<Void> {
        wait(handle.applyTransform(transform))
        return CompletableFuture.completedFuture(null)
    }

    override fun applyHeadLook(yawDegrees: Double, pitchDegrees: Double) =
        wait(handle.applyHeadLook(yawDegrees, pitchDegrees))

    override fun playClip(animation: String, elapsedMillis: Long) =
        wait(handle.playClip(animation, elapsedMillis))

    override fun stopClipIfPlaying(animation: String?) = wait(if (animation == null) handle.stopClip() else handle.stopClipIfPlaying(animation))

    override fun advanceClipTo(elapsedMillis: Long) =
        wait(handle.advanceClipTo(elapsedMillis))

    override fun applyViewerState(player: Player, state: NpcViewerState) =
        wait(handle.applyViewerState(player, state))

    override fun clearViewerState(player: Player) = wait(handle.clearViewerState(player))

    override fun close() {
        closed = true
        handle.closeAsync()
    }
}

internal val unitBounds = NpcBounds(NpcVector3(.5, .5, .5), NpcVector3.ONE)
