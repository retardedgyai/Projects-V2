package com.yuuki14202028.tools

import com.yuuki14202028.Blocks
import net.minestom.server.instance.Instance
import com.yuuki14202028.lobby.{LobbyLayout, LobbyManager}
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.block.{Block, BlockFace}
import net.minestom.server.map.MapColors

import java.awt.{BasicStroke, Color, Font, GradientPaint, RenderingHints}
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.nio.file.{Files, Path, Paths}
import javax.imageio.ImageIO
import scala.collection.mutable

/** 実チャンクから造形確認用の三面図を出力する。テクスチャ・空・照明はクライアント描画の代用ではない。 */
object LobbyPreview {
  private case class Face(vertices: Vector[Vec], normal: Vec, rgb: Int, luminous: Boolean) {
    val center: Vec = vertices.reduce(_.add(_)).mul(0.25)
  }
  private case class Box(low: Vec, high: Vec)

  private val directions = Vector(
    (Vec(1, 0, 0), BlockFace.WEST), (Vec(-1, 0, 0), BlockFace.EAST),
    (Vec(0, 1, 0), BlockFace.BOTTOM), (Vec(0, -1, 0), BlockFace.TOP),
    (Vec(0, 0, 1), BlockFace.NORTH), (Vec(0, 0, -1), BlockFace.SOUTH)
  )

  private def color(block: Block): Int = {
    val name = block.name()
    if name.contains("end_gateway") then 0x3e3c72
    else if name.contains("soul_lantern") || name.contains("sea_lantern") then 0xaff1e8
    else if name.contains("lantern") || name.contains("froglight") then 0xffd68a
    else if name.contains("tuff") then 0x74776c
    else if name.contains("sandstone") && !name.contains("red_") then 0xd6c7a0
    else if name.contains("oxidized") then 0x4c9b83
    else if name.contains("weathered") then 0x739278
    else if name.contains("deepslate") then 0x454550
    else if name.contains("blackstone") then 0x35323a
    else if name.contains("spruce") then 0x715338
    else if name.contains("dark_oak") then 0x4c3627
    else if name.contains("flowering") then 0x7c9770
    else if name.contains("leaves") then 0x547552
    else {
      val palette = MapColors.values()(block.mapColorId())
      (palette.red() << 16) | (palette.green() << 8) | palette.blue()
    }
  }

  private def boxes(block: Block): Vector[Box] = {
    val name = block.name()
    val full = Box(Vec(0, 0, 0), Vec(1, 1, 1))
    if name.endsWith("stairs") then {
      val top = block.getProperty("half") == "top"
      val base = Box(Vec(0, if top then 0.5 else 0, 0), Vec(1, if top then 1 else 0.5, 1))
      val lowY = if top then 0 else 0.5
      val highY = if top then 0.5 else 1
      val step = block.getProperty("facing") match {
        case "east" => Box(Vec(0.5, lowY, 0), Vec(1, highY, 1))
        case "west" => Box(Vec(0, lowY, 0), Vec(0.5, highY, 1))
        case "south" => Box(Vec(0, lowY, 0.5), Vec(1, highY, 1))
        case _ => Box(Vec(0, lowY, 0), Vec(1, highY, 0.5))
      }
      Vector(base, step)
    }
    else if block.collisionShape().relativeEnd().y() > 0 then
      Vector(Box(block.collisionShape().relativeStart().asVec(), block.collisionShape().relativeEnd().asVec()))
    else if name.contains("chain") || name.contains("rod") then Vector(Box(Vec(0.4, 0, 0.4), Vec(0.6, 1, 0.6)))
    else Vector(full)
  }

  private def faces(box: Box): Vector[Vector[Vec]] = {
    val a = box.low
    val b = box.high
    Vector(
      Vector(Vec(b.x, a.y, a.z), Vec(b.x, b.y, a.z), Vec(b.x, b.y, b.z), Vec(b.x, a.y, b.z)),
      Vector(Vec(a.x, a.y, b.z), Vec(a.x, b.y, b.z), Vec(a.x, b.y, a.z), Vec(a.x, a.y, a.z)),
      Vector(Vec(a.x, b.y, a.z), Vec(a.x, b.y, b.z), Vec(b.x, b.y, b.z), Vec(b.x, b.y, a.z)),
      Vector(Vec(a.x, a.y, b.z), Vec(a.x, a.y, a.z), Vec(b.x, a.y, a.z), Vec(b.x, a.y, b.z)),
      Vector(Vec(b.x, a.y, b.z), Vec(b.x, b.y, b.z), Vec(a.x, b.y, b.z), Vec(a.x, a.y, b.z)),
      Vector(Vec(a.x, a.y, a.z), Vec(a.x, b.y, a.z), Vec(b.x, b.y, a.z), Vec(b.x, a.y, a.z))
    )
  }

  private def render(mesh: Vector[Face], camera: Vec, forward: Vec, out: Path): Unit = {
    val width = 1600
    val height = 1000
    val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setPaint(GradientPaint(0f, 0f, Color(0x29273b), 0f, height.toFloat, Color(0xb7836c)))
    g.fillRect(0, 0, width, height)
    val right = forward.cross(Vec(0, 1, 0)).normalize()
    val up = right.cross(forward).normalize()
    val focal = height / 2.0 / math.tan(math.toRadians(70) / 2)
    val light = Vec(0.7, 1, 0.4).normalize()

    def view(p: Vec): Vec = {
      val v = p.sub(camera)
      Vec(v.dot(right), v.dot(up), v.dot(forward))
    }
    // 足元の床のようにカメラ面をまたぐ面も、手前で切ってから投影する。
    def clip(vertices: Vector[Vec]): Vector[Vec] = {
      val out = Vector.newBuilder[Vec]
      vertices.zip(vertices.tail :+ vertices.head).foreach { (a, b) =>
        if a.z >= 0.15 then out += a
        if (a.z >= 0.15) != (b.z >= 0.15) then out += a.add(b.sub(a).mul((0.15 - a.z) / (b.z - a.z)))
      }
      out.result()
    }

    mesh.filter(f => f.normal.dot(camera.sub(f.center)) > 0)
      .sortBy(f => -view(f.center).z).foreach { face =>
        val points = clip(face.vertices.map(view))
        if points.size >= 3 then {
          val polygon = new Path2D.Double()
          points.zipWithIndex.foreach { (v, index) =>
            val x = width / 2.0 + v.x / v.z * focal
            val y = height / 2.0 - v.y / v.z * focal
            if index == 0 then polygon.moveTo(x, y) else polygon.lineTo(x, y)
          }
          polygon.closePath()
          val shade = if face.luminous then 1.0 else 0.65 + 0.35 * math.max(0, face.normal.dot(light))
          def channel(shift: Int): Int = (((face.rgb >> shift) & 255) * shade).toInt.min(255)
          g.setColor(Color(channel(16), channel(8), channel(0)))
          g.fill(polygon)
          if view(face.center).z < 95 then {
            g.setColor(Color(0, 0, 0, 20))
            g.setStroke(BasicStroke(0.35f))
            g.draw(polygon)
          }
        }
      }
    g.setColor(Color(0, 0, 0, 150))
    g.fillRect(18, 18, 680, 62)
    g.setColor(Color.WHITE)
    g.setFont(Font("SansSerif", Font.BOLD, 21))
    g.drawString("SCORPIUS / " + out.getFileName.toString.stripSuffix(".png"), 34, 45)
    g.setFont(Font("SansSerif", Font.PLAIN, 14))
    g.drawString("Generated blocks / geometry preview / approximate materials and lighting", 34, 66)
    g.dispose()
    ImageIO.write(img, "png", out.toFile)
  }

  private def meshFor(inst: Instance, xs: Range, ys: Range, zs: Range): Vector[Face] = {
    val mesh = Vector.newBuilder[Face]
    val materialColors = mutable.Map.empty[Int, Int]
    val shapes = mutable.Map.empty[Int, Vector[Box]]
    for x <- xs; z <- zs; y <- ys do {
      val block = inst.getBlock(x, y, z)
      if !block.air() && !block.compare(Blocks.BARRIER) then {
        val visible = directions.map { (normal, opposite) =>
          val neighbor = inst.getBlock(x + normal.blockX(), y + normal.blockY(), z + normal.blockZ())
          neighbor.compare(Blocks.BARRIER) || !neighbor.occlusionShape().isFaceFull(opposite)
        }
        if visible.contains(true) then {
          val rgb = materialColors.getOrElseUpdate(block.stateId(), color(block))
          val offset = Vec(x, y, z)
          shapes.getOrElseUpdate(block.stateId(), boxes(block)).foreach { box =>
            faces(box).zipWithIndex.foreach { (vertices, index) =>
              if visible(index) then mesh += Face(vertices.map(_.add(offset)), directions(index)._1, rgb, block.lightEmission() > 0)
            }
          }
        }
      }
    }
    mesh.result()
  }

  private[tools] def writeViews(inst: Instance, xs: Range, ys: Range, zs: Range,
                              views: Vector[(String, Vec, Vec)], out: Path): Unit = {
    Files.createDirectories(out)
    val mesh = meshFor(inst, xs, ys, zs)
    views.foreach { (name, eye, target) => render(mesh, eye, target.sub(eye).normalize(), out.resolve(name + ".png")) }
  }

  def main(args: Array[String]): Unit = {
    MinecraftServer.init()
    val out = if args.nonEmpty then Paths.get(args(0)) else Paths.get("build/reports/lobby")
    Files.createDirectories(out)
    val inst = LobbyManager.instance
    for x <- -5 to 5; z <- -4 to 3 do inst.loadChunk(x, z).join()
    val geometry = meshFor(inst, -72 to 79, -24 to 44, -55 to 54)
    val spawn = LobbyLayout.Spawn
    render(geometry, spawn.add(0, 1.62, 0).asVec(), spawn.direction(), out.resolve("spawn.png"))
    val eye = Vec(69, 69, 75)
    render(geometry, eye, Vec(-2, 3, 0).sub(eye).normalize(), out.resolve("overview.png"))
    render(geometry, Vec(1, 5, 0), Vec(25, 6, -12).normalize(), out.resolve("shops.png"))

    val map = BufferedImage(1210, 850, BufferedImage.TYPE_INT_RGB)
    val g = map.createGraphics()
    g.setColor(Color(0x20232b))
    g.fillRect(0, 0, map.getWidth, map.getHeight)
    val scale = 10
    for x <- -43 to 44; z <- -37 to 37 do {
      val over = (2 to 5).reverse.map(y => inst.getBlock(x, y, z)).find(!_.air())
      val block = over.getOrElse(inst.getBlock(x, 1, z))
      g.setColor(Color(color(block)))
      g.fillRect(20 + (x + 43) * scale, 50 + (z + 37) * scale, scale, scale)
    }
    g.setFont(Font("SansSerif", Font.BOLD, 18))
    g.setColor(Color.WHITE)
    g.drawString("SCORPIUS / WALKING LEVEL", 20, 29)
    g.setFont(Font("SansSerif", Font.PLAIN, 13))
    LobbyLayout.Residents.zipWithIndex.foreach { (npc, index) =>
      val x = 25 + (npc.pos.blockX() + 43) * scale
      val y = 55 + (npc.pos.blockZ() + 37) * scale
      g.setColor(Color(0x212435))
      g.fillOval(x - 9, y - 9, 19, 19)
      g.setColor(Color.WHITE)
      g.drawString((index + 1).toString, x - 5, y + 5)
      g.drawString(s"${index + 1}. ${npc.name.replaceAll("§.", "")}", 920, 100 + index * 32)
    }
    val sx = 25 + (spawn.blockX() + 43) * scale
    val sz = 55 + (spawn.blockZ() + 37) * scale
    g.setColor(Color(0xffe7a0))
    g.fillOval(sx - 6, sz - 6, 12, 12)
    g.setStroke(BasicStroke(3))
    g.drawLine(sx, sz, sx - 45, sz)
    g.drawString("SPAWN", sx - 10, sz - 12)
    g.dispose()
    ImageIO.write(map, "png", out.resolve("plan.png").toFile)
    println(s"[LobbyPreview] ${geometry.size} faces / ${LobbyLayout.Residents.size} NPC positions -> $out")
    MinecraftServer.stopCleanly()
  }
}
