package dev.projects.server.coreloop

/** Read-only model descriptors. A future Bedrock renderer can consume the same committed state without changing rules. */
internal data class FoundryVisual(val key:String,val model:String,val cell:FoundryCell,val yaw:Float=0f)
internal object WorldFoundryPresentation {
    fun frame(s:WorldFoundryState):List<FoundryVisual> = buildList {
        s.equipment.forEach { (k,c)->
            val m=s.equipment[FoundryEquipment.MELTER]
            val yaw=if(k==FoundryEquipment.FAUCET && m!=null)when {
                c.x>m.x->-90f;c.x<m.x->90f;c.z>m.z->180f;else->0f
            } else 0f
            add(FoundryVisual(k.name.lowercase(),k.name.lowercase(),c,yaw))
        }
        fun overlay(key:String,name:String,c:FoundryCell) { add(FoundryVisual(key,name,c)) }
        s.equipment[FoundryEquipment.MELTER]?.let { c->
            if(s.liquid>0)overlay("liquid","liquid_${s.liquid}",c)
            if(s.input>0)overlay("input","input",c)
        }
        s.equipment[FoundryEquipment.HEATER]?.let { if(s.meltSeconds>0)overlay("heat","heat",it) }
        s.equipment[FoundryEquipment.TABLE]?.let { table->
            if(s.phase!=FoundryPhase.EMPTY) {
                val shape=s.shape?.name?.lowercase()
                val name=when(s.phase) {
                    FoundryPhase.SAND->"sand"
                    FoundryPhase.PATTERN->"pattern_$shape"
                    FoundryPhase.MOLD->"mold_$shape"
                    FoundryPhase.POURING->"pour_$shape"
                    FoundryPhase.COOLING->"cool_${shape}_${s.seconds}"
                    FoundryPhase.OUTPUT->"part_$shape"
                    else->error("empty")
                }
                overlay("table-content",name,table)
                if(s.phase==FoundryPhase.POURING)add(FoundryVisual("stream","stream",table,first { it.key=="faucet" }.yaw))
            }
        }
    }
}
