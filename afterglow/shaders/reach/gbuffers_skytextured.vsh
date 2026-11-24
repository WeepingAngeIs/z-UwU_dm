#version 330 compatibility
out vec2 uv;
out vec4 tint;
void main() {
    uv=(gl_TextureMatrix[0]*gl_MultiTexCoord0).xy;
    gl_Position=ftransform();
    tint=gl_Color;
}
