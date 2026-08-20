package dev.projects.server

/** Server-owned aerial state used only while Twin Rods is equipped. */
class TwinRodsAirState {
    private var airJumpChargesValue = 0
    private var airDodgeChargesValue = MAX_AIR_DODGE_CHARGES
    private var rewardExecutionId: Long? = null

    val airJumpCharges: Int
        get() = airJumpChargesValue

    val airDodgeCharges: Int
        get() = airDodgeChargesValue

    fun tick(isGrounded: Boolean) {
        if (isGrounded) {
            airJumpChargesValue = 0
            airDodgeChargesValue = MAX_AIR_DODGE_CHARGES
            rewardExecutionId = null
        }
    }

    fun clearAirJump() {
        airJumpChargesValue = 0
    }

    fun onAttackHit(weapon: WeaponType, isGrounded: Boolean, attackExecutionId: Long) {
        if (isGrounded || weapon != WeaponType.TWIN_RODS) return

        if (rewardExecutionId != attackExecutionId) {
            rewardExecutionId = attackExecutionId
            airJumpChargesValue = MAX_AIR_JUMP_CHARGES
            airDodgeChargesValue = (airDodgeChargesValue + 1).coerceAtMost(MAX_AIR_DODGE_CHARGES)
        }
    }

    fun canStartAirJump(): Boolean = airJumpChargesValue > 0

    fun consumeAirJump(): Boolean {
        if (!canStartAirJump()) return false
        airJumpChargesValue--
        return true
    }

    fun canStartAirDodge(): Boolean = airDodgeChargesValue > 0

    fun consumeAirDodge(): Boolean {
        if (!canStartAirDodge()) return false
        airDodgeChargesValue--
        return true
    }

    companion object {
        const val MAX_AIR_JUMP_CHARGES = 1
        const val MAX_AIR_DODGE_CHARGES = 2
    }
}
