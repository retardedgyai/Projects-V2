package dev.projects.server.coreloop

import dev.projects.webui.ForgeLightPhase
import dev.projects.webui.ForgeUiFlow
import dev.projects.webui.ForgeUiGear
import dev.projects.webui.ForgeUiMaterial
import dev.projects.webui.ForgeUiReceipt
import dev.projects.webui.ForgeUiState
import dev.projects.webui.Polish05Scene
import dev.projects.webui.UiScene

/** One NPC session. The account and roll stay in the production ledger. */
internal class CorePolish05ForgeFlow(
    private val presentation: Polish05Scene,
    private val account: () -> CoreAccount?,
    private val allowed: () -> Boolean,
    private val enhance: (CoreGearSlot, CoreEnhancementMode, Long, (Boolean) -> Unit) -> Unit,
) : ForgeUiFlow {
    override val view = "forge"
    override var muted = false; private set
    override var operationActive = false; private set
    private var selected = CoreGearSlot.WEAPON
    private var focused = false
    private var modal = false
    private var quotedRevision: Long? = null
    private var receipt: ForgeUiReceipt? = null
    private var history: String? = null
    private var note = "素材と成功率を確かめてから鍛造を始めます。"

    private fun mode() = if(focused) CoreEnhancementMode.FOCUSED else CoreEnhancementMode.STANDARD
    private fun quote(a: CoreAccount) = CoreEnhancementCatalog.quote(a, selected, mode())

    override fun scene(light: ForgeLightPhase): UiScene = presentation.forge(project(requireNotNull(account())), light)

    private fun project(a: CoreAccount): ForgeUiState {
        val q = quote(a)
        val gear = CoreGearSlot.equipSlots.map { slot ->
            val level = CoreEnhancementCatalog.state(a, slot).level
            val next = a.copy(
                weaponEnhancement = if(slot == CoreGearSlot.WEAPON && level < 30) CoreEnhancementState(level+1) else a.weaponEnhancement,
            ).let { if (slot == CoreGearSlot.WEAPON || level >= 30) it else it.withArmor(slot,
                a.armor(slot).copy(enhancement = CoreEnhancementState(level+1))) }
            ForgeUiGear(
                slot.name.lowercase(),
                "T${CoreAffixCatalog.gearTier(a,slot)} ${slot.displayName}",
                CoreAffixCatalog.gearTier(a,slot),
                level,
                if(slot == CoreGearSlot.WEAPON) CoreWeaponPresentation.damage(a) else CoreWeaponPresentation.health(a),
                if(slot == CoreGearSlot.WEAPON) CoreWeaponPresentation.damage(next) else CoreWeaponPresentation.health(next),
                CoreEconomy.broken(a, slot),
            )
        }
        val cost = q.recipe.costs.map { (material, required) ->
            ForgeUiMaterial(material.displayName, a.amount(material), required, when(material.resource) {
                CoreResource.WOOD -> "forge_material_wood"
                CoreResource.ORE -> "forge_material_ore"
                CoreResource.STONE -> "forge_material_stone"
                CoreResource.HIDE -> "forge_material_hide"
                CoreResource.FIBER -> "forge_material_fiber"
                CoreResource.INGOT -> "forge_material_ingot"
                CoreResource.BOARD -> "forge_material_board"
                CoreResource.STONE_BLOCK -> "forge_material_cut_stone"
                CoreResource.LEATHER -> "forge_material_leather"
                CoreResource.CLOTH -> "forge_material_cloth"
                CoreResource.AFFIX_DUST -> "forge_material_affix_dust"
                else -> "forge_material_affix_dust"
            })
        }.toMutableList()
        while(cost.size < 3) cost += ForgeUiMaterial("消費なし",0,0,"forge_material_affix_dust")
        val materialBlock = cost.firstOrNull { it.owned < it.required }?.let { "${it.name}が不足しています" }
        return ForgeUiState(gear,selected.name.lowercase(),a.silver,
            q.successChancePercent,q.breakOnFailurePercent,focused,cost,
            a.amount(CoreResource.AFFIX_DUST),q.blockedReason ?: materialBlock,history,note,modal,muted,operationActive)
    }

    override fun action(action: String): Boolean {
        if(operationActive) return false
        if(action == "sound") { muted = !muted; return true }
        if(modal) return when(action) {
            "cancel" -> { modal=false; quotedRevision=null; true }
            "confirm" -> {
                val a=account() ?: return false
                val revision=quotedRevision ?: return false
                val q=quote(a)
                if(a.revision != revision || q.blockedReason != null || !allowed()) {
                    modal=false;quotedRevision=null;note="状態が変わりました。費用を確認し直してください。";true
                } else {
                    val before=q.currentLevel
                    val target=selected
                    val selectedMode=mode()
                    operationActive=true;modal=false;quotedRevision=null
                    note="鍛造中…"
                    enhance(target,selectedMode,revision) { committed ->
                        val after=account()?.let { CoreEnhancementCatalog.state(it,target).level } ?: before
                        operationActive=false
                        if(committed) {
                            val success=after>before
                            history=if(success) "強化成功  +$before → +$after" else "強化失敗・段階維持  +$before"
                            note=if(success) "強化成功。次の強化を表示しています。" else "強化失敗。素材を消費し、段階を維持しました。"
                            receipt=ForgeUiReceipt(success)
                        } else note="保存できませんでした。状態を確認してやり直してください。"
                    }
                    true
                }
            }
            else -> false
        }
        val a=account() ?: return false
        return when(action) {
            "select:weapon", "select:head", "select:chest", "select:legs", "select:feet", "select:ember", "select:ash" -> {
                selected=when(action) {
                    "select:ember", "select:weapon" -> CoreGearSlot.WEAPON
                    "select:ash", "select:chest" -> CoreGearSlot.CHEST
                    else -> CoreGearSlot.valueOf(action.substringAfter(':').uppercase())
                }
                focused=false;note="装備を選択しました。";true
            }
            "catalyst" -> {
                if(!focused && (CoreEnhancementCatalog.quote(a,selected).guaranteed || CoreEnhancementCatalog.state(a,selected).level >= 30)) false
                else { focused = !focused; note="触媒の費用と成功率を確認してください。";true }
            }
            "enhance" -> {
                if(!allowed()) false else if(quote(a).blockedReason != null) {
                    note=quote(a).blockedReason ?: "強化できません";true
                } else { modal=true;quotedRevision=a.revision;true }
            }
            else -> false
        }
    }

    override fun tick(nowMs: Long): ForgeUiReceipt? = receipt.also { receipt=null }
}
