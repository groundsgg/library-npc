package gg.grounds.npc.minestom

import gg.grounds.npc.core.animation.NpcLoopMode
import gg.grounds.npc.core.math.NpcMatrix
import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcClipSound
import gg.grounds.npc.core.model.NpcModel
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.math.abs
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.instance.Instance
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule

/** Shared externally driven clip clock and idle/variation policy; called under the engine monitor. */
internal class NpcSharedPlayback(
    private val model: NpcModel,
    private val config: NpcRenderConfig,
    private val nearby: () -> Boolean,
    private val playSound: (String) -> Unit,
) {
    var requested: String? = null
    var elapsed = 0L
    private var lastRendered = Long.MIN_VALUE
    private var variationStarted: Long? = null
    private var nextVariation = config.variationDelayMillis
    var sampledClip: String? = null
        private set
    var sampledSeconds = 0.0
        private set

    fun start(animation: String, time: Long) {
        require(animation in model.animations) { "Unknown NPC clip $animation" }
        requested = animation; elapsed = time; variationStarted = null
        nextVariation = time + config.variationDelayMillis
    }
    fun stop(animation: String?): Boolean {
        if (animation != null && requested != animation) return false
        requested = null; variationStarted = null
        return true
    }
    fun seek(time: Long): Boolean {
        val seeking = time < elapsed
        if (seeking && variationStarted?.let { time < it } == true) {
            variationStarted = null; nextVariation = time + config.variationDelayMillis
        }
        elapsed = time
        return seeking
    }
    fun shouldRender(time: Long, force: Boolean, seeking: Boolean, interval: Long): Boolean {
        if (!force && !seeking && lastRendered != Long.MIN_VALUE && time >= lastRendered && time-lastRendered < interval) return false
        lastRendered = time
        return true
    }
    fun sampleHidden() {
        val time = elapsed
            sampledClip = requested
            sampledSeconds = time / 1000.0
            val clip = requested?.let(model.animations::get)
            if (
                clip != null &&
                    clip.loop == NpcLoopMode.ONCE &&
                    sampledSeconds >= clip.lengthSeconds &&
                    config.idleClip in model.animations
            ) {
                sampledClip = config.idleClip
                sampledSeconds -= clip.lengthSeconds
            }
            val variation = variationStarted
            if (
                sampledClip == config.idleClip &&
                    variation != null &&
                    config.variationClip in model.animations
            ) {
                val age = (time - variation) / 1000.0
                if (age in 0.0..<model.animations.getValue(config.variationClip!!).lengthSeconds) {
                    sampledClip = config.variationClip
                    sampledSeconds = age
                }
            }
    }
    fun sampleVisible() {
        val time = elapsed
        val name = requested
        val clip = name?.let(model.animations::get)
        var displayed = name
        var seconds = time / 1000.0
        if (
            clip != null &&
                clip.loop == NpcLoopMode.ONCE &&
                seconds >= clip.lengthSeconds &&
                config.idleClip != null &&
                config.idleClip in model.animations
        ) {
            displayed = config.idleClip
            seconds -= clip.lengthSeconds
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
                if (age < model.animations.getValue(config.variationClip!!).lengthSeconds) {
                    displayed = config.variationClip
                    seconds = age
                } else {
                    variationStarted = null
                    nextVariation = time + config.variationDelayMillis
                }
            } else if (time >= nextVariation) {
                nextVariation = time + config.variationDelayMillis
                val near = nearby()
                if (!near) {
                    variationStarted = time
                    displayed = config.variationClip
                    seconds = 0.0
                    playSound(config.variationClip!!)
                }
            }
        }
        sampledClip = displayed
        sampledSeconds = seconds
    }
}
