package dev.projects.server.coreloop.ui

import dev.projects.server.coreloop.CoreClass
import dev.projects.server.coreloop.PlayerMenuDialog
import dev.projects.server.questmap.QuestGatheringDiscipline
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DialogCanvasTest {
    private val metrics = assertNotNull(PlayerMenuDialog.metrics(), "polish05/menu-metrics.json is missing; run scripts/build_menu_dialog_assets.py")

    @Test
    fun everyLineIsExactlyTheCanvasWidthAtEveryGuiScaleSoTheClientLeavesItInPlace() {
        for ((scale, size) in metrics.sizes) {
            val canvas = DialogCanvas(size, metrics.space, size.canvasWidth.toDouble(), size.lines)
            canvas.background("page_self", 0.0, 0.0)
            canvas.text("dot16", 200.0, 84.0, "Steve_42", NamedTextColor.WHITE)
            canvas.text("d7", 136.0, 43.0, "Lv 17", NamedTextColor.WHITE, DialogCanvas.Align.RIGHT)
            val bar = size.layout.getAsJsonArray("pathBar")
            repeat(20) { canvas.sprite("bar_combat", bar[0].asDouble + it * 2, bar[1].asDouble) }
            val row = size.layout.getAsJsonArray("rows")[1].asJsonArray
            canvas.sprite("row_on", row[0].asDouble, row[1].asDouble)
            canvas.sprite("slot_sel", 39.0, 122.0)
            repeat(7) { canvas.sprite("dia_on", 287.0 + it * 10, 125.0) }
            canvas.text("m7", 331.0, 46.0, "冒険 Lv 17 / 70", NamedTextColor.WHITE)
            val lines = split(canvas.build())
            assertEquals(size.lines, lines.size, "GUI scale $scale")
            lines.forEachIndexed { i, line -> assertEquals(size.canvasWidth.toDouble(), width(size, line), 0.26, "GUI scale $scale, line $i") }
        }
    }

    @Test
    fun theTitleEndsAtItsFixedWidthSoTheWarningMarkLandsAtTheRight() {
        for ((scale, size) in metrics.sizes) {
            val canvas = DialogCanvas(size, metrics.space, size.titleWidth.toDouble(), 1, size.titleTop, 1.0, metrics.forScale(4).styles)
            canvas.background("band", -size.titleX.toDouble(), 0.0)
            val wallet = size.layout.getAsJsonArray("wallet")
            canvas.text("d9", wallet[0].asDouble - size.titleX, wallet[1].asDouble, "48,300", NamedTextColor.WHITE, DialogCanvas.Align.RIGHT)
            assertEquals(size.titleWidth.toDouble(), width(size, flatten(canvas.buildLine()), metrics.forScale(4)), 0.26, "GUI scale $scale")
        }
    }

    @Test
    fun eachGuiScaleGetsItsOwnSizeAndOthersTheNearest() {
        assertEquals(setOf(2, 3, 4), metrics.sizes.keys)
        assertEquals(2.0, metrics.forScale(1).unit)
        assertEquals(1.0, metrics.forScale(6).unit)
        metrics.sizes.forEach { (scale, size) -> assertTrue(size.canvasWidth + 9 <= 480 * 4 / scale, "GUI scale $scale fits a 1080p screen") }
    }

    @Test
    fun everyWordTheMenuCanShowHasAGlyph() {
        val words = CoreClass.entries.map { it.displayName } + QuestGatheringDiscipline.entries.map { it.displayName } +
            quoted(File("src/main/kotlin/dev/projects/server/coreloop/PlayerMenuDialog.kt").readText()) +
            quoted(File("src/main/kotlin/dev/projects/server/coreloop/CoreJourney.kt").readText()) +
            CoreClass.entries.flatMap { job -> dev.projects.server.coreloop.CoreSkillCatalog.skills(job).flatMap { listOf(it.name, it.description) } }
        val missing = words.flatMap { word -> word.codePoints().toArray().filter { it >= 0x80 && it !in metrics.forScale(4).styles.getValue("m7").advances } }
            .map { String(Character.toChars(it)) }.toSortedSet()
        assertTrue(missing.isEmpty(), "rerun scripts/build_menu_dialog_assets.py; missing: $missing")
    }

    @Test
    fun everySpriteThePageUsesWasBakedAtItsPosition() {
        val names = listOf("page_self", "page_skills", "band", "slot_sel", "slot_dim", "slot_x", "key_tri", "dia_on", "dia_gear", "row_on",
            "bar_fill", "ms_now", "hit_slot", "hit_row", "hit_act", "hit_go", "hit_edit", "hit_reset") + (0..3).map { "hit_tab$it" } +
            CoreClass.entries.flatMap { listOf("emblem64_${it.name.lowercase()}", "emblem24_${it.name.lowercase()}") } +
            listOf("combat", "gather", "smith", "magic").map { "bar_$it" }
        val skillPage = CoreClass.entries.flatMap { job -> dev.projects.server.coreloop.CoreSkillCatalog.skills(job).flatMap { listOf("sk20_${it.icon}", "skb_${it.icon}") } }
        metrics.sizes.forEach { (scale, size) -> (names + skillPage).forEach { assertTrue(it in size.sprites, "GUI scale $scale: sprite $it") } }
    }

    private fun quoted(source: String) = Regex("\"([^\"\\\\$]*)\"").findAll(source).map { it.groupValues[1] }.toList()

    /** Leaves of the component, in order. */
    private fun flatten(component: Component): List<Component> = listOf(component.children(emptyList())) + component.children().flatMap(::flatten)

    private fun split(component: Component): List<List<Component>> {
        val lines = mutableListOf(mutableListOf<Component>())
        for (leaf in flatten(component)) {
            if (leaf is TextComponent && leaf.content() == "\n") lines += mutableListOf<Component>() else lines.last() += leaf
        }
        return lines
    }

    private fun width(size: DialogCanvas.Size, leaves: List<Component>, text: DialogCanvas.Size = size): Double = leaves.sumOf { leaf ->
        val content = (leaf as? TextComponent)?.content() ?: return@sumOf 8.0
        val font = leaf.font()?.asString() ?: return@sumOf 0.0
        val codePoints = content.codePoints().toArray()
        when {
            font.endsWith("menu_space") -> codePoints.sumOf { cp -> metrics.space.entries.first { it.value == cp }.key }
            font == size.art.asString() -> codePoints.sumOf { cp -> size.sprites.values.first { cp in it.chars.values }.advance.toDouble() }
            else -> {
                val style = text.styles.values.first { font.startsWith(it.font) }
                codePoints.sumOf { style.advances.getValue(it) }
            }
        }
    }
}
