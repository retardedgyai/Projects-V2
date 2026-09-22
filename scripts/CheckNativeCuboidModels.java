// Headless 26.2 client parser check. This does not start Minecraft or alter its code.
// Run with Java 25 source-file mode and the existing Vanilla client classpath.
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.resources.model.cuboid.CuboidModel;

class CheckNativeCuboidModels {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Supply explicit exported model directories");
        boolean rejected = false;
        try {
            CuboidModel.fromStream(new StringReader("""
                {"elements":[{"from":[0,0,0],"to":[1,1,1],"faces":{},
                "rotation":{"origin":[0,0,0],"axis":"invalid","angle":6.4}}]}
                """));
        } catch (RuntimeException expected) { rejected = true; }
        if (!rejected) throw new IllegalStateException("Negative control was not rejected");
        int count = 0;
        for (String argument : args) {
            Path root = Path.of(argument).toAbsolutePath().normalize();
            if (!Files.isDirectory(root)) throw new IllegalArgumentException("Not a model directory: " + root);
            try (var files = Files.walk(root)) {
                for (Path path : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                    try (var reader = Files.newBufferedReader(path)) {
                        var model = CuboidModel.fromStream(reader);
                        if (model.geometry() == null) throw new IllegalStateException("No parsed geometry: " + path);
                        count++;
                    } catch (RuntimeException error) {
                        throw new IllegalStateException("Native model parse failed: " + path, error);
                    }
                }
            }
        }
        if (count == 0) throw new IllegalStateException("No models checked");
        System.out.println("Vanilla 26.2 CuboidModel: " + count + " models accepted; invalid-axis control rejected.");
    }
}
