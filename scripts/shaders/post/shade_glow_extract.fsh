#version 330
#extension GL_ARB_separate_shader_objects : require

// 黒翳竜 glow (assets/projects/post_effect/shade_glow.json), pass 1: keep only bright violet-pink pixels (the boss VFX
// palette and the violet-lit floor), not white lamps or green; everything else goes black before the blur.

uniform sampler2D InSampler;

layout(std140) uniform GlowConfig {
    float Threshold;
    float Strength;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    float lum = dot(c, vec3(0.299, 0.587, 0.114));
    float violet = clamp((c.b + c.r * 0.6 - c.g * 1.4) * 2.0, 0.0, 1.0);
    float k = smoothstep(Threshold, Threshold + 0.25, lum) * violet;
    fragColor = vec4(c * k * Strength, 1.0);
}
