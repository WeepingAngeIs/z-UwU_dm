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
    assert all(checks[dim+"_control"]["changed_over_8"]<.015 for dim in ["overworld","the_nether"]),"Other dimension changed visibly"
    assert checks["end_off_recovery"]["changed_over_8"]<.015,"Disabling the pack did not restore the baseline"
print(json.dumps(checks,indent=2))
