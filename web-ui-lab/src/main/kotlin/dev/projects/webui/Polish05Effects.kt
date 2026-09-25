package dev.projects.webui

import dev.projects.webui.polish05.Polish05ScreenSpace
import kotlin.math.min
import kotlin.math.roundToInt

/** The approved HTML canvas ember loop, mapped from its 270×191 canvas to the forge stage. */
class Polish05Effects {
    private data class Ember(val id:Int,var x:Double,var y:Double,val vx:Double,val vy:Double,
                             var life:Double,val size:Int,val color:String)
    private val screen=Polish05ScreenSpace(800.0,480.0)
    private val embers=mutableListOf<Ember>()
    private var seed=7251L
    private var nextId=0
    private var lastFrameMs=0L
    private var strikeUntilMs=0L
    private var warmUntilMs=0L

    private fun rng():Double {
        seed=(seed*16807)%2147483647
        return (seed-1).toDouble()/2147483646.0
    }
    fun beginStrike(nowMs:Long=System.currentTimeMillis()) {
        strikeUntilMs=nowMs+720
        warmUntilMs=0
        burst(30)
    }
    fun finish(success:Boolean,nowMs:Long=System.currentTimeMillis()) {
        strikeUntilMs=0
        warmUntilMs=if(success)nowMs+1100 else 0
        if(success)burst(56)
    }
    fun phase(nowMs:Long=System.currentTimeMillis())=when {
        nowMs<warmUntilMs -> ForgeLightPhase.RESULT_WARM
        nowMs<strikeUntilMs -> ForgeLightPhase.STRIKING
        else -> ForgeLightPhase.IDLE
    }
    fun clear() { embers.clear();lastFrameMs=0 }
    private fun burst(count:Int) {
        repeat(count) {
            embers+=Ember(nextId++,135+(rng()-.5)*30,140+rng()*12,
                (rng()-.5)*1.8,-.4-rng()*2.1,20+rng()*55,
                if(rng()>.86)2 else 1,if(rng()>.6)"edcf89" else "b97e4b")
        }
    }
    fun frame(base:UiScene,nowMs:Long=System.currentTimeMillis()):UiScene {
        if(nowMs-lastFrameMs>=45) {
            lastFrameMs=nowMs
            if(rng()>.66)embers+=Ember(nextId++,104+rng()*62,172.0,
                (rng()-.5)*.32,-.22-rng()*.4,35+rng()*90,1,"c58b52")
            embers.removeIf { it.life<=0 }
            embers.forEach { it.x+=it.vx;it.y+=it.vy;it.life-- }
        }
        // CSS gentle-strike moves the sharp sword down by only two source pixels.
        val strikeMs=nowMs-(strikeUntilMs-720)
        val swordOffset=if(nowMs<strikeUntilMs)when {
            strikeMs<0 -> 0.0
            strikeMs<270 -> 2.0*strikeMs/270.0
            strikeMs<390 -> 2.0*(390-strikeMs)/120.0
            else -> 0.0
        } else 0.0
        val baseNodes=if(swordOffset==0.0)base.nodes else base.nodes.map { node ->
            if(node.id=="hero-weapon")node.copy(box=node.box.copy(y=node.box.y+swordOffset*screen.scale)) else node
        }
        val nodes=embers.filter { it.life>0 }.map { ember ->
            val x=367+ember.x*628.0/270.0
            val y=296+ember.y*382.0/191.0
            val p=screen.forward(x,y)
            val w=ember.size*628.0/270.0*screen.scale
            val h=ember.size*382.0/191.0*screen.scale
            val alpha=(min(.75,ember.life/28.0)*255).roundToInt().coerceIn(0,255)
            UiNode("ember-${ember.id}",Box(p.x,p.y,w,h),"",
                mapOf("background-color" to "#${alpha.toString(16).padStart(2,'0')}${ember.color}"),
                null,null,true,11)
        }
        return UiScene(base.width,base.height,baseNodes+nodes)
    }
}
