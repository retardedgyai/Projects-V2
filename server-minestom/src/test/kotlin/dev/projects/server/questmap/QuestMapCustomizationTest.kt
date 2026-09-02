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
}
