#version 450

layout(location = 0) in vec2 textureCoordinates;

layout(push_constant) uniform PushConstants {
	layout(offset = 8) int imageIndex;
};

layout(set = 0, binding = 0) uniform texture2D images[4];
layout(set = 0, binding = 1) uniform sampler imageSampler;

layout(location = 0) out vec4 outColor;

void main() {
	outColor = texture(sampler2D(images[imageIndex], imageSampler), textureCoordinates);
}
