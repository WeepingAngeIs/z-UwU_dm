uniform int renderStage;
uniform vec3 skyColor,fogColor;
uniform float fogStart,fogEnd;
in vec4 passedColor;
in float skyDistance;
flat in int nativeFan;
layout(location=0) out vec4 outColor;
void main() {
    if(renderStage==MC_RENDER_STAGE_SKY) {
        float blend=smoothstep(fogStart,max(fogEnd,fogStart+0.001),skyDistance);
        outColor=vec4(nativeFan==1?mix(skyColor,fogColor,blend):fogColor,1.0);
    } else {
        outColor=passedColor;
    }
}
