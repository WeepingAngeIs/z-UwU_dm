"""Geometric regression checks for the male Guardian. Shared edges and attached shoulder sockets are intentional."""
from collections import defaultdict
from functools import lru_cache
import argparse
import hashlib
import json
from pathlib import Path
import math
import sys
import numpy as np
sys.dont_write_bytecode = True
from review import source, view_pose, rot, PIVOTS, ASSETS, skin

def tris(q, group):
    p=np.concatenate((q[:,[0,1,2]],q[:,[0,2,3]]))
    labels=np.tile(group,2)
    area=np.cross(p[:,1]-p[:,0],p[:,2]-p[:,0])
    keep=(np.linalg.norm(area,axis=1)>1e-7)
    return p[keep],labels[keep]

def area(poly):
    return abs(sum(p[0]*q[1]-p[1]*q[0] for p,q in zip(poly,np.roll(poly,-1,axis=0))))/2 if len(poly)>=3 else 0

def overlap2(a,b):
    # Clip two convex triangles after removing their common plane's largest normal component.
    winding=np.sign((b[1,0]-b[0,0])*(b[2,1]-b[0,1])-(b[1,1]-b[0,1])*(b[2,0]-b[0,0]))
    clipped=list(a)
    for u,v in zip(b,np.roll(b,-1,axis=0)):
        prev=clipped;clipped=[]
        if not prev:return 0
        den=v-u
        def side(p):return winding*(den[0]*(p[1]-u[1])-den[1]*(p[0]-u[0]))
        s=prev[-1];ds=side(s)
        for e in prev:
            de=side(e)
            if (de>=0)!=(ds>=0):clipped.append(s+(e-s)*(ds/(ds-de)))
            if de>=0:clipped.append(e)
            s=e;ds=de
    return area(clipped)

def coincident(q,ids,names):
    p,group=tris(q,ids)
    n=np.cross(p[:,1]-p[:,0],p[:,2]-p[:,0]);n/=np.linalg.norm(n,axis=1)[:,None]
    d=np.einsum("ij,ij->i",n,p[:,0])
    axis=np.argmax(abs(n),axis=1)
    sign=np.where(n[np.arange(len(n)),axis]<0,-1,1)
    keys=np.rint(np.column_stack([n*sign[:,None]*10000,d*sign*1000])).astype(int)
    planes=defaultdict(list)
    for i,key in enumerate(keys):planes[tuple(key)].append(i)
    hits=defaultdict(float)
    for bucket in planes.values():
        if len(bucket)<2 or len(set(group[bucket]))<2:continue
        for j,i in enumerate(bucket):
            candidate=[k for k in bucket[j+1:] if group[k]!=group[i]]
            if not candidate:continue
            others=p[candidate]
            touch=np.all(np.minimum(p[i].max(0),others.max(1))>=np.maximum(p[i].min(0),others.min(1))-1e-6,axis=1)
            a2=np.delete(p[i],axis[i],axis=1)
            for k in np.array(candidate)[touch]:
                val=overlap2(a2,np.delete(p[k],axis[i],axis=1))/abs(n[i,axis[i]])
                if val>.0002:hits[" x ".join(sorted([names[group[i]],names[group[k]]]))]+=val
    return {k:round(v,4) for k,v in sorted(hits.items())}

def boxes():
    # The retired male Model.layer, its flower builder and lily pads at 072811cb288996978e7f664a3bda47a570fea079.
    result=[];groups=[];names=[]
    def add(name,quads):
        groups.extend([len(names)]*len(quads));names.append(name);result.extend(quads)
    def box(name,loc,size,origin=(0,0,0),rotation=None):
        x,y,z=loc;dx,dy,dz=size
        vs=np.array([[x,y,z],[x+dx,y,z],[x+dx,y+dy,z],[x,y+dy,z],[x,y,z+dz],[x+dx,y,z+dz],
                     [x+dx,y+dy,z+dz],[x,y+dy,z+dz]])
        if rotation is not None:vs=vs@rotation.T
        add(name,(vs+origin)[np.array([[0,1,2,3],[4,5,6,7],[0,4,7,3],[1,5,6,2],[0,1,5,4],[3,2,6,7]])])
    def blossom(name,origin,rz):
        matrix=rot(x=-.12,z=rz)
        box(name+" pollen",[-.9,-.9,-.9],[2,2,2],origin,matrix)
        for i in range(5):
            box(name+" petal "+str(i),[-1,-3,-.5],[2,3,1],origin,matrix@rot(z=i*2*math.pi/5))
    box("basket",[-8,-5,-5],[16,16,10]);box("collar",[-9,-4,-6],[18,5,12]);box("lower cuff",[-7,10,-5],[14,3,10])
    box("face",[-8,-10,-6],[16,11,12],[0,-4,0]);box("cap",[-9,-10.5,-7],[18,4,14],[0,-4,0])
    blossom("brooch",[-4,1,-6.6],-.2)
    for sign,side in [(-1,"right"),(1,"left")]:
        arm=np.array([sign*10,-2,0]);leg=np.array([sign*4.5,13,0])
        box(side+" arm",[-3.5,-1,-3.5],[7,17,7],arm);box(side+" hand",[-4.5,12.5,-4],[9,6,8],arm)
        blossom(side+" shoulder",arm+[sign*2.5,1,-3.9],sign*.35)
        box(side+" leg",[-3.4,0,-3.5],[7,9,7],leg);box(side+" boot",[-4,7,-5],[8,4,9],leg)
    old_lily=16*np.array([[-.245,-.245,0],[.245,-.245,0],[.245,.245,0],[-.245,.245,0]])
    for i in range(10):
        a=i*2*math.pi/10
        add("old pad "+str(i),[old_lily @ (rot(y=-a)@rot(x=math.radians(-42))).T+
                               [math.cos(a)*.48*16,-.48*16-4,math.sin(a)*.48*16]])
    return np.array(result),np.array(groups),names

def crossed(seg_start,seg_end,tri):
    # Möller–Trumbore test for intersections inside a segment and inside a triangle.
    ep=1e-6
    direction=seg_end-seg_start
    e1=tri[:,1]-tri[:,0];e2=tri[:,2]-tri[:,0]
    p=np.cross(direction,e2);det=np.einsum("ij,ij->i",e1,p)
    keep=np.abs(det)>ep
    if not keep.any():return False
    t=seg_start-tri[:,0]; inv=np.divide(1,det,out=np.zeros_like(det),where=keep)
    u=inv*np.einsum("ij,ij->i",t,p)
    q=np.cross(t,e1);v=inv*np.einsum("j,ij->i",direction,q)
    d=inv*np.einsum("ij,ij->i",e2,q)
    return np.any(keep&(u>-ep)&(v>-ep)&(u+v<1+ep)&(d>ep)&(d<1-ep))

def intersects(a,b):
    if len(a)==0 or len(b)==0:return False
    amin,amax=a.min((0,1)),a.max((0,1))
    bmin,bmax=b.min(1),b.max(1)
    possible=np.all((bmax>=amin)&(bmin<=amax),axis=1);b,bmin,bmax=b[possible],bmin[possible],bmax[possible]
    if not len(b):return False
    for tri in a:
        near=np.all((bmax>=tri.min(0))&(bmin<=tri.max(0)),axis=1)
        if not near.any():continue
        c=b[near]
        for i in range(3):
            if crossed(tri[i],tri[(i+1)%3],c):return True
        for target in c:
            for i in range(3):
                if crossed(target[i],target[(i+1)%3],tri[None,:]):return True
    return False

def separated(q,ids,names,forearm_mask):
    # Lower forearms, hands and feet must not pass through the opposing body or head. Deltoids deliberately join the chest at their pivot.
    p,group=tris(q,ids)
    def take(predicate):
        mask=np.array([predicate(s) for s in names])
        return p[mask[group]]
    hands=take(lambda s:s.endswith(("_hand","_thumb","_inner_bracer")))
    forearms=np.concatenate((hands,tris(q[forearm_mask],ids[forearm_mask])[0]))
    arms=take(lambda s:s.endswith(("_arm","_hand","_thumb","_inner_bracer","_biceps","_leaf_pauldron")) or s.startswith("shoulder_blossom"))
    feet=take(lambda s:s.endswith(("_shin_leaf","_boot")))
    head=take(lambda s:s.startswith(("crown","nose")) or s.endswith(("temple_leaf","crown_fold")) or s in ("face_and_double_chin","moss_cap","fitted_leaf_hair"))
    torso=take(lambda s:s in ("living_bark_torso","leaf_belt") or s.endswith(("_pectoral","_hip_petal","_waist_oblique")) or "_rib_" in s)
    lhand=take(lambda s:s.startswith("left") and s.endswith(("_hand","_thumb")))
    rhand=take(lambda s:s.startswith("right") and s.endswith(("_hand","_thumb")))
    lfoot=take(lambda s:s=="left_boot");rfoot=take(lambda s:s=="right_boot")
    pairs=[("distal forearms and hands/body",forearms,torso),("entire arms/head or crown",arms,head),("feet/head or crown",feet,head),("hands/feet",hands,feet),
           ("left/right hand",lhand,rhand),("left/right foot",lfoot,rfoot)]
    return [name for name,a,b in pairs if intersects(a,b)]

def physical_poses():
    yield ("idle","idle",{})
    for i in range(9):yield ("walk "+str(i),"walk",{"swing":.98*math.sin(2*math.pi*i/9)})
    for degrees in [-34,34]:yield ("head shake "+str(degrees),"idle",{"shake":math.radians(degrees)})
    yield ("grip","grip",{})
    for tick in [0,3,6,9,12,15,18,21,21.5,22,22.5,23,23.5,24]:yield ("held flip tick "+str(tick),"flip",{"t":tick/24})

def palette_checks():
    female=[]
    for suff in ["","_half","_blink","_half_lips","_blink_lips"]:
        src=skin("entity/female_uwu_guardian"+suff);dst=skin("entity/dusk_guardian_female"+suff)
        assert src.shape==dst.shape and np.array_equal(src[:,:,3],dst[:,:,3])
        female.append(suff or "open")
    open0=skin("entity/female_uwu_guardian");wink=skin("entity/petal_guardian_female_wink")
    full=skin("entity/female_uwu_guardian_blink");delta=np.any(wink!=open0,axis=2)
    yy,xx=np.where(delta)
    assert len(xx)>10 and xx.min()>=160 and xx.max()<176 and yy.min()>=32 and yy.max()<64 and np.array_equal(wink[delta],full[delta])
    male=skin("entity/petal_guardian_male");male_wink=skin("entity/petal_guardian_male_wink")
    yy2,xx2=np.where(np.any(male!=male_wink,axis=2))
    assert male.shape == male_wink.shape == (256,256,4)
    assert len(xx2)>35 and 13 <= xx2.min() < xx2.max() < 63 and 162 <= yy2.min() < yy2.max() < 186
    dusk=skin("entity/dusk_guardian_male")
    female_head=next(b for b in json.loads((ASSETS/"geometry/guardian_bones.json").read_text())["bones"] if b["name"]=="head")
    source_tiles={c["tile"] for c in female_head["cubes"] if c["name"].startswith(("fringe_leaf_","middle_canopy_","right_nape_leaf_"))
                  or c["name"] in ("under_canopy","right_temple_slip","left_temple_slip")}
    allowed=set()
    for tile in source_tiles:
        x,y=(tile%4)*128+32,(tile//4)*64+32
        swatch=open0[y:y+32,x:x+32]
        colors=np.floor(swatch[:,:,:3].astype(float)*.86+.5).astype(np.uint8)
        allowed.update(map(tuple, colors[swatch[:,:,3]>0]))
    used=set()
    for x,y,w,h in ((2,68,108,54),(116,68,88,54),(208,68,32,54),(132,132,116,70)):
        chart=male[y:y+h,x:x+w]
        assert np.array_equal(chart,dusk[y:y+h,x:x+w]) and np.array_equal(chart,male_wink[y:y+h,x:x+w])
        assert .08 < np.mean(chart[:,:,3]>0) < 1
        used.update(map(tuple,chart[chart[:,:,3]>0,:3]))
    assert len(used)>=8 and used<=allowed, "Hair must use the darkened source Guardian lock palette"
    for k in ("male","girl"):
        a=skin("item/owl_guardian_"+k+"_egg");b=skin("item/dusk_guardian_"+k+"_egg")
        assert a.shape==(16,16,4) and np.array_equal(a[:,:,3],b[:,:,3])
    return {"femaleExpressionFramesWithIdenticalAlpha":female,"femaleChangedWinkTexels":len(xx),"maleChangedWinkTexels":len(xx2),"femaleHairSourceTiles":sorted(source_tiles),"maleHairRGBFactor":.86}

def head_fit(data):
    mesh, parts = data
    head = mesh[1]
    def vertices(name):
        part = next(r for r in parts if r["name"] == name)
        start, count = part["first"]//4, part["count"]//4
        return head[start:start+count, :, :3]
    face, hair, hat = (vertices(n) for n in ("face_and_double_chin", "fitted_leaf_hair", "moss_cap"))
    size = np.ptp(face.reshape(-1,3), axis=0)
    assert .98 < size[0]/size[1] < 1.07
    rim = hair[:,0,:]
    cap = hat.reshape(-1,3)
    seam = max(np.min(np.linalg.norm(cap-p,axis=1)) for p in rim)
    assert len(rim)==8 and seam<.00002, "Hat and hair rim do not meet"
    # In model pixels the upper square face is bounded by this chamfered rectangle.
    # The hair side walls must all lie outside it, including the midpoints of the four bevels.
    margin = []
    for quad in hair:
        for t in np.linspace(0,1,13):
            x,_,z = quad[0]*(1-t)+quad[3]*t
            ax,az = abs(x)/6.08, abs(z+.72)/5.05
            margin.append(max(ax,az,(ax+az)/1.85)-1)
    assert min(margin)>.035, "A hair panel cuts into the face"
    return {"headWidthToHeight":float(size[0]/size[1]), "maximumRimGapModelPixels":float(seam),
            "minimumHairClearanceFractionOfFaceRadius":float(min(margin))}

def verify(out):
    data=source();old=coincident(*boxes())
    assert abs(old["basket x lower cuff"]-28)<.001,old
    assert any("brooch petal" in name for name in old), "The old overlapping flower petals were not detected"
    # Select the integrated arm surfaces below the elbow in their original local coordinates.
    raw=np.concatenate(data[0])
    selectors=np.all(raw[:,:,1]>=7.58,axis=1)
    full=[]; failures=[]
    cached={}
    for frame,stage,options in physical_poses():
        q,mat,ids,names=view_pose(data,"Petal",stage,**options)
        # The held turn applies one common rigid transform to all bones, preserving all relative geometry.
        key="held-rigid" if stage=="flip" and options["t"]<=21/24 else frame
        if key not in cached:
            p=q[:,:,:3]
            forearm_mask=selectors & np.array([names[i] in ("left_arm","right_arm") for i in ids])
            cached[key]=(coincident(p,ids,names),separated(p,ids,names,forearm_mask))
        planes,clipping=cached[key]
        if planes or clipping:failures.append((frame,planes,clipping))
        full.append({"frame":frame,"overlappingCoplanarSurfaces":planes,"unrelatedLimbIntersections":clipping})
    assets=ASSETS/"geometry/petal_guardian_male.mesh"
    result={"meshSHA256":hashlib.sha256(assets.read_bytes()).hexdigest(),
        "originalArmorFlowersAndCrownCoplanarAreaModelPixelsSquared":old,"poses":full,"textureChecks":palette_checks(),"headFit":head_fit(data)}
    out.mkdir(parents=True,exist_ok=True);(out/"Male Guardian surface audit.json").write_text(json.dumps(result,indent=2)+"\n")
    if failures:raise SystemExit("Surface audit failed: "+str(failures))
    print("Guardian mesh validated",result["meshSHA256"],"across",len(full),"poses; legacy front/back overlap verified.")

if __name__=="__main__":
    a=argparse.ArgumentParser();a.add_argument("output",type=Path);verify(a.parse_args().output)
