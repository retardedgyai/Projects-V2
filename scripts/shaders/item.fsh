#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec3 vfxPosition;

out vec4 fragColor;

// ProjectS combat VFX. Only texels with a marker alpha are touched (scripts/build_warrior_iron_vfx.py):
// 253 = heat, self-lit and flickering; 121 / 65 = near / far glow halo. Every other item renders as vanilla.
float vfxHash(vec3 cell) {
    return fract(sin(dot(cell, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    int marker = int(color.a * 255.0 + 0.5);
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
        fragColor = apply_fog(lit, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
        return;
    }
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

    color *= vertexColor * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
