package dev.projects.server.questmap

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuestGatheringItemTooltipTest {
    @Test
    fun `all common and rare gathering resources use the authored tooltip`() {
        MinecraftServer.init(Auth.Offline())

        QuestGatheringDiscipline.entries.forEach { discipline ->
            assertResourceTooltip(discipline, QuestGatheringResourceGrade.COMMON, amount = 3)
            assertResourceTooltip(discipline, QuestGatheringResourceGrade.RARE, amount = 1)
        }
    }

    private fun assertResourceTooltip(
        discipline: QuestGatheringDiscipline,
        grade: QuestGatheringResourceGrade,
        amount: Int,
    ) {
        val item = discipline.resourceItem(tier = 2, grade = grade, amount = amount)
        val expectedMaterial = when (grade) {
            QuestGatheringResourceGrade.COMMON -> discipline.commonMaterial
            QuestGatheringResourceGrade.RARE -> discipline.rareMaterial
        }
        val expectedName = when (grade) {
            QuestGatheringResourceGrade.COMMON -> discipline.commonResourceName
            QuestGatheringResourceGrade.RARE -> discipline.rareResourceName
        }
        val lore = item.get(DataComponents.LORE).orEmpty()
        val plain = lore.map(PlainTextComponentSerializer.plainText()::serialize)

        assertEquals(expectedMaterial, item.material())
        assertEquals(amount, item.amount())
        assertEquals(expectedName, plain(item.get(DataComponents.CUSTOM_NAME)))
        assertEquals(TextDecoration.State.TRUE, item.get(DataComponents.CUSTOM_NAME)?.decoration(TextDecoration.BOLD))
        assertEquals(grade.tooltipStyleId, item.get(DataComponents.TOOLTIP_STYLE))
        assertEquals(
            grade == QuestGatheringResourceGrade.RARE,
            item.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE),
        )
        assertTrue(plain.first().startsWith("TIER II • ${grade.rarityLabel}"))
        assertTrue(plain.any { it.contains("採取系統") && it.contains(discipline.displayName) })
        assertTrue(plain.any { it.contains("素材区分") && it.contains(grade.resourceTypeLabel) })
        assertTrue(plain.any { it.contains("内部Tier") && it.contains("T2") })
        assertTrue(plain.any { it.contains("採取素材・${discipline.displayName}") })
        assertFalse(plain.any { it.contains("SHIFT") || it.contains("projects:") })

        val customFontText = lore.flatMap { component -> component.children() }
            .filter { child -> child.style().font() == Key.key("projects", "tooltip_icons") }
            .joinToString("") { child -> plain(child) }
        assertFalse(customFontText.contains(' '), "Custom icon font must never receive a normal space glyph")
    }

    private fun plain(component: net.kyori.adventure.text.Component?): String =
        component?.let(PlainTextComponentSerializer.plainText()::serialize).orEmpty()
}
