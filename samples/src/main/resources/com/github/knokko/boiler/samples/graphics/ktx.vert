#version 450

vec2 positions[] = {
		vec2(-0.8, -0.8), vec2(-0.3, -0.8), vec2(-0.3, -0.3), vec2(-0.3, -0.3), vec2(-0.8, -0.3), vec2(-0.8, -0.8)
};

void main() {
	gl_Position = vec4(positions[gl_VertexIndex], 0.0, 1.0);
}
