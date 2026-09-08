// Read-only 26.2 animation metadata check; no client launch or client changes.
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;

class CheckNativeBladeAnimation {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Supply the exported ember PNG");
        Path png = Path.of(args[0]);
        var image = ImageIO.read(png.toFile());
        var json = JsonParser.parseString(Files.readString(Path.of(args[0] + ".mcmeta")))
            .getAsJsonObject().get("animation");
        var metadata = AnimationMetadataSection.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        var size = metadata.calculateFrameSize(image.getWidth(), image.getHeight());
        if (size.width() != 64 || size.height() != 128)
            throw new IllegalStateException("Wrong native frame size: " + size);
        int available = image.getWidth() / size.width() * (image.getHeight() / size.height());
        var frames = metadata.frames().orElseThrow();
        if (available != 24 || frames.size() != 24 || metadata.interpolatedFrames())
            throw new IllegalStateException("Unexpected sequence or interpolated pixels");
        int ticks = 0;
        for (int i = 0; i < frames.size(); i++) {
            var frame = frames.get(i);
            if (frame.index() != i || frame.timeOr(metadata.defaultFrameTime()) != 1)
                throw new IllegalStateException("Invalid index/duration at " + i);
            ticks += frame.timeOr(metadata.defaultFrameTime());
        }
        System.out.println("Vanilla 26.2 animation metadata: 24 frames, 64x128, " + ticks
            + " ticks, no interpolation. GPU rendering still unverified.");
    }
}
