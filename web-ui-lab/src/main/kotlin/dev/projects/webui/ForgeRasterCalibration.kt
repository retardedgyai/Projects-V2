package dev.projects.webui

import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path

/** Finite forge design fixtures using the same PNG glyphs as the browser canvas.
 *
 * This is a calibration plate, not a live account renderer. Values and armor are
 * baked into each frame; actual UiRenderer sampling still requires a client check.
 */
class ForgeRasterCalibration(manifest: String) {
    constructor(path: Path) : this(Files.readString(path))
    private val root = JsonParser.parseString(manifest).asJsonObject
    private val width = root.get("sceneWidth").asDouble
    private val height = root.get("sceneHeight").asDouble
    private val frames = root.getAsJsonArray("frames").associate { element ->
        val value = element.asJsonObject
        value.get("id").asString to value
    }
    private val sprites = root.getAsJsonObject("sprites").entrySet().associate { (id, element) ->
        val value = element.asJsonObject
        id to UiSprite(value.get("char").asString, value.get("font").asString,
            value.get("width").asInt, value.get("height").asInt)
    }
    val frameIds: Set<String> get() = frames.keys

    init {
        require(root.get("schema").asInt == 2 && root.get("kind").asString == "forge-raster-calibration")
        require(!root.get("nativeEngineCapture").asBoolean)
        require(width == 800.0 && height == 480.0 && frames.isNotEmpty())
        require(sprites.values.all { it.width in 1..256 && it.height in 1..256 })
        require(sprites.values.map { it.char }.distinct().size == sprites.size)
    }

    /** Drop-in input to the existing UiRenderer; it does not execute forge actions. */
    fun scene(frameId: String): UiScene {
        val frame = requireNotNull(frames[frameId]) { "Unknown forge calibration frame: $frameId" }
        val sourceWidth = frame.get("width").asDouble
        val sourceHeight = frame.get("height").asDouble
        val rasterScale = frame.get("rasterScale").asDouble
        require(rasterScale == 2.0)
        val scale = minOf(width / sourceWidth, height / sourceHeight)
        val left = (width - sourceWidth * scale) / 2
        val top = (height - sourceHeight * scale) / 2
        fun box(x: Double, y: Double, w: Double, h: Double) = Box(left + x * scale, top + y * scale, w * scale, h * scale)
        val nodes = frame.getAsJsonArray("tiles").mapIndexed { index, element ->
            val tile = element.asJsonObject
            val sprite = sprites.getValue(tile.get("sprite").asString)
            require(sprite.width == tile.get("w").asInt && sprite.height == tile.get("h").asInt)
            UiNode("calibration-tile-$index", box(tile.get("x").asDouble / rasterScale, tile.get("y").asDouble / rasterScale,
                sprite.width / rasterScale, sprite.height / rasterScale), "", emptyMap(), null, null, true, 1, sprite)
        }.toMutableList()
        frame.getAsJsonArray("hits").forEachIndexed { index, element ->
            val hit = element.asJsonObject
            nodes += UiNode("calibration-hit-$index", box(hit.get("x").asDouble, hit.get("y").asDouble,
                hit.get("w").asDouble, hit.get("h").asDouble), "", emptyMap(),
                "calibration:${hit.get("action").asString}", null, true, 10)
        }
        return UiScene(width, height, nodes)
    }
}

/** Display-only navigation for the isolated lab. No economy/roll/save callback. */
class ForgeRasterCalibrationFlow(
    private val calibration: ForgeRasterCalibration,
    initialFrame: String = "chest",
) : ForgeUiFlow {
    var frameId = initialFrame; private set
    override val muted = true
    // Do not let UiSessions add the live forge's ember animation over calibration pixels.
    override val view = "calibration"
    override val operationActive = false
    init { require(frameId in calibration.frameIds) }
    override fun scene(light: ForgeLightPhase) = calibration.scene(frameId)
    override fun action(action: String): Boolean {
        if (!action.startsWith("calibration:")) return false
        val command = action.removePrefix("calibration:")
        val next = when {
            command.startsWith("select:") -> command.removePrefix("select:") +
                if (frameId.endsWith("-focused")) "-focused" else ""
            command == "catalyst" -> if (frameId.endsWith("-focused")) frameId.removeSuffix("-focused") else "$frameId-focused"
            command == "enhance" -> "confirm-$frameId"
            command == "cancel" || command == "confirm" -> frameId.removePrefix("confirm-")
            else -> return false
        }
        if (next !in calibration.frameIds) return false
        frameId = next
        return true
    }
}
