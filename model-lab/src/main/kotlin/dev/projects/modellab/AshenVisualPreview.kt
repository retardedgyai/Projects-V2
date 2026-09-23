package dev.projects.modellab

import com.yuuki14202028.WseeAssets
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import java.util.UUID
import kotlin.math.ceil

/** Pairs the authored boss animation with its detached tells and impact art in the isolated lab. */
class AshenVisualPreview(private val bundle: ModelBundle, private val instance: Instance) : AutoCloseable {
    private class Scene(val owner: Player, val boss: BossModelActor, val animation: String,
        val repeating: Boolean, val duration: Int) {
        var age = 0
        var warning: BossModelActor? = null
        val effects = mutableListOf<Pair<Int, BossModelActor>>()
        fun close() {
            warning?.close(); warning = null
            effects.forEach { it.second.close() }; effects.clear()
        }
    }

    private val scenes = mutableMapOf<UUID, Scene>()

    fun play(player: Player, boss: BossModelActor, animation: String, repeating: Boolean) {
        clear(player.uuid)
        if (boss.definition.id != WseeAssets.AshenKnight.Model) return
        val duration = ceil(boss.definition.animations.getValue(animation) * 20).toInt().coerceAtLeast(1)
        val scene = Scene(player, boss, animation, repeating, duration)
        scenes[player.uuid] = scene
        start(scene)
    }

    private fun start(scene: Scene) {
        scene.age = 0
        if (scene.animation in setOf("cleave", "cleave_reverse", "slam", "leap", "thrust")) {
            scene.warning = BossModelActor(bundle.definition(WseeAssets.AshenWarning.Model), instance,
                scene.boss.currentPosition).also { it.move(scene.boss.currentPosition) }
        }
    }

    fun tick() {
        scenes.toMap().forEach { (id, scene) ->
            if (scene.owner.isRemoved || scene.owner.instance !== instance) { clear(id); return@forEach }
            scene.age++
            val hitAt = when (scene.animation) {
                "cleave" -> 14
                "cleave_reverse" -> 10
                "thrust" -> 11
                "slam" -> 19
                "leap" -> 22
                else -> -1
            }
            if (scene.age == hitAt) {
                scene.warning?.close(); scene.warning = null
                val model = if (scene.animation == "slam" || scene.animation == "leap")
                    WseeAssets.AshenFissure.Model else WseeAssets.AshenSlash.Model
                val effect = BossModelActor(bundle.definition(model), instance, scene.boss.currentPosition)
                effect.move(scene.boss.currentPosition)
                effect.play(if (model == WseeAssets.AshenFissure.Model) "burst" else "slash")
                scene.effects += (scene.age + if (model == WseeAssets.AshenFissure.Model) 28 else 9) to effect
            }
            scene.effects.removeIf { (end, effect) ->
                if (scene.age < end) false else { effect.close(); true }
            }
            scene.warning?.syncViewers()
            scene.effects.forEach { it.second.syncViewers() }
            if (scene.age >= scene.duration && scene.repeating) {
                scene.close(); start(scene)
            } else if (scene.age >= scene.duration && scene.warning == null && scene.effects.isEmpty()) {
                scenes.remove(id)
            }
        }
    }

    fun clear(id: UUID) { scenes.remove(id)?.close() }
    override fun close() { scenes.keys.toList().forEach(::clear) }
}
