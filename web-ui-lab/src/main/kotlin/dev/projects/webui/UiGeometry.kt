package dev.projects.webui

import net.minestom.server.coordinate.Vec

/** Geometry derived from vanilla 26.2 DisplayRenderer.TextDisplayRenderer. */
class UiGeometry(private val zoom: Double=1.0) {
    data class Transform(val translation: Vec,val scale: Vec)
    fun unit(depth: Double)=0.008*zoom*(DISTANCE-depth)/DISTANCE
    fun point(x: Double,y: Double,depth: Double)=Vec((x-400)*unit(depth),(240-y)*unit(depth),depth)
    fun panel(b: Box,depth: Double): Transform {
        val u=unit(depth)
        // Single-space bounds: x=[-.05,.075], y=[0,.25]. Origin is at the bottom, NOT the centre.
        return Transform(point(b.x+b.w/2,b.y+b.h/2,depth).add(-b.w*u*.1,-b.h*u*.5,0.0),Vec(b.w*u*8,b.h*u*4,1.0))
    }
    companion object {
        const val DISTANCE=2.2
        /** Default ASCII advances; Japanese fallback may vary with the user's font pack. */
        fun textAdvance(text: String,bold: Boolean)=text.codePoints().toArray().sumOf { cp ->
            val width=when(cp.toChar()) {
                ' ' -> 4; '!', '.', ',', ':', ';', 'i', '|', '\'' -> 2
                'l', '`' -> 3; 'I', 't', '[', ']', '"' -> 4
                'f', 'k', '(', ')', '{', '}', '<', '>' -> 5
                '@', '~' -> 7
                else -> if(cp>255) 8 else 6
            }
            width+if(bold && cp!=32) 1 else 0
        }.toDouble()
    }
}
