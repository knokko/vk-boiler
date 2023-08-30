#version 450

layout(push_constant) uniform PushConstants {
    float innerRadius;
    float outerRadius;
    float centerX;
    float centerY;
    int numArcVertices;
} pushConstants;

void main() {
    int triangleIndex = gl_VertexIndex / 3;
    int relativeVertexIndex = gl_VertexIndex % 3;
    int arcIndex = 2 * triangleIndex;
    float radius = pushConstants.innerRadius;

    if (arcIndex < pushConstants.numArcVertices) {
        if (relativeVertexIndex == 0) radius = pushConstants.outerRadius;
    } else {
        arcIndex -= pushConstants.numArcVertices;
        if (relativeVertexIndex != 0) radius = pushConstants.outerRadius;
        arcIndex += 1;
    }

    if (relativeVertexIndex == 1) arcIndex += 1;
    if (relativeVertexIndex == 2) arcIndex -= 1;

    float angle = radians(360.0) * arcIndex / pushConstants.numArcVertices;

    gl_Position = vec4(
        pushConstants.centerX + radius * cos(angle),
        pushConstants.centerY + radius * sin(angle),
        0.5, 1.0
    );
}
