uniform int fogShape;
out vec4 passedColor;
out float skyDistance;
void main() {
    gl_Position=ftransform();
    passedColor=gl_Color;
    vec3 p=gl_Vertex.xyz;
    skyDistance=fogShape==0?length(p):max(length(p.xz),abs(p.y));
}
