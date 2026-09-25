package dev.projects.webui

import org.w3c.dom.Element
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

data class Box(val x: Double, val y: Double, val w: Double, val h: Double) {
    fun contains(px: Double, py: Double) = px >= x && py >= y && px < x + w && py < y + h
}
data class UiNode(
    val id: String, val box: Box, val text: String, val style: Map<String, String>,
    val action: String?, val item: String?, val enabled: Boolean, val depth: Int,
    val sprite: UiSprite? = null,
) {
    val fontSize get() = style["font-size"]?.removeSuffix("px")?.toDouble() ?: 14.0
    val color get() = style["color"] ?: "#eee8df"
    val background get() = style["background-color"]
}
data class UiSprite(val char: String, val font: String, val width: Int, val height: Int)
data class UiScene(val width: Double, val height: Double, val nodes: List<UiNode>) {
    fun hit(x: Double, y: Double): UiNode? = nodes.lastOrNull { it.action != null && it.box.contains(x, y) }
}

/** Deliberately small XHTML + CSS subset. No JS, network, external CSS, DTD or entity expansion. */
class UiDocument private constructor(private val root: Element, private val classes: Map<String, Map<String, String>>) {
    fun layout(values: Map<String, String>, flags: Set<String>): UiScene {
        val result = mutableListOf<UiNode>()
        fun styles(e: Element, inherited: Map<String, String>): Map<String, String> = buildMap {
            inherited.filterKeys { it in setOf("color", "font-size", "font-weight", "text-align") }.let(::putAll)
            e.getAttribute("class").split(Regex("\\s+")).filter(String::isNotBlank).forEach {
                putAll(requireNotNull(classes[it]) { "Unknown CSS class: $it" })
            }
            putAll(parseStyle(e.getAttribute("style")))
        }
        fun visible(e: Element): Boolean {
            val condition = e.getAttribute("data-if")
            return condition.isEmpty() || if (condition.startsWith("!")) condition.drop(1) !in flags else condition in flags
        }
        fun expand(text: String) = Regex("\\{\\{([a-zA-Z0-9_-]+)}}").replace(text) {
            requireNotNull(values[it.groupValues[1]]) { "Unknown binding: ${it.groupValues[1]}" }
        }
        fun visit(e: Element, box: Box, inherited: Map<String, String>, depth: Int) {
            require(depth <= 12) { "DOM depth exceeds 12" }
            val css = styles(e, inherited)
            val children = (0 until e.childNodes.length).mapNotNull { e.childNodes.item(it) as? Element }.filter(::visible)
            val text = if (children.isEmpty()) expand(e.textContent.trim()) else ""
            require(text.length <= 160 && '\n' !in text) { "Use separate short text elements, max 160 characters" }
            val id = e.getAttribute("id").ifEmpty { "node-${result.size}" }
            require(result.none { it.id == id }) { "Duplicate id: $id" }
            val action = e.getAttribute("data-action").ifEmpty { null }
            require(action == null || action.matches(Regex("[a-z0-9:-]{1,64}"))) { "Invalid action id" }
            val enabled = e.getAttribute("data-enabled").let { it.isEmpty() || it in flags }
            val item = e.getAttribute("data-item").ifEmpty { null }
            require(item == null || item.matches(Regex("minecraft:[a-z_]+"))) { "Only vanilla item IDs in this lab" }
            require(item == null || net.minestom.server.item.Material.fromKey(item) != null) { "Unknown vanilla item: $item" }
            result += UiNode(id, box, text, css, action, item, enabled, depth)
            require(result.size <= 180) { "UI exceeds 180 elements" }
            if (children.isEmpty()) return
            val padding = px(css["padding"] ?: "0")
            val gap = px(css["gap"] ?: "0")
            val row = css["flex-direction"] == "row"
            val innerW = (box.w - padding * 2).coerceAtLeast(0.0)
            val innerH = (box.h - padding * 2).coerceAtLeast(0.0)
            val primary = if (row) "width" else "height"
            val cross = if (row) "height" else "width"
            val specs = children.map { styles(it, css) }
            val available = (if (row) innerW else innerH) - gap * (children.size - 1)
            val fixed = specs.sumOf { if (it["flex-grow"] == "1") 0.0 else px(requireNotNull(it[primary]) { "Missing $primary on child of $id" }) }
            val growing = specs.count { it["flex-grow"] == "1" }
            require(available >= fixed - 0.01) { "Children overflow $id ($fixed > $available)" }
            var offset = 0.0
            children.zip(specs).forEach { (child, spec) ->
                val mainSize = if (spec["flex-grow"] == "1") (available - fixed) / growing else px(spec.getValue(primary))
                val crossSize = spec[cross]?.let(::px) ?: if (row) innerH else innerW
                require(crossSize <= (if (row) innerH else innerW) + 0.01) { "Cross-axis overflow in $id" }
                val childBox = if (row) Box(box.x + padding + offset, box.y + padding, mainSize, crossSize)
                    else Box(box.x + padding, box.y + padding + offset, crossSize, mainSize)
                visit(child, childBox, css, depth + 1)
                offset += mainSize + gap
            }
        }
        val css = styles(root, emptyMap())
        val w = px(css["width"] ?: "800px")
        val h = px(css["height"] ?: "480px")
        require(w in 320.0..1200.0 && h in 200.0..800.0)
        visit(root, Box(0.0, 0.0, w, h), emptyMap(), 0)
        return UiScene(w, h, result)
    }

    companion object {
        private val allowed = setOf("display", "flex-direction", "flex-grow", "width", "height", "padding", "gap",
            "background-color", "color", "font-size", "font-weight", "text-align")
        fun px(s: String): Double = s.removeSuffix("px").toDouble().also {
            require(it.isFinite() && it in 0.0..1600.0) { "Invalid pixel length: $s" }
        }
        fun parseStyle(s: String): Map<String, String> = s.split(';').filter(String::isNotBlank).associate { declaration ->
            val parts = declaration.split(':', limit = 2).map(String::trim)
            require(parts.size == 2 && parts[0] in allowed) { "Unsupported CSS: $declaration" }
            val (key, value) = parts
            when(key) {
                "display" -> require(value == "flex")
                "flex-direction" -> require(value in setOf("row", "column"))
                "flex-grow" -> require(value == "1")
                "font-weight" -> require(value in setOf("normal", "bold"))
                "text-align" -> require(value in setOf("left", "center", "right"))
                "color", "background-color" -> require(value.matches(Regex("#[0-9a-fA-F]{6}")))
                "font-size" -> require(px(value) in 8.0..40.0)
                else -> px(value)
            }
            key to value
        }
        fun parse(source: String): UiDocument {
            require(source.length <= 96_000) { "UI source too large" }
            val factory = DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
                isXIncludeAware = false
                isExpandEntityReferences = false
            }
            val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(source)))
            val all = doc.getElementsByTagName("*")
            require(all.length <= 200)
            for(i in 0 until all.length) {
                val e = all.item(i) as Element
                require(e.tagName in setOf("html", "head", "title", "meta", "style", "body", "main", "div", "p", "button")) { "Unsupported element: ${e.tagName}" }
                for (a in 0 until e.attributes.length) require(e.attributes.item(a).nodeName in setOf(
                    "lang", "charset", "id", "class", "style", "data-action", "data-item", "data-if", "data-enabled")) { "Unsupported attribute" }
            }
            val rules = mutableMapOf<String, Map<String, String>>()
            val styles = doc.getElementsByTagName("style")
            require(styles.length == 1)
            val css = styles.item(0).textContent.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            val pattern = Regex("\\.([a-zA-Z][a-zA-Z0-9_-]*)\\s*\\{([^{}]*)}")
            require(pattern.replace(css, "").isBlank()) { "Only .class CSS selectors are supported" }
            pattern.findAll(css).forEach { rules[it.groupValues[1]] = parseStyle(it.groupValues[2]) }
            val roots = doc.getElementsByTagName("main")
            require(roots.length == 1) { "Exactly one main element required" }
            return UiDocument(roots.item(0) as Element, rules)
        }
    }
}
