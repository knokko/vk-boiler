#version 450

layout(location = 0) out vec3 passPosition;
layout(location = 1) out vec2 textureCoordinates;

layout(push_constant) uniform PushConstants {
    vec3 base;
    float quadSize;
    vec2 textureOffset;
    int numColumns;
} pushConstants;

layout(set = 0, binding = 0) uniform Camera {
    mat4 matrix;
} camera;
layout(set = 0, binding = 1) uniform isampler2D heightMap;

#include "height.glsl"

void main() {
    int quadIndex = gl_VertexIndex / 6;
    int cornerIndex = gl_VertexIndex % 6;
    int column = quadIndex % pushConstants.numColumns;
    int row = quadIndex / pushConstants.numColumns;

    if (cornerIndex > 0 && cornerIndex < 4) column += 1;
    if (cornerIndex < 2 || cornerIndex == 5) row += 1;

    float realOffsetX = column * pushConstants.quadSize;
    float realOffsetZ = row * pushConstants.quadSize;

    float realX = pushConstants.base.x + realOffsetX;
    float realZ = pushConstants.base.z + realOffsetZ;
    float horizontalDistance = sqrt(realX * realX + realZ * realZ);

    float height = computeHeight(
        vec2(realOffsetX, realOffsetZ), pushConstants.textureOffset, heightMap
    );
    vec3 worldPosition = pushConstants.base + vec3(realOffsetX, height, realOffsetZ);

    passPosition = worldPosition;
    gl_Position = camera.matrix * vec4(worldPosition, 1.0);

    textureCoordinates = pushConstants.textureOffset + vec2(realOffsetX, realOffsetZ) / 108030.0;
}
