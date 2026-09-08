// Actual installed server bundler, without starting a server or client.
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;

class CheckMaterialPlaytestResources {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Supply material review classpath root");
        Path root = Path.of(args[0]).toAbsolutePath().normalize().resolve("core-ui-pack");
        var loader = CheckMaterialPlaytestResources.class.getClassLoader();
        var location = loader.getResource("core-ui-pack/index.txt");
        if (location == null || !location.getProtocol().equals("file") ||
                !Path.of(location.toURI()).toAbsolutePath().normalize().equals(root.resolve("index.txt"))) {
            throw new IllegalStateException("Wrong classpath precedence");
        }
        var owner = Class.forName("dev.projects.server.coreloop.ui.CoreUiPackServer");
        var companion = owner.getField("Companion").get(null);
        var method = Arrays.stream(companion.getClass().getMethods())
            .filter(m -> m.getName().startsWith("bundle") && m.getParameterCount() == 0)
            .findFirst().orElseThrow();
        byte[] bytes = (byte[]) method.invoke(companion);
        Map<String, byte[]> files = new HashMap<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (files.put(entry.getName(), zip.readAllBytes()) != null)
                    throw new IllegalStateException("Duplicate bundle entry");
            }
        }
        var paths = Files.readAllLines(root.resolve("index.txt"));
        if (paths.size() != files.size()) throw new IllegalStateException("Bundle/index count mismatch");
        for (String name : paths) {
            if (!Arrays.equals(files.get(name), Files.readAllBytes(root.resolve(name))))
                throw new IllegalStateException("Bundler changed or omitted: " + name);
        }
        if (!Arrays.equals(files.get("assets/projects/items/weapons/greatsword_t1.json"),
                files.get("assets/projects/items/weapons/pixel_material_greatsword.json")))
            throw new IllegalStateException("Material candidate not routed to T1 greatsword");
        System.out.println("Actual CoreUiPackServer bundle: " + files.size()
            + " byte-matched entries; T1 greatsword candidate routed; no server/client started.");
    }
}
