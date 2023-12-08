#version 450

layout(location = 0) in vec3 inColor;

layout(location = 0) out vec4 outColor;

layout(push_constant) uniform PushConstants {
    bool dim;
} pushConstants;

void main() {
    float factor = pushConstants.dim ? 0.3 : 1.0;
    outColor = vec4(inColor * factor, 1.0);
}
