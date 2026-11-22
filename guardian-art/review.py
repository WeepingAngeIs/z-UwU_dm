"""Review the shipping male Guardian mesh, UV atlas, pose anchors and crown without launching Minecraft."""
import argparse
import json
import math
import struct
import subprocess
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/uwudimension"
BONES = ["basket", "head", "left", "right", "left_root", "right_root"]
PIVOTS = np.array([[0,0,0],[0,-4,0],[10,-2,0],[-10,-2,0],[4.5,13,0],[-4.5,13,0]], dtype=float)
PI = math.pi

def rot(x=0,y=0,z=0):
    a,b,c=np.cos([x,y,z]);d,e,f=np.sin([x,y,z])
    return np.array([[c,-f,0],[f,c,0],[0,0,1]]) @ np.array([[b,0,e],[0,1,0],[-e,0,b]]) @ np.array([[1,0,0],[0,a,-d],[0,d,a]])

def source():
    data=(ASSETS/"geometry/petal_guardian_male.mesh").read_bytes()
    assert data[:4]==b"GDM1"
    at=4; meshes=[]
    for _ in BONES:
        size=struct.unpack_from(">I",data,at)[0];at+=4
        values=np.frombuffer(data,">f4",count=8*size,offset=at).astype(float).reshape((-1,4,8)); at+=32*size
        values[:,:,:3]*=16
        assert np.all(np.isfinite(values)) and np.allclose(np.linalg.norm(values[:,:,3:6],axis=2),1,atol=1e-5)
        meshes.append(values)
    assert at==len(data)
    metadata=subprocess.run(["node","-e","console.log(JSON.stringify(require('./guardian-art/male-mesh.cjs').bake(false)))"],
                            cwd=ROOT,capture_output=True,check=True,text=True)
    names=json.loads(metadata.stdout)
    return meshes, names

def skin(name):
    return np.array(Image.open(ASSETS/("textures/"+name+".png")).convert("RGBA"))

def view_pose(data, name, stage, swing=0, t=0, shake=0):
    meshes,names=data
    touching=stage in ("grip","flip")
    speed=swing if stage=="walk" else 0
    arm=-1.34 if stage=="flip" else -1.25 if touching else 0
    roll=.28 if stage=="flip" else .08
    settle=np.clip((t*24-21)/3,0,1) if stage=="flip" else 0
    arm*=1-settle
    roll=roll*(1-settle)+.08*settle
    rx_leg=-.2*(1-settle) if stage=="flip" else speed
    m=[rot(),rot(y=shake),rot(arm+speed*.45,z=roll),rot(arm-speed*.45,z=-roll),
        rot(rx_leg),rot(rx_leg if stage=="flip" else -speed)]
    body=rot()
    pivot_y=24-1.12*16; lift=0
    if stage=="flip":
        # World up is negative Y in model coordinates so conjugating the right-axis flip changes its sign.
        body=rot(x=-2*PI*t*t*(3-2*t));lift=-1.25*16*math.sin(PI*t)
    def transform(q,b):
        d=q.copy()
        d[:,:,:3]=(d[:,:,:3] @ m[b].T+PIVOTS[b]-[0,pivot_y,0]) @ body.T+[0,pivot_y+lift,0]
        d[:,:,3:6]=d[:,:,3:6] @ m[b].T @ body.T
        return d
    qs=[transform(meshes[i],i) for i in range(6)]
    mats=np.zeros(sum(len(q) for q in qs),int)
    shape_ids=[]
    labels=[]
    for b,bone in enumerate(BONES):
        for r in [r for r in names if r["bone"]==bone]:
            shape_ids.extend([len(labels)]*(r["count"]//4)); labels.append(r["name"])
    return np.concatenate(qs),mats,np.array(shape_ids),labels


def render(pose, faction, angle, elev, size, scale, center=(0,2,0), wink=False, debug=False):
    qs,mats,ids,labels=pose; width,height=size
    direction=np.array([math.sin(angle)*math.cos(elev),-math.sin(elev),-math.cos(angle)*math.cos(elev)])
    right=np.array([math.cos(angle),0,math.sin(angle)])
    down=np.cross(right,direction)
    points=qs[:,:,:3]-np.array(center)
    sx=points @ right*scale+width*.5; sy=points @ down*scale+height*.5; sz=points @ direction
    lighting=np.array([-.46,-.66,-.60])
    shade=.69+.32*np.maximum(0,qs[:,:,3:6] @ lighting)
    mats0=skin("entity/"+("dusk_guardian_male" if faction=="Dusk" else "petal_guardian_male"+("_wink" if wink else "")))
    atlas=[mats0]
    view=np.zeros((height,width,4),dtype=np.uint8)
    zbuf=np.full((height,width),-1e5); idbuf=np.full((height,width),-1,dtype=int)
    # Painter order avoids most depth writes; actual coverage still uses the per-pixel z-buffer.
    order=np.argsort(-np.max(sz,axis=1))
    for k in order:
        if sx[k].max()<0 or sx[k].min()>=width or sy[k].max()<0 or sy[k].min()>=height:continue
        for tri in ([0,1,2],[0,2,3]):
            x=sx[k,tri]; y=sy[k,tri]; z=sz[k,tri]
            a=int(max(0,np.floor(x.min()))); b=int(min(width,np.ceil(x.max())+1))
            c=int(max(0,np.floor(y.min()))); d=int(min(height,np.ceil(y.max())+1))
            if a>=b or c>=d:continue
            den=(y[1]-y[2])*(x[0]-x[2])+(x[2]-x[1])*(y[0]-y[2])
            if abs(den)<1e-6:continue
            if den>0 and mats[k]==0:continue
            zz=zbuf[c:d,a:b]
            if zz.min()>z.max():continue
            gy,gx=np.ogrid[c+.5:d+.5,a+.5:b+.5]
            w0=((y[1]-y[2])*(gx-x[2])+(x[2]-x[1])*(gy-y[2]))/den
            w1=((y[2]-y[0])*(gx-x[2])+(x[0]-x[2])*(gy-y[2]))/den
            w2=1-w0-w1; zz1=w0*z[0]+w1*z[1]+w2*z[2]
            good=(w0>=-1e-8)&(w1>=-1e-8)&(w2>=-1e-8)&(zz1>zz)
            if not good.any():continue
            u=qs[k,tri,6];v=qs[k,tri,7]; tex=atlas[mats[k]]; h,w=tex.shape[:2]
            tu=np.clip(((w0*u[0]+w1*u[1]+w2*u[2])*w).astype(int),0,w-1)
            tv=np.clip(((w0*v[0]+w1*v[1]+w2*v[2])*h).astype(int),0,h-1)
            col=tex[tv,tu];good &= col[:,:,3]>0
            if not good.any():continue
            s=shade[k,tri]; lit=w0*s[0]+w1*s[1]+w2*s[2]
            layer=view[c:d,a:b]; layer[good,:3]=np.minimum(255,col[good,:3]*lit[good,None])
            layer[good,3]=255;zz[good]=zz1[good];idbuf[c:d,a:b][good]=ids[k]
    return Image.fromarray(view),idbuf

def font(n,bold=False):
    for s in ["C:/Windows/Fonts/"+("segoeuib.ttf" if bold else "segoeui.ttf"),"/usr/share/fonts/truetype/dejavu/DejaVuSans"+("-Bold" if bold else "")+".ttf"]:
        if Path(s).exists():return ImageFont.truetype(s,n)
    return ImageFont.load_default(size=n)

def gallery(data,out):
    sheet=Image.new("RGB",(1100,760),"#18151e"); d=ImageDraw.Draw(sheet)
    d.text((32,23),"The Guardians of Sakura Reach",font=font(31,True),fill="#f6e9dc")
    d.text((33,69),"Male Guardian design study   /   Native mesh and texture colors",font=font(16),fill="#c0aebd")
    cols=[("Front",0,0,"idle",False),("Side",math.radians(82),.045,"idle",False),("Grab · three-quarter",math.radians(35),.08,"grip",False),("Petal wink",0,.035,"idle",True)]
    for i,(label,_,_,_,_) in enumerate(cols):
        d.text((119+242*i,100),label if i<3 else "Expression",font=font(16,True),fill="#e7d8d8")
    for row,(faction,accent,surface) in enumerate([("Petal","#eab6c3","#282a28"),("Dusk","#c6a5df","#292530")]):
        y0=128+row*286
        d.text((22,y0+115),faction,font=font(21,True),fill=accent)
        for i,(label,yaw,pitch,stage,wink) in enumerate(cols):
            x=107+242*i;d.rounded_rectangle((x,y0,x+232,y0+265),radius=10,fill=surface)
            pose=view_pose(data,faction,stage)
            if i==3:
                image,_=render(pose,faction,.08,pitch,(226,231),13.9,center=(0,-9.0,-1),wink=wink)
            else:image,_=render(pose,faction,yaw,pitch,(226,245),5.35,center=(0,2.1,-.5))
            sheet.paste(image,(x+3,y0+2),image)
            if i==3:d.text((x+9,y0+237),"One-eye wink" if row==0 else "Heroic smirk",font=font(14),fill=accent)
    d.text((109,714),"The same six bone anchors carry the lily crown, double chin and carved chest in both village colors.",font=font(16),fill="#c8bdc7")
    sheet.save(out/"Petal and Dusk Guardian.png")
    action=Image.new("RGB",(1100,430),"#19161f"); a=ImageDraw.Draw(action)
    a.text((27,16),"Petal Guardian  /  backward somersault",font=font(27,True),fill="#f6e9dc")
    a.text((28,56),"Orthographic pose study   •   Five samples of the 24-tick held turn",font=font(16),fill="#c5b1bf")
    for i,t in enumerate([0,.25,.5,.75,1]):
        pose=view_pose(data,"Petal","flip",t=t)
        image,_=render(pose,"Petal",math.radians(62),.05,(211,250),3.72,center=(0,-6.1,0))
        x=12+217*i
        a.rounded_rectangle((x,92,x+208,377),radius=9,fill="#29292a")
        action.paste(image,(x+1,92),image)
        a.text((x+10,343),["Grip","Tip back","Inverted","Return","Release"][i],font=font(16,True),fill="#e6becc")
    a.text((28,394),"Model study. Hosted Minecraft captures show the held player and flight path.",font=font(15),fill="#bfaebb")
    action.save(out/"Petal Guardian somersault.png")

if __name__=="__main__":
    ap=argparse.ArgumentParser()
    ap.add_argument("output",type=Path);args=ap.parse_args()
    args.output.mkdir(parents=True,exist_ok=True);gallery(source(),args.output)
