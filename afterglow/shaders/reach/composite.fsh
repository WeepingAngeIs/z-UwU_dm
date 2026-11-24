#version 330 compatibility
/* RENDERTARGETS: 0 */
#include "/lib/options.glsl"
in vec2 uv;
layout(location=0) out vec4 outColor;
uniform sampler2D colortex0,colortex1,depthtex0;
uniform float near,far,viewWidth,viewHeight,rainStrength;
uniform vec3 fogColor;
const bool colortex1Clear=true;
const vec4 colortex1ClearColor=vec4(0.0);
const bool colortex1MipmapEnabled=true;
float zMeters(float d) { return (near*far)/(far-d*(far-near)); }
void main() {
    vec4 base=texture(colortex0,uv);
    outColor=base;
    vec2 screen=uv-0.5;
    float aspect=viewWidth/viewHeight;
    float center=length(screen*vec2(aspect,1.0));
    if(center<=0.045) return;
    float depth=texture(depthtex0,uv).r;
    float z=zMeters(depth);
    float edge=smoothstep(0.58,0.96,2.0*max(abs(screen.x),abs(screen.y)));
    if(depth<1.0 && edge>0.0 && z>24.0) {
        vec2 stepUV=vec2(1.0/viewWidth,1.0/viewHeight)*float(AFTERGLOW_QUALITY);
        float left=zMeters(texture(depthtex0,uv-vec2(stepUV.x,0.0)).r);
        float right=zMeters(texture(depthtex0,uv+vec2(stepUV.x,0.0)).r);
        float coverage=1.0-smoothstep(0.5,2.0,max(abs(z-left),abs(z-right)));
        outColor.rgb=mix(base.rgb,fogColor,(AFTERGLOW_QUALITY==1?0.08:0.14)*edge*coverage*smoothstep(24.0,60.0,z));
    }
    if(rainStrength<=0.001) return;
    float level=max(1.0,log2(viewHeight*(AFTERGLOW_QUALITY==1?0.025:0.045)));
    vec2 spread=vec2(0.0008,-0.0025);
    vec4 cloud=textureLod(colortex1,uv+spread,level);
#if AFTERGLOW_QUALITY == 2
    cloud=mix(cloud,textureLod(colortex1,uv-spread,level-0.8),0.35);
#endif
    if(cloud.a<0.001 || z+1.0<20.0*cloud.b/cloud.a) return;
    float outside=1.0-clamp(textureLod(colortex1,uv,0.0).a,0.0,1.0);
    vec3 halo=cloud.r*vec3(1.0,0.78,0.74)+cloud.g*vec3(0.48,0.81,1.0);
    outColor.rgb+=(1.0-outColor.rgb)*min(halo,vec3(0.34))*outside*rainStrength*smoothstep(0.045,0.085,center);
}
