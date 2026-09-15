// QA only: read actual shipped UVs with the installed, unchanged Vanilla 26.2
// PNG decoder and sprite transparency classifier. No window, GPU or networking.
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import java.nio.file.Files;
import java.nio.file.Path;

class CheckMageMaterialAlpha {
    public static void main(String[] args) throws Exception {
        if(args.length!=1) throw new IllegalArgumentException("Supply repository root");
        var assets=Path.of(args[0]).resolve("server-minestom/src/main/resources/core-ui-pack/assets/projects");
        var image=NativeImage.read(Files.readAllBytes(assets.resolve("textures/combat_vfx/warrior_support/cloth.png")));
        if(image.getWidth()!=48 || image.getHeight()!=80 || (image.getPixel(24,61)>>>24)!=68)
            throw new AssertionError("The actual vapor texel or source dimensions changed");
        int samples=0;
        try(var sprite=new SpriteContents(Identifier.fromNamespaceAndPath("projects","combat_vfx/warrior_support/cloth"),
                new FrameSize(image.getWidth(),image.getHeight()),image);
            var models=Files.newDirectoryStream(assets.resolve("models/combat_vfx/mage_material"),"meteor*.json")) {
            for(var path:models) {
                var model=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                for(var element:model.getAsJsonArray("elements")) {
                    for(var entry:element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                        var face=entry.getValue().getAsJsonObject();
                        if(!face.get("texture").getAsString().equals("#6")) continue;
                        if(!model.getAsJsonObject("textures").get("6").getAsString().equals("projects:combat_vfx/warrior_support/cloth"))
                            throw new AssertionError("Vapor texture binding changed: "+path);
                        var uv=face.getAsJsonArray("uv");
                        float u0=uv.get(0).getAsFloat()/16,v0=uv.get(1).getAsFloat()/16;
                        float u1=uv.get(2).getAsFloat()/16,v1=uv.get(3).getAsFloat()/16;
                        var transparency=sprite.computeTransparency(Math.min(u0,u1),Math.min(v0,v1),Math.max(u0,u1),Math.max(v0,v1));
                        if(!transparency.hasTranslucent() || transparency.isOpaque())
                            throw new AssertionError("Native sprite classifier lost partial alpha: "+path+" "+uv);
                        samples++;
                    }
                }
            }
        }
        if(samples==0) throw new AssertionError("No actual vapor faces inspected");
        System.out.println("PASS: "+samples+" shipped face UVs retain partial alpha in Vanilla 26.2. No GPU/lighting claim.");
    }
}
