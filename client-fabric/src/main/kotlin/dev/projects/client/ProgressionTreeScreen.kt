package dev.projects.client

import dev.projects.protocol.PassiveNodeSpendRequest
import dev.projects.protocol.PassiveNodeSpendResponse
import dev.projects.protocol.PassiveNodeSpendResult
import dev.projects.protocol.ProgressionSnapshot
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ProgressionTreeScreen(
    initialSnapshot: ProgressionSnapshot,
    private val sendMessage: (PassiveNodeSpendRequest) -> Unit,
) : Screen(Component.literal("Global Passive Tree")) {
    private var snapshot = initialSnapshot
    private var selectedNodeId: String? = null
    private var notice: String? = null
    private var purchaseButton: Button? = null

    override fun isPauseScreen(): Boolean = false

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) = Unit

    override fun init() {
        purchaseButton = addRenderableWidget(
            Button.builder(Component.literal("取得")) { purchaseSelected() }
                .bounds(
                    if (isCompact()) panelLeft() + panelWidth() - 124 else panelLeft() + 350,
                    if (isCompact()) panelTop() + panelHeight() - 44 else panelTop() + 236,
                    110,
                    20,
                )
                .build(),
        )
        updatePurchaseButton()
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        val left = panelLeft()
        val top = panelTop()
        val panelWidth = panelWidth()
        val panelHeight = panelHeight()
        graphics.fill(0, 0, width, height, 0x88060A0D.toInt())
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF0131B20.toInt())
        graphics.fill(left, top, left + 2, top + panelHeight, 0xFF638B83.toInt())
        outline(graphics, HudRect(left, top, panelWidth, panelHeight), 0xFF4E625F.toInt())
        graphics.text(font, "GLOBAL PASSIVE TREE", left + 16, top + 12, 0xFFE5F0E9.toInt(), true)
        graphics.text(font, "Kで閉じる / サーバーが状態を確定", left + 16, top + 27, 0xFF91AAA3.toInt(), false)

        val available = snapshot.grantedPassivePoints - snapshot.spentPassivePoints
        graphics.text(
            font,
            "Lv ${snapshot.level}   XP ${snapshot.xp} / ${snapshot.xpRequiredForNextLevel}   Point $available",
            left + 16,
            top + panelHeight - 22,
            if (available > 0) 0xFFE5C878.toInt() else 0xFFAFC0BA.toInt(),
            true,
        )

        val positions = nodePositions(left, top)
        drawConnections(graphics, positions)
        positions.forEach { (nodeId, position) ->
            drawNode(graphics, nodeId, position.x, position.y, nodeId == selectedNodeId)
        }

        val detailLeft = if (isCompact()) left + 14 else left + 330
        val detailTop = if (isCompact()) top + 235 else top + 48
        val detailWidth = if (isCompact()) panelWidth - 28 else panelWidth - 344
        val detailHeight = if (isCompact()) 130 else 172
        graphics.fill(detailLeft, detailTop, detailLeft + detailWidth, detailTop + detailHeight, 0xAA1B272B.toInt())
        outline(graphics, HudRect(detailLeft, detailTop, detailWidth, detailHeight), 0xFF374744.toInt())
        val node = selectedNodeId?.let(::nodeFor)
        if (node == null) {
            graphics.text(font, "Nodeを選択", detailLeft + 12, detailTop + 16, 0xFFB7C8C1.toInt(), true)
            graphics.text(font, "6つの固定Nodeから選べます", detailLeft + 12, detailTop + 36, 0xFF7F9790.toInt(), false)
        } else {
            val state = nodeState(node.id)
            graphics.text(font, node.label, detailLeft + 12, detailTop + 16, nodeColor(state), true)
            drawWrapped(graphics, node.description, detailLeft + 12, detailTop + 36, detailWidth - 24, 0xFFD5E1DB.toInt())
            graphics.text(font, "Cost: ${node.cost}", detailLeft + 12, detailTop + 76, 0xFFE5C878.toInt(), false)
            val prerequisite = if (node.prerequisites.isEmpty()) "なし" else node.prerequisites.joinToString { nodeFor(it)?.label ?: it }
            graphics.text(font, "Prerequisite: $prerequisite", detailLeft + 12, detailTop + 94, 0xFF9BAFA8.toInt(), false)
            graphics.text(font, stateLabel(state), detailLeft + 12, detailTop + 114, nodeColor(state), true)
        }
        notice?.let { text ->
            graphics.text(font, text, left + 16, top + panelHeight - 42, 0xFFE5C878.toInt(), false)
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val positions = nodePositions(panelLeft(), panelTop())
        val selected = positions.entries.firstOrNull { (_, position) ->
            abs(event.x() - position.x) <= NODE_RADIUS && abs(event.y() - position.y) <= NODE_RADIUS
        }?.key
        if (selected != null) {
            selectedNodeId = selected
            notice = null
            updatePurchaseButton()
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_K) {
            onClose()
            return true
        }
        return super.keyPressed(event)
    }

    fun setSnapshot(updated: ProgressionSnapshot) {
        snapshot = updated
        updatePurchaseButton()
    }

    fun setSpendResponse(response: PassiveNodeSpendResponse) {
        notice = when (response.result) {
            PassiveNodeSpendResult.ACCEPTED -> "取得しました"
            PassiveNodeSpendResult.UNKNOWN_NODE -> "不明なNodeです"
            PassiveNodeSpendResult.ALREADY_ACQUIRED -> "取得済みです"
            PassiveNodeSpendResult.INSUFFICIENT_POINTS -> "Pointが足りません"
            PassiveNodeSpendResult.MISSING_PREREQUISITE -> "Prerequisiteが必要です"
            PassiveNodeSpendResult.STALE_REVISION -> "状態が更新されました"
            PassiveNodeSpendResult.PERSISTENCE_FAILURE -> "保存に失敗しました"
        }
        updatePurchaseButton()
    }

    private fun purchaseSelected() {
        val nodeId = selectedNodeId ?: return
        if (nodeState(nodeId) != NodeState.AVAILABLE) return
        sendMessage(PassiveNodeSpendRequest(snapshot.revision, nodeId))
        purchaseButton?.active = false
        notice = "サーバーで確認中..."
    }

    private fun updatePurchaseButton() {
        purchaseButton?.active = selectedNodeId?.let { nodeState(it) == NodeState.AVAILABLE } == true
    }

    private fun nodeFor(nodeId: String): ClientNode? = CLIENT_NODES.firstOrNull { it.id == nodeId }

    private fun nodeState(nodeId: String): NodeState {
        if (nodeId in snapshot.allocatedPassiveNodeIds) return NodeState.ACQUIRED
        val node = nodeFor(nodeId) ?: return NodeState.LOCKED
        val available = snapshot.grantedPassivePoints - snapshot.spentPassivePoints
        return if (available >= node.cost && node.prerequisites.all(snapshot.allocatedPassiveNodeIds::contains)) {
            NodeState.AVAILABLE
        } else {
            NodeState.LOCKED
        }
    }

    private fun drawConnections(graphics: GuiGraphicsExtractor, positions: Map<String, NodePosition>) {
        CLIENT_NODES.forEach { node ->
            node.prerequisites.forEach { prerequisite ->
                val from = positions[prerequisite] ?: return@forEach
                val to = positions[node.id] ?: return@forEach
                drawLine(graphics, from.x, from.y, to.x, to.y, 0xFF40524E.toInt())
            }
        }
    }

    private fun drawNode(graphics: GuiGraphicsExtractor, nodeId: String, x: Int, y: Int, selected: Boolean) {
        val state = nodeState(nodeId)
        val color = nodeColor(state)
        graphics.fill(x - NODE_RADIUS, y - NODE_RADIUS, x + NODE_RADIUS + 1, y + NODE_RADIUS + 1, 0xFF10171A.toInt())
        graphics.fill(x - NODE_RADIUS + 2, y - NODE_RADIUS + 2, x + NODE_RADIUS - 1, y + NODE_RADIUS - 1, color)
        outline(graphics, HudRect(x - NODE_RADIUS, y - NODE_RADIUS, NODE_RADIUS * 2 + 1, NODE_RADIUS * 2 + 1), if (selected) 0xFFFFFFFF.toInt() else color)
        val glyph = nodeFor(nodeId)?.label?.firstOrNull()?.toString() ?: "?"
        graphics.text(font, glyph, x - font.width(glyph) / 2, y - 4, 0xFF10171A.toInt(), true)
        graphics.text(font, nodeFor(nodeId)?.label ?: nodeId, x - font.width(nodeFor(nodeId)?.label ?: nodeId) / 2, y + NODE_RADIUS + 4, color, false)
    }

    private fun drawWrapped(graphics: GuiGraphicsExtractor, text: String, x: Int, y: Int, maxWidth: Int, color: Int) {
        var line = ""
        var lineY = y
        text.forEach { character ->
            val next = line + character
            if (line.isNotEmpty() && font.width(next) > maxWidth) {
                graphics.text(font, line, x, lineY, color, false)
                line = character.toString()
                lineY += 12
            } else {
                line = next
            }
        }
        if (line.isNotEmpty()) graphics.text(font, line, x, lineY, color, false)
    }

    private fun drawLine(graphics: GuiGraphicsExtractor, startX: Int, startY: Int, endX: Int, endY: Int, color: Int) {
        val steps = max(abs(endX - startX), abs(endY - startY)).coerceAtLeast(1)
        repeat(steps + 1) { step ->
            val progress = step.toDouble() / steps
            val x = (startX + (endX - startX) * progress).toInt()
            val y = (startY + (endY - startY) * progress).toInt()
            graphics.fill(x, y, x + 2, y + 2, color)
        }
    }

    private fun outline(graphics: GuiGraphicsExtractor, rect: HudRect, color: Int) {
        graphics.fill(rect.x, rect.y, rect.x + rect.width, rect.y + 1, color)
        graphics.fill(rect.x, rect.y + rect.height - 1, rect.x + rect.width, rect.y + rect.height, color)
        graphics.fill(rect.x, rect.y, rect.x + 1, rect.y + rect.height, color)
        graphics.fill(rect.x + rect.width - 1, rect.y, rect.x + rect.width, rect.y + rect.height, color)
    }

    private fun nodeColor(state: NodeState): Int = when (state) {
        NodeState.ACQUIRED -> 0xFF7FB59B.toInt()
        NodeState.AVAILABLE -> 0xFFE5C878.toInt()
        NodeState.LOCKED -> 0xFF586662.toInt()
    }

    private fun stateLabel(state: NodeState): String = when (state) {
        NodeState.ACQUIRED -> "ACQUIRED"
        NodeState.AVAILABLE -> "AVAILABLE"
        NodeState.LOCKED -> "LOCKED"
    }

    private fun panelWidth(): Int = min(560, (width - 16).coerceAtLeast(120))
    private fun panelHeight(): Int = min(if (isCompact()) 430 else 320, (height - 16).coerceAtLeast(120))
    private fun isCompact(): Boolean = panelWidth() < 500
    private fun panelLeft(): Int = (width - panelWidth()) / 2
    private fun panelTop(): Int = (height - panelHeight()) / 2

    private fun nodePositions(left: Int, top: Int): Map<String, NodePosition> = mapOf(
        "projects:passive/force" to NodePosition(left + 112, top + 174),
        "projects:passive/overpower" to NodePosition(left + 62, top + 124),
        "projects:passive/tempo" to NodePosition(left + 232, top + 174),
        "projects:passive/flow" to NodePosition(left + 282, top + 124),
        "projects:passive/vitality" to NodePosition(left + 172, top + 124),
        "projects:passive/guard" to NodePosition(left + 172, top + 74),
    )

    private data class NodePosition(val x: Int, val y: Int)
    private enum class NodeState { ACQUIRED, AVAILABLE, LOCKED }

    private companion object {
        const val NODE_RADIUS = 14
        val CLIENT_NODES = listOf(
            ClientNode("projects:passive/force", "Force", "通常攻撃と直接スキルのダメージ +15%"),
            ClientNode("projects:passive/overpower", "Overpower", "通常攻撃と直接スキルのダメージをさらに +15%", setOf("projects:passive/force")),
            ClientNode("projects:passive/tempo", "Tempo", "通常攻撃速度 +15%"),
            ClientNode("projects:passive/flow", "Flow", "S1 / S2 / S3のクールダウン回復 +20%", setOf("projects:passive/tempo")),
            ClientNode("projects:passive/vitality", "Vitality", "最大HP +4"),
            ClientNode("projects:passive/guard", "Guard", "PvE被ダメージ -10%", setOf("projects:passive/vitality")),
        )
    }
}

private data class ClientNode(
    val id: String,
    val label: String,
    val description: String,
    val prerequisites: Set<String> = emptySet(),
    val cost: Int = 1,
)
