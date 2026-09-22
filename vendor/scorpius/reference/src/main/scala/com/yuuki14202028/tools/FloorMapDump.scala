package com.yuuki14202028.tools

import com.yuuki14202028.db.Database
import com.yuuki14202028.dungeon.{DungeonManager, FloorStore}
import net.minestom.server.MinecraftServer

import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.{Files, Paths}
import java.util.UUID
import javax.imageio.ImageIO

/**
 * ボス部屋を真上から色分けして PNG に落とす確認用ツール（gradle task `floorMapDump`）。
 * 実機を起動せずに「床の模様・柱の配置・部屋の形」を目で確かめるために使う。
 *
 * 引数: [階] [出力パス] [倍率 px/ブロック]
 */
object FloorMapDump {

  private def colorOf(name: String, over: String): Int = {
    val n = name.stripPrefix("minecraft:")
    val o = over.stripPrefix("minecraft:")
    // 宝物殿（黒石・金・玄武岩）は generic な "chiseled"/"stone_bricks" の判定より先に拾う
    if (o.contains("lava")) 0xff6a00
    else if (o.contains("magma")) 0xa03a1a
    else if (o.contains("raw_gold")) 0xe0a030
    else if (o.contains("gold_block")) 0xffd633
    else if (o.contains("gilded_blackstone")) 0xb8860b
    else if (o.contains("shroomlight")) 0xffb060
    else if (o.contains("basalt")) 0x9a9aa4                  // 列柱・門柱の柱身
    else if (o.contains("blackstone_brick_wall")) 0x5a5560   // 宝物庫の塀
    else if (o.contains("chiseled_polished_blackstone")) 0x6d6472
    else if (o.contains("blackstone")) 0x3b3741
    else if (o.contains("copper")) 0xb5714a
    else if (o.contains("anvil") || o.contains("furnace")) 0x686975
    else if o.contains("log") || o.contains("brick") && o.contains("moss") then 0x6b4b2a
    else if o.contains("pitcher") then 0xc44e84
    else if o.contains("poppy") || o.contains("dandelion") || o.contains("cornflower") ||
             o.contains("oxeye") || o.contains("lily_of") then 0xf2e06a
    else if o.contains("fern") || o.contains("grass") then 0x87b45a
    else if o.contains("leaves") then 0x2f5d2f
    else if o.contains("iron_bars") || o.contains("portal") then 0xd0d0d0
    else if o.contains("soul_fire") then 0x2fd6d6      // 大燭台の魂の火
    else if o.contains("chain") || o.contains("lantern") then 0xdcd7a0
    else if o.contains("wool") then 0x2f8f9f
    else if o.contains("chiseled") then 0xe0d8c0       // 柱・玉座・墓標の鑿加工
    else if o.contains("slab") then 0xa89f8c           // 石棺の蓋
    else if o.contains("cobweb") then 0xf0f0f0
    else if o.contains("stone_bricks") || o.contains("deepslate") then 0x8a8677  // 柱・壇
    else n match {
      case "stone"                 => 0x1b1b1f  // 部屋の外
      case "air"                   => 0x000000
      case "moss_block"            => 0x5b8a3c
      case "grass_block"           => 0x74a34a
      case "smooth_stone"          => 0xb8b8b8
      case "polished_andesite"     => 0x92948f
      case "mossy_stone_bricks"    => 0x77836a
      case "gilded_blackstone"     => 0xc9a227
      case "nether_bricks"         => 0x4a2226
      case "polished_blackstone"   => 0x2e2b31
      case "polished_blackstone_bricks"         => 0x3b3741
      case "cracked_polished_blackstone_bricks" => 0x34313a
      case "chiseled_polished_blackstone"       => 0x6d6472
      case "blackstone"            => 0x26242b
      case "gold_block"            => 0xffd633
      case "raw_gold_block"        => 0xe0a030
      case "polished_basalt"       => 0x9a9aa4
      case "basalt"                => 0x6e6e76
      case "magma_block"           => 0xa03a1a
      case "lava"                  => 0xff6a00
      case "shroomlight"           => 0xffb060
      case "crying_obsidian"       => 0x2a0f45
      case "stone_bricks"          => 0x6f6f68
      case "cracked_stone_bricks"  => 0x5f5f58
      case "chiseled_stone_bricks" => 0xa39d8a
      case "polished_deepslate"    => 0x3a3a3e
      case "deepslate_bricks"      => 0x33333a
      case "deepslate_tiles"       => 0x2c3038
      case "chiseled_deepslate"    => 0x58585e
      case "waxed_cut_copper"      => 0xc58055
      case "waxed_exposed_cut_copper" => 0x9b7557
      case "waxed_oxidized_cut_copper" => 0x47877c
      case "stripped_dark_oak_log" => 0x57402b
      case "sea_lantern"           => 0xbfe6e6
      case _                       => 0xff00ff
    }
  }

  def main(args: Array[String]): Unit = {
    MinecraftServer.init()
    Database.init(Files.createTempDirectory("floor-map-dump").resolve("dump.db"))
    FloorStore.init()

    val floor = if args.nonEmpty then args(0).toInt else 10
    val out   = if args.length > 1 then args(1) else s"floor$floor.png"

    val (inst, layout) = DungeonManager.getOrCreateInstance(
      floor, s"floor-map-dump-$floor", partySize = 1, enteredBy = Some(UUID.randomUUID()))
    val room   = layout.bossRoom.getOrElse(sys.error("ボス部屋が無い"))
    val offset = layout.size / 2
    val cx = room.centerX - offset
    val cz = room.centerZ - offset
    val r  = 34

    // 1ブロック＝4px（第3引数で上書き）。原寸だと模様も柱も潰れて見分けが付かない
    val scale = if args.length > 2 then args(2).toInt else 4
    val img = BufferedImage((2 * r + 1) * scale, (2 * r + 1) * scale, BufferedImage.TYPE_INT_RGB)
    for dz <- -r to r; dx <- -r to r do {
      val bx = cx + dx
      val bz = cz + dz
      inst.loadChunk(bx >> 4, bz >> 4).join()
      val ground = inst.getBlock(bx, 1, bz).name()
      val over   = inst.getBlock(bx, 4, bz).name()
      val over2  = inst.getBlock(bx, 2, bz).name()
      val pick   = if over.stripPrefix("minecraft:") != "air" then over else over2
      val rgb    = colorOf(ground, pick)
      for py <- 0 until scale; px <- 0 until scale do
        img.setRGB((dx + r) * scale + px, (dz + r) * scale + py, rgb)
    }
    ImageIO.write(img, "png", File(out))
    println(s"[FloorMapDump] wrote $out (floor $floor, center=($cx,$cz))")
    System.exit(0)
  }
}
