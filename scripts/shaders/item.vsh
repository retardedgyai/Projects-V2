#version 330
#extension GL_ARB_separate_shader_objects : require

// ProjectS combat VFX: vanilla 26.3 core/item, plus two values for item.fsh. rawColor is the item tint untouched
// by lighting (the 黒翳竜 VFX parameters, scripts/build_boss_vfx.py); vfxPosition is the camera-relative position
// for the warrior heat shimmer (scripts/build_warrior_iron_vfx.py). Rendering is otherwise the vanilla vertex stage.
// 26.3 needs an explicit location on every in/out; 7 and 8 follow vanilla's last (6, texCoordGlint).
// Point lights (SakuraLight.kt, scripts/shaders/post/sakura_light.fsh): an item tinted blue 80..83 is a light probe. It is
// not drawn where it stands: its quad moves onto the screen's bottom pixel row, where item.fsh writes the light's data
// for the post effect, which lights the scene and hides the row again.

#include <minecraft:light.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
#ifdef GLINT_SPECIAL
layout(location = 5) in vec2 UV3;
#endif
layout(location = 6) in vec3 Normal;

#ifndef OIT_ALPHA_ONLY
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
#endif
layout(location = 2) out vec4 vertexColor;
#ifndef OIT_ALPHA_ONLY
layout(location = 3) out vec4 lightMapColor;
layout(location = 4) out vec4 overlayColor;
#endif

layout(location = 5) out vec2 texCoord0;
#ifdef GLINT
layout(location = 6) out vec2 texCoordGlint;
#endif
layout(location = 7) out vec4 rawColor;
layout(location = 8) out vec3 vfxPosition;

const float LIGHT_PROBE_PIXELS = 97.0;   // item.fsh: header 33 + 8 lights x 8

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    #ifndef OIT_ALPHA_ONLY
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    #endif
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color);
    rawColor = Color;
    vfxPosition = Position;
    #ifndef OIT_ALPHA_ONLY
    lightMapColor = sample_lightmap(Sampler2, UV2);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    #endif

    texCoord0 = UV0;
    #ifdef GLINT
    #ifdef GLINT_SPECIAL
    texCoordGlint = (TextureMat * vec4(UV3, 0.0, 1.0)).xy;
    #else
    texCoordGlint = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
    #endif
    #endif

    int probe = int(Color.b * 255.0 + 0.5);
    if (probe >= 80 && probe <= 83) {
        // Corners in quad order; every second quad (the model's back face) winds the other way, so one of the two
        // survives back-face culling whichever way the item faces. Just in front of the near plane, so nothing hides it.
        int corner = gl_VertexIndex & 3;
        vec2 c = vec2(corner == 1 || corner == 2 ? 1.0 : 0.0, corner >= 2 ? 1.0 : 0.0);
        if (((gl_VertexIndex >> 2) & 1) == 1) c = c.yx;
        vec4 near = ProjMat * vec4(0.0, 0.0, -0.2, 1.0);
        if (!(near.w > 0.0)) near = vec4(0.0, 0.0, 0.0, 1.0);   // not a perspective projection: stay on screen
        vec2 ndc = -1.0 + 2.0 * c * vec2(LIGHT_PROBE_PIXELS, 1.0) / ScreenSize;
        gl_Position = vec4(ndc * near.w, near.z, near.w);
    }
}
