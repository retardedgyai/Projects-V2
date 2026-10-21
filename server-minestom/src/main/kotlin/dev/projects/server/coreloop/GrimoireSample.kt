package dev.projects.server.coreloop

/** Sample progress until quests are driven by the world. */
internal object GrimoireSample {
    /** Partway through 序章: the look the screen was designed with. */
    fun midway() = GrimoireProgress(
        done = mutableSetOf("n1", "n2", "n3", "n4"),
        claimed = mutableSetOf("n1", "n2", "n3"),
        pinned = "n5",
        counts = mutableMapOf("n8:0" to 7),
        ink = mutableMapOf("aqua" to 14, "ignis" to 9, "aer" to 8, "terra" to 5),
        silver = 12_480,
    )

    /** The very beginning, for 「最初から」 while developing. */
    fun start() = GrimoireProgress()
}
