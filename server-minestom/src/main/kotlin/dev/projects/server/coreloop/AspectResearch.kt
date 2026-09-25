package dev.projects.server.coreloop

/** Research ink is separate from physical Essentia in jars. IDs are stable save keys. */
internal data class AspectDefinition(
    val id: String,
    val name: String,
    val color: Int,
    val components: List<String> = emptyList(),
)

internal object AspectCatalog {
    val all = listOf(
        AspectDefinition("aer", "Aer / 風", 0xDBE8AF),
        AspectDefinition("aqua", "Aqua / 水", 0x78CADB),
        AspectDefinition("ignis", "Ignis / 火", 0xEBA16B),
        AspectDefinition("terra", "Terra / 地", 0xA9B889),
        AspectDefinition("ordo", "Ordo / 秩序", 0xE8D9B4),
        AspectDefinition("perditio", "Perditio / 混沌", 0xB9A0C8),
        AspectDefinition("lux", "Lux / 光", 0xF3DB91, listOf("aer", "ignis")),
        AspectDefinition("tempestas", "Tempestas / 嵐", 0xA4D5D7, listOf("aer", "aqua")),
        AspectDefinition("motus", "Motus / 運動", 0xD6C7AA, listOf("aer", "ordo")),
        AspectDefinition("vacuos", "Vacuos / 虚空", 0xAD9CC8, listOf("aer", "perditio")),
        AspectDefinition("victus", "Victus / 生命", 0xB5D89F, listOf("aqua", "terra")),
        AspectDefinition("gelum", "Gelum / 氷", 0xB5DFE9, listOf("aqua", "ordo")),
        AspectDefinition("venenum", "Venenum / 毒", 0xB8C687, listOf("aqua", "perditio")),
        AspectDefinition("potentia", "Potentia / 力", 0xE9BB86, listOf("ignis", "ordo")),
        AspectDefinition("mortuus", "Mortuus / 死", 0xA89FAD, listOf("terra", "perditio")),
        AspectDefinition("metallum", "Metallum / 金属", 0xC8B79B, listOf("terra", "ordo")),
    )
    val byId = all.associateBy(AspectDefinition::id)
    val primal = all.filter { it.components.isEmpty() }
    val compound = all.filter { it.components.isNotEmpty() }

    init {
        require(byId.size == all.size)
        require(compound.all { it.components.size == 2 && it.components.all(byId::containsKey) })
    }

    fun linked(first: String, second: String): Boolean =
        first != second && (byId[first]?.components?.contains(second) == true ||
            byId[second]?.components?.contains(first) == true)

    fun combine(first: String, second: String): AspectDefinition? =
        compound.firstOrNull { it.components.toSet() == setOf(first, second) }
}

internal data class ResearchDefinition(
    val id: String,
    val title: String,
    val description: String,
    val anchors: Map<Int, String>,
)

/** Axial radius-two hexes, spaced into the vanilla nine-column inventory. */
internal object ResearchBoard {
    val cells: Map<Int, Pair<Int, Int>> = buildMap {
        for (r in -2..2) for (q in -2..2) {
            if (kotlin.math.abs(q + r) > 2) continue
            val column = q * 2 + r + 4
            put((r + 2) * 9 + column, q to r)
        }
    }

    fun neighbors(slot: Int): Set<Int> {
        val (q, r) = cells[slot] ?: return emptySet()
        return cells.filterValues { (otherQ, otherR) ->
            val dq = otherQ - q
            val dr = otherR - r
            (dq == 1 && dr == 0) || (dq == -1 && dr == 0) ||
                (dq == 0 && dr == 1) || (dq == 0 && dr == -1) ||
                (dq == 1 && dr == -1) || (dq == -1 && dr == 1)
        }.keys
    }

    fun connectedAnchors(research: ResearchDefinition, placed: Map<Int, String>): Set<Int> {
        val occupied = research.anchors + placed
        if (occupied.any { (slot, aspect) -> slot !in cells || aspect !in AspectCatalog.byId }) return emptySet()
        val start = research.anchors.keys.first()
        val reached = mutableSetOf(start)
        val queue = ArrayDeque<Int>()
        queue.add(start)
        while (queue.isNotEmpty()) {
            val slot = queue.removeFirst()
            for (next in neighbors(slot)) {
                if (next !in reached && occupied[next]?.let { AspectCatalog.linked(occupied.getValue(slot), it) } == true) {
                    reached += next
                    queue.add(next)
                }
            }
        }
        return research.anchors.keys.intersect(reached)
    }

    fun complete(research: ResearchDefinition, placed: Map<Int, String>): Boolean =
        connectedAnchors(research, placed).size == research.anchors.size
}

internal object ResearchCatalog {
    /** Each subject has three fixed clues. Players supply a valid path across the hex board. */
    val all = listOf(
        ResearchDefinition("lamp", "星灯", "光を器に留める", mapOf(18 to "ignis", 22 to "aer", 26 to "aqua")),
        ResearchDefinition("living_ink", "生きたインク", "観測を記録へ変える", mapOf(2 to "terra", 22 to "aqua", 42 to "ordo")),
        ResearchDefinition("storm_glass", "嵐ガラス", "空模様を閉じ込める", mapOf(6 to "ignis", 22 to "aer", 38 to "ordo")),
        ResearchDefinition("ore_sight", "鉱脈視", "金属の眠りを聴く", mapOf(18 to "perditio", 22 to "terra", 26 to "ordo")),
        ResearchDefinition("frost_seal", "氷の封", "水の動きを止める", mapOf(2 to "aer", 22 to "aqua", 42 to "ordo")),
        ResearchDefinition("void_lens", "空洞レンズ", "見えない間隙を覗く", mapOf(6 to "perditio", 22 to "aer", 38 to "aqua")),
        ResearchDefinition("kinetic_rune", "動力刻印", "動きを符へ移す", mapOf(18 to "ignis", 22 to "ordo", 26 to "aer")),
        ResearchDefinition("venom_filter", "毒の分離", "水に混じる異物を知る", mapOf(2 to "terra", 22 to "aqua", 42 to "perditio")),
        ResearchDefinition("ember_engine", "熾火装置", "熱を秩序へ導く", mapOf(6 to "aer", 22 to "ignis", 38 to "ordo")),
        ResearchDefinition("last_bloom", "終花", "生命と終わりを結ぶ", mapOf(18 to "aqua", 22 to "terra", 26 to "perditio")),
    )
    val byId = all.associateBy(ResearchDefinition::id)

    init {
        require(byId.size == all.size)
        require(all.all { it.anchors.keys.all(ResearchBoard.cells::containsKey) && it.anchors.values.all(AspectCatalog.byId::containsKey) })
    }
}
