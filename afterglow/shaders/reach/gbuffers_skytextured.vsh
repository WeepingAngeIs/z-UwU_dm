#version 330 compatibility
#include "/lib/options.glsl"
uniform int renderStage,moonPhase;
flat out vec4 moonClip;
out vec2 uv;
out vec4 tint;
void main() {
    uv=(gl_TextureMatrix[0]*gl_MultiTexCoord0).xy;
    gl_Position=ftransform();
    moonClip=vec4(0.0);
#if REACH_MOON != 0
    if(renderStage==MC_RENDER_STAGE_MOON) {
        // Keep the game's visible face and celestial transform, even at the zenith.
        moonClip=gl_ModelViewProjectionMatrix*vec4(0.0,gl_Vertex.y,0.0,1.0);
        gl_Position=gl_ModelViewProjectionMatrix*vec4(gl_Vertex.xyz*vec3(3.0,1.0,3.0),1.0);
    }
#endif
    tint=gl_Color;
}
