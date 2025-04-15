#version 450

#extension GL_EXT_nonuniform_qualifier : require

layout(location = 0) in flat int textureIndex;
layout(location = 1) in vec2 textureCoordinates;

layout(location = 0) out vec4 outColor;

layout(binding = 1) uniform sampler2D textures[3];

void main() {
	outColor = texture(nonuniformEXT(textures[textureIndex]), textureCoordinates);
}
