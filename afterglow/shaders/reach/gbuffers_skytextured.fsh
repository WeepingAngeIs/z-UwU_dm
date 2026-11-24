#version 330 compatibility
/* RENDERTARGETS: 0 */
#include "/lib/options.glsl"
uniform sampler2D gtexture,petalMoon,duskMoon;
uniform int renderStage,moonPhase;
in vec2 uv;
in vec4 tint;
layout(location=0) out vec4 outColor;
void main() {
    vec4 texel=texture(gtexture,uv);
#if REACH_MOON != 0
    if(renderStage==MC_RENDER_STAGE_MOON) {
        vec2 localUV=clamp(uv*vec2(4.0,2.0)-vec2(float(moonPhase%4),float(moonPhase/4)),0.0,1.0);
#if REACH_MOON == 1
        texel=texture(petalMoon,localUV);
#else
        texel=texture(duskMoon,localUV);
#endif
    }
#endif
    outColor=texel*tint;
}
