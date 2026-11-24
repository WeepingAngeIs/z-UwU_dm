#version 330 compatibility
#include "/lib/options.glsl"
uniform int renderStage,moonPhase;
uniform vec3 moonPosition;
uniform mat4 gbufferProjection;
out vec2 uv;
out vec4 tint;
void main() {
    uv=(gl_TextureMatrix[0]*gl_MultiTexCoord0).xy;
    gl_Position=ftransform();
#if REACH_MOON != 0
    if(renderStage==MC_RENDER_STAGE_MOON) {
        vec2 tile=uv*vec2(4.0,2.0)-vec2(float(moonPhase%4),float(moonPhase/4));
        vec2 across=vec2(tile.x-0.5,0.5-tile.y);
        vec3 position=moonPosition+vec3(across*0.68*length(moonPosition),0.0);
        gl_Position=gbufferProjection*vec4(position,1.0);
    }
#endif
    tint=gl_Color;
}
