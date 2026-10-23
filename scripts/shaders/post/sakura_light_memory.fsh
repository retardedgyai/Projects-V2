#version 330
#extension GL_ARB_separate_shader_objects : require

// Point lights, first pass: the light data row (see sakura_light.fsh) as one 98x1 row. x 0..96 is this frame's probe
// row when there is one, else the remembered row (the persistent `memory` target, last frame's output); x 97 counts
// the frames since a fresh row (0 = this frame), so sakura_light.fsh stops using a stale one.

uniform sampler2D InSampler;
uniform sampler2D MemorySampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const int ROW_PIXELS = 97;

bool header(ivec2 p) {
    ivec3 h = ivec3(texelFetch(InSampler, p, 0).rgb * 255.0 + 0.5);
    return h.r == 83 && h.g == 76 && (h.b == 1 || h.b == 2);
}

void main() {
    ivec2 size = textureSize(InSampler, 0);
    int x = int(gl_FragCoord.x);
    int row = header(ivec2(0, 0)) ? 0 : (header(ivec2(0, size.y - 1)) ? size.y - 1 : -1);
    if (x < ROW_PIXELS) {
        fragColor = row >= 0 ? texelFetch(InSampler, ivec2(x, row), 0) : texelFetch(MemorySampler, ivec2(x, 0), 0);
        return;
    }
    float age = row >= 0 ? 0.0 : min(texelFetch(MemorySampler, ivec2(x, 0), 0).r * 255.0 + 1.5, 255.0);
    fragColor = vec4(floor(age) / 255.0, 0.0, 0.0, 1.0);
}
