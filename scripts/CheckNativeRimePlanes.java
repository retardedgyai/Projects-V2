// QA only: compare actual 26.2 parsed rotations with the software projection.
// This runs no window, renderer, network, world or modified client code.
import java.nio.file.*;
import com.google.gson.*;
import net.minecraft.client.resources.model.cuboid.*;
import org.joml.Vector3f;

class CheckNativeRimePlanes {
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Supply explicit rime model directory");
        int models=0,vertices=0;
        try(var files=Files.list(Path.of(args[0]))) {
            for(var path:files.filter(p->p.getFileName().toString().startsWith("rime_") && p.toString().endsWith(".json")).toList()) {
                var json=JsonParser.parseString(Files.readString(path)).getAsJsonObject().getAsJsonArray("elements");
                CuboidModel model;
                try(var reader=Files.newBufferedReader(path)){model=CuboidModel.fromStream(reader);}
                var elements=((UnbakedCuboidGeometry)model.geometry()).elements();
                if(elements.size()!=json.size())throw new IllegalStateException("Element count changed");
                for(int i=0;i<elements.size();i++) {
                    var element=elements.get(i);
                    var raw=json.get(i).getAsJsonObject();
                    var rotation=raw.getAsJsonObject("rotation");
                    double ax=Math.toRadians(rotation.get("x").getAsDouble());
                    double ay=Math.toRadians(rotation.get("y").getAsDouble());
                    double az=Math.toRadians(rotation.get("z").getAsDouble());
                    var origin=rotation.getAsJsonArray("origin");
                    for(int corner=0;corner<8;corner++) {
                        double[] pos=new double[3];
                        for(int j=0;j<3;j++)pos[j]=raw.getAsJsonArray((corner&(1<<j))==0?"from":"to").get(j).getAsDouble();
                        var nativePoint=new Vector3f((float)(pos[0]/16),(float)(pos[1]/16),(float)(pos[2]/16));
                        nativePoint.sub(element.rotation().origin());
                        element.rotation().transform().transformPosition(nativePoint);
                        nativePoint.add(element.rotation().origin()).mul(16);
                        double x=pos[0]-origin.get(0).getAsDouble(),y=pos[1]-origin.get(1).getAsDouble(),z=pos[2]-origin.get(2).getAsDouble();
                        double yy=y*Math.cos(ax)-z*Math.sin(ax),zz=y*Math.sin(ax)+z*Math.cos(ax);
                        y=yy;z=zz;
                        double xx=x*Math.cos(ay)+z*Math.sin(ay);zz=-x*Math.sin(ay)+z*Math.cos(ay);
                        x=xx;z=zz;
                        xx=x*Math.cos(az)-y*Math.sin(az);yy=x*Math.sin(az)+y*Math.cos(az);
                        var projected=new Vector3f((float)(xx+origin.get(0).getAsDouble()),
                            (float)(yy+origin.get(1).getAsDouble()),(float)(z+origin.get(2).getAsDouble()));
                        if(nativePoint.distance(projected)>0.0001f)throw new IllegalStateException("Preview/native mismatch: "+path+":"+i);
                        vertices++;
                    }
                }
                models++;
            }
        }
        if(models!=7)throw new IllegalStateException("Expected seven rime models, found "+models);
        System.out.println("PASS: "+models+" native parsed rime models / "+vertices+" vertices match preview Euler transforms; no GPU claim.");
    }
}
