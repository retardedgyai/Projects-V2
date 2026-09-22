package dev.projects.modellab

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import com.yuuki14202028.WseeAssets

class ModelContractTest {
    @Test fun generatedNamesReferToRealModels() {
        assertEquals(16, WseeAssets.All.size)
        assertEquals("ice_fang.bbmodel", WseeAssets.IceFang.Model)
        assertEquals("erupt", WseeAssets.IceFang.Anim.Erupt)
        assertEquals("vesper.bbmodel", WseeAssets.Vesper.Model)
        assertEquals("sweep", WseeAssets.Vesper.Anim.Sweep)
        assertEquals("shatter", WseeAssets.PiglinLord.Anim.Shatter)
    }
    @Test fun unknownAnimationFailsBeforePlayback() {
        val definition = ModelDefinition("test", mapOf("idle" to 1.0))
        assertEquals("idle", definition.requireAnimation("idle"))
        assertFailsWith<IllegalArgumentException> { definition.requireAnimation("idlle") }
    }
    @Test fun importedBossScalesMatchTheirOriginalVisuals() {
        assertEquals(3.2f, ModelDefinition(WseeAssets.Osirion.Model, emptyMap()).previewScale)
        assertEquals(4f, ModelDefinition(WseeAssets.Radix.Model, emptyMap()).previewScale)
        assertEquals(3f, ModelDefinition(WseeAssets.Vesper.Model, emptyMap()).previewScale)
        assertEquals(3f, ModelDefinition(WseeAssets.PiglinLord.Model, emptyMap()).previewScale)
    }
    @Test fun metricsAreBoundedAndSnapshotsDoNotMutate() {
        val metrics = TickMetrics(3)
        metrics.record(1.0)
        val before = metrics.snapshot()
        listOf(2.0, 3.0, 4.0).forEach(metrics::record)
        assertEquals(listOf(1.0), before)
        assertEquals(listOf(2.0, 3.0, 4.0), metrics.snapshot())
        assertTrue(metrics.summary(42).contains("Entity 42"))
    }
    @Test fun invalidMeasurementsRejected() {
        val metrics = TickMetrics()
        assertFailsWith<IllegalArgumentException> { metrics.record(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { metrics.record(-1.0) }
        assertFailsWith<IllegalArgumentException> { TickMetrics(0) }
    }
}
