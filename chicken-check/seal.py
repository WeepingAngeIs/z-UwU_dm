"""Compare decompressed production entries against the installed, already accepted build."""
import hashlib
import json
import os
from pathlib import Path
from zipfile import ZipFile

BASE_SHA = "21f6780d5f2cf0176716828116abbbd530563b7dbfecd3db7e5feac7cd39ef41"
def sha(data):
    return hashlib.sha256(data).hexdigest()

def only(directory):
    [jar] = list(Path(directory).glob("*.jar"))
    return jar

def contents(path):
    with ZipFile(path) as z:
        files = [info.filename for info in z.infolist() if not info.is_dir()]
        assert len(files) == len(set(files)), "duplicate entries"
        return {name: z.read(name) for name in files}

base = only("build/accepted")
assert sha(base.read_bytes()) == BASE_SHA, "wrong reference JAR"
old = contents(base)
candidate = only("build/libs")
new = contents(candidate)
assert old.keys() <= new.keys(), "old product entries missing"
art = json.loads(Path("chicken-check/approved-art.json").read_text())
for name, expected in art.items():
    assert sha(new[name]) == expected, name
classes = {"ChickenCoatArt", "NativeChickens", "NativeFarmSpawns", "mixin/ChickenBirthCoatMixin",
           "mixin/FarmHeadPartsAccessor", "mixin/ChickenNativeCoatMixin", "mixin/NativeFarmGenerationMixin"}
added_classes = set()
for name in new.keys() - old.keys() - art.keys():
    assert name.startswith("dev/uwu/dimension/") and name.endswith(".class"), name
    cls = name.removeprefix("dev/uwu/dimension/").removesuffix(".class").split("$")[0]
    assert cls in classes, name
    added_classes.add(cls)
assert classes == added_classes
changed = {n for n in old if old[n] != new[n]}
assert changed == {"META-INF/neoforge.mods.toml", "uwudimension.mixins.json"}, sorted(changed)
assert old["META-INF/neoforge.mods.toml"].replace(b"-uwuzi.1\"",b"-uwuzi.1-chickens.1\"") == new["META-INF/neoforge.mods.toml"]
config = json.loads(new["uwudimension.mixins.json"])
for side, expected in {"mixins": ["ChickenBirthCoatMixin","NativeFarmGenerationMixin"],
                       "client": ["FarmHeadPartsAccessor","ChickenNativeCoatMixin"]}.items():
    assert config[side][:2] == expected
    config[side] = config[side][2:]
assert config == json.loads(old["uwudimension.mixins.json"])
out = Path("build/chicken-candidate")
out.mkdir(parents=True, exist_ok=True)
name = "UwU-Dimension-1.21.1-TEST-native-chickens-" + os.environ["GITHUB_SHA"][:12] + ".jar"
jar = out/name
jar.write_bytes(candidate.read_bytes())
digest = sha(jar.read_bytes())
(out/"SHA256SUMS.txt").write_text(digest+"  "+name+"\n")
(out/"provenance.json").write_text(json.dumps({"commit":os.environ["GITHUB_SHA"],"jar_sha256":digest,
    "baseline_sha256":BASE_SHA,"run_id":os.environ["GITHUB_RUN_ID"],"unchanged_original_entries":len(old)-len(changed),
    "changed_existing_entries":sorted(changed),"identical_approved_art":sorted(art)},indent=2)+"\n")
with open(os.environ["GITHUB_ENV"],"a") as f:
    f.write(f"UWU_EXPECTED_JAR_SHA256={digest}\nUWU_JAR={jar.resolve()}\n")
print(f"Sealed {digest}, {len(old)-len(changed)} old entries byte-identical, {len(art)} approved art entries")
