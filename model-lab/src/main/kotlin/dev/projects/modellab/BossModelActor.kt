package dev.projects.modellab

import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.worldseed.multipart.GenericModelImpl
import net.worldseed.multipart.animations.AnimationHandlerImpl

/** Scorpius's visual/AI separation, ported to Kotlin. Call on the owning instance's thread. */
class BossModelActor(
    val definition: ModelDefinition,
    private val instance: Instance,
    position: Pos,
    scale: Float = definition.previewScale,
    private val viewerRange: Double = 64.0,
) : AutoCloseable {
    private val model = object : GenericModelImpl() { override fun getId() = definition.id }
    private val animation: AnimationHandlerImpl
    private val viewers = mutableSetOf<Player>()
    private var position = position
    private var closed = false
    init {
        require(scale.isFinite() && scale > 0 && scale <= 16)
        require(viewerRange.isFinite() && viewerRange > 0)
        model.init(instance, position, scale)
        animation = AnimationHandlerImpl(model)
        if ("idle" in definition.animations) repeat("idle")
        syncViewers()
    }
    fun repeat(name: String) {
        check(!closed)
        animation.playRepeat(definition.requireAnimation(name))
    }
    fun play(name: String, exclusive: Boolean = true) {
        check(!closed)
        animation.playOnce(definition.requireAnimation(name), exclusive, Runnable {})
    }
    fun setBoneVisible(name: String, visible: Boolean) {
        check(!closed)
        require(model.getPart(name) != null) { "Unknown bone: $name" }
        model.setBoneVisible(name, visible)
    }
    fun move(to: Pos) {
        check(!closed)
        position = to
        model.setPosition(to)
        model.setGlobalRotation(to.yaw().toDouble())
    }
    fun syncViewers() {
        if (closed) return
        val current = instance.players.filter { it.position.distanceSquared(position) <= viewerRange * viewerRange }.toSet()
        (current - viewers).forEach(model::addViewer)
        (viewers - current).forEach(model::removeViewer)
        viewers.clear()
        viewers.addAll(current)
    }
    override fun close() {
        if (closed) return
        closed = true
        animation.destroy()
        viewers.forEach(model::removeViewer)
        viewers.clear()
        model.destroy()
    }
}
