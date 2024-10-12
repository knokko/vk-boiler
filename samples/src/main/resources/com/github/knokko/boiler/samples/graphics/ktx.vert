#version 450

layout(push_constant) uniform PushConstants {
	vec2 minCoords;
};

layout(location = 0) out vec2 textureCoordinates;

float size = 0.8f;
vec2 offsets[] = {
		vec2(0.0, 0.0), vec2(size, 0.0), vec2(size, size), vec2(size, size), vec2(0.0, size), vec2(0.0, 0.0)
};

void main() {
	gl_Position = vec4(minCoords + offsets[gl_VertexIndex], 0.0, 1.0);
	textureCoordinates = offsets[gl_VertexIndex] / size;
}
