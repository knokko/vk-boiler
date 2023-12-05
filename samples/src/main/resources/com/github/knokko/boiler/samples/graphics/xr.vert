#version 450

#extension GL_EXT_multiview : require

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inColor;

layout(location = 0) out vec3 outColor;

layout(binding = 0) uniform Matrices {
    mat4 eyes[2];
} matrices;

void main() {
    gl_Position = matrices.eyes[gl_ViewIndex] * vec4(inPosition, 1.0);
    outColor = inColor;
}
