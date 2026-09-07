package dev.projects.server.warden

import com.sun.net.httpserver.HttpServer
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.key.Key
import net.kyori.adventure.resource.ResourcePackInfo
import net.kyori.adventure.resource.ResourcePackRequest
import net.kyori.adventure.resource.ResourcePackStatus
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.*
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.entity.metadata.other.InteractionMeta
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.event.player.*
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import java.net.InetSocketAddress
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.*

/** Opt-in vanilla-client vertical slice in the ProjectS Minestom executable. */
object WardenArena {
    fun start() {
        val server=MinecraftServer.init(Auth.Offline())
        val instance=MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.setChunkSupplier(::LightingChunk)
        instance.setGenerator {unit->
            unit.modifier().fillHeight(0,39,Block.DEEPSLATE)
            unit.modifier().fillHeight(39,40,Block.POLISHED_DEEPSLATE)
        }
        instance.setTime(6000);instance.defaultClock()?.pause()
        for(x in -2..2)for(z in -2..2)instance.loadChunk(x,z).join()
        for(x in -17..17)for(z in -17..17) {
            val radius=hypot(x.toDouble(),z.toDouble())
            if(radius in 14.7..16.0)instance.setBlock(x,39,z,Block.CHISELED_STONE_BRICKS)
            if(abs(x)%4==0 && abs(z)%4==0 && radius<14)instance.setBlock(x,39,z,Block.CRACKED_DEEPSLATE_TILES)
            if(radius in 16.0..17.0)for(y in 40..41)instance.setBlock(x,y,z,Block.DEEPSLATE_BRICKS)
        }
        for(x in listOf(-11,11))for(z in listOf(-11,11)) {
            for(y in 40..44)instance.setBlock(x,y,z,Block.POLISHED_BASALT)
            instance.setBlock(x,45,z,Block.SOUL_LANTERN)
        }
        val arena=Runtime(instance,WardenAsset.load())
        arena.register()
        MinecraftServer.getSchedulerManager().buildShutdownTask {arena.close()}
        java.lang.Runtime.getRuntime().addShutdownHook(Thread({MinecraftServer.stopCleanly()},"warden-shutdown"))
        val port=Integer.getInteger("projects.warden.port",25575)
        server.start("127.0.0.1",port)
        println("WARDEN_READY minecraft=26.2 address=127.0.0.1:$port bones=${arena.asset.bones.size} clips=${arena.asset.clips.size}")
    }

    private class Runtime(val instance:InstanceContainer,val asset:WardenAsset) : AutoCloseable {
        val fight=WardenFight(asset)
        val actors=ConcurrentHashMap<UUID,Actor>()
        val pack=PackHost()
        val displays=asset.bones.mapIndexedNotNull {i,b->if(!b.visible)null else i to Entity(EntityType.ITEM_DISPLAY).apply {
            setNoGravity(true);setHasPhysics(false)
            editEntityMeta(ItemDisplayMeta::class.java) {m->
                m.setItemStack(ItemStack.of(Material.PAPER).with(DataComponents.ITEM_MODEL,"projects:warden/${b.name}"))
                m.setDisplayContext(ItemDisplayMeta.DisplayContext.NONE);m.setScale(Vec(2.0,2.0,2.0))
                m.setTransformationInterpolationDuration(1);m.setPosRotInterpolationDuration(0)
                m.setViewRange(2f);m.setWidth(40f);m.setHeight(12f);m.setShadowRadius(0f)
            }
            setInstance(this@Runtime.instance,Pos(0.0,40.0,0.0)).join()
        }}
        val body=Entity(EntityType.INTERACTION).apply {
            setNoGravity(true);setHasPhysics(false)
            editEntityMeta(InteractionMeta::class.java){m->m.setWidth(1.3f);m.setHeight(2.8f);m.setResponse(true)}
            setInstance(this@Runtime.instance,Pos(0.0,40.0,0.0)).join()
        }
        val bar=BossBar.bossBar(Component.text("灰燼の番人"),1f,BossBar.Color.GREEN,BossBar.Overlay.PROGRESS)
        var tick=0L;var sequence= -1;var closed=false
        data class Actor(val player:Player,var loaded:Boolean=false,var hp:Double=100.0,var attackAt:Long=0,var attackReady:Long=0,
            var facing:V3=V3.ZERO,var dodgeUntil:Long=0,var dodgeReady:Long=0,var sneak:Boolean=false,var healReady:Long=0,var defeated:Boolean=false)

        fun register() {
            val events=MinecraftServer.getGlobalEventHandler()
            events.addListener(AsyncPlayerConfigurationEvent::class.java) {e->e.spawningInstance=instance;e.player.respawnPoint=Pos(0.0,40.0,7.0,180f,0f)}
            events.addListener(PlayerSpawnEvent::class.java) {e->
                if(!e.isFirstSpawn)return@addListener
                val p=e.player;actors[p.uuid]=Actor(p);p.gameMode=GameMode.ADVENTURE;p.food=20;p.foodSaturation=20f
                p.inventory.setItemStack(0,ItemStack.of(Material.IRON_SWORD).withCustomName(Component.text("番人に挑む剣")))
                p.sendMessage(Component.text("灰燼の番人｜左クリック：斬撃 ／ Shift：回避 ／ /mend：回復 ／ /fight：開始・再戦"))
                p.sendResourcePacks(ResourcePackRequest.resourcePackRequest().packs(pack.info).required(true).replace(false)
                    .prompt(Component.text("灰燼の番人のモデルとピクセルテクスチャを読み込みます。")).build())
                p.showBossBar(bar)
            }
            events.addListener(PlayerResourcePackStatusEvent::class.java) {e->
                if(e.packUuid!=pack.info.id())return@addListener
                actors[e.player.uuid]?.let {a->
                    a.loaded=e.status==ResourcePackStatus.SUCCESSFULLY_LOADED
                    if(a.loaded)e.player.sendMessage(Component.text("準備完了。/fight で戦闘を開始できます。"))
                }
            }
            events.addListener(PlayerDisconnectEvent::class.java){e->actors.remove(e.player.uuid);e.player.hideBossBar(bar)}
            events.addListener(PlayerHandAnimationEvent::class.java){e->if(e.hand==PlayerHand.MAIN)attack(e.player)}
            events.addListener(EntityAttackEvent::class.java){e->(e.entity as? Player)?.let(::attack)}
            events.addListener(InstanceTickEvent::class.java){e->if(e.instance===instance)tick()}
            val begin=Command("fight");begin.setDefaultExecutor {s,_->
                val p=s as? Player?:return@setDefaultExecutor;val a=actors[p.uuid]?:return@setDefaultExecutor
                if(!a.loaded){p.sendMessage(Component.text("Resource Packの読み込みを待ってください。"));return@setDefaultExecutor}
                if(fight.running && !fight.finished){p.sendMessage(Component.text("戦闘中です。敗北後または討伐後に再戦できます。"));return@setDefaultExecutor}
                actors.values.forEach {it.hp=100.0;it.defeated=false;it.attackAt=0;it.dodgeUntil=0;it.player.health=20f;it.player.teleport(Pos(0.0,40.0,7.0,180f,0f))}
                fight.begin();announce("灰燼の番人","刃を見極め、振り抜いた隙を狙え")
            };MinecraftServer.getCommandManager().register(begin)
            val mend=Command("mend");mend.setDefaultExecutor {s,_->
                val p=s as? Player?:return@setDefaultExecutor;val a=actors[p.uuid]?:return@setDefaultExecutor
                if(a.defeated || tick<a.healReady)return@setDefaultExecutor
                a.hp=min(100.0,a.hp+35);a.healReady=tick+400;p.sendMessage(Component.text("体力を35回復しました（再使用20秒）。"))
            };MinecraftServer.getCommandManager().register(mend)
            render(fight.worldPose())
        }
        fun attack(p:Player) {
            val a=actors[p.uuid]?:return
            if(!a.loaded || a.defeated || !fight.running || fight.dead || tick<a.attackReady || tick<a.dodgeUntil || p.itemInMainHand.material()!=Material.IRON_SWORD)return
            a.attackReady=tick+14;a.attackAt=tick+5
            val d=p.position.direction();a.facing=V3(d.x(),d.y(),d.z()).unit()
            sound("entity.player.attack.sweep",playerPos(p),.7f,1.1f)
        }
        fun playerPos(p:Player)=V3(p.position.x(),p.position.y()-40,p.position.z())
        fun render(poses:List<BonePose>) {
            displays.forEach {(i,e)->val p=poses[i];e.editEntityMeta(ItemDisplayMeta::class.java){m->
                m.setTranslation(Vec(p.p.x,p.p.y,p.p.z));m.setLeftRotation(p.q.array());m.setTransformationInterpolationStartDelta(0)
            }}
        }
        fun tick() {
            if(closed)return
            tick++
            val available=actors.values.filter {it.loaded && !it.defeated && it.player.isOnline && it.player.instance===instance}
            available.forEach {a->
                val p=a.player;val pos=playerPos(p)
                if(pos.y< -2 || hypot(pos.x,pos.z)>16.2 || pos.y>10) {a.hp=0.0;defeat(a)}
                val sneak=p.isSneaking
                if(sneak && !a.sneak && tick>=a.dodgeReady && fight.running && !a.defeated){
                    a.dodgeReady=tick+28;a.dodgeUntil=tick+7;a.attackAt=0
                    val v=p.position.direction();val dir=V3(v.x(),0.0,v.z()).unit()
                    p.velocity=Vec(dir.x*10,1.5,dir.z*10);sound("entity.player.attack.knockback",pos,.5f,1.5f)
                }
                a.sneak=sneak
                if(a.attackAt==tick && !a.defeated){
                    a.attackAt=0;val origin=playerPos(p)+V3(0.0,1.3,0.0);val end=origin+a.facing*3.4
                    val center=fight.position
                    // The received entity id is never used to select a victim.
                    if(segmentBoxDistanceSquared(origin,end,center+V3(-.65,.0,-.55),center+V3(.65,2.8,.55))<=.12*.12 && fight.damage(if(fight.recovery)22.0 else 16.0)) {
                        particles(Particle.CRIT,center+V3(0.0,1.5,0.0),10);sound("entity.iron_golem.hurt",center,.8f,.7f)
                    }
                }
                p.health=(a.hp/5).toFloat().coerceIn(1f,20f)
                if(tick%4==0L)p.sendActionBar(Component.text("体力 ${a.hp.toInt()}/100  |  回避 ${if(tick>=a.dodgeReady)"準備完了" else "${(a.dodgeReady-tick+19)/20}秒"}  |  ${label()}"))
            }
            val target=available.filter {!it.defeated}.minByOrNull {(playerPos(it.player)-fight.position).length()}
            if(available.none {!it.defeated} && fight.running && !fight.dead)fight.reset()
            // Evaluate the interval sent last tick, which has now finished interpolating.
            // New transforms are sent below, after this interval's damage and trail.
            if(sequence!=fight.sequence) {
                sequence=fight.sequence
                when(fight.action) {
                    "heavy_slash"->sound("block.anvil.land",fight.position,.45f,.55f)
                    "dash"->sound("entity.ravager.ambient",fight.position,.8f,.7f)
                    "phase_transition"->announce("封印崩壊","番人の攻勢が激しくなる")
                    "death"->{announce("灰燼の番人 討伐","/fight で再戦");sound("entity.wither.death",fight.position,.55f,.7f)}
                }
            }
            if(fight.active) {
                // Trail and damage share these exact blade poses and active ticks.
                for(s in 0..3) {
                    val pose=fight.previousWeapon.lerp(fight.currentWeapon,s/3.0)
                    for(n in 0..12)particles(Particle.SOUL_FIRE_FLAME,pose.point(V3(0.0,0.0,.44+n*2.2/12)))
                }
                if(fight.frame==fight.clip.activeStart)sound("entity.player.attack.sweep",fight.position,1f,.55f)
                available.filter {!it.defeated && tick>=it.dodgeUntil}.forEach {a->
                    val pos=playerPos(a.player)
                    if(sweptBladeHit(fight.previousWeapon,fight.currentWeapon,pos+V3(-.3,0.0,-.3),pos+V3(.3,1.8,.3)) && fight.claimHit(a.player.uuid)) {
                        val damage=when(fight.action){"heavy_slash"->34.0;"dash"->27.0;else->20.0}*(if(fight.phase==2)1.15 else 1.0)
                        a.hp=max(0.0,a.hp-damage);val away=(pos-fight.position).unit();a.player.velocity=Vec(away.x*5,2.0,away.z*5)
                        sound("entity.player.hurt",pos,.8f,.8f);if(a.hp<=0)defeat(a)
                    }
                }
            } else if(fight.clip.activeStart>0 && fight.frame in 3 until fight.clip.activeStart && tick%2==0L) {
                particles(Particle.END_ROD,fight.currentWeapon.point(V3(0.0,0.0,2.64)),2)
            }
            if(fight.action=="phase_transition" && fight.frame in 28..40) {
                val center=fight.worldPose()[asset.chest].p
                repeat(12){n->val a=n*PI/6;particles(Particle.SOUL_FIRE_FLAME,center+V3(cos(a),.1,sin(a))*(.2+(fight.frame-28)*.10))}
                if(fight.frame==28)sound("entity.warden.roar",fight.position,.7f,.6f)
            }
            if(fight.action=="walk" && fight.frame%16==0)sound("block.deepslate.step",fight.position,.8f,.65f)
            if(fight.dead && fight.frame==32)particles(Particle.SMOKE,fight.position+V3(0.0,.7,0.0),35)
            fight.tick(target?.takeUnless {it.defeated}?.let {playerPos(it.player)})
            render(fight.worldPose());body.teleport(Pos(fight.position.x,40.0,fight.position.z))
            bar.progress((fight.health/360).toFloat());bar.name(Component.text("灰燼の番人  ${fight.health.toInt()}/360  第${fight.phase}形態"))
            bar.color(if(fight.phase==2)BossBar.Color.PURPLE else BossBar.Color.GREEN)
            val smoke=Integer.getInteger("projects.warden.smokeTicks",0)
            if(smoke>0 && tick>=smoke) {
                check(displays.all {it.second.instance===instance && !it.second.isRemoved});check(pack.bytes.isNotEmpty())
                println("WARDEN_SERVER_SMOKE_PASS ticks=$tick displays=${displays.size} packBytes=${pack.bytes.size}")
                MinecraftServer.stopCleanly()
            }
        }
        fun label()=when(fight.action){"slash_01"->if(fight.recovery)"斬撃後の隙" else "横薙ぎ";"heavy_slash"->if(fight.recovery)"叩き斬り後の隙" else "叩き斬り";"dash"->"踏み込み斬り";"phase_transition"->"形態移行";"death"->"討伐";else->if(fight.running)"交戦中" else "/fight で開始"}
        fun defeat(a:Actor){if(a.defeated)return;a.defeated=true;a.hp=0.0;a.attackAt=0;a.player.sendMessage(Component.text("力尽きた…。全員の戦闘終了後、/fight で再戦できます。"));a.player.teleport(Pos(0.0,40.0,13.0,180f,0f))}
        fun announce(title:String,sub:String){actors.values.forEach {it.player.showTitle(Title.title(Component.text(title),Component.text(sub)))}}
        fun sound(id:String,pos:V3,volume:Float,pitch:Float){instance.players.forEach {it.playSound(Sound.sound(Key.key("minecraft:$id"),Sound.Source.HOSTILE,volume,pitch),pos.x,pos.y+40,pos.z)}}
        fun particles(type:Particle,pos:V3,count:Int=1){instance.sendGroupedPacket(ParticlePacket(type,Pos(pos.x,pos.y+40,pos.z),Vec.ZERO,0f,count))}
        override fun close(){if(closed)return;closed=true;actors.values.forEach {it.player.hideBossBar(bar)};actors.clear();displays.forEach {it.second.remove()};body.remove();pack.close()}
    }

    private class PackHost : AutoCloseable {
        val bytes=requireNotNull(javaClass.getResourceAsStream("/ashen-warden/warden-pack.zip")).use {it.readAllBytes()}
        val hash=MessageDigest.getInstance("SHA-1").digest(bytes).joinToString(""){"%02x".format(it)}
        val http=HttpServer.create(InetSocketAddress("127.0.0.1",Integer.getInteger("projects.warden.packPort",25576)),8)
        val pool=Executors.newFixedThreadPool(2){r->Thread(r,"warden-pack").apply {isDaemon=true}}
        val info:ResourcePackInfo
        init {
            http.executor=pool;val path="/warden-$hash.zip"
            http.createContext(path){e->try {
                if(e.requestURI.path!=path || e.requestMethod !in listOf("GET","HEAD"))e.sendResponseHeaders(404,-1)
                else {e.responseHeaders.set("Content-Type","application/zip");e.responseHeaders.set("Content-Length",bytes.size.toString())
                    if(e.requestMethod=="HEAD")e.sendResponseHeaders(200,-1) else {e.sendResponseHeaders(200,bytes.size.toLong());e.responseBody.use {it.write(bytes)}}}
            }finally {e.close()}}
            http.start();info=ResourcePackInfo.resourcePackInfo(UUID.nameUUIDFromBytes(hash.toByteArray()),URI("http://127.0.0.1:${http.address.port}$path"),hash)
            println("WARDEN_PACK_READY url=${info.uri()} sha1=$hash")
        }
        override fun close(){http.stop(0);pool.shutdownNow()}
    }
}
