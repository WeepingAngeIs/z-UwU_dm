"""Freeze the tested archive and verify its game payload against the preceding shipped JAR."""
from hashlib import sha256
from io import BytesIO
import json
from pathlib import Path
import re
import shutil
import sys
from zipfile import ZipFile

OLD_ARCHIVE = "d9cee2c877790f21e833b9a46e8652bd09baa243c9fc9c22209b204c1f33fa2c"
OLD_JAR = "b500b85abce49b661d8a93dd0df9f31c565e0535de1082ffedf572a0f4deed1e"
BASE = "072811cb288996978e7f664a3bda47a570fea079"
out = Path("build/village-input")
out.mkdir(parents=True, exist_ok=True)
old_archive = Path(sys.argv[1]).read_bytes()
assert sha256(old_archive).hexdigest() == OLD_ARCHIVE, "Unexpected baseline artifact"
with ZipFile(BytesIO(old_archive)) as archive:
    old_name, = [name for name in archive.namelist() if name.endswith(".jar")]
    baseline = archive.read(old_name)
assert sha256(baseline).hexdigest() == OLD_JAR, "Unexpected baseline JAR"
(out / "baseline.jar").write_bytes(baseline)
candidate, = Path("build/libs").glob("*-villages.1.jar")
shutil.copyfile(candidate, out / "candidate.jar")
shutil.copyfile("build/village-size-check/village-size-probe.jar", out / "probe.jar")
allowed_class = re.compile(r"dev/uwu/dimension/(BlossomVillage|SettlementLayout|MoonveilTreehouse|VillageSakura|VillageHouseLots)(\$[^/]*)?\.class")
with ZipFile(BytesIO(baseline)) as old, ZipFile(candidate) as new:
    entries = sorted({x.filename for jar in (new, old) for x in jar.infolist() if not x.is_dir()})
    changed = []
    for path in entries:
        if path in old.NameToInfo and path in new.NameToInfo and old.read(path) == new.read(path):
            continue
        changed.append(path)
        assert allowed_class.fullmatch(path) or path in {"META-INF/neoforge.mods.toml", "META-INF/MANIFEST.MF"}, path
    assert "dev/uwu/dimension/VillageHouseLots.class" in changed
scope = dict(base=BASE, baselineSha256=OLD_JAR, candidateName=candidate.name,
             candidateSha256=sha256(candidate.read_bytes()).hexdigest(), identicalGameEntries=len(entries)-len(changed),
             changedGameEntries=changed)
(out / "scope.json").write_text(json.dumps(scope, indent=2) + "\n")
(out / "checksums.txt").write_text("".join(sha256(p.read_bytes()).hexdigest() + "  " + p.name + "\n" for p in sorted(out.glob("*.jar"))))
print(json.dumps(scope, indent=2))
