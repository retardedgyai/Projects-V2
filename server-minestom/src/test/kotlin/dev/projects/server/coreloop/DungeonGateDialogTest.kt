package dev.projects.server.coreloop

import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DungeonGateDialogTest {
    private val metrics = assertNotNull(PlayerMenuDialog.metrics(), "menu art missing: run scripts/build_menu_dialog_assets.py")
    // Building the dialog never touches the host; only show and click do.
    private val host = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(CoreMenuHost::class.java)) { _, method, _ ->
        error("host.${method.name} must not be called while drawing")
    } as CoreMenuHost

    /** Every glyph is placed at a height the bake made for it, or DialogCanvas throws: the screen hung from the title, the
     *  tier beside it and the body's hit areas, at each GUI scale. */
    @Test fun `the gate builds at every GUI scale`() {
        val gate = DungeonGateDialog(metrics, host, { 4 }, {})
        for (scale in listOf(2, 3, 4)) {
            val size = metrics.forScale(scale)
            gate.dialog(CoreAccount(UUID.randomUUID()), size)
            for (tier in 1..4) assertTrue("gate_tier$tier" in size.sprites, "gate_tier$tier at GUI scale $scale")
        }
    }
}
