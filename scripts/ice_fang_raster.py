"""ProjectS's existing per-pixel native-model projection; no generated art."""
import math
import numpy as np
def raster_quad(canvas, depth_buffer, points, texture, uv, perspective=False, vertex_uv=None):
    """Nearest native UV sampling with per-pixel depth, for intersecting models.

    No art is generated here: inputs are the actual authored quad, bitmap and
    display transform. Unlike painter sorting, one long face cannot overwrite
    an entire short face merely because its average depth is nearer.
    """
    tex=np.asarray(texture)
    texture_height,texture_width=tex.shape[:2]
    coords=np.array(vertex_uv if vertex_uv is not None else
                    ((uv[0],uv[1]),(uv[2],uv[1]),(uv[2],uv[3]),(uv[0],uv[3])))/16
    for ids in ((0,i,i+1) for i in range(1,len(points)-1)):
        verts=np.array([points[i] for i in ids],dtype=float)
        tc=coords[list(ids)]
        xmin=max(0,int(math.floor(verts[:,0].min())))
        xmax=min(canvas.shape[1]-1,int(math.ceil(verts[:,0].max())))
        ymin=max(0,int(math.floor(verts[:,1].min())))
        ymax=min(canvas.shape[0]-1,int(math.ceil(verts[:,1].max())))
        if xmin>xmax or ymin>ymax: continue
        x,y=np.meshgrid(np.arange(xmin,xmax+1)+.5,np.arange(ymin,ymax+1)+.5)
        a,b,c=verts
        den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
        if abs(den)<1e-9: continue
        w0=((b[1]-c[1])*(x-c[0])+(c[0]-b[0])*(y-c[1]))/den
        w1=((c[1]-a[1])*(x-c[0])+(a[0]-c[0])*(y-c[1]))/den
        weights=np.stack((w0,w1,1-w0-w1),axis=-1)
        inside=(weights>=-1e-7).all(axis=-1)
        if perspective:
            weighted=weights/verts[:,2]
            inv=weighted.sum(axis=-1)
            safe=np.where(abs(inv)>1e-10,inv,1e-10)
            depth=1/safe
            sample=sum(weighted[:,:,i,None]*tc[i] for i in range(3))/safe[:,:,None]
            inside &= depth>=.1
        else:
            depth=sum(weights[:,:,i]*verts[i,2] for i in range(3))
            sample=sum(weights[:,:,i,None]*tc[i] for i in range(3))
        tx=np.clip((sample[:,:,0]*texture_width).astype(int),0,texture_width-1)
        ty=np.clip((sample[:,:,1]*texture_height).astype(int),0,texture_height-1)
        ink=tex[ty,tx]
        target_depth=depth_buffer[ymin:ymax+1,xmin:xmax+1]
        mask=inside & (depth<target_depth-1e-8) & (ink[:,:,3]>127)
        canvas[ymin:ymax+1,xmin:xmax+1][mask]=ink[mask]
        target_depth[mask]=depth[mask]
