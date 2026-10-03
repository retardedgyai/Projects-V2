package dev.projects.modellab

import com.google.gson.JsonObject
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.worldseed.multipart.GenericModel
import net.worldseed.multipart.ModelLoader.AnimationType
import net.worldseed.multipart.animations.AnimationHandler.AnimationDirection
import net.worldseed.multipart.animations.BoneAnimation
import net.worldseed.multipart.animations.FrameProvider
import net.worldseed.multipart.animations.ModelAnimation
import kotlin.math.*

/** Dragon-only unchanged numeric BB keys; the action owner supplies time, never WSEE's clock. */
class DragonNumericClip(model: GenericModel, val sourceName: String, clip: JsonObject,
                        private val clipPriority: Int) : ModelAnimation {
    private data class Key(val seconds: Double, val value: Vec)
    val lengthSeconds = clip["animation_length"].asDouble
    var sourceSeconds = 0.0
        private set
    private val channels = mutableListOf<Channel>()
    private val bones = linkedSetOf<String>()
    private var activeWeight = 1.0
    private var playDirection = AnimationDirection.FORWARD
    init {
        require(lengthSeconds.isFinite() && lengthSeconds > 0)
        for ((bone, raw) in clip.getAsJsonObject("bones").entrySet()) {
            val part = requireNotNull(model.getPart(bone)) { "Missing dragon bone $bone" }
            for ((property, value) in raw.asJsonObject.entrySet()) {
                val data = value.asJsonObject
                if (data.size() == 0) continue
                val type = when (property) {
                    "position" -> AnimationType.TRANSLATION
                    "rotation" -> AnimationType.ROTATION
                    else -> error("Unsupported dragon channel $property")
                }
                val keys = data.entrySet().map { (time, entry) ->
                    val k = entry.asJsonObject
                    require(k["lerp_mode"].asString == "linear" && k.getAsJsonArray("post").size() == 1)
                    val p = k.getAsJsonArray("post")[0].asJsonObject
                    Key(time.toDouble(), Vec(constant(p["x"].asString), constant(p["y"].asString), constant(p["z"].asString)))
                }.sortedBy { it.seconds }
                require(keys.size >= 2 && abs(keys.first().seconds) < 1e-10 && abs(keys.last().seconds - lengthSeconds) < 1e-9)
                require(keys.zipWithNext().all { (a, b) -> b.seconds > a.seconds })
                if (type == AnimationType.ROTATION) require(keys.zipWithNext().all { (a, b) ->
                    maxOf(abs(b.value.x()-a.value.x()), abs(b.value.y()-a.value.y()), abs(b.value.z()-a.value.z())) < 180 })
                val channel = Channel(bone, type, keys)
                part.addAnimation(channel);channels.add(channel);bones.add(bone)
            }
        }
        require(bones.size == 34)
    }
    private fun constant(raw: String): Double {
        var token = raw.trim();var sign = 1.0
        while (true) {
            token.toDoubleOrNull()?.let { require(it.isFinite());return sign * it }
            token = when {
                token.startsWith("-(") && token.endsWith(")") -> { sign = -sign;token.substring(2, token.length-1).trim() }
                token.startsWith("+(") && token.endsWith(")") -> token.substring(2, token.length-1).trim()
                token.startsWith("(") && token.endsWith(")") -> token.substring(1, token.length-1).trim()
                else -> error("Non-numeric dragon key $raw")
            }
        }
    }
    fun seek(seconds: Double) { require(seconds.isFinite() && seconds >= -1e-9 && seconds <= lengthSeconds+1e-9);sourceSeconds=seconds.coerceIn(0.0,lengthSeconds) }
    override fun name() = sourceName + "__isolated_owner_clock"
    override fun priority() = clipPriority
    override fun animationTime() = ceil(lengthSeconds*20).toInt()
    // Handler repeats only to keep channels active. Explicit owner clamps/changes the once clips.
    override fun loops() = true
    override fun currentTick() = floor(sourceSeconds*20+1e-10).toInt()
    override fun direction() = playDirection
    override fun setDirection(direction: AnimationDirection) { require(direction != AnimationDirection.BACKWARD);playDirection=direction }
    override fun getAnimatedBones(): Set<String> = bones
    override fun weight() = activeWeight
    override fun setWeight(weight: Double) { require(weight.isFinite() && weight in 0.0..1.0);activeWeight=weight }
    override fun fadedOut() = activeWeight <= 1e-6
    override fun play(resume: Boolean) { activeWeight=1.0;channels.forEach { it.play() } }
    override fun stop() { channels.forEach { it.stop() } }
    override fun stop(bones: Set<String>) { channels.filter { it.boneName() in bones }.forEach { it.stop() } }
    override fun tick() { }
    private inner class Channel(private val bone: String, private val type: AnimationType,
                                private val keys: List<Key>) : BoneAnimation {
        private var playing = false
        override fun name() = this@DragonNumericClip.name()
        override fun boneName() = bone
        override fun getType() = type
        override fun isPlaying() = playing
        override fun weight() = activeWeight
        override fun setWeight(weight: Double) { this@DragonNumericClip.setWeight(weight) }
        override fun setDirection(direction: AnimationDirection) { this@DragonNumericClip.setDirection(direction) }
        override fun play() { playing=true }
        override fun stop() { playing=false }
        override fun tick() { }
        override fun resume(tick: Short) { playing=true }
        override fun getTick() = currentTick().toShort()
        override fun getTransform(): Point = if (playing) sample(sourceSeconds) else Vec.ZERO
        override fun getTransformAtTime(tick: Int): Point = sample((tick/20.0).coerceIn(0.0,lengthSeconds))
        private fun sample(seconds: Double): Point {
            var lo=0;var hi=keys.lastIndex
            while (hi-lo>1) { val mid=(lo+hi)/2;if (keys[mid].seconds<=seconds) lo=mid else hi=mid }
            val a=keys[lo];val b=keys[hi];val q=((seconds-a.seconds)/(b.seconds-a.seconds)).coerceIn(0.0,1.0)
            val v=a.value.mul(1-q).add(b.value.mul(q))
            return if (type==AnimationType.ROTATION) v.mul(FrameProvider.RotationMul)
                   else v.mul(FrameProvider.TranslationMul).mul(0.25)
        }
    }
}
