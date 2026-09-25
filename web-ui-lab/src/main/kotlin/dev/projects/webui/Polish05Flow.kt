package dev.projects.webui

import dev.projects.webui.polish05.Polish05PreviewModel

/** One player's isolated Polish05 fixture. No gameplay account, save, currency or production odds. */
class Polish05Flow(private val presentation: Polish05Scene) {
    val model=Polish05PreviewModel()
    var view="forge"; private set
    var modal=false; private set
    var compare=false; private set
    var bagSelected="ember"; private set
    var recipe=Polish05PreviewModel.Recipe.ORE; private set
    var batch=1; private set
    var returnToForge=false; private set
    var muted=false; private set
    private var startedAtMs=0L
    private var strikePlayed=false
    var note="消費内容を確かめてから、鍛造を始めます。"; private set
    var quote: Polish05PreviewModel.Quote?=null; private set
    var operation: Polish05PreviewModel.Operation?=null; private set
    fun scene(light:ForgeLightPhase=ForgeLightPhase.IDLE)=when(view) {
        "bag" -> presentation.inventory(model,bagSelected,compare,muted)
        "refine" -> presentation.refine(model,recipe,batch,modal,note,returnToForge,muted)
        else -> presentation.forge(model,note,modal,muted,light)
    }
    fun action(action:String): Boolean {
        if(operation!=null)return false
        if(modal || compare) return when(action) {
            "cancel" -> { model.cancelQuote();quote=null;modal=false;compare=false;true }
            "bag:forge" -> if(compare) { compare=false;model.select(bagSelected);view="forge";true } else false
            "confirm" -> {
                operation=quote?.let { model.begin(it.id,System.currentTimeMillis()) }
                if(operation!=null) {startedAtMs=System.currentTimeMillis();strikePlayed=false}
                modal=false;quote=null
                note=if(operation!=null)"${if(view=="forge")"鍛造" else "精錬"}中…" else "残高が変わったため確認をやり直してください。"
                true
            }
            else -> false
        }
        return when {
        action=="sound" -> {muted=!muted;true}
        action.startsWith("select:") && view=="forge" -> model.select(action.substringAfter(':')).also { if(it)note="装備を選択しました。" }
        action.startsWith("bag:") && action !in setOf("bag:forge","bag:equip") && view=="bag" -> {
            val id=action.substringAfter(':')
            if(model.snapshot().gears.any { it.id==id }) {bagSelected=id;true} else false
        }
        action=="bag:forge" && view=="bag" -> model.select(bagSelected).also { if(it)view="forge" }
        action=="bag:equip" && view=="bag" -> model.equip(bagSelected)
        action=="compare" -> {bagSelected=model.snapshot().selected;view="bag";compare=true;true}
        action.startsWith("view:") -> {
            view=action.substringAfter(':')
            if(view=="refine") { recipe=Polish05PreviewModel.Recipe.ORE;batch=1 }
            if(view=="bag")bagSelected=model.snapshot().selected
            true
        }
        action.startsWith("refine:") && action!="refine:quote" -> {
            recipe=if(action.substringAfter(':')=="crystal")Polish05PreviewModel.Recipe.CRYSTAL else Polish05PreviewModel.Recipe.ORE
            returnToForge=view=="forge" || returnToForge
            view="refine"
            batch=model.replenishment(recipe).suggestedBatch
            note="原石と銀貨を確認してから精錬します。"
            true
        }
        action=="batch:minus" && view=="refine" -> {batch=maxOf(1,batch-1);true}
        action=="batch:plus" && view=="refine" -> {batch=minOf(model.replenishment(recipe).capacity,batch+1).coerceAtLeast(1);true}
        action=="batch:max" && view=="refine" -> {batch=model.replenishment(recipe).capacity.coerceAtLeast(1);true}
        action=="refine:quote" && view=="refine" -> {
            quote=model.quoteRefinement(recipe,batch);modal=quote!=null
            if(!modal)note="原石か銀貨が不足しています。"
            true
        }
        action=="catalyst" && view=="forge" -> model.useCatalyst(!model.snapshot().catalyst).also { if(it)note="触媒の使用を切り替えました。" }
        action=="enhance" && view=="forge" -> {
            quote=model.quoteEnhancement();modal=quote!=null
            if(!modal)note="素材か銀貨が不足しています。"
            true
        }
        else -> false
        }
    }
    fun takeStrike(nowMs:Long=System.currentTimeMillis()): Boolean {
        val op=operation ?: return false
        if(view!="refine" || strikePlayed || nowMs-startedAtMs<185 || nowMs>=op.finishAtMs)return false
        strikePlayed=true
        return true
    }
    fun tick(nowMs:Long=System.currentTimeMillis()): Polish05PreviewModel.Receipt? {
        val op=operation ?: return null
        val receipt=model.finish(op.id,nowMs) ?: return null
        operation=null
        note=when {
            receipt.kind==Polish05PreviewModel.Kind.REFINE -> "${receipt.count}個の精錬が完了しました。装備の強化に戻れます。"
            receipt.success -> "強化成功。素材と銀貨を消費し、次の強化を表示しています。"
            else -> "強化失敗。段階は維持し、素材と銀貨を消費しました。"
        }
        return receipt
    }
}
