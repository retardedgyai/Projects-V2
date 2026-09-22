package com.yuuki14202028.vfx

import com.yuuki14202028.EntityTypes
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.network.packet.server.play.SetPassengersPacket
import net.worldseed.multipart.GenericModelImpl

import scala.collection.mutable
import scala.jdk.CollectionConverters.*

/**
 * アニメを持たない単発の WSEE モデルプロップ（弾・予兆円など）の汎用ラッパー。
 */
final class WseeProp(modelId: String, instance: Instance, initial: Pos, scale: Float) {
  private val model: GenericModelImpl =
    new GenericModelImpl {
      override def getId: String = modelId
      /**
       * ボーン名 `pitch_*` を首の傾き追従ボーン [[PitchBone]] として拾う。
       * boneSuppliers は WSEE 正規の拡張点で、yaw 専用の head ボーンと同じ仕組みに乗る。
       */
      override protected def registerBoneSuppliers(): Unit = {
        super.registerBoneSuppliers()
        boneSuppliers.put(
          (name: String) => name.startsWith("pitch_"),
          info => PitchBone(info.pivot, info.name, info.rotation, info.model, info.scale))
      }
    }
  model.init(instance, initial, scale)

  private val pitchBones: List[PitchBone] =
    model.getParts.asScala.collect { case b: PitchBone => b }.toList

  private val viewers = mutable.Set[Player]()

  /** 騎乗追従（[[mountOn]]）中の乗せ先。null なら通常の setPos 駆動。 */
  private var mountedOn: Player = null

  def setPos(p: Pos): Unit = {
    model.setPosition(p)
    model.draw()
  }

  def setScale(s: Float): Unit = {
    model.setGlobalScale(s)
    model.draw()
  }

  /** 床の移動する波など、補間の遅れが回避判定を誤読させるプロップで指定する。 */
  def setTransformationInterpolationTicks(ticks: Int): Unit = {
    model.getParts.asScala.foreach { part =>
      val e = part.getEntity
      if (e != null) e.getEntityMeta match {
        case m: net.minestom.server.entity.metadata.display.AbstractDisplayMeta =>
          m.setTransformationInterpolationDuration(ticks)
        case _ => ()
      }
    }
  }

  def setRotation(yawDeg: Double): Unit = {
    model.setGlobalRotation(yawDeg)
    model.draw()
  }

  /**
   * `pitch_*` ボーンへ視線 pitch を張る。
   * 描画は直後の setRotation（毎tick）に乗るので、ここでは draw しない。
   */
  def setViewPitch(deg: Double): Unit =
    pitchBones.foreach(_.setViewPitch(deg))

  def setGlow(color: net.kyori.adventure.util.RGBLike): Unit =
    model.setGlowing(color)

  /**
   * 全ボーン（ITEM_DISPLAY）に明るさ最大の brightness override を焼く。
   * ディスプレイエンティティはその場のブロック光で陰影がつくため、焼き込み彩色
   * （glow なし）のモデルは暗いダンジョンでほぼ黒く沈んで見えなくなる。
   * 陣・領域など「見えることが情報」の演出はこれでフルブライトにする。
   */
  def setFullbright(): Unit =
    model.getParts.asScala.foreach { part =>
      val e = part.getEntity
      if e != null then e.getEntityMeta match {
        case m: net.minestom.server.entity.metadata.display.AbstractDisplayMeta =>
          m.setBrightness(15, 15)
        case _ => ()
      }
    }

  /**
   * ボーン（ITEM_DISPLAY）をプレイヤー本体の同乗者にする（SkillVfx.ride 用）。
   *
   * 毎tickの setPos はサーバーが位置を知る1tick遅れ＋クライアントの移動補間でプレイヤーに
   * 遅れて見えるが、騎乗させれば追従はクライアントが毎フレーム行うので遅延なく張り付く。
   * 乗せた後は setPos を呼ばないこと（位置は乗り物が決める）。
   *
   * クライアントは同乗者をプレイヤーの当たり判定の最上端（立ち姿勢で足元+1.8ブロック）へ
   * 置くため、ride 用モデルは接地面ではなく騎乗原点を基準に組む（gen_spellvfx.py の
   * rock_brace 参照）。
   *
   * 複数プロップの同時騎乗（例: コスメの紋＋天使の羽＋スペルの衣）は WseeProp 側の
   * マウントレジストリで合成する — SetPassengersPacket は「乗り物1体の同乗者リスト全体」を上書き
   * するため、各プロップが自分のボーンだけを送ると最後の1つしか残らない。常に登録済み
   * 全プロップのボーンをまとめた合成パケットを送る。
   */
  def mountOn(rider: Player): Unit = {
    mountedOn = rider
    model.addPartsAsPassengers(rider)   // 実際の騎乗は下の合成パケットが決める。これは WSEE 内部状態の更新用
    WseeProp.register(rider, this)
    WseeProp.broadcastMounts(rider)
  }

  /** 合成パケットに載せるボーンのエンティティID。騎乗するのは ITEM_DISPLAY ボーンのみ。 */
  private[vfx] def partIds: List[java.lang.Integer] =
    model.getParts.asScala.iterator
      .map(_.getEntity)
      .filter(e => e != null && e.getEntityType == EntityTypes.ITEM_DISPLAY)
      .map(e => java.lang.Integer.valueOf(e.getEntityId))
      .toList

  def syncViewers(): Unit = {
    val cur = instance.getPlayers.asScala.toSet
    val newcomers = cur.diff(viewers)
    newcomers.foreach(model.addViewer)
    viewers.diff(cur).foreach(model.removeViewer)
    // WSEE は新規 viewer にボーンをルート（RootBoneEntity.updateNewViewer）へ乗せ直す
    // SetPassengersPacket を送るので、その後にプレイヤー本体への騎乗（全プロップ合成）を
    // 送り直して勝たせる
    if mountedOn != null && newcomers.nonEmpty then {
      val pkt = WseeProp.combinedPacket(mountedOn)
      newcomers.foreach(_.sendPacket(pkt))
    }
    viewers.clear()
    viewers ++= cur
  }

  def destroy(): Unit = {
    if mountedOn != null then {
      val rider = mountedOn
      mountedOn = null
      WseeProp.unregister(rider, this)
      // 残りのプロップの騎乗リストを送り直す（自ボーンは despawn で消える）
      WseeProp.broadcastMounts(rider)
    }
    viewers.foreach(model.removeViewer)
    viewers.clear()
    model.destroy()
  }
}

object WseeProp {
  import java.util.UUID
  import java.util.concurrent.ConcurrentHashMap

  /** プレイヤーごとの騎乗中プロップ。合成 SetPassengersPacket の材料。 */
  private val mounts = ConcurrentHashMap[UUID, mutable.Set[WseeProp]]()

  private def register(rider: Player, source: WseeProp): Unit =
    mounts.computeIfAbsent(rider.getUuid, _ => mutable.Set()).synchronized {
      mounts.get(rider.getUuid) += source
    }

  private def unregister(rider: Player, source: WseeProp): Unit = {
    val set = mounts.get(rider.getUuid)
    if set != null then {
      set.synchronized { set -= source }
      if set.isEmpty then mounts.remove(rider.getUuid)
    }
  }

  private[vfx] def combinedPacket(rider: Player): SetPassengersPacket = {
    val set = mounts.get(rider.getUuid)
    val ids =
      if set == null then Nil
      else set.synchronized { set.toList }.flatMap(_.partIds)
    new SetPassengersPacket(rider.getEntityId, ids.asJava)
  }

  private def broadcastMounts(rider: Player): Unit = {
    val inst = rider.getInstance
    if inst != null then {
      val pkt = combinedPacket(rider)
      inst.getPlayers.asScala.foreach(_.sendPacket(pkt))
    }
  }
}
