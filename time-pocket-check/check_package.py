import json
import sys
from zipfile import ZipFile

with ZipFile(sys.argv[1]) as jar:
    entries = set(jar.namelist())
    assert not any(n.startswith(("dev/uwu/pocketcheck/", "time-pocket-check/")) for n in entries)
    assert "dev/uwu/dimension/TimePocket.class" in entries
    root = "assets/uwudimension/"
    lang = json.loads(jar.read(root + "lang/en_us.json"))
    assert lang["item.uwudimension.pocket_clock_prototype"] == "UwUzi"
    sounds = json.loads(jar.read(root + "sounds.json"))
    for name in ("pocket_tick", "pocket_tock"):
        assert sounds[name]["sounds"] == ["uwudimension:" + name]
        assert jar.read(root + "sounds/" + name + ".ogg").startswith(b"OggS")
    for name in ("clock_star", "pocket_clock_prototype"):
        model = json.loads(jar.read(root + "models/item/" + name + ".json"))
        assert model["textures"]["layer0"].startswith("minecraft:item/")
    for ext in ("vsh", "fsh", "json"):
        assert root + "shaders/core/clock_negative." + ext in entries
print("Time pocket product contains its effects and no disposable block-breaking fixture")
