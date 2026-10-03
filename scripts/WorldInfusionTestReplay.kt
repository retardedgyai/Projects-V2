package dev.projects.server.coreloop

/** Explicit local-test visual ledger. It never owns a repository or grants output. */
internal class WorldInfusionTestReplay(val original:WorldInfusionState) {
    val fixture:WorldInfusionState
    private var finished=false
    init {
        require(original.phase !in setOf(InfusionPhase.ESSENTIA,InfusionPhase.INGREDIENTS))
        require(original.matrix!=null && original.pedestals.size==4 && original.jars.size==3 && original.jars.all { it.cell!=null })
        val fresh=WorldInfusionState.fresh(original.account.playerId)
        val ingredients=WorldInfusionRules.ingredients.entries.flatMap { (r,n)->List(n){r} }
        fixture=WorldInfusionEnergy.start(fresh.copy(account=fresh.account.copy(balances=emptyMap()),matrix=original.matrix,
            pedestals=original.pedestals.mapIndexed { i,p->p.copy(item=ingredients[i]) },
            jars=original.jars.map { it.copy(amount=it.capacity) },gearPlace=InfusionGearPlace.CENTER))
    }
    fun accept(next:WorldInfusionState):WorldInfusionState {
        check(!finished)
        // The existing timing/receipt calculation is reused, but all item/account changes are discarded.
        return next.copy(account=fixture.account)
    }
    fun restore():WorldInfusionState { finished=true;return original }
}
