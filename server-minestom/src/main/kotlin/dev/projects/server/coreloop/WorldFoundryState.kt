package dev.projects.server.coreloop

import java.util.UUID
import kotlin.math.abs

internal enum class FoundryEquipment(val label: String) {
    HEATER("炭床"), MELTER("耐火るつぼ"), FAUCET("出湯口"), TABLE("砂床鋳造台")
}
internal enum class FoundryShape(val label: String, val units: Int) { ROD("芯棒", 2), PLATE("補強板", 4) }
internal enum class FoundryPhase { EMPTY, SAND, PATTERN, MOLD, POURING, COOLING, OUTPUT }
internal data class FoundryCell(val x: Int, val y: Int, val z: Int) {
    init { require(x in -12..12 && z in -12..12 && y in 41..42) }
    fun below() = FoundryCell(x, y-1, z)
}

/** Private playground envelope. Core T2 ingots keep their existing identity; no production account is opened. */
internal data class WorldFoundryState(
    val account: CoreAccount,
    val equipment: Map<FoundryEquipment, FoundryCell> = emptyMap(),
    val coal: Int = 8, val sand: Int = 8, val fuel: Int = 0,
    val input: Int = 0, val meltSeconds: Int = 0, val liquid: Int = 0,
    val phase: FoundryPhase = FoundryPhase.EMPTY,
    val shape: FoundryShape? = null, val castUnits: Int = 0, val seconds: Int = 0,
    val parts: Map<FoundryShape, Int> = emptyMap()
) {
    init {
        require(account.balances.keys.all { it == WorldFoundryRules.metal } && account.amount(WorldFoundryRules.metal) in 0..1000)
        require(account.storedGear.isEmpty() && account.offers.isEmpty() && account.activeRun == null)
        require(equipment.values.distinct().size == equipment.size)
        require(equipment.all { (kind,c) -> c.y == if(kind in setOf(FoundryEquipment.MELTER,FoundryEquipment.FAUCET))42 else 41 })
        require(coal in 0..100 && sand in 0..100 && fuel in 0..8 && input in 0..3 && meltSeconds in 0..6 && liquid in 0..12)
        require(meltSeconds == 0 || input > 0)
        require(input*4+liquid <= 12)
        require(fuel==0 || FoundryEquipment.HEATER in equipment)
        require((input==0 && liquid==0) || FoundryEquipment.MELTER in equipment)
        require(parts.values.all { it in 0..1000 })
        require(phase == FoundryPhase.EMPTY || FoundryEquipment.TABLE in equipment)
        require((shape == null) == (phase in setOf(FoundryPhase.EMPTY,FoundryPhase.SAND)))
        require(castUnits == if(phase in setOf(FoundryPhase.POURING,FoundryPhase.COOLING,FoundryPhase.OUTPUT))shape!!.units else 0)
        require(seconds in 0..5 && (seconds>0) == (phase in setOf(FoundryPhase.POURING,FoundryPhase.COOLING)))
        require(phase!=FoundryPhase.POURING || seconds<=2)
    }
    companion object {
        fun fresh(owner: UUID) = WorldFoundryState(CoreAccount(owner, balances=mapOf(WorldFoundryRules.metal to 8L)))
    }
}

internal object WorldFoundryRules {
    val metal=CoreMaterial(CoreResource.INGOT,2)
    const val INGOT_UNITS=4 // ProjectS quarter-ingot units, deliberately not Tinkers mB.
    fun place(s:WorldFoundryState,kind:FoundryEquipment,c:FoundryCell):WorldFoundryState {
        require(kind !in s.equipment && c !in s.equipment.values) { "設置済み、または場所が塞がっています" }
        require(c.y==if(kind in setOf(FoundryEquipment.MELTER,FoundryEquipment.FAUCET))42 else 41) { "るつぼ・出湯口は二段目、炭床・鋳造台は床の上です" }
        return s.copy(equipment=s.equipment+(kind to c))
    }
    fun linked(s:WorldFoundryState):Boolean {
        val m=s.equipment[FoundryEquipment.MELTER] ?: return false
        val h=s.equipment[FoundryEquipment.HEATER] ?: return false
        val f=s.equipment[FoundryEquipment.FAUCET] ?: return false
        val t=s.equipment[FoundryEquipment.TABLE] ?: return false
        return h==m.below() && f.y==m.y && abs(f.x-m.x)+abs(f.z-m.z)==1 && t==f.below()
    }
    fun hot(s:WorldFoundryState)=s.equipment[FoundryEquipment.MELTER]?.let { s.equipment[FoundryEquipment.HEATER]==it.below() } == true
    fun remove(s:WorldFoundryState,kind:FoundryEquipment):WorldFoundryState {
        require(kind in s.equipment)
        require(s.phase !in setOf(FoundryPhase.POURING,FoundryPhase.COOLING)) { "注湯・冷却中は解体できません。離れても保存されます" }
        require(when(kind) {
            FoundryEquipment.MELTER -> s.input==0 && s.liquid==0
            FoundryEquipment.HEATER -> s.fuel==0 && s.meltSeconds==0
            FoundryEquipment.TABLE -> s.phase==FoundryPhase.EMPTY
            FoundryEquipment.FAUCET -> true
        }) { "中身を回収してから解体してください" }
        return s.copy(equipment=s.equipment-kind)
    }
    fun fuel(s:WorldFoundryState):WorldFoundryState {
        require(FoundryEquipment.HEATER in s.equipment && s.coal>0 && s.fuel<8) { "炭がないか炭床が満杯です" }
        return s.copy(coal=s.coal-1,fuel=s.fuel+1)
    }
    fun takeFuel(s:WorldFoundryState):WorldFoundryState {
        require(s.fuel>0 && s.coal<100);return s.copy(fuel=s.fuel-1,coal=s.coal+1)
    }
    fun insert(s:WorldFoundryState):WorldFoundryState {
        require(FoundryEquipment.MELTER in s.equipment && s.account.amount(metal)>0 && s.input*4+s.liquid<=8) { "T2インゴットがないか、るつぼが満杯です" }
        return s.copy(account=s.account.copy(balances=s.account.balances+(metal to s.account.amount(metal)-1)),input=s.input+1)
    }
    fun takeInput(s:WorldFoundryState):WorldFoundryState {
        require(s.input>0 && s.meltSeconds==0) { "溶解中です。残った金属は冷却回収してください" }
        return s.copy(account=s.account.copy(balances=s.account.balances+(metal to s.account.amount(metal)+1)),input=s.input-1)
    }
    fun reclaim(s:WorldFoundryState):WorldFoundryState {
        require(s.liquid>=4 && s.phase!=FoundryPhase.POURING) { "回収にはインゴット1本分の湯が必要です" }
        return s.copy(account=s.account.copy(balances=s.account.balances+(metal to s.account.amount(metal)+1)),liquid=s.liquid-4)
    }
    fun sand(s:WorldFoundryState):WorldFoundryState {
        require(FoundryEquipment.TABLE in s.equipment && s.phase==FoundryPhase.EMPTY && s.sand>0) { "鋳造台が空でないか砂がありません" }
        return s.copy(sand=s.sand-1,phase=FoundryPhase.SAND)
    }
    fun imprint(s:WorldFoundryState,shape:FoundryShape):WorldFoundryState {
        require(s.phase==FoundryPhase.SAND);return s.copy(phase=FoundryPhase.PATTERN,shape=shape)
    }
    fun takePattern(s:WorldFoundryState):WorldFoundryState {
        require(s.phase==FoundryPhase.PATTERN);return s.copy(phase=FoundryPhase.MOLD)
    }
    fun clearMold(s:WorldFoundryState):WorldFoundryState {
        require(s.phase in setOf(FoundryPhase.SAND,FoundryPhase.MOLD));return s.copy(phase=FoundryPhase.EMPTY,shape=null,sand=s.sand+1)
    }
    fun pour(s:WorldFoundryState):WorldFoundryState {
        require(linked(s)) { "炭床の上にるつぼ、横に出湯口、その真下に鋳造台を組んでください" }
        require(s.phase==FoundryPhase.MOLD && s.liquid>=s.shape!!.units) { "原型を外した砂型と、必要量の溶湯が必要です" }
        // All metal moves into durable table escrow before the flow is shown. Repeated clicks cannot reserve it twice.
        return s.copy(liquid=s.liquid-s.shape.units,castUnits=s.shape.units,phase=FoundryPhase.POURING,seconds=2)
    }
    fun takePart(s:WorldFoundryState):WorldFoundryState {
        require(s.phase==FoundryPhase.OUTPUT)
        return s.copy(parts=s.parts+(s.shape!! to (s.parts[s.shape]?:0)+1),phase=FoundryPhase.EMPTY,shape=null,castUnits=0)
    }
    /** One durable active second; disconnected islands receive no time and resume with their exact remaining seconds. */
    fun tick(s:WorldFoundryState):WorldFoundryState {
        var n=s
        if(s.phase in setOf(FoundryPhase.POURING,FoundryPhase.COOLING)) {
            n=when {
                s.seconds>1 -> s.copy(seconds=s.seconds-1)
                s.phase==FoundryPhase.POURING -> s.copy(phase=FoundryPhase.COOLING,seconds=5)
                else -> s.copy(phase=FoundryPhase.OUTPUT,seconds=0)
            }
        }
        if(hot(n) && n.input>0 && n.liquid<=8) {
            if(n.meltSeconds==0 && n.fuel>0)n=n.copy(fuel=n.fuel-1,meltSeconds=6)
            else if(n.meltSeconds>1)n=n.copy(meltSeconds=n.meltSeconds-1)
            else if(n.meltSeconds==1)n=n.copy(input=n.input-1,liquid=n.liquid+4,meltSeconds=0)
        }
        return n
    }
    fun metalUnits(s:WorldFoundryState)=s.account.amount(metal)*4+s.input*4+s.liquid+s.castUnits+s.parts.entries.sumOf { it.key.units*it.value }
}
