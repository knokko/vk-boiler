#version 450

layout(push_constant) uniform PushConstants {
	vec2 offset;
};

vec2 positions[] = { vec2(-0.1, 0.1), vec2(0.0, 0.0), vec2(0.1, 0.1) };

void main() {
	vec2 position = positions[gl_VertexIndex];
	gl_Position = vec4(position + offset, 0.0, 1.0);
}
