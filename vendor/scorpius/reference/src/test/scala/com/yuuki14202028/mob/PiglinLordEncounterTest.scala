package com.yuuki14202028.mob

import com.yuuki14202028.{Blocks, EntityTypes, Log}
import com.yuuki14202028.combat.CombatSystem
import com.yuuki14202028.db.Database
import com.yuuki14202028.dungeon.{DungeonManager, FloorStore, LordTreasury}
import com.yuuki14202028.item.{ItemManager, ItemType}
import com.yuuki14202028.mob.species.MagmaPiglinCreature
import com.yuuki14202028.mob.species.piglinlord.{LavaWalkerNodeGenerator, PiglinLordCreature, PiglinLordFight, PiglinLordGoal, PiglinLordTheme}
import com.yuuki14202028.player.{PlayerManager, ScorpiusPlayer}
import net.minestom.server.coordinate.{Pos, Vec}
import net.minestom.server.entity.{GameMode, Player}
import net.minestom.server.instance.Chunk
import net.minestom.testing.{Env, EnvTest}
import net.worldseed.multipart.ModelEngine
import org.junit.jupiter.api.{Test, Timeout}
import org.junit.jupiter.api.Assertions.*

import java.nio.file.{Files, Path}
import java.util.UUID
import scala.jdk.CollectionConverters.*

/** ./gradlew piglinLordSmoke。ソケットを開かず、実AI・モデル・溝の溶岩・床の傷・戦闘ファネルをtickする。 */
@EnvTest
class PiglinLordEncounterTest {
  @Test
  @Timeout(120)
  def encounterAndCleanup(env: Env): Unit = {
    Database.init(Files.createTempDirectory("lord-smoke").resolve("smoke.db"))
    FloorStore.init()
    Log.muteWseeViewerSpam()
    val reader = Files.newBufferedReader(Path.of("model_mappings.json"))
    try ModelEngine.loadMappings(reader, Path.of("wsee_models")) finally reader.close()
    env.process().connection().setPlayerProvider { (connection, profile) =>
      new ScorpiusPlayer(connection, profile) {
        override def sendChunk(chunk: Chunk): Unit = sendPacket(chunk.getFullDataPacket)
      }
    }
    val owner = UUID.randomUUID()
    val (inst, layout) = DungeonManager.getOrCreateInstance(20, "lord-smoke", enteredBy = Some(owner))
    for (x <- -3 to 3; z <- -3 to 3) inst.loadChunk(x, z).join()
    val home = Pos(.5, 2, .5)
    val lavaY = 2
    val bait = home.add(6, 1, 0)
    val player = env.createPlayer(inst, bait)
    player.setNoGravity(true)
    val ps = PlayerManager.getStats(player)
    ps.hp = 10000
    val spectator = env.createPlayer(inst, home.add(-3, 1, 3))
    spectator.setGameMode(GameMode.SPECTATOR)
    spectator.setNoGravity(true)
    PlayerManager.getStats(spectator).hp = 10000

    def step(count: Int): Unit = (0 until count).foreach { _ => env.tick() }
    def move(p: Player, at: Pos): Unit = {
      p.teleport(at).join()
      p.setVelocity(Vec.ZERO)
    }
    def spawnBoss(): (PiglinLordCreature, MobStats, PiglinLordGoal) = {
      MobSpawn.spawnBossForFloor(inst, layout)
      step(2)
      val c = inst.getEntities.asScala.flatMap(PiglinLordCreature.opt).find(!_.isRemoved).get
      (c, MobManager.getMobStats(c.getUuid).get, c.goal.get)
    }
    /** 条件が満ちるまで tick する（上限つき）。満ちなければ理由つきで落とす。 */
    def waitFor(budget: Int, why: String)(cond: => Boolean): Unit = {
      var left = budget
      while (!cond && left > 0) { env.tick(); left -= 1 }
      assertTrue(cond, why)
    }
    def displays: Set[Int] = inst.getEntities.asScala.filter(_.getEntityType == EntityTypes.ITEM_DISPLAY).map(_.getEntityId).toSet
    def block(x: Int, y: Int, z: Int) = inst.getBlock(x, y, z)
    def isLava(x: Int, z: Int): Boolean = block(x, lavaY, z).compare(Blocks.LAVA)
    val channel0 = LordTreasury.Channels(0)
    val hubCell = channel0.cells.find(_._2 == 0).get      // (7, 0): 参道と交わる溝のマス
    val before = displays

    // ── 叩きつけ: 弧を描いて跳び、着地で床が割れ、いちばん近い堰が破れて溶岩の波が走る ──────
    val (boss, stats, goal) = spawnBoss()
    assertEquals(PiglinLordFight.BodyHeight, boss.getBoundingBox.height(), .001)
    waitFor(40, "登場しても楽譜が回らない") { goal.musicPlaying }
    assertEquals(before.size + 15, displays.size, "専用モデルのボーンが出現しない")
    assertEquals(LordTreasury.ChannelBlock, block(hubCell._1, lavaY - 1, hubCell._2), "溝の床が金入り黒石でない")
    var apex = 0.0
    waitFor(200, "叩きつけの着地で堰が熾らない") {
      apex = math.max(apex, boss.getPosition.y)
      block(channel0.cells.head._1, lavaY - 1, channel0.cells.head._2) == LordTreasury.ChannelHotBlock
    }
    assertTrue(apex >= home.y + PiglinLordFight.LeapApex * .8, s"跳躍が弧を描かない（最高点 $apex）")
    assertEquals(1, goal.craterCount, "着地で床が割れない")
    assertEquals(Blocks.MAGMA_BLOCK, block(6, lavaY - 1, 0), "クレーターの芯がマグマでない")
    val spurtCells = (for (x <- 3 to 9; z <- -3 to 3 if !LordTreasury.isChannelCell(x, z) && isLava(x, z)) yield (x, z)).toList
    assertTrue(spurtCells.nonEmpty, "割れ目から溶岩が噴かない")
    waitFor(120, "溶岩の波が溝を走ってこない") { isLava(hubCell._1, hubCell._2) }
    assertTrue(isLava(channel0.cells.head._1, channel0.cells.head._2), "堰側の溝が溶岩でない")
    assertFalse(isLava(0, 0), "溝の外へ溶岩が漏れた")
    assertEquals(10000, PlayerManager.getStats(spectator).hp, "観戦者が溶岩に焼かれた")
    // 経路探索は沸いた溝を壁と見ない（黄金卿は溶岩を渡って歩く）
    val walker = new LavaWalkerNodeGenerator
    val from = Pos(5.5, lavaY, .5)
    assertTrue(walker.canMoveTowards(inst, from, Pos(hubCell._1 + .5, lavaY, hubCell._2 + .5), boss.getBoundingBox),
      "溶岩のマスを通れないと判定した")
    assertFalse(walker.canMoveTowards(inst, Pos(28.5, lavaY, .5), Pos(30.5, lavaY, .5), boss.getBoundingBox),
      "壁のマスを通れると判定した")

    // ── 溶岩を渡る突進で金の鎧が灼け、壁に激突して砕ける ─────────────────────────
    move(player, home.add(14, 1, 0))
    HateSystem.addHate(boss.getUuid, player.getUuid, 1000.0)
    waitFor(300, "溶岩を渡っても灼けない") { boss.damageGate.exposedUntilMs > System.currentTimeMillis() }
    val ordinary = MobDamageGate.applyIncomingDamage(boss, 20, 1000L)
    assertEquals(30, ordinary, "灼けた金に露出倍率が乗らない")
    assertEquals(stats.maxHp - 30, stats.hp)
    waitFor(200, "突進が壁に阻まれてひるまない") { MobPoise.isStaggered(boss.getUuid) }

    // 溶岩は寿命で引く。溝の床は金入り黒石へ戻り、割れ目の溶岩も引いてマグマの芯だけが残る。
    waitFor(PiglinLordFight.FloodHoldTicks.toInt + PiglinLordFight.FloodFadeTicks.toInt + 120, "溶岩が引かない") {
      !isLava(hubCell._1, hubCell._2) && block(hubCell._1, lavaY - 1, hubCell._2) == LordTreasury.ChannelBlock
    }
    assertTrue(spurtCells.forall((x, z) => !isLava(x, z)), "割れ目の溶岩が引かない")
    assertEquals(Blocks.MAGMA_BLOCK, block(6, lavaY - 1, 0), "戦いの跡が消えた")

    // ── 溜めのある技: 回転斬りは竜巻を残し、足踏みは環を走らせ、突き上げは打ち上げて跳んで追う ──
    // 激突のひるみは壁時計（1.3秒）なので、実時間で明けるのを待ってから座へ戻す。
    Thread.sleep(1400)
    boss.teleport(home).join()
    goal.forceTech("Spin")
    move(player, home.add(11, 1, 0))
    waitFor(240, "回転斬りが竜巻を残さない") { goal.hasTornado }
    goal.forceTech("Stomp")
    move(player, home.add(6, 0, 0))
    val displaysBeforeStomp = displays.size
    waitFor(200, "足踏みの環が走らない") { goal.waveCount > 0 }
    waitFor(20, "環の実体が出ない") { displays.size > displaysBeforeStomp }
    waitFor(160, "環が引かない") { goal.waveCount == 0 }
    goal.forceTech("Uppercut")
    move(player, Pos(boss.getPosition.x + 2, home.y, boss.getPosition.z))
    HateSystem.addHate(boss.getUuid, player.getUuid, 1000.0)
    val cratersBefore = goal.craterCount
    waitFor(200, "突き上げで打ち上げられない") { player.getPosition.y > home.y + 1.5 || player.getVelocity.y > 1.0 }
    waitFor(120, "打ち上げた相手へ跳んで叩き落とさない") { goal.craterCount > cratersBefore }

    // ── 戦闘途中の撤去は、跳躍の円・沸いた溝・竜巻・環を畳む。床の傷だけが残る ────────────
    move(player, bait)
    MobManager.despawnMob(boss.getUuid)
    step(3)
    assertEquals(before, displays, "一掃でモデルや環が残る")
    assertFalse(goal.hasTornado, "撤去で竜巻が残る")
    assertFalse(goal.musicPlaying, "撤去で楽譜が鳴り続ける")
    assertTrue(LordTreasury.Channels.forall(c => c.cells.forall((x, z) =>
      !isLava(x, z) && block(x, lavaY - 1, z) == LordTreasury.ChannelBlock)), "撤去で溝に溶岩か熾りが残る")
    assertTrue((for (x <- -12 to 24; z <- -12 to 12) yield (x, z)).forall((x, z) => !isLava(x, z)), "撤去で割れ目の溶岩が残る")
    assertEquals(Blocks.MAGMA_BLOCK, block(6, lavaY - 1, 0), "床の傷は残る")
    assertEquals(0L, boss.damageGate.exposedUntilMs, "撤去で露出が残る")

    // ── 溶け落ち（HP50%）で金板が剥がれ、貢ぎの召集で溶岩撒きが出る ──────────────
    val (second, secondStats, secondGoal) = spawnBoss()
    waitFor(40, "二体目が現れない") { displays.size >= before.size + 15 }
    secondStats.hp = (secondStats.maxHp * PiglinLordFight.MoltenHp).toInt
    waitFor(200, "貢ぎの召集で溶岩撒きが呼ばれない") {
      inst.getEntities.asScala.exists(e => MagmaPiglinCreature.opt(e).isDefined && !e.isRemoved)
    }
    val minions = inst.getEntities.asScala.filter(e => MagmaPiglinCreature.opt(e).isDefined && !e.isRemoved).toList
    assertEquals(1, minions.size, "一人なら呼ばれるのは一体")
    assertTrue(minions.forall(m => MobSpawn.isSummoned(m.getUuid)), "呼ばれた溶岩撒きに実入りが付く")
    // 断末魔（HP20%）で四つの堰が同時に破れる
    secondStats.hp = (secondStats.maxHp * PiglinLordFight.FinaleHp).toInt
    waitFor(200, "断末魔で四つの堰が破れない") {
      LordTreasury.Channels.forall(c => block(c.cells.head._1, lavaY - 1, c.cells.head._2) == LordTreasury.ChannelHotBlock ||
        isLava(c.cells.head._1, c.cells.head._2))
    }
    minions.foreach(m => MobManager.despawnMob(m.getUuid))

    // ── 戦闘途中の保存は全快からの再戦。舞台と頭目の組み合わせも戻る ─────────────
    DungeonManager.persistFloorOf(inst)
    assertTrue(FloorStore.load(owner).get.bossAlive)
    val (resumed, resumedLayout) = DungeonManager.getOrCreateInstance(20, "lord-resumed", enteredBy = Some(owner))
    resumed.loadChunk(0, 0).join()
    assertTrue(DungeonManager.restorePopulation(resumed, resumedLayout))
    step(2)
    val restoredBoss = resumed.getEntities.asScala.flatMap(PiglinLordCreature.opt).head
    val restoredStats = MobManager.getMobStats(restoredBoss.getUuid).get
    assertEquals(restoredStats.maxHp, restoredStats.hp, "再戦で前の局面や傷が残る")
    assertEquals(layout.seed, resumedLayout.seed)
    MobManager.despawnMob(restoredBoss.getUuid)

    // ── 撃破。溶岩は退き、鍵は一度だけ渡り、討伐済みの階に黄金卿は戻らない ─────────
    val keyCountBefore = player.getInventory.getItemStacks.count(s => ItemManager.getItemTypeFromStack(s).contains(ItemType.BossKey))
    secondStats.hp = 0
    assertTrue(CombatSystem.creditMobKill(player, second, secondStats))
    assertFalse(CombatSystem.creditMobKill(player, second, secondStats), "同じ撃破が二重に計上される")
    assertTrue(secondGoal.musicPlaying, "撃破の瞬間に終曲が切られた")
    step(PiglinLordFight.DeathTicks.toInt + 4)
    assertEquals(before, displays, "撃破後にモデル・予兆が残る")
    waitFor(PiglinLordTheme.Victory.lengthTicks + PiglinLordTheme.Rampage.beatTicks + 4, "終曲が鳴り終わらない") {
      !secondGoal.musicPlaying
    }
    assertTrue(LordTreasury.Channels.forall(c => c.cells.forall((x, z) => !isLava(x, z))), "撃破後に溶岩が残る")
    val keyCountAfter = player.getInventory.getItemStacks.count(s => ItemManager.getItemTypeFromStack(s).contains(ItemType.BossKey))
    assertEquals(keyCountBefore + 1, keyCountAfter, "撃破の鍵が一度だけ渡らない")
    assertFalse(MobManager.hasLivingBoss(inst))
    BossKeyManager.openGate(inst, layout)
    DungeonManager.persistFloorOf(inst)
    assertFalse(FloorStore.load(owner).get.bossAlive)
    val (cleared, clearedLayout) = DungeonManager.getOrCreateInstance(20, "lord-cleared", enteredBy = Some(owner))
    cleared.loadChunk(0, 0).join()
    assertTrue(DungeonManager.restorePopulation(cleared, clearedLayout))
    step(2)
    assertFalse(MobManager.hasLivingBoss(cleared), "討伐済みの黄金卿が復活した")
    val lockX = clearedLayout.stairsWorldX - 4
    val lockZ = clearedLayout.stairsWorldZ
    cleared.loadChunk(lockX >> 4, lockZ >> 4).join()
    assertEquals(Blocks.AIR, cleared.getBlock(lockX, 2, lockZ), "開けた錠前が復元で塞がった")
  }
}
