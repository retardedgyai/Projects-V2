package dev.projects.server.coreloop.adventure

import java.util.UUID

data class DungeonParty(val id: UUID, val leader: UUID, val tier: Int, val ascension: Int,
    val members: List<UUID>, val ready: Set<UUID> = emptySet(), val starting: Boolean = false) {
    init { require(members.size in 1..4 && members.distinct().size == members.size && leader in members && members.containsAll(ready)); require(tier in 1..4 && ascension in 0..20) }
}

/** A snapshot is a launch contract. Join/leave/readiness changes cannot alter a starting roster. */
class DungeonParties {
    private val parties = linkedMapOf<UUID, DungeonParty>()
    @Synchronized fun list() = parties.values.toList()
    @Synchronized fun of(player: UUID) = parties.values.firstOrNull { player in it.members }
    @Synchronized fun create(leader: UUID, tier: Int, ascension: Int): DungeonParty {
        check(of(leader) == null) { "すでにパーティに参加しています" }
        return DungeonParty(UUID.randomUUID(), leader, tier, ascension, listOf(leader)).also { parties[it.id] = it }
    }
    @Synchronized fun join(player: UUID, id: UUID): DungeonParty {
        check(of(player) == null) { "先に今のパーティを抜けてください" }
        val p = parties[id] ?: error("募集は終了しました")
        check(!p.starting && p.members.size < 4) { "出発済みか満員です" }
        return p.copy(members = p.members + player, ready = emptySet()).also { parties[id] = it }
    }
    @Synchronized fun ready(player: UUID): DungeonParty {
        val p = of(player) ?: error("パーティがありません")
        check(!p.starting)
        return p.copy(ready = if (player in p.ready) p.ready - player else p.ready + player).also { parties[p.id] = it }
    }
    @Synchronized fun launch(leader: UUID): DungeonParty {
        val p = of(leader) ?: error("パーティがありません")
        check(p.leader == leader && !p.starting) { "リーダーだけが出発できます" }
        check(p.ready.containsAll(p.members)) { "全員が準備完了にしてください" }
        return p.copy(starting = true).also { parties[p.id] = it }
    }
    @Synchronized fun leave(player: UUID) {
        val p = of(player) ?: return
        check(!p.starting) { "出発の準備中です" }
        if (p.members.size == 1) parties.remove(p.id)
        else {
            val members = p.members - player
            parties[p.id] = p.copy(members = members, leader = if (p.leader == player) members.first() else p.leader, ready = emptySet())
        }
    }
    @Synchronized fun remove(id: UUID) { parties.remove(id) }
    @Synchronized fun unlock(id: UUID) { parties[id]?.let { parties[id] = it.copy(starting = false, ready = emptySet()) } }
}
