#version 330
#extension GL_ARB_separate_shader_objects : require

// ProjectS night light (vanilla 26.3 core/lightmap override, server resource pack only; no client mod).
// The lightmap is the 16x16 table the game multiplies every block face with: x = block light level, y = sky light level.
// Same inputs and the same last steps as vanilla (night vision, darkness, boss darkening, the brightness slider);
// what changes is the colour of the two lights, so houses read at night the way 藍's reference builds do:
// - lamps, fire and lit windows are warm amber, with a paler hot core right next to the source;
// - moonlight is a cool blue instead of vanilla's grey, a little darker, so the warm pools stand out;
// - the two lights are combined like screen blending, not added and clipped, so a lamp under the sky stays soft.
// By day (SkyFactor near 1) the moon tint is gone and the sky matches vanilla; lamp light indoors stays a little warmer.
// Preview with the real shader text: the house pages in refs/build/tools/ (viewer.js runs this file in WebGL2).

layout(std140) uniform LightmapInfo {
    float SkyFactor;
    float BlockFactor;
    float NightVisionFactor;
    float DarknessScale;
    float BossOverlayWorldDarkeningFactor;
    float BrightnessFactor;
    vec3 BlockLightTint;
    vec3 SkyLightColor;
    vec3 AmbientColor;
    vec3 NightVisionColor;
} lightmapInfo;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

float get_brightness(float level) {
    return level / (4.0 - 3.0 * level);
}

vec3 notGamma(vec3 color) {
    float maxComponent = max(max(color.x, color.y), color.z);
    float maxInverted = 1.0 - maxComponent;
    float maxScaled = 1.0 - maxInverted * maxInverted * maxInverted * maxInverted;
    return color * (maxScaled / max(maxComponent, 1e-4));
}

const vec3 LAMP = vec3(1.00, 0.56, 0.24);      // lantern / fire amber
const vec3 LAMP_CORE = vec3(1.00, 0.82, 0.58); // right next to the source, still warm
const vec3 MOON = vec3(0.62, 0.74, 1.10);      // cool moonlight

void main() {
    float block_level = floor(texCoord.x * 16.0) / 15.0;
    float sky_level = floor(texCoord.y * 16.0) / 15.0;

    // 0 by day .. 1 at midnight (vanilla SkyFactor: about 1.0 by day, about 0.24 at night).
    float night = 1.0 - smoothstep(0.30, 0.85, lightmapInfo.SkyFactor);

    // Sky: vanilla brightness, the colour moves to blue at night and dims a little.
    float sky_brightness = get_brightness(sky_level) * lightmapInfo.SkyFactor;
    vec3 skyColor = lightmapInfo.SkyLightColor * mix(vec3(1.0), MOON, night) * mix(1.0, 0.95, night);

    vec3 nightVisionColor = lightmapInfo.NightVisionColor * lightmapInfo.NightVisionFactor;
    vec3 color = max(lightmapInfo.AmbientColor, nightVisionColor) + skyColor * sky_brightness;

    // Block light: vanilla curve with a slightly longer tail, so a lamp lights the wall around it, not one block.
    float block_brightness = mix(get_brightness(block_level), pow(block_level, 1.7), 0.35 + 0.25 * night)
        * lightmapInfo.BlockFactor;
    vec3 vanillaBlock = mix(lightmapInfo.BlockLightTint, vec3(1.0), 0.9 * (2.0 * block_level - 1.0) * (2.0 * block_level - 1.0));
    vec3 warm = mix(LAMP, LAMP_CORE, smoothstep(0.75, 1.0, block_level));
    // Brightness is compressed smoothly (1 - e^-x) instead of clipped, so the amber survives right next to a lamp.
    float block_light = 1.0 - exp(-2.3 * block_brightness);
    vec3 blockColor = mix(vanillaBlock, warm, mix(0.25, 0.9, night)) * mix(min(block_brightness, 1.0), block_light, night);

    // Screen-like blend: keeps the warm pool soft where moonlight already lights the wall.
    color = clamp(color, 0.0, 1.0);
    blockColor = clamp(blockColor, 0.0, 1.0);
    color = 1.0 - (1.0 - color) * (1.0 - blockColor);

    color = mix(color, color * vec3(0.7, 0.6, 0.6), lightmapInfo.BossOverlayWorldDarkeningFactor);
    color = color - vec3(lightmapInfo.DarknessScale);
    color = clamp(color, 0.0, 1.0);
    color = mix(color, notGamma(color), lightmapInfo.BrightnessFactor);

    fragColor = vec4(color, 1.0);
}
