uniform int fogShape;
out vec4 passedColor;
out float skyDistance;
flat out int nativeFan;
void main() {
    gl_Position=ftransform();
    passedColor=gl_Color;
    vec3 p=gl_Vertex.xyz;
    nativeFan=int(abs(length(p.xz)-512.0)<0.1);
    skyDistance=fogShape==0?length(p):max(length(p.xz),abs(p.y));
}
