package dev.projects.server.coreloop

import dev.projects.server.questmap.VerdantRoadQuestRuntime
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** One selected preview per player. A MOD edit invalidates both the plan and its placed resources. */
internal class CoreMapPreparation(private val executor: Executor) {
    private class Preview(val signature: String, map: CoreOwnedMap, executor: Executor) {
        private val discarded = AtomicBoolean()
        val future: CompletableFuture<VerdantRoadQuestRuntime> = CompletableFuture.supplyAsync({
            if (discarded.get()) throw CancellationException("Map selection changed")
            VerdantRoadQuestRuntime.prepare(map.seed, customization = CoreLoopItems.customization(map), resourceTier = map.tier).join()
        }, executor)
        fun discard() {
            discarded.set(true)
            future.thenAccept { it.close() }
        }
    }
    private val previews = mutableMapOf<UUID, Preview>()

    @Synchronized
    fun warm(playerId: UUID, map: CoreOwnedMap): Boolean {
        var preview = previews[playerId]
        if (preview == null || preview.signature != map.toString() || preview.future.isCompletedExceptionally) {
            preview?.discard()
            preview = Preview(map.toString(), map, executor)
            previews[playerId] = preview
        }
        return preview.future.isDone && !preview.future.isCompletedExceptionally
    }

    @Synchronized
    fun take(playerId: UUID, map: CoreOwnedMap): CompletableFuture<VerdantRoadQuestRuntime> {
        val preview = previews.remove(playerId)
        if (preview?.signature == map.toString() && !preview.future.isCompletedExceptionally) return preview.future
        preview?.discard()
        return Preview(map.toString(), map, executor).future
    }

    @Synchronized
    fun forget(playerId: UUID) { previews.remove(playerId)?.discard() }

    @Synchronized
    fun close() { previews.values.forEach { it.discard() }; previews.clear() }
}
