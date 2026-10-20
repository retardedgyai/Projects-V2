#version 330
#extension GL_ARB_separate_shader_objects : require

// Point lights (SakuraLight.kt): coloured light from glowing things onto the ground, rocks and mobs around them.
// The lights are light-probe items; item.vsh / item.fsh drew each one as data on the screen's bottom pixel row:
// the inverse projection and, per slot, the light's view-space position, radius, strength and colour. Here every
// pixel's view position comes back from the depth buffer, its normal from the neighbouring pixels, and each light
// in range adds its colour, fading with distance and turning away from it. No shadows. The data row is painted
// over with the row above it.

uniform sampler2D InSampler;
uniform sampler2D MainDepthSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const int SLOTS = 8;
const int ROW_PIXELS = 33 + SLOTS * 8;

ivec3 bytes(ivec2 p) {
    return ivec3(texelFetch(InSampler, p, 0).rgb * 255.0 + 0.5);
}

float readFloat(int x, int row) {
    ivec3 h = bytes(ivec2(x, row));
    ivec3 l = bytes(ivec2(x + 1, row));
    return uintBitsToFloat((uint(h.r) << 24) | (uint(h.g) << 16) | (uint(h.b) << 8) | uint(l.r));
}

vec3 palette(int i) {
    if (i == 1) return vec3(.70, .38, 1.0);    // shade violet
    if (i == 2) return vec3(1.0, .62, .30);    // ember
    if (i == 3) return vec3(.45, .75, 1.0);    // frost
    return vec3(1.0, .40, .70);                // sakura pink
}

void main() {
    ivec2 size = textureSize(InSampler, 0);
    ivec2 fc = ivec2(gl_FragCoord.xy);
    vec4 base = texelFetch(InSampler, fc, 0);
    // The probe row is the bottom row in GL; the top one in case a backend flips it.
    int row = -1;
    ivec3 h0 = bytes(ivec2(0, 0));
    ivec3 h1 = bytes(ivec2(0, size.y - 1));
    if (h0.r == 83 && h0.g == 76 && (h0.b == 1 || h0.b == 2)) row = 0;
    else if (h1.r == 83 && h1.g == 76 && (h1.b == 1 || h1.b == 2)) row = size.y - 1;
    if (row < 0) {
        fragColor = base;
        return;
    }
    if (fc.y == row && fc.x < ROW_PIXELS) {
        fragColor = texelFetch(InSampler, ivec2(fc.x, row == 0 ? 1 : row - 1), 0);
        return;
    }
    bool zeroToOne = bytes(ivec2(0, row)).b == 1;
    mat4 inv;
    for (int i = 0; i < 16; i++) inv[i >> 2][i & 3] = readFloat(1 + i * 2, row);

    float depth = texelFetch(MainDepthSampler, fc, 0).r;
    vec3 ndc = vec3((vec2(fc) + .5) / vec2(size) * 2.0 - 1.0, zeroToOne ? depth : depth * 2.0 - 1.0);
    vec4 v = inv * vec4(ndc, 1.0);
    vec3 p = v.xyz / v.w;
    vec3 n = normalize(cross(dFdx(p), dFdy(p)));
    if (dot(n, p) > 0.0) n = -n;

    vec3 light = vec3(0.0);
    for (int s = 0; s < SLOTS; s++) {
        int x = 33 + s * 8;
        ivec3 check = bytes(ivec2(x + 7, row));
        if (check != ivec3(170, s, 85)) continue;
        vec3 at = vec3(readFloat(x, row), readFloat(x + 2, row), readFloat(x + 4, row));
        ivec3 params = bytes(ivec2(x + 6, row));
        float radius = float(params.r) / 8.0;
        vec3 to = at - p;
        float d = length(to);
        if (d >= radius) continue;
        float fall = 1.0 - d / radius;
        fall *= fall;
        float facing = max(dot(n, to / max(d, 1e-3)), 0.0);
        light += palette(params.b) * (float(params.g) / 255.0 * 3.0) * fall * (.3 + .7 * facing);
    }
    // Tinted by the surface (a lit surface keeps its texture), plus a little of the light itself so dark ground glows.
    fragColor = vec4(min(base.rgb * (1.0 + light) + light * .06, vec3(1.0)), base.a);
}
