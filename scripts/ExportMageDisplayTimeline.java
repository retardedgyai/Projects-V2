// QA only: replay server-exported metadata through the installed, unchanged
// Vanilla Display implementation. No window, network, input or client patch.
// Compile beside CheckNativeDisplayInterpolation.java with the existing client
// classpath. This checks transforms, not GPU rendering, packet jitter or art.
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import com.google.gson.*;
import net.minecraft.world.entity.Display;
import net.minecraft.network.syncher.SynchedEntityData;
import org.joml.Quaternionf;
import org.joml.Vector3fc;

class ExportMageDisplayTimeline extends CheckNativeDisplayInterpolation {
    static void assign(Display.ItemDisplay d,JsonObject p) {
        var q=p.getAsJsonArray("quaternion");
        d.getEntityData().assignValues(List.of(
            SynchedEntityData.DataValue.create(DELAY,0),
            SynchedEntityData.DataValue.create(TRANSLATION,vector(p.getAsJsonArray("offset"))),
            SynchedEntityData.DataValue.create(SCALE,vector(p.getAsJsonArray("scale"))),
            SynchedEntityData.DataValue.create(ROTATION,new Quaternionf(q.get(0).getAsFloat(),q.get(1).getAsFloat(),q.get(2).getAsFloat(),q.get(3).getAsFloat()))));
    }
    static List<Float> xyz(Vector3fc v) { return List.of(v.x(),v.y(),v.z()); }
    public static void main(String[] args) throws Exception {
        require(args.length==2 || args.length==3,"Supply actual server timeline, output path, optional scene id");
        String sceneId=args.length==3?args[2]:"meteor";
        var result=new ArrayList<Object>();int checked=0;
        for(var raw:JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray()) {
            var scene=raw.getAsJsonObject();
            if(!scene.get("id").getAsString().equals(sceneId))continue;
            var displays=new HashMap<Integer,Display.ItemDisplay>();
            var previous=new HashMap<Integer,JsonObject>();
            var sourceOrigin=new HashMap<Integer,Float>();
            var frames=new ArrayList<Object>();int tick=0;
            for(var source:scene.getAsJsonArray("frames")) {
                var current=new LinkedHashMap<Integer,JsonObject>();
                for(var entry:source.getAsJsonArray()) {
                    var p=entry.getAsJsonObject();int id=p.get("entityId").getAsInt();current.put(id,p);
                    int duration=p.get("interpolation").getAsInt();
                    var d=displays.get(id);
                    if(d==null) {
                        // CoreCombatMeshes spawns immediate Mage contours at
                        // their authored initial transform, not a zero-scale tween.
                        d=display(0);assign(d,p);d.tick();render(d,2f/3);
                        d.getEntityData().assignValues(List.of(SynchedEntityData.DataValue.create(DURATION,duration)));
                        displays.put(id,d);if(duration==1)checked++;
                        sourceOrigin.put(id,vector(p.getAsJsonArray("offset")).y());
                    }
                    d.tickCount=tick;assign(d,p);d.tick();
                }
                for(var old:previous.entrySet()) if(!current.containsKey(old.getKey())) {
                    var p=old.getValue();
                    if(p.get("interpolation").getAsInt()==1) {
                        String model=p.get("model").getAsString();
                        if(model.contains("/cryo_") && !model.contains("/cryo_seed_")) {
                            var first=sourceOrigin.get(old.getKey());
                            float height=model.contains("/cryo_root_")?.75f:
                                model.contains("/cryo_buttress_")?.3125f:model.contains("/cryo_crown_")?1.08f:1.5f;
                            require(vector(p.getAsJsonArray("offset")).y()+height*vector(p.getAsJsonArray("scale")).y()<first-.1f,
                                "Ice removed before its tip withdrew below the original ground");
                        } else require(vector(p.getAsJsonArray("scale")).lengthSquared()<1e-7,"Moving contour removed before zero-scale target");
                    }
                    displays.remove(old.getKey());
                }
                for(int sample=0;sample<3;sample++) {
                    var poses=new ArrayList<Object>();
                    for(var entry:current.entrySet()) {
                        var p=entry.getValue();var d=displays.get(entry.getKey());
                        var transform=d.renderState().transformation().get(d.calculateInterpolationProgress(sample/3f));
                        require(transform.getMatrix().isFinite(),"Nonfinite native transform");
                        if(transform.scale().lengthSquared()<1e-7)continue;
                        var q=transform.leftRotation();
                        poses.add(Map.of("model",p.get("model").getAsString(),"offset",xyz(transform.translation()),
                            "scale",xyz(transform.scale()),"quaternion",List.of(q.x(),q.y(),q.z(),q.w()),
                            "yaw",0,"pitch",0,"roll",0));
                    }
                    frames.add(poses);
                }
                previous=new HashMap<>(current);tick++;
            }
            require(displays.isEmpty(),"Native replay has orphan displays");
            result.add(Map.of("id",sceneId,"name",scene.get("name").getAsString(),"frames",frames));
        }
        require(checked>=5,"Missing moving contours in actual metadata");
        Files.writeString(Path.of(args[1]),new Gson().toJson(result));
        System.out.println("PASS: "+checked+" moving contours replayed through native Display; zero scale or buried ice tip before removal. No GPU/network claim.");
    }
}
