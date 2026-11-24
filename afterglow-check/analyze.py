"""Check actual captured frames and measure only the hosted renderer that produced them."""
from pathlib import Path
import sys,json
import numpy as np
from PIL import Image

p=Path(sys.argv[1])
def image(name): return np.array(Image.open(p/(name+".png")).convert("RGB"),np.int16)
def diff(a,b): return np.abs(image(a)-image(b))
def summarize(a):
    return {"mean":round(float(a.mean()),3),"p95":round(float(np.quantile(a,.95)),3),
            "changed_over_8":round(float(np.mean(a.max(axis=2)>8)),5)}

rows=(p/"views.tsv").read_text().strip().splitlines()
checks={}
if list(p.glob("*.ns")):
    assert len(rows)==9
    for quality in range(3):
        trials=[]
        for f in sorted(p.glob("bench-*-"+str(quality)+".ns")):
            values=np.loadtxt(f,ndmin=1)/1e6
            assert values.size>=10 and values.sum()>=59000
            trials.append(values)
        assert len(trials)==3
        q=np.quantile(np.concatenate(trials),[.5,.95,.99])
        checks[["Off","Low","Default"][quality]]={"frames":sum(x.size for x in trials),"p50_p95_p99_ms":q.round(2).tolist()}
    checks["renderer"]=rows[0].split("\t")[3]
    checks["resolution"]=rows[0].split("\t")[4]
    assert checks["resolution"]=="1920x1080"
    (p/"software-benchmark.json").write_text(json.dumps(checks,indent=2))
else:
    north=image("end-north")
    h,w=north.shape[:2]
    y,x=np.mgrid[:h,:w]
    radius=np.hypot(x-w*.5,y-h*.435)
    core=radius<h*.073
    spiral=(radius>h*.098)&(radius<h*.18)&(y<h*.64)
    gas=(y<h*.32)&(radius>h*.22)
    checks["core_black_fraction"]=float(np.mean(north[core].max(axis=1)<=5))
    checks["blue_violet_rim_fraction"]=float(np.mean(north[:,:,2][spiral]-north[:,:,0][spiral]>13))
    checks["inward_sky_motion_pixels_above_noise"]=int((diff("end-north","end-north-later").max(axis=2)[gas]>2).sum())
    checks["south_center_black_fraction"]=float(np.mean(image("end-south")[core].max(axis=1)<=5))
    checks["end_off_recovery"]=summarize(diff("end-off","end-off-return"))
    for dim in ["overworld","the_nether"]:
        checks[dim+"_control"]=summarize(diff(dim+"-0",dim+"-2"))
    central=np.s_[h//5:4*h//5,w//3:2*w//3]
    for a,b in [(0,1),(0,2),(1,2)]:
        checks[f"moon_{a}_vs_{b}"]=summarize(diff("moon-"+str(a),"moon-"+str(b))[central])
    delta=diff("liquids-100","liquids-55")
    checks["liquid_scene"]=summarize(delta)
    fixed=next(row for row in rows if row.startswith("liquids-100\t")).split("\t")
    eye=np.array(list(map(float,fixed[8:11])))
    yaw,pitch,fov=np.radians(list(map(float,fixed[11:14])))
    forward=np.array([-np.sin(yaw)*np.cos(pitch),-np.sin(pitch),np.cos(yaw)*np.cos(pitch)])
    right=np.array([-np.cos(yaw),0,-np.sin(yaw)])
    up=np.cross(right,forward)
    sx=(((x+.5)/w)*2-1)*w/h*np.tan(fov/2)
    sy=(1-((y+.5)/h)*2)*np.tan(fov/2)
    ray=forward+sx[:,:,None]*right+sy[:,:,None]*up
    t=(213.875-eye[1])/np.where(abs(ray[:,:,1])>1.e-8,ray[:,:,1],1.e-8)
    wx=eye[0]+t*ray[:,:,0];wz=eye[2]+t*ray[:,:,2]
    rgb100=image("liquids-100");rgb55=image("liquids-55")
    for material,left,right_edge in [("water",27.25,31.75),("lava",33.25,37.75),("stone",32.25,32.75)]:
        roi=(t>0)&(wx>left)&(wx<right_edge)&(wz>22.25)&(wz<26.75)
        assert roi.sum()>30,"Insufficient visible "+material
        chan=[.2126,.7152,.0722]
        checks[material+"_mean_luminance_100_55"]=[round(float((im[roi]@chan).mean()),3) for im in (rgb100,rgb55)]
    for i in [1,2]:checks["rain_off_vs_"+str(i)]=summarize(diff("rain-0","rain-"+str(i)))
    checks["renderer"]=rows[0].split("\t")[3]
    (p/"capture-checks.json").write_text(json.dumps(checks,indent=2))
    print(json.dumps(checks,indent=2),flush=True)
    assert len(rows)==19
    assert checks["core_black_fraction"]>.97 and checks["blue_violet_rim_fraction"]>.20, "Northern disc or rim missing"
    assert checks["south_center_black_fraction"]<.70, "Southern sky duplicated the black disc"
    assert checks["inward_sky_motion_pixels_above_noise"]>60,"Outer sky does not move"
    assert all(checks[f"moon_{a}_vs_{b}"]["changed_over_8"]>.018 for a,b in [(0,1),(0,2),(1,2)]),"Moon selections did not render distinctly"
    assert checks["liquid_scene"]["changed_over_8"]>.001,"Liquid option did not affect visible water/lava"
    for material in ["water","lava"]:
        full,dim=checks[material+"_mean_luminance_100_55"]
        assert dim<full*.98,material+" did not get dimmer"
    assert abs(np.diff(checks["stone_mean_luminance_100_55"])[0])<2.5,"Liquid control changed the stone divider"
    assert all(checks[dim+"_control"]["changed_over_8"]<.015 for dim in ["overworld","the_nether"]),"Other dimension changed visibly"
    assert checks["end_off_recovery"]["changed_over_8"]<.015,"Disabling the pack did not restore the baseline"
print(json.dumps(checks,indent=2))
