package com.yuuki14202028.admin

import com.yuuki14202028.{Log, TaskSlot}
import com.yuuki14202028.player.RunStateManager
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.timer.TaskSchedule

import scala.jdk.CollectionConverters.*

/**
 * 緩やかな再起動フロー。
 *
 * データ保全自体はシャットダウンフック（Database 経由）が担保するので、このフローが
 * 解くのは「試合が巻き戻る・戦闘の最中に突然切れる」というプレイヤー体験の問題である。
 * そのため猶予中は告知を重ね、闘技場の新規試合も止める
 * （ArenaManager.validateEntrant が [[blocksNewMatches]] を見る）。
 *
 * プロセスの再立ち上げはこの object の外側（systemd の `Restart=always` 等）が担う。
 * ここは正常終了（exit 0）までしか行わない。
 */
object RestartManager {

  private val Tag = "Restart"

  /** 始まった試合が期限を跨がないよう、この残り時間を切ったら新規試合を受け付けない。 */
  private val BlockMatchesWithinMs = 10 * 60 * 1000L

  /** 告知する残り時間（秒）。 */
  private val Checkpoints = List(1800L, 600L, 300L, 180L, 60L, 30L, 10L, 5L, 4L, 3L, 2L, 1L)

  @volatile private var deadlineMs: Long = 0L
  private val tickTask = TaskSlot()
  private val announced = scala.collection.mutable.Set.empty[Long]

  def isPending: Boolean = deadlineMs > 0L
  def remainingMs: Long = if isPending then math.max(0L, deadlineMs - System.currentTimeMillis()) else 0L

  def blocksNewMatches: Boolean = isPending && remainingMs <= BlockMatchesWithinMs

  def statusLabel: Option[String] =
    if isPending then Some(s"${remainLabel(remainingMs / 1000)}後に再起動予定") else None

  /**
   * 再起動を予約する。
   *
   * tick スレッドから呼ぶこと — [[announced]] とタスク参照は tick 側と共有する非同期化されない
   * 状態なので、他スレッドから触ると壊れる（コマンド経由なら自然に満たされる）。
   */
  def schedule(durationMs: Long, by: String): Either[String, String] = {
    if isPending then return Left("§c既に再起動が予定されている（/restart cancel で中止）")
    if durationMs < 0 then return Left("§c期間が不正")
    deadlineMs = System.currentTimeMillis() + durationMs
    announced.clear()
    Log.info(Tag, s"$by が ${durationMs / 1000}秒後の再起動を予約しました")
    if durationMs == 0 then executeRestart()
    else
      tickTask.hold(MinecraftServer.getSchedulerManager.scheduleTask(
        () => tick(),
        TaskSchedule.tick(10),
        TaskSchedule.tick(10)
      ))
    Right(s"§e${remainLabel(math.max(1, durationMs / 1000))}後の再起動を予約した")
  }

  def cancel(): Boolean = {
    if !isPending then return false
    deadlineMs = 0L
    tickTask.cancel()
    broadcast("§6[告知] §a予定されていた再起動は中止された")
    Log.info(Tag, "再起動を中止しました")
    true
  }

  private def tick(): Unit = {
    val remaining = remainingMs
    if remaining <= 0 then {
      executeRestart()
      return
    }
    // 越えたチェックポイントはまとめて既読にしつつ、告知は最小の1つだけ。
    // 「10分後に再起動」のような短い予約でも、上位の告知が一斉に流れない。
    val crossed = Checkpoints.filter(cp => !announced.contains(cp) && remaining <= cp * 1000)
    if crossed.nonEmpty then {
      announced ++= crossed
      val cp = crossed.min
      if cp <= 10 then broadcast(s"§6[告知] §c再起動まで $cp 秒")
      else broadcast(s"§6[告知] §e${remainLabel(cp)}後にサーバーを再起動する。安全な場所で待機を")
    }
  }

  private def executeRestart(): Unit = {
    deadlineMs = 0L
    tickTask.cancel()
    val players = MinecraftServer.getConnectionManager.getOnlinePlayers.asScala.toSeq
    Log.info(Tag, s"再起動シーケンス開始（オンライン ${players.size}名）")
    players.foreach { p =>
      try RunStateManager.saveRun(p)
      catch case ex: Exception => Log.error(Tag, s"${p.getUsername} のラン保存に失敗", ex)
    }
    players.foreach(_.kick(Component.text(
      "§6サーバー再起動のため、いったん退出となる\n§7進行は保存済み — まもなく戻って続きから遊べる")))
    // キックのパケット送出と切断処理を流し切ってから停止する。
    // tick スレッドを止めずに待つため別スレッドで寝かせる（最終保存はシャットダウンフック）。
    new Thread(() => {
      Thread.sleep(2000)
      try MinecraftServer.stopCleanly()
      catch case ex: Exception => Log.warn(Tag, "stopCleanly に失敗（そのまま終了します）", ex)
      System.exit(0)
    }, "scorpius-restart").start()
  }

  private def broadcast(message: String): Unit = {
    Log.info(Tag, message.replaceAll("§.", ""))
    MinecraftServer.getConnectionManager.getOnlinePlayers.asScala
      .foreach(_.sendMessage(Component.text(message)))
  }

  private def remainLabel(seconds: Long): String =
    if seconds >= 3600 then s"${seconds / 3600}時間"
    else if seconds >= 60 then s"${seconds / 60}分"
    else s"${seconds}秒"
}
