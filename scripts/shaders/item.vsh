#version 330

// ProjectS combat VFX: vanilla 26.2 core/item, plus two values for item.fsh. rawColor is the item tint untouched
// by lighting (the 黒翳竜 VFX parameters, scripts/build_boss_vfx.py); vfxPosition is the camera-relative position
// for the warrior heat shimmer (scripts/build_warrior_iron_vfx.py). Rendering is otherwise the vanilla vertex stage.

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec4 rawColor;

out vec2 texCoord0;
out vec3 vfxPosition;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);

    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color);
    lightMapColor = sample_lightmap(Sampler2, UV2);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    rawColor = Color;

    texCoord0 = UV0;
    vfxPosition = Position;
}
