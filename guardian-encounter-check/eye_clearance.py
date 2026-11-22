"""Measure visible scenery in the real warned player's framebuffer during the two Petal flips."""
import json

import numpy as np
from PIL import Image


def visible_scenery(image):
    # The filmed courtyard has a blue sky and cool gray diorite, smooth stone and andesite.
    # The crosshair alone occupies <0.2% of this crop. Exclude the hotbar and the screen edges.
    pixels = np.asarray(image.convert("RGB"), dtype=np.int16)
    height, width = pixels.shape[:2]
    center = pixels[height // 3:2 * height // 3, 3 * width // 10:7 * width // 10]
    red, green, blue = center.transpose(2, 0, 1)
    sky = (blue > red + 34) & (blue > green + 12) & (blue > 134)
    stone = (np.ptp(center, axis=2) <= 9) & (green > 55)
    return float((sky | stone).mean())


def check_views(root, samples, frame_folder="frames"):
    report = {}
    for number, name in ((1, "male"), (3, "female")):
        rows = [row for row in samples if row["trial"] == number and row["action"] in ("flip", "follow")]
        measured = []
        for row in rows:
            with Image.open(root / frame_folder / row["frame"]) as image:
                fraction = visible_scenery(image)
            measured.append(dict(tick=row["tick"], phase=row["phaseTick"], action=row["action"],
                                 frame=row["frame"], visibleCenterFraction=round(fraction, 4)))
        phases = {int(row["phase"]) for row in measured}
        if len(phases) < 14 or min(phases) > 5 or max(phases) < 22 or len(phases & set(range(16, 22))) < 4:
            raise AssertionError(f"Incomplete native {name} view across the turn: {sorted(phases)}")
        report[name] = measured
    (root / "eye-clearance.json").write_text(json.dumps(report, indent=2))
    for name, measured in report.items():
        obscured = [s for s in measured if s["visibleCenterFraction"] < .08]
        if obscured:
            raise AssertionError(f"Courtyard disappeared from the central first-person {name} view: {obscured}")
    # All of these other encounters have unmodified first-person eyes, including the female control
    # with independent mouse movement, and the male's release onwards.
    ordinary = [r for r in samples if r["trial"] != 1 or r["action"] != "flip" or not r["carried"]]
    if not ordinary or max(r["nativeEyeGap"] for r in ordinary) > .08:
        raise AssertionError("First-person origin moved outside the local male Petal carry")
    if not any(r.get("cameraBarrierChecked", False) for r in samples):
        raise AssertionError("The held camera was never checked against a solid block")
