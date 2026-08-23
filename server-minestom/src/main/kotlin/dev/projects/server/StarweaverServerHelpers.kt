package dev.projects.server

import dev.projects.server.particle.ParticleAnimationScheduler
import dev.projects.server.particle.ParticleManager
import dev.projects.server.particle.startParticlePreset
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.attribute.AttributeModifier
import net.minestom.server.entity.attribute.AttributeOperation
import net.minestom.server.instance.Instance
import net.kyori.adventure.key.Key
import kotlin.math.ceil
import kotlin.math.floor

private val starweaverReloadSpeedModifierKey = Key.key("projects", "starweaver_reload_speed")

internal fun applyStarweaverMovementSpeed(player: Player, bonus: Double) {
    val attribute = player.getAttribute(Attribute.MOVEMENT_SPEED)
    attribute.removeModifier(starweaverReloadSpeedModifierKey)
    val amount = bonus.coerceIn(0.0, StarweaverBalance.RELOAD_MOVEMENT_SPEED_BONUS)
    if (amount > 0.0) {
        attribute.addModifier(
            AttributeModifier(
                starweaverReloadSpeedModifierKey,
                amount,
                AttributeOperation.ADD_MULTIPLIED_BASE,
            ),
        )
    }
}

internal fun restoreStarweaverMovementSpeed(player: Player) {
    applyStarweaverMovementSpeed(player, 0.0)
}

internal fun resolveStarweaverGroundTarget(
    instance: Instance,
    eye: Point,
    direction: Vec,
    maxRange: Double,
): Pos? {
    val length = direction.length()
    if (length <= 1.0e-9 || maxRange <= 0.0) return null
    val normalized = direction.mul(1.0 / length)
    val samples = ceil(maxRange * 10.0).toInt()
    for (sample in 1..samples) {
        val distance = maxRange * sample / samples
        val point = eye.add(
            normalized.x() * distance,
            normalized.y() * distance,
            normalized.z() * distance,
        )
        val blockX = floor(point.x()).toInt()
        val blockY = floor(point.y()).toInt()
        val blockZ = floor(point.z()).toInt()
        if (instance.getBlock(blockX, blockY, blockZ).blocksMotion()) {
            return Pos(blockX + 0.5, blockY + 1.0, blockZ + 0.5)
        }
    }

    // Horizontal/skyward views still get a natural Minecraft-style fallback:
    // search down from the end of the ray for the first solid surface.
    val end = eye.add(
        normalized.x() * maxRange,
        normalized.y() * maxRange,
        normalized.z() * maxRange,
    )
    val blockX = floor(end.x()).toInt()
    val blockZ = floor(end.z()).toInt()
    val topY = floor(end.y()).toInt()
    for (blockY in topY downTo (topY - 8).coerceAtLeast(0)) {
        if (instance.getBlock(blockX, blockY, blockZ).blocksMotion()) {
            return Pos(blockX + 0.5, blockY + 1.0, blockZ + 0.5)
        }
    }
    return null
}

internal fun isStarweaverBlockCollision(instance: Instance, start: Point, end: Point): Boolean {
    val distance = start.distance(end)
    val samples = ceil(distance / 0.25).toInt().coerceAtLeast(1)
    for (sample in 1..samples) {
        val progress = sample.toDouble() / samples
        val point = start.add(
            (end.x() - start.x()) * progress,
            (end.y() - start.y()) * progress,
            (end.z() - start.z()) * progress,
        )
        if (instance.getBlock(floor(point.x()).toInt(), floor(point.y()).toInt(), floor(point.z()).toInt()).blocksMotion()) {
            return true
        }
    }
    return false
}

internal fun starweaverProjectilePresetId(cast: StarweaverCast): String = when {
    cast.kind == StarweaverCastKind.CONJUNCTION && cast.celestial == StarweaverCelestial.SUN ->
        "projects:class/starweaver/q_solar"
    cast.slot != StarweaverSlot.Q -> starweaverZonePresetId(cast)
    cast.celestial == StarweaverCelestial.SUN -> "projects:class/starweaver/q_sun"
    cast.celestial == StarweaverCelestial.MOON -> "projects:class/starweaver/q_moon"
    else -> "projects:class/starweaver/q_star"
}

internal fun starweaverImpactPresetId(cast: StarweaverCast): String = when {
    cast.kind == StarweaverCastKind.CONJUNCTION && cast.celestial == StarweaverCelestial.SUN ->
        "projects:class/starweaver/q_solar"
    cast.celestial == StarweaverCelestial.SUN -> "projects:class/starweaver/q_sun"
    cast.celestial == StarweaverCelestial.MOON -> "projects:class/starweaver/q_moon"
    else -> "projects:class/starweaver/q_star"
}

internal fun starweaverZonePresetId(cast: StarweaverCast): String = when (cast.slot) {
    StarweaverSlot.W -> if (cast.kind == StarweaverCastKind.CONJUNCTION) {
        "projects:class/starweaver/w_lunar"
    } else when (cast.celestial) {
        StarweaverCelestial.SUN -> "projects:class/starweaver/w_sun"
        StarweaverCelestial.MOON -> "projects:class/starweaver/w_moon"
        StarweaverCelestial.STAR -> "projects:class/starweaver/w_star"
    }
    StarweaverSlot.E -> if (cast.kind == StarweaverCastKind.CONJUNCTION) {
        "projects:class/starweaver/e_stellar"
    } else when (cast.celestial) {
        StarweaverCelestial.SUN -> "projects:class/starweaver/e_sun"
        StarweaverCelestial.MOON -> "projects:class/starweaver/e_moon"
        StarweaverCelestial.STAR -> "projects:class/starweaver/e_star"
    }
    StarweaverSlot.Q -> error("Q does not have a ground zone preset")
}

internal fun showStarweaverPreset(
    player: Player,
    id: String,
    origin: Point,
    direction: Vec,
    scheduler: ParticleAnimationScheduler,
    manager: ParticleManager,
    durationTicks: Int,
) {
    startParticlePreset(
        player = player,
        id = id,
        scheduler = scheduler,
        origin = origin,
        direction = direction,
        manager = manager,
        values = mapOf(
            "duration" to durationTicks.coerceIn(1, 40).toDouble(),
        ),
    )
}
