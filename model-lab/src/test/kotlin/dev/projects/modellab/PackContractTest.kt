package dev.projects.modellab

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertSame

class PackContractTest {
    @Test fun jsonProviderLookupKeepsProviderOrderAndRestoresThreadLoader() {
        val thread = Thread.currentThread()
        val loader = thread.contextClassLoader
        val service = "META-INF/services/javax.json.spi.JsonProvider"
        val expected = java.util.Collections.list(loader.getResources(service))
        withCachedJsonProviderResources {
            repeat(3) { assertEquals(expected, java.util.Collections.list(thread.contextClassLoader.getResources(service))) }
            assertSame(loader.loadClass("javax.json.Json"), thread.contextClassLoader.loadClass("javax.json.Json"))
        }
        assertSame(loader, thread.contextClassLoader)
        assertFailsWith<IllegalStateException> { withCachedJsonProviderResources { error("conversion failed") } }
        assertSame(loader, thread.contextClassLoader)
    }
    @Test fun missingTextureRejectedBeforeShipping() {
        val root = Files.createTempDirectory("projects-pack-contract")
        try {
            Files.writeString(root.resolve("model.json"), """{"textures":{"all":"worldseed:missing"}}""")
            assertFailsWith<IllegalArgumentException> { verifyPack(root) }
        } finally { root.toFile().deleteRecursively() }
    }
    @Test fun externalTraversalRejected() {
        val root = Files.createTempDirectory("projects-pack-contract")
        try {
            Files.writeString(root.resolve("model.json"), """{"model":"worldseed:../../../../escape"}""")
            assertFailsWith<IllegalArgumentException> { verifyPack(root) }
        } finally { root.toFile().deleteRecursively() }
    }
    @Test fun vanillaFallbackAndExistingModelAllowed() {
        val root = Files.createTempDirectory("projects-pack-contract")
        try {
            val model = root.resolve("assets/worldseed/models/test.json")
            Files.createDirectories(model.parent)
            Files.writeString(model, """{"parent":"minecraft:item/generated"}""")
            Files.writeString(root.resolve("item.json"), """{"model":"worldseed:test"}""")
            verifyPack(root)
        } finally { root.toFile().deleteRecursively() }
    }
}
