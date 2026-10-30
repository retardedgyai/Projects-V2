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
    vec3 c = texture(InSampler, texCoord).rgb;
    // only the fire's and the ice's own colours glow, never the sky, the fog, clouds, wood or sand (the effect is on
    // for the whole fight: 2026-10-28 the Creator, "a flash, like Valorant"; 10-31 "the sky and the fog glow too, it
    // hurts my eyes"). Fire: red high and well above green and blue (orange, not planks or sand). Ice: the cyan, green
    // as high as blue and red low (the sky and the fog are bluer than green, so they stay out).
    float warm = smoothstep(Threshold + .1, Threshold + .3, c.r) * smoothstep(.25, .4, c.r - c.g) * smoothstep(.4, .6, c.r - c.b);
    float cold = smoothstep(.8, .92, c.g) * smoothstep(-.06, -.01, c.g - c.b) * (1.0 - smoothstep(.4, .55, c.r));
    fragColor = vec4(c * max(warm, cold) * Strength, 1.0);
}
