#version 450

layout(push_constant) uniform PushConstants {
	uint colorIndex;
} pushConstants;

layout(set = 0, binding = 0) readonly buffer ColorBuffer {
	vec4 colors[3];
} colorBuffer;

layout(location = 0) out vec4 outColor;

void main() {
	outColor = colorBuffer.colors[pushConstants.colorIndex];
}
