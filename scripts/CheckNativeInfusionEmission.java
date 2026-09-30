// Headless unmodified Vanilla 26.2 model parser. This does not launch a client or measure GPU output.
import com.google.gson.JsonParser;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.client.resources.model.cuboid.UnbakedCuboidGeometry;

class CheckNativeInfusionEmission {
    public static void main(String[] args) throws Exception {
        if(args.length!=1) throw new IllegalArgumentException("Supply v5 native model directory");
        for(int invalid:new int[]{-1,16}) {
            boolean rejected=false;
            try {
                CuboidModel.fromStream(new StringReader("{\"elements\":[{\"from\":[0,0,0],\"to\":[1,1,1],\"faces\":{},\"light_emission\":"+invalid+"}]}"));
            } catch(RuntimeException expected) { rejected=true; }
            if(!rejected) throw new IllegalStateException("Invalid emission accepted: "+invalid);
        }
        int models=0,elements=0,emissive=0;
        for(String state:new String[]{"idle","active","unlit"}) {
            Path path=Path.of(args[0],"core_"+state+".json");
            String raw=Files.readString(path);
            var source=JsonParser.parseString(raw).getAsJsonObject().getAsJsonArray("elements");
            var geometry=(UnbakedCuboidGeometry)CuboidModel.fromStream(new StringReader(raw)).geometry();
            if(geometry.elements().size()!=source.size()) throw new IllegalStateException("Element count mismatch");
            for(int i=0;i<source.size();i++) {
                var json=source.get(i).getAsJsonObject();
                int expected=json.has("light_emission")?json.get("light_emission").getAsInt():0;
                var nativeElement=geometry.elements().get(i);
                if(nativeElement.lightEmission()!=expected) throw new IllegalStateException("Incorrect native emission: "+i);
                if(expected==15) {
                    if(nativeElement.shade()) throw new IllegalStateException("Glow unexpectedly shaded");
                    emissive++;
                } else if(expected!=0) throw new IllegalStateException("Stone body must retain ambient lighting");
                elements++;
            }
            models++;
        }
        System.out.println("{\"passed\":true,\"minecraftVersion\":\"26.2\",\"models\":"+models+",\"elements\":"+elements+",\"emissiveElements\":"+emissive+",\"invalidEmissionRejected\":true,\"clientStarted\":false,\"gpuVerified\":false}");
    }
}
