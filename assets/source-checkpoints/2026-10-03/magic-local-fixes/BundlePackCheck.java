import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.zip.*;
public class BundlePackCheck {
    public static void main(String[] args) throws Exception {
        Class<?> cls=Class.forName("dev.projects.server.coreloop.ui.CoreUiPackServer");
        Object companion=cls.getField("Companion").get(null);
        Method bundle=Arrays.stream(companion.getClass().getMethods()).filter(m->m.getName().startsWith("bundle$")).findFirst().orElseThrow();
        byte[] bytes=(byte[])bundle.invoke(companion);
        Files.write(Path.of(args[0]),bytes);
        System.out.println("BUNDLE_OK bytes="+bytes.length);
    }
}
