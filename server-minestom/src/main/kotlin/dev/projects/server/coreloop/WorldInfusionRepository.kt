package dev.projects.server.coreloop

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*
import java.nio.file.StandardOpenOption.*
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

/** A single atomic envelope owns account resources, held items, placed Jars and ritual progress. */
internal object WorldInfusionCodec {
    fun encode(s: WorldInfusionState): String {
        val body=buildString {
            append("PROJECTS_WORLD_INFUSION\t${if(s.energy==null)1 else 2}\t${s.account.playerId}\n")
            append("account\t${Base64.getEncoder().encodeToString(CoreAccountCodec.encode(s.account).toByteArray(Charsets.UTF_8))}\n")
            append("ritual\t${s.gearPlace}\t${s.phase}\t${s.paused}\n")
            s.matrix?.let { append("matrix\t${cell(it)}\n") }
            s.pedestals.forEach { append("pedestal\t${cell(it.cell)}\t${it.item ?: "-"}\n") }
            s.jars.forEach { append("jar\t${it.id}\t${it.aspect}\t${it.amount}\t${it.capacity}\t${it.cell?.let(::cell) ?: "-"}\n") }
            s.supplied.toSortedMap().forEach { (a,n)->append("supplied\t$a\t$n\n") }
            s.reservoir.toSortedMap().forEach { (a,n)->append("reservoir\t$a\t$n\n") }
            s.consumed.toSortedMap().forEach { (a,n)->append("consumed\t$a\t$n\n") }
            s.energy?.let { append("energy\t${it.recipeId}\t${it.requiredMilli}\t${it.receivedMilli}\n") }
        }
        return body+"sha256\t${hash(body)}\n"
    }
    private fun cell(c: InfusionCell)="${c.x},${c.y},${c.z}"
    private fun cell(v: String): InfusionCell { val f=v.split(',');require(f.size==3);return InfusionCell(f[0].toInt(),f[1].toInt(),f[2].toInt()) }
    private fun hash(v:String)=MessageDigest.getInstance("SHA-256").digest(v.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    fun decode(text:String,owner:UUID):WorldInfusionState {
        require(text.toByteArray(Charsets.UTF_8).size <= 1_048_576 && text.endsWith('\n'))
        val lines=text.dropLast(1).split('\n');val checksum=lines.last().split('\t')
        val body=lines.dropLast(1).joinToString("\n",postfix="\n")
        require(checksum.size==2 && checksum[0]=="sha256" && checksum[1]==hash(body)) { "祭壇保存データの整合性を確認できません" }
        val version=when(lines[0]) { "PROJECTS_WORLD_INFUSION\t1\t$owner"->1;"PROJECTS_WORLD_INFUSION\t2\t$owner"->2;else->error("Unsupported isolated infusion envelope") }
        val rows=lines.drop(1).dropLast(1).map { it.split('\t') }
        require(rows.all { it[0] in setOf("account","ritual","matrix","pedestal","jar","supplied","reservoir","consumed","energy") })
        val energyRows=rows.filter { it[0]=="energy" }
        require(energyRows.size==if(version==2)1 else 0)
        val energy=energyRows.singleOrNull()?.let { require(it.size==4);InfusionEnergyLedger(it[1],it[2].toLong(),it[3].toLong()) }
        val a=rows.single { it[0]=="account" };require(a.size==2)
        val account=CoreAccountCodec.decode(String(Base64.getDecoder().decode(a[1]),Charsets.UTF_8),owner)
        val r=rows.single { it[0]=="ritual" };require(r.size==4)
        val m=rows.filter { it[0]=="matrix" };require(m.size<=1 && m.all { it.size==2 })
        fun aspects(key:String):Map<InfusionAspect,Int> {
            val selected=rows.filter { it[0]==key };require(selected.all { it.size==3 } && selected.map { it[1] }.distinct().size==selected.size)
            return selected.associate { InfusionAspect.valueOf(it[1]) to it[2].toInt() }
        }
        val consumed=rows.filter { it[0]=="consumed" };require(consumed.all { it.size==3 } && consumed.map { it[1] }.distinct().size==consumed.size)
        return WorldInfusionState(account,m.singleOrNull()?.let { cell(it[1]) },
            rows.filter { it[0]=="pedestal" }.map { require(it.size==3);InfusionPedestal(cell(it[1]),if(it[2]=="-")null else CoreResource.valueOf(it[2])) },
            rows.filter { it[0]=="jar" }.map { require(it.size==6);InfusionJar(UUID.fromString(it[1]),InfusionAspect.valueOf(it[2]),it[3].toInt(),it[4].toInt(),if(it[5]=="-")null else cell(it[5])) },
            InfusionGearPlace.valueOf(r[1]),InfusionPhase.valueOf(r[2]),aspects("supplied"),aspects("reservoir"),
            consumed.associate { CoreResource.valueOf(it[1]) to it[2].toInt() },r[3].toBooleanStrict(),energy)
    }
}

internal class WorldInfusionRepository(private val directory: Path,
    private val replace: (Path,Path)->Unit = { a,b->Files.move(a,b,ATOMIC_MOVE,REPLACE_EXISTING);Unit }) {
    private fun file(owner:UUID)=directory.resolve("$owner.infusion")
    @Synchronized fun load(owner:UUID):WorldInfusionState? {
        val f=file(owner);if(!Files.exists(f,NOFOLLOW_LINKS))return null
        require(Files.isRegularFile(f,NOFOLLOW_LINKS) && Files.size(f)<=1_048_576)
        return WorldInfusionCodec.decode(Files.readString(f,Charsets.UTF_8),owner)
    }
    @Synchronized fun save(expected:Long,s:WorldInfusionState):WorldInfusionState {
        require(s.account.revision==expected && expected < Long.MAX_VALUE)
        Files.createDirectories(directory);require(Files.isDirectory(directory,NOFOLLOW_LINKS))
        val lock=directory.resolve("${s.account.playerId}.lock");require(!Files.exists(lock,NOFOLLOW_LINKS)||Files.isRegularFile(lock,NOFOLLOW_LINKS))
        FileChannel.open(lock,CREATE,WRITE).use { channel->channel.lock().use {
            require((load(s.account.playerId)?.account?.revision ?: 0L)==expected) { "保存競合：再接続してください" }
            val next=s.copy(account=s.account.copy(revision=expected+1))
            val bytes=WorldInfusionCodec.encode(next).toByteArray(Charsets.UTF_8)
            val temp=Files.createTempFile(directory,".infusion-",".tmp")
            try {
                FileChannel.open(temp,WRITE,TRUNCATE_EXISTING).use { out->val buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining())out.write(buffer);out.force(true) }
                try { replace(temp,file(s.account.playerId)) } catch(failure:Exception) {
                    if(!runCatching { Files.readAllBytes(file(s.account.playerId)).contentEquals(bytes) }.getOrDefault(false))throw failure
                }
                return next
            } finally { Files.deleteIfExists(temp) }
        } }
    }
}
