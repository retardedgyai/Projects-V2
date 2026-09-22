// Calls the installed server's actual bundler without starting a server or client.
// Classpath: review resource directory first, then installed server lib/*.
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.io.ByteArrayInputStream;
import java.util.zip.ZipInputStream;

class CheckWeaponPlaytestResources {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected explicit review resource root");
        var loader = CheckWeaponPlaytestResources.class.getClassLoader();
        var expected = Path.of(args[0]).toAbsolutePath().normalize().resolve("core-ui-pack/index.txt");
        var indexUrl = loader.getResource("core-ui-pack/index.txt");
        if (indexUrl == null || !indexUrl.getProtocol().equals("file") ||
                !Path.of(indexUrl.toURI()).toAbsolutePath().normalize().equals(expected)) {
            throw new IllegalStateException("Review resources did not take classpath precedence");
        }
        var owner = Class.forName("dev.projects.server.coreloop.ui.CoreUiPackServer");
        var companion = owner.getField("Companion").get(null);
        var method = java.util.Arrays.stream(companion.getClass().getMethods())
            .filter(m -> m.getName().startsWith("bundle") && m.getParameterCount()==0)
            .findFirst().orElseThrow();
        byte[] bytes = (byte[])method.invoke(companion);
        Map<String,byte[]> files = new HashMap<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry=zip.getNextEntry(); entry!=null; entry=zip.getNextEntry()) {
                if (files.put(entry.getName(),zip.readAllBytes())!=null) throw new IllegalStateException("Duplicate entry");
            }
        }
        for (String family : new String[]{"greatsword","staff","bow","dagger","mace","tome","astrolabe"}) {
            for (int tier=1;tier<=4;tier++) {
                String key=(family.equals("greatsword") || family.equals("dagger") || family.equals("staff") || family.equals("mace") || family.equals("bow")) && tier>1 ? family+"_t"+tier : family;
                byte[] reference = files.get("assets/projects/items/weapons/pixel_"+key+".json");
                if (reference==null) throw new IllegalStateException("Missing pixel graph: "+key);
                var normal = files.get("assets/projects/items/weapons/"+family+"_t"+tier+".json");
                if (!java.util.Arrays.equals(normal,reference)) throw new IllegalStateException("Wrong equipment alias");
            }
        }
        try (var stream=loader.getResourceAsStream("core-ui-pack/index.txt")) {
            for (String name : new String(stream.readAllBytes(),StandardCharsets.UTF_8).split("\\R")) {
                if (name.isBlank() || name.startsWith("#")) continue;
                if (!files.containsKey(name)) throw new IllegalStateException("Bundler omitted "+name);
            }
        }
        System.out.println("Actual CoreUiPackServer bundler: "+files.size()+" entries; 28 weapon aliases; no server started.");
    }
}
