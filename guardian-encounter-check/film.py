"""Verify what Minecraft rendered and assemble review media from those exact framebuffer captures."""
import csv
import json
import math
import os
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageOps

ROOT = Path(sys.argv[1])
ROLE = ROOT.name
SAMPLES = json.loads((ROOT / "frames.json").read_text())
OUTCOMES = {r["trial"]: r for r in json.loads((ROOT / "outcomes.json").read_text())}
CHECKED = []
COLORS = {"Petal": "#f4a5bf", "Dusk": "#bda8ef"}

def need(ok, text):
    if not ok:
        raise AssertionError(text)

def font(size, bold=False):
    family = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
    return ImageFont.truetype("/usr/share/fonts/truetype/dejavu/" + family, size)

def crop_fill(frame, size):
    width, height = size
    ratio = min(frame.width / width, frame.height / height)
    w, h = int(width * ratio), int(height * ratio)
    x, y = (frame.width - w) // 2, (frame.height - h) // 2
    return frame.crop((x, y, x + w, y + h)).resize(size, Image.Resampling.LANCZOS)

def camera_checks():
    need(set(OUTCOMES) == set(range(7)), "Missing a native warning case")
    for k in (0, 1, 2, 3, 4, 6):
        rows = [r for r in SAMPLES if r["trial"] == k]
        need(len(rows) >= 13, f"Only {len(rows)} camera frames in case {k}")
        seen = [r for r in rows if r["guardianDraw"]]
        need(bool(seen), f"Guardian was absent from camera {k}")
        expected_view = "THIRD_PERSON_BACK" if ROLE == "observer" else "FIRST_PERSON"
        need(all(r["cameraType"] == expected_view for r in rows), f"Incorrect camera: {k}")
        if ROLE == "observer":
            both = [r for r in rows if r["actorDraw"] and r["guardianDraw"] and r["cameraToPlayer"] > 2]
            need(len(both) >= 8, f"Observer failed to film both native models: {k}")
        else:
            need(min(r["cameraToPlayer"] for r in rows) < .12, f"Missing native eye camera: {k}")
        if k in (1, 3):
            wink = [r for r in seen if r["action"] == "wink" and "_wink.png" in r["texture"] and r["playerGround"]]
            need(bool(wink), f"Missing visible single-eye Petal texture before lift in {k}")
            if os.environ.get("UWU_REVIEW_PRE_TELEGRAPH") != "1":
                need(any(r.get("hurtOverlay") is False for r in wink),
                     f"Every actually drawn wink had the red Minecraft hurt overlay in case {k}")
            flip = [r for r in seen if r["action"] == "flip" and "bodyUp" in r]
            if ROLE == "observer":
                need(len(flip) >= 4, f"Missing flip frames in case {k}")
                need(min(r["bodyUp"] for r in flip) < -.72, f"Actual model never inverted: {k}")
                need(max(r["bodyUp"] for r in flip) > .64, f"Flip never showed an upright body: {k}")
                need(min(abs(r["bodyUp"]) for r in flip) < .72, f"Flip skipped its side-on rotation: {k}")
                need(max(r["minArmRaise"] for r in flip) > 36, f"Hands never held the attacker during flip: {k}")
                need(min(r["tick"] for r in wink) < min(r["tick"] for r in flip), "Wink occurred after grasp")
                contact=[r for r in flip if r["actorDraw"] and "actorBodyUp" in r]
                need(len(contact)>=4, f"No independent dual-model draw samples during flip {k}")
                need(max(r["palmToPlayerAnchor"] for r in contact)<.22,
                     f"Drawn palms missed the player's waist in case {k}: {[(r['phaseTick'],round(r['palmToPlayerAnchor'],3)) for r in contact]}")
                need(min(r["actorBodyUp"] for r in contact)<-.72 and max(r["actorBodyUp"] for r in contact)>.64
                     and min(r["actorGuardAlignment"] for r in contact)>.93,
                     f"Attacker failed to turn with the actual Guardian mesh in {k}")
                follow=[r for r in rows if r["action"]=="follow" and r["actorDraw"] and r["guardianDraw"] and "actorBodyUp" in r]
                need(follow and all(not r["carried"] and r["actorBodyUp"]>.93 for r in follow),
                     f"Player remained visually attached after the Petal release in {k}")
                need(any(r["palmToPlayerAnchor"]>.55 and r["drawnGuardianUp"]<.9995 for r in follow),
                     f"No visible separation while the Guardian was still rotating in {k}: {follow}")
                after=[r for r in rows if r["tick"]>max(s["tick"] for s in flip) and r["action"]=="free" and "actorBodyUp" in r]
                need(bool(after) and min(r["actorBodyUp"] for r in after)>.93,
                     f"Attacker remained visually rotated after release in {k}")
            else:
                attached=[r for r in rows if r["action"]=="flip" and "ownerRoll" in r]
                need(len(attached)>=4 and all(abs(r["ownerRoll"])<1 and r["ownerYawError"]<2 and r["ownerPitchError"]<2 for r in attached),
                     f"First-person camera was rotated away from the player's own look in {k}")
                need(any(r["action"]=="follow" and not r["carried"] for r in rows),
                     f"No first-person frames between release and the end of the turn in {k}")
                if k==3:
                    need(max(r["cameraYaw"] for r in attached)-min(r["cameraYaw"] for r in attached)>15,
                         "Player could not look around during the second female Petal warning")
        else:
            held = [r for r in rows if r["action"] == "grip"]
            need(bool(held), f"Missing native grip in case {k}")
            first_grab = held[0]["tick"]
            shake = [r for r in seen if r["action"] == "shake" and r["playerGround"] and r["tick"] < first_grab]
            need(len(shake) >= 4 and min(r["headYaw"] for r in shake) < -13 and max(r["headYaw"] for r in shake) > 13,
                 f"No clearly rendered left and right head turns before the first grab: {k}, {[(r['tick'],r['headYaw']) for r in shake]}")
            if k!=4:
                need(not any(r["action"]=="shake" and r["tick"]>=first_grab for r in rows),
                     f"First warning repeated after the grab: {k}")
            # Case four also films a second player's independent first warning after the first release.
            if ROLE=="observer":
                neutral=[r for r in rows if r["actorDraw"] and "actorBodyUp" in r]
                need(len(neutral)>=8 and min(r["actorBodyUp"] for r in neutral)>.93,
                     f"Non-flip player acquired a body tilt in {k}")
        rec = OUTCOMES[k]
        CHECKED.append({"trial": k, "faction": rec["variant"], "sex": rec["sex"], "warning": rec["warning"],
                        "frames": len(rows), "actualGuardianDraws": len(seen),
                        "actorsDrawnTogether": sum(r["actorDraw"] and r["guardianDraw"] for r in rows),
                        "headRange": [round(f(seen_row["headYaw"] for seen_row in seen), 1) for f in (min, max)]})
        if ROLE=="observer" and rec["warning"]==2:
            CHECKED[-1].update({"measuredDualModelFrames":len(contact),
                                "largestPalmToWaistGap":max(r["palmToPlayerAnchor"] for r in contact),
                                "minimumPlayerUp":min(r["actorBodyUp"] for r in contact),
                                "minimumAlignment":min(r["actorGuardAlignment"] for r in contact),
                                "followThroughFrames":len(follow),
                                "gapDuringFollowThrough":max(r["palmToPlayerAnchor"] for r in follow)})

def gallery():
    records = json.loads((ROOT / "gallery.json").read_text())
    need(len(records) == 108, "Each faction must have 6 angles at 3 distances, with 3 native samples per angle")
    stats = []
    for form in ("petal","dusk"):
        for distance in (4.8,8.4,13.0):
            for angle in (0,40,90,145,180,270):
                samples = sorted((r for r in records if r["form"] == form and r["range"] == distance and r["angle"] == angle),
                                 key=lambda r:r["subpixelSample"])
                need(len(samples) == 3 and all(r["texture"].endswith(f"{form}_guardian_male.png") for r in samples),
                     f"Wrong native male model/texture for {form} {angle}")
                imgs = []
                for r in samples:
                    with Image.open(ROOT / "gallery" / r["file"]) as im:
                        width,height = im.size
                        # The stationary character remains centered while the eye jogs by under one display pixel.
                        half_w = int(width*(.105 if distance<6 else .065 if distance<10 else .044))
                        half_h = int(height*(.245 if distance<6 else .14 if distance<10 else .094))
                        crop = im.crop((width//2-half_w,height//2-half_h,width//2+half_w,height//2+half_h))
                        imgs.append(np.asarray(crop.convert("RGB"), dtype=np.int16))
                changes = [float((np.max(np.abs(a-b),axis=2)>36).mean()) for a,b in ((imgs[0],imgs[1]),(imgs[1],imgs[2]))]
                stats.append({"form":form,"angle":angle,"distance":distance,"changedPixelFraction":changes})
    first=[c for item in stats for c in item["changedPixelFraction"]]
    need(max(first)<.038 and np.median(first)<.009,
         f"Subpixel camera-jitter check found conspicuous temporal instability, max={max(first):.4f} median={np.median(first):.4f}")
    (ROOT/"surface-stability.json").write_text(json.dumps({"source":"Framebuffer samples from the stationary native male. Threshold is a 36-level RGB change on the body crop under a 0.00035-block camera jog. Animation footage and source face audits complement this test.","views":stats},indent=2))
    canvas=Image.new("RGB",(1180,1187),"#151720");d=ImageDraw.Draw(canvas)
    d.text((24,17),"Petal and Dusk Guardians",font=font(30,True),fill="#f6e7dc")
    d.text((24,60),"Near, middle and far in native Minecraft",font=font(16),fill="#c7c4cf")
    for col,(angle,label) in enumerate(((0,"Front"),(40,"Three-quarter"),(90,"Profile"),(180,"Back"))):
        d.text((24+col*290,96),label,font=font(15,True),fill="#ded6cf")
    for idx,(form,dist) in enumerate((("petal",4.8),("dusk",4.8),("petal",8.4),("dusk",8.4),("petal",13.0),("dusk",13.0))):
        top=129+idx*175
        for col,angle in enumerate((0,40,90,180)):
            src=next(r for r in records if r["form"]==form and r["range"]==dist and r["angle"]==angle and r["subpixelSample"]==0)
            with Image.open(ROOT/"gallery"/src["file"]) as img:
                # Same fixed crop for both colorways, keeping the whole silhouette and feet.
                w,h=img.size; native=img.crop((int(w*.26),int(h*.04),int(w*.74),int(h*.98)))
                tile=ImageOps.contain(native,(275,147),Image.Resampling.LANCZOS)
                canvas.paste(tile,(24+col*290+(275-tile.width)//2,top))
        d.text((24,top+148),f"{form.title()}  ·  {dist:g} blocks",font=font(14),fill=COLORS[form.title()])
    canvas.save(ROOT/"guardian-colorways.png",optimize=True)

def movies():
    fps=12
    with Image.open(ROOT/"frames"/SAMPLES[0]["frame"]) as sample:
        width,height=sample.size
    size=(width,height+74);writer=subprocess.Popen([
        "ffmpeg","-hide_banner","-loglevel","error","-y","-f","rawvideo","-pix_fmt","rgb24","-s",f"{size[0]}x{size[1]}",
        "-r",str(fps),"-i","-","-an","-c:v","libx264","-crf","21","-preset","veryfast","-pix_fmt","yuv420p","-movflags","+faststart",
        str(ROOT/f"guardian-{ROLE}.mp4")],stdin=subprocess.PIPE)
    try:
        for k in (0,1,6,2,3,4):
            rows=[r for r in SAMPLES if r["trial"]==k]
            data=OUTCOMES[k];action="Second warning" if data["warning"]==2 else "First warning"
            last=min(rows[-1]["tick"],max((r["tick"] for r in rows if r["action"] in ("flip","grip")),default=100)+57)
            i=0; prev=None; frame=None
            for sample_tick in np.arange(rows[0]["tick"],last+.01,20/fps):
                while i+1<len(rows) and abs(rows[i+1]["tick"]-sample_tick)<abs(rows[i]["tick"]-sample_tick):i+=1
                if i!=prev:
                    with Image.open(ROOT/"frames"/rows[i]["frame"]) as img:
                        frame=Image.new("RGB",size,"#171924");frame.paste(img.convert("RGB"),(0,0))
                    label=f"{data['variant']} Guardian   /   {action}   /   {data['sex'].title()}"
                    d=ImageDraw.Draw(frame);d.text((15,height+6),label,font=font(21,True),fill=COLORS[data["variant"]])
                    description="Warned player" if ROLE=="player" else "Observer replay"
                    info=f"{description}  ·  {data['serverRise']:.1f} blocks high"
                    if data["warning"]==2:info+=f"  ·  {data['horizontal']:.1f} blocks across"
                    d.text((16,height+39),info,font=font(16),fill="#e7e2dc");prev=i
                writer.stdin.write(frame.tobytes())
    finally:
        writer.stdin.close()
        need(writer.wait()==0,"FFmpeg failed to encode exact capture frames")

def chart():
    rec=OUTCOMES[1]
    with (ROOT/"track-1.csv").open() as f:rows=list(csv.DictReader(f))
    released=next(i for i,r in enumerate(rows) if i>0 and rows[i-1]["phase"]=="flip" and r["phase"]!="flip")
    x0,y0,z0=(float(rows[released][key]) for key in ("playerX","playerY","playerZ")); ground=min(float(r["playerY"]) for r in rows)
    points=[(math.hypot(float(r["playerX"])-x0,float(r["playerZ"])-z0),float(r["playerY"])-ground) for r in rows[released:]]
    im=Image.new("RGB",(940,465),"#1b2030");d=ImageDraw.Draw(im)
    d.text((28,17),"Petal Guardian’s second throw",font=font(29,True),fill="#f4d8e2")
    d.text((30,61),"Warned player’s recorded position on each server tick",font=font(17),fill="#c5cadd")
    left,bottom,scale=72,382,30
    max_y=math.ceil(max(y for x,y in points));scale=min(30,270/max(1,max_y))
    for y in range(0,max(10,max_y)+1,2):
        by=bottom-y*scale;d.line((left,by,888,by),fill="#394258")
        d.text((left-12,by),str(y),font=font(14),fill="#d6daea",anchor="rm")
    for x in range(0,26,5):
        bx=left+x*30;d.line((bx,112,bx,bottom),fill="#394258")
        d.text((bx,bottom+9),str(x),font=font(14),fill="#d6daea",anchor="mt")
    pp=[(left+x*30,bottom-y*scale) for x,y in points];d.line(pp,fill="#ff9ecc",width=4)
    for px,py in pp[::2]:d.ellipse((px-3,py-3,px+3,py+3),fill="#fff1df")
    d.text((25,106),"Height",font=font(15),fill="#d8dce6");d.text((left,423),"Horizontal distance from release, in blocks",font=font(16),fill="#d8dce6")
    d.text((910,427),f"{rec['horizontal']:.1f} across   ·   {rec['serverRise']:.1f} high",font=font(17,True),fill="#ffb5d4",anchor="ra")
    im.save(ROOT/"petal-trajectory.png",optimize=True)

camera_checks()
if ROLE=="player":
    from eye_clearance import check_views
    check_views(ROOT, SAMPLES)
movies()
if ROLE=="observer":
    gallery()
    chart()
(ROOT/"camera-checks.json").write_text(json.dumps(CHECKED,indent=2))
print("GUARDIAN NATIVE CAMERA AND FOOTAGE PASS",ROLE,[(r["trial"],r["frames"]) for r in CHECKED])
