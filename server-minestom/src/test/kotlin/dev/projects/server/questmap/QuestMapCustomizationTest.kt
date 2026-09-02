package dev.projects.server.questmap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponents
import net.minestom.server.item.ItemAnimation
import net.minestom.server.item.Material

class QuestMapCustomizationTest {
    @Test
    fun `quest map item preserves seed and gathering modifiers`() {
        MinecraftServer.init(Auth.Offline())
        val data = QuestMapItemData(
            seed = 91_337L,
            customization = QuestMapCustomization(
                listOf(
                    QuestMapGatheringModifier(null, QuestMapGatheringStat.AMOUNT, 14),
                    QuestMapGatheringModifier(
                        QuestGatheringDiscipline.WOODCUTTING,
                        QuestMapGatheringStat.QUALITY,
                        31,
                    ),
                    QuestMapGatheringModifier(
                        QuestGatheringDiscipline.MINING,
                        QuestMapGatheringStat.DENSE_REGIONS,
                        44,
                    ),
                ),
            ),
        )

        assertEquals(data, QuestMapItems.read(QuestMapItems.questMap(data)))
        assertNull(QuestMapItems.read(QuestMapItems.gatheringTablet()))
        assertTrue(QuestMapItems.isGatheringTablet(QuestMapItems.gatheringTablet()))

        QuestGatheringDiscipline.entries.forEach { discipline ->
            val tool = discipline.toolItem()
            val consumable = assertNotNull(tool.get(DataComponents.CONSUMABLE))
            assertEquals(Material.STICK, tool.material())
            assertEquals(discipline.toolMaterial.name().toString(), tool.get(DataComponents.ITEM_MODEL))
            assertEquals(ItemAnimation.NONE, consumable.animation())
            assertTrue(consumable.consumeTicks() > 1_000)
        }
    }

    @Test
    fun `three tablets add distinct modifiers with global rolls lower than focused rolls`() {
        var data = QuestMapItemData(seed = 44L)
        repeat(QuestMapCustomization.MAX_MODIFIERS) { index ->
            data = assertNotNull(QuestMapItems.applyTablet(data, index * 7_919L + 3L))
        }

        assertEquals(QuestMapCustomization.MAX_MODIFIERS, data.customization.modifiers.size)
        assertEquals(
            data.customization.modifiers.size,
            data.customization.modifiers.map { it.key }.distinct().size,
        )
        data.customization.modifiers.forEach { modifier ->
            val allowed = when {
                modifier.discipline == null && modifier.stat == QuestMapGatheringStat.AMOUNT -> 10..20
                modifier.discipline == null -> 8..15
                modifier.stat == QuestMapGatheringStat.AMOUNT -> 25..50
                modifier.stat == QuestMapGatheringStat.DENSE_REGIONS -> 25..50
                else -> 20..40
            }
            assertTrue(modifier.percent in allowed, "Unexpected modifier magnitude: $modifier")
        }
        assertNull(QuestMapItems.applyTablet(data, 99L))
    }

    @Test
    fun `focused amount modifier adds objects only for its gathering discipline`() {
        val plan = VerdantRoadQuestPlanner.generate(91_337L)
        val base = questGatheringNodes(plan)
        val customized = questGatheringNodes(
            plan,
            QuestMapCustomization(
                listOf(
                    QuestMapGatheringModifier(
                        QuestGatheringDiscipline.WOODCUTTING,
                        QuestMapGatheringStat.AMOUNT,
                        100,
                    ),
                ),
            ),
        )

        val baseCounts = base.groupingBy { it.discipline }.eachCount()
        val customizedCounts = customized.groupingBy { it.discipline }.eachCount()
        assertTrue(
            customizedCounts.getValue(QuestGatheringDiscipline.WOODCUTTING) >
                baseCounts.getValue(QuestGatheringDiscipline.WOODCUTTING),
        )
        QuestGatheringDiscipline.entries.filterNot { it == QuestGatheringDiscipline.WOODCUTTING }.forEach { discipline ->
            assertEquals(baseCounts[discipline], customizedCounts[discipline])
        }
        assertEquals(customized.size, customized.map { it.blockPosition }.distinct().size)
        customized.drop(base.size).forEach { node ->
            assertTrue(plan.roadDistanceSquaredAt(node.blockPosition.blockX(), node.blockPosition.blockZ()) > 7 * 7)
        }
    }

    @Test
    fun `global and focused amount modifiers stack for their matching resource`() {
        val plan = VerdantRoadQuestPlanner.generate(91_337L)
        val discipline = QuestGatheringDiscipline.WOODCUTTING
        val base = questGatheringNodes(plan)
        val customized = questGatheringNodes(
            plan,
            QuestMapCustomization(
                listOf(
                    QuestMapGatheringModifier(null, QuestMapGatheringStat.AMOUNT, 100),
                    QuestMapGatheringModifier(discipline, QuestMapGatheringStat.AMOUNT, 100),
                ),
            ),
        )

        val baseCounts = base.groupingBy { it.discipline }.eachCount()
        val customizedCounts = customized.groupingBy { it.discipline }.eachCount()
        assertEquals(baseCounts.getValue(discipline) * 3, customizedCounts.getValue(discipline))
        QuestGatheringDiscipline.entries.filterNot { it == discipline }.forEach { other ->
            assertEquals(baseCounts.getValue(other) * 2, customizedCounts.getValue(other))
        }
    }

    @Test
    fun `one hundred percent quality modifier upgrades every gathering object by one tier`() {
        val plan = VerdantRoadQuestPlanner.generate(91_337L)
        val base = questGatheringNodes(plan)
        val customized = questGatheringNodes(
            plan,
            QuestMapCustomization(
                listOf(QuestMapGatheringModifier(null, QuestMapGatheringStat.QUALITY, 100)),
            ),
        )

        assertEquals(base.size, customized.size)
        base.zip(customized).forEach { (before, after) ->
            val expected = when (before.quality) {
                QuestGatheringQuality.COMMON -> QuestGatheringQuality.BOUNTIFUL
                QuestGatheringQuality.BOUNTIFUL, QuestGatheringQuality.RARE -> QuestGatheringQuality.RARE
            }
            assertEquals(expected, after.quality)
        }
    }

    @Test
    fun `focused dense region modifier creates seven node resource landmarks`() {
        val plan = VerdantRoadQuestPlanner.generate(91_337L)
        val discipline = QuestGatheringDiscipline.MINING
        val nodes = questGatheringNodes(
            plan,
            QuestMapCustomization(
                listOf(
                    QuestMapGatheringModifier(
                        discipline,
                        QuestMapGatheringStat.DENSE_REGIONS,
                        200,
                    ),
                ),
            ),
        )

        val regions = nodes.filter { it.denseRegionId != null }
            .groupBy { it.denseRegionId }
            .filterValues { region -> region.first().discipline == discipline }
        val expectedRegionCount = nodes.count { it.id in 0 until 8 && it.discipline == discipline }
        assertEquals(expectedRegionCount, regions.size)
        regions.values.forEach { region ->
            assertEquals(7, region.size)
            assertTrue(region.all { it.discipline == discipline })
            assertEquals(1, region.count { it.id == it.denseRegionId })
            region.forEach { node ->
                assertTrue(plan.roadDistanceSquaredAt(node.blockPosition.blockX(), node.blockPosition.blockZ()) > 7 * 7)
            }
        }
        assertEquals(nodes.size, nodes.map { it.blockPosition }.distinct().size)
    }

    @Test
    fun `dense regions remain valid and become more common with their modifier`() {
        var baselineRegions = 0
        var modifiedMiningRegions = 0
        var baselineMiningRegions = 0
        repeat(120) { index ->
            val plan = VerdantRoadQuestPlanner.generate(91_337L + index * 7_919L)
            val baseline = questGatheringNodes(plan)
            val modified = questGatheringNodes(
                plan,
                QuestMapCustomization(
                    listOf(
                        QuestMapGatheringModifier(
                            QuestGatheringDiscipline.MINING,
                            QuestMapGatheringStat.DENSE_REGIONS,
                            50,
                        ),
                    ),
                ),
            )
            baselineRegions += validateDenseRegions(plan, baseline)
            validateDenseRegions(plan, modified)
            baselineMiningRegions += baseline.mapNotNull { node ->
                node.denseRegionId?.let { it to node.discipline }
            }.distinct().count { (_, discipline) -> discipline == QuestGatheringDiscipline.MINING }
            modifiedMiningRegions += modified.mapNotNull { node ->
                node.denseRegionId?.let { it to node.discipline }
            }.distinct().count { (_, discipline) -> discipline == QuestGatheringDiscipline.MINING }
        }

        assertTrue(baselineRegions > 0, "Baseline maps never produced a dense region")
        assertTrue(
            modifiedMiningRegions > baselineMiningRegions * 4,
            "Focused modifier did not materially increase mining regions: $baselineMiningRegions -> $modifiedMiningRegions",
        )
    }

    private fun validateDenseRegions(plan: QuestMapPlan, nodes: List<QuestGatheringNode>): Int {
        assertEquals(nodes.size, nodes.map { it.blockPosition }.distinct().size)
        val regions = nodes.filter { it.denseRegionId != null }.groupBy { it.denseRegionId }
        regions.values.forEach { region ->
            assertEquals(7, region.size)
            assertEquals(1, region.map { it.discipline }.distinct().size)
            region.forEach { node ->
                assertTrue(plan.roadDistanceSquaredAt(node.blockPosition.blockX(), node.blockPosition.blockZ()) > 7 * 7)
            }
        }
        return regions.size
    }
}
