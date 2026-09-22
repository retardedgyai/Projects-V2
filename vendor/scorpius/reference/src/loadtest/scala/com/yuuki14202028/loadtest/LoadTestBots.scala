package com.yuuki14202028.loadtest

import org.geysermc.mcprotocollib.network.Session
import org.geysermc.mcprotocollib.network.event.session.{DisconnectedEvent, SessionAdapter}
import org.geysermc.mcprotocollib.network.packet.Packet
import org.geysermc.mcprotocollib.network.session.ClientNetworkSession
import org.geysermc.mcprotocollib.auth.GameProfile
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol
import org.geysermc.mcprotocollib.protocol.data.game.ResourcePackStatus
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundResourcePackPushPacket
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundResourcePackPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}
import scala.util.Random

/**
 * MCProtocolLib で実プロトコルを喋る負荷テストボット。ダミー接続ではなく本物の
 * ハンドシェイクを踏むため、クライアントライブラリのプロトコル版はサーバーと同じ
 * 26.2 / protocol 776 に揃っている必要がある。
 *
 * 接続先サーバーは `AUTH_MODE=offline LOADTEST_ENABLED=1` で、かつ**本番とは別の
 * 作業ディレクトリから**起動しておくこと（オフライン認証は成りすまし可・DB を汚すため）。
 * `/ltenter` は LOADTEST_ENABLED のときだけ登録される、NPC GUI を操作できない
 * ボット用のダンジョン入場口。
 *
 * このプロセス自体は負荷をかけるだけで何も計測しない — TPS・tick 時間・ヒープは
 * 本体の管理ダッシュボード（http://127.0.0.1:9090/）で見る。
 *
 * 実行: ./gradlew loadTestBots -Pargs="<host> <port> <体数> <lobby|dungeon> [ramp/秒=2] [継続秒=300]"
 */
object LoadTestBots {

  private val rng = Random()

  private final class Bot(
    val name: String,
    host: String,
    port: Int,
    scenario: String,
    scheduler: ScheduledExecutorService,
    stats: Stats
  ) {
    @volatile private var x = 0.0
    @volatile private var y = 0.0
    @volatile private var z = 0.0
    @volatile private var joined = false
    @volatile private var closed = false

    // 名前から UUID を決める（バニラのオフライン規約と同じ）。MinecraftProtocol(name) の
    // 既定は毎回ランダム UUID で、そのままだと接続ごとに別人になる＝保存済みのラン・
    // 攻略中フロアの復元経路を負荷テストで踏めず、DB の行も毎回増える
    private val protocol = new MinecraftProtocol(
      new GameProfile(
        UUID.nameUUIDFromBytes(s"OfflinePlayer:$name".getBytes(StandardCharsets.UTF_8)), name),
      null)
    private val session = new ClientNetworkSession(
      new InetSocketAddress(host, port), protocol, scheduler, null, null)

    def connect(): Unit = {
      session.addListener(new SessionAdapter {
        override def packetReceived(s: Session, packet: Packet): Unit =
          try handle(packet)
          catch case ex: Exception => stats.errors.incrementAndGet()

        override def disconnected(event: DisconnectedEvent): Unit = {
          stats.disconnected.incrementAndGet()
          if !closed then
            println(s"[$name] 切断: ${event.getReason}")
        }
      })
      session.connect()
    }

    private def handle(packet: Packet): Unit =
      packet match {
        case p: ClientboundLoginPacket =>
          joined = true
          stats.joined.incrementAndGet()
          if scenario == "dungeon" then
            // 入場＝インスタンス生成なので、参加直後に一斉送信すると接続ランプが台無しになる。
            // 乱数で散らして生成コストを均す
            scheduler.schedule(
              (() => if !closed then session.send(new ServerboundChatCommandPacket("ltenter"))): Runnable,
              3000 + rng.nextInt(5000), TimeUnit.MILLISECONDS)

        case p: ClientboundPlayerPositionPacket =>
          // 相対フラグは解釈せず常に絶対座標として採用する（ボットは正しい位置に居る必要がなく、
          // 実クライアント同等の相対合成は割に合わない）。confirm を返さないとサーバーが
          // 以後の移動を無視するので、承諾と同時に現在地を送り返して同期を閉じる
          val pos = p.getPosition
          x = pos.getX; y = pos.getY; z = pos.getZ
          session.send(new ServerboundAcceptTeleportationPacket(p.getId))
          session.send(new ServerboundMovePlayerPosPacket(true, false, x, y, z))

        case p: ClientboundResourcePackPushPacket =>
          // 実際にはダウンロードしないが、終端ステータスまで返さないとサーバーが
          // 適用完了を待ち続けて入場が進まない。ACCEPTED だけでは足りない
          session.send(new ServerboundResourcePackPacket(p.getId, ResourcePackStatus.ACCEPTED))
          session.send(new ServerboundResourcePackPacket(p.getId, ResourcePackStatus.SUCCESSFULLY_LOADED))

        case _ => ()
      }

    /**
     * ランダムウォークを1歩進める。狙いは「モブAIの標的として動き続けること」だけなので
     * 地形も衝突も見ない（壁にめり込んだ座標を送っても負荷計測には支障がない）。
     *
     * x==0 かつ z==0 は「位置パケットをまだ受けていない」の番兵 — 初期値のまま歩き出すと
     * ワールド原点から移動を送ることになるので、同期が済むまで何も送らない。
     */
    def wanderStep(): Unit = {
      if closed || !joined || (x == 0.0 && z == 0.0) then return
      x += (rng.nextDouble() - 0.5) * 0.8
      z += (rng.nextDouble() - 0.5) * 0.8
      session.send(new ServerboundMovePlayerPosPacket(true, false, x, y, z))
      stats.moves.incrementAndGet()
    }

    def close(): Unit = {
      closed = true
      try session.disconnect("load test finished")
      catch case _: Exception => ()
    }
  }

  private final class Stats {
    val joined = new AtomicInteger(0)
    val disconnected = new AtomicInteger(0)
    val errors = new AtomicLong(0)
    val moves = new AtomicLong(0)
  }

  def main(args: Array[String]): Unit = {
    if args.length < 4 then {
      println("使い方: loadTestBots -Pargs=\"<host> <port> <体数> <lobby|dungeon> [ramp/秒=2] [継続秒=300]\"")
      sys.exit(1)
    }
    val host      = args(0)
    val port      = args(1).toInt
    val count     = args(2).toInt
    val scenario  = args(3)
    val rampPerS  = if args.length > 4 then args(4).toInt else 2
    val duration  = if args.length > 5 then args(5).toInt else 300
    require(scenario == "lobby" || scenario == "dungeon", "シナリオは lobby か dungeon")

    val scheduler = Executors.newScheduledThreadPool(4)
    val stats = new Stats
    val bots = new java.util.concurrent.CopyOnWriteArrayList[Bot]()

    println(s"=== 負荷テスト開始: $host:$port へ $count 体（$scenario、${rampPerS}体/秒、${duration}秒） ===")

    // 一斉接続はログイン処理だけで詰まって「同時接続数の負荷」を測れないため、rampPerS 体/秒で開栓する
    for i <- 0 until count do
      scheduler.schedule((() => {
        // LoadBot_ 接頭辞はサーバー側の PlayerSession.resolveSkin が Mojang 照会を飛ばす目印
        val bot = new Bot(f"LoadBot_$i%03d", host, port, scenario, scheduler, stats)
        bots.add(bot)
        try bot.connect()
        catch case ex: Exception => {
          stats.errors.incrementAndGet()
          println(s"[LoadBot_$i] 接続失敗: ${ex.getMessage}")
        }
      }): Runnable, (i.toLong * 1000) / rampPerS, TimeUnit.MILLISECONDS)

    // 全ボットを1本のタスクでまとめて歩かせる。初回は 2 秒待って先頭のボットのログインを跨ぐ
    scheduler.scheduleAtFixedRate((() => {
      bots.forEach(_.wanderStep())
    }): Runnable, 2000, 250, TimeUnit.MILLISECONDS)

    scheduler.scheduleAtFixedRate((() => {
      println(s"[進捗] 参加 ${stats.joined.get()}/${count}・切断 ${stats.disconnected.get()}" +
        s"・移動送信 ${stats.moves.get()}・エラー ${stats.errors.get()}")
    }): Runnable, 10, 10, TimeUnit.SECONDS)

    Thread.sleep(duration.toLong * 1000)

    println("=== 終了: 全ボットを切断 ===")
    bots.forEach(_.close())
    // 切断パケットが送り切られる前にスケジューラを落とすと、サーバー側にタイムアウト待ちの
    // 幽霊プレイヤーが残って次の計測を汚すので猶予を置く
    Thread.sleep(3000)
    scheduler.shutdownNow()
    println(s"=== 結果: 参加 ${stats.joined.get()}/${count}・切断 ${stats.disconnected.get()}" +
      s"・移動送信 ${stats.moves.get()}・エラー ${stats.errors.get()} ===")
    println("TPS・tick時間・ヒープの推移は管理ダッシュボード（/status.json の履歴）で確認してください")
    // ネットワーク層の非デーモンスレッドが残って main の復帰では JVM が終わらないため明示的に落とす
    sys.exit(0)
  }
}
