package dev.projects.webui.polish05
import dev.projects.webui.polish05.Polish05PreviewModel.*
import kotlin.math.abs

fun main() {
    var checks=0
    fun ok(value: Boolean, label: String) { check(value) { label }; checks++ }
    val m=Polish05PreviewModel()
    ok(m.snapshot().gears[0].power==148,"initial power")
    ok(m.possibleEnhancements()==2,"initial remaining enhancements")
    ok(m.replenishment(Recipe.ORE)==Replenish(0,11,1),"ore replenishment")
    ok(m.replenishment(Recipe.CRYSTAL)==Replenish(0,8,1),"crystal replenishment")
    val q=m.quoteEnhancement()!!
    ok(m.snapshot().silver==12480L,"quote does not spend")
    m.cancelQuote();ok(m.begin(q.id,0)==null,"cancel invalidates")
    val stale=m.quoteEnhancement()!!;m.select("ash")
    ok(m.begin(stale.id,0)==null,"changing equipment invalidates quote")
    ok(m.enhancementCost().silver==800L,"ash price")
    m.select("ember");val live=m.quoteEnhancement()!!;val op=m.begin(live.id,1000)!!
    ok(m.snapshot().silver==11280L,"one debit")
    ok(m.snapshot().materials.getValue(Material.ORE)==36,"ore debit")
    ok(m.begin(live.id,1000)==null,"double click rejected")
    ok(!m.select("ash"),"selection locked during operation")
    ok(m.finish(op.id,1719)==null,"early finish rejected")
    val result=m.finish(op.id,1720)!!
    ok(result.success && result.afterPower==157 && result.afterLevel==7,"success state")
    ok(m.finish(op.id,1721)==null,"double completion rejected")
    val fail=Polish05PreviewModel(ResultMode.FAIL_EXAMPLE)
    val fo=fail.begin(fail.quoteEnhancement()!!.id,0)!!
    val fr=fail.finish(fo.id,720)!!
    ok(!fr.success && fr.beforeLevel==fr.afterLevel,"failure holds level")
    ok(fail.snapshot().silver==11280L,"failure consumes fee")
    val cat=Polish05PreviewModel(ResultMode.FAIL_EXAMPLE);cat.useCatalyst(true)
    val co=cat.begin(cat.quoteEnhancement()!!.id,0)!!
    ok(cat.finish(co.id,720)!!.success,"catalyst guarantees even fail example")
    ok(cat.snapshot().materials.getValue(Material.CATALYST)==0,"catalyst consumed once")
    val ref=Polish05PreviewModel()
    ok(ref.quoteRefinement(Recipe.ORE,0)==null && ref.quoteRefinement(Recipe.ORE,100)==null,"quantity bounded")
    ok(ref.quoteRefinement(Recipe.ORE,12)==null,"ore source bounded")
    val ro=ref.begin(ref.quoteRefinement(Recipe.ORE,3)!!.id,0)!!
    ok(ref.finish(ro.id,900)!!.count==3,"refine result count")
    ok(ref.snapshot().materials.getValue(Material.ORE)==63 && ref.snapshot().materials.getValue(Material.RAW_ORE)==32,"refine linked balances")
    ok(ref.snapshot().silver==12240L,"refine fee")
    val s=Polish05ScreenSpace(800.0,480.0)
    val pt=s.forward(1020.0,700.0);val back=s.inverse(pt.x,pt.y)!!
    ok(abs(back.x-1020)<1e-9 && abs(back.y-700)<1e-9,"screen forward inverse")
    ok(s.inverse(0.0,240.0)==null,"letterbox not clickable")
    ok(s.inverse(Double.NaN,0.0)==null,"NaN pointer rejected")
    ok(abs(s.rect(Polish05ScreenSpace.Rect(66.0,140.0,1308.0,697.0)).w/1308-s.scale)<1e-9,"uniform frame scale")
    println("$checks isolated model / coordinate checks passed. Native Minecraft adapter NOT tested.")
}
