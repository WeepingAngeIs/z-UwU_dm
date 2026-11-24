#include "/lib/options.glsl"
uniform sampler2D gtexture,lightmap;
uniform float alphaTestRef, fogStart, fogEnd;
uniform vec3 fogColor;
in vec2 uv,lightUV;
in vec4 tint;
in float eyeDepth;
flat in int material;
layout(location=0) out vec4 outColor;
layout(location=1) out vec4 lamp;
void main() {
    vec4 albedo=texture(gtexture,uv)*tint;
    if(albedo.a<alphaTestRef) discard;
    vec3 lit=albedo.rgb*texture(lightmap,lightUV).rgb;
    if(material==11001 || material==11002) lit*=float(LIQUID_BRIGHTNESS)/100.0;
    float fog=smoothstep(fogStart,max(fogEnd,fogStart+0.01),eyeDepth);
    outColor=vec4(mix(lit,fogColor,fog),albedo.a);
    float range=AFTERGLOW_QUALITY==1?10.0:14.0;
    float visible=(1.0-smoothstep(range-1.0,range,eyeDepth))*albedo.a;
    float warm=material==11003?visible:0.0;
    float cool=material==11004?visible:0.0;
    lamp=vec4(warm,cool,(warm+cool)*clamp(eyeDepth/20.0,0.0,1.0),warm+cool);
}
