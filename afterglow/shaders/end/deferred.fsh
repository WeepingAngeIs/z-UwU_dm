#version 330 compatibility
/* RENDERTARGETS: 0 */
in vec2 uv;
layout(location=0) out vec4 outColor;
uniform sampler2D colortex0, depthtex0;
uniform sampler2D endSky, endCos, endSin, endLocal;
uniform mat4 gbufferProjectionInverse, gbufferModelViewInverse;
uniform float frameTimeCounter, blindness, darknessFactor;
uniform int isEyeInWater;
const float TAU = 6.28318530718;
const vec2 sourceSize = vec2(952.0,724.0);
const vec2 centerPx = vec2(467.9,314.3);
vec3 skyTex(vec2 packedUV, vec2 phase) {
    vec3 original = texture(endSky,packedUV).rgb;
    vec3 c = (texture(endCos,packedUV).rgb-vec3(128.0/255.0))*0.25;
    vec3 s = (texture(endSin,packedUV).rgb-vec3(128.0/255.0))*0.25;
    return original+c*phase.x+s*phase.y;
}
vec3 curlFrame(float frameIndex, vec2 pixel) {
    vec2 tile = vec2(mod(frameIndex,8.0),floor(frameIndex/8.0));
    vec2 localUV=(pixel-centerPx)/360.0+0.5;
    vec2 atlas=(tile*182.0+1.0+clamp(localUV*180.0,vec2(0.5),vec2(179.5)))/1456.0;
    return texture(endLocal,atlas).rgb-vec3(128.0/255.0);
}
vec3 endColor(vec3 ray) {
    float angle=TAU*mod(frameTimeCounter,8.0)/8.0;
    vec2 phase=vec2(cos(angle)-1.0,sin(angle));
    float lon=atan(ray.x,-ray.z)/TAU+0.5;
    float lat=0.5-asin(clamp(ray.y,-1.0,1.0))/3.14159265359;
    vec2 around=vec2((0.5+1023.0*lon)/1024.0,(752.5+255.0*lat)/1024.0);
    if(ray.z>=-0.001) return skyTex(around,phase);
    // North is -Z. The approved framing used a 70-degree vertical field of view.
    vec2 pixel=vec2(centerPx.x,361.5)+vec2(ray.x,-ray.y)*517.0/(-ray.z);
    vec2 margin=min(pixel+0.5,sourceSize-0.5-pixel);
    float front=smoothstep(0.0,48.0,min(margin.x,margin.y));
    if(front<=0.0) return skyTex(around,phase);
    float radius=length(pixel-centerPx);
    if(radius<70.5) return vec3(0.0);
    vec3 north=skyTex((clamp(pixel,vec2(0.0),sourceSize-1.0)+0.5)/1024.0,phase);
    if(radius<180.0) {
        float frame=10.0*mod(frameTimeCounter,6.0);
        north+=mix(curlFrame(floor(frame),pixel),curlFrame(mod(floor(frame)+1.0,60.0),pixel),fract(frame));
    }
    if(front==1.0) return clamp(north,0.0,1.0);
    return mix(skyTex(around,phase),north,front);
}
void main() {
    vec4 scene=texture(colortex0,uv);
    outColor=scene;
    if(texture(depthtex0,uv).r<1.0 || isEyeInWater!=0) return;
    float visibility=1.0-clamp(max(blindness,darknessFactor),0.0,1.0);
    if(visibility<=0.0) return;
    vec4 view=gbufferProjectionInverse*vec4(uv*2.0-1.0,1.0,1.0);
    vec3 ray=normalize(mat3(gbufferModelViewInverse)*view.xyz);
    outColor.rgb=mix(scene.rgb,endColor(ray),visibility);
}
