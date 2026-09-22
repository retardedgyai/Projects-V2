package com.yuuki14202028.vfx

import net.minestom.server.coordinate.Point
import net.worldseed.multipart.GenericModel
import net.worldseed.multipart.model_bones.display_entity.ModelBonePartDisplay

/**
 * 首の傾き（pitch）追従ボーン。WSEE 組み込みの head ボーン
 * （ModelBoneHeadDisplay — オイラー Y 加算 = yaw 専用）の pitch 版で、
 * ボーン名 `pitch_` 接頭辞に対して WseeProp が boneSuppliers へ登録する。
 *
 * 回転はエンティティ回転ではなく**ボーンのピボット（bbmodel の outliner
 * origin ＝ 首の付け根）を軸に**ディスプレイ変換（setRightRotation）で掛かる。
 * エンティティ pitch では騎乗点（頭の天辺 +1.8）しか軸にできず防具と回転軸が
 * 合わないので採らない。頭装備と同じ関節位置で回すことで、頭から生えている
 * 装身具（悪魔の角）が視線の上下でも頭に張り付いたままになる。
 */
final class PitchBone(pivot: Point, name: String, rotation: Point,
                      model: GenericModel, scale: Float)
    extends ModelBonePartDisplay(pivot, name, rotation, model, scale) {

  /** WSEE 空間の X 軸オイラー角（度）。 */
  @volatile private var pitchDeg: Double = 0.0

  /**
   * 視線 pitch（Minecraft 規約: 下向き=+90）を張る。WSEE のオイラー X へは
   * **符号そのまま**で足すと「下を向く＝前（+Z）へ傾ぐ」になる——符号を反転すると
   * 逆向きに傾ぐ。
   */
  def setViewPitch(deg: Double): Unit = pitchDeg = deg

  override def getPropagatedRotation: Point =
    super.getPropagatedRotation.add(pitchDeg, 0.0, 0.0)
}
