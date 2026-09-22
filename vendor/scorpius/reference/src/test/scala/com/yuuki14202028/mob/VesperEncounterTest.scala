package com.yuuki14202028.mob

import com.yuuki14202028.{Blocks, EntityTypes, Log}
import com.yuuki14202028.combat.{Combatant, CombatSystem, SkillTargeting}
import com.yuuki14202028.db.Database
import com.yuuki14202028.dungeon.{DungeonManager, FloorStore}
import com.yuuki14202028.item.{ItemManager, ItemType}
import com.yuuki14202028.mob.species.vesper.{VesperCreature, VesperFight, VesperGoal}
import com.yuuki14202028.player.{PlayerManager, ScorpiusPlayer}
import net.minestom.server.coordinate.{Pos, Vec}
import net.minestom.server.entity.{GameMode, Player}
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.instance.Chunk
import net.minestom.testing.{Env, EnvTest}
import net.worldseed.multipart.ModelEngine
import org.junit.jupiter.api.{Test, Timeout}
import org.junit.jupiter.api.Assertions.*

import java.nio.file.{Files, Path}
import java.util.UUID
import scala.jdk.CollectionConverters.*

/** ./gradlew vesperSmoke。ソケットを開かず、実AI・モデル・戦闘ファネルをtickする。 */
@EnvTest
class VesperEncounterTest {
  @Test
  @Timeout(60)
  def encounterAndCleanup(env: Env): Unit = {
    Database.init(Files.createTempDirectory("vesper-smoke").resolve("smoke.db"))
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
    val (inst, layout) = DungeonManager.getOrCreateInstance(15, "vesper-smoke", enteredBy = Some(owner))
    for (x <- -2 to 2; z <- -2 to 2) inst.loadChunk(x, z).join()
    val home = Pos(.5, 2, .5)
    val bait = VesperFight.bellsAt(home).head.add(-4, 0, 0)
    val player = env.createPlayer(inst, bait)
    player.setNoGravity(true)
    val ps = PlayerManager.getStats(player)
    ps.hp = 10000
    val spectator = env.createPlayer(inst, home.add(2, 0, 0))
    spectator.setGameMode(GameMode.SPECTATOR)
    spectator.setNoGravity(true)
    PlayerManager.getStats(spectator).hp = 10000

    def step(count: Int): Unit = (0 until count).foreach { _ => env.tick() }
    def move(p: Player, at: Pos): Unit = {
      p.teleport(at).join()
      p.setVelocity(Vec.ZERO)
    }
    def spawnBoss(): (VesperCreature, MobStats) = {
      MobSpawn.spawnBossForFloor(inst, layout)
      step(2)
      val c = inst.getEntities.asScala.flatMap(VesperCreature.opt).find(!_.isRemoved).get
      (c, MobManager.getMobStats(c.getUuid).get)
    }
    def until(boss: VesperCreature, tick: Long): Unit = {
      var budget = 600
      while (boss.getAliveTicks < tick && budget > 0) { env.tick(); budget -= 1 }
      assertTrue(budget > 0, "ボスのtickが進まない")
    }
    def displays: Set[Int] = inst.getEntities.asScala.filter(_.getEntityType == EntityTypes.ITEM_DISPLAY).map(_.getEntityId).toSet
    val before = displays
    val (boss, stats) = spawnBoss()
    assertEquals(VesperFight.BodyHeight, boss.getBoundingBox.height(), .001)
    assertEquals(before.size + 15, displays.size, "専用モデルのボーンが出現しない")

    // 予兆を鐘の横に確定させて退く。既に指名された位置を追尾し直してはならない。
    until(boss, 65)
    move(player, home.add(5, 1.0, 0))
    val hpBeforeDrop = ps.hp
    until(boss, 110)
    assertEquals(hpBeforeDrop, ps.hp, "退いたプレイヤーを落鐘が追尾した")
    until(boss, 148)
    assertTrue(boss.damageGate.exposedUntilMs > System.currentTimeMillis(), "鐘の波が番人を崩していない")
    assertEquals(stats.maxHp, stats.hp, "鐘がボスのHPを勝手に削った")
    assertEquals(10000, PlayerManager.getStats(spectator).hp, "観戦者に波が命中した")
    val ordinary = MobDamageGate.applyIncomingDamage(boss, 20, 1000L)
    assertEquals(30, ordinary, "共鳴崩しの反撃に露出倍率が乗らない")
    until(boss, 240)
    assertEquals(0L, boss.damageGate.exposedUntilMs, "崩し明けに露出が残った")

    // 床の波は跳べる。頭上の落鐘は同じジャンプで抜けられない。扇の背後は安全。
    val candidates = List(Combatant.Duelist(player))
    move(player, home.add(4, 0, 0))
    assertEquals(1, SkillTargeting.targetsInCylinder(candidates, home, 4.2, .55, 3.7).size)
    move(player, home.add(4, .9, 0))
    assertTrue(SkillTargeting.targetsInCylinder(candidates, home, 4.2, .55, 3.7).isEmpty)
    assertEquals(1, SkillTargeting.targetsInCylinder(candidates, home.add(4, 0, 0), 3.2, 7).size)
    move(player, home.add(0, 0, -3))
    assertTrue(SkillTargeting.targetsInCone(candidates, home, 6.5, 3, math.toRadians(60)).isEmpty)

    // 戦闘途中の撤去は、落ちかけた鐘も含めてすべて畳む。
    MobManager.despawnMob(boss.getUuid)
    step(3)
    assertEquals(before, displays, "一掃でモデルが残る")
    move(player, bait)
    val (second, secondStats) = spawnBoss()
    until(second, 65)
    assertTrue(displays.size > before.size + 15, "落鐘と予兆円が実体化していない")
    MobManager.despawnMob(second.getUuid)
    step(3)
    assertEquals(before, displays, "予兆中の撤去で鐘か円が残る")

    // 二人目が途中でヘイトを奪っても、既に始めた指名は移さない。
    move(player, bait)
    val (switched, _) = spawnBoss()
    until(switched, 45)
    spectator.setGameMode(GameMode.SURVIVAL)
    move(spectator, home.add(10, 1, 0))
    HateSystem.addHate(switched.getUuid, spectator.getUuid, 10000.0)
    until(switched, 65)
    move(player, home.add(5, 1, 0))
    until(switched, 148)
    assertTrue(switched.damageGate.exposedUntilMs > System.currentTimeMillis(), "指名の途中で別人の足元へ落鐘が移った")
    MobManager.despawnMob(switched.getUuid)
    spectator.setGameMode(GameMode.SPECTATOR)
    step(3)
    assertEquals(before, displays)

    // 参道で待っている者は襲わず、場外から撃った者には応戦する。
    inst.loadChunk(-3, 0).join()
    move(player, home.add(-40, 0, 0))
    val (ranged, _) = spawnBoss()
    until(ranged, 20)
    assertEquals(before.size + 15, displays.size)
    MobDamageGate.applyIncomingDamage(ranged, 20)
    HateSystem.addHate(ranged.getUuid, player.getUuid, 20)
    until(ranged, 100)
    assertTrue(displays.size > before.size + 15, "場外から一方的に撃ててしまう")
    MobManager.despawnMob(ranged.getUuid)
    step(3)
    assertEquals(before, displays)

    // 無人・観戦者だけの部屋では攻撃を開始しない。
    player.setGameMode(GameMode.SPECTATOR)
    val (third, thirdStats) = spawnBoss()
    thirdStats.hp = (thirdStats.maxHp * VesperFight.UnboundHp).toInt
    until(third, 160)
    assertEquals(before.size + 15, displays.size, "観戦者へ落鐘を撃った")
    player.setGameMode(GameMode.SURVIVAL)
    move(player, bait)
    thirdStats.hp = (thirdStats.maxHp * VesperFight.UnboundHp).toInt
    step(50)
    // 後半も一撃目の共鳴で二撃目が折れる。
    until(third, 272)
    move(player, home.add(5, 1, 0))
    until(third, 348)
    assertTrue(third.damageGate.exposedUntilMs > System.currentTimeMillis(), "後半の二撃目が共鳴で中断されない")

    // 最後の三波を跳び、校音鐘の横に落鐘を確定させて反撃へ戻る。
    thirdStats.hp = (thirdStats.maxHp * VesperFight.FinaleHp).toInt
    move(player, bait.add(0, 1, 0))
    until(third, 540)
    assertTrue(displays.size > before.size + 15, "最後の警鐘が波と落鐘を出さない")
    val exactWaves = inst.getEntities.asScala.count { e =>
      e.getEntityType == EntityTypes.ITEM_DISPLAY && (e.getEntityMeta match {
        case m: AbstractDisplayMeta => m.getTransformationInterpolationDuration == 0
        case _ => false
      })
    }
    assertTrue(exactWaves >= 3, "三波の描画がダメージ判定より遅れる")
    move(player, home.add(5, 1, 0))
    until(third, 615)
    assertTrue(third.damageGate.exposedUntilMs > System.currentTimeMillis(), "終盤の落鐘も共鳴崩しへ返せない")

    // 戦闘途中の保存は全快からの再戦。舞台と頭目の組み合わせも戻る。
    DungeonManager.persistFloorOf(inst)
    assertTrue(FloorStore.load(owner).get.bossAlive)
    val (resumed, resumedLayout) = DungeonManager.getOrCreateInstance(15, "vesper-resumed", enteredBy = Some(owner))
    resumed.loadChunk(0, 0).join()
    assertTrue(DungeonManager.restorePopulation(resumed, resumedLayout))
    step(2)
    val restoredBoss = resumed.getEntities.asScala.flatMap(VesperCreature.opt).head
    val restoredStats = MobManager.getMobStats(restoredBoss.getUuid).get
    assertEquals(restoredStats.maxHp, restoredStats.hp, "再戦で前の局面や傷が残る")
    assertEquals(layout.seed, resumedLayout.seed)
    MobManager.despawnMob(restoredBoss.getUuid)

    val keyCountBefore = player.getInventory.getItemStacks.count(s => ItemManager.getItemTypeFromStack(s).contains(ItemType.BossKey))
    thirdStats.hp = 0
    assertTrue(CombatSystem.creditMobKill(player, third, thirdStats))
    assertFalse(CombatSystem.creditMobKill(player, third, thirdStats), "同じ撃破が二重に計上される")
    step(VesperFight.DeathTicks.toInt + 4)
    assertEquals(before, displays, "撃破後にモデル・波・予兆が残る")
    val keyCountAfter = player.getInventory.getItemStacks.count(s => ItemManager.getItemTypeFromStack(s).contains(ItemType.BossKey))
    assertEquals(keyCountBefore + 1, keyCountAfter, "撃破の鍵が一度だけ渡らない")
    assertFalse(MobManager.hasLivingBoss(inst))
    BossKeyManager.openGate(inst, layout)
    DungeonManager.persistFloorOf(inst)
    assertFalse(FloorStore.load(owner).get.bossAlive)
    val (cleared, clearedLayout) = DungeonManager.getOrCreateInstance(15, "vesper-cleared", enteredBy = Some(owner))
    cleared.loadChunk(0, 0).join()
    assertTrue(DungeonManager.restorePopulation(cleared, clearedLayout))
    step(2)
    assertFalse(MobManager.hasLivingBoss(cleared), "討伐済みの番人が復活した")
    val lockX = clearedLayout.stairsWorldX - 4
    val lockZ = clearedLayout.stairsWorldZ
    cleared.loadChunk(lockX >> 4, lockZ >> 4).join()
    assertEquals(Blocks.AIR, cleared.getBlock(lockX, 2, lockZ), "開けた錠前が復元で塞がった")
  }
}
