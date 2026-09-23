package dev.projects.modellab

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerHand
import net.minestom.server.event.GlobalEventHandler
import net.minestom.server.event.player.*
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Only the isolated lab: a persistent entry point and short Japanese testing flows. */
class LabTestUi(events: GlobalEventHandler, private val menu: LabMenu, private val bundle: ModelBundle,
    private val instance: Instance, private val ice: IceFangTraining,
    private val actors: MutableMap<UUID,BossModelActor>, private val ready: (Player)->Boolean, private val metrics: TickMetrics) {
    private val repeat=ConcurrentHashMap.newKeySet<UUID>()
    init {
        events.addListener(PlayerUseItemEvent::class.java) { e ->
            if(usesOpener(e.player,e.hand)) { e.isCancelled=true; home(e.player) }
        }
        events.addListener(PlayerBlockInteractEvent::class.java) { e ->
            if(usesOpener(e.player,e.hand)) { e.isCancelled=true; home(e.player) }
        }
        events.addListener(PlayerEntityInteractEvent::class.java) { e -> if(usesOpener(e.player,e.hand)) home(e.player) }
        events.addListener(PlayerSwapItemEvent::class.java) { e ->
            if(e.player.instance === instance && e.player.isSneaking) { e.isCancelled=true; home(e.player) }
        }
        events.addListener(PlayerDisconnectEvent::class.java) { repeat.remove(it.player.uuid) }
    }
    fun giveOpener(player: Player) { player.inventory.setItemStack(8,opener()) }
    fun home(player: Player) {
        if(player.instance !== instance) return
        val loaded=ready(player)
        val hasModel=actors.containsKey(player.uuid)
        fun prepare() {
            if(!ensureReady(player)) return
            actors.remove(player.uuid)?.close()
            player.closeInventory(); ice.equip(player)
        }
        menu.panel(player,"テスト工房",mapOf(
            4 to LabMenu.Button(if(loaded) "テスト準備完了" else "パック読み込み中",Material.BOOK,
                listOf("アイコンをクリックして選択", "コンパス右クリック／Shift＋Fで再び開く")) {},
            10 to LabMenu.Button("氷牙の連鎖を試す",Material.BLUE_ICE,
                listOf("杖を装備し、正面に標的を3体配置", "地面に立って杖を右クリック", "マナ20／再使用4秒／飛行中は使用不可"),loaded) { prepare() },
            11 to LabMenu.Button("標的・マナ・再使用をリセット",Material.HONEY_BOTTLE,
                listOf("標的を今向いている方向に並べ直す", "マナ全回復・再使用待ちを解除"),loaded) { prepare() },
            12 to LabMenu.Button("氷魔法テストを終了",Material.BUCKET,listOf("自分の杖・標的・氷を片づける")) {
                ice.remove(player); player.closeInventory()
            },
            14 to LabMenu.Button("ボスモデルを選ぶ",Material.ARMOR_STAND,
                listOf("5体から選んで目の前に表示", "戦闘AIではなくモデルの動作確認"),loaded) { bosses(player) },
            15 to LabMenu.Button("表示中モデルの動きを選ぶ",Material.BLAZE_POWDER,
                listOf(if(hasModel) "単発／連続を切り替えて再生" else "先にボスモデルを選んでください"),loaded && hasModel) { animations(player) },
            16 to LabMenu.Button("表示中モデルを片づける",Material.CAULDRON,
                listOf("自分が出したモデルだけ削除"),hasModel) { actors.remove(player.uuid)?.close(); home(player) },
            20 to LabMenu.Button("動作状況",Material.CLOCK,listOf(metrics.summary(instance.entities.size))) { home(player) },
            22 to LabMenu.Button("自分のテストを全部片づける",Material.LAVA_BUCKET,
                listOf("モデル・杖・標的・氷を削除", "ほかのプレイヤーには影響しません")) {
                actors.remove(player.uuid)?.close(); ice.remove(player); player.closeInventory()
            },
            26 to LabMenu.Button("閉じてテストする",Material.BARRIER) { player.closeInventory() },
        ))
    }
    fun bosses(player: Player) {
        if(!ensureReady(player)) return
        menu.show(player,"ボスモデルを選ぶ",bosses.map { (id,name,icon) ->
            LabMenu.Button(name,icon,listOf("正面8mに配置して、動きの一覧を開く")) {
                if(ensureReady(player)) {
                    val definition=bundle.definition(id)
                    val direction=IceFangPlan.direction(player.position.yaw())
                    val pos=Pos(player.position.x()+direction.x*8,1.0,player.position.z()+direction.z*8,player.position.yaw()+180f,0f)
                    val actor=BossModelActor(definition,instance,pos)
                    actor.move(pos)
                    actors.put(player.uuid,actor)?.close()
                    ice.remove(player)
                    animations(player)
                }
            }
        },back={home(player)})
    }
    private fun animations(player: Player) {
        if(!ensureReady(player)) return
        val actor=actors[player.uuid] ?: return home(player)
        val looping=player.uuid in repeat
        val name=bosses.firstOrNull { it.id==actor.definition.id }?.name ?: "モデル"
        val mode=if(looping) "連続" else "単発"
        menu.show(player,"$name：動き（$mode）",actor.definition.animations.map { (animation,seconds) ->
            LabMenu.Button(animationLabel(animation),if(looping) Material.REPEATER else Material.BLAZE_POWDER,
                listOf("${"%.1f".format(seconds)}秒／$mode 再生", "選ぶと画面を閉じて再生", "識別名：$animation")) {
                if(ensureReady(player) && actors[player.uuid] === actor) {
                    actor.stopRepeating()
                    if(looping) actor.repeat(animation) else actor.play(animation)
                    player.closeInventory()
                }
            }
        },back={home(player)},extra=LabMenu.Button("再生方法：$mode",Material.LEVER,
            listOf("クリックで${if(looping) "単発" else "連続"}に切り替える", "止めるときは工房でモデルを片づける")) {
            if(looping) repeat.remove(player.uuid) else repeat.add(player.uuid)
            animations(player)
        })
    }
    private fun ensureReady(player: Player): Boolean {
        if(player.instance !== instance || player.isRemoved) return false
        if(ready(player)) return true
        player.sendMessage(Component.text("パック読み込み中です。少し待ってメニューを開き直してください。",NamedTextColor.YELLOW))
        return false
    }
    companion object {
        private val openerTag=Tag.Boolean("projects:lab_test_menu")
        fun opener(): ItemStack=ItemStack.builder(Material.COMPASS)
            .customName(Component.text("テストメニュー",NamedTextColor.GOLD).decoration(TextDecoration.ITALIC,false))
            .lore(listOf("右クリックでテスト工房を開く", "Shift＋Fでも開けます").map { Component.text(it,NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false) })
            .set(openerTag,true).build()
        fun usesOpener(player: Player,hand: PlayerHand)=hand==PlayerHand.MAIN && player.itemInMainHand.getTag(openerTag)==true
        private data class Boss(val id:String,val name:String,val icon:Material)
        private val bosses=listOf(Boss("osirion.bbmodel","不滅の王",Material.GOLDEN_HELMET),Boss("radix.bbmodel","母樹",Material.FLOWERING_AZALEA),
            Boss("vesper.bbmodel","鐘の番人",Material.BELL),Boss("piglin_lord.bbmodel","黄金卿",Material.GOLD_INGOT),
            Boss("ashen_knight.bbmodel","灰淵の騎士",Material.NETHERITE_SWORD))
        internal fun animationLabel(key:String):String {
            val words=mapOf("idle" to "待機","idle_bloom" to "開花待機","attack" to "攻撃","charge" to "突進","cast" to "詠唱",
                "death" to "倒れる","walk" to "歩く","cleave" to "薙ぎ払い","slam" to "叩きつけ","leap" to "跳躍",
                "dash" to "突進","awaken" to "目覚め","stagger" to "よろめく",
                "run" to "走る","cleave_reverse" to "返し斬り","thrust" to "突き","enrage" to "狂化",
                "roar" to "咆哮","crash" to "衝突","shatter" to "粉砕","spin" to "回転攻撃","uppercut" to "斬り上げ",
                "stomp" to "踏みつけ","devour" to "捕食","bite" to "噛みつき","bloom" to "開花","choke" to "締めつけ",
                "unbound" to "解放","hoist" to "持ち上げ","suspend" to "空中で保持","drop" to "落下","sweep" to "振り払い",
                "return" to "振り戻し","sweep_return" to "振り戻し","toll" to "鐘を鳴らす","rupture" to "破裂","kneel" to "膝をつく","finale" to "終幕")
            words[key]?.let{return it}
            for((suffix,label) in listOf("_unbound" to "・解放形態","_windup" to "・予備動作","_hold" to "・溜め"))
                if(key.endsWith(suffix)) return animationLabel(key.removeSuffix(suffix))+label
            return key
        }
    }
}
