"""Check the sealed product and exactly the three approved derived cues."""
import hashlib
import json
import sys
from pathlib import Path
from zipfile import ZipFile

CUES = {
    "opening": "137a9585b99d8e6572a3e40c426a771651f9ea5adea292f35d4c9335755bf71e",
    "clock_loop": "f16f7e3657657645ad4e4fd4c4c67b084ccc116c399914b8710a91831de83f66",
    "thaw": "d630b4708a2c6958dfe0f68669cfb5233c65fff38f303c2a452216c7323728b0",
}
PUBLIC_PROTOTYPE = {
    "pocket_tick": "23a4d8ededc308e045c418524c4d3e48221b8659",
    "pocket_tock": "6e9ce3e84fdcc49b1d53a2386dbb0cb2b6c482ce",
}

def require(ok, message):
    if not ok:
        raise AssertionError(message)

jar_path = Path(sys.argv[1]).resolve(strict=True)
expected = sys.argv[2].lower()
require(hashlib.sha256(jar_path.read_bytes()).hexdigest() == expected, "Sealed JAR hash differs")
measurements = {}
with ZipFile(jar_path) as jar:
    names = jar.namelist()
    entries = set(names)
    require(len(names) == len(entries), "Duplicate product JAR entries")
    require(not any(n.startswith(("dev/uwu/pocketcheck/", "time-pocket-check/")) for n in entries), "Disposable probe leaked into product")
    require(not any(n.lower().endswith((".mp3", ".wav")) for n in entries), "Original or unrelated audio format in product")
    require("dev/uwu/dimension/TimePocket.class" in entries, "Missing TimePocket product class")
    root = "assets/uwudimension/"
    cue_root = root + "sounds/uwuzi/"
    require({n for n in entries if n.startswith(cue_root) and not n.endswith("/")} ==
            {cue_root + name + ".ogg" for name in CUES}, "Embedded cue set differs from the exact three approved cues")
    require(not any("uwuzi" in n.lower() and n.lower().endswith(".ogg") and not n.startswith(cue_root)
                    for n in entries), "Additional UwUzi audio outside the approved cue directory")
    lang = json.loads(jar.read(root + "lang/en_us.json"))
    require(lang["item.uwudimension.pocket_clock_prototype"] == "UwUzi", "Standard item name differs")
    require(lang["item.uwudimension.uwuzi_no_cooldown"] == "UwUzi (No Cooldown)", "No-cooldown item name differs")
    variant = json.loads(jar.read(root + "models/item/uwuzi_no_cooldown.json"))
    require(variant["parent"] == "uwudimension:item/pocket_clock_prototype", "No-cooldown held model differs")
    sounds = json.loads(jar.read(root + "sounds.json"))
    for name, wanted in CUES.items():
        data = jar.read(cue_root + name + ".ogg")
        actual = hashlib.sha256(data).hexdigest()
        require(data.startswith(b"OggS") and actual == wanted, "Approved cue bytes differ: " + name)
        require(sounds["uwuzi_" + name]["sounds"] == [{
            "name": "uwudimension:uwuzi/" + name, "stream": False, "preload": True,
        }], "Cue sound definition differs: " + name)
        measurements[name] = {"bytes": len(data), "sha256": actual}
    for name, blob in PUBLIC_PROTOTYPE.items():
        require(sounds[name]["sounds"] == ["uwudimension:" + name], "Public prototype cue definition differs")
        data = jar.read(root + "sounds/" + name + ".ogg")
        actual_blob = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
        require(data.startswith(b"OggS") and actual_blob == blob, "Public prototype cue changed: " + name)
    require(json.loads(jar.read(root + "models/item/pocket_clock_prototype.json"))["textures"]["layer0"] ==
            "minecraft:item/clock_00", "Existing held clock texture differs")
    require(json.loads(jar.read(root + "models/item/clock_star.json"))["textures"]["layer0"] ==
            "minecraft:item/nether_star", "Existing star texture differs")
    require(json.loads(jar.read(root + "models/item/uwuzi_inventory.json"))["textures"]["layer0"] ==
            "uwudimension:item/uwuzi_inventory", "GUI icon model differs")
    icon = jar.read(root + "textures/item/uwuzi_inventory.png")
    require(hashlib.sha1(b"blob " + str(len(icon)).encode() + b"\0" + icon).hexdigest() ==
            "c725089068e77ff2643b0025ef38398285364cd8", "Approved GUI icon bytes differ")
    for ext in ("vsh", "fsh", "json"):
        require(root + "shaders/core/clock_negative." + ext in entries, "Missing color filter resource")
record = {"jar_sha256": expected, "embedded_cues": measurements, "public_prototype_blobs": PUBLIC_PROTOTYPE}
if len(sys.argv) > 3:
    Path(sys.argv[3]).write_text(json.dumps(record, indent=2) + "\n")
print("UWUZI PACKAGE PASS " + json.dumps(record, sort_keys=True))

