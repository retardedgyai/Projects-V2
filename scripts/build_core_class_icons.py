"""Original ProjectS pixel silhouettes, authored geometry at the existing 32px icon grid.

No downloaded artwork, text rasterization, or model-generated placeholders. Shapes communicate
the action: blade / missile / ground field / displacement / protection / recovery. Each class
has a limited palette, each skill its own silhouette, ultimates a double gold corner treatment.
The runtime catalog is read only to validate IDs; it cannot silently invent missing artwork.
"""
from PIL import Image, ImageDraw
from build_core_hud_assets import ROOT, SKILLS, save

PALETTES = (
    ("#3d2127", "#c45543", "#ffbc77", "#fff0c2"),
    ("#182e2e", "#3f8873", "#9bdb94", "#e8f7cc"),
    ("#25213e", "#7661ba", "#9ce0ed", "#f1ecff"),
    ("#1e2444", "#6964ba", "#b1a6ff", "#fff0cc"),
    ("#2d1839", "#8e467c", "#e18aaf", "#fde4ee"),
    ("#26333d", "#507b99", "#d3b577", "#fff0b7"),
    ("#1c3533", "#5c9a83", "#e0c880", "#fff8d5"),
)
# Explicit art direction, not a numeric/random icon assignment.
MOTIFS = (
    ("blade", "impact", "whirl", "parry", "wound", "counter", "banner", "lance", "cleave", "fortress"),
    ("arrow", "fan", "rain", "retreat", "pierce", "trap", "target", "volley", "greatarrow", "storm"),
    ("flame", "snow", "meteor", "blink", "bolt", "icefield", "burst", "ward", "sunfall", "winter"),
    ("thread", "orbit", "starfall", "starstep", "needle", "nebula", "starward", "starbreak", "cosmos", "constellation"),
    ("dagger", "execute", "knives", "escape", "venom", "chase", "dart", "parry", "deathwheel", "contract"),
    ("mace", "gravity", "shield", "fortress", "impact", "seal", "break", "lance", "singularity", "citadel"),
    ("ray", "heal", "pillar", "wing", "lamp", "ward", "brand", "wind", "wings", "judgment"),
)


def artwork(job, slot, motif):
    dark, mid, light, white = PALETTES[job]
    im = Image.new("RGBA", (32, 32))
    d = ImageDraw.Draw(im)
    d.polygon([(5,2),(26,2),(29,5),(29,26),(26,29),(5,29),(2,26),(2,5)], fill=dark)
    d.line([(5,3),(25,3),(28,6)], fill=mid)
    def line(points, color=light, width=2): d.line(points, fill=color, width=width)
    def diamond(x,y,r=3,color=white): d.polygon([(x,y-r),(x+r,y),(x,y+r),(x-r,y)],fill=color)
    def ring(box=(7,7,25,25), color=mid, width=2): d.ellipse(box,outline=color,width=width)
    def star(x,y,r=5):
        d.polygon([(x,y-r),(x+1,y-1),(x+r,y),(x+1,y+1),(x,y+r),(x-1,y+1),(x-r,y),(x-1,y-1)],fill=white)
    def blade(x=0,y=0):
        d.polygon([(8+x,21+y),(20+x,5+y),(24+x,4+y),(24+x,9+y),(12+x,23+y)],fill=light)
        line([(12+x,20+y),(23+x,6+y)],white,1)
        line([(7+x,18+y),(15+x,25+y)],mid,3)
        line([(10+x,23+y),(6+x,28+y)],white,2)
    def arrow(x=16,y=16,angle=0):
        if angle == 0:
            line([(x-7,y+7),(x+6,y-6)],white,2)
            line([(x,y-7),(x+7,y-7),(x+7,y)],light,2)
            line([(x-8,y+3),(x-8,y+8),(x-3,y+8)],mid,2)
        else:
            line([(x,y+9),(x,y-7)],white,2)
            d.polygon([(x-4,y-4),(x,y-10),(x+4,y-4)],fill=light)
    def shield():
        d.polygon([(6,7),(16,4),(26,7),(24,20),(16,28),(8,20)],fill=mid,outline=light)
        d.polygon([(10,10),(16,8),(22,10),(21,18),(16,23),(11,18)],fill=dark)
        line([(16,10),(16,21)],white); line([(12,14),(20,14)],white)
    def ground():
        d.polygon([(4,21),(15,16),(28,21),(17,28)],fill=mid,outline=light)
        line([(9,22),(16,19),(23,22),(17,25),(9,22)],dark,2)

    if motif in ("blade","wound","counter","lance","cleave","dagger","execute","chase","contract","break"):
        if motif in ("counter","chase"): d.arc((3,4,26,27),120,305,fill=mid,width=3)
        if motif in ("wound","execute","contract"):
            line([(7,6),(22,25)],mid,3); line([(5,10),(18,26)],light,1)
        blade()
        if motif in ("cleave","break"): line([(6,9),(10,13),(5,15),(11,18)],white,2); diamond(25,23,4)
        if motif == "lance": line([(5,28),(27,3)],white,2); line([(4,17),(11,17)],mid)
        if motif == "dagger": im = im.resize((26,26),Image.Resampling.NEAREST); out=Image.new("RGBA",(32,32));out.alpha_composite(im,(3,3));im=out
    elif motif in ("whirl","knives","deathwheel"):
        ring((4,4,28,28),light); d.arc((8,8,24,24),20,260,fill=white,width=2)
        for x,y in ((7,9),(23,8),(24,23)):
            d.polygon([(x-3,y+3),(x+4,y-4),(x+2,y+3)],fill=white)
        if motif == "deathwheel": diamond(16,16,5)
        if motif == "knives": line([(12,22),(20,10)],mid,2)
    elif motif in ("arrow","pierce","greatarrow","retreat","volley","fan","rain","storm","dart"):
        if motif in ("rain","storm"):
            ground()
            for x,y in ((8,12),(16,9),(24,13)): arrow(x,y,1)
        elif motif in ("volley","fan"):
            for x,y in ((9,19),(16,15),(22,11)): arrow(x,y,1 if motif == "fan" else 0)
        else:
            arrow()
            if motif == "pierce": ring((16,3,29,16),mid,1);line([(3,26),(12,17)],light,3)
            if motif == "greatarrow": line([(4,28),(25,7)],light,4);arrow();star(24,7,4)
            if motif == "retreat": line([(4,12),(4,22),(13,22)],mid,3)
            if motif == "dart": diamond(21,9,3);d.point((8,26),fill=white)
    elif motif in ("parry","shield","fortress","ward","starward","citadel"):
        shield()
        if motif == "parry": line([(4,27),(24,4)],white,2);star(24,5,4)
        if motif in ("fortress","citadel"):
            for x in (5,14,23): d.rectangle((x,3,x+4,8),fill=light)
        if motif == "starward": star(16,15,6)
        if motif == "ward": ring((2,2,30,30),light,1)
    elif motif in ("impact","burst","starbreak","singularity","gravity"):
        ring((5,8,27,28),mid)
        for x,y in ((5,8),(26,7),(5,24),(27,25)):
            line([(x,y),(16,17)],light,2)
        diamond(16,17,6,white)
        if motif in ("gravity","singularity"):
            ring((11,12,21,22),dark,3)
            if motif == "singularity": ring((2,2,30,30),light,1)
        if motif == "impact": line([(14,4),(18,4),(18,15)],white,3)
        if motif == "starbreak": star(16,17,9)
    elif motif in ("trap","icefield","seal","pillar","meteor","starfall","sunfall","nebula","cosmos","judgment"):
        ground()
        if motif in ("meteor","starfall","sunfall","cosmos"):
            for x,y in ((10,14),(20,10),(25,17)):
                line([(x,y),(x+4,y-8)],mid,3);diamond(x,y,3)
            if motif in ("sunfall","cosmos"): star(15,9,6)
        elif motif in ("pillar","judgment"):
            line([(16,5),(16,22)],white,4);line([(10,7),(10,20)],light,1);line([(22,7),(22,20)],light,1)
            if motif == "judgment": d.polygon([(7,5),(25,5),(16,12)],fill=light)
        elif motif == "trap": line([(6,16),(9,9),(16,17),(23,9),(26,16)],white,2)
        elif motif == "icefield":
            for x,y in ((9,15),(16,10),(23,16)):d.polygon([(x-3,y+5),(x,y-7),(x+3,y+5)],fill=white)
        else:
            ring((7,5,25,20),light,1);star(16,12,5)
            if motif == "nebula": d.arc((4,3,28,23),180,345,fill=white,width=2)
    elif motif in ("thread","orbit","needle","constellation","snow","winter","target","brand"):
        if motif in ("orbit","snow","winter","target"):ring((4,4,28,28),light,1)
        if motif in ("thread","needle","constellation"):
            line([(6,24),(13,15),(22,20),(25,6)],light,1)
            for x,y in ((6,24),(13,15),(25,6)):star(x,y,3 if motif == "needle" else 4)
        else:
            star(16,16,10 if motif == "winter" else 7)
            if motif in ("snow","winter"):line([(8,8),(24,24)],white);line([(8,24),(24,8)],white)
            if motif in ("target","brand"): ring((11,11,21,21),dark,2);diamond(16,16,2)
    elif motif in ("blink","starstep","escape","wind","wing","wings","heal"):
        for y in (9,15,21): line([(5,y+3),(12,y+3),(20,y-3)],mid if y == 9 else light,2)
        if motif in ("blink","starstep","escape"):diamond(23,12,5);line([(22,18),(18,27)],white,2)
        if motif in ("wing","wings"):
            d.polygon([(15,22),(4,16),(5,5),(11,13),(15,10),(17,23)],fill=white)
            if motif == "wings":d.polygon([(17,22),(28,16),(27,5),(21,13),(17,10)],fill=light)
        if motif == "heal": line([(16,6),(16,25)],white,5);line([(7,15),(25,15)],white,5)
    elif motif in ("flame","bolt","ray","venom","mace","lamp","banner"):
        if motif == "flame":
            d.polygon([(8,24),(5,17),(12,10),(16,3),(22,12),(27,18),(23,26),(14,28)],fill=mid)
            d.polygon([(11,24),(12,16),(18,10),(19,19),(23,22),(18,26)],fill=white)
        elif motif in ("bolt","ray"):
            d.polygon([(18,3),(8,18),(15,18),(12,29),(25,12),(18,12)],fill=white)
            if motif == "ray":line([(4,7),(8,11)],light);line([(24,24),(28,28)],light)
        elif motif == "venom":
            d.polygon([(16,4),(25,18),(24,25),(17,29),(9,25),(7,18)],fill=light)
            diamond(16,21,5,dark);d.rectangle((13,21,14,23),fill=white);d.rectangle((18,21,19,23),fill=white)
        elif motif == "mace":
            line([(8,27),(21,11)],light,4);d.polygon([(12,8),(19,3),(28,12),(23,19)],fill=mid,outline=white);diamond(20,11,4)
        elif motif == "lamp":
            ring((11,3,21,13),light,2);d.rectangle((9,10,23,26),fill=mid,outline=light);star(16,18,6)
        else:
            line([(8,4),(8,28)],white,2);d.polygon([(10,5),(25,7),(21,14),(26,21),(10,19)],fill=mid,outline=light);diamond(16,12,3)
    else:
        raise ValueError(f"Unauthored skill silhouette: {motif}")
    d = ImageDraw.Draw(im)
    if slot >= 8:
        for x,y,sx,sy in ((2,2,1,1),(29,2,-1,1),(2,29,1,-1),(29,29,-1,-1)):
            d.line([(x,y+sy*5),(x,y),(x+sx*5,y)],fill="#e4bd70",width=1)
        d.point((15,2),fill="#fff3ba");d.point((16,29),fill="#fff3ba")
    return im


def build():
    assert len(SKILLS) == 70
    for job,motifs in enumerate(MOTIFS):
        for slot,motif in enumerate(motifs):
            save(artwork(job,slot,motif),ROOT / f"assets/core-ui/skills/{SKILLS[job*10+slot]}.png")
    print("Authored 70 ProjectS skill silhouettes")


if __name__ == "__main__":
    build()
