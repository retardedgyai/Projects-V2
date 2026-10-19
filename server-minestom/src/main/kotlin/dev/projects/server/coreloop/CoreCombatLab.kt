package dev.projects.server.coreloop

import dev.projects.server.coreloop.adventure.BossArena
import dev.projects.server.coreloop.adventure.BossArenaFactory
import dev.projects.server.coreloop.ui.CoreMenuInventory
import dev.projects.server.mob.QuestCombatEncounter
import dev.projects.server.mob.QuestEncounterCombat
import dev.projects.server.questmap.VerdantRoadQuestRuntime
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/** Deliberately separate from CoreAccountService: no test loadout can enter the ledger. */
internal class CoreLabLoadout(private val id: UUID, job: CoreClass) {
    var journey = CoreJourney.legacy(job, CoreJourneyRules.threshold(40))
        private set
    var free = true
    var retaliation = false
    var selectedSlot = 0
    val weaponBase get() = CoreWeaponBase.entries.first { it.usable(journey.job) }
    fun changeClass(job: CoreClass) { journey = journey.changeClass(job) }
    fun equip(choice: Int) { journey = journey.copy(build = journey.build.equip(if (choice >= 8) 4 else selectedSlot, if (choice >= 8) choice - 8 else choice)) }
    fun account(): CoreAccount = CoreAccount(id, balances = emptyMap(), journey = journey,
        weaponIdentity = CoreGearIdentity.legacy(id, CoreGearSlot.WEAPON).copy(base = weaponBase))
}

/** Private random-map laboratory. It owns its actor, enemies and temporary loadout, not a saved run. */
internal class CoreCombatLab(
    private val executor: Executor,
    private val eligible: (Player) -> Boolean,
    private val packed: (Player) -> Boolean,
    private val resetOriginal: (Player) -> Unit,
    private val harbor: (Player) -> CompletableFuture<Boolean>,
    private val restored: (Player) -> Unit,
    private val seed: () -> Long = System::nanoTime,
) {
    /** [bossId] != null: a dedicated boss arena (/bosstest) instead of a random map. */
    private class Entry(val player: Player, val loadout: CoreLabLoadout, val bossId: String? = null, val tier: Int = 1) {
        /** The boss lab's potion works like the real one (review 2026-10-19): [LAB_POTIONS] charges, [LAB_POTION_COOLDOWN_MILLIS] apart. */
        var potions = LAB_POTIONS
        var potionReadyAt = 0L
        @Volatile var runtime: VerdantRoadQuestRuntime? = null
        @Volatile var arena: BossArena? = null
        val instance get() = arena?.instance ?: runtime?.instance
        val spawn get() = arena?.playerSpawn ?: runtime?.spawn
        val prepared get() = runtime != null || arena != null
        fun closePlace() { runtime?.close(); arena?.let { disposeWhenVacated(it, 0) } }
        /** Like the random map's close: an arena may only unload after its last player has left. */
        private fun disposeWhenVacated(arena: BossArena, waited: Int) {
            if (arena.instance.players.isEmpty() || waited >= 200) runCatching { arena.dispose() }
                .onFailure { System.err.println("CORE_BOSS_LAB_DISPOSE_FAILED: ${it.message}") }
            else MinecraftServer.getSchedulerManager().scheduleNextTick { disposeWhenVacated(arena, waited + 1) }
        }
        var actor: CorePlayerCombat? = null
        var combat: QuestEncounterCombat? = null
        var returning = false
        var transferring = false
    }
    private val entries = ConcurrentHashMap<UUID, Entry>()
    private val screens = CoreMenuInventory()
    fun contains(player: Player) = entries[player.uuid]?.player === player
    internal fun ready(player: Player) = entries[player.uuid]?.let { it.player === player && it.combat != null && !it.returning && !it.transferring } == true
    fun actor(player: Player) = entries[player.uuid]?.takeIf { it.player === player && !it.returning }?.actor
    internal fun combat(player: Player) = entries[player.uuid]?.takeIf { it.player === player && !it.returning }?.combat
    fun account(player: Player) = entries[player.uuid]?.takeIf { it.player === player }?.loadout?.account()
    /** Dev-only (/bosstest rage): the boss drops just under its enrage ratio, as if hit by this player. */
    internal fun enrageBoss(player: Player): Boolean {
        val combat = combat(player) ?: return false
        val boss = combat.entities().firstOrNull { combat.isBoss(it.uuid) } ?: return false
        val ratio = combat.bossEnrageRatio()
        val amount = combat.bossHealth() - combat.bossMaxHealth() * (ratio - .01)
        return ratio > 0 && amount > 0 && combat.applyEffectDamage(boss.uuid, player, amount)
    }
    fun summary(player: Player) = entries[player.uuid]?.let {
        if (!it.prepared) "テスト用マップを準備中…"
        else "${if (it.bossId != null) "ボス試験場" else "スキル試験場"} / ${if (it.loadout.free) "制限なし" else "通常条件"} / 設定は手帳 [9]"
    }

    /** [bossId]: dev-only /bosstest. The boss fights back at real strength; nothing is rewarded or saved. */
    /** [tier]: the boss's tier and the test character's gear tier (1-4), so difficulty can be measured at that tier. */
    fun enter(player: Player, job: CoreClass, bossId: String? = null, tier: Int = 1) {
        if (contains(player)) { menu(player); return }
        val command = if (bossId != null) "/bosstest" else "/skilltest"
        if (!eligible(player)) { player.sendMessage(CoreLoopItems.text("港へ帰還してから $command を実行してください。")); return }
        val entry = Entry(player, CoreLabLoadout(player.uuid, job), bossId, tier.coerceIn(1, 4))
        if (bossId != null) { entry.loadout.free = false; entry.loadout.retaliation = true }
        if (entries.putIfAbsent(player.uuid, entry) != null) return
        player.closeInventory(); resetOriginal(player)
        player.sendMessage(CoreLoopItems.text(if (bossId != null) "ボス試験場を準備しています。報酬・成長・装備の保存はありません。"
            else "テスト専用のランダムマップを生成しています。報酬・成長・装備の保存はありません。"))
        CompletableFuture.supplyAsync({
            if (entries[player.uuid] !== entry || entry.returning) throw java.util.concurrent.CancellationException("Lab entry cancelled")
            if (bossId != null) entry.arena = BossArenaFactory.create(bossId, entry.tier)
            else entry.runtime = VerdantRoadQuestRuntime.prepare(seed()).join()
        }, executor)
            .whenComplete { _, error -> nextTick {
                if (entries[player.uuid] !== entry || !player.isOnline || entry.returning) { entry.closePlace(); return@nextTick }
                val instance = entry.instance; val spawn = entry.spawn
                if (error != null || instance == null || spawn == null) {
                    entries.remove(player.uuid, entry)
                    entry.closePlace()
                    System.err.println("CORE_SKILL_LAB_GENERATION_FAILED: $error")
                    player.sendMessage(CoreLoopItems.text("テスト用マップを生成できませんでした。保存データは変更していません。"))
                    return@nextTick
                }
                entry.actor = CorePlayerCombat(player, { entry.tier }, { entry.tier }, { if (entry.returning) null else entry.combat },
                    journey = { entry.loadout.journey }, weaponBase = { entry.loadout.weaponBase }) {
                    nextTick { if (entries[player.uuid] === entry && !entry.returning) {
                        entry.actor?.reset(); player.teleport(spawn)
                        // A lost boss fight starts over: the boss comes back whole too, the potions refill.
                        if (entry.bossId != null) {
                            runCatching { spawnTargets(entry) }
                            entry.potions = LAB_POTIONS; entry.potionReadyAt = 0L
                            player.sendMessage(CoreLoopItems.text("倒れた。ボスも最初からやり直し（ポーション${LAB_POTIONS}個）。"))
                        }
                    } }
                }
                entry.transferring = true
                val transfer = try { player.setInstance(instance, spawn) }
                    catch (failure: Exception) { CompletableFuture.failedFuture<Void>(failure) }
                transfer.whenComplete { _, transferError -> nextTick {
                    entry.transferring = false
                    if (entries[player.uuid] !== entry || !player.isOnline) {
                        if (!player.isOnline && player.instance === instance) player.remove()
                        entry.closePlace(); return@nextTick
                    }
                    if (entry.returning) { returnEntry(entry); return@nextTick }
                    if (transferError != null) { leave(player); return@nextTick }
                    try {
                        spawnTargets(entry)
                        entry.actor?.reset(); entry.actor?.refillTraining()
                        refresh(player)
                        player.sendMessage(CoreLoopItems.text(if (bossId != null)
                            "${entry.arena?.displayName}に到着（Tier${entry.tier}）。${entry.combat?.bossName()}が待っています。ポーションは${LAB_POTIONS}個・再使用10秒。手帳 [9] で職業・技・再配置、帰還は /hub。"
                            else "試験場に到着。手帳 [9] または /skilltest で職業・技・敵を変更できます。帰還は /hub。"))
                        println("CORE_SKILL_LAB_ENTERED player=${player.username} " +
                            (entry.runtime?.let { "seed=${it.plan.seed}" } ?: "boss=$bossId"))
                    } catch (failure: Exception) {
                        System.err.println("CORE_SKILL_LAB_SETUP_FAILED: $failure"); leave(player)
                    }
                } }
            } }
    }

    private fun spawnTargets(entry: Entry) {
        entry.arena?.let { arena ->
            val next = QuestEncounterCombat(arena.instance, entry.tier, emptyList(), arena.bossSpawn,
                onMobDefeated = { _, boss -> if (boss) nextTick {
                    entry.player.sendMessage(CoreLoopItems.text("${arena.displayName}の主を討伐（テストのため報酬なし）。手帳 [9] で再配置できます。"))
                } },
                damagePlayer = { _, damage -> if (!entry.loadout.free) entry.actor?.hurt(damage) },
                typedDamagePlayer = { _, damage, type -> if (!entry.loadout.free) entry.actor?.hurt(damage, type) },
                crowdPlayer = { _, kind, millis, from -> if (!entry.loadout.free) entry.actor?.receiveCrowdControl(kind, millis, from) },
                evading = { entry.actor?.evading == true },
                canTarget = { it === entry.player && !entry.returning },
                explicitBossArchetype = arena.archetype)
            entry.actor?.resetActions(); entry.combat?.dispose(); entry.combat = next
            return
        }
        val runtime = entry.runtime ?: return
        val from = entry.player.position
        val facing = from.direction().withY(0.0).let { if (it.lengthSquared() < .001) net.minestom.server.coordinate.Vec(0.0, 0.0, 1.0) else it.normalize() }
        val right = net.minestom.server.coordinate.Vec(facing.z(), 0.0, -facing.x())
        val positions = listOf(-2.0, 0.0, 2.0).map { side ->
            val desired = from.add(facing.mul(7.0)).add(right.mul(side))
            val x = desired.blockX().coerceIn(2, runtime.plan.size - 3)
            val z = desired.blockZ().coerceIn(2, runtime.plan.size - 3)
            val point = dev.projects.server.questmap.QuestMapPoint(x, z)
            QuestCombatPlacement.resolve(runtime.instance, Pos(x + .5, runtime.plan.heightAt(point) + 1.0, z + .5))
        }
        val next = QuestEncounterCombat(runtime.instance, 1, listOf(QuestCombatEncounter(positions)), positions[1],
            onMobDefeated = { _, _ -> }, // No loot, XP, account callback or completion transition.
            damagePlayer = { _, damage -> if (!entry.loadout.free) entry.actor?.hurt(damage) },
            typedDamagePlayer = { _, damage, type -> if (!entry.loadout.free) entry.actor?.hurt(damage, type) },
            crowdPlayer = { _, kind, millis, from -> if (!entry.loadout.free) entry.actor?.receiveCrowdControl(kind, millis, from) },
            evading = { entry.actor?.evading == true },
            // This predicate also authorizes incoming player hits. Pausing AI must not disable damage.
            canTarget = { it === entry.player && !entry.returning },
            spawnBoss = false, healthMultiplier = 20.0)
        entry.actor?.resetActions(); entry.combat?.dispose(); entry.combat = next
    }

    fun beforeTick(player: Player) {
        val entry = entries[player.uuid]?.takeIf { it.player === player && !it.returning } ?: return
        if (!ready(player)) return
        val spawn = entry.spawn ?: return
        if (entry.instance !== player.instance) return
        if (player.position.y() < 15) { entry.actor?.reset(); player.teleport(spawn); return }
        if (entry.loadout.free) entry.actor?.refillTraining()
        if (entry.loadout.retaliation) entry.combat?.tick(System.currentTimeMillis())
    }

    fun refresh(player: Player): Boolean {
        val entry = entries[player.uuid]?.takeIf { it.player === player } ?: return false
        val actor = entry.actor ?: return true
        CoreLoopItems.refresh(player, entry.loadout.account(), packed = packed(player), combatSheet = actor.sheet)
        player.inventory.setItemStack(8, ItemStack.of(Material.NETHER_STAR).withCustomName(CoreLoopItems.text("スキル試験場の設定"))
            .withLore(CoreLoopItems.text("右クリック：職業・技・試験条件・敵の再配置"))
            .withTag(CoreLoopItems.actionTag, "journal"))
        val bossLab = entry.bossId != null && !entry.loadout.free
        player.inventory.setItemStack(6, if (bossLab) ItemStack.of(Material.POTION).withCustomName(CoreLoopItems.text("回復ポーション（残り${entry.potions}）"))
            .withLore(CoreLoopItems.text("右クリック：最大HPの45%を回復 / 再使用10秒（本番と同じ）"))
            .withTag(CoreLoopItems.actionTag, "potion")
            else ItemStack.of(Material.POTION).withCustomName(CoreLoopItems.text("テスト用の全補充"))
            .withLore(CoreLoopItems.text("右クリック：HP・マナ・固有ゲージ・クールダウンを補充"))
            .withTag(CoreLoopItems.actionTag, "potion"))
        return true
    }

    /** The potion item: a full refill in the skill lab or free mode, the real potion in a boss fight at normal rules. */
    fun potion(player: Player) {
        val entry = entries[player.uuid]?.takeIf { it.player === player && !it.returning } ?: return
        val actor = entry.actor ?: return
        if (entry.bossId == null || entry.loadout.free) { actor.refillTraining(); return }
        val now = System.currentTimeMillis()
        when {
            entry.potions <= 0 -> player.sendMessage(CoreLoopItems.text("ポーションがもうない。"))
            now < entry.potionReadyAt -> player.sendMessage(CoreLoopItems.text("ポーションはあと${(entry.potionReadyAt - now + 999) / 1000}秒で使える。"))
            else -> { actor.healPotion(); entry.potions--; entry.potionReadyAt = now + LAB_POTION_COOLDOWN_MILLIS; refresh(player) }
        }
    }

    fun click(event: InventoryPreClickEvent): Boolean {
        if (!contains(event.player)) return false
        if (screens.click(event)) return true
        event.isCancelled = true // Temporary equipment is a projection, not transferable inventory.
        return true
    }

    fun menu(player: Player) {
        val entry = entries[player.uuid]?.takeIf { it.player === player && !it.returning } ?: return
        if (!ready(player)) { player.sendMessage(CoreLoopItems.text("マップを準備中です。少しお待ちください。")); return }
        val actor = entry.actor ?: run { player.sendMessage(CoreLoopItems.text("マップを準備中です。少しお待ちください。")); return }
        val state = entry.loadout
        val items = Array(54) { ItemStack.AIR }
        val actions = mutableMapOf<Int, () -> Unit>()
        fun button(slot: Int, material: Material, name: String, description: String, action: () -> Unit) {
            items[slot] = ItemStack.of(material).withCustomName(CoreLoopItems.text(name)).withLore(CoreLoopItems.text(description))
            actions[slot] = action
        }
        fun changed() { actor.reset(); if (state.free) actor.refillTraining(); refresh(player); menu(player) }
        CoreClass.entries.forEachIndexed { i, job ->
            button(i, Material.NETHER_STAR, (if (state.journey.job == job) "選択中：" else "職業：") + job.displayName,
                "テスト専用。元の職業・装備は変更しません") { state.changeClass(job); changed() }
            if (packed(player)) items[i] = items[i].withItemModel("projects:core_ui/${CoreSkillCatalog.skills(job).first().icon}")
        }
        repeat(4) { slot ->
            button(9 + slot, Material.BOOK, (if (state.selectedSlot == slot) "変更先：" else "枠：") + "${slot + 1} / ${actor.skillDefinitions[slot].name}",
                "この枠を選び、その下のスキルをクリック") { state.selectedSlot = slot; menu(player) }
        }
        val skills = CoreSkillCatalog.skills(state.journey.job)
        skills.forEachIndexed { i, skill ->
            if (skill.retired) return@forEachIndexed
            val slot = if (i < 8) 18 + i else 27 + i - 8
            items[slot] = CoreSkillTooltip.item(skill, actor.sheet, state.journey, packed(player),
                footer = if (i < 8) "クリック：枠${state.selectedSlot + 1}へ設定" else "クリック：奥義へ設定")
            actions[slot] = { state.equip(i); changed() }
        }
        button(36, Material.POTION, "全回復・再使用を補充", "HP・マナ・固有ゲージ・クールダウンを補充") { actor.refillTraining(); player.closeInventory() }
        button(37, Material.REDSTONE, "条件：${if (state.free) "制限なし" else "通常条件"}", "制限なし＝毎tick補充・無敵。通常条件＝実際の資源と待ち時間") {
            state.free = !state.free; changed()
        }
        if (entry.bossId != null) button(38, Material.ARMOR_STAND, "ボスを再配置", "HP満タンで初期位置に戻す。ポーションも戻す。報酬なし") {
            spawnTargets(entry); entry.potions = LAB_POTIONS; entry.potionReadyAt = 0L; refresh(player); if (state.free) actor.refillTraining(); player.closeInventory()
        } else button(38, Material.ARMOR_STAND, "目の前に敵を再配置", "HP20倍の敵3体。古い敵と攻撃状態は消去。報酬なし") {
            try { spawnTargets(entry); if (state.free) actor.refillTraining(); player.closeInventory() }
            catch (failure: Exception) { player.sendMessage(CoreLoopItems.text("敵を置ける広さがありません。道など広い場所で再試行してください。")) }
        }
        button(39, Material.IRON_SWORD, "敵の反撃：${if (state.retaliation) "あり" else "なし"}", "反撃なしは演出確認用。戦闘も試すなら通常条件と反撃あり") {
            try { spawnTargets(entry); state.retaliation = !state.retaliation; changed() }
            catch (failure: Exception) { player.sendMessage(CoreLoopItems.text("敵を置ける広さがありません。道など広い場所で再試行してください。")) }
        }
        button(40, Material.GLASS_BOTTLE, "初期化・固有ゲージをゼロ", "通常条件へ切替。スタックを貯める動作から試す") {
            state.free = false; actor.reset(); player.closeInventory()
        }
        button(44, Material.OAK_DOOR, "港へ帰還", "テスト場を破棄し、元の装備・職業に戻ります") { leave(player) }
        button(41, Material.FIREWORK_STAR, "戦闘演出：${CoreCombatPresentation.detail(player).label}", "華やか → 控えめ → 最小。自分の表示だけ変更、再接続で華やかに戻ります") {
            CoreCombatPresentation.cycle(player); menu(player)
        }
        button(49, Material.ARROW, "閉じて試す", "ホットバー2～6のスキルを右クリック。設定は手帳9") { player.closeInventory() }
        screens.show(player, CoreMenuInventory.Screen(CoreLoopItems.text("スキル試験場：職業 → 枠 → 技"), items, actions) { menu(player) })
    }

    fun leave(player: Player): Boolean {
        val entry = entries[player.uuid]?.takeIf { it.player === player } ?: return false
        if (entry.returning) return true
        entry.returning = true; entry.actor?.resetActions(); entry.combat?.dispose(); player.closeInventory()
        // Serialize outgoing transfer after any in-flight incoming transfer; never close its destination early.
        if (!entry.transferring) returnEntry(entry)
        return true
    }

    private fun returnEntry(entry: Entry) {
        val player = entry.player
        harbor(player).whenComplete { success, failure -> nextTick {
            if (entries[player.uuid] !== entry) { entry.closePlace(); return@nextTick }
            if (success == true && failure == null) {
                entries.remove(player.uuid, entry); screens.forget(player.uuid)
                entry.closePlace()
                resetOriginal(player); restored(player)
                println("CORE_SKILL_LAB_RETURNED player=${player.username}")
            } else player.kick(CoreLoopItems.text("帰還先へ移動できませんでした。再接続してください。保存データは変更していません。"))
        } }
    }

    fun disconnect(player: Player) {
        val entry = entries[player.uuid]?.takeIf { it.player === player } ?: return
        entries.remove(player.uuid, entry); screens.forget(player.uuid)
        entry.returning = true; entry.actor?.resetActions(); entry.combat?.dispose()
        // While transferring, the transfer callback sees the removed entry and closes the map itself.
        entry.instance?.takeUnless { entry.transferring }?.let { instance -> nextTick {
            if (!player.isOnline && player.instance === instance) player.remove()
            entry.closePlace()
        } }
    }

    fun close() { entries.values.toList().forEach { disconnect(it.player) } }
    private fun nextTick(action: () -> Unit) { MinecraftServer.getSchedulerManager().scheduleNextTick { action() } }
}

private const val LAB_POTIONS = 3
private const val LAB_POTION_COOLDOWN_MILLIS = 10_000L
