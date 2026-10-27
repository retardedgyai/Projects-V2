#version 330
#extension GL_ARB_separate_shader_objects : require

// The mage's glow (assets/projects/post_effect/mage_glow.json, on while a mage's spell is out), pass 1: keep the
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
    float warm = smoothstep(Threshold + .05, Threshold + .3, c.r) * clamp((c.r - c.b - .04) * 6.0, 0.0, 1.0);
    float cold = smoothstep(.78, .92, c.g) * smoothstep(.78, .92, c.b) * clamp((c.b - c.r * .9 + .1) * 4.0, 0.0, 1.0);
    fragColor = vec4(c * max(warm, cold) * Strength, 1.0);
}
