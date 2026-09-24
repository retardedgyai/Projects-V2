package dev.projects.webui.polish05

/**
 * ISOLATED LAB ONLY: dummy values copied from approved Polish05 HTML, not production balance.
 * No Player, disk, database or CoreAccount dependency. Create one instance per lab session.
 * The native input layer sends action IDs/quote IDs only. It MUST NOT accept client balances,
 * levels, prices, result modes, completion tokens or success flags.
 */
class Polish05PreviewModel(private val mode: ResultMode = ResultMode.SUCCESS_EXAMPLE,
                           private val roll: () -> Double = { kotlin.random.Random.nextDouble() }) {
    enum class ResultMode { SUCCESS_EXAMPLE, FAIL_EXAMPLE, RANDOM_EXAMPLE }
    enum class Material { ORE, CRYSTAL, CATALYST, RAW_ORE, RAW_CRYSTAL }
    enum class Recipe { ORE, CRYSTAL }
    enum class Kind { ENHANCE, REFINE }
    data class Gear(val id: String, val name: String, val tier: Int, val level: Int,
                    val base: Int, val step: Int, val speed: Double) {
        val power: Int get() = base + level * step
    }
    data class Cost(val silver: Long, val materials: Map<Material, Int>)
    data class Quote(val id: Long, val revision: Long, val kind: Kind, val gearId: String,
                     val recipe: Recipe?, val count: Int, val cost: Cost, val guaranteed: Boolean)
    data class Operation(val id: Long, val finishAtMs: Long)
    data class Receipt(val kind: Kind, val gearId: String, val success: Boolean,
                       val beforeLevel: Int, val afterLevel: Int, val beforePower: Int,
                       val afterPower: Int, val recipe: Recipe?, val count: Int, val cost: Cost)
    data class Snapshot(val revision: Long, val gears: List<Gear>, val selected: String,
                        val equipped: String, val silver: Long, val materials: Map<Material, Int>,
                        val catalyst: Boolean, val busy: Boolean, val history: List<Receipt>)
    data class Replenish(val missing: Int, val capacity: Int, val suggestedBatch: Int)
    private data class Pending(val operation: Operation, val quote: Quote, val before: Gear)
    private var gears = listOf(Gear("ember", "熾火の大剣", 2, 6, 94, 9, 1.24),
                               Gear("ash", "灰燼の大剣", 1, 3, 86, 7, 1.32))
    private var selectedId = "ember"
    private var equippedId = "ash"
    private var silver = 12_480L
    private val materials = mutableMapOf(Material.ORE to 60, Material.CRYSTAL to 12,
        Material.CATALYST to 1, Material.RAW_ORE to 44, Material.RAW_CRYSTAL to 24)
    private var catalyst = false
    private var revision = 0L
    private var serial = 0L
    private var quote: Quote? = null
    private var pending: Pending? = null
    private val history = mutableListOf<Receipt>()
    private fun gear() = gears.single { it.id == selectedId }
    private fun invalidate() { revision++; quote = null }
    private fun affordable(c: Cost) = silver >= c.silver && c.materials.all { (m,n) -> n >= 0 && materials.getValue(m) >= n }
    private fun enhanceCost(): Cost {
        val t2 = gear().tier == 2
        return Cost(if (t2) 1200 else 800, mapOf(Material.ORE to if(t2) 24 else 18,
            Material.CRYSTAL to if(t2) 4 else 2, Material.CATALYST to if(catalyst) 1 else 0))
    }
    @Synchronized fun snapshot() = Snapshot(revision, gears.toList(), selectedId, equippedId,
        silver, materials.toMap(), catalyst, pending != null, history.toList())
    @Synchronized fun select(id: String): Boolean {
        if (pending != null || gears.none { it.id == id }) return false
        selectedId = id; invalidate(); return true
    }
    @Synchronized fun equip(id: String): Boolean {
        if (pending != null || gears.none { it.id == id }) return false
        equippedId = id; invalidate(); return true
    }
    @Synchronized fun useCatalyst(enabled: Boolean): Boolean {
        if (pending != null || gear().level >= 30) return false
        catalyst = enabled; invalidate(); return true
    }
    @Synchronized fun enhancementCost(): Cost = enhanceCost()
    @Synchronized fun possibleEnhancements(): Int {
        if (gear().level >= 30) return 0
        val c = enhanceCost()
        return minOf(30-gear().level, (silver/c.silver).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            c.materials.filterValues { it>0 }.minOf { (m,n) -> materials.getValue(m)/n })
    }
    @Synchronized fun replenishment(recipe: Recipe): Replenish {
        val ore=recipe==Recipe.ORE
        val raw=if(ore) Material.RAW_ORE else Material.RAW_CRYSTAL
        val made=if(ore) Material.ORE else Material.CRYSTAL
        val unit=if(ore) 4 else 3; val fee=if(ore) 80L else 60L
        val missing=(enhanceCost().materials.getValue(made)-materials.getValue(made)).coerceAtLeast(0)
        val capacity=minOf(99, materials.getValue(raw)/unit, (silver/fee).coerceAtMost(99).toInt())
        // HTML retains a disabled quantity=1 control when capacity=0. Do not silently mint materials.
        return Replenish(missing, capacity, if(missing>0) minOf(missing,capacity).coerceAtLeast(1) else 1)
    }
    @Synchronized fun quoteEnhancement(): Quote? {
        if(pending!=null || gear().level>=30) return null
        val c=enhanceCost(); if(!affordable(c)) return null
        return Quote(++serial,revision,Kind.ENHANCE,selectedId,null,1,c,catalyst).also { quote=it }
    }
    @Synchronized fun quoteRefinement(recipe: Recipe, count: Int): Quote? {
        if(pending!=null || count !in 1..99) return null
        val ore=recipe==Recipe.ORE
        val c=Cost(count*(if(ore)80L else 60L),mapOf((if(ore)Material.RAW_ORE else Material.RAW_CRYSTAL) to count*(if(ore)4 else 3)))
        if(!affordable(c))return null
        return Quote(++serial,revision,Kind.REFINE,selectedId,recipe,count,c,false).also { quote=it }
    }
    @Synchronized fun cancelQuote() { quote=null }
    /** Server-owned confirm handler: revalidates revision and debits exactly once. */
    @Synchronized fun begin(quoteId: Long, nowMs: Long, reducedMotion: Boolean=false): Operation? {
        if(pending!=null)return null
        val q=quote ?: return null
        if(q.id!=quoteId || q.revision!=revision || q.gearId!=selectedId || !affordable(q.cost))return null
        if(q.kind==Kind.ENHANCE && gear().level>=30)return null
        val g=gear()
        val op=Operation(++serial,Math.addExact(nowMs,if(reducedMotion)80L else if(q.kind==Kind.ENHANCE)720L else 900L))
        silver-=q.cost.silver
        q.cost.materials.forEach { (m,n) -> materials[m]=materials.getValue(m)-n }
        invalidate(); pending=Pending(op,q,g)
        return op
    }
    /** Call from the SERVER scheduler only, never bind to a client action. */
    @Synchronized fun finish(operationId: Long, nowMs: Long): Receipt? {
        val p=pending ?: return null
        if(p.operation.id!=operationId || nowMs<p.operation.finishAtMs)return null
        val q=p.quote; val before=p.before
        val success=q.kind==Kind.REFINE || q.guaranteed || when(mode) {
            ResultMode.SUCCESS_EXAMPLE -> true
            ResultMode.FAIL_EXAMPLE -> false
            ResultMode.RANDOM_EXAMPLE -> roll().let { it.isFinite() && it>=0.0 && it<0.8 }
        }
        var after=before
        if(q.kind==Kind.ENHANCE) {
            if(success) { after=before.copy(level=before.level+1); gears=gears.map { if(it.id==before.id)after else it } }
            catalyst=false
        } else {
            val m=if(q.recipe==Recipe.ORE)Material.ORE else Material.CRYSTAL
            materials[m]=Math.addExact(materials.getValue(m),q.count)
        }
        val receipt=Receipt(q.kind,before.id,success,before.level,after.level,before.power,after.power,q.recipe,q.count,q.cost)
        history.add(0,receipt); if(history.size>100)history.removeAt(history.lastIndex)
        pending=null; invalidate(); return receipt
    }
}
