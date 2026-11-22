"""Compare actual rendered pixels across a visible negative-coordinate chunk boundary."""
import json
import sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont

root=Path(sys.argv[1])
names=("baseline","negative","filter-off","negative-again","restored")
frames={n:np.asarray(Image.open(root/(n+".png")).convert("RGB"),dtype=np.int16) for n in names}
h,w=frames["baseline"].shape[:2]
def patch(name,x):
    image=frames[name]
    return np.median(image[int(.34*h):int(.42*h),int((x-.035)*w):int((x+.035)*w)],axis=(0,1))
samples={n:{side:patch(n,x).tolist() for side,x in (("outside",.27),("inside",.73))} for n in names}
for side in ("inside","outside"):
    color=np.array(samples["baseline"][side])
    assert color[1]>color[0]+20 and color[1]>color[2]+30,("Unloaded or hidden control wall",side,color)
    for name in names[1:]:
        expected=(255-color) if side=="inside" and name in ("negative","negative-again") else color
        actual=np.array(samples[name][side])
        assert np.max(np.abs(expected-actual))<=12,(name,side,expected,actual,samples)
font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",17)
sheet=Image.new("RGB",(900,560),(34,32,41));draw=ImageDraw.Draw(sheet)
for i,(n,label) in enumerate((("baseline","Before activation"),("negative","Five chunks paused"),("filter-off","Colors switched off while paused"),("restored","After time resumed"))):
    x=(i%2)*450;y=(i//2)*280
    im=Image.fromarray(frames[n].astype(np.uint8)).resize((450,253),Image.Resampling.LANCZOS)
    sheet.paste(im,(x,y));draw.text((x+7,y+254),label,fill="white",font=font)
sheet.save(root/"time-pocket-colors.png")
geometry=[]
for model in ("clock","star"):
    for distance in (26,55):
        for angle in (0,90,180):
            images=[np.asarray(Image.open(root/f"model-{model}-{distance}-{angle:03}-{j}.png").convert("RGB"),dtype=np.int16) for j in range(3)]
            # Central region contains the actual native held item and its holder; ignore sky, HUD and terrain edges outside it.
            center=np.stack([im[int(h*.12):int(h*.64),int(w*.32):int(w*.68)] for im in images])
            changes=[float((np.abs(a-b).max(axis=2)>40).mean()) for a,b in zip(center,center[1:])]
            geometry.append({"model":model,"blocks":distance/10,"angle":angle,"jitter":changes})
(root/"native-measurements.json").write_text(json.dumps({"pixels":samples,"modelViews":geometry},indent=2))
print("POCKET BOUNDARY AND FILTER PASS",samples)
