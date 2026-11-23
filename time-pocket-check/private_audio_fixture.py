"""Mount the approved clips only in a private GitHub runner's disposable client."""
import hashlib
import json
import os
from pathlib import Path
from shutil import copyfile

event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
if os.environ.get("GITHUB_ACTIONS") != "true" or event["repository"]["private"] is not True:
    raise SystemExit("Private UwUzi audio requires a private GitHub Actions run")

source = Path(os.environ["UWUZI_PRIVATE_PACK"]).resolve(strict=True)
destination = Path("build/pocket-client/resourcepacks/uwuzi-private")
manifest = json.loads((source.parent / "cue-measurements.json").read_text())
assets = Path("assets/uwudimension")
paths = [Path("pack.mcmeta"), assets / "sounds.json"]
for cue in ("opening", "clock_loop", "thaw"):
    relative = assets / "sounds" / "uwuzi" / (cue + ".ogg")
    data = (source / relative).read_bytes()
    if not data.startswith(b"OggS") or hashlib.sha256(data).hexdigest() != manifest["cuts"][cue]["encoded_sha256"]:
        raise ValueError("Private clip differs from the approved cut: " + cue)
    paths.append(relative)

for relative in paths:
    target = destination / relative
    target.parent.mkdir(exist_ok=True, parents=True)
    copyfile(source / relative, target)
