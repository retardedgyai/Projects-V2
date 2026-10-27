#version 330
#extension GL_ARB_separate_shader_objects : require

// ProjectS combat VFX: vanilla 26.3 core/item, plus two values for item.fsh. rawColor is the item tint untouched
// by lighting (the 黒翳竜 VFX parameters, scripts/build_boss_vfx.py); vfxPosition is the camera-relative position
// for the warrior heat shimmer (scripts/build_warrior_iron_vfx.py). Rendering is otherwise the vanilla vertex stage.
// 26.3 needs an explicit location on every in/out; 7 and 8 follow vanilla's last (6, texCoordGlint).
// Point lights (SakuraLight.kt, scripts/shaders/post/sakura_light.fsh): an item tinted blue 80..83 is a light probe. It is
// not drawn where it stands: its quad moves onto the screen's bottom pixel row, where item.fsh writes the light's data
// for the post effect, which lights the scene and hides the row again.
// The greatsword swing (CoreArmamentPresentation.shaderSwing, scripts/class_armament_swing.py): a held sword tinted blue
// 84..105 is moved here, in the first-person hand only: back to the sword's own space through its held resting pose,
// then placed by the beat's keys at this frame's time, so the swing runs at the screen's frame rate. Elsewhere (other
// players, F5, the hotbar) it is drawn as the plain held sword, its tint dropped. The two arms that come with the
// swing (Better Combat's first person shows them) are tinted the same, marked 106..127 (right) and 128..149 (left), and
// each is placed by its own keys. Beats 1..3 are the normal attacks; 4 and 5 are 体当たり's charge and impact; 6 and 7
// are 跳び叩き's rise and slam; 8 is 押し払い; 9, 10 and 11 are 切っ先薙ぎ's wind-up, cut and bite. Big swords are 84..94
// (beat 1..11), the T1 sprite 95..105.

#include <minecraft:light.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <minecraft:greatsword_swing.glsl>

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

// part: 0 the sword, 1 the right arm, 2 the left arm (the tracks are generated into greatsword_swing.glsl)
void swingSegment(int beat, bool plain, int part, float u, out SwingSegment g) {
    if (part == 1) {
        if (beat == 1) swingTrack_RARM_1(u, g); else if (beat == 2) swingTrack_RARM_2(u, g); else if (beat == 3) swingTrack_RARM_3(u, g);
        else if (beat == 4) swingTrack_RARM_4(u, g); else if (beat == 5) swingTrack_RARM_5(u, g); else if (beat == 6) swingTrack_RARM_6(u, g);
        else if (beat == 7) swingTrack_RARM_7(u, g); else if (beat == 8) swingTrack_RARM_8(u, g); else if (beat == 9) swingTrack_RARM_9(u, g);
        else if (beat == 10) swingTrack_RARM_10(u, g); else swingTrack_RARM_11(u, g);
    } else if (part == 2) {
        if (beat == 1) swingTrack_LARM_1(u, g); else if (beat == 2) swingTrack_LARM_2(u, g); else if (beat == 3) swingTrack_LARM_3(u, g);
        else if (beat == 4) swingTrack_LARM_4(u, g); else if (beat == 5) swingTrack_LARM_5(u, g); else if (beat == 6) swingTrack_LARM_6(u, g);
        else if (beat == 7) swingTrack_LARM_7(u, g); else if (beat == 8) swingTrack_LARM_8(u, g); else if (beat == 9) swingTrack_LARM_9(u, g);
        else if (beat == 10) swingTrack_LARM_10(u, g); else swingTrack_LARM_11(u, g);
    } else if (plain) {
        if (beat == 1) swingTrack_PLAIN_1(u, g); else if (beat == 2) swingTrack_PLAIN_2(u, g); else if (beat == 3) swingTrack_PLAIN_3(u, g);
        else if (beat == 4) swingTrack_PLAIN_4(u, g); else if (beat == 5) swingTrack_PLAIN_5(u, g); else if (beat == 6) swingTrack_PLAIN_6(u, g);
        else if (beat == 7) swingTrack_PLAIN_7(u, g); else if (beat == 8) swingTrack_PLAIN_8(u, g); else if (beat == 9) swingTrack_PLAIN_9(u, g);
        else if (beat == 10) swingTrack_PLAIN_10(u, g); else swingTrack_PLAIN_11(u, g);
    } else {
        if (beat == 1) swingTrack_BIG_1(u, g); else if (beat == 2) swingTrack_BIG_2(u, g); else if (beat == 3) swingTrack_BIG_3(u, g);
        else if (beat == 4) swingTrack_BIG_4(u, g); else if (beat == 5) swingTrack_BIG_5(u, g); else if (beat == 6) swingTrack_BIG_6(u, g);
        else if (beat == 7) swingTrack_BIG_7(u, g); else if (beat == 8) swingTrack_BIG_8(u, g); else if (beat == 9) swingTrack_BIG_9(u, g);
        else if (beat == 10) swingTrack_BIG_10(u, g); else swingTrack_BIG_11(u, g);
    }
}

float swingEase(int kind, float w) {
    if (kind == 1) return w * w;                                   // in: gathering speed
    if (kind == 2) return 1.0 - (1.0 - w) * (1.0 - w);             // out: coming to rest
    if (kind == 3) return w * w * (3.0 - 2.0 * w);
    return w;
}

vec4 swingSlerp(vec4 a, vec4 b, float w) {
    if (dot(a, b) < 0.0) b = -b;
    float c = clamp(dot(a, b), -1.0, 1.0);
    if (c > .9995) return normalize(mix(a, b, w));
    float ang = acos(c);
    return (sin((1.0 - w) * ang) * a + sin(w * ang) * b) / sin(ang);
}

vec3 swingRotate(vec4 q, vec3 v) {
    return v + 2.0 * cross(q.xyz, cross(q.xyz, v) + q.w * v);
}

// The swing pose at this frame: hand position, rotation of the sword's axes, scale.
void swingPose(int beat, bool plain, int part, float u, out vec3 hand, out vec4 rot, out float scale) {
    SwingSegment g;
    swingSegment(beat, plain, part, u, g);
    float w = g.u1 <= g.u0 ? 1.0 : swingEase(g.ease, clamp((u - g.u0) / (g.u1 - g.u0), 0.0, 1.0));
    hand = mix(g.h0, g.h1, w);
    rot = swingSlerp(g.q0, g.q1, w);
    scale = mix(g.s0, g.s1, w);
}

void main() {
    vec3 position = Position;
    vec3 normal = Normal;
    vec4 tint = Color;
    bool swung = false;
    bool hidden = false;
    int swingMarker = int(Color.b * 255.0 + .5);
    int swingPart = swingMarker >= 128 && swingMarker <= 149 ? 2 : swingMarker >= 106 && swingMarker <= 127 ? 1 : 0;
    swingMarker -= swingPart * 22;
    if (swingMarker >= 84 && swingMarker <= 105) {
        tint = vec4(1.0, 1.0, 1.0, Color.a);
        // The first-person hand: a perspective projection and the vertex within reach of the eye (26.3 draws the hand
        // with a model-view matrix of its own, so everything here is worked in view space and carried back).
        mat4 mv = ModelViewMat;
        vec3 view = (mv * vec4(Position, 1.0)).xyz;
        bool hand = ProjMat[2][3] < -.5 && length(view) < 2.0;
        if (hand) {
            bool plain = swingMarker >= 95;
            int beat = swingMarker - (plain ? 94 : 83);
            float swingLength = max(Color.r * 255.0, 1.0);
            float elapsed = mod(GameTime * 24000.0 - Color.g * 255.0 + 512.0, 256.0);
            float u = clamp(elapsed / swingLength, 0.0, 1.0);
            vec3 restT = plain ? SWING_PLAIN_T : SWING_BIG_T;
            mat3 restR = plain ? SWING_PLAIN_R : SWING_BIG_R;
            float restS = plain ? SWING_PLAIN_S : SWING_BIG_S;
            vec3 grip = swingPart > 0 ? SWING_ARM_ORIGIN : plain ? SWING_PLAIN_GRIP : SWING_BIG_GRIP;
            // Back to the sword's own space, relative to the hand on the handle (blocks).
            vec3 local = transpose(restR) * (view - SWING_ARM - restT) / restS + vec3(.5) - grip;
            vec3 h; vec4 q; float s;
            swingPose(beat, plain, swingPart, u, h, q, s);
            vec3 moved = h + swingRotate(q, local * s);
            position = (inverse(mv) * vec4(moved, 1.0)).xyz;
            normal = swingRotate(q, transpose(restR) * normalize(mat3(mv) * Normal));
            // Light the face the camera sees (a face turned by the swing would otherwise keep its old shading side).
            if (dot(normal, -moved) < 0.0) normal = -normal;
            swung = true;
        } else if (swingPart > 0) {
            hidden = true;   // the arms are only for the first-person hand: elsewhere the held sword is drawn alone
        }
    }
    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);
    if (hidden) gl_Position = vec4(0.0, 0.0, 2.0, 1.0);   // beyond the far plane: the triangle is clipped away

    #ifndef OIT_ALPHA_ONLY
    sphericalVertexDistance = fog_spherical_distance(position);
    cylindricalVertexDistance = fog_cylindrical_distance(position);
    #endif
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, tint);
    if (swung) {
        // The swung sword's normal is in view space, not the lights' space: shade it simply, lit from above-front.
        // Close to vanilla's hand lighting (faces toward the camera near full bright, edges a little darker).
        float lit = .82 + .18 * abs(dot(normalize(normal), normalize(vec3(.25, .45, .85))));
        vertexColor = vec4(vec3(lit), tint.a);
    }
    rawColor = Color;
    vfxPosition = position;
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
