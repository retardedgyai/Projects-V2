package dev.projects.server.coreloop

import java.util.UUID

/** Local world playground only. No production save, research unlock or currency is opened. */
internal enum class InfusionAspect(val label: String, val rgb: Int) {
    EMBER("火", 0xee9850), TIDE("水", 0x84b6bd), GALE("風", 0xc6d6a0)
}
internal data class InfusionCell(val x: Int, val y: Int, val z: Int) {
    init { require(x in -16..16 && z in -16..16 && y == 41) }
    fun nearby(other: InfusionCell, radius: Int) = (x-other.x)*(x-other.x)+(z-other.z)*(z-other.z) <= radius*radius
}
internal data class InfusionJar(val id: UUID, val aspect: InfusionAspect, val amount: Int, val capacity: Int,
    val cell: InfusionCell? = null) { init { require(capacity > 0 && amount in 0..capacity) } }
internal data class InfusionPedestal(val cell: InfusionCell, val item: CoreResource? = null)
internal enum class InfusionPhase { READY, ESSENTIA, INGREDIENTS, COMPLETE }
internal enum class InfusionGearPlace { INVENTORY, CENTER, OUTPUT }
internal data class InfusionPulse(val jar: UUID? = null, val ingredient: InfusionCell? = null,
    val aspect: InfusionAspect? = null, val completed: Boolean = false)
internal data class WorldInfusionState(val account: CoreAccount, val matrix: InfusionCell? = null,
    val pedestals: List<InfusionPedestal> = emptyList(), val jars: List<InfusionJar> = emptyList(),
    val gearPlace: InfusionGearPlace = InfusionGearPlace.INVENTORY,
    val phase: InfusionPhase = InfusionPhase.READY,
    val supplied: Map<InfusionAspect, Int> = emptyMap(), val reservoir: Map<InfusionAspect, Int> = emptyMap(),
    val consumed: Map<CoreResource, Int> = emptyMap(), val paused: Boolean = false,
    val energy: InfusionEnergyLedger? = null) {
    val gear get() = account.storedGear.single()
    init {
        require(account.storedGear.size == 1 && account.offers.isEmpty() && account.activeRun == null)
        require(pedestals.size <= 4 && pedestals.map { it.cell }.distinct().size == pedestals.size)
        require(jars.map { it.id }.distinct().size == jars.size)
        require(pedestals.all { it.item == null || it.item in WorldInfusionRules.ingredients })
        val cells = pedestals.map { it.cell } + jars.mapNotNull { it.cell }
        require(cells.distinct().size == cells.size)
        require(matrix != null || (phase == InfusionPhase.READY && gearPlace == InfusionGearPlace.INVENTORY && supplied.isEmpty()))
        require(matrix == null || cells.none { WorldInfusionRules.structureCells(matrix).contains(it) })
        require(supplied.all { it.value in 1..WorldInfusionRules.cost.getValue(it.key) })
        require(reservoir.all { it.value in 1..WorldInfusionRules.cost.getValue(it.key) })
        require(consumed.all { it.key in WorldInfusionRules.ingredients && it.value in 1..WorldInfusionRules.ingredients.getValue(it.key) })
        require(phase != InfusionPhase.COMPLETE || gearPlace == InfusionGearPlace.OUTPUT)
        require(phase !in setOf(InfusionPhase.ESSENTIA, InfusionPhase.INGREDIENTS) || gearPlace == InfusionGearPlace.CENTER)
        require(phase != InfusionPhase.ESSENTIA || consumed.isEmpty())
        require(phase !in setOf(InfusionPhase.INGREDIENTS, InfusionPhase.COMPLETE) || supplied == WorldInfusionRules.cost)
        require(phase != InfusionPhase.READY || (supplied.isEmpty() && consumed.isEmpty() && !paused))
        require(phase != InfusionPhase.COMPLETE || consumed == WorldInfusionRules.ingredients)
        require(energy==null || phase!=InfusionPhase.COMPLETE || energy.receivedMilli==energy.requiredMilli)
        if(energy!=null)WorldInfusionEnergy.validate(this)
    }
    companion object {
        fun fresh(owner: UUID): WorldInfusionState {
            fun id(s: String) = UUID.nameUUIDFromBytes("$owner/infusion-lab/$s".toByteArray(Charsets.UTF_8))
            val gear = CoreStoredGear(CoreGearIdentity(id("weapon"), owner, quality = 12, itemLevel = 12),
                CoreGearSlot.WEAPON, 2, CoreGearRarity.MAGIC, CoreEnhancementState(4), listOf(
                    CoreEquippedAffix(CoreGearSlot.WEAPON, 0, CoreAffixStone(id("fire"), "projects:flame", 2, 6.0)),
                    CoreEquippedAffix(CoreGearSlot.WEAPON, 1, CoreAffixStone(id("haste"), "projects:haste", 2, 7.0))))
            return WorldInfusionState(CoreAccount(owner, storedGear = listOf(gear), balances = mapOf(
                CoreMaterial(CoreResource.INGOT, 2) to 2L, CoreMaterial(CoreResource.CLOTH, 2) to 1L,
                CoreMaterial(CoreResource.STONE_BLOCK, 2) to 1L)), jars = InfusionAspect.entries.map {
                InfusionJar(id("jar-${it.name}"), it, 4, 4) // Small filled test jars; capacity is per-instance data, never a global limit.
            })
        }
    }
}

/** One recipe; durations/range/cancellation and frost conversion are ProjectS playground proposals. */
internal object WorldInfusionRules {
    const val MOD = "projects:glacial-attunement"
    const val JAR_RADIUS = 6
    val cost = mapOf(InfusionAspect.EMBER to 3, InfusionAspect.TIDE to 2, InfusionAspect.GALE to 2)
    val ingredients = mapOf(CoreResource.INGOT to 2, CoreResource.CLOTH to 1, CoreResource.STONE_BLOCK to 1)
    fun structureCells(at: InfusionCell): Set<InfusionCell> = buildSet {
        add(at); for (x in listOf(-1, 1)) for (z in listOf(-1, 1)) {
            if (at.x+x in -16..16 && at.z+z in -16..16) add(at.copy(x=at.x+x, z=at.z+z))
        }
    }
    private fun idle(s: WorldInfusionState) { require(s.phase == InfusionPhase.READY) { "稼働中は配置を変更できません" } }
    fun placeMatrix(s: WorldInfusionState, cell: InfusionCell): WorldInfusionState {
        require(s.matrix == null && cell.x in -14..14 && cell.z in -14..14) { "祭壇は一台です。端から離して設置してください" }
        require((s.pedestals.map { it.cell } + s.jars.mapNotNull { it.cell }).none { it in structureCells(cell) }) { "設置先が塞がっています" }
        return s.copy(matrix=cell)
    }
    fun placePedestal(s: WorldInfusionState, cell: InfusionCell): WorldInfusionState {
        idle(s); require(s.matrix != null && cell.nearby(s.matrix, 8)) { "祭壇の周囲に設置してください" }
        require(s.pedestals.size < 4 && cell !in occupied(s)) { "台座を置けません" }
        return s.copy(pedestals=s.pedestals + InfusionPedestal(cell))
    }
    fun placeJar(s: WorldInfusionState, id: UUID, cell: InfusionCell): WorldInfusionState {
        require(cell !in occupied(s)) { "設置先が塞がっています" }
        val jar = s.jars.single { it.id == id }; require(jar.cell == null) { "既に設置されています" }
        return s.copy(jars=s.jars.map { if (it.id == id) it.copy(cell=cell) else it })
    }
    fun removeJar(s: WorldInfusionState, id: UUID) = s.copy(jars=s.jars.map { if (it.id == id) it.copy(cell=null) else it })
    fun occupied(s: WorldInfusionState) = s.pedestals.map { it.cell }.toSet() + s.jars.mapNotNull { it.cell } +
        (s.matrix?.let(::structureCells) ?: emptySet())
    fun removePedestal(s: WorldInfusionState, cell: InfusionCell): WorldInfusionState {
        idle(s); val p=s.pedestals.single { it.cell == cell }; require(p.item == null) { "先に素材を回収してください" }
        return s.copy(pedestals=s.pedestals.filterNot { it.cell == cell })
    }
    fun placeGear(s: WorldInfusionState, id: UUID): WorldInfusionState {
        idle(s); require(s.matrix != null && s.gearPlace == InfusionGearPlace.INVENTORY && s.gear.identity.id == id)
        require(s.gear.affixes.none { it.stone.modId == MOD }) { "この装備は既に調律されています" }
        return s.copy(gearPlace=InfusionGearPlace.CENTER)
    }
    fun takeGear(s: WorldInfusionState): WorldInfusionState {
        require(s.gearPlace != InfusionGearPlace.INVENTORY && s.phase in setOf(InfusionPhase.READY, InfusionPhase.COMPLETE))
        return s.copy(gearPlace=InfusionGearPlace.INVENTORY, phase=InfusionPhase.READY, supplied=emptyMap(), consumed=emptyMap())
    }
    fun material(s: WorldInfusionState, cell: InfusionCell, resource: CoreResource?, take: Boolean = false): WorldInfusionState {
        idle(s); val p=s.pedestals.single { it.cell == cell }
        val r=if (take) requireNotNull(p.item) else requireNotNull(resource)
        require(r in ingredients && (take || p.item == null))
        val m=CoreMaterial(r, 2); val amount=s.account.amount(m) + if (take) 1 else -1
        require(amount >= 0) { "素材がありません" }
        return s.copy(account=s.account.copy(balances=s.account.balances+(m to amount)),
            pedestals=s.pedestals.map { if (it.cell == cell) it.copy(item=if(take) null else r) else it })
    }
    fun missing(s: WorldInfusionState): String? {
        if (s.matrix == null || s.gearPlace != InfusionGearPlace.CENTER) return "中心台座に装備を載せてください"
        val f=s.gear.affixes.singleOrNull { it.index == 0 }
        if (f?.stone?.modId != "projects:flame") return "固定0枠に火属性MODが必要です（試作レシピ）"
        if (s.phase == InfusionPhase.READY && s.pedestals.mapNotNull { it.item }.groupingBy { it }.eachCount() != ingredients)
            return "台座にT2鉄×2・布×1・加工石×1を載せてください"
        for ((a,n) in cost) {
            val available=s.supplied.getOrDefault(a,0)+s.reservoir.getOrDefault(a,0)+
                s.jars.filter { it.aspect == a && it.cell?.nearby(s.matrix, JAR_RADIUS) == true }.sumOf { it.amount }
            if (available < n) return "近くの設置Jar：${a.label}が${n-available}不足（試作範囲6ブロック）"
        }
        return null
    }
    fun start(s: WorldInfusionState): WorldInfusionState {
        require(s.phase == InfusionPhase.READY || s.paused) { "既に稼働中です" }
        require(missing(s) == null) { missing(s)!! }
        return s.copy(phase=if(s.phase == InfusionPhase.READY) InfusionPhase.ESSENTIA else s.phase, paused=false)
    }
    fun tick(s: WorldInfusionState): Pair<WorldInfusionState, InfusionPulse?> {
        if (s.paused || s.phase in setOf(InfusionPhase.READY, InfusionPhase.COMPLETE)) return s to null
        if (s.phase == InfusionPhase.ESSENTIA) {
            val a=cost.keys.firstOrNull { s.supplied.getOrDefault(it,0) < cost.getValue(it) }
                ?: return s.copy(phase=InfusionPhase.INGREDIENTS) to null
            val filled=s.supplied+(a to s.supplied.getOrDefault(a,0)+1)
            if (s.reservoir.getOrDefault(a,0) > 0) {
                val b=s.reservoir.toMutableMap(); val n=b.getValue(a)-1; if(n==0)b.remove(a) else b[a]=n
                return s.copy(supplied=filled,reservoir=b) to InfusionPulse(aspect=a)
            }
            val jar=s.jars.firstOrNull { it.aspect == a && it.amount > 0 && it.cell?.nearby(requireNotNull(s.matrix),JAR_RADIUS) == true }
                ?: return s.copy(paused=true) to null
            return s.copy(supplied=filled, jars=s.jars.map { if(it.id == jar.id)it.copy(amount=it.amount-1) else it }) to InfusionPulse(jar=jar.id,aspect=a)
        }
        val p=s.pedestals.firstOrNull { it.item != null }
        if (p != null) return s.copy(pedestals=s.pedestals.map { if(it.cell == p.cell) it.copy(item=null) else it },
            consumed=s.consumed+(p.item!! to s.consumed.getOrDefault(p.item,0)+1)) to InfusionPulse(ingredient=p.cell)
        require(s.consumed == ingredients)
        val g=s.gear
        val converted=CoreStoredGear(g.identity,g.slot,g.tier,g.rarity,g.enhancement,
            g.affixes.map { if(it.index==0) it.copy(stone=it.stone.copy(modId=MOD)) else it },g.legacy,g.broken)
        return s.copy(account=s.account.copy(storedGear=listOf(converted)),phase=InfusionPhase.COMPLETE,gearPlace=InfusionGearPlace.OUTPUT) to InfusionPulse(completed=true)
    }
    /** Proposal: return consumed ordinary items, hold supplied essence in the physical Matrix, never mint Jar content. */
    fun cancel(s: WorldInfusionState): WorldInfusionState {
        require(s.phase in setOf(InfusionPhase.ESSENTIA, InfusionPhase.INGREDIENTS))
        val b=s.account.balances.toMutableMap()
        s.consumed.forEach { (r,n) -> val m=CoreMaterial(r,2);b[m]=(b[m]?:0)+n }
        val reservoir=s.reservoir.toMutableMap();s.supplied.forEach { (a,n)->reservoir[a]=(reservoir[a]?:0)+n }
        return s.copy(account=s.account.copy(balances=b),phase=InfusionPhase.READY,paused=false,
            supplied=emptyMap(),consumed=emptyMap(),reservoir=reservoir)
    }
}
