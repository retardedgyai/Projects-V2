package dev.projects.server.coreloop

/** Build genuine pre-v11 fixture rows from a current account, excluding the three new armor identities. */
internal fun armorV10Body(account: CoreAccount): String {
    val partIds = account.armorParts.values.map { it.identity.id.toString() }.toSet()
    return CoreAccountCodec.encode(account).substringBefore("checksum\t").lineSequence()
        .filterNot { row -> row.startsWith("armor-part\t") ||
            (row.startsWith("gear-quality\t") || row.startsWith("gear-base\t")) && row.split('\t').getOrNull(1) in partIds }
        .joinToString("\n").replaceFirst("PROJECTS_CORE_LOOP\t11\t", "PROJECTS_CORE_LOOP\t10\t")
}
