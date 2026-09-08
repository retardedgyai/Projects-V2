// Read-only Vanilla 26.2 parser check, not a Minecraft launch or GPU capture.
import com.google.gson.JsonParser;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.client.resources.model.cuboid.UnbakedCuboidGeometry;

class CheckNativeSwordLighting {
    public static void main(String[] args) throws Exception {
        if(args.length!=1) throw new IllegalArgumentException("Supply candidate weapon model directory");
        for(int invalid:new int[]{-1,16}) {
            boolean rejected=false;
            try {
                CuboidModel.fromStream(new StringReader("{\"elements\":[{\"from\":[0,0,0],\"to\":[1,1,1],\"faces\":{},\"light_emission\":"+invalid+"}]}"));
            } catch(RuntimeException expected) { rejected=true; }
            if(!rejected) throw new IllegalStateException("Invalid emission accepted: "+invalid);
        }
        int count=0,checked=0;
        try(var paths=Files.list(Path.of(args[0]))) {
            for(Path path:paths.filter(p->p.getFileName().toString().startsWith("pixel_material_greatsword") && p.toString().endsWith(".json")).toList()) {
                String raw=Files.readString(path);
                var source=JsonParser.parseString(raw).getAsJsonObject().getAsJsonArray("elements");
                var geometry=(UnbakedCuboidGeometry)CuboidModel.fromStream(new StringReader(raw)).geometry();
                if(geometry.elements().size()!=source.size()) throw new IllegalStateException("Element count mismatch");
                for(int i=0;i<source.size();i++) {
                    String name=source.get(i).getAsJsonObject().get("name").getAsString();
                    int expected=name.startsWith("blade_embers:")?15:name.startsWith("blade:")?12:name.startsWith("jewel:")?9:0;
                    if(geometry.elements().get(i).lightEmission()!=expected)
                        throw new IllegalStateException("Wrong parsed emission: "+path+" "+name);
                    checked++;
                }
                count++;
            }
        }
        if(count!=25) throw new IllegalStateException("Expected 25 poses, got "+count);
        System.out.println("Vanilla 26.2: "+count+" sword poses / "+checked+" element light levels verified; -1/16 rejected. GPU appearance unverified.");
    }
}
