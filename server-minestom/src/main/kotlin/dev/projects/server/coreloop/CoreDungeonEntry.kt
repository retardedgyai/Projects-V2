package dev.projects.server.coreloop

/** Reward checkpoints persist; world/run boons intentionally do not survive logout. */
data class CoreDungeonEntry(val ascension: Int, val stages: Int, val roomsPerFloor: Int, val rewardedStage: Int = 0) {
    companion object {
        const val RETIRED_MESSAGE = "星環の深殿は終了しました"
    }

    init {
        require(ascension in 0..20 && roomsPerFloor in 3..6 && stages in roomsPerFloor..18 && stages % roomsPerFloor == 0)
        require(rewardedStage in 0..stages)
    }
}
