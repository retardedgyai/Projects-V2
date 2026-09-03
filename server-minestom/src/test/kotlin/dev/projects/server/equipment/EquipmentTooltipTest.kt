package dev.projects.server.equipment

import dev.projects.server.mod.AttackTag
import dev.projects.server.mod.ModDefinition
import dev.projects.server.mod.ModEntry
import dev.projects.server.mod.ModRank
import dev.projects.server.mod.ModStackingLayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponents
import net.minestom.server.item.Material

class EquipmentTooltipTest {
    private val definition = ModDefinition(
        modId = "projects:gale",
        rank = ModRank.RANK_2,
        allowedSlots = setOf(EquipmentSlot.WEAPON),
        requiredTags = setOf(AttackTag.MELEE),
        excludedTags = emptySet(),
        statId = "projects:attack-speed",
        minimumValue = 10.0,
        maximumValue = 15.0,
        stackingLayer = ModStackingLayer.INCREASED,
        definitionRevision = 1,
    )
    private val definitions = mapOf(definition.modId to definition)

    @Test
    fun `each rarity selects its authored tooltip style`() {
        MinecraftServer.init(Auth.Offline())
        val expected = mapOf(
            EquipmentRarity.COMMON to "projects:item_common",
            EquipmentRarity.UNCOMMON to "projects:item_uncommon",
            EquipmentRarity.RARE to "projects:item_rare",
            EquipmentRarity.EPIC to "projects:item_epic",
        )

        expected.forEach { (rarity, styleId) ->
            val stack = equipment(rarity).toPresentationItemStack(Material.IRON_SWORD, "双刃", definitions)
            assertEquals(styleId, stack.get(DataComponents.TOOLTIP_STYLE))
        }
    }

    @Test
    fun `tooltip model separates tier rarity base stats and valid mods`() {
        val model = equipment(EquipmentRarity.RARE).toTooltipModel(definitions)

        assertEquals("TIER II", model.tierLabel)
        assertEquals("RARE", model.rarityLabel)
        assertEquals(3, model.modCapacity)
        assertEquals("武器・武器枠", model.equipmentTypeLabel)
        assertEquals(listOf("攻撃力", "攻撃速度", "クリティカル率"), model.baseStats.map { it.label })
        assertEquals(listOf("42.8", "1.45", "6%"), model.baseStats.map { it.valueText })
        assertEquals(1, model.mods.size)
        assertEquals("疾風", model.mods.single().displayName)
        assertEquals("II", model.mods.single().rankLabel)
        assertEquals("+12.4% 攻撃速度", model.mods.single().effectText)
        assertEquals(0.48, model.mods.single().rollQuality, 0.000_001)
    }

    @Test
    fun `invalid mod is not presented or valued as a normal mod`() {
        val invalidEntry = ModEntry("projects:gale", ModRank.RANK_2, 99.0, 0, definitionRevision = 1)
        val invalid = equipment(EquipmentRarity.COMMON, invalidEntry)
        val withoutMod = equipment(EquipmentRarity.COMMON, null)

        assertTrue(invalid.toTooltipModel(definitions).mods.isEmpty())
        assertEquals(
            EquipmentMarketValuePolicy.estimate(withoutMod, definitions),
            EquipmentMarketValuePolicy.estimate(invalid, definitions),
        )
    }

    @Test
    fun `lore keeps base stats mods and estimated market value in distinct sections`() {
        MinecraftServer.init(Auth.Offline())
        val stack = equipment(EquipmentRarity.EPIC)
            .toPresentationItemStack(Material.IRON_SWORD, "双刃", definitions, "双剣・近接武器")
        val lore = stack.get(DataComponents.LORE).orEmpty()
        val plain = lore.map(PlainTextComponentSerializer.plainText()::serialize)

        val baseIndex = plain.indexOf("基本性能")
        val modIndex = plain.indexOfFirst { it.startsWith("MOD") }
        val marketIndex = plain.indexOf("市場価値")
        assertTrue(baseIndex >= 0)
        assertTrue(modIndex > baseIndex)
        assertTrue(marketIndex > modIndex)
        val attackIndex = plain.indexOfFirst { it.contains("攻撃力") }
        val speedIndex = plain.indexOfFirst { it.contains("攻撃速度") }
        val criticalIndex = plain.indexOfFirst { it.contains("クリティカル率") }
        assertTrue(plain[attackIndex].contains("\uE001"))
        assertTrue(plain[speedIndex].contains("\uE002"))
        assertTrue(plain[criticalIndex].contains("\uE003"))
        assertTrue(plain[attackIndex].indexOf("攻撃力") < plain[attackIndex].indexOf("42.8"))
        assertTrue(plain[speedIndex].indexOf("攻撃速度") < plain[speedIndex].indexOf("1.45"))
        assertTrue(plain[criticalIndex].indexOf("クリティカル率") < plain[criticalIndex].indexOf("6%"))
        assertTrue(plain[modIndex].contains("◆") && plain[modIndex].count { it == '◇' } == 3)
        assertTrue(plain.any { it.contains("\uE009") && it.contains("疾風 II") })
        assertTrue(plain.any { it.contains("└") && it.contains("+12.4%") && it.contains("攻撃速度") })
        assertTrue(plain.any { it.contains("\uE008") && it.contains("推定") && it.endsWith(" G") })
        assertTrue(plain.any { it.contains("双剣・近接武器") })
        assertFalse(plain.any { it.contains("SHIFT") || it.contains("詳細情報") || it.contains("内部Tier") })
        assertFalse(plain.any { it.contains("projects:") })
        assertEquals(TextDecoration.State.TRUE, lore.first().decoration(TextDecoration.BOLD))
        assertEquals(TextDecoration.State.TRUE, lore[baseIndex].decoration(TextDecoration.BOLD))
        assertEquals(TextDecoration.State.TRUE, lore[modIndex].children().first().decoration(TextDecoration.BOLD))
        assertEquals(TextDecoration.State.TRUE, lore[marketIndex].decoration(TextDecoration.BOLD))
        assertEquals(
            TextDecoration.State.FALSE,
            lore[attackIndex].decoration(TextDecoration.BOLD),
        )
        assertEquals(
            Key.key("projects", "tooltip_icons"),
            lore[attackIndex]
                .children()
                .single {
                    it.style().font() == Key.key("projects", "tooltip_icons") &&
                        PlainTextComponentSerializer.plainText().serialize(it) == "\uE001"
                }
                .style()
                .font(),
        )
        assertEquals(
            "\uE001",
            PlainTextComponentSerializer.plainText().serialize(
                lore[attackIndex]
                    .children()
                    .single {
                        it.style().font() == Key.key("projects", "tooltip_icons") &&
                            PlainTextComponentSerializer.plainText().serialize(it) == "\uE001"
                    },
            ),
        )
        assertEquals(
            TextColor.color(0xB4B9BA),
            lore[attackIndex].children().single {
                PlainTextComponentSerializer.plainText().serialize(it).contains("攻撃力")
            }.style().color(),
        )
        assertEquals(
            TextColor.color(0xE39A91),
            lore[attackIndex].children().single {
                PlainTextComponentSerializer.plainText().serialize(it) == "42.8"
            }.style().color(),
        )
        val customFontText = lore.flatMap { component -> component.children() }
            .filter { child -> child.style().font() == Key.key("projects", "tooltip_icons") }
            .joinToString("") { child -> PlainTextComponentSerializer.plainText().serialize(child) }
        assertFalse(customFontText.contains(' '), "Custom icon font must never receive a normal space glyph")
        assertEquals(
            TextDecoration.State.TRUE,
            stack.get(DataComponents.CUSTOM_NAME)?.decoration(TextDecoration.BOLD),
        )
    }

    @Test
    fun `estimated market value is deterministic finite and non negative`() {
        val item = EquipmentItem(
            itemId = "projects:extreme-roll",
            category = EquipmentCategory.WEAPON,
            slot = EquipmentSlot.WEAPON,
            tier = EquipmentTier.T3,
            itemLevel = 45,
            rarity = EquipmentRarity.EPIC,
            baseStatRolls = listOf(BaseStatRoll("projects:physical-attack", Double.MAX_VALUE)),
            modSlots = List(EquipmentRarity.EPIC.modCapacity, EquipmentModSlot::empty),
        )

        val first = EquipmentMarketValuePolicy.estimate(item, emptyMap())
        assertEquals(first, EquipmentMarketValuePolicy.estimate(item, emptyMap()))
        assertTrue(first >= 0)
        assertTrue(first.toDouble().isFinite())
    }

    @Test
    fun `mod roll quality colors progress from muted to gold`() {
        val expectedColors = listOf(
            10.0 to TextColor.color(0x9CA3A5),
            12.4 to TextColor.color(0x78BBA8),
            14.0 to TextColor.color(0x69BDD0),
            15.0 to TextColor.color(0xD8B962),
        )

        expectedColors.forEach { (rolledValue, expectedColor) ->
            val entry = ModEntry("projects:gale", ModRank.RANK_2, rolledValue, 0, definitionRevision = 1)
            val lore = equipment(EquipmentRarity.RARE, entry).toTooltipModel(definitions).toLore()
            val effect = lore.first { component -> plain(component).contains("└") }
            val valueText = if (rolledValue % 1.0 == 0.0) {
                "+${rolledValue.toInt()}%"
            } else {
                "+$rolledValue%"
            }
            assertEquals(
                expectedColor,
                effect.children().single { child -> plain(child) == valueText }.style().color(),
            )
        }
    }

    private fun equipment(
        rarity: EquipmentRarity,
        entry: ModEntry? = ModEntry("projects:gale", ModRank.RANK_2, 12.4, 0, definitionRevision = 1),
    ): EquipmentItem = EquipmentItem(
        itemId = "projects:tooltip-test-${rarity.name.lowercase()}",
        category = EquipmentCategory.WEAPON,
        slot = EquipmentSlot.WEAPON,
        tier = EquipmentTier.T2,
        itemLevel = 24,
        rarity = rarity,
        baseStatRolls = listOf(
            BaseStatRoll("projects:physical-attack", 42.8),
            BaseStatRoll("projects:attack-speed", 1.45),
            BaseStatRoll("projects:critical-chance", 6.0),
        ),
        modSlots = List(rarity.modCapacity) { index ->
            if (index == 0 && entry != null) EquipmentModSlot(index, entry) else EquipmentModSlot.empty(index)
        },
    )

    private fun plain(component: net.kyori.adventure.text.Component): String =
        PlainTextComponentSerializer.plainText().serialize(component)
}
