#version 330
#extension GL_ARB_separate_shader_objects : require

// 黒翳竜 glow, last pass: the scene plus a tight and a wide blur of its violet highlights, added softly (a screen-like
// blend so bright areas do not blow out to white).

uniform sampler2D InSampler;
uniform sampler2D SmallSampler;
uniform sampler2D WideSampler;

layout(std140) uniform CompositeConfig {
    float SmallIntensity;
    float WideIntensity;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec3 base = texture(InSampler, texCoord).rgb;
    vec3 glow = texture(SmallSampler, texCoord).rgb * SmallIntensity + texture(WideSampler, texCoord).rgb * WideIntensity;
    glow = clamp(glow, 0.0, 1.0);
    fragColor = vec4(1.0 - (1.0 - base) * (1.0 - glow), 1.0);
}
