package dev.projects.server.coreloop

import dev.projects.server.coreloop.ui.CoreUiPackServer
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.*
import net.minestom.server.entity.metadata.display.*
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.event.player.*
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.component.CustomModelData
import net.minestom.server.component.DataComponents
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sin

/** Opt-in Minecraft world server, not an altar menu. All resources here are isolated local test fixtures. */
internal object WorldInfusionServer {
    private val token=Tag.String("projects_world_infusion_item")
    private val games=ConcurrentHashMap<UUID,WorldInfusionGame>()
    fun start() {
        val server=MinecraftServer.init(Auth.Offline())
        val repository=WorldInfusionRepository(Path.of(System.getProperty("projects.infusion.save","config/projects/world-infusion-lab")))
        val pack=CoreUiPackServer.start()
        val events=MinecraftServer.getGlobalEventHandler()
        events.addListener(AsyncPlayerConfigurationEvent::class.java) { e->
            try {
                require(games[e.player.uuid]==null) { "既に接続されています" }
                val saved=repository.load(e.player.uuid) ?: repository.save(0,WorldInfusionState.fresh(e.player.uuid))
                val instance=MinecraftServer.getInstanceManager().createInstanceContainer()
                instance.setChunkSupplier(::LightingChunk)
                instance.setGenerator { it.modifier().fillHeight(0,41,Block.STONE_BRICKS) }
                instance.setTime(6000);instance.defaultClock()?.pause()
                (-1..1).flatMap { x->(-1..1).map { z->instance.loadChunk(x,z) } }.forEach { it.join() }
                val game=WorldInfusionGame(e.player,instance,repository,saved)
                games[e.player.uuid]=game
                e.spawningInstance=instance;e.player.respawnPoint=Pos(.5,41.0,-7.5,0f,0f)
            } catch(f:Exception) { e.player.kick(Component.text("試作保存データは保護されました：${f.message?.take(100)}")) }
        }
        events.addListener(PlayerSpawnEvent::class.java) { e->
            val game=games[e.player.uuid] ?: return@addListener
            e.player.gameMode=GameMode.ADVENTURE
            game.rebuild();game.inventory()
            e.player.sendMessage(Component.text("ワールド祭壇試作：祭壇キットを床へ右クリック → 外側台座とJarを設置。装備と素材を手に持って台座へ。",NamedTextColor.GOLD))
            e.player.sendMessage(Component.text("Matrixを筆記杖で起動。しゃがみ＋空手で素材回収／空台座・Jar撤去、しゃがみ＋杖で取消し。",NamedTextColor.GRAY))
            e.player.sendMessage(Component.text("これは独立した試験ワールド。初期装備・素材・充填Jar・範囲6・霜転レシピは仮設定です。",NamedTextColor.GRAY))
            pack?.offer(e.player) { player,loaded->games[player.uuid]?.let { it.packed=loaded;it.rebuild();it.inventory() } }
        }
        events.addListener(PlayerBlockInteractEvent::class.java) { e->
            if(e.hand!=PlayerHand.MAIN)return@addListener
            val game=games[e.player.uuid] ?: return@addListener
            if(e.player.instance!==game.instance)return@addListener
            e.isCancelled=true
            game.interact(e.blockPosition,e.blockFace)
        }
        events.addListener(PlayerBlockBreakEvent::class.java) { it.isCancelled=true }
        events.addListener(PlayerBlockPlaceEvent::class.java) { it.isCancelled=true }
        events.addListener(ItemDropEvent::class.java) { it.isCancelled=true }
        events.addListener(PlayerSwapItemEvent::class.java) { it.isCancelled=true }
        events.addListener(InstanceTickEvent::class.java) { e->games.values.filter { it.instance===e.instance }.forEach { it.tick() } }
        events.addListener(PlayerDisconnectEvent::class.java) { e->
            pack?.forget(e.player)
            games.remove(e.player.uuid)?.let { game->game.close();MinecraftServer.getInstanceManager().unregisterInstance(game.instance) }
        }
        Runtime.getRuntime().addShutdownHook(Thread({ games.values.forEach { it.close() };pack?.close() },"world-infusion-close"))
        val port=System.getProperty("projects.port","25585").toInt()
        server.start("127.0.0.1",port)
        println("PROJECTS_WORLD_INFUSION_READY address=127.0.0.1:$port save=${System.getProperty("projects.infusion.save","config/projects/world-infusion-lab")} dedicatedMaterials=false researchGate=false")
    }

    internal class WorldInfusionGame(val player:Player,val instance:InstanceContainer,val repository:WorldInfusionRepository,var state:WorldInfusionState) {
        var packed=false
        private var ticks=0L
        private var lastClick=-10L
        private val displays=mutableMapOf<String,Entity>()
        private val visibleKeys=mutableSetOf<String>()
        private val blocks=mutableMapOf<Triple<Int,Int,Int>,Block>()
        private val blockKeys=mutableSetOf<Triple<Int,Int,Int>>()
        private data class Trail(val from:Vec,val to:Vec,val rgb:Int,val started:Long)
        private val trails=mutableListOf<Trail>()
        private val smoke=mutableListOf<WorldInfusionSmoke.Transfer>()
        private val smokeDisplays=mutableMapOf<String,Entity>()
        private fun at(c:InfusionCell,y:Double=0.0)=Pos(c.x+.5,c.y+y,c.z+.5)
        private fun block(x:Int,y:Int,z:Int,b:Block) {
            val key=Triple(x,y,z);blockKeys+=key
            if(blocks[key]!=b) { instance.setBlock(x,y,z,b);blocks[key]=b }
        }
        private fun item(material:Material,name:String,id:String)=ItemStack.of(material).withCustomName(Component.text(name,NamedTextColor.GOLD))
            .withTag(token,id)
        private fun model(model:String,material:Material,name:String,id:String)=item(material,name,id).let {
            if(packed) it.withItemModel("projects:infusion/$model") else it
        }
        fun inventory() {
            // Server ledger projections, like CoreLoopItems: tags alone never confer ownership.
            player.inventory.clear();player.inventory.cursorItem=ItemStack.AIR;player.setItemInOffHand(ItemStack.AIR)
            val items=mutableListOf<ItemStack>()
            items+=item(Material.BLAZE_ROD,"筆記杖：Matrixを右クリックで起動","cast")
            if(state.matrix==null)items+=model("matrix",Material.LODESTONE,"祭壇キット（試作）","matrix")
            if(state.pedestals.size<4)items+=model("pedestal",Material.CHISELED_STONE_BRICKS,"外側台座","pedestal").withAmount(4-state.pedestals.size)
            state.jars.filter { it.cell==null }.forEach { j->items+=model("jar_${j.aspect.name.lowercase()}",Material.GLASS_BOTTLE,"${j.aspect.label} Jar ${j.amount}/${j.capacity}","jar:${j.id}") }
            if(state.gearPlace==InfusionGearPlace.INVENTORY)items+=gearItem()
            WorldInfusionRules.ingredients.keys.forEach { r->val n=state.account.amount(r,2);if(n>0)items+=
                CoreLoopItems.resource(CoreMaterial(r,2),n).withTag(token,"material:${r.name}") }
            items.forEachIndexed { i,v->player.inventory.setItemStack(i,v) }
        }
        private fun gearItem():ItemStack {
            val g=state.gear
            val projected=g.project(state.account)
            return CoreLoopItems.gear(projected,CoreGearSlot.WEAPON,packed)
                .withTag(token,"gear:${g.identity.id}")
                .withLore(listOf(Component.text("UUID ${g.identity.id}"),Component.text("製作者 ${g.identity.crafter}"),
                    Component.text("品質${g.identity.quality} / T${g.tier} Lv${g.identity.itemLevel} +${g.enhancement.level}"))+
                    g.affixes.map { Component.text("枠${it.index} ${it.stone.modId} ${it.stone.value}") })
        }
        private fun mutate(change:(WorldInfusionState)->WorldInfusionState):Boolean = try {
            val next=change(state)
            if(next===state)return false
            state=repository.save(state.account.revision,next)
            if(state.paused || state.phase!=InfusionPhase.ESSENTIA) {
                for(i in smoke.indices)smoke[i]=smoke[i].copy(releaseUntil=minOf(smoke[i].releaseUntil,ticks))
            }
            rebuild();inventory();true
        } catch(f:Exception) {
            if(f !is IllegalArgumentException) {
                System.err.println("WORLD_INFUSION_OPERATION_FAILED player=${player.uuid} revision=${state.account.revision}")
                f.printStackTrace()
            }
            player.sendActionBar(Component.text(f.message?.take(160) ?: "操作できません",NamedTextColor.RED));false
        }
        fun interact(point:Point,face:net.minestom.server.instance.block.BlockFace) {
            if(ticks-lastClick<3 || player.position.distance(point)>6)return
            lastClick=ticks
            val hand=player.itemInMainHand
            val id=hand.getTag(token)
            val p=Triple(point.blockX(),point.blockY(),point.blockZ())
            val matrix=state.matrix
            val matrixHit=matrix!=null && p.first==matrix.x && p.third==matrix.z && p.second in matrix.y..matrix.y+3
            val cell=runCatching { InfusionCell(p.first,41,p.third) }.getOrNull() ?: return
            val pedestal=state.pedestals.firstOrNull { it.cell==cell && p.second==41 }
            val jar=state.jars.firstOrNull { it.cell==cell && p.second==41 }
            if(matrixHit) {
                when {
                    id=="cast" && player.isSneaking->mutate { WorldInfusionRules.cancel(it) }
                    id=="cast"->{ if(mutate { WorldInfusionRules.start(it) }) {
                        player.playSound(Sound.sound(SoundEvent.BLOCK_AMETHYST_BLOCK_CHIME,Sound.Source.BLOCK,.6f,.8f))
                        trails+=Trail(Vec(matrix!!.x+.5,44.0,matrix.z+.5),Vec(matrix.x+.5,42.1,matrix.z+.5),0xe2cf91,ticks)
                    } }
                    id?.startsWith("gear:")==true->mutate { WorldInfusionRules.placeGear(it,UUID.fromString(id.substringAfter(':'))) }
                    hand.isAir->mutate { WorldInfusionRules.takeGear(it) }
                    else->player.sendActionBar(Component.text("中心に装備、Matrixには筆記杖。取消しはしゃがみ＋杖。"))
                }
                return
            }
            if(jar!=null) {
                if(player.isSneaking && hand.isAir)mutate { WorldInfusionRules.removeJar(it,jar.id) }
                else player.sendActionBar(Component.text("${jar.aspect.label} Jar ${jar.amount}/${jar.capacity} / しゃがみ＋空手で撤去"))
                return
            }
            if(pedestal!=null) {
                when {
                    hand.isAir && pedestal.item!=null->mutate { WorldInfusionRules.material(it,cell,null,take=true) }
                    hand.isAir && player.isSneaking->mutate { WorldInfusionRules.removePedestal(it,cell) }
                    id?.startsWith("material:")==true->mutate { WorldInfusionRules.material(it,cell,CoreResource.valueOf(id.substringAfter(':'))) }
                    else->player.sendActionBar(Component.text("通常素材を手に持って右クリック。空手で回収。"))
                }
                return
            }
            // This private test island permits floor placement, not arbitrary real-world overwrites.
            if(point.blockY()!=40 || face!=net.minestom.server.instance.block.BlockFace.TOP)return
            when {
                id=="matrix"->mutate { WorldInfusionRules.placeMatrix(it,cell) }
                id=="pedestal"->mutate { WorldInfusionRules.placePedestal(it,cell) }
                id?.startsWith("jar:")==true->mutate { WorldInfusionRules.placeJar(it,UUID.fromString(id.substringAfter(':')),cell) }
            }
        }
        private fun display(stack:ItemStack,pos:Pos,scale:Vec=Vec(1.0,1.0,1.0)) {
            val key="item:${pos.x()},${pos.y()},${pos.z()}";visibleKeys+=key
            val entity=displays.getOrPut(key) { Entity(EntityType.ITEM_DISPLAY).apply {
                setNoGravity(true);setHasPhysics(false);setInstance(this@WorldInfusionGame.instance,pos)
            } }
            entity.editEntityMeta(ItemDisplayMeta::class.java) { m->
                m.setItemStack(stack);m.setDisplayContext(ItemDisplayMeta.DisplayContext.NONE);m.setScale(scale);m.setViewRange(2f)
            }
        }
        private fun liquid(j:InfusionJar) {
            if(j.amount==0)return
            val c=j.cell ?: return
            val key="liquid:${j.id}";visibleKeys+=key
            val e=displays.getOrPut(key) { Entity(EntityType.BLOCK_DISPLAY).apply {
                setNoGravity(true);setHasPhysics(false);setInstance(this@WorldInfusionGame.instance,at(c))
            } }
            if(e.position.distanceSquared(at(c))>.0001)e.teleport(at(c))
            e.editEntityMeta(BlockDisplayMeta::class.java) { m->
                m.setBlockState(when(j.aspect){InfusionAspect.EMBER->Block.ORANGE_STAINED_GLASS;InfusionAspect.TIDE->Block.CYAN_STAINED_GLASS;InfusionAspect.GALE->Block.LIME_STAINED_GLASS})
                m.setScale(Vec(.36,.5*j.amount/j.capacity,.36));m.setTranslation(Vec(-.18,.13,-.18));m.setBrightness(12,12)
            }
        }
        fun rebuild() {
            // Reuse physical entities through the ritual, update only liquid/items; no rebuild flicker per unit.
            visibleKeys.clear();blockKeys.clear()
            state.matrix?.let { c->
                block(c.x,41,c.z,if(packed)Block.BARRIER else Block.CHISELED_STONE_BRICKS)
                display(model("pedestal",Material.CHISELED_STONE_BRICKS,"中心台座","visual"),at(c,.5))
                for(x in listOf(-1,1))for(z in listOf(-1,1)) {
                    for(y in 41..43)block(c.x+x,y,c.z+z,if(y==43)Block.CUT_COPPER else Block.POLISHED_DEEPSLATE_WALL)
                }
                block(c.x,44,c.z,if(packed)Block.BARRIER else Block.LODESTONE)
                display(model("matrix",Material.LODESTONE,"Matrix","visual"),at(c,3.5))
                if(state.gearPlace!=InfusionGearPlace.INVENTORY)display(gearItem(),at(c,1.15),Vec(.65,.65,.65))
            }
            state.pedestals.forEach { p->
                block(p.cell.x,41,p.cell.z,if(packed)Block.BARRIER else Block.CHISELED_STONE_BRICKS)
                if(packed)display(model("pedestal",Material.CHISELED_STONE_BRICKS,"外側台座","visual"),at(p.cell,.5))
                p.item?.let { display(CoreLoopItems.resource(CoreMaterial(it,2),1),at(p.cell,1.12),Vec(.55,.55,.55)) }
            }
            state.jars.filter { it.cell!=null }.forEach { j->val c=j.cell!!
                block(c.x,41,c.z,if(packed)Block.BARRIER else Block.GLASS)
                if(packed)display(model("jar_${j.aspect.name.lowercase()}",Material.GLASS_BOTTLE,"Jar","visual"),at(c,.5))
                liquid(j)
            }
            displays.entries.removeIf { (key,e)->if(key !in visibleKeys) { e.remove();true } else false }
            blocks.entries.removeIf { (c,_)->if(c !in blockKeys) { instance.setBlock(c.first,c.second,c.third,Block.AIR);true } else false }
        }
        fun tick() {
            ticks++
            if(!player.isOnline || player.instance!==instance)return
            if(ticks%18==0L) {
                val (next,pulse)=WorldInfusionRules.tick(state)
                if(next!==state && mutate { next }) {
                    val c=state.matrix!!;val target=Vec(c.x+.5,44.3,c.z+.5)
                    pulse?.let { v->
                        val source=v.jar?.let { id->state.jars.single { it.id==id }.cell?.let { Vec(it.x+.5,41.55,it.z+.5) } }
                            ?: v.ingredient?.let { Vec(it.x+.5,42.15,it.z+.5) } ?: Vec(c.x+.5,42.0,c.z+.5)
                        if(v.jar!=null && v.aspect!=null) {
                            // Only a durably committed real Jar drain releases smoke. No smoke for reservoir reuse.
                            val mouth=source.add(0.0,.4,0.0)
                            smoke+=WorldInfusionSmoke.Transfer(mouth,target,v.aspect.rgb,ticks)
                        } else trails+=Trail(source,target,v.aspect?.rgb ?: 0xe1cfab,ticks)
                        if(v.completed)player.playSound(Sound.sound(SoundEvent.BLOCK_AMETHYST_BLOCK_CHIME,Sound.Source.BLOCK,.7f,1.3f))
                    }
                }
            }
            trails.removeIf { ticks-it.started>18 }
            smoke.removeIf { WorldInfusionSmoke.expired(it,ticks) }
            if(ticks%2==0L) {
                val frame=WorldInfusionSmoke.frame(smoke,ticks)
                val active=if(packed)frame.map { it.key }.toSet() else emptySet()
                smokeDisplays.entries.removeIf { (key,e)->if(key !in active) { e.remove();true } else false }
                frame.forEach { mote->
                    if(packed) {
                        val e=smokeDisplays.getOrPut(mote.key) { Entity(EntityType.ITEM_DISPLAY).apply {
                            setNoGravity(true);setHasPhysics(false);setInstance(this@WorldInfusionGame.instance,Pos(mote.position.x(),mote.position.y(),mote.position.z()))
                        } }
                        e.teleport(Pos(mote.position.x(),mote.position.y(),mote.position.z()))
                        e.editEntityMeta(ItemDisplayMeta::class.java) { m->
                            m.setItemStack(ItemStack.of(Material.PAPER).withItemModel("projects:infusion/smoke")
                                .with(DataComponents.CUSTOM_MODEL_DATA,CustomModelData(emptyList(),emptyList(),emptyList(),
                                    listOf(net.kyori.adventure.text.format.TextColor.color(mote.rgb)))))
                            m.setDisplayContext(ItemDisplayMeta.DisplayContext.NONE)
                            m.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER)
                            val size=.26*mote.scale;m.setScale(Vec(size,size,size))
                            m.setPosRotInterpolationDuration(2);m.setTransformationInterpolationDuration(2)
                            m.setTransformationInterpolationStartDelta(0);m.setBrightness(12,12)
                            m.setShadowRadius(0f);m.setViewRange(2f)
                        }
                    } else player.sendPacket(ParticlePacket(Particle.DUST.withColor(net.kyori.adventure.text.format.TextColor.color(mote.rgb)).withScale(mote.scale),
                        false,false,mote.position,Vec.ZERO,0f,1))
                }
            }
            trails.forEach { t->
                val u=(ticks-t.started)/18.0
                for(i in 0..4) {
                    val v=(u-i*.045).coerceIn(0.0,1.0)
                    val p=t.from.add(t.to.sub(t.from).mul(v)).add(0.0,sin(v*Math.PI)*.65,0.0)
                    player.sendPacket(ParticlePacket(Particle.DUST.withColor(net.kyori.adventure.text.format.TextColor.color(t.rgb)).withScale(.55f),
                        false,false,p,Vec.ZERO,0f,1))
                }
            }
            if(ticks%10==0L && state.matrix!=null && player.position.distance(at(state.matrix!!))<12) {
                val message=when {
                    state.paused->"供給停止：Jarを設置し直しMatrixへ筆記杖で再開 / しゃがみ＋杖で取消し"
                    state.phase==InfusionPhase.ESSENTIA->"元素吸収 ${state.supplied.values.sum()}/7 — 周囲素材はまだ残っています"
                    state.phase==InfusionPhase.INGREDIENTS->"副素材吸収 ${state.consumed.values.sum()}/4"
                    state.phase==InfusionPhase.COMPLETE->"霜転の調律 完了 — 中心台座へ空手で右クリックして回収"
                    else->WorldInfusionRules.missing(state) ?: "準備完了：頭上のMatrixへ筆記杖で右クリック"
                }
                player.sendActionBar(Component.text(message,NamedTextColor.GOLD))
            }
        }
        fun close() { displays.values.forEach { it.remove() };displays.clear();trails.clear();smoke.clear();smokeDisplays.values.forEach { it.remove() };smokeDisplays.clear() }
    }
}
