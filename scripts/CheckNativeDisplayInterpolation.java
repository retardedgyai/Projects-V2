// Headless verification against the installed, UNMODIFIED Vanilla client classes.
// No Minecraft window, network connection, game input or client patch.
import java.lang.reflect.Field;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import sun.misc.Unsafe;

class CheckNativeDisplayInterpolation {
    static Field field(Class<?> type, String name) throws Exception {
        var f=type.getDeclaredField(name); f.setAccessible(true); return f;
    }
    @SuppressWarnings("unchecked")
    static <T> EntityDataAccessor<T> key(String name) throws Exception {
        return (EntityDataAccessor<T>)field(Display.class,name).get(null);
    }
    static final EntityDataAccessor<Integer> DELAY;
    static final EntityDataAccessor<Integer> DURATION;
    static final EntityDataAccessor<Vector3f> TRANSLATION;
    static final EntityDataAccessor<Vector3f> SCALE;
    static final EntityDataAccessor<Quaternionf> ROTATION;
    static {
        try {
            SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
            DELAY=key("DATA_TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS_ID");
            DURATION=key("DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID");
            TRANSLATION=key("DATA_TRANSLATION_ID");
            SCALE=key("DATA_SCALE_ID");
            ROTATION=key("DATA_LEFT_ROTATION_ID");
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    static Display.ItemDisplay display(int duration) throws Exception {
        // Only Level.isClientSide is read by Display.tick; no world simulation runs.
        var unsafe=(Unsafe)field(Unsafe.class,"theUnsafe").get(null);
        var level=(ClientLevel)unsafe.allocateInstance(ClientLevel.class);
        field(Level.class,"isClientSide").set(level,true);
        var d=new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY,level);
        d.getEntityData().assignValues(List.of(SynchedEntityData.DataValue.create(DURATION,duration),
            SynchedEntityData.DataValue.create(SCALE,new Vector3f(0))));
        d.tickCount=-2;
        d.tick();
        render(d,2f/3);
        return d;
    }
    static void target(Display.ItemDisplay d,float x) {
        d.getEntityData().assignValues(List.of(
            SynchedEntityData.DataValue.create(DELAY,0),
            SynchedEntityData.DataValue.create(TRANSLATION,new Vector3f(x,0,0))));
    }
    static float render(Display.ItemDisplay d,float partial) {
        return d.renderState().transformation().get(d.calculateInterpolationProgress(partial)).translation().x();
    }
    static void require(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        require(args.length==1,"Supply Kotlin-exported flow-display-contract.json");
        float[] missedTickTravel=new float[3];
        for(int duration:new int[]{1,2}) {
            var d=display(duration);
            float previous=0;
            for(int tick=1;tick<=8;tick++) {
                d.tickCount=tick;
                // One missed delivery tick followed by a catch-up packet.
                if(tick!=4) target(d,tick);
                d.tick();
                float start=render(d,0),mid=render(d,1f/3),end=render(d,2f/3);
                require(start>=previous-1e-5 && mid>=start && end>=mid,"Client position reversed");
                if(tick==4) missedTickTravel[duration]=end-start;
                previous=end;
                System.out.printf("duration=%d tick=%d x=[%.3f, %.3f, %.3f]%n",duration,tick,start,mid,end);
            }
        }
        require(missedTickTravel[1]==0,"Negative control no longer reproduces a held frame");
        require(missedTickTravel[2]>.1,"Two-tick interpolation failed to bridge the missed delivery");
        System.out.println("PASS: Vanilla 26.2 reproduces the one-tick hold; two ticks bridge one missing delivery.");
        // Removing immediately at the final target truncates the interpolated fade.
        var tail=display(2);
        tail.getEntityData().assignValues(List.of(SynchedEntityData.DataValue.create(DELAY,0),
            SynchedEntityData.DataValue.create(SCALE,new Vector3f(1))));
        tail.tickCount=1; tail.tick(); render(tail,2f/3);
        tail.tickCount=3; tail.tick(); render(tail,2f/3);
        tail.getEntityData().assignValues(List.of(SynchedEntityData.DataValue.create(DELAY,0),
            SynchedEntityData.DataValue.create(SCALE,new Vector3f(0))));
        tail.tickCount=4; tail.tick();
        float before=tail.renderState().transformation().get(tail.calculateInterpolationProgress(0)).scale().x();
        tail.tickCount=6; tail.tick();
        float after=tail.renderState().transformation().get(tail.calculateInterpolationProgress(0)).scale().x();
        require(before>.9 && after==0,"Final scale was not allowed to drain");
        System.out.println("PASS: two drain ticks finish the final shrink without a despawn cut.");
        int count=0;
        for(var row:JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray()) {
            var contract=row.getAsJsonObject();
            require(contract.get("interpolation").getAsInt()==2,"Server must actually send the tested duration");
            // Same server targets, delivered on time and with one missing mid-cut tick.
            for(int missed:new int[]{-99,4}) {
                var d=display(contract.get("interpolation").getAsInt());
                var targets=contract.getAsJsonArray("targets");
                for(int i=0;i<targets.size();i++) {
                    d.tickCount=i;
                    var target=targets.get(i);
                    if(!target.isJsonNull() && i!=missed) {
                        var p=target.getAsJsonObject();var q=p.getAsJsonArray("rotation");
                        d.getEntityData().assignValues(List.of(
                            SynchedEntityData.DataValue.create(DELAY,0),
                            SynchedEntityData.DataValue.create(TRANSLATION,vector(p.getAsJsonArray("translation"))),
                            SynchedEntityData.DataValue.create(SCALE,vector(p.getAsJsonArray("scale"))),
                            SynchedEntityData.DataValue.create(ROTATION,new Quaternionf(q.get(0).getAsFloat(),q.get(1).getAsFloat(),q.get(2).getAsFloat(),q.get(3).getAsFloat()))));
                    }
                    d.tick();
                    for(int frame=0;frame<3;frame++) {
                        var t=d.renderState().transformation().get(d.calculateInterpolationProgress(frame/3f));
                        require(t.getMatrix().isFinite(),"Non-finite actual Vanilla transform");
                    }
                }
                var t=d.renderState().transformation().get(d.calculateInterpolationProgress(0));
                require(t.scale().x()*t.scale().z()<1e-7,"Server removes a still-visible slash surface");
                count++;
            }
        }
        require(count>0,"Empty server target contract");
        System.out.println("PASS: "+count+" real slash target streams through Vanilla Display.tick/Transformation, including a missed delivery; no GPU/network simulation.");
    }
    static Vector3f vector(JsonArray a) { return new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()); }
}
