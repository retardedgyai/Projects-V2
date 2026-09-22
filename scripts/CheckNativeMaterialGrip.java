// Compare the approved sword's bare grip with Vanilla's painted iron-sword grip.
// Uses actual 26.2 ItemTransforms, not a second copy of the Python matrix formula.
import java.nio.file.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;

class CheckNativeMaterialGrip {
    static Vector3f apply(CuboidModel model, ItemDisplayContext context, Vector3f point) {
        var pose=new PoseStack().last();
        model.transforms().getTransform(context).apply(context.leftHand(),pose);
        return pose.pose().transformPosition(new Vector3f(point).div(16)).mul(16);
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=4) throw new IllegalArgumentException("Model directory and model-space grip X Y Z");
        var grip=new Vector3f(Float.parseFloat(args[1]),Float.parseFloat(args[2]),Float.parseFloat(args[3]));
        CuboidModel stock;
        try(var stream=CheckNativeMaterialGrip.class.getResourceAsStream("/assets/minecraft/models/item/handheld.json")) {
            if(stream==null) throw new IllegalStateException("Missing stock handheld reference");
            stock=CuboidModel.fromStream(new java.io.InputStreamReader(stream));
        }
        int models=0,contexts=0;
        try(var paths=Files.list(Path.of(args[0]))) {
            for(var path:paths.filter(p->p.getFileName().toString().startsWith("pixel_material_greatsword") && p.toString().endsWith(".json")).toList()) {
                CuboidModel model;
                try(var reader=Files.newBufferedReader(path)) { model=CuboidModel.fromStream(reader); }
                for(var c:new ItemDisplayContext[]{ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                        ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,ItemDisplayContext.THIRD_PERSON_LEFT_HAND}) {
                    var target=apply(stock,c,new Vector3f(3.5f,3.5f,8));
                    var actual=apply(model,c,grip);
                    if(target.distance(actual)>1e-4) throw new IllegalStateException(path+" "+c+": "+actual+" != "+target);
                    contexts++;
                }
                models++;
            }
        }
        if(models!=25) throw new IllegalStateException("Expected 25 poses, got "+models);
        System.out.println("Native 26.2 grip: "+contexts+" contexts match stock painted-handle anchor. Manual palm/camera review still required.");
    }
}
