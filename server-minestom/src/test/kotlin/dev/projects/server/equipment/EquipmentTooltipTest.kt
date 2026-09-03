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
        assertEquals(listOf("攻撃力", "攻撃速度", "クリティカル率"), model.baseStats.map { it.label })
        assertEquals(listOf("42.8", "1.45", "6%"), model.baseStats.map { it.valueText })
        assertEquals(1, model.mods.size)
        assertEquals("疾風", model.mods.single().displayName)
        assertEquals("II", model.mods.single().rankLabel)
        assertEquals("+12.4% 攻撃速度", model.mods.single().effectText)
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
            .toPresentationItemStack(Material.IRON_SWORD, "双刃", definitions)
        val lore = stack.get(DataComponents.LORE).orEmpty()
        val plain = lore.map(PlainTextComponentSerializer.plainText()::serialize)

        val baseIndex = plain.indexOf("基本性能")
        val modIndex = plain.indexOf("MOD")
        val marketIndex = plain.indexOf("市場価値")
        assertTrue(baseIndex >= 0)
        assertTrue(modIndex > baseIndex)
        assertTrue(marketIndex > modIndex)
        assertTrue(plain.any { it.contains("42.8 攻撃力") })
        assertTrue(plain.any { it.contains("\uE001") && it.contains("42.8 攻撃力") })
        assertTrue(plain.any { it.contains("\uE002") && it.contains("1.45 攻撃速度") })
        assertTrue(plain.any { it.contains("\uE003") && it.contains("6% クリティカル率") })
        assertTrue(plain.any { it.contains("疾風 II") })
        assertTrue(plain.any { it.startsWith("  推定 ") && it.endsWith(" G") })
        assertFalse(plain.any { it.contains("projects:") })
        assertEquals(TextDecoration.State.TRUE, lore.first().decoration(TextDecoration.BOLD))
        assertEquals(TextDecoration.State.TRUE, lore[baseIndex].decoration(TextDecoration.BOLD))
        assertEquals(TextDecoration.State.TRUE, lore[modIndex].decoration(TextDecoration.BOLD))
        assertEquals(TextDecoration.State.TRUE, lore[marketIndex].decoration(TextDecoration.BOLD))
        assertEquals(
            TextDecoration.State.FALSE,
            lore[plain.indexOfFirst { it.contains("42.8 攻撃力") }].decoration(TextDecoration.BOLD),
        )
        assertEquals(
            Key.key("projects", "tooltip_icons"),
            lore[plain.indexOfFirst { it.contains("42.8 攻撃力") }]
                .children()
                .single { it.style().font() == Key.key("projects", "tooltip_icons") }
                .style()
                .font(),
        )
        assertEquals(
            "\uE001",
            PlainTextComponentSerializer.plainText().serialize(
                lore[plain.indexOfFirst { it.contains("42.8 攻撃力") }]
                    .children()
                    .single { it.style().font() == Key.key("projects", "tooltip_icons") },
            ),
        )
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
}
