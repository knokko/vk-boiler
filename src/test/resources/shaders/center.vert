#version 450

vec2 positions[] = {
	vec2(-0.2, -0.2),
	vec2(0.2, -0.2),
	vec2(0.2, 0.2),
	vec2(0.2, 0.2),
	vec2(-0.2, 0.2),
	vec2(-0.2, -0.2)
};

void main() {
	vec2 position = positions[gl_VertexIndex];
	gl_Position = vec4(position, 0.0, 1.0);
}
