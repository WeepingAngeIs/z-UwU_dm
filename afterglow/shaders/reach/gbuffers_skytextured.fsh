#version 330 compatibility
/* RENDERTARGETS: 0 */
#include "/lib/options.glsl"
uniform sampler2D gtexture;
uniform int renderStage;
in vec2 uv;
in vec4 tint;
layout(location=0) out vec4 outColor;
void main() {
#if REACH_MOON != 0
    if(renderStage==MC_RENDER_STAGE_MOON) discard;
#endif
    outColor=texture(gtexture,uv)*tint;
}
