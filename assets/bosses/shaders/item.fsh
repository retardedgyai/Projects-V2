#version 330

// ProjectS boss VFX experiment (2026-10-17). Vanilla 26.2 core/item, except for items whose tint is a VFX
// marker: blue == 77 and green == the effect kind (1..5). For those the texture is a data map
// (R = shape mask, G = flow coordinate along the effect, B = across), the tint red is the effect's progress
// (0..1, driven by the server every tick) and the colour is computed here: emissive, moving noise, a hot
// leading edge, dissolving tails. Every other item renders exactly as vanilla.

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec4 rawColor;
in vec2 texCoord0;

out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    return .55 * noise(p) + .3 * noise(p * 2.1 + 7.3) + .15 * noise(p * 4.3 + 1.7);
}

// Shade-dust palette: black-violet body, violet, lavender, hot pink-white core.
vec3 ramp(float h) {
    vec3 c = mix(vec3(.10, .04, .16), vec3(.42, .20, .66), smoothstep(.0, .35, h));
    c = mix(c, vec3(.78, .60, .98), smoothstep(.35, .7, h));
    return mix(c, vec3(1.0, .80, .95), smoothstep(.7, 1.0, h));
}

vec4 shadeVfx(vec4 data, float kind, float p) {
    float mask = data.r, flow = data.g, across = data.b;
    float t = GameTime * 1200.0;                       // seconds
    float n = fbm(vec2(flow * 9.0 - t * 2.4, across * 5.0 + t * .7));
    float heat, alpha;
    if (kind < 1.5) {                                  // 1 slash: a crescent whose head runs along flow with p
        float head = p * 1.25;
        float trail = clamp(1.0 - (head - flow) / .55, 0.0, 1.0) * step(flow, head);
        float body = mask * trail * smoothstep(.15, .55, n + trail * .5);
        heat = body * (.7 + .5 * trail) + smoothstep(.85, 1.0, trail) * mask * .6;
        alpha = min(1.0, body * 1.5) * (1.0 - smoothstep(.75, 1.0, p));
    } else if (kind < 2.5) {                           // 2 ring: a shock ring the server scales; flickers, fades with p
        float flick = .75 + .25 * sin(t * 40.0 + flow * 60.0);
        heat = mask * (.75 + .35 * n) * flick + mask * .3;
        alpha = min(1.0, mask * 1.3) * (.8 + .2 * n) * (1.0 - p * p);
    } else if (kind < 3.5) {                           // 3 beam: dust streaming along flow, a white core
        float stream = fbm(vec2(flow * 14.0 - t * 9.0, across * 3.0));
        heat = mask * mask * (.6 + .6 * stream);
        alpha = mask * smoothstep(.1, .5, stream + mask * .5) * smoothstep(0.0, .12, p) * (1.0 - smoothstep(.6, 1.0, p));
    } else if (kind < 4.5) {                           // 4 puff: a dust cloud dissolving from its rim, glowing edge
        float threshold = p * 1.1;
        float v = mask * .7 + n * .5;
        float edge = 1.0 - smoothstep(0.0, .12, v - threshold);
        heat = mask * .55 + edge;
        alpha = step(threshold, v) * mask * (1.0 - .4 * p);
    } else {                                           // 5 wall: dust rising off the ground in a curtain
        float rise = fbm(vec2(flow * 18.0, across * 2.5 - t * 3.0));
        heat = mask * rise * (1.0 - across * .6) + .2 * mask;
        alpha = mask * smoothstep(.25, .65, rise + (1.0 - across) * .3) * (1.0 - across) * (1.0 - smoothstep(.55, 1.0, p));
    }
    return vec4(ramp(clamp(heat, 0.0, 1.0)), clamp(alpha, 0.0, 1.0));
}

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    int markerB = int(rawColor.b * 255.0 + .5);
    int kind = int(rawColor.g * 255.0 + .5);
    if (markerB == 77 && kind >= 1 && kind <= 5) {
        vec4 fx = shadeVfx(color, float(kind), rawColor.r);
        if (fx.a < .01) {
            discard;
        }
        fragColor = apply_fog(fx, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
        return;
    }
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

    color *= vertexColor * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
