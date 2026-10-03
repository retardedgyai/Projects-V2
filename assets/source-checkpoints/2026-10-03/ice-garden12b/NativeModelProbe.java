package dev.projects.artprobe;

import com.google.gson.*;
import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.client.resources.model.cuboid.*;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import java.nio.file.*;

/** Actual 26.2 parser, native corner/UV order, and actual rotation transform.
 * Identity model state only. No GPU, client launch, sprite atlas, or feel claim.
 */
public class NativeModelProbe {
    public static void main(String[] args) throws Exception {
        var ec = Class.forName("net.minecraft.client.resources.model.cuboid.CuboidModelElement$Deserializer").getDeclaredConstructor(); ec.setAccessible(true);
        var fc = Class.forName("net.minecraft.client.resources.model.cuboid.CuboidFace$Deserializer").getDeclaredConstructor(); fc.setAccessible(true);
        Gson gson = new GsonBuilder()
            .registerTypeAdapter(CuboidModelElement.class, ec.newInstance())
            .registerTypeAdapter(CuboidFace.class, fc.newInstance()).create();
        JsonObject source = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        JsonArray faces = new JsonArray();
        int elementIndex = 0;
        for (JsonElement json : source.getAsJsonArray("elements")) {
            CuboidModelElement e = gson.fromJson(json, CuboidModelElement.class);
            for (var entry : e.faces().entrySet()) {
                Direction direction = entry.getKey(); CuboidFace face = entry.getValue();
                JsonObject row = new JsonObject(); row.addProperty("element", elementIndex);
                row.addProperty("side", direction.getSerializedName()); row.addProperty("texture", face.texture());
                JsonArray points = new JsonArray(), uvs = new JsonArray(), cornerIds = new JsonArray();
                FaceInfo info = FaceInfo.fromFacing(direction);
                for (int i = 0; i < 4; i++) {
                    Vector3f raw = info.getVertexInfo(i).select(e.from(), e.to());
                    Vector3f binary = info.getVertexInfo(i).select(new Vector3f(0), new Vector3f(1));
                    cornerIds.add((int)binary.x + 2*(int)binary.y + 4*(int)binary.z);
                    Vector3f p = raw.div(16f);
                    if (e.rotation() != null) {
                        p.sub(e.rotation().origin()); e.rotation().transform().transformPosition(p); p.add(e.rotation().origin());
                    }
                    // Same stage as FaceBakery.bakeVertex, before atlas U/V mapping.
                    p.sub(.5f,.5f,.5f).mul(6.25f);
                    JsonArray point = new JsonArray(); point.add(p.x); point.add(p.y); point.add(p.z); points.add(point);
                    JsonArray uv = new JsonArray();
                    uv.add(CuboidFace.getU(face.uvs(), face.rotation(), i));
                    uv.add(CuboidFace.getV(face.uvs(), face.rotation(), i)); uvs.add(uv);
                }
                row.add("points_m", points); row.add("uv", uvs); row.add("cube_corner_ids", cornerIds); faces.add(row);
            }
            elementIndex++;
        }
        JsonObject result = new JsonObject(); result.addProperty("minecraft_version", "26.2");
        result.addProperty("parsed_elements", elementIndex); result.addProperty("native_faces", faces.size());
        result.addProperty("actual_parser", true); result.addProperty("actual_rotation_transform", true);
        result.addProperty("actual_FaceInfo_and_CuboidFace_uv", true); result.addProperty("gpu_render_verified", false);
        result.add("faces", faces);
        Files.writeString(Path.of(args[1]), new GsonBuilder().setPrettyPrinting().create().toJson(result));
        System.out.println("Native parser + corner/UV/rotation: " + elementIndex + " elements / " + faces.size() + " faces");
    }
}
