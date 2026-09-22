package dev.projects.webui

/** Explicit demo actions only; no commands, purchases, saves or real equipment mutation. */
class ForgeDemo {
    var tab = "forge"; private set
    var level = 3; private set
    var ore = 18; private set
    var coins = 1200; private set
    var page = 0; private set
    var delayMs = 0; private set
    var zoom = 1.0; private set
    var status = "試作専用の素材です。本編の所持品は変わりません。"; private set
    val cost get() = 6 + level * 2
    val affordable get() = ore >= cost && coins >= 200 && level < 9
    fun flags() = buildSet {
        add(tab)
        if (affordable) add("affordable")
        if (page == 0) add("first") else add("second")
    }
    fun values() = mapOf(
        "level" to "+$level", "next" to "+${level + 1}", "ore" to "$ore", "cost" to "$cost",
        "coins" to "$coins", "status" to status, "damage" to "${32 + level * 4}",
        "nextDamage" to "${36 + level * 4}", "delay" to "${delayMs}ms", "page" to "${page+1} / 2",
        "button" to if (affordable) "強化する" else if(level >= 9) "試作の上限です" else "素材が足りません",
        "zoom" to "${(zoom*100).toInt()}%",
    )
    fun action(id: String): Boolean = when(id) {
        "tab:forge", "tab:catalog" -> { tab = id.substringAfter(':'); true }
        "forge" -> if (affordable) {
            ore -= cost; coins -= 200; level++; status = "強化成功。攻撃力が4上がりました。"; true
        } else false
        "reset" -> { level=3; ore=18; coins=1200; status="テスト素材を補充しました。"; true }
        "page:next", "page:prev" -> { page=1-page; true }
        "delay" -> { delayMs=when(delayMs){0->50;50->100;100->200;else->0}; true }
        "zoom" -> { zoom=when(zoom){1.0->0.8;0.8->0.65;else->1.0}; true }
        else -> false
    }
}

/** Relative mouse deltas do not depend on the user's FOV. NaN/huge jumps never enter entity metadata. */
class UiPointer(var x: Double = 400.0, var y: Double = 240.0) {
    private var yaw: Float? = null
    private var pitch: Float? = null
    fun reset() { yaw=null; pitch=null }
    fun move(nextYaw: Float, nextPitch: Float, width: Double, height: Double) {
        if(!nextYaw.isFinite() || !nextPitch.isFinite()) return
        val oldYaw=yaw; val oldPitch=pitch
        yaw=nextYaw; pitch=nextPitch
        if(oldYaw==null || oldPitch==null) return
        val dx=((nextYaw-oldYaw+540.0)%360.0)-180.0
        val dy=(nextPitch-oldPitch).toDouble()
        if(kotlin.math.abs(dx)>80 || kotlin.math.abs(dy)>80) return
        x=(x+dx*8.0).coerceIn(0.0,width-0.001)
        y=(y+dy*8.0).coerceIn(0.0,height-0.001)
    }
}
