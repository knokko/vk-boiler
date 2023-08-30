float bezier(float t, float p0, float p1, float p2, float p3) {
    return (1 - t) * (1 - t) * (1 - t) * p0 + 3 * (1 - t) * (1 - t) * t * p1 + 3 * (1 - t) * t * t * p2 + t * t * t * p3;
}

float applyBezier(float t, float height1, float height2, float height3, float height4) {
    float d2 = (height3 - height1) / 60.0;
    float d3 = (height4 - height2) / 60.0;
    float control2 = height2 + 10.0 * d2;
    float control3 = height3 - 10.0 * d3;

    return bezier(t, height2, control2, control3, height3);
}
