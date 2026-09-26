package dev.projects.webui

import net.kyori.adventure.text.Component
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.sound.SoundStop
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.RelativeFlags
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerPacketEvent
import net.minestom.server.network.packet.client.play.*
import net.minestom.server.network.packet.server.play.CameraPacket
import net.minestom.server.network.packet.server.play.ChangeGameStatePacket
import net.minestom.server.network.packet.server.play.PlayerPositionAndLookPacket
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Captures packets only while a player is viewing a display UI, in the lab or at the harbor smith. */
class UiSessions(
    events: EventNode<Event>, private val path: Path?,
    private val packReady: (Player) -> Boolean = { true },
    private val polishScene: Polish05Scene? = null,
    private val flowFactory: ((Player) -> ForgeUiFlow)? = null,
    private val cameraOrigin: (Player) -> Pos = { it.position.add(0.0,it.eyeHeight+4.0,0.0).withView(0f,0f) },
) : AutoCloseable {
    private data class Input(val due: Long, val generation: Int, val yaw: Float?=null, val pitch: Float?=null, val action: String?=null)
    private data class Voice(val cue:String,val until:Long)
    private class Session(val player: Player, val saved: Pos, var document: UiDocument?, val camera: Entity, val renderer: UiRenderer,
                          sceneBuilder: Polish05Scene?, factory: ((Player) -> ForgeUiFlow)?) {
        val demo=ForgeDemo()
        val polish: ForgeUiFlow?=factory?.invoke(player) ?: sceneBuilder?.let(::Polish05Flow)
        val effects=if(polish!=null)Polish05Effects() else null
        var light=ForgeLightPhase.IDLE
        val pointer=UiPointer()
        var scene=polish?.scene()?:requireNotNull(document).layout(demo.values(),demo.flags())
        val pending=ConcurrentHashMap<Int,Long>()
        val queue=ArrayDeque<Input>()
        @Volatile var active=false
        var generation=0
        var hover: String?=null
        var lastClick=0L
        var lastSlot=player.heldSlot.toInt()
        var lastResponse=System.nanoTime()
        var recenter=false
        @Volatile var probeError: Exception?=null
        val voices=ArrayDeque<Voice>()
        var lastClickSound=0L
    }
    private data class Retired(val pending: MutableSet<Int>,val expires: Long)
    private val retired=ConcurrentHashMap<UUID,Retired>()
    private val sessions=ConcurrentHashMap<UUID,Session>()
    private val ids=AtomicInteger(-10_000)
    private val sampler=Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task,"polish05-pointer-probe").apply { isDaemon=true }
    }
    val sessionCount get() = sessions.size
    val entityCount get() = sessions.values.sumOf { it.renderer.size+1 }
    init {
        // A production server ticks at 20 Hz. Probe the external-camera mouse
        // between ticks so the pointer can receive denser input without delaying hits.
        sampler.scheduleAtFixedRate({
            sessions.values.forEach { s ->
                try {
                    synchronized(s) {
                        if(s.active && s.probeError==null && sessions[s.player.uuid]===s) sample(s)
                    }
                } catch(ex: Exception) {
                    s.probeError=ex
                }
            }
        },0,16_666_667,TimeUnit.NANOSECONDS)
        events.addListener(PlayerPacketEvent::class.java,::packet)
        events.addListener(PlayerDisconnectEvent::class.java) { close(it.player,false);retired.remove(it.player.uuid) }
        events.addListener(InstanceTickEvent::class.java) { e -> sessions.values.filter { it.player.instance===e.instance }.forEach { s ->
            try { synchronized(s) { if(sessions[s.player.uuid]===s) tick(s) } } catch(ex: Exception) {
                close(s.player)
                s.player.sendMessage(Component.text("UIを安全に終了しました：${ex.message?.take(160)}"))
                ex.printStackTrace()
            }
        } }
    }
    fun open(player: Player) {
        if(sessions.containsKey(player.uuid)) return
        if(!packReady(player)) {
            player.sendMessage(Component.text("工房UIの素材を読み込んでから、もう一度話しかけてください。"))
            return
        }
        val document=if(flowFactory==null) UiDocument.parse(Files.readString(requireNotNull(path))) else null
        // Compile and validate before changing the camera or creating any entity.
        if(document!=null) { val initial=ForgeDemo(); document.layout(initial.values(),initial.flags()) }
        // This isolated lab is a flat world: lift the presentation plane clear of its floor.
        // Eye-level placement lets the lower buttons intersect blocks at larger UI scales.
        val origin=cameraOrigin(player)
        val camera=Entity(EntityType.TEXT_DISPLAY).apply {
            setHasPhysics(false);setNoGravity(true);setAutoViewable(false)
            (entityMeta as TextDisplayMeta).apply {
                setText(Component.empty());setBackgroundColor(0);setUseDefaultBackground(false)
            }
        }
        val s=Session(player,player.position,document,camera,UiRenderer(player,origin),polishScene,flowFactory)
        // At Vanilla 26.2's presentation FOV, 1.0 crops the approved 1440×920
        // stage on a 1920×1080 client. 0.8 matches the HTML's 1080px-fit scale.
        if(s.polish!=null)s.renderer.zoom=0.8
        sessions[player.uuid]=s
        camera.setInstance(player.instance!!,origin).whenComplete { _,error ->
            if(error!=null || sessions[player.uuid]!==s) {
                camera.remove()
                if(sessions[player.uuid]===s) close(player)
            }
            else {
                try {
                    camera.addViewer(player)
                    s.renderer.render(s.scene,null,s.pointer)
                    // Only the client's presentation changes. The authoritative player stays Adventure.
                    player.sendPacket(ChangeGameStatePacket(ChangeGameStatePacket.Reason.CHANGE_GAMEMODE,3f))
                    player.sendPacket(CameraPacket(camera.entityId))
                    sample(s,true)
                } catch(ex: Exception) { close(player);player.sendMessage(Component.text("UIを表示できません：${ex.message?.take(160)}")) }
            }
        }
    }
    fun close(player: Player, restore: Boolean=true, teleportBack: Boolean=true) {
        val s=sessions[player.uuid]?:return
        synchronized(s) {
            if(!sessions.remove(player.uuid,s)) return
        // Replies to already sent sample packets may arrive after the menu has closed.
        val old=retired[player.uuid]
        retired[player.uuid]=Retired(((old?.pending?:emptySet())+s.pending.keys).toList().takeLast(64).toMutableSet(),System.nanoTime()+5_000_000_000L)
        if(restore) {
            player.sendPacket(CameraPacket(player.entityId))
            player.sendPacket(ChangeGameStatePacket(ChangeGameStatePacket.Reason.CHANGE_GAMEMODE,player.gameMode.ordinal.toFloat()))
            player.setHeldItemSlot(player.heldSlot)
            if(teleportBack) player.teleport(s.saved)
        }
        s.queue.clear();s.pending.clear();s.renderer.close();s.camera.remove()
        if(s.polish!=null) POLISH_SOUNDS.forEach { player.stopSound(SoundStop.named(Key.key("projects_ui_polish05:ui.$it"))) }
        }
    }
    private fun sample(s: Session, reset: Boolean=false) {
        if(s.pending.size>=12) return
        val id=ids.getAndDecrement()
        s.pending[id]=System.nanoTime()
        if(reset) { s.pointer.reset();s.recenter=true }
        // External cameras suppress natural LOOK updates. Relative zero sync requests a vanilla PosRot reply.
        val flags=RelativeFlags.COORD or RelativeFlags.DELTA_COORD or if(reset) 0 else RelativeFlags.VIEW
        s.player.sendPacket(PlayerPositionAndLookPacket(id,Vec.ZERO,Vec.ZERO,0f,0f,flags))
    }
    private fun enqueue(s: Session, yaw: Float?=null, pitch: Float?=null, action: String?=null) {
        if(!s.active || s.recenter) return
        if(s.queue.size>=64) return
        s.queue.addLast(Input(System.nanoTime()+s.demo.delayMs*1_000_000L,s.generation,yaw,pitch,action))
        // PlayerPacketEvent is already dispatched by Minestom on the player's tick thread.
        // Do not wait for a second InstanceTickEvent when no artificial latency was requested.
        if(s.demo.delayMs==0) { drain(s,System.nanoTime()); if(sessions[s.player.uuid]===s) paintPointer(s) }
    }
    private fun packet(event: PlayerPacketEvent) {
        val old=retired[event.player.uuid]
        if(old!=null) {
            if(System.nanoTime()>old.expires) retired.remove(event.player.uuid)
            else when(val packet=event.packet) {
                is ClientTeleportConfirmPacket -> if(old.pending.remove(packet.teleportId())) {
                    event.isCancelled=true;return
                }
                else -> Unit
            }
        }
        val s=sessions[event.player.uuid]?:return
        synchronized(s) {
        if(sessions[event.player.uuid]!==s) return
        when(val packet=event.packet) {
            is ClientTeleportConfirmPacket -> if(packet.teleportId()<0) {
                event.isCancelled=true
                if(s.pending.remove(packet.teleportId())!=null) { s.active=true;s.lastResponse=System.nanoTime() }
            }
            is ClientPlayerPositionAndRotationPacket -> {
                event.isCancelled=true
                rotation(s,packet.position().yaw(),packet.position().pitch())
            }
            is ClientPlayerRotationPacket -> { event.isCancelled=true;rotation(s,packet.yaw(),packet.pitch()) }
            is ClientPlayerPositionPacket, is ClientPlayerPositionStatusPacket, is ClientVehicleMovePacket -> event.isCancelled=true
            is ClientUseItemPacket -> {
                event.isCancelled=true
                if(packet.hand()==net.minestom.server.entity.PlayerHand.MAIN) {
                    rotation(s,packet.yaw(),packet.pitch());enqueue(s,action="click")
                }
            }
            // 26.2 spectator rendering sends this for LEFT click, including clicks on empty space.
            is ClientSpectatorActionPacket -> { event.isCancelled=true;enqueue(s,action="click") }
            is ClientPlayerBlockPlacementPacket, is ClientInteractEntityPacket -> { event.isCancelled=true; enqueue(s,action="click") }
            is ClientAnimationPacket, is ClientAttackPacket -> { event.isCancelled=true;enqueue(s,action="click") }
            is ClientHeldItemChangePacket -> {
                event.isCancelled=true
                val slot=packet.slot().toInt()
                if(slot in 0..8 && slot!=s.lastSlot) {
                    enqueue(s,action=if((slot-s.lastSlot+9)%9<=4) "page:next" else "page:prev")
                    s.lastSlot=slot
                }
            }
            is ClientInputPacket -> {
                event.isCancelled=true
                if(packet.shift()) close(s.player)
            }
            is ClientPlayerActionPacket, is ClientCreativeInventoryActionPacket,
            is ClientClickWindowPacket, is ClientPlayerAbilitiesPacket -> event.isCancelled=true
            else -> Unit
        }
        }
    }
    /** Preserve the probe's confirm/rotation order on Minestom's socket thread.
     * Other packets remain on the normal 20 Hz gameplay path. */
    fun consumeImmediateUiPacket(player: Player, packet: net.minestom.server.network.packet.client.ClientPacket): Boolean {
        val s=sessions[player.uuid]?:return false
        synchronized(s) {
            if(sessions[player.uuid]!==s) return false
            when(packet) {
                is ClientTeleportConfirmPacket -> {
                    if(packet.teleportId()>=0) return false
                    if(s.pending.remove(packet.teleportId())!=null) { s.active=true;s.lastResponse=System.nanoTime() }
                }
                is ClientPlayerRotationPacket -> rotation(s,packet.yaw(),packet.pitch())
                is ClientPlayerPositionAndRotationPacket -> rotation(s,packet.position().yaw(),packet.position().pitch())
                else -> return false
            }
            return true
        }
    }
    private fun rotation(s: Session,yaw: Float,pitch: Float) {
        if(!s.active || !yaw.isFinite() || !pitch.isFinite()) return
        if(s.recenter) {
            // The first reply establishes the new camera baseline, regardless
            // of its absolute angle. Waiting for exactly 0 can freeze input.
            s.pointer.reset();s.pointer.move(yaw,pitch,s.scene.width,s.scene.height);s.recenter=false
            return
        }
        enqueue(s,yaw,pitch)
    }
    private fun tick(s: Session) {
        if(s.probeError!=null) {
            close(s.player)
            s.player.sendMessage(Component.text("UIの入力を更新できなかったため終了しました。もう一度開いてください。"))
            return
        }
        if(s.player.instance!==s.camera.instance || s.camera.isRemoved) { close(s.player,teleportBack=false);return }
        if(s.polish!=null && !packReady(s.player)) { close(s.player);return }
        val now=System.nanoTime()
        if(now-s.lastResponse>5_000_000_000L) {
            close(s.player);s.player.sendMessage(Component.text("UIの応答が途切れたため終了しました。/ui で再度開けます。"));return
        }
        if(s.polish?.takeStrike()==true) sound(s,"refine_strike")
        val receipt=s.polish?.tick(System.currentTimeMillis())
        if(receipt!=null) {
            if(!receipt.refined)
                s.effects?.finish(receipt.success)
            sound(s,when {
                receipt.refined -> "refine_success"
                receipt.success -> "enhance_success"
                else -> "enhance_fail"
            })
            s.light=s.effects?.phase()?:ForgeLightPhase.IDLE
            s.scene=s.polish.scene(s.light)
            s.renderer.render(s.scene,s.hover,s.pointer)
        }
        drain(s,now)
        if(sessions[s.player.uuid]===s) {
            if(s.polish!=null && s.effects!=null) {
                if(s.polish.view=="forge") {
                    val phase=s.effects.phase()
                    if(phase!=s.light || flowFactory!=null) {
                        s.light=phase
                        s.scene=s.polish.scene(phase)
                    }
                    s.renderer.render(s.effects.frame(s.scene),s.hover,s.pointer)
                } else s.effects.clear()
            }
            paintPointer(s)
        }
        retired.entries.removeIf { now>it.value.expires }
    }
    private fun drain(s: Session,now: Long) {
        while(s.queue.isNotEmpty() && s.queue.first().due<=now) {
            val input=s.queue.removeFirst()
            if(input.generation!=s.generation) continue
            if(input.yaw!=null) s.pointer.move(input.yaw,input.pitch!!,s.scene.width,s.scene.height)
            if(input.action!=null) {
                var action=input.action
                if(action=="click") {
                    if(now-s.lastClick<if(s.polish!=null)70_000_000L else 220_000_000L) continue
                    s.lastClick=now
                    val hit=s.scene.hit(s.pointer.x,s.pointer.y)
                    if(hit==null || !hit.enabled) continue
                    action=hit.action!!
                }
                if(action=="close") { close(s.player);return }
                if(s.polish!=null) {
                    val oldMuted=s.polish.muted
                    if(!s.polish.action(action)) continue
                    if(action=="sound" && !oldMuted && s.polish.muted) {
                        POLISH_SOUNDS.forEach { s.player.stopSound(SoundStop.named(Key.key("projects_ui_polish05:ui.$it"))) }
                    } else if(action=="confirm" && s.polish.operationActive) {
                        if(s.polish.view=="forge") {
                            s.effects?.beginStrike()
                            sound(s,"enhance_prepare")
                        }
                    } else if(action!="sound") {
                        sound(s,when {
                            action.startsWith("select:") || action.startsWith("bag:") -> "select"
                            action=="cancel" || action.startsWith("view:") -> "back"
                            else -> "click"
                        })
                    }
                    s.generation++
                    s.light=s.effects?.phase()?:ForgeLightPhase.IDLE
                    s.scene=s.polish.scene(s.light)
                    s.hover=s.scene.hit(s.pointer.x,s.pointer.y)?.id
                    s.renderer.render(s.scene,s.hover,s.pointer)
                    continue
                }
                if(action.startsWith("page:") && s.demo.tab!="catalog") continue
                if(action=="reload") {
                    try {
                        val candidate=UiDocument.parse(Files.readString(requireNotNull(path)))
                        candidate.layout(s.demo.values(),s.demo.flags())
                        s.document=candidate
                    } catch(e: Exception) {
                        s.player.sendMessage(Component.text("再読込できません。前の画面を維持します：${e.message?.take(160)}"));continue
                    }
                } else if(!s.demo.action(action)) continue
                s.generation++
                s.scene=requireNotNull(s.document).layout(s.demo.values(),s.demo.flags())
                s.renderer.zoom=s.demo.zoom
                s.hover=s.scene.hit(s.pointer.x,s.pointer.y)?.id
                s.renderer.render(s.scene,s.hover,s.pointer)
            }
        }
    }
    private fun paintPointer(s: Session) {
        val hover=s.scene.hit(s.pointer.x,s.pointer.y)?.id
        if(hover!=s.hover) { s.renderer.hover(s.scene,s.hover,hover);s.hover=hover }
        s.renderer.cursor(s.pointer)
    }
    private fun sound(s:Session,cue:String) {
        if(s.polish?.muted!=false)return
        val now=System.nanoTime()
        s.voices.removeIf { it.until<=now }
        if(s.voices.size>=4) {
            if(cue in setOf("click","select","back","equip"))return
            val oldest=s.voices.removeFirst()
            s.player.stopSound(SoundStop.named(Key.key("projects_ui_polish05:ui.${oldest.cue}")))
        }
        if(cue in setOf("click","select","back","equip") && now-s.lastClickSound<70_000_000L)return
        if(cue in setOf("click","select","back","equip"))s.lastClickSound=now
        val durationMs=when(cue) {
            "enhance_success" -> 2250L
            "enhance_success_radiant" -> 2650L
            "enhance_fail" -> 1480L
            "enhance_prepare" -> 650L
            "refine_success" -> 390L
            "refine_strike" -> 270L
            else -> 130L
        }
        s.voices.addLast(Voice(cue,now+durationMs*1_000_000L))
        s.player.playSound(Sound.sound(Key.key("projects_ui_polish05:ui.$cue"),Sound.Source.MASTER,0.35f,1f))
    }
    private companion object {
        val POLISH_SOUNDS=listOf("click","select","back","equip","enhance_prepare","enhance_success",
            "enhance_success_radiant","enhance_fail","refine_strike","refine_success")
    }
    override fun close() {
        sampler.shutdownNow()
        sessions.values.toList().forEach { close(it.player) }
    }
}
