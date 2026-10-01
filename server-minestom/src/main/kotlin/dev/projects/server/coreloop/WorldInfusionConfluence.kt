package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** Optional isolated variant. Supplemental supply is an input, NOT an item, currency or saved resource. */
internal object WorldInfusionConfluence {
    data class Drive(val supply:Double) {
        init { require(supply.isFinite() && supply in 0.0..1.0) }
        val period get()=(48-32*supply).roundToInt()
        val travelTicks get()=(42-28*supply).roundToInt()
        val tuftStep get()=if(supply>=.5)1 else 2
        val rotationSpeed get()=1.5+3*supply
        val channelTicks get()=(36-20*supply).roundToInt()
    }
    /** One atomic wave: one unit of EVERY still-required aspect; no partial drain on shortage. */
    fun tick(s:WorldInfusionState):Pair<WorldInfusionState,List<InfusionPulse>> {
        if(s.paused || s.phase in setOf(InfusionPhase.READY,InfusionPhase.COMPLETE))return s to emptyList()
        if(s.phase!=InfusionPhase.ESSENTIA) {
            val (next,pulse)=WorldInfusionRules.tick(s);return next to listOfNotNull(pulse)
        }
        val needs=WorldInfusionRules.cost.keys.filter { s.supplied.getOrDefault(it,0)<WorldInfusionRules.cost.getValue(it) }
        if(needs.isEmpty())return s.copy(phase=InfusionPhase.INGREDIENTS) to emptyList()
        val jars=mutableMapOf<InfusionAspect,InfusionJar>()
        for(a in needs)if(s.reservoir.getOrDefault(a,0)==0) {
            val jar=s.jars.firstOrNull { it.aspect==a && it.amount>0 && it.cell?.nearby(requireNotNull(s.matrix),WorldInfusionRules.JAR_RADIUS)==true }
                ?: return s.copy(paused=true) to emptyList()
            jars[a]=jar
        }
        val filled=s.supplied.toMutableMap();val reservoir=s.reservoir.toMutableMap()
        val pulses=needs.map { a->
            filled[a]=filled.getOrDefault(a,0)+1
            if(a !in jars) {
                val left=reservoir.getValue(a)-1;if(left==0)reservoir.remove(a) else reservoir[a]=left
            }
            InfusionPulse(jar=jars[a]?.id,aspect=a)
        }
        val drained=jars.values.map { it.id }.toSet()
        return s.copy(supplied=filled,reservoir=reservoir,
            jars=s.jars.map { if(it.id in drained)it.copy(amount=it.amount-1) else it },
            phase=if(filled==WorldInfusionRules.cost)InfusionPhase.INGREDIENTS else InfusionPhase.ESSENTIA) to pulses
    }
    /** Cosmetic clock; only accepted durable transactions advance it. Reload restarts visual channel, not costs. */
    class Clock {
        private var lastOperation:Long?=null
        private var armedAt:Long?=null
        var channelAt:Long?=null;private set
        fun due(s:WorldInfusionState,tick:Long,drive:Drive,transfers:List<WorldInfusionSmoke.Transfer>):Boolean {
            if(s.paused || s.phase in setOf(InfusionPhase.READY,InfusionPhase.COMPLETE)) {
                lastOperation=null;armedAt=null;channelAt=null;return false
            }
            if(armedAt==null)armedAt=tick
            if(s.phase==InfusionPhase.INGREDIENTS && transfers.any { !WorldInfusionSmoke.expired(it,tick) })return false
            if(s.phase==InfusionPhase.INGREDIENTS && s.consumed==WorldInfusionRules.ingredients) {
                if(channelAt==null)channelAt=tick
                return tick-channelAt!!>=drive.channelTicks
            }
            return tick-(lastOperation ?: armedAt!!)>=drive.period
        }
        fun accepted(s:WorldInfusionState,tick:Long) {
            lastOperation=tick
            if(s.phase==InfusionPhase.INGREDIENTS && s.consumed==WorldInfusionRules.ingredients)channelAt=tick
        }
        fun channel(s:WorldInfusionState,tick:Long,drive:Drive):Double? = channelAt?.takeIf {
            !s.paused && s.phase==InfusionPhase.INGREDIENTS && s.consumed==WorldInfusionRules.ingredients
        }?.let { ((tick-it).toDouble()/drive.channelTicks).coerceIn(0.0,1.0) }
    }
    class Track {
        var pose=WorldInfusionAnimation.Pose(0.0,0.0,WorldInfusionAnimation.IDLE_GLOW);private set
        private var completedAt:Long?=null
        fun step(s:WorldInfusionState,tick:Long,drive:Drive,completed:Boolean=false,speedOverride:Double?=null,chargedCore:Boolean=false):WorldInfusionAnimation.Pose {
            if(completed)completedAt=tick
            val active=!s.paused && s.phase in setOf(InfusionPhase.ESSENTIA,InfusionPhase.INGREDIENTS)
            if(active)completedAt=null
            val target=if(active)(speedOverride ?: drive.rotationSpeed) else 0.0
            val speed=if(pose.speed<target)(pose.speed+.18).coerceAtMost(target) else (pose.speed-.09).coerceAtLeast(target)
            val age=completedAt?.let { tick-it };val charge=s.supplied.values.sum().toDouble()/WorldInfusionRules.cost.values.sum()
            val glow=when {
                age!=null && age in 0..14 -> .75+.25*sin(PI*age/14)
                age!=null && age in 15..70 -> .22+(.75-.22)*(70-age)/56
                chargedCore && s.phase in setOf(InfusionPhase.ESSENTIA,InfusionPhase.INGREDIENTS) -> WorldInfusionCharge.fill(s).glow(tick,active)
                active -> (.28+.22*drive.supply+.25*charge+.025*sin(tick*PI/24)).coerceIn(.22,.80)
                else -> (pose.glow-.025).coerceAtLeast(.22)
            }
            pose=WorldInfusionAnimation.Pose(pose.yaw+speed,speed,glow);return pose
        }
        /** Small transparent violet knots/weapon sparks; never a whiteout or new item mesh. */
        fun pedestalGlow(s:WorldInfusionState,tick:Long):Double {
            val age=completedAt?.let { tick-it } ?: return 0.0
            return if(s.phase==InfusionPhase.COMPLETE && s.gearPlace==InfusionGearPlace.OUTPUT && age in 0..30)sin(PI*age/30) else 0.0
        }
        fun finishSamples(s:WorldInfusionState,tick:Long,clock:Clock,drive:Drive,pivot:Vec,weapon:Vec,
            channelOverride:Double?=null,useChannelOverride:Boolean=false,pedestal:Boolean=false):List<WorldInfusionSmoke.Sample> = buildList {
            val channel=if(useChannelOverride)channelOverride else clock.channel(s,tick,drive)
            if(channel!=null)for(i in 0..5) {
                val u=(channel*1.35-i*.08).coerceIn(0.0,1.0)
                if(u==0.0 || u==1.0)continue
                val p=pose.inlet(pivot).add(weapon.sub(pose.inlet(pivot)).mul(u))
                    .add(sin(u*PI*5+i)*.09,0.0,cos(u*PI*5+i)*.09)
                add(WorldInfusionSmoke.Sample("convergence:$i",p,0xac70dd,(.45+.24*sin(PI*u)).toFloat(),u))
            }
            val age=completedAt?.let { tick-it }
            val sparkAge=age?.let { if(pedestal)it-10 else it }
            if(sparkAge!=null && sparkAge in 0..18 && s.phase==InfusionPhase.COMPLETE && s.gearPlace==InfusionGearPlace.OUTPUT)for(i in 0..7) {
                val u=sparkAge/18.0;val a=i*PI/4+u*.5;val radius=.12+.22*u
                val p=weapon.add(cos(a)*radius,.05+sin(u*PI)*.1,sin(a)*radius)
                add(WorldInfusionSmoke.Sample("weapon-success:$i",p,if(i%2==0)0xad78dc else 0xd3b4dc,(.52*(1-u)+.12).toFloat(),u))
            }
            if(pedestal && age!=null && age in 0..26 && s.phase==InfusionPhase.COMPLETE && s.gearPlace==InfusionGearPlace.OUTPUT)for(i in 0..4) {
                val a=age-i*2;if(a !in 0..18)continue
                val u=a/18.0;val p=weapon.add(sin(i*1.8+u*3)*.08,-.36*(1-u),cos(i*1.8+u*3)*.08)
                add(WorldInfusionSmoke.Sample("pedestal-rise:$i",p,0xb282db,(.72-.3*u).toFloat(),u))
            }
        }
    }
}
