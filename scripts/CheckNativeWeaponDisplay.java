// Actual Vanilla 26.2 transforms, headless; no game startup, input or client edits.
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;

class CheckNativeWeaponDisplay {
    static Vector3f vector(JsonArray values) {
        return new Vector3f(values.get(0).getAsFloat(),values.get(1).getAsFloat(),values.get(2).getAsFloat());
    }

    public static void main(String[] args) throws Exception {
        if (args.length!=1) throw new IllegalArgumentException("Supply generated display-contract.json");
        var cases = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray();
        if (cases.size()!=250) throw new IllegalStateException("Expected 175 family poses plus 75 greatsword Tier poses");
        int count=0;
        for (var value:cases) {
            var check=value.getAsJsonObject();
            var path=Path.of(check.get("path").getAsString());
            CuboidModel model;
            try (var reader=Files.newBufferedReader(path)) { model=CuboidModel.fromStream(reader); }
            for (var context:new ItemDisplayContext[]{ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                    ItemDisplayContext.FIRST_PERSON_LEFT_HAND,ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    ItemDisplayContext.THIRD_PERSON_LEFT_HAND}) {
                var pose=new PoseStack().last();
                model.transforms().getTransform(context).apply(context.leftHand(),pose);
                var actual=vector(check.getAsJsonArray("grip")).div(16);
                pose.pose().transformPosition(actual).mul(16);
                var target=vector(check.getAsJsonObject("targets").getAsJsonArray(
                    context.firstPerson()?"firstperson":"thirdperson"));
                if (context.leftHand()) target.x=-target.x;
                if (actual.distance(target)>1e-4) throw new IllegalStateException(
                    path.getFileName()+" "+context+": "+actual+" != "+target);
                count++;
            }
        }
        System.out.println("Vanilla 26.2 ItemTransform: "+count+" grip placements accepted across "+cases.size()+" poses; not in-game palm/camera approval.");
    }
}
