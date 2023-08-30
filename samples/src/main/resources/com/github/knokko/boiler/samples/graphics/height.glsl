#include "bezier.glsl"

float computeHeight(vec2 realOffset, vec2 textureOffset, isampler2D heightMap) {
    vec2 textureCoordinates = textureOffset + realOffset / 108030.0;

    int u2 = int(textureCoordinates.x * 3601);
    float ut = textureCoordinates.x * 3601 - u2;
    int u1 = u2 - 1;
    if (u1 < 0) u1 = 0;
    int u3 = u2 + 1;
    int u4 = u3 + 1;
    if (u3 >= 3601) u3 = 3601 - 1;
    if (u4 >= 3601) u4 = 3601 - 1;

    int v2 = int(textureCoordinates.y * 3601);
    float vt = textureCoordinates.y * 3601 - v2;
    int v1 = v2 - 1;
    if (v1 < 0) v1 = 0;
    int v3 = v2 + 1;
    int v4 = v3 + 1;
    if (v3 >= 3601) v3 = 3601 - 1;
    if (v4 >= 3601) v4 = 3601 - 1;

    float height11 = texture(heightMap, ivec2(u1, v1)).r;
    float height12 = texture(heightMap, ivec2(u1, v2)).r;
    float height13 = texture(heightMap, ivec2(u1, v3)).r;
    float height14 = texture(heightMap, ivec2(u1, v4)).r;

    float height21 = texture(heightMap, ivec2(u2, v1)).r;
    float height22 = texture(heightMap, ivec2(u2, v2)).r;
    float height23 = texture(heightMap, ivec2(u2, v3)).r;
    float height24 = texture(heightMap, ivec2(u2, v4)).r;

    float height31 = texture(heightMap, ivec2(u3, v1)).r;
    float height32 = texture(heightMap, ivec2(u3, v2)).r;
    float height33 = texture(heightMap, ivec2(u3, v3)).r;
    float height34 = texture(heightMap, ivec2(u3, v4)).r;

    float height41 = texture(heightMap, ivec2(u4, v1)).r;
    float height42 = texture(heightMap, ivec2(u4, v2)).r;
    float height43 = texture(heightMap, ivec2(u4, v3)).r;
    float height44 = texture(heightMap, ivec2(u4, v4)).r;

    float heightU1 = applyBezier(vt, height11, height12, height13, height14);
    float heightU2 = applyBezier(vt, height21, height22, height23, height24);
    float heightU3 = applyBezier(vt, height31, height32, height33, height34);
    float heightU4 = applyBezier(vt, height41, height42, height43, height44);

    return applyBezier(ut, heightU1, heightU2, heightU3, heightU4);
}