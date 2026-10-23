#version 330
#extension GL_ARB_separate_shader_objects : require

// Point lights (SakuraLight.kt): coloured light from glowing things onto the ground, rocks and mobs around them.
// The lights are light-probe items; item.vsh / item.fsh drew each one as data on the screen's bottom pixel row:
// the inverse projection and, per slot, the light's view-space position, radius, strength and colour. Here every
// pixel's view position comes back from the depth buffer, its normal from the neighbouring pixels, and each light
// in range adds its colour, fading with distance and turning away from it. No shadows. The data row is painted
// over with the row above it.
// The data is read from sakura_light_memory.fsh's copy, which keeps the last frame's row for a few frames when this
// frame has none (something translucent drawn over the screen's corner): without it the light blinks off.

uniform sampler2D InSampler;
uniform sampler2D MainDepthSampler;
uniform sampler2D DataSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const int SLOTS = 8;
const int ROW_PIXELS = 33 + SLOTS * 8;

const int MAX_AGE = 8;   // frames a remembered row still lights

ivec3 bytes(ivec2 p) {
    return ivec3(texelFetch(InSampler, p, 0).rgb * 255.0 + 0.5);
}

ivec3 data(int x) {
    return ivec3(texelFetch(DataSampler, ivec2(x, 0), 0).rgb * 255.0 + 0.5);
}

float readFloat(int x) {
    ivec3 h = data(x);
    ivec3 l = data(x + 1);
    return uintBitsToFloat((uint(h.r) << 24) | (uint(h.g) << 16) | (uint(h.b) << 8) | uint(l.r));
}

vec3 palette(int i) {
    if (i == 1) return vec3(.70, .38, 1.0);    // shade violet
    if (i == 2) return vec3(1.0, .62, .30);    // ember
    if (i == 3) return vec3(.45, .75, 1.0);    // frost
    return vec3(1.0, .40, .70);                // sakura pink
}

bool finite(float f) {
    return !isnan(f) && !isinf(f);
}

// Every read stays inside the screen (texelFetch outside it is undefined); a pixel whose position cannot be rebuilt
// (w ~ 0: the sky at the far plane, or a broken matrix) comes back far away, out of every light's reach.
vec3 viewAt(ivec2 at, ivec2 size, mat4 inv, bool zeroToOne) {
    at = clamp(at, ivec2(0), size - 1);
    float depth = texelFetch(MainDepthSampler, at, 0).r;
    vec3 ndc = vec3((vec2(at) + .5) / vec2(size) * 2.0 - 1.0, zeroToOne ? depth : depth * 2.0 - 1.0);
    vec4 v = inv * vec4(ndc, 1.0);
    if (abs(v.w) < 1e-6 || !finite(v.x) || !finite(v.y) || !finite(v.z) || !finite(v.w)) return vec3(0.0, 0.0, -1e6);
    return v.xyz / v.w;
}

void main() {
    ivec2 size = textureSize(InSampler, 0);
    ivec2 fc = ivec2(gl_FragCoord.xy);
    vec4 base = texelFetch(InSampler, fc, 0);
    fragColor = base;
    if (size.x < ROW_PIXELS + 1 || size.y < 2) return;
    // This frame's probe row (bottom in GL, top in case a backend flips it) is hidden under the row next to it.
    ivec3 h0 = bytes(ivec2(0, 0));
    ivec3 h1 = bytes(ivec2(0, size.y - 1));
    int row = h0.r == 83 && h0.g == 76 && (h0.b == 1 || h0.b == 2) ? 0
        : (h1.r == 83 && h1.g == 76 && (h1.b == 1 || h1.b == 2) ? size.y - 1 : -1);
    if (fc.y == row && fc.x < ROW_PIXELS) {
        fragColor = texelFetch(InSampler, ivec2(fc.x, row == 0 ? 1 : row - 1), 0);
        return;
    }
    ivec3 header = data(0);
    if (header.r != 83 || header.g != 76 || (header.b != 1 && header.b != 2) || data(ROW_PIXELS).r > MAX_AGE) return;
    bool zeroToOne = header.b == 1;
    mat4 inv;
    for (int i = 0; i < 16; i++) {
        float f = readFloat(1 + i * 2);
        if (!finite(f)) return;
        inv[i >> 2][i & 3] = f;
    }

    // The lights in reach of this pixel first: most of the screen has none and stops here, before the normal.
    vec3 p = viewAt(fc, size, inv, zeroToOne);
    vec3 lightAt[SLOTS];
    vec4 lightColour[SLOTS];   // rgb colour * strength, a = radius
    int count = 0;
    for (int s = 0; s < SLOTS; s++) {
        int x = 33 + s * 8;
        if (data(x + 7) != ivec3(170, s, 85)) continue;
        vec3 at = vec3(readFloat(x), readFloat(x + 2), readFloat(x + 4));
        if (!finite(at.x) || !finite(at.y) || !finite(at.z)) continue;
        ivec3 params = data(x + 6);
        float radius = float(params.r) / 8.0;
        if (radius < .5 || distance(at, p) >= radius) continue;
        lightAt[count] = at;
        lightColour[count] = vec4(palette(params.b) * (float(params.g) / 255.0 * 3.0), radius);
        count++;
    }
    if (count == 0) return;

    // Normal from the neighbour on the nearer side of each axis: across a silhouette (a mob's fur, a leg in front of
    // the ground) the far side would give a wrong normal that changes every frame as the model moves.
    vec3 l = viewAt(fc - ivec2(1, 0), size, inv, zeroToOne), r = viewAt(fc + ivec2(1, 0), size, inv, zeroToOne);
    vec3 b = viewAt(fc - ivec2(0, 1), size, inv, zeroToOne), t = viewAt(fc + ivec2(0, 1), size, inv, zeroToOne);
    vec3 dx = abs(r.z - p.z) < abs(p.z - l.z) ? r - p : p - l;
    vec3 dy = abs(t.z - p.z) < abs(p.z - b.z) ? t - p : p - b;
    vec3 c = cross(dx, dy);
    vec3 n = dot(c, c) > 1e-12 ? normalize(c) : vec3(0.0);
    if (dot(n, p) > 0.0) n = -n;

    vec3 light = vec3(0.0);
    for (int i = 0; i < count; i++) {
        vec3 to = lightAt[i] - p;
        float d = length(to);
        float radius = lightColour[i].a;
        float fall = 1.0 - d / radius;
        fall *= fall;
        // The light usually sits inside what carries it (a mob's chest): its own body is left alone, or every move of
        // the model flickers through the light.
        fall *= smoothstep(1.5, 4.0, d);
        float facing = max(dot(n, to / max(d, 1e-3)), 0.0);
        light += lightColour[i].rgb * fall * (.45 + .55 * facing);
    }
    light = clamp(light, vec3(0.0), vec3(4.0));
    // Tinted by the surface (a lit surface keeps its texture), plus a little of the light itself so dark ground glows.
    vec3 rgb = base.rgb * (1.0 + light) + light * .15;
    if (!finite(rgb.r) || !finite(rgb.g) || !finite(rgb.b)) return;
    fragColor = vec4(min(rgb, vec3(1.0)), base.a);
}
