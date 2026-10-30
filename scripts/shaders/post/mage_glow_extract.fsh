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
    // only clearly coloured pixels glow: white clouds, the sky and pale blocks stay out (the effect is on for the
    // whole fight, so it must never light the scene by itself; 2026-10-28 the Creator: "a flash, like Valorant")
    float warm = smoothstep(Threshold + .05, Threshold + .3, c.r) * smoothstep(.28, .5, c.r - c.b);
    float cold = smoothstep(.7, .9, c.b) * smoothstep(.16, .32, c.b - c.r) * smoothstep(.6, .8, c.g);
    fragColor = vec4(c * max(warm, cold) * Strength, 1.0);
}
