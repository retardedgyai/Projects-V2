package dev.projects.webui.polish05

/** Uniform mapping of the approved 1440x920 canvas into an existing lab canvas.
 * Never stretch it into 800x480. Use inverse() for pointer/hit coordinates too.
 */
class Polish05ScreenSpace(val width: Double, val height: Double) {
    init { require(width.isFinite() && height.isFinite() && width>0 && height>0) }
    val scale = minOf(width/1440.0,height/920.0)
    val left = (width-1440.0*scale)/2
    val top = (height-920.0*scale)/2
    data class Point(val x: Double, val y: Double)
    data class Rect(val x: Double,val y: Double,val w: Double,val h: Double) {
        fun contains(p: Point) = p.x>=x && p.y>=y && p.x<x+w && p.y<y+h
    }
    fun forward(x: Double,y: Double) = Point(left+x*scale,top+y*scale)
    fun inverse(x: Double,y: Double): Point? {
        if(!x.isFinite() || !y.isFinite())return null
        val p=Point((x-left)/scale,(y-top)/scale)
        return p.takeIf { it.x>=0 && it.y>=0 && it.x<1440 && it.y<920 }
    }
    fun rect(r: Rect) = Rect(left+r.x*scale,top+r.y*scale,r.w*scale,r.h*scale)
}
