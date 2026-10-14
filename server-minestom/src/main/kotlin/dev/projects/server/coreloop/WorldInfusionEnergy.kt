package dev.projects.server.coreloop

/** Isolated recipe-owned ledger. 1 E = 1000 milliE, a DEMO unit, not an item or account currency. */
internal data class InfusionEnergyLedger(
    val recipeId:String=WorldInfusionEnergy.recipe.id,
    val requiredMilli:Long=WorldInfusionEnergy.recipe.requiredMilli,
    val receivedMilli:Long=0
) {
    init { require(recipeId==WorldInfusionEnergy.recipe.id && requiredMilli==WorldInfusionEnergy.recipe.requiredMilli && receivedMilli in 0..requiredMilli) }
    val remainingMilli get()=requiredMilli-receivedMilli
}

/** New opt-in version. Fixed recipe E, external P, atomically saved with actual Jar/material progress. */
internal object WorldInfusionEnergy {
    data class Recipe(val id:String,val requiredMilli:Long)
    val recipe=Recipe("glacial-attunement-energy-demo",120_000)
    const val STEP_TICKS=4L // 5 Hz finite local durable updates; no wall-clock catch-up after disconnect.
    data class Supply(val perSecond:Int) {
        init { require(perSecond in 0..18) } // INPUT bound for this comparison, not a production cap.
        val deliveredPerStep get()=perSecond*1_000L*STEP_TICKS/20
        val drive get()=WorldInfusionConfluence.Drive((perSecond/18.0).coerceIn(0.0,1.0))
        val flightTicks get()=if(perSecond==0)42 else (42.0*6/perSecond).toInt().coerceIn(14,42)
        val tuftStep get()=if(perSecond>=12)1 else 2
    }
    fun start(s:WorldInfusionState):WorldInfusionState {
        if(s.phase!=InfusionPhase.READY)require(s.energy!=null) { "旧試作の稼働保存は自動換算しません。専用saveを使用してください" }
        val next=WorldInfusionRules.start(if(s.phase==InfusionPhase.READY)s.copy(energy=null) else s)
        return next.copy(energy=if(s.phase==InfusionPhase.READY)InfusionEnergyLedger() else s.energy)
    }
    private fun threshold(s:WorldInfusionState):Long {
        if(s.phase==InfusionPhase.ESSENTIA) {
            val waves=s.supplied.getOrDefault(InfusionAspect.EMBER,0)
            return (waves+1)*20_000L
        }
        val items=s.consumed.values.sum()
        return if(items<4)60_000L+(items+1)*10_000L else recipe.requiredMilli
    }
    fun channel(s:WorldInfusionState):Double? = s.energy?.takeIf {
        !s.paused && s.phase==InfusionPhase.INGREDIENTS && s.consumed==WorldInfusionRules.ingredients
    }?.let { ((it.receivedMilli-100_000)/20_000.0).coerceIn(0.0,1.0) }
    fun validate(s:WorldInfusionState) {
        val e=requireNotNull(s.energy).receivedMilli
        when(s.phase) {
            InfusionPhase.ESSENTIA->{
                val w=s.supplied.getOrDefault(InfusionAspect.EMBER,0);require(w in 0..2)
                require(s.supplied==if(w==0)emptyMap() else InfusionAspect.entries.associateWith { w })
                require(e in w*20_000L..(w+1)*20_000L)
            }
            InfusionPhase.INGREDIENTS->{
                val n=s.consumed.values.sum();require(e in (60_000L+n*10_000)..if(n==4)120_000 else 70_000L+n*10_000)
            }
            else->Unit // READY retains the previous cancelled/complete receipt; next new ritual resets it.
        }
    }
    fun advance(s:WorldInfusionState,p:Supply):Pair<WorldInfusionState,List<InfusionPulse>> {
        if(p.perSecond==0 || s.paused || s.phase in setOf(InfusionPhase.READY,InfusionPhase.COMPLETE))return s to emptyList()
        val ledger=requireNotNull(s.energy)
        if(s.phase==InfusionPhase.ESSENTIA) {
            val needs=WorldInfusionRules.cost.keys.filter { s.supplied.getOrDefault(it,0)<WorldInfusionRules.cost.getValue(it) }
            val missing=needs.any { a->s.reservoir.getOrDefault(a,0)==0 && s.jars.none { it.aspect==a && it.amount>0 && it.cell?.nearby(requireNotNull(s.matrix),WorldInfusionRules.JAR_RADIUS)==true } }
            if(missing)return s.copy(paused=true) to emptyList() // No charge/drain of any kind for a missing wave.
        }
        val target=threshold(s)
        val charged=s.copy(energy=ledger.copy(receivedMilli=minOf(target,ledger.receivedMilli+p.deliveredPerStep)))
        if(charged.energy!!.receivedMilli<target)return charged to emptyList()
        return WorldInfusionConfluence.tick(charged)
    }
}
