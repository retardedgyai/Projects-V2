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
    fun everyLineIsExactlyTheCanvasWidthSoTheClientLeavesItInPlace() {
        val canvas = DialogCanvas(metrics, metrics.canvasWidth.toDouble(), metrics.lines)
        canvas.sprite("page_self", 0.0, 0)
        canvas.text("b14", 200.0, 84, "Steve_42", NamedTextColor.WHITE)
        canvas.text("d7", 136.0, 43, "Lv 17", NamedTextColor.WHITE, DialogCanvas.Align.RIGHT)
        repeat(20) { canvas.sprite("bar_combat", 32.0 + it * 2, 49) }
        canvas.sprite("ring_gather_4", 206.0, 120)
        canvas.text("m7", 331.0, 46, "冒険 Lv 17 / 70", NamedTextColor.WHITE)
        val lines = split(canvas.build())
        assertEquals(metrics.lines, lines.size)
        lines.forEachIndexed { i, line -> assertEquals(metrics.canvasWidth.toDouble(), width(line), 0.26, "line $i") }
    }

    @Test
    fun theTitleEndsAtItsFixedWidthSoTheWarningMarkLandsAtTheRight() {
        val canvas = DialogCanvas(metrics, metrics.titleWidth.toDouble(), 1, metrics.titleTop)
        canvas.sprite("band", -metrics.titleX.toDouble(), 0)
        canvas.text("d9", 412.0, 20, "48,300", NamedTextColor.WHITE, DialogCanvas.Align.RIGHT)
        assertEquals(metrics.titleWidth.toDouble(), width(flatten(canvas.buildLine())), 0.26)
    }

    @Test
    fun everyWordTheMenuCanShowHasAGlyph() {
        val words = CoreClass.entries.map { it.displayName } + QuestGatheringDiscipline.entries.map { it.displayName } +
            quoted(File("src/main/kotlin/dev/projects/server/coreloop/PlayerMenuDialog.kt").readText()) +
            quoted(File("src/main/kotlin/dev/projects/server/coreloop/CoreJourney.kt").readText())
        val missing = words.flatMap { word -> word.codePoints().toArray().filter { it >= 0x80 && it !in metrics.styles.getValue("m7").advances } }
            .map { String(Character.toChars(it)) }.toSortedSet()
        assertTrue(missing.isEmpty(), "rerun scripts/build_menu_dialog_assets.py; missing: $missing")
    }

    @Test
    fun everySpriteThePageUsesWasBakedAtItsPosition() {
        val names = listOf("page_self", "band", "row_sel", "ring_sel", "hit_row", "hit_edit", "hit_primary") +
            (1..4).map { "hit_tab$it" } + CoreClass.entries.map { "emblem_${it.name.lowercase()}" } +
            listOf("combat", "gather", "smith", "magic").flatMap { id -> listOf("bar_$id") + (0..10).map { "ring_${id}_$it" } }
        names.forEach { assertTrue(it in metrics.sprites, "sprite $it") }
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

    private fun width(leaves: List<Component>): Double = leaves.sumOf { leaf ->
        val text = (leaf as? TextComponent)?.content() ?: return@sumOf 8.0
        val font = leaf.font()?.asString() ?: return@sumOf 0.0
        val codePoints = text.codePoints().toArray()
        when {
            font.endsWith("menu_space") -> codePoints.sumOf { cp -> metrics.space.entries.first { it.value == cp }.key }
            font.endsWith("menu_art") -> codePoints.sumOf { cp -> metrics.sprites.values.first { cp in it.chars.values }.advance.toDouble() }
            else -> {
                val style = metrics.styles.values.first { font.startsWith(it.font) }
                codePoints.sumOf { style.advances.getValue(it) }
            }
        }
    }
}
