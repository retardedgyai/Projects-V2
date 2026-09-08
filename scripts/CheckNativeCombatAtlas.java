// Uses the unmodified 26.2 client's sprite-source codec and resource enumeration.
// No game window, gameplay input, GPU or modified client code is needed.
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.renderer.texture.atlas.*;
import net.minecraft.client.renderer.texture.atlas.sources.DirectoryLister;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;

class CheckNativeCombatAtlas {
    static class Output implements SpriteSource.Output {
        final Set<Identifier> sprites = new HashSet<>();
        public void add(Identifier id, SpriteSource.DiscardableLoader loader) { sprites.add(id); }
        public void removeAll(Predicate<Identifier> filter) { sprites.removeIf(filter); }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Supply one explicit pack directory");
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        var info = new PackLocationInfo("combat-review", Component.literal("combat-review"), PackSource.DEFAULT, Optional.empty());
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES,
                List.of(new PathPackResources(info, root)))) {
            Output stock = new Output();
            new DirectoryLister("item", "item/").run(resources, stock);
            if (stock.sprites.stream().anyMatch(id -> id.getPath().startsWith("combat_vfx/")))
                throw new IllegalStateException("Negative control unexpectedly sees combat sprites");
            SpriteSources.bootstrap();
            var document = JsonParser.parseString(Files.readString(root.resolve("assets/minecraft/atlases/items.json")));
            var sources = SpriteSources.FILE_CODEC.parse(JsonOps.INSTANCE, document).getOrThrow();
            Output fixed = new Output();
            for (var source : sources) source.run(resources, fixed);
            Set<Identifier> referenced = new HashSet<>();
            try (var paths = Files.walk(root.resolve("assets/projects/models/combat_vfx"))) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                    var model = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                    for (var entry : model.getAsJsonObject("textures").entrySet()) {
                        var value = entry.getValue();
                        String sprite = value.isJsonPrimitive() ? value.getAsString() : value.getAsJsonObject().get("sprite").getAsString();
                        if (!sprite.startsWith("projects:")) continue;
                        var id = Identifier.parse(sprite);
                        if (!fixed.sprites.contains(id)) throw new IllegalStateException("Not stitched: " + id + " in " + path);
                        referenced.add(id);
                    }
                }
            }
            if (referenced.size() < 100) throw new IllegalStateException("Too few animated sprites checked");
            System.out.println("Native 26.2 atlas: " + referenced.size() + " referenced combat sprites discoverable; stock-directory negative control sees none. GPU rendering not tested.");
        }
    }
}
