#version 330
#extension GL_ARB_separate_shader_objects : require

// The mage's glow (assets/projects/post_effect/mage_glow.json, on for a mage's whole fight), pass 1: keep the
// bright pixels of the mage's two palettes, the fire's (white-hot to orange: red high, blue well below it) and the
// ice's (white to cyan: green and blue both high, red not above them). Everything else goes black before the blur.
// The rest of the chain is the arena glow's (shade_glow.json): two box blurs and the same soft composite.

uniform sampler2D InSampler;

layout(std140) uniform GlowConfig {
    float Threshold;
    float Strength;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    // the 3 x 3 pixels round this one, averaged: a one-pixel ice ring or a far flame flickers in and out of the filter
    // as it moves over the pixel grid (the Creator 2026-10-31, "the ice still blinks"); averaged, it fades instead
    ivec2 size = textureSize(InSampler, 0);
    ivec2 at = ivec2(texCoord * vec2(size));
    vec3 c = vec3(0.0);
    for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) c += texelFetch(InSampler, clamp(at + ivec2(x, y), ivec2(0), size - 1), 0).rgb;
    c /= 9.0;
    // only the fire's and the ice's own colours glow, never the sky, the fog, clouds, wood or sand (the effect is on
    // for the whole fight: 2026-10-28 the Creator, "a flash, like Valorant"; 10-31 "the sky and the fog glow too, it
    // hurts my eyes"). Fire: red high and well above green and blue (orange, not planks or sand). Ice: the cyan, green
    // as high as blue and red low (the sky and the fog are bluer than green, so they stay out).
    float warm = smoothstep(Threshold + .1, Threshold + .3, c.r) * smoothstep(.25, .4, c.r - c.g) * smoothstep(.4, .6, c.r - c.b);
    float cold = smoothstep(.8, .92, c.g) * smoothstep(-.06, -.01, c.g - c.b) * (1.0 - smoothstep(.4, .55, c.r));
    fragColor = vec4(c * max(warm, cold) * Strength, 1.0);
}
