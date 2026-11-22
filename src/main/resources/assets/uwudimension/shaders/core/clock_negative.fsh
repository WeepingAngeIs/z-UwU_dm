#version 150
uniform sampler2D Scene;
uniform sampler2D SceneDepth;
uniform mat4 InvClip;
uniform vec2 LocalEye;
in vec2 uv;
out vec4 fragColor;
void main() {
    vec4 color = texture(Scene, uv);
    float depth = texture(SceneDepth, uv).r;
    if (depth < 0.9999999) {
        vec4 ray = InvClip * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
        vec2 chunk = floor((LocalEye + ray.xz / ray.w) / 16.0);
        if (abs(chunk.x) + abs(chunk.y) < 1.5) color.rgb = 1.0 - color.rgb;
    }
    fragColor = color;
}
