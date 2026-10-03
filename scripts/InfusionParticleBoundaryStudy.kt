package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.hypot
import kotlin.math.min

/** Pure sampler locality audit; no MinecraftServer, connection, instance, or save. */
fun main(args:Array<String>) {
    val pivot=Vec(.5,3.4,.5)
    val positions=listOf(0 to 2,2 to 0,2 to 2,4 to 0,-4 to 2,4 to 2,4 to 4,6 to 0,0 to 6)
    val rows=mutableListOf<String>();val progressDistances=mutableListOf<Double>();val spatialDistances=mutableListOf<Double>()
    var spatialFlickers=0;var spatialNeverEntered=0;var totalKeys=0
    for(power in listOf(6,18))for((x,z) in positions)for(start in listOf(0L,44L,92L)) {
        val supply=WorldInfusionEnergy.Supply(power)
        val transfer=WorldInfusionSmoke.Transfer(Vec(x+.5,.95,z+.5),pivot,InfusionAspect.TIDE.rgb,start,
            travelTicks=supply.flightTicks,tuftStep=supply.tuftStep)
        val progressFirst=mutableMapOf<String,Double>();val spatialFirst=mutableMapOf<String,Double>()
        val seen=mutableSetOf<String>();val last=mutableMapOf<String,Boolean>();val switches=mutableMapOf<String,Int>()
        for(tick in start..start+6*supply.tuftStep+supply.flightTicks) {
            val pose=WorldInfusionAnimation.Pose(tick*power/18.0*4.5,power/18.0*4.5,.88)
            val inlet=pose.inlet(pivot)
            for(s in WorldInfusionSmoke.sample(transfer,tick,inlet)) {
                seen+=s.key
                val distance=s.position.distance(inlet)
                if(s.progress>=.65)progressFirst.putIfAbsent(s.key,distance)
                val near=distance<=.55
                val prior=last.put(s.key,near)
                if(prior!=null && prior!=near)switches[s.key]=switches.getOrDefault(s.key,0)+1
                if(near)spatialFirst.putIfAbsent(s.key,distance)
            }
        }
        totalKeys+=seen.size
        spatialFlickers+=switches.count { it.value>1 }
        spatialNeverEntered+=seen.count { it !in spatialFirst }
        progressDistances+=progressFirst.values;spatialDistances+=spatialFirst.values
        rows+="{\"power\":$power,\"jarXZ\":[$x,$z],\"horizontalDistance\":${hypot(x.toDouble(),z.toDouble())},\"started\":$start,\"sampleKeys\":${seen.size},\"progress65FirstDistanceMin\":${progressFirst.values.min()},\"progress65FirstDistanceMax\":${progressFirst.values.max()},\"radius055FirstDistanceMin\":${spatialFirst.values.minOrNull() ?: "null"},\"radius055FirstDistanceMax\":${spatialFirst.values.maxOrNull() ?: "null"},\"radius055NeverEntered\":${seen.count { it !in spatialFirst }},\"radius055FlickerKeys\":${switches.count { it.value>1 }}}"
    }
    check(spatialDistances.all { it<=.55+1e-9 })
    check(spatialFlickers==0 && spatialNeverEntered==0)
    val output=Path.of(args.single());Files.createDirectories(output.parent)
    Files.writeString(output,"{\"cases\":${rows.size},\"sampleKeys\":$totalKeys,\"progress65FirstDistanceMin\":${progressDistances.min()},\"progress65FirstDistanceMax\":${progressDistances.max()},\"radius055FirstDistanceMin\":${spatialDistances.min()},\"radius055FirstDistanceMax\":${spatialDistances.max()},\"radius055FlickerKeys\":$spatialFlickers,\"radius055NeverEntered\":$spatialNeverEntered,\"judgment\":\"progress .65 does not guarantee inlet locality; spatial .55 is a tested candidate policy, still not a TC6 specification\",\"rows\":[${rows.joinToString(",")}] }\n")
    println("BOUNDARY_STUDY_PASS cases=${rows.size} keys=$totalKeys progress65Distance=${progressDistances.min()}..${progressDistances.max()} spatial055Distance=${spatialDistances.min()}..${spatialDistances.max()} flickers=$spatialFlickers neverEntered=$spatialNeverEntered noServer noSave")
}
