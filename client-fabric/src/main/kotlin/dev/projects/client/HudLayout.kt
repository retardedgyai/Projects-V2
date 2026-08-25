package dev.projects.client

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.roundToInt

enum class HudElementId(val label: String) {
    SKILLS("Skills"),
    HP("HP"),
    RESOURCE("Resource"),
    HOTBAR("Hotbar"),
}

enum class HudAnchorX { CENTER, LEFT, RIGHT }
enum class HudAnchorY { BOTTOM, CENTER, TOP }

data class HudElementLayout(
    val anchorX: HudAnchorX,
    val anchorY: HudAnchorY,
    val offsetX: Int,
    val offsetY: Int,
    val width: Int,
    val height: Int,
) {
    fun resolve(screenWidth: Int, screenHeight: Int): HudRect {
        val x = when (anchorX) {
            HudAnchorX.CENTER -> (screenWidth - width) / 2 + offsetX
            HudAnchorX.LEFT -> offsetX
            HudAnchorX.RIGHT -> screenWidth - width - offsetX
        }
        val y = when (anchorY) {
            HudAnchorY.BOTTOM -> screenHeight - height - offsetY
            HudAnchorY.CENTER -> (screenHeight - height) / 2 + offsetY
            HudAnchorY.TOP -> offsetY
        }
        return HudRect(x, y, width, height)
    }

    fun movedTo(x: Int, y: Int, screenWidth: Int, screenHeight: Int): HudElementLayout {
        val rect = HudRect(x, y, width, height).clamped(screenWidth, screenHeight)
        val newX = when (anchorX) {
            HudAnchorX.CENTER -> rect.x - (screenWidth - width) / 2
            HudAnchorX.LEFT -> rect.x
            HudAnchorX.RIGHT -> screenWidth - width - rect.x
        }
        val newY = when (anchorY) {
            HudAnchorY.BOTTOM -> screenHeight - height - rect.y
            HudAnchorY.CENTER -> rect.y - (screenHeight - height) / 2
            HudAnchorY.TOP -> rect.y
        }
        return copy(offsetX = newX, offsetY = newY)
    }

    fun resized(width: Int = this.width, height: Int = this.height): HudElementLayout =
        copy(width = width.coerceAtLeast(MIN_SIZE), height = height.coerceAtLeast(MIN_SIZE))

    fun resizedFor(id: HudElementId, width: Int = this.width, height: Int = this.height): HudElementLayout =
        copy(
            width = width.coerceAtLeast(minimumWidth(id)),
            height = height.coerceAtLeast(MIN_SIZE),
        )

    companion object {
        const val MIN_SIZE = 4
        const val SKILLS_SLOT_COUNT = 4
        const val SKILLS_SLOT_GAP = 4
        const val SKILLS_GAP_TOTAL = SKILLS_SLOT_GAP * (SKILLS_SLOT_COUNT - 1)
        const val SKILLS_MIN_WIDTH = SKILLS_GAP_TOTAL + SKILLS_SLOT_COUNT

        fun minimumWidth(id: HudElementId): Int =
            if (id == HudElementId.SKILLS) SKILLS_MIN_WIDTH else MIN_SIZE

        fun skillsSlotWidth(width: Int): Int =
            ((width - SKILLS_GAP_TOTAL) / SKILLS_SLOT_COUNT).coerceAtLeast(1)
    }
}

data class HudRect(val x: Int, val y: Int, val width: Int, val height: Int) {
    fun contains(mouseX: Double, mouseY: Double): Boolean =
        mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height

    fun clamped(screenWidth: Int, screenHeight: Int): HudRect = copy(
        x = x.coerceIn(0, (screenWidth - width).coerceAtLeast(0)),
        y = y.coerceIn(0, (screenHeight - height).coerceAtLeast(0)),
    )
}

data class HudLayoutConfig(val elements: MutableMap<HudElementId, HudElementLayout>) {
    fun copyLayouts(): HudLayoutConfig = HudLayoutConfig(elements.toMutableMap())

    companion object {
        fun defaults(): HudLayoutConfig = HudLayoutConfig(
            mutableMapOf(
                HudElementId.SKILLS to HudElementLayout(HudAnchorX.CENTER, HudAnchorY.BOTTOM, 0, 84, 94, 22),
                HudElementId.HP to HudElementLayout(HudAnchorX.CENTER, HudAnchorY.BOTTOM, -44, 52, 82, 12),
                HudElementId.RESOURCE to HudElementLayout(HudAnchorX.CENTER, HudAnchorY.BOTTOM, 44, 52, 82, 12),
                HudElementId.HOTBAR to HudElementLayout(HudAnchorX.CENTER, HudAnchorY.BOTTOM, 0, 22, 182, 22),
            ),
        )
    }
}

class HudLayoutStore(private val file: Path) {
    fun load(): HudLayoutConfig {
        if (!Files.exists(file)) return HudLayoutConfig.defaults()
        return runCatching {
            val root = JsonParser.parseString(Files.readString(file)).asJsonObject
            val config = HudLayoutConfig.defaults()
            config.elements.keys.forEach { id ->
                root.getAsJsonObject(id.name.lowercase())?.let { json ->
                    config.elements[id] = HudElementLayout(
                        HudAnchorX.valueOf(json.get("anchorX").asString),
                        HudAnchorY.valueOf(json.get("anchorY").asString),
                        json.get("offsetX").asInt,
                        json.get("offsetY").asInt,
                        json.get("width").asInt,
                        json.get("height").asInt,
                    ).resizedFor(id)
                }
            }
            config
        }.getOrElse { HudLayoutConfig.defaults() }
    }

    fun save(config: HudLayoutConfig) {
        Files.createDirectories(file.parent)
        val root = JsonObject()
        config.elements.forEach { (id, layout) ->
            root.add(id.name.lowercase(), JsonObject().apply {
                addProperty("anchorX", layout.anchorX.name)
                addProperty("anchorY", layout.anchorY.name)
                addProperty("offsetX", layout.offsetX)
                addProperty("offsetY", layout.offsetY)
                addProperty("width", layout.width)
                addProperty("height", layout.height)
            })
        }
        Files.writeString(file, root.toString())
    }
}

fun HudLayoutStore.defaultPath(gameDirectory: Path): Path =
    gameDirectory.resolve("config").resolve("projects").resolve("hud-layout.json")
