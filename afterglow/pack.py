"""Build and verify the same Iris ZIP on Windows and GitHub's standard Linux runner."""
import argparse
import hashlib
import re
import struct
import subprocess
import tempfile
from pathlib import Path
from zipfile import ZipFile, ZipInfo, ZIP_STORED

ROOT = Path(__file__).resolve().parent
SHADERS = ROOT / "shaders"
EXPECTED_DIMS = {"end": "minecraft:the_end", "reach": "uwudimension:sakura_reach", "ordinary": "minecraft:overworld"}
SHADER_TYPES = {".vsh", ".fsh", ".glsl"}

def digest(data):
    return hashlib.sha256(data).hexdigest()

def expand(path, parents=()):
    if path in parents:
        raise ValueError("Cyclic shader include: " + str(path))
    parts = []
    for line in path.read_text(encoding="utf-8").splitlines():
        match = re.fullmatch(r'\s*#include "(/[^"]+)"\s*', line)
        if match:
            include = (SHADERS / match[1][1:]).resolve()
            if SHADERS not in include.parents:
                raise ValueError("Out-of-pack include")
            parts.append(expand(include, parents + (path,)))
        else:
            parts.append(line)
    return "\n".join(parts) + "\n"

def create(output_dir, compiler=None):
    output_dir.mkdir(parents=True, exist_ok=True)
    archive = output_dir / "UwU Afterglow.zip"
    data = {}
    for file in sorted(SHADERS.rglob("*")):
        if not file.is_file(): continue
        name=file.relative_to(ROOT).as_posix()
        if file.suffix not in SHADER_TYPES | {".lang",".png",".mcmeta",".properties"}:
            raise ValueError("Unexpected pack member: "+name)
        content=file.read_bytes() if file.suffix==".png" else file.read_text(encoding="utf-8").replace("\r\n","\n").encode()
        data[name]=content
    dimensions=dict(re.findall(r'^dimension\.(\w+)\s*=\s*(\S+)\s*$',data["shaders/dimension.properties"].decode(),re.M))
    assert dimensions==EXPECTED_DIMS
    assert sum(len(list((SHADERS/dim).glob("*.fsh"))) for dim in dimensions)==7
    assert len({x for x in data if x.endswith(".png")})==6
    shaders_props=data["shaders/shaders.properties"].decode()
    for ref in re.findall(r'^customTexture\.\w+\s*=\s*(\S+)',shaders_props,re.M):
        img=data["shaders/"+ref]
        assert max(struct.unpack(">II",img[16:24]))<=2048 and img[:8]==b"\x89PNG\r\n\x1a\n"
    pairs=sorted(p for dim in dimensions for p in (SHADERS/dim).glob("*.vsh"))
    assert all(p.with_suffix(".fsh").is_file() for p in pairs)
    for vert in pairs:
        for p in [vert,vert.with_suffix(".fsh")]:
            src=expand(p)
            assert src.startswith("#version 330 compatibility\n")
            for operands in re.findall(r"const vec4 colortex\d+ClearColor\s*=\s*vec4\(([^)]*)\)",src):
                assert len(operands.split(","))==4,"Iris 1.8.14 requires four explicit clear-color values"
    if compiler:
        with tempfile.TemporaryDirectory() as temp:
            for vert in pairs:
                for moon in (0,1,2) if "skytextured" in vert.name else (0,):
                    for quality in (1,2) if vert.parent.name=="reach" else (2,):
                        inputs=[]
                        for p,ext in [(vert,"vert"),(vert.with_suffix(".fsh"),"frag")]:
                            src=expand(p).replace("#version 330 compatibility","#version 330 compatibility\n#define MC_RENDER_STAGE_MOON 4\n#define MC_RENDER_STAGE_SKY 1",1)
                            src=re.sub(r'#define REACH_MOON \d',f'#define REACH_MOON {moon}',src)
                            src=re.sub(r'#define AFTERGLOW_QUALITY \d',f'#define AFTERGLOW_QUALITY {quality}',src)
                            dest=Path(temp)/("stage."+ext)
                            dest.write_text(src,encoding="utf-8")
                            inputs.append(str(dest))
                        completed=subprocess.run([compiler,"-l",*inputs],capture_output=True,text=True)
                        if completed.returncode:
                            raise RuntimeError(f"{vert.parent.name}/{vert.stem} moon={moon} quality={quality}\n{completed.stdout}{completed.stderr}")
    staging=archive.with_suffix(".tmp")
    with ZipFile(staging,"w",compression=ZIP_STORED) as out:
        for name,content in data.items():
            info=ZipInfo(name,(2026,11,24,0,0,0))
            info.create_system=3
            info.external_attr=0o100644<<16
            out.writestr(info,content)
    with ZipFile(staging) as reread:
        assert reread.namelist()==list(data) and reread.testzip() is None
        assert all(reread.read(n)==c for n,c in data.items())
    staging.replace(archive)
    identity=digest(archive.read_bytes())
    (output_dir/"UwU Afterglow.sha256").write_text(identity+"  UwU Afterglow.zip\n",encoding="ascii")
    print(f"{archive.name}  {identity}  {archive.stat().st_size} bytes, {len(data)} verified members")
    return identity

if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("directory",type=Path)
    parser.add_argument("--compiler")
    args=parser.parse_args()
    create(args.directory,args.compiler)
