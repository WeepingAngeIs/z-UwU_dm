"""Execute the actual packed End program in hosted Mesa, with known foreground depth."""
import ctypes, ctypes.util, hashlib, json, math, os, sys, zipfile
from io import BytesIO
from pathlib import Path
import numpy as np
from PIL import Image

assert os.environ.get("GITHUB_ACTIONS")=="true"
os.environ["PYOPENGL_PLATFORM"]="osmesa"
from OpenGL import GL as gl
from OpenGL.GL.shaders import compileShader,compileProgram

W,H=480,360
mem=(ctypes.c_ubyte*(W*H*4))()
lib=ctypes.CDLL(ctypes.util.find_library("OSMesa"))
lib.OSMesaCreateContextAttribs.restype=ctypes.c_void_p
lib.OSMesaCreateContextAttribs.argtypes=[ctypes.POINTER(ctypes.c_int),ctypes.c_void_p]
attrs=(ctypes.c_int*9)(0x22,gl.GL_RGBA,0x33,0x35,0x36,3,0x37,3,0)
ctx=lib.OSMesaCreateContextAttribs(attrs,None)
assert ctx
lib.OSMesaMakeCurrent.argtypes=[ctypes.c_void_p,ctypes.c_void_p,ctypes.c_uint,ctypes.c_int,ctypes.c_int]
assert lib.OSMesaMakeCurrent(ctx,mem,gl.GL_UNSIGNED_BYTE,W,H)
z=zipfile.ZipFile(sys.argv[1])
def load(name):
    lines=[]
    for line in z.read("shaders/"+name).decode().splitlines():
        lines.append(load(line.split('"')[1][1:]) if line.startswith("#include") else line)
    return "\n".join(lines)+"\n"
program=compileProgram(compileShader(load("end/deferred.vsh"),gl.GL_VERTEX_SHADER),
                       compileShader(load("end/deferred.fsh"),gl.GL_FRAGMENT_SHADER),validate=False)
gl.glUseProgram(program)
def uniform(n): return gl.glGetUniformLocation(program,n)
def texture(unit,name,arr,fmt,internal,kind):
    gl.glActiveTexture(gl.GL_TEXTURE0+unit);gl.glBindTexture(gl.GL_TEXTURE_2D,gl.glGenTextures(1))
    for axis in [gl.GL_TEXTURE_WRAP_S,gl.GL_TEXTURE_WRAP_T]:gl.glTexParameteri(gl.GL_TEXTURE_2D,axis,gl.GL_CLAMP_TO_EDGE)
    for axis in [gl.GL_TEXTURE_MIN_FILTER,gl.GL_TEXTURE_MAG_FILTER]:gl.glTexParameteri(gl.GL_TEXTURE_2D,axis,gl.GL_LINEAR)
    gl.glPixelStorei(gl.GL_UNPACK_ALIGNMENT,1)
    gl.glTexImage2D(gl.GL_TEXTURE_2D,0,internal,arr.shape[1],arr.shape[0],0,fmt,kind,np.ascontiguousarray(arr))
    gl.glUniform1i(uniform(name),unit)
control=np.tile(np.array([31,84,54,255],np.uint8),(H,W,1))
depth=np.ones((H,W),np.float32);depth[:H//7,:W//3]=.75
texture(0,"colortex0",control,gl.GL_RGBA,gl.GL_RGBA8,gl.GL_UNSIGNED_BYTE)
texture(1,"depthtex0",depth,gl.GL_RED,gl.GL_R32F,gl.GL_FLOAT)
for unit,(name,path) in enumerate([("endSky","end_sky"),("endCos","end_phase_cos"),("endSin","end_phase_sin"),("endLocal","end_local")],2):
    pix=np.array(Image.open(BytesIO(z.read("shaders/textures/"+path+".png"))).convert("RGB"))
    texture(unit,name,pix,gl.GL_RGB,gl.GL_RGB8,gl.GL_UNSIGNED_BYTE)
f=1/math.tan(math.radians(35))
projection=np.array([[f*H/W,0,0,0],[0,f,0,0],[0,0,-1.001,-.1],[0,0,-1,0]],np.float32)
gl.glUniformMatrix4fv(uniform("gbufferProjectionInverse"),1,gl.GL_TRUE,np.linalg.inv(projection))
gl.glViewport(0,0,W,H)
gl.glDisable(gl.GL_DITHER);gl.glDisable(gl.GL_BLEND);gl.glDisable(gl.GL_DEPTH_TEST)
def render(t,yaw=0,water=0):
    c,s=math.cos(yaw),math.sin(yaw)
    inverse=np.array([[c,0,s,0],[0,1,0,0],[-s,0,c,0],[0,0,0,1]],np.float32)
    gl.glUniformMatrix4fv(uniform("gbufferModelViewInverse"),1,gl.GL_TRUE,inverse)
    gl.glUniform1f(uniform("frameTimeCounter"),t);gl.glUniform1i(uniform("isEyeInWater"),water)
    gl.glActiveTexture(gl.GL_TEXTURE0)
    gl.glBegin(gl.GL_QUADS)
    for u,v in [(0,0),(1,0),(1,1),(0,1)]:
        gl.glTexCoord2f(u,v);gl.glVertex2f(2*u-1,2*v-1)
    gl.glEnd()
    b=gl.glReadPixels(0,0,W,H,gl.GL_RGBA,gl.GL_UNSIGNED_BYTE)
    return np.frombuffer(b,np.uint8).reshape(H,W,4)[::-1,:,:3].copy()
out=Path(sys.argv[2]);out.mkdir(exist_ok=True,parents=True)
a=render(0);south=render(0,math.pi);wet=render(1,water=1)
Image.fromarray(a).save(out/"end-program-north.png");Image.fromarray(south).save(out/"end-program-south.png")
yy,xx=np.mgrid[:H,:W];rad=np.hypot(xx+.5-W/2,yy+.5-H*(.5+(314.3-361.5)/724))
core=rad<.076*H
core_fraction=float(np.mean(a[core].max(axis=1)<=3))
ann=(rad>H*.103)&(rad<H*.19)
rim_fraction=float(np.mean(a[:,:,2][ann].astype(float)-a[:,:,0][ann]>13))
assert core_fraction>.995 and rim_fraction>.25,(core_fraction,rim_fraction)
assert np.all(a[-H//8:,:W//3]==control[0,0,:3]) and np.all(wet==control[0,0,:3])
assert np.mean(south[core].max(axis=1)<4)<.6
outer=(rad>H*.30)&(yy<H*.36)
step=lambda old,new:float(np.abs(old.astype(float)-new)[outer].mean())
ordinary=[step(render(t-1/60),render(t)) for t in np.linspace(.14,7.85,24)]
joins=[step(render(t-1/60),render(t)) for t in (8,16,24)]
assert all(0<d<=max(ordinary)*1.45 for d in joins),(joins,min(ordinary),max(ordinary))
assert np.array_equal(a,render(24)) and np.array_equal(a,render(3600))
assert np.all(render(2)[core]<=3) and np.mean(np.abs(render(2).astype(float)-a)[outer])>.25
report={"renderer":gl.glGetString(gl.GL_RENDERER).decode(),"zip_sha256":hashlib.sha256(Path(sys.argv[1]).read_bytes()).hexdigest(),
        "black_fraction":core_fraction,"blue_rim_fraction":rim_fraction,"outer_seam_mean_rgb_steps":joins,
        "ordinary_mean_rgb_step_min_max":[min(ordinary),max(ordinary)],"foreground_depth":True,"underwater_passthrough":True,
        "wrap_at_24_and_3600":True}
(out/"mesa-end.json").write_text(json.dumps(report,indent=2));print(json.dumps(report))
