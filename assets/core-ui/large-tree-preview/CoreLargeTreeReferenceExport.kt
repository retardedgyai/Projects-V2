package dev.projects.server.coreloop

import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path

/** Read-only snapshots from the current catalog; never receives a player or ledger. */
object CoreLargeTreeReferenceExport {
 @JvmStatic fun main(args:Array<String>) {
  val jobs=listOf(CoreClass.WARRIOR,CoreClass.TEMPLAR,CoreClass.MAGE,CoreClass.RANGER,CoreClass.ASSASSIN)
  val profiles=jobs.flatMap { job -> listOf("basic","support","mark").map { kit ->
   val candidates=CoreSkillCatalog.skills(job)
   var build=CoreClassBuild(fourth=if(job==CoreClass.WARRIOR)7 else 3)
   val index=when(kit){"support"->candidates.indexOfFirst { it.motion==CoreSkillMotion.SHIELD }.takeIf {it>=0}?:candidates.indexOfFirst { it.motion==CoreSkillMotion.GUARD };"mark"->candidates.indexOfFirst {it.status==CoreSkillStatus.MARK};else->-1}
   if(index in 0..7)build=build.equip(3,index)
   val journey=CoreJourney(job=job,xp=CoreJourneyRules.threshold(40),legacy=false,build=build)
   val equipped=CoreSkillCatalog.equipped(journey)
   mapOf("start" to (if(job==CoreClass.TEMPLAR)"tank" else job.name.lowercase()),"kit" to kit,"resource" to job.resourceName,"job" to job.name,"meleeClass" to job.melee,
     "skills" to equipped.map {s-> mapOf("name" to s.name,"icon" to s.icon,"mana" to s.mana,"spend" to s.spend,"gain" to s.gain,"motion" to s.motion.name,"status" to s.status.name,"tags" to s.tags.map{it.name},"pulses" to s.pulses,"ad" to s.formula.ad,"ap" to s.formula.ap,"radius" to s.radius,"element" to s.element)},
     "nodes" to CoreClassTrees.nodes(job).mapIndexed{i,n->mapOf("index" to i,"name" to n.name,"description" to n.description)})
  } }
  Files.writeString(Path.of(args.single()),GsonBuilder().setPrettyPrinting().create().toJson(mapOf("base" to "7341fe25","runtimeReference" to "9f3bd0fa","runtimeApplied" to false,"profiles" to profiles,"statusEnum" to CoreSkillStatus.entries.map{it.name},"siphonSource" to "CoreAffixCatalog.projects:siphon / CoreCombatMath.lifeSteal")))
  println("LARGE_TREE_CURRENT_CATALOG_EXPORT_PASS ${profiles.size} read-only fixtures, no server or player save")
 }
}
