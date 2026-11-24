#version 330 compatibility
/* RENDERTARGETS: 0 */
#include "/lib/options.glsl"
uniform sampler2D gtexture,petalMoon,duskMoon;
uniform int renderStage,moonPhase;
in vec2 uv;
in vec4 tint;
flat in vec4 moonClip;
uniform float viewWidth,viewHeight;
layout(location=0) out vec4 outColor;
void main() {
    vec4 texel=texture(gtexture,uv);
#if REACH_MOON != 0
    if(renderStage==MC_RENDER_STAGE_MOON) {
        vec2 center=(moonClip.xy/moonClip.w*0.5+0.5)*vec2(viewWidth,viewHeight);
        vec2 localUV=0.5+(gl_FragCoord.xy-center)*vec2(1.0,-1.0)/(0.55*viewHeight);
        if(any(lessThan(localUV,vec2(0))) || any(greaterThan(localUV,vec2(1)))) discard;
#if REACH_MOON == 1
        texel=texture(petalMoon,localUV);
#else
        texel=texture(duskMoon,localUV);
#endif
    }
#endif
    outColor=texel*tint;
}
