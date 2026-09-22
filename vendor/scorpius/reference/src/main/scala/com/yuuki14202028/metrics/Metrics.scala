package com.yuuki14202028.metrics

import com.google.gson.{JsonArray, JsonObject}
import com.yuuki14202028.player.PlayerManager
import com.yuuki14202028.{Log, db}
import net.minestom.server.MinecraftServer
import net.minestom.server.event.GlobalEventHandler
import net.minestom.server.event.server.ServerTickMonitorEvent
import net.minestom.server.timer.TaskSchedule

import scala.jdk.CollectionConverters.*

/**
 * サーバー計測。集計もサンプリングもともに tick スレッド上で走るため同期は要らない。
 *
 * 出力系（statusJson / prometheus）は HTTP スレッドから呼ばれるので、volatile な不変
 * スナップショット `published` とスレッドセーフな API しか読んではいけない。
 */
object Metrics {

  /** サンプリング間隔（tick）と保持数（360本 ≒ 1時間分）。 */
  private val SampleTicks = 200
  private val HistorySize = 360

  final case class Sample(
    atMs: Long,
    tps: Double,
    avgTickMs: Double,
    maxTickMs: Double,
    players: Int,
    instances: Int,
    entities: Int,
    heapUsedMb: Long,
    heapMaxMb: Long,
    dbQueue: Int,
    errorsTotal: Long
  )

  private val startedAtMs = System.currentTimeMillis()

  // tick スレッド上でのみ更新される集計窓
  private var windowTicks = 0
  private var windowTickMsSum = 0.0
  private var windowTickMsMax = 0.0
  private var windowStartMs = System.currentTimeMillis()

  private val ring = scala.collection.mutable.ArrayDeque.empty[Sample]
  @volatile private var published: Vector[Sample] = Vector.empty

  def init(handler: GlobalEventHandler): Unit = {
    handler.addListener(classOf[ServerTickMonitorEvent], (event: ServerTickMonitorEvent) => {
      val ms = event.getTickMonitor.getTickTime
      windowTicks += 1
      windowTickMsSum += ms
      windowTickMsMax = math.max(windowTickMsMax, ms)
    })
    MinecraftServer.getSchedulerManager.scheduleTask(
      () => sample(),
      TaskSchedule.tick(SampleTicks),
      TaskSchedule.tick(SampleTicks)
    )
    Log.info("Metrics", s"計測開始（${SampleTicks / 20}秒間隔・${HistorySize}本保持）")
  }

  private def sample(): Unit = {
    val now       = System.currentTimeMillis()
    val elapsedS  = math.max(0.001, (now - windowStartMs) / 1000.0)
    val runtime   = Runtime.getRuntime
    val instances = MinecraftServer.getInstanceManager.getInstances
    val s = Sample(
      atMs        = now,
      tps         = math.min(20.0, windowTicks / elapsedS),
      avgTickMs   = if windowTicks == 0 then 0.0 else windowTickMsSum / windowTicks,
      maxTickMs   = windowTickMsMax,
      players     = MinecraftServer.getConnectionManager.getOnlinePlayers.size,
      instances   = instances.size,
      entities    = instances.asScala.map(_.getEntities.size).sum,
      heapUsedMb  = (runtime.totalMemory() - runtime.freeMemory()) / 1048576,
      heapMaxMb   = runtime.maxMemory() / 1048576,
      dbQueue     = db.Database.pendingWrites,
      errorsTotal = Log.errorCount
    )
    windowTicks = 0
    windowTickMsSum = 0.0
    windowTickMsMax = 0.0
    windowStartMs = now

    ring.append(s)
    while ring.size > HistorySize do ring.removeHead()
    published = ring.toVector
  }

  def statusJson(): String = {
    val history = published
    val root = JsonObject()
    root.addProperty("uptimeMs", System.currentTimeMillis() - startedAtMs)

    val arr = JsonArray()
    history.foreach(s => arr.add(sampleJson(s)))
    root.add("history", arr)
    history.lastOption.foreach(s => root.add("now", sampleJson(s)))

    val players = JsonArray()
    MinecraftServer.getConnectionManager.getOnlinePlayers.asScala.foreach { p =>
      // getStats は computeIfAbsent なので、走査中に切断した相手のエントリを
      // 復活させ statsMap に残してしまう。読むだけなら peekStats（作らない版）を使う
      val stats = PlayerManager.peekStats(p.getUuid)
      val o = JsonObject()
      o.addProperty("name", p.getUsername)
      o.addProperty("level", stats.map(_.level).getOrElse(0))
      o.addProperty("floor", stats.map(_.floor).getOrElse(0))
      o.addProperty("ping", p.getLatency)
      players.add(o)
    }
    root.add("players", players)
    root.toString
  }

  private def sampleJson(s: Sample): JsonObject = {
    val o = JsonObject()
    o.addProperty("at", s.atMs)
    o.addProperty("tps", math.round(s.tps * 10.0) / 10.0)
    o.addProperty("avgTickMs", math.round(s.avgTickMs * 100.0) / 100.0)
    o.addProperty("maxTickMs", math.round(s.maxTickMs * 100.0) / 100.0)
    o.addProperty("players", s.players)
    o.addProperty("instances", s.instances)
    o.addProperty("entities", s.entities)
    o.addProperty("heapUsedMb", s.heapUsedMb)
    o.addProperty("heapMaxMb", s.heapMaxMb)
    o.addProperty("dbQueue", s.dbQueue)
    o.addProperty("errorsTotal", s.errorsTotal)
    o
  }

  /** 外部監視（Grafana / Uptime-Kuma 等）の接続口。 */
  def prometheus(): String = {
    val s = published.lastOption
    val sb = StringBuilder()
    def gauge(name: String, help: String, value: Any): Unit = {
      sb ++= s"# HELP scorpius_$name $help\n# TYPE scorpius_$name gauge\nscorpius_$name $value\n"
    }
    gauge("up", "server is running", 1)
    gauge("uptime_seconds", "seconds since start", (System.currentTimeMillis() - startedAtMs) / 1000)
    s.foreach { v =>
      gauge("tps", "ticks per second (window avg)", v.tps)
      gauge("tick_ms_avg", "average tick duration ms", v.avgTickMs)
      gauge("tick_ms_max", "max tick duration ms in window", v.maxTickMs)
      gauge("players", "online players", v.players)
      gauge("instances", "registered instances", v.instances)
      gauge("entities", "total entities", v.entities)
      gauge("heap_used_bytes", "JVM heap used", v.heapUsedMb * 1048576)
      gauge("heap_max_bytes", "JVM heap max", v.heapMaxMb * 1048576)
      gauge("db_queue", "pending DB writes", v.dbQueue)
      gauge("errors_total", "logged errors since start", v.errorsTotal)
    }
    sb.result()
  }
}
