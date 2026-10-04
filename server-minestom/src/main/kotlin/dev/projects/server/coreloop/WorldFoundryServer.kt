package dev.projects.server.coreloop

import dev.projects.server.coreloop.ui.CoreUiPackServer
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
import net.minestom.server.event.instance.InstanceUnregisterEvent
import net.minestom.server.event.player.*
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.block.BlockFace
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Opt-in private workshop: no global workshop policy or production resource migration is implied. */
internal object WorldFoundryServer {
    private val token=Tag.String("projects_foundry")
    private val games=ConcurrentHashMap<UUID,Game>()
    fun start() {
        val server=MinecraftServer.init(Auth.Offline())
        val repository=WorldFoundryRepository(Path.of(System.getProperty("projects.foundry.save","config/projects/world-foundry-lab")))
        val pack=CoreUiPackServer.start()
        val events=MinecraftServer.getGlobalEventHandler()
        fun retire(game:Game) {
            if(!games.remove(game.player.uuid,game))return
            game.close()
            val manager=MinecraftServer.getInstanceManager()
            if(manager.getInstance(game.instance.uuid)===game.instance && game.instance.players.none { it.isOnline })manager.unregisterInstance(game.instance)
        }
        events.addListener(AsyncPlayerConfigurationEvent::class.java) { e->
            try {
                require(games[e.player.uuid]==null) { "すでに接続しています" }
                val state=repository.load(e.player.uuid) ?: repository.save(0,WorldFoundryState.fresh(e.player.uuid))
                val world=MinecraftServer.getInstanceManager().createInstanceContainer()
                world.setChunkSupplier(::LightingChunk)
                world.setGenerator { it.modifier().fillHeight(0,41,Block.STONE_BRICKS) }
                world.setTime(6000);world.defaultClock()?.pause()
                (-1..1).flatMap { x->(-1..1).map { z->world.loadChunk(x,z) } }.forEach { it.join() }
                val game=Game(e.player,world,repository,state)
                if(games.putIfAbsent(e.player.uuid,game)!=null) {
                    game.close();MinecraftServer.getInstanceManager().unregisterInstance(world);error("接続が競合しました")
                }
                e.spawningInstance=world;e.player.respawnPoint=Pos(.5,41.0,-4.5,0f,0f)
            } catch(f:Exception) { e.player.kick(Component.text("鋳造保存は保持しています: ${f.message?.take(100)}")) }
        }
        events.addListener(PlayerSpawnEvent::class.java) { e->games[e.player.uuid]?.let { game->
            if(e.player.instance!==game.instance) { retire(game);return@let }
            e.player.gameMode=GameMode.ADVENTURE;game.refresh()
            e.player.sendMessage(Component.text("工房試作: 炭床→その上にるつぼ→横に出湯口→真下に鋳造台。設備を持って設置面を右クリック。",NamedTextColor.GOLD))
            e.player.sendMessage(Component.text("炭床へ炭、るつぼへT2インゴット。台へ砂→木製原型→空手で原型を外す→出湯口→冷却→空手で回収。",NamedTextColor.GRAY))
            e.player.sendMessage(Component.text("空手＋しゃがみ: 設備解体 / 未使用砂型回収。るつぼへしゃがみ＋回収棒: 湯4単位をインゴットに戻す。試作資源は本編から独立。",NamedTextColor.GRAY))
            pack?.offer(e.player) { p,loaded->games[p.uuid]?.let { it.packed=loaded;it.refresh() } }
        } }
        events.addListener(PlayerBlockInteractEvent::class.java) { e->
            if(e.hand==PlayerHand.MAIN)games[e.player.uuid]?.let { game->
                if(e.player.instance===game.instance) { e.isCancelled=true;game.interact(e.blockPosition,e.blockFace) }
            }
        }
        events.addListener(PlayerBlockBreakEvent::class.java) { e->games[e.player.uuid]?.let { e.isCancelled=true;it.info("空手＋しゃがみで解体します。中身のある設備は保護されます") } }
        events.addListener(PlayerBlockPlaceEvent::class.java) { it.isCancelled=true }
        events.addListener(ItemDropEvent::class.java) { it.isCancelled=true }
        events.addListener(PlayerSwapItemEvent::class.java) { it.isCancelled=true }
        events.addListener(InstanceTickEvent::class.java) { e->games.values.filter { it.instance===e.instance }.forEach { it.tick() } }
        events.addListener(PlayerDisconnectEvent::class.java) { e->pack?.forget(e.player);games[e.player.uuid]?.let(::retire) }
        events.addListener(InstanceUnregisterEvent::class.java) { e->games.values.filter { it.instance===e.instance }.forEach(::retire) }
        Runtime.getRuntime().addShutdownHook(Thread({ games.values.toList().forEach { it.close() };pack?.close() },"foundry-close"))
        val port=System.getProperty("projects.port","25587").toInt()
        server.start("127.0.0.1",port)
        println("PROJECTS_WORLD_FOUNDRY_READY address=127.0.0.1:$port privateWorkshop=true productionSave=false")
    }

    internal class Game(val player:Player,val instance:InstanceContainer,val repository:WorldFoundryRepository,var state:WorldFoundryState) {
        var packed=false
        private var closed=false
        private var faulted=false
        private var ticks=0L
        private var lastClick=-10L
        private val displays=mutableMapOf<String,Entity>()
        private val blocks=mutableSetOf<FoundryCell>()
        fun info(text:String)=player.sendActionBar(Component.text(text,NamedTextColor.GOLD))
        private fun item(m:Material,name:String,id:String,n:Int=1)=ItemStack.of(m).withCustomName(Component.text(name,NamedTextColor.GOLD)).withTag(token,id).withAmount(n.coerceIn(1,64))
        private fun art(name:String,fallback:Material)=if(packed)ItemStack.of(Material.PAPER).withItemModel("projects:foundry/$name") else ItemStack.of(fallback)
        @Synchronized fun refresh() { if(!closed) { inventory();rebuild() } }
        private fun inventory() {
            player.inventory.clear();player.inventory.cursorItem=ItemStack.AIR;player.setItemInOffHand(ItemStack.AIR)
            // Fixed hotbar leaves slot 8 empty for taking and dismantling; remaining materials use the normal inventory.
            val toolMaterials=listOf(Material.CAMPFIRE,Material.CAULDRON,Material.TRIPWIRE_HOOK,Material.SMITHING_TABLE)
            FoundryEquipment.entries.forEachIndexed { i,k->if(k !in state.equipment)player.inventory.setItemStack(i,item(toolMaterials[i],k.label,"kit:$k")) }
            if(state.coal>0)player.inventory.setItemStack(4,item(Material.COAL,"工房炭 ${state.coal}","coal",state.coal))
            val ingots=state.account.amount(WorldFoundryRules.metal)
            if(ingots>0)player.inventory.setItemStack(5,CoreLoopItems.resource(WorldFoundryRules.metal,ingots,packed).withTag(token,"metal"))
            if(state.sand>0)player.inventory.setItemStack(6,item(Material.SAND,"鋳物砂 ${state.sand}","sand",state.sand))
            player.inventory.setItemStack(if(FoundryEquipment.FAUCET in state.equipment)2 else 7,item(Material.BLAZE_ROD,"冷却回収棒 / 原型回収・砂型清掃は空手", "reclaim"))
            FoundryShape.entries.forEachIndexed { i,k->
                val slot=if((if(i==0)FoundryEquipment.HEATER else FoundryEquipment.MELTER) in state.equipment)i else 9+i
                if(state.phase!=FoundryPhase.PATTERN || state.shape!=k)player.inventory.setItemStack(slot,item(if(k==FoundryShape.ROD)Material.STICK else Material.OAK_PRESSURE_PLATE,"木製原型: ${k.label}","pattern:$k"))
                val n=state.parts[k] ?: 0
                if(n>0)player.inventory.setItemStack(18+i,item(if(k==FoundryShape.ROD)Material.IRON_NUGGET else Material.IRON_INGOT,"T2鋳造${k.label} $n / 試作部品","part:$k",n))
            }
        }
        @Synchronized private fun mutate(change:(WorldFoundryState)->WorldFoundryState):Boolean {
            if(closed || faulted)return false
            val next=try { change(state) } catch(f:IllegalArgumentException) { info(f.message ?: "条件を確認してください");return false }
            if(next===state)return false
            try { state=repository.save(state.account.revision,next) } catch(f:Exception) {
                faulted=true;System.err.println("WORLD_FOUNDRY_SAVE_FAILED owner=${player.uuid} revision=${state.account.revision}");f.printStackTrace()
                info("保存失敗。操作を停止しました。保存先を確認して再接続してください");return false
            }
            // A render failure cannot roll back a committed operation or manufacture a second output.
            runCatching { refresh() }.onFailure { System.err.println("WORLD_FOUNDRY_PROJECTION_FAILED: ${it.message}") }
            return true
        }
        @Synchronized fun interact(p:Point,face:BlockFace) {
            if(closed || faulted || ticks-lastClick<3 || player.position.distance(p)>6)return
            lastClick=ticks
            val hand=player.itemInMainHand;val id=hand.getTag(token)
            val c=runCatching { FoundryCell(p.blockX(),p.blockY(),p.blockZ()) }.getOrNull()
            val kind=state.equipment.entries.firstOrNull { it.value==c }?.key
            // Server kit ownership and empty target are rechecked, never inferred from an item tag.
            if(id?.startsWith("kit:")==true) {
                val target=runCatching { FoundryCell(p.blockX()+face.toDirection().normalX(),p.blockY()+face.toDirection().normalY(),p.blockZ()+face.toDirection().normalZ()) }.getOrNull()
                if(target!=null && instance.getBlock(target.x,target.y,target.z).isAir)mutate { WorldFoundryRules.place(it,FoundryEquipment.valueOf(id.substringAfter(':')),target) }
                else info("設備は二段まで。空いている設置面を選んでください")
                return
            }
            when(kind) {
                FoundryEquipment.HEATER -> when {
                    id=="coal"->mutate(WorldFoundryRules::fuel)
                    hand.isAir && player.isSneaking && state.fuel==0->mutate { WorldFoundryRules.remove(it,kind) }
                    hand.isAir->mutate(WorldFoundryRules::takeFuel)
                    else->info("炭を投入。空手で未使用の炭を回収")
                }
                FoundryEquipment.MELTER -> when {
                    id=="metal"->mutate(WorldFoundryRules::insert)
                    id=="reclaim" && player.isSneaking->mutate(WorldFoundryRules::reclaim)
                    hand.isAir && player.isSneaking && state.input==0 && state.liquid==0->mutate { WorldFoundryRules.remove(it,kind) }
                    hand.isAir && state.input>0->mutate(WorldFoundryRules::takeInput)
                    else->info("湯 ${state.liquid}/12単位 / 投入 ${state.input}/3 / 溶解残り ${state.meltSeconds}秒")
                }
                FoundryEquipment.FAUCET -> if(hand.isAir && player.isSneaking)mutate { WorldFoundryRules.remove(it,kind) } else mutate(WorldFoundryRules::pour)
                FoundryEquipment.TABLE -> when {
                    id=="sand"->mutate(WorldFoundryRules::sand)
                    id?.startsWith("pattern:")==true->mutate { WorldFoundryRules.imprint(it,FoundryShape.valueOf(id.substringAfter(':'))) }
                    hand.isAir && state.phase==FoundryPhase.PATTERN->mutate(WorldFoundryRules::takePattern)
                    hand.isAir && state.phase==FoundryPhase.OUTPUT->mutate(WorldFoundryRules::takePart)
                    hand.isAir && player.isSneaking && state.phase in setOf(FoundryPhase.SAND,FoundryPhase.MOLD)->mutate(WorldFoundryRules::clearMold)
                    hand.isAir && player.isSneaking->mutate { WorldFoundryRules.remove(it,kind) }
                    else->info("砂床 ${state.phase} ${state.shape?.label ?: ""} ${if(state.seconds>0)"残り${state.seconds}秒" else ""}")
                }
                null->info("設備の近くで右クリック。木製原型はインベントリから選べます")
            }
        }
        private fun display(key:String,stack:ItemStack,pos:Pos,scale:Vec=Vec(1.0,1.0,1.0)) {
            val e=displays.getOrPut(key) { Entity(EntityType.ITEM_DISPLAY).apply { setNoGravity(true);setHasPhysics(false);setInstance(this@Game.instance,pos) } }
            e.teleport(pos)
            e.editEntityMeta(ItemDisplayMeta::class.java) { m->m.setItemStack(stack);m.setDisplayContext(ItemDisplayMeta.DisplayContext.NONE);m.setScale(scale);m.setViewRange(2f) }
        }
        private fun rebuild() {
            val visible=mutableSetOf<String>()
            val occupied=state.equipment.values.toSet()
            (blocks-occupied).forEach { instance.setBlock(it.x,it.y,it.z,Block.AIR) };blocks.clear();blocks+=occupied
            state.equipment.forEach { (k,c)->instance.setBlock(c.x,c.y,c.z,if(packed)Block.BARRIER else fallback(k,state)) }
            // Models are optional visual projections. Native blocks remain the entire interaction path without a pack.
            if(packed)WorldFoundryPresentation.frame(state).forEach { v->
                visible+=v.key
                display(v.key,art(v.model,Material.PAPER),Pos(v.cell.x+.5,v.cell.y+.5,v.cell.z+.5,v.yaw,0f))
            }
            displays.entries.removeIf { (k,e)->if(k !in visible) { e.remove();true } else false }
        }
        private fun fallback(k:FoundryEquipment,s:WorldFoundryState)=when(k) {
            FoundryEquipment.HEATER->Block.FURNACE.withProperty("lit",if(s.meltSeconds>0)"true" else "false")
            FoundryEquipment.MELTER->if(s.liquid>0)Block.LAVA_CAULDRON else Block.CAULDRON
            FoundryEquipment.FAUCET->Block.IRON_BARS
            FoundryEquipment.TABLE->when(s.phase) {
                FoundryPhase.EMPTY->Block.SMITHING_TABLE
                FoundryPhase.SAND,FoundryPhase.PATTERN,FoundryPhase.MOLD->Block.SANDSTONE
                FoundryPhase.POURING->Block.ORANGE_TERRACOTTA
                FoundryPhase.COOLING->if(s.seconds>2)Block.ORANGE_TERRACOTTA else Block.LIGHT_GRAY_TERRACOTTA
                FoundryPhase.OUTPUT->Block.IRON_BLOCK
            }
        }
        @Synchronized fun tick() {
            if(closed || faulted || !player.isOnline || player.instance!==instance)return
            ticks++
            // External destruction suspends progress and restores the committed equipment; never deletes its content.
            if(ticks%20==0L) {
                if(state.equipment.values.any { instance.getBlock(it.x,it.y,it.z).isAir }) { rebuild();info("保存済み設備を復元しました。解体は空手＋しゃがみです");return }
                mutate(WorldFoundryRules::tick)
            }
        }
        @Synchronized fun close() { if(closed)return;closed=true;displays.values.forEach { it.remove() };displays.clear() }
    }
}
