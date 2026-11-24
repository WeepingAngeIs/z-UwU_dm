in vec4 mc_Entity;
out vec2 uv;
out vec2 lightUV;
out vec4 tint;
out float eyeDepth;
flat out int material;
void main() {
    vec4 view=gl_ModelViewMatrix*gl_Vertex;
    gl_Position=gl_ProjectionMatrix*view;
    uv=(gl_TextureMatrix[0]*gl_MultiTexCoord0).xy;
    lightUV=(gl_TextureMatrix[1]*gl_MultiTexCoord1).xy;
    tint=gl_Color;
    material=int(mc_Entity.x+0.5);
    eyeDepth=length(view.xyz);
}
