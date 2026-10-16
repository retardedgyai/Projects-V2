package dev.projects.server.coreloop

/** What the 魔術書 says: entries, branches and chapters. Content only; what a player has done is [GrimoireProgress]. */
internal object GrimoireBook {
    data class Goal(val text: String, val target: Int = 1)

    data class Reward(val kind: Kind, val amount: Int, val aspect: String? = null) {
        enum class Kind { SILVER, INK }
        val label get() = when (kind) { Kind.SILVER -> "銀貨 $amount"; Kind.INK -> "インク ×$amount" }
    }

    data class Entry(
        val id: String, val col: Int, val row: Int, val icon: String, val title: String, val chapter: String,
        val concept: String, val goals: List<Goal>, val unlocks: List<Pair<String, String>>, val reward: Reward?,
        val parents: List<String>, val side: Boolean = false, val mystery: Boolean = false, val goal: Boolean = false,
        val hero: String? = null,
    )

    /** Mastery points from one 魔術熟練 rank to the next. */
    const val RANK_XP = 1000

    fun entry(id: String) = ENTRIES.first { it.id == id }

    val BRANCHES = listOf("幹" to "ic_book", "刻印術" to "ic_scroll", "注入" to "ic_ignis", "召喚" to "ic_mortuus",
        "錬金" to "ic_victus", "地脈術" to "ic_terra", "魔導工学" to "ic_potentia")
    val BRANCH_LINES = listOf("",
        "帰還・加速・転移などの巻物。使えば消える、魔術の効果そのもの。",
        "武器や防具に、魔術ならではの性質を与える・付け替える。",
        "ボスを呼ぶ封印の鍵を作る。戦闘職がボスに挑むための入口。",
        "薬や食べ物。体に入れる魔術。",
        "領地の結界、天候、転移の網。世界そのものに手を出す。",
        "仕分けや自動化の装置。工房を工場のように組む。")
    val CHAPTERS = listOf("序章" to 1.0, "一章" to 3.5, "二章" to 5.0, "三章" to 6.0, "終章" to 7.0)

    val ENTRIES = listOf(
        Entry("n1", 0, 2, "ic_book", "古い手帳", "序章 観測", "光る草に挟まっていた手帳。最初のページには一文だけ。「すべての物は、目に見えない性質（相）でできている」",
            listOf(Goal("光る素材を拾う")), listOf("開く" to "魔術書"), null, emptyList()),
        Entry("n2", 1, 1, "ic_goggles", "観測の眼鏡", "序章 観測", "かぶっている間だけ、見ている物の相が見える。",
            listOf(Goal("眼鏡を作る（ガラス2・銅1）")), listOf("見える" to "照準の先の相"), null, listOf("n1")),
        Entry("n3", 1, 3, "ic_desk", "研究机", "序章 観測", "読めない相は、机の上で考えると分かる。最初の「自分の魔術の場所」。",
            listOf(Goal("研究机を置く")), listOf("置ける" to "研究机"), null, listOf("n1"), hero = "hero_desk"),
        Entry("n4", 2, 2, "ic_lux", "最初の研究「光」", "序章 観測", "相は2つ合わさって新しい相になる。松明の相は、Aer と Ignis が合わさった Lux だった。",
            listOf(Goal("研究盤で Lux を解く")), listOf("見える" to "珍しい物の光"), Reward(Reward.Kind.SILVER, 20), listOf("n2", "n3")),
        Entry("n5", 3, 1, "ic_distiller", "蒸留器", "1章 抽出", "相は取り出して液体（Essentia）にできる。素材を窯で溶かすと、中の相が分かれて出てくる。",
            listOf(Goal("蒸留器を作って工房に置く")), listOf("置ける" to "蒸留器"), Reward(Reward.Kind.INK, 4, "aqua"), listOf("n4"), hero = "hero_distiller"),
        Entry("n6", 3, 3, "ic_jar", "瓶", "1章 抽出", "Essentia は瓶に溜まる。瓶ひとつにつき相はひとつ。",
            listOf(Goal("瓶を3つ置く")), listOf("置ける" to "瓶"), null, listOf("n4"), hero = "hero_jar"),
        Entry("n7", 4, 2, "ic_aqua", "相を溜める", "1章 抽出", "蒸留器と瓶がそろうと、相が溜まっていく。",
            listOf(Goal("ひとつの瓶に Essentia を16溜める")), listOf("作れる" to "瓶詰めの Essentia"), Reward(Reward.Kind.SILVER, 40), listOf("n5", "n6")),
        Entry("n8", 4, 4, "ic_vacuos", "寄り道：相の図鑑", "1章 抽出", "本筋には要らない。いろんな物を見て、相を集める。",
            listOf(Goal("相を12種類観察する", 12)), listOf("もらえる" to "眼鏡の見た目"), null, listOf("n6"), side = true),
        Entry("n9", 5, 1, "ic_wand", "杖", "2章 流れ", "杖は工房をつなぐレンチ。顎で管をはさみ、弁の向きを決める。",
            listOf(Goal("杖を作る")), listOf("作れる" to "杖"), null, listOf("n7")),
        Entry("n10", 5, 3, "ic_motus", "管と弁", "2章 流れ", "相は、引っ張る力の強いほうへ流れる。",
            listOf(Goal("蒸留器から瓶へ管でつなぐ")), listOf("置ける" to "管、弁"), null, listOf("n7")),
        Entry("n11", 6, 2, "ic_vacuos", "地脈", "3章 地脈", "足もとを流れる、見えない川がある。",
            listOf(Goal("地脈の上に研究机を置く")), listOf("見える" to "地脈の流れ"), null, listOf("n9", "n10"), mystery = true),
        Entry("n12", 7, 2, "ic_scroll", "帰還の巻物", "終章 最初の儀式", "幹のゴール。遠征先から、魔術で帰ってくる。ここまで来ると専門の枝の封が解ける。",
            listOf(Goal("小さな祭壇で帰還の巻物を作る")), listOf("開く" to "専門の枝すべて"), null, listOf("n11"), goal = true),
    )
}
