package dev.projects.server.coreloop.ui

/** Pixel-art subjects shared with the pack generator. Ordinals are the E700 atlas contract. */
enum class CoreMenuArt {
    EXPEDITION, FORGE, STORAGE, GEAR, GATHER, HELP, TRIAL, RETURN,
    ENHANCE, REFINE, CRAFT, MOD, WEAPON, ARMOR, WOOD, ORE, STONE, HIDE,
    FIBER, PLANK, INGOT, CUT_STONE, LEATHER, CLOTH, POTION, TABLET, ORB, BOSS, SHARD;

    internal val glyph: Char get() = (0xE700 + ordinal).toChar()

    /** Bitmap advances depend on the opaque right edge, not the nominal square cell. */
    internal fun advance(size: Int): Int = when (size) {
        16 -> advances.getValue(this).first
        32 -> advances.getValue(this).second
        else -> error("Menu art has no $size px atlas")
    }

    companion object {
        private val advances: Map<CoreMenuArt, Pair<Int, Int>> by lazy {
            val resource = "core-ui-pack/assets/projects/menu/art.tsv"
            val parsed = requireNotNull(CoreMenuArt::class.java.classLoader.getResourceAsStream(resource)) {
                "Missing menu art metrics; regenerate scripts/build_core_ui_assets.py"
            }.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
                    val fields = line.split('\t')
                    require(fields.size == 4) { "Malformed menu art metrics" }
                    val art = valueOf(fields[0])
                    require(fields[1].toInt() == art.ordinal) { "Menu art ordinal differs from its pack glyph: $art" }
                    val small = fields[2].toInt()
                    val large = fields[3].toInt()
                    require(small in 1..17 && large in 1..33) { "Menu art escaped its source cell: $art" }
                    art to (small to large)
                }.toList()
            }
            require(parsed.size == entries.size && parsed.map { it.first }.toSet() == entries.toSet()) {
                "Menu art metrics must contain every subject exactly once"
            }
            parsed.toMap()
        }
    }
}
