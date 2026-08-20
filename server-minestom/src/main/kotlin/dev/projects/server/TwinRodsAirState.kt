package dev.projects.server

import net.minestom.server.coordinate.Vec

/** Server-owned aerial state used only while Twin Rods is equipped. */
class TwinRodsAirState {
    private var sustainTicksRemaining = 0
    private var airDodgeChargesValue = MAX_AIR_DODGE_CHARGES
    private var chargeRecoveryExecutionId: Long? = null

    val airDodgeCharges: Int
        get() = airDodgeChargesValue

    val isSustainActive: Boolean
        get() = sustainTicksRemaining > 0

    fun tick(isGrounded: Boolean) {
        if (isGrounded) {
            sustainTicksRemaining = 0
            airDodgeChargesValue = MAX_AIR_DODGE_CHARGES
            chargeRecoveryExecutionId = null
        } else if (sustainTicksRemaining > 0) {
            sustainTicksRemaining--
        }
    }

    fun clearSustain() {
        sustainTicksRemaining = 0
    }

    fun onAttackHit(weapon: WeaponType, isGrounded: Boolean, attackExecutionId: Long) {
        if (isGrounded || weapon != WeaponType.TWIN_RODS) return

        sustainTicksRemaining = SUSTAIN_WINDOW_TICKS
        if (chargeRecoveryExecutionId != attackExecutionId) {
            chargeRecoveryExecutionId = attackExecutionId
            airDodgeChargesValue = (airDodgeChargesValue + 1).coerceAtMost(MAX_AIR_DODGE_CHARGES)
        }
    }

    fun canStartAirDodge(): Boolean = airDodgeChargesValue > 0

    fun consumeAirDodge(): Boolean {
        if (!canStartAirDodge()) return false
        airDodgeChargesValue--
        return true
    }

    companion object {
        const val MAX_AIR_DODGE_CHARGES = 2
        const val SUSTAIN_WINDOW_TICKS = 10
    }
}

internal fun applyAerialSustain(velocity: Vec, sustainActive: Boolean): Vec {
    if (!sustainActive || velocity.y() >= 0.0) return velocity
    return Vec(velocity.x(), 0.0, velocity.z())
}
