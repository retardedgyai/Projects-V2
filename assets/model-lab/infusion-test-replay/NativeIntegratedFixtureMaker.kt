package dev.projects.server.coreloop

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID

/** A disposable fixture copies the user's altar layout, never the user's progress or original save. */
fun main(args:Array<String>) {
    val source=Path.of(args[0]);val target=Path.of(args[1])
    check(source.toAbsolutePath()!=target.toAbsolutePath())
    val files=Files.list(source).use { list->list.filter { it.fileName.toString().endsWith(".infusion") }.toList() }
    check(files.size==1)
    val file=files.single();val before=Files.readAllBytes(file)
    val owner=UUID.fromString(file.fileName.toString().removeSuffix(".infusion"))
    val saved=WorldInfusionCodec.decode(String(before,Charsets.UTF_8),owner)
    check(saved.matrix!=null && saved.pedestals.size==4 && saved.jars.size==3 && saved.jars.all { it.cell!=null })
    val fresh=WorldInfusionState.fresh(owner)
    val ingredients=WorldInfusionRules.ingredients.entries.flatMap { (r,n)->List(n){r} }
    val ready=fresh.copy(account=fresh.account.copy(balances=emptyMap()),matrix=saved.matrix,
        pedestals=saved.pedestals.mapIndexed { i,p->p.copy(item=ingredients[i]) },
        jars=saved.jars.map { it.copy(amount=it.capacity) },gearPlace=InfusionGearPlace.CENTER)
    val staged=ready
    WorldInfusionRepository(target).save(0,staged)
    check(Files.readAllBytes(file).contentEquals(before))
    println("NATIVE_ROLE_FIXTURE_PASS copiedOriginalLayout pedestals4 jars3 fixtureFreshRecipe sourceSaveUnchanged phase=${staged.phase} idleBeforeQaStart")
}
