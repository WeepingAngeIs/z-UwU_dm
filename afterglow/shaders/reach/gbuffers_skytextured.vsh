#version 330 compatibility
#include "/lib/options.glsl"
uniform int renderStage;
out vec2 uv;
out vec4 tint;
void main() {
    vec4 pos=gl_Vertex;
#if REACH_MOON != 0
    if(renderStage==MC_RENDER_STAGE_MOON) pos.xz*=1.65;
#endif
    gl_Position=gl_ModelViewProjectionMatrix*pos;
    uv=(gl_TextureMatrix[0]*gl_MultiTexCoord0).xy;
    tint=gl_Color;
}
