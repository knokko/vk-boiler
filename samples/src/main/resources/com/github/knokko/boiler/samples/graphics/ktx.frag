#version 450

layout(set = 0, binding = 0) uniform texture2D images[4];
layout(set = 0, binding = 1) uniform sampler imageSampler;

layout(location = 0) out vec4 outColor;

void main() {
	//outColor = vec4(0.0, 1.0, 0.5, 1.0);
	outColor = texture(sampler2D(images[0], imageSampler), vec2(0.5, 0.5));
}
