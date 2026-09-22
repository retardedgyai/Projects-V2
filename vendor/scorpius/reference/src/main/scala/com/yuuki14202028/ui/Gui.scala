package com.yuuki14202028.ui

import com.yuuki14202028.Materials
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.`trait`.InventoryEvent
import net.minestom.server.event.inventory.{InventoryCloseEvent, InventoryItemChangeEvent, InventoryPreClickEvent}
import net.minestom.server.event.{EventFilter, EventNode}
import net.minestom.server.inventory.click.Click
import net.minestom.server.inventory.{AbstractInventory, Inventory}
import net.minestom.server.item.ItemStack

import java.util.UUID
import scala.collection.mutable

/**
 * インベントリ GUI の共通配線。
 *
 * かつては GUI ごとに InventoryPreClickEvent を購読し、インベントリ単位の EventNode の
 * 張り剥がし・クリックのキャンセル・閉じたときの後始末・再描画の呼び忘れを毎回手書きしていた。
 * その「毎回間違えうる手続き」を [[GuiSession]] が一手に引き受け、GUI 側は build ブロックに
 * 盤面（どのスロットに何を出すか）と反応（押されたら何をするか）だけを宣言する。
 *
 * build ブロックは Compose 式——[[fill]] や [[button]] を呼べば積まれる——で、
 * 開くときに1回実行され、以後は
 *  - ボタンの action が走ったあと、プレイヤーがまだこの画面を見ているとき
 *  - 投入スロット（[[deposits]]）の中身が動いたとき
 * に再実行されて描き直される。action が画面を閉じた・別の画面を開いた・
 * action 中の投入変化で既に描き直されたときは重ねて描かない。
 * 状態（ページ番号や選択中の項目）は呼び出し側のクロージャ変数に持たせ、
 * build ブロックがそれを読んで盤面に写す。
 *
 * 別の画面への切り替えは [[goto]]（タイトルに状態を焼き込むならタイトルも渡す）。
 * スケジューラ駆動の演出は [[Gui.open]] が返す [[GuiHandle]] の refresh で描き直す。
 *
 * {{{
 * val inv = Inventory(InventoryType.CHEST_6_ROW, title)  // タイトルに所持金等を焼き込む GUI があるので生成は呼び出し側
 * Gui.open(player, inv) {
 *   fill(UiItemFactory.grayFiller)
 *   put(4, infoItem)
 *   button(20, headItem) { p => PartyManager.invitePlayer(p, name) }
 *   closeButton(49)
 * }
 * }}}
 */
object Gui {

  /** 全 GUI 共通の閉じるボタンの見た目。 */
  val closeItem: ItemStack =
    ItemStack.builder(Materials.BARRIER)
      .customName(Component.text("§c✖ 閉じる").decoration(TextDecoration.ITALIC, false))
      .build()

  /**
   * GUI を開く。配線（ノードの張り剥がし・キャンセル・後始末）はセッションが持つ。
   * 返る [[GuiHandle]] は外から描き直す GUI（スケジューラ駆動の演出）用で、捨ててよい。
   */
  def open(player: Player, inventory: Inventory, clicks: ClickPolicy = ClickPolicy.CancelAll)
          (build: GuiBuilder ?=> Unit): GuiHandle = {
    val session = new GuiSession(inventory, clicks, build)
    session.openFor(player)
    new GuiHandle(session)
  }
}

/** クリックの止め方。 */
enum ClickPolicy {
  /** 投入スロット以外の全クリックを止める（既定）。盤面が読み取り専用の GUI 用。 */
  case CancelAll
  /** ボタンのあるスロットだけ止める。台の上を自由に触らせる GUI（売却台）用。 */
  case CancelHandledOnly
}

/**
 * 開いている GUI への外部ハンドル。時間経過で盤面が変わる GUI が
 * `inv.setItemStack` を直叩きせずに描き直すための口。
 */
final class GuiHandle private[ui] (session: GuiSession) {
  /** build ブロックを実行し直して描き直す。画面が既に閉じられていれば何もしない。 */
  def refresh(): Unit = session.refreshIfOpen()
  /** 画面を閉じる。通常の閉じ際と同じ後始末（投入物の受け渡し・onClose）が走る。 */
  def close(): Unit = session.closeIfOpen()
  /** この画面がまだ開かれているか。 */
  def isOpen: Boolean = session.isOpen
}

// ─── build ブロックの語彙 ───────────────────────────────────────────────────

/** 背景。宣言のない全スロットをこのアイテムで敷き詰める（投入スロットは除く）。 */
def fill(stack: ItemStack)(using b: GuiBuilder): Unit = {
  b.fillStack = Some(stack)
}

/** 表示だけのスロット。 */
def put(slot: Int, stack: ItemStack)(using b: GuiBuilder): Unit = {
  b.items(slot) = stack
}

/** 押せるスロット。 */
def button(slot: Int, stack: ItemStack)(action: Player => Unit)(using b: GuiBuilder): Unit = {
  b.items(slot) = stack
  b.handlers(slot) = (p, _) => action(p)
}

/** 左右クリックを見分けるボタン。区別が要らなければ [[button]] を使う。 */
def buttonWithClick(slot: Int, stack: ItemStack)(action: (Player, Click) => Unit)(using b: GuiBuilder): Unit = {
  b.items(slot) = stack
  b.handlers(slot) = action
}

/** 閉じるボタン。 */
def closeButton(slot: Int, stack: ItemStack = Gui.closeItem)(using b: GuiBuilder): Unit = {
  button(slot, stack)(_.closeInventory())
}

/**
 * 画面遷移。button の action 内から呼ぶ。今の画面の後始末（投入物の受け渡しと onClose）を
 * 済ませてから build を差し替えて描き直すので、`p.closeInventory(); open(p)` と違って
 * InventoryCloseEvent が挟まらず、後始末と次画面の初期化が競合しない。
 * title を渡すと同型の Inventory を作り直して開き直す（プロトコル上、開いている画面の
 * タイトルだけは差し替えられない）。ClickPolicy は open で決めたものを引き継ぐ。
 */
def goto(player: Player, title: Component = null)(build: GuiBuilder ?=> Unit)(using b: GuiBuilder): Unit = {
  b.session.gotoScreen(player, Option(title), build)
}

/**
 * 投入スロット。プレイヤーが実アイテムを置ける/取れる唯一の枠で、
 * 中身が動くたびに build ブロックを再実行して描き直す。
 * 描画はこの枠に決して触らない：盤面の描き直しで持ち物を消さないため。
 *
 * 閉じ際（[[goto]] での遷移も含む）に残っていた中身の行き先は、どちらか一方を渡す:
 *  - restore: 残っていた分を (プレイヤー, スロット番号, 中身) で1枠ずつ持ち主へ返す。
 *    空の枠は呼ばれない。返し方——あふれたら足元に落とすか諦めるか——は
 *    GUI の流儀があるので呼び出し側が決める
 *  - commit: 全枠の中身を宣言順・位置つきで一括で引き取る（返さない）。空の枠も AIR で
 *    見える。引き取ったあとの枠はセッションが空にする
 */
def deposits(
  slots: Seq[Int],
  restore: (Player, Int, ItemStack) => Unit = null,
  commit: (Player, Vector[(Int, ItemStack)]) => Unit = null
)(using b: GuiBuilder): Unit = {
  require((restore != null) != (commit != null), "deposits には restore か commit のどちらか一方を渡す")
  b.deposit = Some(DepositSpec(slots.toVector, Option(restore), Option(commit)))
}

/**
 * 投入スロットの中身が動いた瞬間のフック（動いた後の中身が渡る。取り出されたときは AIR）。
 * 盤面の描き直しの前に呼ばれる。対象にできるのは [[deposits]] で宣言した枠だけ。
 */
def onDeposit(slots: Seq[Int])(hook: (Int, ItemStack) => Unit)(using b: GuiBuilder): Unit = {
  b.depositHooks += ((slots.toSet, hook))
}

/**
 * 投入枠から n 個取り分ける（作業台が材料を消費する経路）。実際に取れた個数を返す。
 * 枠の変化として扱われるので、盤面は描き直される。
 */
def consume(slot: Int, n: Int = 1)(using b: GuiBuilder): Int =
  b.session.consumeFromDeposit(slot, n)

/** 閉じたときのフック。ノードの除去と投入物の受け渡しのあとに呼ばれる。goto の遷移時も走る。 */
def onClose(hook: Player => Unit)(using b: GuiBuilder): Unit = {
  b.closeHooks += hook
}

// ─── 実装 ──────────────────────────────────────────────────────────────────

/** build ブロック1回分の受け皿。宣言の順序は問わず、適用はブロック実行後にまとめて行う。 */
final class GuiBuilder private[ui] (private[ui] val session: GuiSession) {
  private[ui] var fillStack: Option[ItemStack] = None
  // LinkedHashMap: 同じスロットへの再宣言は後勝ちで、適用順は宣言順のまま保つ
  private[ui] val items = mutable.LinkedHashMap.empty[Int, ItemStack]
  private[ui] val handlers = mutable.HashMap.empty[Int, (Player, Click) => Unit]
  private[ui] var deposit: Option[DepositSpec] = None
  private[ui] val depositHooks = mutable.ListBuffer.empty[(Set[Int], (Int, ItemStack) => Unit)]
  private[ui] val closeHooks = mutable.ListBuffer.empty[Player => Unit]
}

private[ui] final case class DepositSpec(
  slots: Vector[Int],
  restore: Option[(Player, Int, ItemStack) => Unit],
  commit: Option[(Player, Vector[(Int, ItemStack)]) => Unit]
)

/** 確定した盤面1枚。クリックの解決と閉じ際の後始末は常に最新の盤面に対して行う。 */
private[ui] final class Frame(
  val fillStack: Option[ItemStack],
  val items: Vector[(Int, ItemStack)],
  val handlers: Map[Int, (Player, Click) => Unit],
  val deposit: Option[DepositSpec],
  val depositSlots: Set[Int],
  val depositHooks: List[(Set[Int], (Int, ItemStack) => Unit)],
  val closeHooks: List[Player => Unit]
)

private[ui] final class GuiSession(
  initialInventory: Inventory,
  clicks: ClickPolicy,
  initialBuild: GuiBuilder ?=> Unit
) {
  private var inventory: Inventory = initialInventory
  private var build: GuiBuilder ?=> Unit = initialBuild
  private var viewer: Player = null
  private var frame = new Frame(None, Vector.empty, Map.empty, None, Set.empty, Nil, Nil)
  private var frameCount = 0
  // 後始末が投入枠を空ける間、その変化に釣られて盤面を描き直さないためのガード
  private var tearingDown = false

  // インベントリ単位のノード。goto が Inventory を作り直しても付いていく
  // （フィルタは現在の inventory を読む）。閉じたら必ず removeChild する（怠るとリスナーが積み上がる）
  private val node: EventNode[InventoryEvent] = EventNode.`type`(
    s"scorpius_gui_${UUID.randomUUID()}",
    EventFilter.INVENTORY,
    (_: InventoryEvent, invArg: AbstractInventory) => invArg == inventory
  )

  private[ui] def openFor(player: Player): Unit = {
    viewer = player
    render()
    node.addListener(classOf[InventoryPreClickEvent], onPreClick(_))
    node.addListener(classOf[InventoryItemChangeEvent], onItemChange(_))
    node.addListener(classOf[InventoryCloseEvent], onCloseEvent(_))
    MinecraftServer.getGlobalEventHandler.addChild(node)
    player.openInventory(inventory)
    ()
  }

  /**
   * 画面の差し替え。今の画面の後始末を済ませてから build を入れ替えて描き直す。
   * タイトルも変えるときは同型の Inventory を作り直して開き直す——Minestom の
   * openInventory は旧 Inventory の InventoryCloseEvent を発火しないので、
   * 閉じ際の後始末が二重に走る隙間はない。
   */
  private[ui] def gotoScreen(player: Player, title: Option[Component], next: GuiBuilder ?=> Unit): Unit = {
    teardown(player)
    build = next
    title match {
      case Some(t) =>
        inventory = new Inventory(inventory.getInventoryType, t)
        render()
        player.openInventory(inventory)
        ()
      case None =>
        render()
        clearStaleDeposits()
    }
  }

  private[ui] def isOpen: Boolean =
    viewer != null && viewer.getOpenInventory == inventory

  private[ui] def refreshIfOpen(): Unit = {
    if isOpen then render()
  }

  private[ui] def closeIfOpen(): Unit = {
    if isOpen then viewer.closeInventory()
  }

  private[ui] def consumeFromDeposit(slot: Int, n: Int): Int = {
    val stack = inventory.getItemStack(slot)
    if stack.isAir || n <= 0 then 0
    else {
      val take = math.min(n, stack.amount())
      val rest = stack.amount() - take
      inventory.setItemStack(slot, if rest <= 0 then ItemStack.AIR else stack.withAmount(rest))
      take
    }
  }

  /** build ブロックを実行し直し、出そろった宣言をインベントリへ写す。 */
  private def render(): Unit = {
    frameCount += 1
    val builder = new GuiBuilder(this)
    build(using builder)
    frame = new Frame(
      builder.fillStack,
      builder.items.toVector,
      builder.handlers.toMap,
      builder.deposit,
      builder.deposit.fold(Set.empty[Int])(_.slots.toSet),
      builder.depositHooks.toList,
      builder.closeHooks.toList
    )
    frame.fillStack.foreach { filler =>
      val declared = frame.items.iterator.map(_._1).toSet
      for slot <- 0 until inventory.getSize do {
        if !declared.contains(slot) && !frame.depositSlots.contains(slot) then
          inventory.setItemStack(slot, filler)
      }
    }
    frame.items.foreach { (slot, stack) =>
      if !frame.depositSlots.contains(slot) then inventory.setItemStack(slot, stack)
    }
  }

  private def onPreClick(event: InventoryPreClickEvent): Unit = {
    val slot = event.getSlot
    if frame.depositSlots.contains(slot) then return  // 出し入れ自由
    val handler = frame.handlers.get(slot)
    clicks match {
      case ClickPolicy.CancelAll         => event.setCancelled(true)
      case ClickPolicy.CancelHandledOnly => if handler.isDefined then event.setCancelled(true)
    }
    handler.foreach { h =>
      val before = frameCount
      h(event.getPlayer, event.getClick)
      // action が画面を離れた（閉じた・別画面を開いた）なら見えない盤面を塗らない。
      // action 中の投入スロット変化・goto で既に描き直されたなら重ねない
      if frameCount == before && event.getPlayer.getOpenInventory == inventory then render()
    }
  }

  private def onItemChange(event: InventoryItemChangeEvent): Unit = {
    if tearingDown then return
    val slot = event.getSlot
    if frame.depositSlots.contains(slot) then {
      frame.depositHooks.foreach { (slots, hook) =>
        if slots.contains(slot) then hook(slot, event.getNewItem)
      }
      render()
    }
  }

  private def onCloseEvent(event: InventoryCloseEvent): Unit = {
    MinecraftServer.getGlobalEventHandler.removeChild(node)
    teardown(event.getPlayer)
  }

  /** 投入物の受け渡しと closeHooks。閉じるときと goto の差し替え時に共通の後始末。 */
  private def teardown(player: Player): Unit = {
    tearingDown = true
    try {
      frame.deposit.foreach { spec =>
        spec.commit match {
          case Some(commitFn) =>
            commitFn(player, spec.slots.map(s => s -> inventory.getItemStack(s)))
            spec.slots.foreach(s => inventory.setItemStack(s, ItemStack.AIR))
          case None =>
            spec.slots.foreach { slot =>
              val stack = inventory.getItemStack(slot)
              if !stack.isAir then {
                spec.restore.foreach(_(player, slot, stack))
                inventory.setItemStack(slot, ItemStack.AIR)
              }
            }
        }
      }
      frame.closeHooks.foreach(_(player))
    } finally {
      tearingDown = false
    }
  }

  /**
   * goto 先の投入枠に前の画面の盤面（描画物）が残っていたら空ける。後始末済みなので
   * 実アイテムはもう無い——残っているのは絵で、放置すると投入枠経由で実物として掴めてしまう。
   */
  private def clearStaleDeposits(): Unit = {
    tearingDown = true
    try {
      frame.depositSlots.foreach { s =>
        if !inventory.getItemStack(s).isAir then inventory.setItemStack(s, ItemStack.AIR)
      }
    } finally {
      tearingDown = false
    }
  }
}
