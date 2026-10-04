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

internal object WorldFoundryCodec {
    private fun hash(v:String)=MessageDigest.getInstance("SHA-256").digest(v.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun encode(s:WorldFoundryState):String {
        val body=buildString {
            append("PROJECTS_WORLD_FOUNDRY\t1\t${s.account.playerId}\n")
            append("account\t${Base64.getEncoder().encodeToString(CoreAccountCodec.encode(s.account).toByteArray(Charsets.UTF_8))}\n")
            append("stock\t${s.coal}\t${s.sand}\t${s.fuel}\t${s.input}\t${s.meltSeconds}\t${s.liquid}\n")
            append("table\t${s.phase}\t${s.shape ?: "-"}\t${s.castUnits}\t${s.seconds}\n")
            s.equipment.toSortedMap().forEach { (k,c)->append("equipment\t$k\t${c.x}\t${c.y}\t${c.z}\n") }
            s.parts.toSortedMap().forEach { (k,n)->append("part\t$k\t$n\n") }
        }
        return body+"sha256\t${hash(body)}\n"
    }
    fun decode(text:String,owner:UUID):WorldFoundryState {
        require(text.toByteArray(Charsets.UTF_8).size<=1_048_576 && text.endsWith('\n'))
        val lines=text.dropLast(1).split('\n');val sum=lines.last().split('\t')
        val body=lines.dropLast(1).joinToString("\n",postfix="\n")
        require(sum.size==2 && sum[0]=="sha256" && sum[1]==hash(body)) { "鋳造保存の整合性を確認できません。元ファイルは保持しています" }
        require(lines.first()=="PROJECTS_WORLD_FOUNDRY\t1\t$owner") { "所有者または試作保存形式が違います" }
        val rows=lines.drop(1).dropLast(1).map { it.split('\t') }
        require(rows.all { it[0] in setOf("account","stock","table","equipment","part") })
        val a=rows.single { it[0]=="account" };require(a.size==2)
        val account=CoreAccountCodec.decode(String(Base64.getDecoder().decode(a[1]),Charsets.UTF_8),owner)
        val stock=rows.single { it[0]=="stock" };require(stock.size==7)
        val table=rows.single { it[0]=="table" };require(table.size==5)
        val equipment=rows.filter { it[0]=="equipment" };val parts=rows.filter { it[0]=="part" }
        require(equipment.all { it.size==5 } && equipment.map { it[1] }.distinct().size==equipment.size)
        require(parts.all { it.size==3 } && parts.map { it[1] }.distinct().size==parts.size)
        return WorldFoundryState(account,
            equipment.associate { FoundryEquipment.valueOf(it[1]) to FoundryCell(it[2].toInt(),it[3].toInt(),it[4].toInt()) },
            stock[1].toInt(),stock[2].toInt(),stock[3].toInt(),stock[4].toInt(),stock[5].toInt(),stock[6].toInt(),
            FoundryPhase.valueOf(table[1]),table[2].takeIf { it!="-" }?.let(FoundryShape::valueOf),table[3].toInt(),table[4].toInt(),
            parts.associate { FoundryShape.valueOf(it[1]) to it[2].toInt() })
    }
}

/** Atomic account + machine + output envelope. Never falls back to truncating the old save. */
internal class WorldFoundryRepository(private val directory:Path,
    private val replace:(Path,Path)->Unit={a,b->Files.move(a,b,ATOMIC_MOVE,REPLACE_EXISTING);Unit}) {
    private fun file(owner:UUID)=directory.resolve("$owner.foundry")
    @Synchronized fun load(owner:UUID):WorldFoundryState? {
        val f=file(owner);if(!Files.exists(f,NOFOLLOW_LINKS))return null
        require(Files.isRegularFile(f,NOFOLLOW_LINKS) && Files.size(f)<=1_048_576)
        return WorldFoundryCodec.decode(Files.readString(f,Charsets.UTF_8),owner)
    }
    @Synchronized fun save(expected:Long,s:WorldFoundryState):WorldFoundryState {
        require(s.account.revision==expected && expected<Long.MAX_VALUE)
        Files.createDirectories(directory);require(Files.isDirectory(directory,NOFOLLOW_LINKS))
        val lock=directory.resolve("${s.account.playerId}.lock")
        require(!Files.exists(lock,NOFOLLOW_LINKS)||Files.isRegularFile(lock,NOFOLLOW_LINKS))
        FileChannel.open(lock,CREATE,WRITE).use { channel->channel.lock().use {
            require((load(s.account.playerId)?.account?.revision ?: 0L)==expected) { "保存が先に更新されました。再接続してください" }
            val target=file(s.account.playerId);require(!Files.exists(target,NOFOLLOW_LINKS)||Files.isRegularFile(target,NOFOLLOW_LINKS))
            val next=s.copy(account=s.account.copy(revision=expected+1))
            val bytes=WorldFoundryCodec.encode(next).toByteArray(Charsets.UTF_8)
            val temp=Files.createTempFile(directory,".foundry-",".tmp")
            try {
                FileChannel.open(temp,WRITE,TRUNCATE_EXISTING).use { out->
                    val buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining())out.write(buffer);out.force(true)
                }
                try { replace(temp,target) } catch(failure:Exception) {
                    if(!runCatching { Files.readAllBytes(target).contentEquals(bytes) }.getOrDefault(false))throw failure
                }
                return next
            } finally { Files.deleteIfExists(temp) }
        } }
    }
}
