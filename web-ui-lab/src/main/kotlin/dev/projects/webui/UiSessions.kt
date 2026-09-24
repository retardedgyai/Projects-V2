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
import java.util.concurrent.atomic.AtomicInteger

/** Only installed in the separate UI lab server. Never intercepts normal ProjectS gameplay packets. */
class UiSessions(
    events: EventNode<Event>, private val path: Path,
    private val packReady: (Player) -> Boolean = { true },
    private val polishScene: Polish05Scene? = null,
) : AutoCloseable {
    private data class Input(val due: Long, val generation: Int, val yaw: Float?=null, val pitch: Float?=null, val action: String?=null)
    private data class Voice(val cue:String,val until:Long)
    private class Session(val player: Player, val saved: Pos, var document: UiDocument, val camera: Entity, val renderer: UiRenderer,
                          sceneBuilder: Polish05Scene?) {
        val demo=ForgeDemo()
        val polish=sceneBuilder?.let(::Polish05Flow)
        val pointer=UiPointer()
        var scene=polish?.scene()?:document.layout(demo.values(),demo.flags())
        val pending=mutableMapOf<Int,Long>()
        val queue=ArrayDeque<Input>()
        var active=false
        var generation=0
        var hover: String?=null
        var lastClick=0L
        var lastSlot=player.heldSlot.toInt()
        var lastResponse=System.nanoTime()
        var recenter=false
        val voices=ArrayDeque<Voice>()
        var lastClickSound=0L
    }
    private data class Retired(val pending: MutableSet<Int>,val expires: Long)
    private val retired=mutableMapOf<UUID,Retired>()
    private val sessions=ConcurrentHashMap<UUID,Session>()
    private val ids=AtomicInteger(-10_000)
    val sessionCount get() = sessions.size
    val entityCount get() = sessions.values.sumOf { it.renderer.size+1 }
    init {
        events.addListener(PlayerPacketEvent::class.java,::packet)
        events.addListener(PlayerDisconnectEvent::class.java) { close(it.player,false);retired.remove(it.player.uuid) }
        events.addListener(InstanceTickEvent::class.java) { e -> sessions.values.filter { it.player.instance===e.instance }.forEach { s ->
            try { tick(s) } catch(ex: Exception) {
                close(s.player)
                s.player.sendMessage(Component.text("UIを安全に終了しました：${ex.message?.take(160)}"))
                ex.printStackTrace()
            }
        } }
    }
    fun open(player: Player) {
        if(sessions.containsKey(player.uuid)) return
        if(!packReady(player)) {
            player.sendMessage(Component.text("Polish05素材の読込完了後に /ui で開いてください。"))
            return
        }
        val document=UiDocument.parse(Files.readString(path))
        // Compile and validate before changing the camera or creating any entity.
        val initial=ForgeDemo(); document.layout(initial.values(),initial.flags())
        // This isolated lab is a flat world: lift the presentation plane clear of its floor.
        // Eye-level placement lets the lower buttons intersect blocks at larger UI scales.
        val origin=player.position.add(0.0,player.eyeHeight+4.0,0.0).withView(0f,0f)
        val camera=Entity(EntityType.TEXT_DISPLAY).apply {
            setHasPhysics(false);setNoGravity(true);setAutoViewable(false)
            (entityMeta as TextDisplayMeta).apply {
                setText(Component.empty());setBackgroundColor(0);setUseDefaultBackground(false)
            }
        }
        val s=Session(player,player.position,document,camera,UiRenderer(player,origin),polishScene)
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
    fun close(player: Player, restore: Boolean=true) {
        val s=sessions.remove(player.uuid)?:return
        // Replies to already sent sample packets may arrive after the menu has closed.
        val old=retired[player.uuid]
        retired[player.uuid]=Retired(((old?.pending?:emptySet())+s.pending.keys).toList().takeLast(64).toMutableSet(),System.nanoTime()+5_000_000_000L)
        if(restore) {
            player.sendPacket(CameraPacket(player.entityId))
            player.sendPacket(ChangeGameStatePacket(ChangeGameStatePacket.Reason.CHANGE_GAMEMODE,player.gameMode.ordinal.toFloat()))
            player.setHeldItemSlot(player.heldSlot)
            player.teleport(s.saved)
        }
        s.queue.clear();s.pending.clear();s.renderer.close();s.camera.remove()
        if(s.polish!=null) POLISH_SOUNDS.forEach { player.stopSound(SoundStop.named(Key.key("projects_ui_polish05:ui.$it"))) }
    }
    private fun sample(s: Session, reset: Boolean=false) {
        if(s.pending.size>=8) return
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
    private fun rotation(s: Session,yaw: Float,pitch: Float) {
        if(!s.active || !yaw.isFinite() || !pitch.isFinite()) return
        if(s.recenter) {
            if(kotlin.math.abs(yaw)>0.1 || kotlin.math.abs(pitch)>0.1) return
            s.pointer.reset();s.pointer.move(yaw,pitch,s.scene.width,s.scene.height);s.recenter=false
            return
        }
        enqueue(s,yaw,pitch)
        if(kotlin.math.abs(pitch)>70 && s.pending.isEmpty()) sample(s,true)
    }
    private fun tick(s: Session) {
        if(s.player.instance!==s.camera.instance || s.camera.isRemoved) { close(s.player);return }
        val now=System.nanoTime()
        if(now-s.lastResponse>5_000_000_000L) {
            close(s.player);s.player.sendMessage(Component.text("UIの応答が途切れたため終了しました。/ui で再度開けます。"));return
        }
        if(s.active) sample(s)
        if(s.polish?.takeStrike()==true) sound(s,"refine_strike")
        val receipt=s.polish?.tick()
        if(receipt!=null) {
            sound(s,when {
                receipt.kind==dev.projects.webui.polish05.Polish05PreviewModel.Kind.REFINE -> "refine_success"
                receipt.success -> "enhance_success"
                else -> "enhance_fail"
            })
            s.scene=s.polish.scene()
            s.renderer.render(s.scene,s.hover,s.pointer)
        }
        drain(s,now)
        if(sessions[s.player.uuid]===s) paintPointer(s)
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
                    } else if(action=="confirm" && s.polish.operation!=null) {
                        if(s.polish.view=="forge") sound(s,"enhance_prepare")
                    } else if(action!="sound") {
                        sound(s,when {
                            action.startsWith("select:") || action.startsWith("bag:") -> "select"
                            action=="cancel" || action.startsWith("view:") -> "back"
                            else -> "click"
                        })
                    }
                    s.generation++
                    s.scene=s.polish.scene()
                    s.hover=s.scene.hit(s.pointer.x,s.pointer.y)?.id
                    s.renderer.render(s.scene,s.hover,s.pointer)
                    continue
                }
                if(action.startsWith("page:") && s.demo.tab!="catalog") continue
                if(action=="reload") {
                    try {
                        val candidate=UiDocument.parse(Files.readString(path))
                        candidate.layout(s.demo.values(),s.demo.flags())
                        s.document=candidate
                    } catch(e: Exception) {
                        s.player.sendMessage(Component.text("再読込できません。前の画面を維持します：${e.message?.take(160)}"));continue
                    }
                } else if(!s.demo.action(action)) continue
                s.generation++
                s.scene=s.document.layout(s.demo.values(),s.demo.flags())
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
    override fun close() { sessions.values.toList().forEach { close(it.player) } }
}
