#version 450

#extension GL_EXT_multiview : require

layout(location = 0) in vec3 inPosition;
layout(location = 1) in int inTextureIndex;
layout(location = 2) in vec2 inTextureCoordinates;

layout(location = 0) out int outTextureIndex;
layout(location = 1) out vec2 outTextureCoordinates;

layout(binding = 0) uniform Matrices {
	mat4 eyes[2];
} matrices;

void main() {
	gl_Position = matrices.eyes[gl_ViewIndex] * vec4(inPosition, 1.0);
	outTextureIndex = inTextureIndex;
	outTextureCoordinates = vec2(inTextureCoordinates.x, 1.0 - inTextureCoordinates.y);
}
