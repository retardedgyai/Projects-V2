package dev.projects.server.coreloop

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.pow
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.GenerationUnit
import net.minestom.server.world.biome.Biome

/** Distant headlands frame the working cove. Does not place anything inside the authored town footprint. */
internal object HarborBackdrop {
    fun generate(unit: GenerationUnit) {
        val modifier = unit.modifier()
        val start = unit.absoluteStart(); val end = unit.absoluteEnd()
        modifier.fillBiome(if (start.blockZ() >= 16) Biome.WARM_OCEAN else Biome.JUNGLE)
        modifier.fillHeight(0,34,Block.STONE)
        modifier.fillHeight(34,36,Block.SAND)
        modifier.fillHeight(36,39,Block.WATER)
        for (x in start.blockX() until end.blockX()) for (z in start.blockZ() until end.blockZ()) {
            val top = height(x,z) ?: continue
            for (y in 36..top) modifier.setBlock(x,y,z,when {
                y == top && top >= 43 -> Block.GRASS_BLOCK
                y == top && top <= 40 -> Block.SAND
                y > top - 3 && top >= 43 -> Block.DIRT
                Math.floorMod(y / 3 + x / 7 - z / 6,7) < 2 -> Block.ANDESITE
                else -> Block.STONE
            })
            // Continuous shrub patches follow the actual surface, never float above a sampled height.
            if (top >= 44 && sin(x * .28) + sin(z * .23) > 1.1)
                modifier.setBlock(x,top+1,z,Block.JUNGLE_LEAVES.withProperty("persistent","true"))
        }
        fun paint(x: Int,y: Int,z: Int,block: Block) {
            if(x in start.blockX() until end.blockX() && z in start.blockZ() until end.blockZ() &&
                y in start.blockY() until end.blockY() && !(abs(x)<=55 && z>=-62)) modifier.setBlock(x,y,z,block)
        }
        // Sparse authored tree clusters. Each chunk generates its portion of the same bent palms, including borders.
        val palms = listOf(-81 to -51,-69 to -37,-74 to -62,-62 to -52,
            68 to -47,81 to -37,87 to -62,73 to -76,91 to -48,
            -39 to -86,-23 to -102,-6 to -80,11 to -93,31 to -84,-48 to -100)
        for ((cx,cz) in palms) {
            if(cx+9<start.blockX() || cx-9>=end.blockX() || cz+9<start.blockZ() || cz-9>=end.blockZ()) continue
            val ground=height(cx,cz)?.takeIf { it>=47 } ?: continue
            val lean=if(cx<0) -1 else 1
            for(i in 1..10) paint(cx+lean*(i/4),ground+i,cz,Block.JUNGLE_LOG)
            val crownX=cx+lean*2; val crownY=ground+10
            for ((dx,dz) in listOf(-1 to 0,1 to 0,0 to -1,0 to 1,-1 to -1,-1 to 1,1 to -1,1 to 1))
                for(step in 0..6) {
                    val y=crownY+2-step*step/10
                    val leaf=Block.JUNGLE_LEAVES.withProperty("persistent","true")
                    paint(crownX+dx*step,y,cz+dz*step,leaf)
                    if(step<5) paint(crownX+dx*step+(if(dz!=0) 1 else 0),y,cz+dz*step+(if(dx!=0) 1 else 0),leaf)
                }
        }
    }

    internal fun height(x: Int,z: Int): Int? {
        if (abs(x) <= 55 && z >= -62) return null
        fun hill(cx: Int,cz: Int,rx: Double,rz: Double,rise: Double): Double {
            val dx=(x-cx)/rx; val dz=(z-cz)/rz
            val edge=(1.0-dx*dx-dz*dz + sin(x*.16+z*.07)*.06 + sin(z*.19-x*.05)*.05).coerceAtMost(1.0)
            if(edge<=0) return 0.0
            // Shelf, steep broken cliff, planted cap. Avoid obvious smooth hemisphere-shaped islands.
            return rise * if(edge<.12) edge*2.5 else .3 + .7*((edge-.12)/.88).pow(.38)
        }
        val rise = max(hill(-72,-45,24.0,35.0,22.0),
            max(hill(78,-55,27.0,41.0,30.0),hill(-8,-93,67.0,36.0,35.0)))
        if(rise<1.0) return null
        val erosion=(sin(x*.31+z*.09)*1.5+sin(z*.37-x*.11)).coerceIn(-2.0,2.0)
        return (37 + rise + erosion).toInt().coerceAtLeast(36)
    }
}
