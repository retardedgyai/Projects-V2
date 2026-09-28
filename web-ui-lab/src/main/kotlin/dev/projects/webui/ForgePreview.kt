package dev.projects.webui

import kotlin.math.cos
import kotlin.math.sin

/** Session-local preview pose. Vanilla spectator clicks have no mouse-up packet. */
internal class ForgePreview {
    var rotating = false; private set
    var yaw = 0.0; private set
    var pitch = 0.0; private set
    private var item: String? = null
    private var lastX = 0.0
    private var lastY = 0.0

    fun reset() { rotating=false; yaw=0.0; pitch=0.0 }

    fun sync(scene: UiScene) {
        val next=scene.nodes.singleOrNull { it.id=="hero-weapon" }?.item
        if(next!=item || scene.nodes.none { it.action=="preview:toggle" }) reset()
        item=next
    }

    fun toggle(x: Double,y: Double) {
        if(item==null) return
        if(rotating) move(x,y)
        rotating=!rotating
        lastX=x;lastY=y
    }

    fun move(x: Double,y: Double) {
        if(!rotating || !x.isFinite() || !y.isFinite()) return
        yaw=(yaw+(x-lastX)*1.2)%360.0
        pitch=(pitch+(y-lastY)*1.0).coerceIn(-65.0,65.0)
        lastX=x;lastY=y
    }

    fun decorate(scene: UiScene): UiScene = scene.copy(nodes=scene.nodes.map {
        when(it.id) {
            "hero-weapon" -> if(it.previewIdleBox!=null && !rotating && yaw==0.0 && pitch==0.0)
                it.copy(box=it.previewIdleBox,item=null)
            else it.copy(itemYaw=yaw,itemPitch=pitch)
            else -> it
        }
    })
}

/** Yaw then pitch in the display's local screen-facing coordinate system; xyzw order. */
internal fun previewQuaternion(yaw: Double,pitch: Double): FloatArray {
    val y=Math.toRadians(yaw)*.5
    val p=Math.toRadians(pitch)*.5
    return floatArrayOf((sin(p)*cos(y)).toFloat(),(cos(p)*sin(y)).toFloat(),
        (sin(p)*sin(y)).toFloat(),(cos(p)*cos(y)).toFloat())
}
