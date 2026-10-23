#version 330
#extension GL_ARB_separate_shader_objects : require

// ProjectS combat VFX on vanilla 26.3 core/item. Two independent markers, every other item renders as vanilla:
// - 黒翳竜 (scripts/build_boss_vfx.py): items whose tint is blue == 77, green == the effect kind (1..6). The
//   texture is a data map (R = shape mask, G = flow coordinate along the effect, B = across), the tint red is the
//   effect's progress (0..1, driven by the server every tick) and the colour is computed here: emissive, moving
//   noise, a hot leading edge, dissolving tails.
//   Kinds 7..9 are the warrior's heat cut (sprite war_slash): white-hot head, cooling to red and ember; 7 is the
//   hottest, 8 and 9 burn cooler (鉄槌's first falls).
// - Warrior iron strikes (scripts/build_warrior_iron_vfx.py): texels whose alpha is a marker. 253 = heat,
//   self-lit and flickering; 121 / 65 = near / far glow halo.
// - 桜牙獣 petal storm (SakuraPetalFx): items whose tint is blue == 78, green == 100 + heat * 100, red == fade. The
//   texture is a data map (R mask, G along: the petal at 0 .. the ribbon end at 1, B across): a white-hot petal
//   that cools to pink, trailing a glowing ribbon.
// - 桜牙獣 (scripts/build_boss_sakura_fang.py): texels with alpha 200..215 and R/G/B low bits 1/2/3 glow, and a
//   light runs along them: alpha - 200 is the texel's place on the flow (0 shoulders .. 15 tail tip).
// - Point lights (SakuraLight.kt): items tinted blue 80..83 with the probe texture (83, 76, 49). item.vsh moved the
//   quad to the bottom pixel row; here it writes, as raw float bits, what post/sakura_light.fsh needs to light the
//   scene: x 0 a header (B = depth convention), x 1..32 the inverse projection, then 8 pixels per light slot
//   (tint green >> 4): its view-space position, radius / strength / colour, and a slot check.
// The VFX colours are self-lit (no overlay, lightmap or glint) but go through the same OIT phases and fog as vanilla.

#include <minecraft:globals.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:oit.glsl>

uniform sampler2D Sampler0;

#ifdef GLINT
uniform sampler2D GlintSampler;
#endif

#ifndef OIT_ALPHA_ONLY
layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
#endif
layout(location = 2) in vec4 vertexColor;
#ifndef OIT_ALPHA_ONLY
layout(location = 3) in vec4 lightMapColor;
layout(location = 4) in vec4 overlayColor;
#endif
layout(location = 5) in vec2 texCoord0;
#ifdef GLINT
layout(location = 6) in vec2 texCoordGlint;
#endif
layout(location = 7) in vec4 rawColor;
layout(location = 8) in vec3 vfxPosition;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

#ifndef OIT_ALPHA_ONLY
vec4 calculateFinalColor(vec4 color) {
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;

    #ifdef GLINT
    vec4 glintColor = GlintAlpha * texture(GlintSampler, texCoordGlint);// Glint color modulator?
    // Matches BlendFuntion.GLINT
    color.rgb += glintColor.rgb * glintColor.rgb;
    #endif

    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif

    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
}
#endif

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

// Forge palette: cold iron, deep red, orange, yellow, white-hot.
vec3 forge(float h) {
    vec3 c = mix(vec3(.07, .03, .03), vec3(.58, .05, .04), smoothstep(.0, .3, h));
    c = mix(c, vec3(1.0, .42, .08), smoothstep(.3, .6, h));
    c = mix(c, vec3(1.0, .80, .36), smoothstep(.6, .82, h));
    return mix(c, vec3(1.0, .97, .88), smoothstep(.82, 1.0, h));
}

// 7..9 heat cut: a smooth self-lit blade of heat, not pixel steps. R is a proximity field around the blade path:
// above .64 is the blade itself, below it a soft glow. The white-hot head runs along flow with p (landing within
// two ticks); behind it the blade cools through yellow and orange to red, tears into flickering embers, and the
// glow around it fades with it. GameTime keeps the embers moving every frame between server ticks.
vec4 heatCut(vec4 data, float kind, float p) {
    float field = data.r, flow = data.g, across = data.b;
    float t = GameTime * 1200.0;
    float hot = kind < 7.5 ? 1.0 : (kind < 8.5 ? .82 : .64);
    float head = 1.3 * (1.0 - pow(1.0 - p, 5.0));
    float behind = head - flow;
    float lit = step(0.0, behind);
    float trail = clamp(1.0 - behind / 1.0, 0.0, 1.0) * lit;     // 1 at the head, 0 a full blade length behind
    float fade = 1.0 - smoothstep(.55, 1.0, p);
    float core = smoothstep(.6, .74, field);
    float ember = fbm(vec2(flow * 14.0 - t * 2.2, across * 9.0 + t * .6));
    float sparks = smoothstep(.72, .9, fbm(vec2(flow * 40.0 - t * 6.0, across * 30.0)));
    // The blade: solid near the head, breaking into embers as it cools.
    float blade = core * lit * smoothstep(.15, .5, ember * .8 + trail * .9) * fade;
    float headFlash = smoothstep(.86, 1.0, trail) * lit;
    float heat = clamp((.25 + .7 * trail + .3 * headFlash) * hot + sparks * .3 * (1.0 - trail), 0.0, 1.0);
    vec3 bladeColor = forge(heat) * (1.0 + .35 * headFlash);
    // The glow: soft and wide, hottest just behind the head, fading out with the cut.
    float glow = field * field * (.35 + .65 * trail) * lit * fade * (.7 + .3 * ember);
    vec3 glowColor = forge(.42 + .4 * trail * hot);
    float glowAlpha = clamp(glow * (.55 + .6 * headFlash), 0.0, .85);
    float alpha = blade + glowAlpha * (1.0 - blade);
    if (alpha < .004) return vec4(0.0);
    vec3 rgb = (bladeColor * blade + glowColor * glowAlpha * (1.0 - blade)) / alpha;
    return vec4(min(rgb, vec3(1.0)), clamp(alpha, 0.0, 1.0));
}

// 桜牙獣 (marker 79): the same shapes in sakura: deep plum body, pink, pale blossom, a white-pink core.
vec3 sakuraRamp(float h) {
    vec3 c = mix(vec3(.14, .03, .09), vec3(.78, .20, .47), smoothstep(.0, .35, h));
    c = mix(c, vec3(1.0, .62, .80), smoothstep(.35, .7, h));
    return mix(c, vec3(1.0, .94, .97), smoothstep(.7, 1.0, h));
}

vec4 shadeVfx(vec4 data, float kind, float p, bool sakura) {
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
        heat = mask * (.95 - .55 * p) + edge;
        alpha = step(threshold, v) * mask * (1.0 - .4 * p);
    } else if (kind < 5.5) {                           // 5 wall: dust rising off the ground in a curtain
        float rise = fbm(vec2(flow * 18.0, across * 2.5 - t * 3.0));
        heat = mask * rise * (1.0 - across * .6) + .2 * mask;
        alpha = mask * smoothstep(.25, .65, rise + (1.0 - across) * .3) * (1.0 - across) * (1.0 - smoothstep(.55, 1.0, p));
    } else {                                           // 6 smoke: a dark billow behind the bright dust, lit at its rim
        float threshold = p * 1.1;
        float v = mask * .7 + n * .5;
        float edge = 1.0 - smoothstep(0.0, .15, v - threshold);
        heat = mask * .18 + edge * .55 + n * .1;
        alpha = step(threshold, v) * mask * .85;
    }
    float h = clamp(heat, 0.0, 1.0);
    return vec4(sakura ? sakuraRamp(h) : ramp(h), clamp(alpha, 0.0, 1.0));
}

float vfxHash(vec3 cell) {
    return fract(sin(dot(cell, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

#ifndef OIT_ALPHA_ONLY
vec4 calculateUnlitColor(vec4 color) {
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif

    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
}
#endif

void finishUnlit(vec4 color) {
    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateUnlitColor(color);
    #endif
}

vec3 floatHigh(float f) {
    uint u = floatBitsToUint(f);
    return vec3(float((u >> 24) & 255u), float((u >> 16) & 255u), float((u >> 8) & 255u));
}

// One pixel of the light probe row (see the header), as 0..255 values; negative = not this probe's pixel.
vec3 lightProbePixel(int x, int slot, int code, int palette) {
    if (x == 0) {
        // Which depth convention the client uses: compare this fragment's depth with the probe's NDC z (item.vsh).
        vec4 near = ProjMat * vec4(0.0, 0.0, -0.2, 1.0);
        float z = near.z / near.w;
        return vec3(83.0, 76.0, abs(gl_FragCoord.z - z) < abs(gl_FragCoord.z - (z * .5 + .5)) ? 1.0 : 2.0);
    }
    if (x <= 32) {
        mat4 inv = inverse(ProjMat);
        int i = (x - 1) >> 1;
        float f = inv[i >> 2][i & 3];
        return (x & 1) == 1 ? floatHigh(f) : vec3(float(floatBitsToUint(f) & 255u), 0.0, 0.0);
    }
    int local = x - 33 - slot * 8;
    if (local < 0 || local > 7) return vec3(-1.0);
    if (local == 7) return vec3(170.0, float(slot), 85.0);
    if (local == 6) return vec3(float(2 + (code & 15)) * 8.0, rawColor.r * 255.0, float(palette));
    vec3 view = (ModelViewMat * vec4(vfxPosition, 1.0)).xyz;
    float f = view[local >> 1];
    return (local & 1) == 0 ? floatHigh(f) : vec3(float(floatBitsToUint(f) & 255u), 0.0, 0.0);
}

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    int markerB = int(rawColor.b * 255.0 + .5);
    int kind = int(rawColor.g * 255.0 + .5);
    if (markerB >= 80 && markerB <= 83) {
        ivec3 t = ivec3(color.rgb * 255.0 + .5);
        vec3 px = lightProbePixel(int(gl_FragCoord.x), kind >> 4, kind, markerB - 80);
        if (t != ivec3(83, 76, 49) || px.r < 0.0) {
            discard;
        }
        #ifdef OIT_ALPHA_ONLY
        discard;
        #else
        fragColor = vec4(px / 255.0, 1.0);
        #endif
        return;
    }
    if ((markerB == 77 || markerB == 79) && kind >= 1 && kind <= (markerB == 77 ? 9 : 6)) {
        vec4 fx = markerB == 77 && kind >= 7 ? heatCut(color, float(kind), rawColor.r)
            : shadeVfx(color, float(kind), rawColor.r, markerB == 79);
        if (fx.a < .01) {
            discard;
        }
        finishUnlit(fx);
        return;
    }
    if (markerB == 78 && kind >= 100 && kind <= 200) {
        float fade = rawColor.r;
        float heat = float(kind - 100) / 100.0;
        float along = color.g, across = color.b, mask = color.r;
        vec3 core = vec3(1.0, .93, .96), hot = vec3(1.0, .58, .77), deep = vec3(.78, .22, .50);
        vec4 fx;
        if (along < .2) {                                  // the petal itself
            fx = vec4(mix(mix(hot, core, .55 + .45 * heat), hot, across * .5), mask * min(1.0, fade * 1.4));
        } else {                                           // its ribbon, fading behind it
            float t = (along - .2) / .8;
            float shimmer = .85 + .15 * sin(GameTime * 24000.0 * .8 + along * 30.0);
            vec3 c = mix(mix(hot, core, heat * (1.0 - t)), deep, t * t);
            fx = vec4(c * (1.1 - .4 * t), mask * pow(1.0 - t, 1.3) * .8 * fade * shimmer);
        }
        if (fx.a < .01) {
            discard;
        }
        finishUnlit(fx * vec4(ColorModulator.rgb, ColorModulator.a));
        return;
    }
    int marker = int(color.a * 255.0 + 0.5);
    if (marker == 251) {
        // 黒翳竜 second form (shade_wyrm_frenzy.png, scripts/build_boss_shade_wyrm.py): the glowing veins are self-lit,
        // breathing slowly, while the rest of the body takes the world's light as usual.
        float n = vfxHash(floor((vfxPosition + CameraOffset) * 2.0));
        float breathe = 0.9 + 0.1 * sin(GameTime * 24000.0 * 0.35 + n * 6.2832);
        finishUnlit(vec4(min(color.rgb * vertexColor.rgb * breathe * 1.15, vec3(1.0)), 1.0) * ColorModulator);
        return;
    }
    if (marker == 253 || marker == 121 || marker == 65) {
        // Camera-relative, so the cells drift slightly as the camera moves: reads as heat shimmer.
        float n = vfxHash(floor((vfxPosition + CameraOffset) * 8.0));
        float t = GameTime * 24000.0;
        float flicker = 1.0 + 0.12 * sin(t * 1.9 + n * 6.2832) + 0.06 * sin(t * 5.3 + n * 17.0);
        vec3 rgb = color.rgb * flicker;
        float alpha = 1.0;
        if (marker != 253) {
            // Halo: pushed toward bright orange and up in strength, pulsing a little: the bloom the references have.
            rgb = mix(rgb, vec3(1.0, 0.56, 0.24), 0.35) * 1.45;
            alpha = (marker == 121 ? 0.6 : 0.34) * (0.85 + 0.15 * sin(t * 2.3 + n * 6.2832));
        }
        vec4 lit = vec4(min(rgb, vec3(1.0)), alpha) * ColorModulator;
        finishUnlit(lit);
        return;
    }
    ivec4 texel = ivec4(color * 255.0 + 0.5);
    if (texel.a >= 200 && texel.a <= 215 && (texel.r & 3) == 1 && (texel.g & 3) == 2 && (texel.b & 3) == 3) {
        float phase = float(texel.a - 200) / 15.0;
        float t = GameTime * 1200.0;                                   // seconds
        // Two waves of light run from the shoulders to the tail tip (2.2 s apart), a slow breath under them.
        float d1 = abs(fract(t * .45 - phase + .5) - .5);
        float d2 = abs(fract(t * .45 - phase) - .5);
        float band = exp(-d1 * d1 * 140.0) + .45 * exp(-d2 * d2 * 260.0);
        float breath = .9 + .1 * sin(t * 2.1);
        vec3 rgb = color.rgb * breath * 1.08;
        rgb = mix(rgb, vec3(1.0, .90, .95), clamp(band * .8, 0.0, .85));
        finishUnlit(vec4(min(rgb, vec3(1.0)), 1.0) * ColorModulator);
        return;
    }
    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif

    color *= vertexColor * ColorModulator;

    #ifdef GLINT
    color.a = max(color.a, GlintAlpha);
    #endif

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
