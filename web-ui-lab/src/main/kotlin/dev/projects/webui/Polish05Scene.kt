package dev.projects.webui

import com.google.gson.JsonParser
import dev.projects.webui.polish05.Polish05PreviewModel
import dev.projects.webui.polish05.Polish05PreviewModel.Material
import dev.projects.webui.polish05.Polish05ScreenSpace
import java.nio.file.Files
import java.nio.file.Path
import java.text.NumberFormat
import java.util.Locale

/** Draws live values over the approved art; hitboxes use the same 1440x920 coordinate map. */
enum class ForgeLightPhase { IDLE, STRIKING, RESULT_WARM }

class Polish05Scene private constructor(approvedJson: String, spriteJson: String) {
    constructor(kit: Path, spriteMap: Path) : this(Files.readString(kit.resolve("layout/forge_initial.json")), Files.readString(spriteMap))
    constructor(approvedJson: ByteArray, spriteJson: ByteArray) : this(approvedJson.toString(Charsets.UTF_8), spriteJson.toString(Charsets.UTF_8))
    private companion object {
        const val TYPOGRAPHY_SCALE=1.175
        const val MODAL_MASK_DEPTH=20
        const val MODAL_BORDER_DEPTH=21
        const val MODAL_PANEL_DEPTH=22
        const val MODAL_CONTENT_DEPTH=23
        const val MODAL_ACTION_DEPTH=24
    }
    private val screen = Polish05ScreenSpace(800.0, 480.0)
    private val sprites = JsonParser.parseString(spriteJson).asJsonObject.entrySet().associate { (name, value) ->
        val v = value.asJsonObject
        name to UiSprite(v.get("char").asString, v.get("font").asString,
            v.get("width").asInt, v.get("height").asInt)
    }
    private val approved = JsonParser.parseString(approvedJson).asJsonObject
    private val productionTemplate = Polish05PreviewModel()
    private val format = NumberFormat.getIntegerInstance(Locale.US)
    private fun number(n: Number) = format.format(n)
    private fun rect(x: Number,y: Number,w: Number,h: Number): Box {
        val p=screen.forward(x.toDouble(),y.toDouble())
        return Box(p.x,p.y,w.toDouble()*screen.scale,h.toDouble()*screen.scale)
    }
    private fun put(nodes: MutableList<UiNode>, id:String,x:Number,y:Number,w:Number,h:Number,text:String="",
                    color:String="#e7e0cd",size:Double=16.0,bg:String?=null,action:String?=null,
                    sprite:String?=null,depth:Int=7,align:String="left",family:String="sans",item:String?=null) {
        val style=mutableMapOf("color" to color,"font-size" to "${size*screen.scale}px","text-align" to align,
            "font-family" to "projects_ui_polish05:$family")
        if(bg!=null)style["background-color"]=bg
        if(bg!=null && action!=null && bg in setOf("#d4b879","#d6bb7f","#d7bb7c"))
            style["hover-background-color"]="#f1d59b"
        nodes+=UiNode(id,rect(x,y,w,h),text,style,action,item,true,depth,sprite?.let(sprites::getValue))
    }
    private fun shell(model: Polish05PreviewModel, muted:Boolean): MutableList<UiNode> = forge(model,muted=muted).nodes.filter { n ->
        n.id in setOf("viewport-backdrop","page","brand","eyebrow","heading","sound-box","sound-label","wallet-icon","wallet","wallet-unit","close","footer-help","footer-state","workshop-icon","workshop-text") ||
            n.id.startsWith("tile-window_chrome/") || (n.id.startsWith("tab-") && n.id!="tab-forge")
    }.toMutableList()
    private fun hit(nodes:MutableList<UiNode>,id:String,x:Number,y:Number,w:Number,h:Number,action:String) =
        put(nodes,id,x,y,w,h,action=action,depth=10)

    fun inventory(model: Polish05PreviewModel, bagSelected:String, compare:Boolean, muted:Boolean=false): UiScene {
        val snap=model.snapshot()
        val gear=snap.gears.firstOrNull { it.id==bagSelected } ?: snap.gears.first()
        val nodes=shell(model,muted)
        put(nodes,"inventory-body",79,214,1282,575,bg="#171d1e",depth=5)
        put(nodes,"inventory-tab",607,150,138,71,sprite="tab_active_bag",depth=2)
        put(nodes,"inventory-heading",99,239,234,27,"旅の持ち物   14 / 32",size=19.0)
        put(nodes,"inventory-filter",713,236,251,30,"すべて     武器     素材     その他",size=12.0,color="#b4b9ac")
        val items=listOf(
            Triple("ember","sword_t2_thumb","+${snap.gears[0].level}"),Triple("ash","greatsword_thumb","+${snap.gears[1].level}"),
            Triple("helm","helm_ui",""),Triple("plate","armor_ui",""),Triple("mail","armor_ui",""),
            Triple("leaf","green_crystal",""),Triple("ore","coin_ore","${snap.materials.getValue(Material.ORE)}"),
            Triple("crystal","purple_crystal","${snap.materials.getValue(Material.CRYSTAL)}"),
            Triple("catalyst","metal_ui","${snap.materials.getValue(Material.CATALYST)}"),
            Triple("rawOre","shard_ui","${snap.materials.getValue(Material.RAW_ORE)}"),
            Triple("rawCrystal","ore_ui","${snap.materials.getValue(Material.RAW_CRYSTAL)}"),
            Triple("wood","wood_ui","32"),Triple("potion","potion_ui","8"),Triple("elixir","potion_smallui","4"))
        repeat(32) { index ->
            val x=99+(index%8)*109.125
            val y=283+(index/8)*91
            val slotPlate=when {
                index>=items.size -> "bag_slot_empty"
                items[index].first==bagSelected -> "bag_slot_selected"
                else -> "bag_slot_regular"
            }
            put(nodes,"slot-$index",x,y,101,83,sprite=slotPlate)
            if(index<items.size) {
                val (id,icon,count)=items[index]
                val size=if(id=="ember" || id=="ash")48 else 42
                put(nodes,"slot-icon-$index",x+26,y+13,size,size,sprite=icon,depth=8)
                if(count.isNotEmpty())put(nodes,"slot-count-$index",x+56,y+62,37,17,count,size=12.0,align="right",depth=8)
                hit(nodes,"slot-hit-$index",x,y,101,83,"bag:$id")
            }
        }
        put(nodes,"bag-instruction",99,654,600,24,"クリックで選択 · 武器は比較してから工房へ",size=12.0,color="#a4aa9e")
        put(nodes,"bag-detail-bg",991,235,350,548,bg="#202829")
        put(nodes,"bag-rarity",1015,256,270,18,"${if(gear.tier==2)"希少" else "魔法"}装備  ·  大剣  ·  T${gear.tier}",size=11.0,color="#b7a9c7")
        put(nodes,"bag-name",1015,283,300,33,"${gear.name} +${gear.level}",size=22.0,family="serif")
        put(nodes,"bag-weapon",1143,325,if(gear.id=="ember")32 else 28,if(gear.id=="ember")95 else 83,
            sprite=if(gear.id=="ember")"sword_t2_hero" else "greatsword_hero",depth=8)
        put(nodes,"bag-power",1015,431,270,49,"${gear.power}  物理攻撃",size=28.0)
        put(nodes,"bag-speed",1015,530,280,21,"攻撃速度                         ${gear.speed}",size=12.0)
        put(nodes,"bag-quality",1015,557,280,21,"品質                                 上質",size=12.0)
        put(nodes,"bag-durability",1015,582,280,21,"耐久                            100 / 100",size=12.0)
        put(nodes,"bag-affix",1015,634,310,49,if(gear.id=="ember")"火炎ダメージ +12%\n通常攻撃の範囲 +8%" else "火炎ダメージ +8%\n攻撃速度 +6%",size=12.0,color="#bdc5af")
        put(nodes,"bag-forge-btn",1015,689,302,43,"この装備を強化する",size=16.0,color="#241e16",bg="#d4b879",action="bag:forge",align="center")
        put(nodes,"bag-compare-btn",1015,736,147,36,"装備中と比較",size=13.0,bg="#26302e",action="compare",align="center")
        put(nodes,"bag-equip-btn",1170,736,147,36,"装備する",size=13.0,bg="#26302e",action="bag:equip",align="center")
        hit(nodes,"bag-tab-forge",343,150,130,63,"view:forge")
        hit(nodes,"bag-tab-refine",475,150,130,63,"view:refine")
        hit(nodes,"bag-close",1311,166,30,30,"close")
        hit(nodes,"bag-sound",1088,24,94,35,"sound")
        if(compare) {
            nodes.removeIf { it.action!=null }
            put(nodes,"bag-modal-mask",0,0,1440,920,bg="#bb0d1418",depth=MODAL_MASK_DEPTH)
            put(nodes,"bag-modal-panel",363,181,714,561,bg="#202827",depth=MODAL_PANEL_DEPTH)
            put(nodes,"bag-modal-title",399,217,639,44,"装備中と比較",size=24.0,depth=MODAL_CONTENT_DEPTH)
            put(nodes,"bag-modal-selected",400,285,292,159,"選択中  ${gear.name} +${gear.level}\n物理攻撃 ${gear.power}",size=17.0,depth=MODAL_CONTENT_DEPTH)
            val equipped=snap.gears.single { it.id==snap.equipped }
            put(nodes,"bag-modal-equipped",735,285,292,159,"装備中  ${equipped.name} +${equipped.level}\n物理攻撃 ${equipped.power}",size=17.0,depth=MODAL_CONTENT_DEPTH)
            put(nodes,"bag-modal-cancel",383,677,325,44,"閉じる",size=16.0,bg="#283230",action="cancel",depth=MODAL_ACTION_DEPTH,align="center")
            put(nodes,"bag-modal-forge",718,677,340,44,"選択中の装備を工房へ",size=16.0,bg="#d6bb7f",color="#211f19",action="bag:forge",depth=MODAL_ACTION_DEPTH,align="center")
        }
        return UiScene(800.0,480.0,nodes)
    }

    fun refine(model: Polish05PreviewModel, recipe: Polish05PreviewModel.Recipe, batch:Int,
               modal:Boolean, note:String, returnToForge:Boolean, muted:Boolean=false): UiScene {
        val snap=model.snapshot()
        val ore=recipe==Polish05PreviewModel.Recipe.ORE
        val replenishment=model.replenishment(recipe)
        val capacity=replenishment.capacity
        val count=batch.coerceIn(1,maxOf(1,capacity))
        val raw=if(ore)Material.RAW_ORE else Material.RAW_CRYSTAL
        val made=if(ore)Material.ORE else Material.CRYSTAL
        val unit=if(ore)4 else 3
        val fee=if(ore)80 else 60
        val nodes=shell(model,muted)
        put(nodes,"refine-body",79,214,1282,575,bg="#171d1e",depth=5)
        put(nodes,"refine-tab",475,150,130,71,sprite="tab_active_refine",depth=2)
        put(nodes,"refine-left-title",99,238,210,25,"精錬できる素材     02",size=16.0)
        for(index in 0..1) {
            val y=281+index*82
            put(nodes,"recipe-card-$index",99,y,238,78,
                sprite=if((index==0)==ore)"recipe_selected" else "recipe_regular",depth=6)
            put(nodes,"recipe-icon-$index",111,y+18,34,36,sprite=if(index==0)"coin_ore" else "purple_crystal",depth=8)
            put(nodes,"recipe-name-$index",160,y+16,164,21,if(index==0)"陽鉱の精製" else "虚晶の研磨",size=15.0)
            put(nodes,"recipe-caption-$index",160,y+39,164,18,if(index==0)"原石から、強化の素材へ" else "不純物を取り除く",size=11.0,color="#a4aa9f")
            hit(nodes,"recipe-hit-$index",99,y,238,74,if(index==0)"refine:ore" else "refine:crystal")
        }
        put(nodes,"refine-left-note",99,463,239,49,"作った素材は素材袋に入ります。\n強化画面に戻って、そのまま使用できます。",size=12.0,color="#a4aa9d")
        put(nodes,"refine-selected-gear",99,535,239,67,"${snap.gears.single { it.id==snap.selected }.name}の強化準備\n不足 ${replenishment.missing}個",size=12.0,color="#c2bb9d")
        if(returnToForge)put(nodes,"return-forge",99,605,238,35,"装備の強化に戻る  ›",size=12.0,action="view:forge",color="#d3bc86")
        put(nodes,"refine-title",555,258,250,40,if(ore)"陽鉱の精製" else "虚晶の研磨",size=27.0,align="center",family="serif")
        put(nodes,"refine-subtitle",530,307,300,22,"砕き、熔かし、不純物を取り除く。",size=12.0,color="#a8a18c",align="center")
        // The approved forge art is reused in the refinery stage, with the original material icon.
        sprites.keys.filter { it.startsWith("forge_environment_plate/") }.forEach { key ->
            val offset=key.substringAfter('/').split('_');val s=sprites.getValue(key)
            put(nodes,"refine-$key",446+offset[0].toInt(),343+offset[1].toInt(),s.width,s.height,sprite=key)
        }
        put(nodes,"refine-hero-icon",639,422,70,75,sprite=if(ore)"shard_ui" else "ore_ui",depth=8)
        put(nodes,"refine-conversion",572,673,228,36,"${unit} 原石  →  1 素材",size=18.0,align="center")
        put(nodes,"refine-result-note",559,719,267,46,"素材はまとめて精錬できます。\n精錬に失敗はありません。",size=11.0,color="#b0b7a9",align="center")
        put(nodes,"refine-count-title",1015,238,280,25,"精錬する数量",size=15.0)
        put(nodes,"refine-minus",1016,299,46,44,"−",size=25.0,bg="#32382d",action="batch:minus",align="center")
        put(nodes,"refine-count",1123,302,111,39,"$count",size=21.0,bg="#1c2526",align="center")
        put(nodes,"refine-plus",1294,299,46,44,"＋",size=25.0,bg="#32382d",action="batch:plus",align="center")
        put(nodes,"refine-max",1015,365,326,36,"作れるだけ指定する",size=13.0,bg="#28312d",action="batch:max",align="center")
        put(nodes,"refine-cost-heading",1023,431,160,20,"必要素材",size=14.0)
        put(nodes,"refine-raw-icon",1028,465,25,25,sprite=if(ore)"shard_ui" else "ore_ui",depth=8)
        put(nodes,"refine-raw",1067,459,177,46,"${if(ore)"陽鉱の原石" else "虚晶の原石"}\n所持 ${snap.materials.getValue(raw)} → 残り ${snap.materials.getValue(raw)-unit*count}",size=12.0)
        put(nodes,"refine-raw-cost",1284,468,56,25,"${unit*count} 個",size=20.0,align="right")
        put(nodes,"refine-silver-icon",1030,518,22,23,sprite="coin_ore",depth=8)
        put(nodes,"refine-silver",1067,513,185,45,"銀貨\n所持 ${number(snap.silver)} → 残り ${number(snap.silver-fee*count)}",size=12.0)
        put(nodes,"refine-silver-cost",1245,518,95,25,"${number(fee*count)} 銀貨",size=19.0,align="right")
        put(nodes,"refine-output",1023,582,280,69,"作成するもの\n${if(ore)"陽鉱の塊" else "虚晶の欠片"} ×$count\n精錬後の所持数：${snap.materials.getValue(made)+count}",size=13.0)
        put(nodes,"refine-button",1015,710,326,51,"${count}個 精錬する",size=18.0,color="#201e18",bg="#d4b879",action="refine:quote",align="center")
        put(nodes,"refine-note",1015,768,326,20,note,size=10.0,color="#a4aa9f",align="center")
        hit(nodes,"refine-tab-forge",343,150,130,63,"view:forge")
        hit(nodes,"refine-tab-bag",607,150,138,63,"view:bag")
        hit(nodes,"refine-close",1311,166,30,30,"close")
        hit(nodes,"refine-sound",1088,24,94,35,"sound")
        if(modal) {
            nodes.removeIf { it.action!=null }
            put(nodes,"refine-modal-mask",0,0,1440,920,bg="#bb0d1418",depth=MODAL_MASK_DEPTH)
            put(nodes,"refine-modal-panel",470,246,500,428,bg="#202827",depth=MODAL_PANEL_DEPTH)
            put(nodes,"refine-modal-title",509,274,424,46,"${count}個 精錬しますか？",size=23.0,depth=MODAL_CONTENT_DEPTH)
            put(nodes,"refine-modal-details",509,356,424,166,"${if(ore)"陽鉱の原石" else "虚晶の原石"} ${unit*count}個\n銀貨 ${number(fee*count)}\n作成 ${count}個",size=16.0,depth=MODAL_CONTENT_DEPTH)
            put(nodes,"refine-modal-cancel",509,615,199,44,"やめる",size=15.0,bg="#283230",action="cancel",depth=MODAL_ACTION_DEPTH,align="center")
            put(nodes,"refine-modal-confirm",718,615,213,44,"精錬する",size=17.0,bg="#d4b879",color="#211f19",action="confirm",depth=MODAL_ACTION_DEPTH,align="center")
        }
        return UiScene(800.0,480.0,nodes)
    }

    fun forge(model: Polish05PreviewModel, note: String = "消費内容を確かめてから、鍛造を始めます。", modal: Boolean = false,
              muted:Boolean=false, light:ForgeLightPhase=ForgeLightPhase.IDLE): UiScene {
        val snapshot=model.snapshot()
        val selected=snapshot.gears.single { it.id==snapshot.selected }
        val cost=model.enhancementCost()
        val ore=snapshot.materials.getValue(Material.ORE)
        val crystal=snapshot.materials.getValue(Material.CRYSTAL)
        val canEnhance=model.possibleEnhancements()>0 && !snapshot.busy
        val list=mutableListOf<UiNode>()
        fun node(id:String,x:Number,y:Number,w:Number,h:Number,text:String="",color:String="#e7e0cd",size:Double=16.0,
                 bg:String?=null,action:String?=null,sprite:String?=null,depth:Int=2,align:String="left",family:String="sans") {
            val b=rect(x,y,w,h)
            val style=mutableMapOf("color" to color,"font-size" to "${size*screen.scale}px","text-align" to align,
                "font-family" to "projects_ui_polish05:$family")
            if(bg!=null) style["background-color"]=bg
            if(bg!=null && action!=null && bg in setOf("#d4b879","#d6bb7f","#d7bb7c"))
                style["hover-background-color"]="#f1d59b"
            list+=UiNode(id,b,text,style,action,null,true,depth,sprite?.let { sprites.getValue(it) })
        }
        fun line(id:String,x:Number,y:Number,w:Number,color:String="#3a3c36",depth:Int=2)=node(id,x,y,w,1,bg=color,depth=depth)
        fun tilePlate(name:String,x:Double,y:Double) {
            sprites.keys.filter { it.startsWith("$name/") }.forEach { key ->
                val offset=key.substringAfter('/').split('_')
                val sprite=sprites.getValue(key)
                node("tile-$key",x+offset[0].toDouble(),y+offset[1].toDouble(),sprite.width,sprite.height,sprite=key,depth=1)
            }
        }
        fun halo(id:String,name:String,x:Int,y:Int) {
            for(offset in listOf(0,256)) {
                val key="$name/${offset}_0"
                val sprite=sprites.getValue(key)
                node("$id-$offset",x+offset,y,sprite.width,sprite.height,sprite=key,depth=2)
            }
        }
        node("viewport-backdrop",-200,-150,1840,1220,bg="#14191e",depth=-1)
        node("page",0,0,1440,920,bg="#14191e",depth=0)
        node("brand-sigil",75,29,23,30,sprite="project_sigil",depth=3)
        node("brand-project",107,29,120,30,"ProjectS",size=22.0,color="#e6d9b7",family="serif")
        node("brand-port",227,35,98,18,"│ 帰還港",size=11.0,color="#a7a699")
        node("eyebrow",635,67,180,18,"T H E  E M B E R  F O R G E",size=10.0,color="#aa9e86",align="center")
        node("heading",525,92,390,40,"熾 火 の 工 房",size=29.0,color="#eee0bd",align="center",family="serif")
        node("sound-box",1088,24,94,35,bg="#1c2225")
        node("sound-label",1101,33,79,20,if(muted)"SE OFF" else "SE ON",size=12.0,color="#aeb6aa")
        tilePlate("window_chrome",66.0,140.0)
        node("workshop-icon",99,165,24,30,sprite="hammer",depth=3)
        node("workshop-text",134,174,150,22,"鍛冶師の仕事場",size=13.0,color="#bbb6a8")
        node("tab-forge",343,150,130,71,sprite="tab_active_forge",depth=2)
        node("tab-icon-forge",374,165,24,30,sprite="hammer",depth=4)
        node("tab-label-forge",407,171,66,30,sprite="tab_label_forge_smooth",depth=4)
        node("wallet-icon",1170,172,22,23,sprite="coin_ore")
        node("wallet",1198,171,87,24,number(snapshot.silver),size=18.0,color="#d9caa8")
        node("wallet-unit",1284,175,35,16,"銀貨",size=11.0,color="#b4aa94")
        node("close",1311,166,30,30,"×",size=25.0,bg="#171c1d",align="center")
        node("left-heading",95,235,180,22,"強化する装備",size=14.0)
        line("left-rule",95,276,229)
        snapshot.gears.forEachIndexed { index, gear ->
            val y=277+index*82
            val active=gear.id==snapshot.selected
            node("gear-plate-$index",95,y,229,82,
                sprite=if(active)"gear_selected" else "gear_unselected",depth=1)
            node("gear-icon-$index",111,y+16,48,48,sprite=if(gear.id=="ember")"sword_t2_thumb" else "greatsword_thumb",depth=3)
            node("gear-name-$index",167,y+10,150,23,"${gear.name} +${gear.level}",size=14.0)
            node("gear-stat-$index",167,y+35,157,18,"T${gear.tier}   物理攻撃 ${gear.power}",size=11.0,color="#b4b9af")
            node("gear-status-$index",167,y+59,150,16,if(gear.id==snapshot.equipped)"装備中  ·  魔法" else "所持品  ·  希少",size=10.0,color="#929a93")
        }
        node("replenish-heading",95,496,127,20,"所持素材",size=13.0)
        node("replenish-caption",258,497,66,16,"強化に使用",size=10.0,color="#858b82")
        for(index in 0..1) {
            val y=523+index*57
            // The approved row bitmap already contains fixed labels and counts. Use its
            // dark plate color here so the live material values are drawn only once.
            node("replenish-bg-$index",95,y,229,57,bg="#1c2223",depth=2)
            val raw=if(index==0)Material.RAW_ORE else Material.RAW_CRYSTAL
            val owned=if(index==0)snapshot.materials.getValue(Material.ORE) else snapshot.materials.getValue(Material.CRYSTAL)
            node("replenish-icon-$index",102,y+15,if(index==0)21 else 19,if(index==0)23 else 22,
                sprite=if(index==0)"coin_ore" else "purple_crystal",depth=3)
            node("replenish-name-$index",139,y+8,130,20,if(index==0)"陽鉱の塊" else "虚晶の欠片",size=13.0)
            node("replenish-count-$index",139,y+30,144,17,
                "所持 ${owned}個  ·  原石 ${snapshot.materials.getValue(raw)}個",
                size=10.0,color="#a4aba0")
        }
        node("can-enhance",95,651,226,21,"▪ 手持ちで あと${model.possibleEnhancements()}回 強化可能",size=10.0,color="#9ca891")
        line("history-line",95,702,229)
        node("history-heading",95,714,160,16,"この装備の鍛造記録",size=10.0,color="#a5b0a6")
        node("history-value",95,742,228,31,snapshot.history.firstOrNull()?.let {
            if(it.success) "強化成功  +${it.beforeLevel} → +${it.afterLevel}" else "強化失敗・段階維持  +${it.beforeLevel}"
        } ?: "鍛造後、ここに結果が残ります。",size=10.0,color="#aeb7aa")
        node("rarity",565,234,230,18,if(selected.tier==2)"希少装備  ·  大剣" else "魔法装備  ·  大剣",size=10.0,color="#bba8c7",align="center")
        node("hero-name",517,260,328,37,"${selected.name}  +${selected.level}",size=27.0,color="#ebdfc9",align="center",family="serif")
        tilePlate(when(light) {
            ForgeLightPhase.IDLE -> "forge_environment_with_glow"
            ForgeLightPhase.STRIKING -> "forge_environment_striking"
            ForgeLightPhase.RESULT_WARM -> "forge_environment_result_warm"
        },367.0,296.0)
        val hero=if(selected.id=="ember")"sword_t2_hero" else "greatsword_hero"
        val weaponHeight=if(selected.id=="ember")276 else 240
        val weaponWidth=if(selected.id=="ember")57 else 45
        // The weapon sits completely behind the confirmation panel. Do not keep a
        // translucent bitmap display there while the modal is open.
        if(!modal) node("hero-weapon",681.0-weaponWidth/2.0,if(selected.id=="ember")365 else 383,weaponWidth,weaponHeight,
            sprite=hero,depth=4)
        line("hero-meta-rule",378,678,605)
        node("hero-meta",544,688,275,21,"武器 Tier ${selected.tier}   │   品質 上質   │   耐久 100 / 100",size=12.0,color="#b8b4a8",align="center")
        node("effect-bg",367,720,628,61,bg="#1d2527",depth=2)
        node("effect-icon",379,733,32,32,sprite="firebolt",depth=3)
        node("effect-name",422,729,380,23,if(selected.id=="ember")"熾火の刻印" else "残り火の刻印",size=13.0,color="#cbbb9f")
        node("effect-text",422,751,500,20,if(selected.id=="ember")"火炎ダメージ +12% ／ 通常攻撃の範囲 +8%" else "火炎ダメージ +8% ／ 攻撃速度 +6%",size=11.0,color="#9aa095")
        node("effect-detail",942,741,42,22,"詳細 ›",size=11.0,color="#b9b4a0")
        val maxed=selected.level>=30
        val next=if(maxed)selected.level else selected.level+1
        val powerGain=if(maxed)0 else selected.step
        node("result-title",1019,237,180,22,if(snapshot.history.firstOrNull()?.success==true)"次の強化" else "強化の結果",size=15.0)
        node("result-caption",1275,241,70,15,"成功したとき",size=10.0,color="#9c9e92")
        line("result-top",1019,263,326,depth=3)
        node("before-caption",1078,271,62,16,"現在",size=10.0,color="#999d94",depth=3)
        node("after-caption",1251,271,62,16,"強化後",size=10.0,color="#999d94",depth=3)
        halo("result-level-halo","result_level_halo",1019,263)
        val currentSprite="current_level_${selected.level}"
        node("before-level",1093.7-sprites.getValue(currentSprite).width/(2*TYPOGRAPHY_SCALE),273,
            sprites.getValue(currentSprite).width/TYPOGRAPHY_SCALE,
            sprites.getValue(currentSprite).height/TYPOGRAPHY_SCALE,sprite=currentSprite,depth=3)
        node("level-arrow",1167,290,64,41,"→",size=31.0,color="#aca690",align="center",depth=3)
        // Both values use the approved Georgia outlines. The next level retains
        // its original golden text shadow; the selected number stays dynamic.
        val nextSprite=if(maxed)"next_level_max" else "next_level_$next"
        node("after-level",if(maxed)1199 else 1230,273,
            sprites.getValue(nextSprite).width/TYPOGRAPHY_SCALE,
            sprites.getValue(nextSprite).height/TYPOGRAPHY_SCALE,sprite=nextSprite,depth=3)
        line("result-level-rule",1019,341,326,depth=3)
        node("attack-label",1028,360,90,20,"物理攻撃",size=12.0,color="#a5aa9f")
        node("attack-value",1190,355,149,28,"${selected.power}  →  ${selected.power+powerGain}  +$powerGain",size=20.0,color="#dfd3b7",align="right")
        line("result-attack-rule",1019,392,326)
        node("chance-label",1028,406,85,22,"成功率",size=14.0)
        node("chance-value",1276,402,67,26,if(snapshot.catalyst)"100.0%" else "80.0%",size=20.0,color="#d5bf89",align="right")
        node("chance-track",1028,431,309,4,bg="#393b34")
        node("chance-fill",1028,431,if(snapshot.catalyst)309 else 246,4,bg="#b19a68")
        node("failure-note",1028,440,310,19,"失敗時：強化段階を維持・素材と銀貨は消費",size=10.0,color="#a9ac9d")
        node("cost-heading",1028,474,111,19,"必要素材",size=14.0)
        node("cost-caption",1266,474,77,16,"今回の消費",size=10.0,color="#a2a89b")
        val costs=listOf(Triple("陽鉱の塊",Material.ORE,cost.materials.getValue(Material.ORE)),
            Triple("虚晶の欠片",Material.CRYSTAL,cost.materials.getValue(Material.CRYSTAL)))
        costs.forEachIndexed { index,(name,material,amount) ->
            val y=494+index*52
            val have=snapshot.materials.getValue(material)
            val enough=have>=amount
            halo("cost-halo-$index",if(enough)"cost_ready_halo" else "cost_missing_halo",1019,y)
            line("cost-line-$index",1019,y,326,depth=3)
            node("cost-icon-$index",1028,y+10,24,26,sprite=if(index==0)"coin_ore" else "purple_crystal",depth=4)
            node("cost-name-$index",1067,y+9,150,20,name,size=13.0,depth=3)
            node("cost-after-$index",1067,y+28,190,16,if(have>=amount)"所持 $have → 残り ${have-amount}" else "所持 $have  ·  あと${amount-have}個不足",
                size=10.0,color=if(have>=amount)"#a2a99d" else "#d5a29a",depth=3)
            node("cost-amount-$index",1273,y+16,65,28,"${amount} 個",size=20.0,
                color=if(enough)"#c5d8bd" else "#eca69b",align="right",depth=3)
        }
        val silverY=598
        val enoughSilver=snapshot.silver>=cost.silver
        halo("cost-halo-silver",if(enoughSilver)"cost_ready_halo" else "cost_missing_halo",1019,silverY)
        line("cost-line-silver",1019,silverY,326,depth=3)
        node("silver-icon",1031,silverY+16,22,23,sprite="coin_ore",depth=4)
        node("silver-name",1067,silverY+10,90,19,"銀貨",size=13.0,depth=3)
        node("silver-after",1067,silverY+29,186,16,if(snapshot.silver>=cost.silver)
            "所持 ${number(snapshot.silver)} → 残り ${number(snapshot.silver-cost.silver)}" else "銀貨が不足しています",size=10.0,
            color=if(enoughSilver)"#a2a99d" else "#d5a29a",depth=3)
        node("silver-amount",1238,silverY+16,101,28,"${number(cost.silver)} 銀貨",size=18.0,
            color=if(enoughSilver)"#c5d8bd" else "#eca69b",align="right",depth=3)
        halo("catalyst-halo",if(snapshot.catalyst)"catalyst_on_halo" else "catalyst_off_halo",1014,652)
        node("catalyst-check",1031,670,15,15,if(snapshot.catalyst)"✓" else "□",size=15.0,
            color=if(snapshot.catalyst)"#171b1c" else "#cbb783",
            bg=if(snapshot.catalyst)"#a88e58" else null,depth=3)
        node("catalyst-text",1052,663,190,35,"触媒を使う  ·  所持 ${snapshot.materials.getValue(Material.CATALYST)}個",size=12.0,color="#b9bdac",depth=3)
        node("catalyst-info",1243,672,93,19,"成功率を100%に",size=10.0,color="#c9b986",depth=3)
        val enhanceLabel=when {
            snapshot.busy -> "鍛造中…"
            selected.level>=30 -> "最大強化"
            !canEnhance -> "素材が足りません"
            snapshot.history.isEmpty() -> "強化する"
            else -> "続けて強化する"
        }
        if(canEnhance && enhanceLabel=="強化する") {
            // This original button bitmap already says 強化する and includes Enter.
            sprites.keys.filter { it.startsWith("enhance_button/") }.forEach { key ->
                val offset=key.substringAfter('/').split('_')
                val sprite=sprites.getValue(key)
                node(key,1019+offset[0].toInt()/TYPOGRAPHY_SCALE,710+offset[1].toInt()/TYPOGRAPHY_SCALE,
                    sprite.width/TYPOGRAPHY_SCALE,sprite.height/TYPOGRAPHY_SCALE,sprite=key,depth=2)
            }
        } else {
            node("enhance-dynamic-bg",1019,710,326,51,bg=if(canEnhance)"#d4b879" else "#44443e")
            node("enhance-label",1063,720,238,29,enhanceLabel,size=18.0,
                color=if(canEnhance)"#211f19" else "#aeb1a6",align="center")
        }
        node("action-note",1020,768,324,16,note,size=10.0,color="#9fa699",align="center")
        node("footer-help",96,796,356,20,"左クリック 選択  │  Shift 閉じる",size=10.0,color="#aeb3a1")
        node("footer-state",1238,796,107,19,"所持素材を表示中",size=10.0,color="#9fac9b",align="right")

        // Interaction bounds are read from the approved browser export, then transformed once.
        // Visuals and hit testing therefore use the same letterboxed space.
        if(!modal) for(control in approved.getAsJsonArray("controls")) {
            val c=control.asJsonObject
            if(!c.get("receivesPointer").asBoolean || c.get("disabled").asBoolean) continue
            val dataset=c.getAsJsonObject("dataset")
            val selector=c.get("selector")?.takeUnless { it.isJsonNull }?.asString
            val action=when {
                dataset.has("gear") -> "select:${dataset.get("gear").asString}"
                selector=="#enhance-btn" && canEnhance -> "enhance"
                selector=="#catalyst-btn" -> "catalyst"
                selector=="#close-window" -> "close"
                selector=="#sound-btn" -> "sound"
                else -> null
            } ?: continue
            val r=c.getAsJsonObject("rect")
            node("hit-${list.size}",r.get("x").asDouble,r.get("y").asDouble,r.get("w").asDouble,r.get("h").asDouble,
                action=action,depth=5)
        }
        if(modal) {
            node("modal-mask",0,0,1440,920,bg="#bb0d1418",depth=MODAL_MASK_DEPTH)
            node("modal-border",470,176,500,568,bg="#ad9562",depth=MODAL_BORDER_DEPTH)
            node("modal-panel",474,180,492,560,bg="#202826",depth=MODAL_PANEL_DEPTH)
            node("modal-title",509,211,379,43,"この装備を強化しますか？",size=24.0,color="#ebdfc4",depth=MODAL_CONTENT_DEPTH,family="serif")
            node("modal-close",908,188,45,51,"×",size=25.0,bg="#202827",action="cancel",depth=MODAL_ACTION_DEPTH,align="center")
            node("modal-rule-top",509,260,422,1,bg="#39413c",depth=MODAL_CONTENT_DEPTH)
            node("modal-icon",525,293,60,66,sprite=if(selected.id=="ember")"sword_t2_thumb" else "greatsword_thumb",depth=MODAL_CONTENT_DEPTH)
            node("modal-gear-name",610,288,286,25,selected.name,size=16.0,depth=MODAL_CONTENT_DEPTH)
            node("modal-level",610,321,265,41,"+${selected.level}  →  +$next",size=30.0,color="#e6c886",depth=MODAL_CONTENT_DEPTH)
            node("modal-rule-gear",509,376,422,1,bg="#39413c",depth=MODAL_CONTENT_DEPTH)
            node("modal-attack-label",510,396,130,25,"成功時の物理攻撃",size=13.0,color="#abb5aa",depth=MODAL_CONTENT_DEPTH)
            node("modal-attack-value",777,395,152,27,"${selected.power} → ${selected.power+selected.step}",size=18.0,color="#eddaad",depth=MODAL_CONTENT_DEPTH,align="right")
            node("modal-rule-attack",509,432,422,1,bg="#39413c",depth=MODAL_CONTENT_DEPTH)
            node("modal-ore-name",510,451,160,21,"陽鉱の塊",size=12.0,color="#b7c0b1",depth=MODAL_CONTENT_DEPTH)
            node("modal-ore-amount",760,451,169,21,"${cost.materials.getValue(Material.ORE)}個  残り ${ore-cost.materials.getValue(Material.ORE)}",size=12.0,depth=MODAL_CONTENT_DEPTH,align="right")
            node("modal-crystal-name",510,482,160,21,"虚晶の欠片",size=12.0,color="#b7c0b1",depth=MODAL_CONTENT_DEPTH)
            node("modal-crystal-amount",760,482,169,21,"${cost.materials.getValue(Material.CRYSTAL)}個  残り ${crystal-cost.materials.getValue(Material.CRYSTAL)}",size=12.0,depth=MODAL_CONTENT_DEPTH,align="right")
            node("modal-silver-name",510,513,160,21,"銀貨",size=12.0,color="#b7c0b1",depth=MODAL_CONTENT_DEPTH)
            node("modal-silver-amount",760,513,169,21,"${number(cost.silver)} 銀貨  残り ${number(snapshot.silver-cost.silver)}",size=12.0,depth=MODAL_CONTENT_DEPTH,align="right")
            node("modal-risk-bg",509,550,422,67,bg="#2c2b25",depth=MODAL_CONTENT_DEPTH)
            node("modal-risk-accent",509,550,2,67,bg="#bd9c64",depth=MODAL_ACTION_DEPTH)
            node("modal-risk",523,566,397,42,if(snapshot.catalyst)"成功率100%。触媒を1個消費します。" else "成功率80%。失敗時も強化段階は維持します。素材と銀貨は消費します。",
                size=12.0,color="#d3bf9e",depth=MODAL_ACTION_DEPTH)
            node("modal-local-note",509,628,420,19,"ローカル表示テスト：成功例を再生",size=10.0,color="#aeb5a4",depth=MODAL_CONTENT_DEPTH)
            node("modal-cancel",509,668,199,44,"やめる",size=15.0,bg="#26302e",action="cancel",depth=MODAL_ACTION_DEPTH,align="center")
            node("modal-confirm",718,668,213,44,"強化する",size=17.0,color="#211f19",bg="#d7bb7c",action="confirm",depth=MODAL_ACTION_DEPTH,align="center")
        }
        return UiScene(800.0,480.0,list)
    }

    /** Reuses the approved art and geometry while replacing every fixture value with live server data. */
    fun forge(state: ForgeUiState, light: ForgeLightPhase = ForgeLightPhase.IDLE): UiScene {
        require(state.gears.size in 2..5 && state.materials.size >= 3)
        val selected = state.gears.single { it.id == state.selected }
        val ready = !state.busy && state.blockedReason == null
        val base = forge(productionTemplate, state.note, state.modal, state.muted, light)
        val lines = state.materials
        val first = lines[0]
        val second = lines[1]
        val third = lines[2]
        val groups = listOf(first, second, third)
        val supplemental = lines.drop(3)
        fun value(n: Long) = number(n)
        fun sprite(name: String) = sprites.getValue(name)
        fun gearIcon(id: String) = when(id) {
            "weapon" -> "sword_t2_thumb"
            "head" -> "helm_ui"
            "feet" -> "boots_ui"
            else -> "armor_ui"
        }
        val nodes = base.nodes.mapNotNull { node ->
            val id = node.id
            if (state.gears.size > 2 && (id.startsWith("gear-") || node.action in setOf("select:ember", "select:ash"))) return@mapNotNull null
            var replacement = node
            fun write(content: String, color: String? = null) {
                replacement = replacement.copy(text = content,
                    style = if (color == null) replacement.style else replacement.style + ("color" to color))
            }
            when {
                id == "wallet" -> write(value(state.silver))
                id == "gear-plate-0" || id == "gear-plate-1" -> {
                    val active = state.gears[id.last().digitToInt()].id == state.selected
                    replacement = replacement.copy(sprite=sprite(if(active) "gear_selected" else "gear_unselected"))
                }
                id == "gear-icon-0" || id == "gear-icon-1" -> {
                    val index = id.last().digitToInt()
                    replacement = replacement.copy(sprite = sprite(if(state.gears[index].id == "weapon") "sword_t2_thumb" else "helm_ui"))
                }
                id.startsWith("gear-name-") -> {
                    val gear = state.gears[id.last().digitToInt()]
                    write("${gear.name} +${gear.level}")
                }
                id.startsWith("gear-stat-") -> {
                    val gear = state.gears[id.last().digitToInt()]
                    write("T${gear.tier}   ${if(gear.id == "weapon") "物理攻撃" else "最大HP"} ${gear.power}")
                }
                id.startsWith("gear-status-") -> {
                    val gear = state.gears[id.last().digitToInt()]
                    write(if(gear.broken) "装備中  ·  破損" else "装備中  ·  強化 +${gear.level}")
                }
                id == "replenish-icon-0" || id == "replenish-icon-1" ->
                    replacement = replacement.copy(sprite = sprite(lines[id.last().digitToInt()].icon))
                id == "replenish-name-0" || id == "replenish-name-1" -> write(lines[id.last().digitToInt()].name)
                id == "replenish-count-0" || id == "replenish-count-1" -> {
                    val material = lines[id.last().digitToInt()]
                    write("所持 ${value(material.owned)}個  ·  必要 ${value(material.required)}個")
                }
                id == "can-enhance" -> write(if(ready) "▪ 素材が揃っています" else "▪ ${state.blockedReason ?: "鍛造中"}",
                    if(ready) "#a4cba4" else "#e1a29a")
                id == "history-heading" -> write(if(supplemental.isEmpty()) "この装備の鍛造記録" else "追加で必要な素材")
                id == "history-value" -> write(if(supplemental.isEmpty()) state.history ?: "鍛造後、ここに結果が残ります。" else
                    supplemental.joinToString("  ") { "${it.name} ${value(it.owned)}/${value(it.required)}" },
                    if(supplemental.any { it.owned < it.required }) "#eca69b" else null)
                id == "rarity" -> write("T${selected.tier} 装備  ·  ${if(selected.id == "weapon") "武器" else "防具"}")
                id == "hero-name" -> write("${selected.name}  +${selected.level}")
                id == "hero-weapon" -> replacement = replacement.copy(item=selected.iconItem,
                    sprite = if(selected.iconItem != null) null else sprite(if(selected.id == "weapon") "sword_t2_hero" else gearIcon(selected.id)),
                    box = if(selected.id == "weapon") replacement.box else rect(621,369,120,164))
                id == "hero-meta" -> write("${if(selected.id == "weapon") "武器" else "防具"} Tier ${selected.tier}   │   強化 +${selected.level}${if(selected.broken) "   │   破損中" else ""}")
                id == "effect-icon" -> replacement = replacement.copy(sprite=sprite("hammer"))
                id == "effect-name" -> write(if(selected.id == "weapon") "装備中の武器" else "装備中の防具")
                id == "effect-text" -> write("本編の装備性能と強化状態を表示しています。")
                id == "effect-detail" -> write("")
                id == "result-title" -> write(if(state.history == null) "強化の結果" else "次の強化")
                id == "before-level" -> {
                    val s = sprite("current_level_${selected.level}")
                    replacement = replacement.copy(sprite=s,box=rect(1093.7-s.width/(2*TYPOGRAPHY_SCALE),273,
                        s.width/TYPOGRAPHY_SCALE,s.height/TYPOGRAPHY_SCALE))
                }
                id == "after-level" -> {
                    val name = if(selected.level >= 30) "next_level_max" else "next_level_${selected.level+1}"
                    val s = sprite(name)
                    replacement = replacement.copy(sprite=s,box=rect(if(selected.level >= 30) 1199 else 1230,273,
                        s.width/TYPOGRAPHY_SCALE,s.height/TYPOGRAPHY_SCALE))
                }
                id == "attack-label" -> write(if(selected.id == "weapon") "物理攻撃" else "最大HP")
                id == "attack-value" -> write("${selected.power} → ${selected.nextPower}  +${selected.nextPower-selected.power}")
                id == "chance-value" -> write("${"%.1f".format(Locale.US,state.chance)}%")
                id == "chance-fill" -> replacement = replacement.copy(box=rect(1028,431,309*state.chance/100.0,4))
                id == "failure-note" -> write(if(state.breakOnFailure > 0)
                    "失敗時：段階維持・破損率 ${"%.1f".format(Locale.US,state.breakOnFailure)}%・素材消費"
                    else "失敗時：強化段階を維持・素材は消費")
                id.startsWith("cost-icon-") -> replacement = replacement.copy(sprite=sprite(groups[id.last().digitToInt()].icon))
                id.startsWith("cost-name-") -> write(groups[id.last().digitToInt()].name)
                id.startsWith("cost-after-") -> {
                    val material = groups[id.last().digitToInt()]
                    write(if(material.owned >= material.required) "所持 ${value(material.owned)} → 残り ${value(material.owned-material.required)}"
                        else "所持 ${value(material.owned)} · あと${value(material.required-material.owned)}個不足")
                }
                id.startsWith("cost-amount-") -> {
                    val material=groups[id.last().digitToInt()]
                    write("${value(material.required)} 個",if(material.owned >= material.required) "#c5d8bd" else "#eca69b")
                }
                id == "silver-icon" -> replacement = replacement.copy(sprite=sprite(third.icon))
                id == "silver-name" -> write(third.name)
                id == "silver-after" -> write(if(third.owned >= third.required) "所持 ${value(third.owned)} → 残り ${value(third.owned-third.required)}"
                    else "所持 ${value(third.owned)} · あと${value(third.required-third.owned)}個不足")
                id == "silver-amount" -> write("${value(third.required)} 個",if(third.owned >= third.required) "#c5d8bd" else "#eca69b")
                id == "catalyst-check" -> write(if(state.focused) "✓" else "□")
                id.startsWith("catalyst-halo-") -> {
                    val offset=id.substringAfterLast('-')
                    replacement=replacement.copy(sprite=sprite("${if(state.focused) "catalyst_on_halo" else "catalyst_off_halo"}/${offset}_0"))
                }
                id == "catalyst-text" -> write("触媒を使う  ·  刻印粉 ${value(state.catalystOwned)}個")
                id == "catalyst-info" -> write("成功率 +15pt")
                id == "action-note" -> write(state.note)
                id == "modal-icon" -> replacement = replacement.copy(item=selected.iconItem,
                    sprite=if(selected.iconItem != null) null else sprite(gearIcon(selected.id)))
                id == "modal-gear-name" -> write(selected.name)
                id == "modal-level" -> write("+${selected.level}  →  +${(selected.level+1).coerceAtMost(30)}")
                id == "modal-attack-label" -> write(if(selected.id == "weapon") "成功時の物理攻撃" else "成功時の最大HP")
                id == "modal-attack-value" -> write("${selected.power} → ${selected.nextPower}")
                id == "modal-ore-name" -> write(first.name)
                id == "modal-ore-amount" -> write("${value(first.required)}個  残り ${value(first.owned-first.required)}")
                id == "modal-crystal-name" -> write(second.name)
                id == "modal-crystal-amount" -> write("${value(second.required)}個  残り ${value(second.owned-second.required)}")
                id == "modal-silver-name" -> write(third.name)
                id == "modal-silver-amount" -> write("${value(third.required)}個  残り ${value(third.owned-third.required)}")
                id == "modal-risk" -> write("成功率 ${"%.1f".format(Locale.US,state.chance)}%。失敗時破損率 ${"%.1f".format(Locale.US,state.breakOnFailure)}%。" +
                    if(supplemental.isEmpty()) "素材は毎回消費します。" else "\n追加：${supplemental.joinToString("・") { "${it.name}×${it.required}" }}")
                id == "modal-local-note" -> write("銀貨消費なし。素材を消費し、強化は取り消せません。")
                id.startsWith("enhance_button/") && !ready -> return@mapNotNull null
                id == "enhance-dynamic-bg" -> replacement = replacement.copy(style=replacement.style + ("background-color" to if(ready) "#d4b879" else "#44443e"))
                id == "enhance-label" -> write(if(ready) "強化する" else state.blockedReason ?: "鍛造中…")
                id.startsWith("hit-") && node.action == "enhance" && !ready -> return@mapNotNull null
                id.startsWith("hit-") && node.action == "catalyst" && selected.level >= 30 -> return@mapNotNull null
            }
            replacement
        }.toMutableList()
        if (state.gears.size > 2) {
            state.gears.forEachIndexed { index, gear ->
                val y = 280 + index * 42
                val active = gear.id == state.selected
                put(nodes,"live-gear-plate-$index",95,y,229,41,
                    sprite=if(active) "gear_selected_compact" else "gear_unselected_compact",depth=1)
                put(nodes,"live-gear-icon-$index",105,y+5,32,32,
                    sprite=if(gear.iconItem == null) gearIcon(gear.id) else null,item=gear.iconItem,depth=3)
                put(nodes,"live-gear-name-$index",145,y+3,165,19,"${gear.name} +${gear.level}",size=12.5)
                put(nodes,"live-gear-stat-$index",145,y+23,168,15,
                    "${if(gear.id == "weapon") "物理攻撃" else "最大HP"} ${gear.power}${if(gear.broken) "  破損" else ""}",
                    size=9.5,color=if(gear.broken) "#e1a29a" else "#aeb5a7")
                if (!state.modal) hit(nodes,"live-gear-hit-$index",95,y,229,41,"select:${gear.id}")
            }
        }
        if(!ready) {
            nodes += UiNode("live-enhance-bg",rect(1019,710,326,51),"",mapOf("background-color" to "#44443e"),null,null,true,2)
            nodes += UiNode("live-enhance-label",rect(1034,719,295,31),state.blockedReason ?: "鍛造中…",
                mapOf("color" to "#e1ada4","font-size" to "15px","text-align" to "center",
                    "font-family" to "projects_ui_polish05:sans"),null,null,true,4)
        }
        // The third cost row replaces the HTML's silver cost; live silver is informational only.
        for(index in 0..2) {
            val material = groups[index]
            if(material.owned >= material.required) continue
            val y = 494 + index*52
            nodes.removeIf { it.id == "cost-halo-$index-0" || it.id == "cost-halo-$index-256" ||
                (index == 2 && it.id.startsWith("cost-halo-silver-")) }
            for(offset in listOf(0,256)) {
                val s=sprite("cost_missing_halo/${offset}_0")
                nodes += UiNode("live-cost-halo-$index-$offset",rect(1019+offset,y,s.width,s.height),"",
                    emptyMap(),null,null,true,2,s)
            }
        }
        return UiScene(base.width,base.height,nodes)
    }
}
