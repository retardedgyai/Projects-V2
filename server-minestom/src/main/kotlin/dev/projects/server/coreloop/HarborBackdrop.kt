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
    /** Continuous chalk strata, with darker wet feet and broad mineral seams. Shared by town and headlands. */
    internal fun coastalStone(x: Int, y: Int, z: Int): Block {
        val seam=y+sin(x*.13+z*.06)*2.5+sin(z*.21)*1.2
        return when {
            y<=37 -> Block.ANDESITE
            y<=40 -> if(sin(x*.24+z*.17)>.4) Block.TUFF else Block.STONE
            Math.floorMod(seam.toInt(),11) in 0..1 -> Block.DIORITE
            else -> Block.CALCITE
        }
    }

    fun generate(unit: GenerationUnit) {
        val modifier = unit.modifier()
        val start = unit.absoluteStart(); val end = unit.absoluteEnd()
        modifier.fillBiome(if (start.blockZ() >= 16) Biome.WARM_OCEAN else Biome.JUNGLE)
        modifier.fillHeight(0,34,Block.STONE)
        modifier.fillHeight(34,36,Block.SAND)
        modifier.fillHeight(36,39,Block.WATER)
        for (x in start.blockX() until end.blockX()) for (z in start.blockZ() until end.blockZ()) {
            val top = height(x,z) ?: continue
            val slope=listOf(x-1 to z,x+1 to z,x to z-1,x to z+1)
                .maxOf { (nx,nz) -> abs(top-(height(nx,nz) ?: 38)) }
            val planted=top>=43 && slope<=2
            for (y in 36..top) modifier.setBlock(x,y,z,when {
                y == top && planted -> if(slope==0) Block.GRASS_BLOCK else Block.MOSS_BLOCK
                y == top && top <= 40 -> Block.SAND
                y == top-1 && planted -> if(slope==0) Block.ROOTED_DIRT else Block.MOSS_BLOCK
                else -> coastalStone(x,y,z)
            })
            // Broad, variable-height undergrowth softens the slopes instead of repeating isolated one-block shrubs.
            val shrub=sin(x*.12+z*.08)+sin(z*.16-x*.06)
            if(planted && shrub>.8 && !(abs(x)<18 && z> -70)) {
                val crown=1+((shrub-.8)*2).toInt().coerceIn(0,2)
                for(y in top+1..top+crown)
                    modifier.setBlock(x,y,z,Block.JUNGLE_LEAVES.withProperty("persistent","true"))
            }
        }
        fun paint(x: Int,y: Int,z: Int,block: Block) {
            if(x in start.blockX() until end.blockX() && z in start.blockZ() until end.blockZ() &&
                y in start.blockY() until end.blockY() && !(abs(x)<=55 && z>=-62)) modifier.setBlock(x,y,z,block)
        }
        // Broadleaf groves form a connected canopy behind the palms. They never enter the authored town.
        val grove = listOf(-80 to -43,-75 to -57,-64 to -58,-87 to -61,-69 to -73,
            69 to -38,82 to -44,92 to -52,73 to -61,84 to -76,63 to -76,94 to -70,
            -48 to -88,-33 to -95,-17 to -88,1 to -92,18 to -92,34 to -89,46 to -97,
            -40 to -108,-22 to -112,-2 to -113,19 to -111)
        val groveLeaves=Block.JUNGLE_LEAVES.withProperty("persistent","true")
        for((cx,cz) in grove) {
            if(cx+8<start.blockX() || cx-8>=end.blockX() || cz+8<start.blockZ() || cz-8>=end.blockZ()) continue
            val ground=height(cx,cz)?.takeIf { it>=47 } ?: continue
            val crown=ground+6+Math.floorMod(cx+cz,3)
            for(y in ground..crown+1) paint(cx,y,cz,Block.JUNGLE_LOG)
            for((dx,dz,lift) in listOf(Triple(-3,0,0),Triple(2,1,1),Triple(0,-3,0),Triple(0,2,2))) {
                // Branches meet the trunk; overlapping leaf lobes produce a non-spherical crown.
                for(step in 0..3) {
                    paint(cx+dx*step/3,crown,cz+dz*step/3,Block.JUNGLE_LOG)
                    paint(cx+dx*step/3,crown,cz+dz*(step-1).coerceAtLeast(0)/3,Block.JUNGLE_LOG)
                }
                for(lx in -3..3) for(lz in -3..3) for(ly in -1..2)
                    if(lx*lx+lz*lz+ly*ly*3 <= 12 && !(lx==0 && lz==0 && ly<=0))
                        paint(cx+dx+lx,crown+lift+ly,cz+dz+lz,groveLeaves)
            }
        }
        // Sparse authored tree clusters. Each chunk generates its portion of the same bent palms, including borders.
        val palms = listOf(-81 to -51,-69 to -37,-74 to -62,-62 to -52,
            68 to -47,81 to -37,87 to -62,73 to -76,91 to -48,
            -39 to -86,-23 to -102,-6 to -80,11 to -93,31 to -84,-48 to -100)
        for ((cx,cz) in palms) {
            if(cx+9<start.blockX() || cx-9>=end.blockX() || cz+9<start.blockZ() || cz-9>=end.blockZ()) continue
            val ground=height(cx,cz)?.takeIf { it>=47 } ?: continue
            val lean=if(cx<0) -1 else 1
            for(i in 1..10) {
                paint(cx+lean*(i/4),ground+i,cz,Block.JUNGLE_LOG)
                paint(cx+lean*((i-1)/4),ground+i,cz,Block.JUNGLE_LOG)
            }
            val crownX=cx+lean*2; val crownY=ground+10
            for ((dx,dz) in listOf(-1 to 0,1 to 0,0 to -1,0 to 1,-1 to -1,-1 to 1,1 to -1,1 to 1))
                for(step in 0..6) {
                    val y=crownY+2-step*step/10
                    val leaf=Block.JUNGLE_LEAVES.withProperty("persistent","true")
                    val previousY=crownY+2-(step-1).coerceAtLeast(0)*(step-1).coerceAtLeast(0)/10
                    paint(crownX+dx*step,previousY,cz+dz*(step-1).coerceAtLeast(0),leaf)
                    for(ly in y..previousY) paint(crownX+dx*step,ly,cz+dz*step,leaf)
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
        val summit = 37 + rise + erosion
        // The authored rear lawn ends at y51 / z-62. Clipping the hill there used to expose
        // a perfectly vertical, 13-block cut face directly in the hall's rear doorway.
        // Grade the unbuilt side into that lawn; retain the distant summit and coastal cliffs.
        val depth = (-62-z).toDouble()
        val joinWeight = ((42.0-abs(x))/12.0).coerceIn(0.0,1.0)
        val shoulder = sin(x*.19+depth*.13)*2.0 + sin(x*.37-depth*.18)
        val lawn = 51.0 - (abs(x)-22).coerceAtLeast(0)*3.0
        val skirt = lawn + depth*.38 + shoulder*(depth/12.0).coerceIn(0.0,1.0)
        val top = if(depth>0 && depth<36 && joinWeight>0) {
            // Fill the hill's shallow hollows as well as cutting its high face, so the east
            // end of the lawn does not become a new drop at the same seam.
            val t=(depth/36.0).coerceIn(0.0,1.0)
            val blend=t*t*(3.0-2.0*t)
            summit + (skirt-summit)*(1.0-blend)*joinWeight
        } else summit
        return top.toInt().coerceAtLeast(36)
    }
}
