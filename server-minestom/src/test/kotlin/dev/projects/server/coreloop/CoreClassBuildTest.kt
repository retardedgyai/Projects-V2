package dev.projects.server.coreloop

import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import kotlin.test.*

class CoreClassBuildTest {
    @Test fun `all seven professions have eight choices two ultimates and unique authored identities`() {
        assertEquals(7,CoreClass.entries.size)
        val icons=CoreClass.entries.flatMap { job ->
            val skills=CoreSkillCatalog.skills(job)
            assertEquals(10,skills.size)
            assertTrue(skills.take(8).none { it.ultimate });assertTrue(skills.drop(8).all { it.ultimate })
            assertTrue(skills.any { it.gain>0 });assertTrue(skills.any { it.spend>0 })
            assertTrue(skills.any { it.motion in setOf(CoreSkillMotion.GUARD,CoreSkillMotion.EVADE) })
            skills.forEach { s ->
                assertTrue(s.cooldown>0 && s.mana>=0 && s.startup>0 && s.pulses in 1..8)
                assertEquals(0.0,if(job.magic) s.formula.ad else s.formula.ap)
            }
            skills.map { it.icon }
        }
        assertEquals(70,icons.distinct().size);assertEquals(icons,CoreSkillCatalog.artNames)
        val font=javaClass.classLoader.getResourceAsStream("core-ui-pack/assets/projects/font/core_hud.json")!!.bufferedReader().readText()
        icons.forEach { assertContains(font,"skill_${it}_states.png") }
    }
    @Test fun `swapping an equipped skill cannot duplicate it and ultimate never enters a normal slot`() {
        assertEquals(listOf(1,0,2,3),CoreClassBuild().equip(0,1).skills)
        assertEquals(listOf(7,1,2,3),CoreClassBuild().equip(0,7).skills)
        assertEquals(1,CoreClassBuild().equip(4,1).ultimate)
        assertFailsWith<IllegalArgumentException> { CoreClassBuild().equip(0,8) }
        assertFailsWith<IllegalArgumentException> { CoreClassBuild().equip(4,2) }
        assertFailsWith<IllegalArgumentException> { CoreClassBuild(first=1) }
    }
    @Test fun `tree requires parents excludes simultaneous keystones and refunds descendants`() {
        val first=CoreClassBuild().toggle(0,6).toggle(1,6).toggle(10,6).toggle(2,6)
        assertEquals(0,first.keystone)
        val mixed=first.toggle(3,6).toggle(4,6)
        assertFailsWith<IllegalArgumentException> { mixed.toggle(5,6) }
        assertEquals(1,first.toggle(1,6).nodes)
        assertEquals(0,first.toggle(0,6).nodes)
        assertFailsWith<IllegalArgumentException> { CoreClassBuild().toggle(2,6) }
        assertFailsWith<IllegalArgumentException> { mixed.toggle(6,5) }
    }
    @Test fun `every class has three distinct branching mechanics and a bounded point budget`() {
        for(job in CoreClass.entries) {
            assertEquals(18,CoreClassTrees.nodes(job).size)
            assertEquals(3,CoreClassTrees.nodes(job).take(9).filterIndexed { i,_ -> i%3==2 }.map { it.description }.distinct().size)
        }
        assertEquals(2,CoreClassTrees.budget(CoreJourney.fresh()))
        assertEquals(4,CoreClassTrees.budget(CoreJourney.fresh().copy(xp=CoreJourneyRules.threshold(8))))
        assertEquals(12,CoreClassTrees.budget(CoreJourney.fresh().copy(xp=CoreJourneyRules.threshold(40))))
        assertFailsWith<IllegalArgumentException> { CoreJourney.fresh().copy(build=CoreClassBuild(nodes=7)) }
    }
    @Test fun `resource cost reduction is applied once and star resources never become fractional`() {
        val j=CoreJourney(build=CoreClassBuild(second=5,nodes=3))
        val s=CoreSkillCatalog.equipped(j)[1]
        assertEquals(26,s.spend)
        val state=CoreClassState();state.gain(26.0,j.job,j.build)
        state.spend(s,j.job,j.build);assertEquals(0.0,state.resource)
        val star=CoreJourney(job=CoreClass.STARWEAVER,build=CoreClassBuild(nodes=8))
        state.normalHit(star.job,UUID.randomUUID(),1,star.build);assertEquals(1.0,state.resource)
    }
    @Test fun `resource gains are per normal impact not per cleaved target and capped`() {
        val s=CoreClassState();val j=CoreJourney()
        repeat(20) { s.normalHit(j.job,UUID.randomUUID(),1,j.build) }
        assertEquals(12.0,s.resource)
        repeat(20) { s.normalHit(j.job,UUID.randomUUID(),it+2L,j.build) }
        assertEquals(100.0,s.resource)
        s.reset();assertEquals(0.0,s.resource)
    }
    @Test fun `mage rewards alternating elements rather than repeating the same generator`() {
        val s=CoreClassState();val j=CoreJourney(job=CoreClass.MAGE)
        s.skillHit(j.job,CoreSkillCatalog.skills(j.job)[0],j.build);assertEquals(15.0,s.resource)
        s.skillHit(j.job,CoreSkillCatalog.skills(j.job)[0],j.build);assertEquals(30.0,s.resource)
        s.skillHit(j.job,CoreSkillCatalog.skills(j.job)[1],j.build);assertEquals(60.0,s.resource)
    }
    @Test fun `ranger focus rewards one quarry and loses half its saved resource on target change`() {
        val s=CoreClassState();val id=UUID.randomUUID();val j=CoreJourney(job=CoreClass.RANGER)
        s.normalHit(j.job,id,1,j.build);s.normalHit(j.job,id,2,j.build)
        assertEquals(23.0,s.resource);assertEquals(2,s.focusHits)
        s.normalHit(j.job,UUID.randomUUID(),3,j.build)
        assertEquals(22.5,s.resource);assertEquals(1,s.focusHits)
    }
    @Test fun `marks are personal finite and consume only once`() {
        val id=UUID.randomUUID();val a=CoreClassState();val b=CoreClassState()
        a.mark(id,10);assertFalse(b.marked(id,10));assertTrue(a.consumeMark(id,10));assertFalse(a.consumeMark(id,10))
        a.mark(id,10);a.tick(131);assertFalse(a.marked(id,131))
    }
    @Test fun `star shield and attack releases both consume all three stars but movement does not`() {
        for(index in listOf(0,1,2,6,7,8,9)) {
            val state=CoreClassState();val j=CoreJourney(job=CoreClass.STARWEAVER)
            state.gain(3.0,j.job,j.build)
            assertEquals(3.0,state.spend(CoreSkillCatalog.skills(j.job)[index],j.job,j.build))
            assertEquals(0.0,state.resource,"skill=$index")
        }
        val state=CoreClassState();val j=CoreJourney(job=CoreClass.STARWEAVER)
        state.gain(3.0,j.job,j.build);state.spend(CoreSkillCatalog.skills(j.job)[3],j.job,j.build);assertEquals(3.0,state.resource)
    }
    @Test fun `guard resource cannot multiply from simultaneous hits`() {
        val s=CoreClassState();s.perfectUntil=12
        repeat(20) { s.guarded(CoreClass.WARRIOR,1,CoreClassBuild()) }
        assertEquals(30.0,s.resource);assertEquals(61,s.counterUntil)
        s.guarded(CoreClass.WARRIOR,20,CoreClassBuild());assertEquals(45.0,s.resource)
    }
    @Test fun `echo and returning modifiers change pulses and their displayed coefficients`() {
        for((job,stat,index) in listOf(Triple(CoreClass.MAGE,CoreAffixStat.MAGE_ECHO,2),Triple(CoreClass.RANGER,CoreAffixStat.HUNT_RETURN,0))) {
            val j=CoreJourney(job=job)
            val original=CoreSkillCatalog.skills(job)[index]
            val modified=CoreSkillCatalog.modify(original,j,CoreAffixStats(additional=mapOf(stat to 4.0)))
            assertEquals(original.pulses+1,modified.pulses)
            assertTrue(modified.formula.base<original.formula.base)
            assertContains(modified.description,"MOD")
        }
    }
    @Test fun `shield tooltip uses same tree and modifier multiplier and heal conversion reports split`() {
        val j=CoreJourney(job=CoreClass.HEALER,build=CoreClassBuild(nodes=192))
        val sheet=CoreCombatSheet.from(CoreCombatGear(base=CoreWeaponBase.TOME),CoreAffixStats(additional=mapOf(CoreAffixStat.SHIELD_POWER to 20.0))).specialize(j)
        val shield=CoreSkillCatalog.skills(j.job)[5]
        assertEquals((shield.formula.evaluate(sheet)+sheet.healingPower)*1.44,shield.preview(sheet),.000001)
        val conversion=sheet.copy(mods=sheet.mods.copy(additional=mapOf(CoreAffixStat.HEAL_CONVERSION to 5.0)))
        assertEquals(.4,CoreSkillCatalog.healConversion(j,conversion.mods))
        assertContains(CoreSkillCatalog.skills(j.job)[1].tooltip(conversion,j).joinToString("\n"),"/ 障壁")
    }
    @Test fun `profession changes retain independent skill and talent configurations through disk codec`() {
        val warrior=CoreClassBuild(first=4,second=5,third=6,nodes=1031,ultimate=1)
        var j=CoreJourney(build=warrior).changeClass(CoreClass.MAGE)
        val mage=CoreClassBuild(first=4,third=6,nodes=8248)
        j=j.copy(build=mage).changeClass(CoreClass.WARRIOR)
        assertEquals(warrior,j.build)
        val a=CoreAccount(UUID.randomUUID(),journey=j)
        val loaded=CoreAccountCodec.decode(CoreAccountCodec.encode(a),a.playerId)
        assertEquals(CoreAccountCodec.encode(a),CoreAccountCodec.encode(loaded));assertEquals(j,loaded.journey)
        assertEquals(mage,loaded.journey.changeClass(CoreClass.MAGE).build)
    }
    @Test fun `v8 load is readonly and first committed class edit preserves exact v8 bytes`() {
        val dir=Files.createTempDirectory("class-v8-");val id=UUID.randomUUID()
        val original=CoreAccount(id,silver=12345,weaponEnhancement=CoreEnhancementState(23,2))
        val old=checksum(CoreAccountCodec.encode(original).substringBefore("checksum\t").lineSequence()
            .filterNot { it.startsWith("class-build\t") || it.startsWith("class-loadout\t") }.joinToString("\n").replaceFirst("\t10\t","\t8\t"))
        val file=dir.resolve("$id.account");Files.writeString(file,old)
        val service=CoreAccountService(CoreAccountRepository(dir));assertIs<CoreAccountLoadResult.Ready>(service.open(id))
        assertEquals(old,Files.readString(file));assertFalse(Files.exists(dir.resolve("$id.account.v8.bak")))
        val result=service.transact(id,CoreOperation(UUID.randomUUID(),0,CoreAction.SelectSkill(0,4)))
        assertEquals(CoreTransactionStatus.COMMITTED,result.status,result.message)
        assertEquals(old,Files.readString(dir.resolve("$id.account.v8.bak")))
        assertEquals(12345,result.account!!.silver);assertEquals(original.weaponEnhancement,result.account.weaponEnhancement)
        assertEquals(4,result.account.journey.build.first)
    }
    @Test fun `v10 rejects missing duplicate disconnected and overbudget builds even with a valid checksum`() {
        val a=CoreAccount(UUID.randomUUID(),journey=CoreJourney.fresh());val body=CoreAccountCodec.encode(a).substringBefore("checksum\t")
        val row="class-build\t0\t1\t2\t3\t0\t0\n"
        for(bad in listOf("",row+row,"class-build\t0\t0\t2\t3\t0\t0\n","class-build\t0\t1\t2\t3\t0\t4\n","class-build\t0\t1\t2\t3\t0\t7\n"))
            assertFailsWith<IllegalArgumentException> { CoreAccountCodec.decode(checksum(body.replace(row,bad)),a.playerId) }
    }
    @Test fun `split paths rejoin and refund preserves an independently connected keystone`() {
        var build=CoreClassBuild()
        for(i in listOf(0,1,10,9,11,2)) build=build.toggle(i,12)
        val leftRefund=build.toggle(1,12)
        assertFalse(leftRefund.has(10));assertTrue(leftRefund.has(2));assertTrue(leftRefund.has(11))
        assertEquals(0,leftRefund.toggle(0,12).nodes)
        for(i in 0..17) {
            assertTrue(CoreClassTrees.slot(i) in 9..44)
            assertTrue(CoreClassTrees.parents(i).all { it in 0..17 && it!=i })
        }
        assertEquals(18,(0..17).map(CoreClassTrees::slot).distinct().size)
    }
    @Test fun `new skill branches change the same catalog used by casts and tooltips`() {
        for(job in CoreClass.entries) {
            val raw=CoreSkillCatalog.skills(job)[CoreClassTrees.signature(job)]
            val j=CoreJourney(job=job,build=CoreClassBuild(nodes=(1 shl 0) or (1 shl 1) or (1 shl 10)))
            val skill=CoreSkillCatalog.modify(raw,j,CoreAffixStats())
            assertEquals(raw.pulses+1,skill.pulses)
            assertEquals(raw.formula.base*.75,skill.formula.base,.00001)
            assertContains(skill.description,"追加発動")
            assertContains(skill.tooltip(CoreCombatSheet.from(CoreAccount(UUID.randomUUID(),journey=j)),j).joinToString(),"回")
        }
    }
    @Test fun `v9 tree is refunded with skills gear and exact backup preserved`() {
        val dir=Files.createTempDirectory("class-v9-");val id=UUID.randomUUID()
        val a=CoreAccount(id,silver=54321,journey=CoreJourney(build=CoreClassBuild(first=4)))
        val old=checksum(CoreAccountCodec.encode(a).substringBefore("checksum\t").replaceFirst("\t10\t","\t9\t")
            .replace("class-build\t4\t1\t2\t3\t0\t0\n","class-build\t4\t1\t2\t3\t0\t7\n"))
        val path=dir.resolve("$id.account");Files.writeString(path,old)
        val service=CoreAccountService(CoreAccountRepository(dir));val loaded=assertIs<CoreAccountLoadResult.Ready>(service.open(id))
        assertEquals(old,Files.readString(path))
        val result=service.transact(id,CoreOperation(UUID.randomUUID(),0,CoreAction.ToggleTalent(0)))
        assertEquals(CoreTransactionStatus.COMMITTED,result.status,result.message)
        assertEquals(1,result.account!!.journey.build.nodes);assertEquals(4,result.account.journey.build.first)
        assertEquals(54321,result.account.silver)
        assertEquals(old,Files.readString(dir.resolve("$id.account.v9.bak")))
    }
    @Test fun `UI budget journey validation and v10 saved loadouts agree above six points`() {
        var b=CoreClassBuild()
        for(i in listOf(0,1,10,2,3,4,13,6,7,16,15,17)) b=b.toggle(i,12)
        val j=CoreJourney(build=b).changeClass(CoreClass.MAGE)
        val a=CoreAccount(UUID.randomUUID(),journey=j)
        val decoded=CoreAccountCodec.decode(CoreAccountCodec.encode(a),a.playerId)
        assertEquals(j,decoded.journey)
        assertEquals(CoreAccountCodec.encode(a),CoreAccountCodec.encode(decoded))
        assertEquals(12,a.journey.savedBuilds.getValue(CoreClass.WARRIOR).points)
        val lvl4=CoreJourney.fresh().copy(xp=CoreJourneyRules.threshold(4),build=CoreClassBuild(nodes=11))
        assertEquals(3,CoreClassTrees.budget(lvl4))
    }
    private fun checksum(body:String)=body+"checksum\t"+MessageDigest.getInstance("SHA-256").digest(body.toByteArray()).joinToString(""){"%02x".format(it)}+"\n"
}
