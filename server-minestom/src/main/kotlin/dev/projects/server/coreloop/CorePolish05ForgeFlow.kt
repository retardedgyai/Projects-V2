package dev.projects.server.coreloop

import dev.projects.webui.ForgeLightPhase
import dev.projects.webui.ForgeUiFlow
import dev.projects.webui.ForgeUiReceipt
import dev.projects.webui.ForgeV3Banner
import dev.projects.webui.ForgeV3Cost
import dev.projects.webui.ForgeV3Gear
import dev.projects.webui.ForgeV3Mod
import dev.projects.webui.ForgeV3Motion
import dev.projects.webui.ForgeV3MotionKind
import dev.projects.webui.ForgeV3Scene
import dev.projects.webui.ForgeV3State
import dev.projects.webui.UiScene

/** One harbor smith session. The account and the roll stay in the production ledger;
 *  this flow only paces the reveal so the item can charge before the result is shown. */
internal class CorePolish05ForgeFlow(
    private val presentation: ForgeV3Scene,
    private val account: () -> CoreAccount?,
    private val allowed: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    private val enhance: (CoreGearSlot, CoreEnhancementMode, Long, (Boolean) -> Unit) -> Unit,
) : ForgeUiFlow {
    override val view = "forge"
    override val ownsEffects = true
    override var muted = false; private set
    override var operationActive = false; private set
    private var selected = CoreGearSlot.WEAPON
    private var focused = false
    private var modal = false
    private var quotedRevision: Long? = null
    private var receipt: ForgeUiReceipt? = null
    private var history: String? = null
    private var note = "素材と成功率を確かめてから鍛造を始めます。"
    private var motion: ForgeV3Motion? = null
    private var banner: ForgeV3Banner? = null

    /** Shown while the item charges, so the new level is not visible before the reveal. */
    private var charging: ForgeV3State? = null
    private var chargeEndsAt = 0L
    private var outcome: Outcome? = null
    private data class Outcome(val committed: Boolean, val slot: CoreGearSlot, val before: Int, val after: Int, val broken: Boolean)

    private fun mode() = if (focused) CoreEnhancementMode.FOCUSED else CoreEnhancementMode.STANDARD
    private fun quote(a: CoreAccount) = CoreEnhancementCatalog.quote(a, selected, mode())

    override fun scene(light: ForgeLightPhase): UiScene {
        val now = clock()
        val state = charging ?: project(requireNotNull(account()))
        return presentation.build(state, now, motion, banner)
    }

    private fun gear(a: CoreAccount, slot: CoreGearSlot): ForgeV3Gear {
        val state = CoreEnhancementCatalog.state(a, slot)
        val level = state.level
        val tier = CoreAffixCatalog.gearTier(a, slot)
        val weapon = slot == CoreGearSlot.WEAPON
        val next = if (level >= 30) a else if (weapon) a.copy(weaponEnhancement = CoreEnhancementState(level + 1))
            else a.withArmor(slot, a.armor(slot).copy(enhancement = CoreEnhancementState(level + 1)))
        val identity = CoreEconomy.identity(a, slot)
        val job = a.journey.job.name.lowercase()
        val part = when (slot) {
            CoreGearSlot.HEAD -> "helmet"
            CoreGearSlot.CHEST, CoreGearSlot.ARMOR -> "chestplate"
            CoreGearSlot.LEGS -> "leggings"
            CoreGearSlot.FEET -> "boots"
            CoreGearSlot.WEAPON -> null
        }
        val greatsword = weapon && identity.base.family == "greatsword"
        val icon = when {
            part != null -> "minecraft:leather_$part|projects:armor/${job}_t${tier}_$part"
            greatsword -> null
            else -> "minecraft:iron_sword|${CoreArmamentPresentation.model(identity.base, a.journey.job, tier)}"
        }
        val mods = a.equippedAffixes.filter { it.gear == slot }.sortedBy { it.index }.map { affix ->
            ForgeV3Mod(CoreAffixCatalog.definition(affix.stone)?.group?.displayName ?: "—", CoreAffixCatalog.describe(affix.stone))
        }
        return ForgeV3Gear(
            id = slot.name.lowercase(),
            name = if (weapon) identity.base.displayName else "開拓者の${slot.displayName}",
            slotLabel = slot.displayName,
            tier = tier,
            rarity = CoreAffixCatalog.rarity(a, slot).displayName,
            level = level,
            failures = state.failures,
            pityThreshold = if (level >= 30) 0 else CoreEnhancementCatalog.pityThreshold(level + 1),
            broken = CoreEconomy.broken(a, slot),
            statLabel = if (weapon) "物理攻撃" else "最大HP",
            power = if (weapon) CoreWeaponPresentation.damage(a) else CoreWeaponPresentation.health(a),
            nextPower = if (weapon) CoreWeaponPresentation.damage(next) else CoreWeaponPresentation.health(next),
            speedPercent = if (weapon) CoreEnhancementCatalog.weaponAttackSpeedPercent(level) else null,
            mods = mods,
            modCapacity = CoreAffixCatalog.capacity(a, slot),
            iconItem = icon,
            heroSprite = if (greatsword) "sword_t2_hero" else null,
            thumbSprite = if (greatsword) "sword_t2_thumb" else null,
        )
    }

    private fun project(a: CoreAccount): ForgeV3State {
        val q = quote(a)
        val maxed = q.currentLevel >= CoreEnhancementCatalog.MAX_LEVEL
        val costs = q.recipe.costs.map { (material, required) ->
            ForgeV3Cost(material.displayName, a.amount(material), required, when (material.resource) {
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
                else -> "forge_material_affix_dust"
            })
        }
        val shortage = costs.firstOrNull { it.owned < it.required }?.let { "${it.name}があと${it.required - it.owned}個" }
        val broken = CoreEconomy.broken(a, selected)
        return ForgeV3State(
            gears = CoreGearSlot.equipSlots.map { gear(a, it) },
            selected = selected.name.lowercase(),
            silver = a.silver,
            dust = a.amount(CoreResource.AFFIX_DUST),
            masteryRank = CoreEnhancementCatalog.masteryRank(a.smithingXp),
            masteryProgress = CoreEnhancementCatalog.masteryProgress(a.smithingXp),
            baseChance = q.baseChancePercent,
            masteryBonus = q.masteryBonusPercent,
            catalystBonus = q.catalystBonusPercent,
            chance = q.successChancePercent,
            pityGuaranteed = !maxed && q.failures >= q.pityThreshold,
            breakOnFailure = q.breakOnFailurePercent,
            costTier = if (maxed) 0 else CoreEnhancementCatalog.materialTier(q.targetLevel),
            costs = costs,
            focused = focused,
            focusAvailable = !maxed && !broken && !CoreEnhancementCatalog.quote(a, selected).guaranteed,
            blockedReason = when {
                broken -> "破損中は強化できません"
                q.blockedReason != null -> q.blockedReason
                shortage != null -> "素材が足りません"
                else -> null
            },
            history = history,
            note = shortage?.let { "$it 足りません" } ?: note,
            modal = modal,
            muted = muted,
            busy = operationActive,
        )
    }

    private fun begin(a: CoreAccount, revision: Long) {
        val target = selected
        val selectedMode = mode()
        val before = CoreEnhancementCatalog.state(a, target).level
        modal = false; quotedRevision = null
        operationActive = true
        note = "鍛造中…"
        val now = clock()
        charging = project(a)
        chargeEndsAt = now + ForgeV3MotionKind.CHARGE.durationMs
        motion = ForgeV3Motion(ForgeV3MotionKind.CHARGE, now, before)
        banner = null
        outcome = null
        enhance(target, selectedMode, revision) { committed ->
            val after = account()
            outcome = Outcome(committed, target,
                before, after?.let { CoreEnhancementCatalog.state(it, target).level } ?: before,
                after?.let { CoreEconomy.broken(it, target) } ?: false)
        }
    }

    override fun action(action: String): Boolean {
        if (operationActive) return false
        if (action == "sound") { muted = !muted; return true }
        if (modal) return when (action) {
            "cancel" -> { modal = false; quotedRevision = null; true }
            "confirm" -> {
                val a = account() ?: return false
                val revision = quotedRevision ?: return false
                if (a.revision != revision || quote(a).blockedReason != null || !allowed()) {
                    modal = false; quotedRevision = null; note = "状態が変わりました。費用を確認し直してください。"; true
                } else { begin(a, revision); true }
            }
            else -> false
        }
        val a = account() ?: return false
        return when (action) {
            "select:weapon", "select:head", "select:chest", "select:legs", "select:feet" -> {
                selected = CoreGearSlot.valueOf(action.substringAfter(':').uppercase())
                focused = false; note = "装備を選択しました。"; banner = null; true
            }
            "catalyst" -> {
                if (!focused && (CoreEnhancementCatalog.quote(a, selected).guaranteed || CoreEnhancementCatalog.state(a, selected).level >= 30)) false
                else { focused = !focused; note = if (focused) "集中鍛造：成功率が15上がります。" else "通常の強化に戻しました。"; true }
            }
            "enhance" -> {
                val state = project(a)
                if (!allowed()) false
                else if (state.blockedReason != null) { note = state.blockedReason ?: note; true }
                else if (state.risky) { modal = true; quotedRevision = a.revision; true }
                else { begin(a, a.revision); true }
            }
            else -> false
        }
    }

    override fun tick(nowMs: Long): ForgeUiReceipt? {
        val result = outcome
        if (operationActive && result != null && clock() >= chargeEndsAt) {
            val now = clock()
            operationActive = false
            charging = null
            outcome = null
            val after = result.after
            if (!result.committed) {
                motion = null
                note = "保存できませんでした。状態を確認してやり直してください。"
                return null
            }
            val success = after > result.before
            motion = ForgeV3Motion(when {
                success -> ForgeV3MotionKind.HIT
                result.broken -> ForgeV3MotionKind.BREAK
                else -> ForgeV3MotionKind.SHAKE
            }, now, after)
            banner = when {
                success -> ForgeV3Banner("成功！ +$after", "${ForgeV3Scene.heatName(after)}の輝きを帯びた", ForgeV3Scene.heat(after), now + 2600)
                result.broken -> ForgeV3Banner("破損…", "強化値 +${result.before} とMODは残っています", "#E58B80", now + 2600)
                else -> ForgeV3Banner("失敗", "強化値 +${result.before} は維持", "#C9C3B8", now + 2600)
            }
            history = when {
                success -> "${result.slot.displayName}  +${result.before} → +$after  成功"
                result.broken -> "${result.slot.displayName}  +${result.before}  失敗・破損"
                else -> "${result.slot.displayName}  +${result.before}  失敗（強化値は維持）"
            }
            note = if (success) "強化成功。次の強化を表示しています。" else if (result.broken) "破損しました。装備庫で修理できます。" else "強化失敗。素材を消費し、段階を維持しました。"
            if (focused && account()?.let { CoreEnhancementCatalog.quote(it, selected).guaranteed } == true) focused = false
            receipt = ForgeUiReceipt(success)
        }
        return receipt.also { receipt = null }
    }
}
