#!/usr/bin/env python3
"""Hosted source proof for the reviewed weapon geometry; no rendering shortcuts.

Run: python3 weapon-audit/verify_geometry.py
The separate native client probe verifies these models after Minecraft bakes them.
This check deliberately requires the public GitHub Actions environment.
"""
from __future__ import annotations

from hashlib import sha256
import json
import math
import os
from pathlib import Path
import struct
import subprocess
import sys
import zlib

ROOT = Path(__file__).resolve().parents[1]
BASELINE = "1f489c5308b686a30672ec19b671a64534eb28a4"
ASSETS = "src/main/resources/assets/uwudimension/"


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def baseline(path):
    return subprocess.check_output(["git", "show", f"{BASELINE}:{path}"], cwd=ROOT)


def rgba_png(encoded):
    """Decode the shipped 8-bit RGBA PNGs with only Python's standard library."""
    require(encoded[:8] == b"\x89PNG\r\n\x1a\n", "Invalid PNG signature")
    offset, compressed = 8, bytearray()
    width = height = None
    while offset < len(encoded):
        size = struct.unpack(">I", encoded[offset:offset + 4])[0]
        kind, body = encoded[offset + 4:offset + 8], encoded[offset + 8:offset + 8 + size]
        if kind == b"IHDR":
            width, height, bits, color, compression, filtering, interlace = struct.unpack(">IIBBBBB", body)
            require((bits, color, compression, filtering, interlace) == (8, 6, 0, 0, 0),
                    "Expected the shipped non-interlaced RGBA8 texture")
        elif kind == b"IDAT":
            compressed.extend(body)
        offset += size + 12
    require(width is not None and height is not None, "PNG dimensions missing")
    data, stride = zlib.decompress(compressed), width * 4
    require(len(data) == height * (stride + 1), "PNG scanline length changed")
    previous, rows = bytearray(stride), []
    for y in range(height):
        start = y * (stride + 1)
        method, row = data[start], bytearray(data[start + 1:start + 1 + stride])
        require(0 <= method <= 4, "Invalid PNG filter")
        for index in range(stride):
            left = row[index - 4] if index >= 4 else 0
            above = previous[index]
            upper_left = previous[index - 4] if index >= 4 else 0
            if method == 0:
                prediction = 0
            elif method == 1:
                prediction = left
            elif method == 2:
                prediction = above
            elif method == 3:
                prediction = (left + above) // 2
            else:
                p = left + above - upper_left
                candidates = [(abs(p - left), left), (abs(p - above), above), (abs(p - upper_left), upper_left)]
                prediction = min(enumerate(candidates), key=lambda pair: (pair[1][0], pair[0]))[1][1]
            row[index] = (row[index] + prediction) & 255
        rows.append(row)
        previous = row
    return width, height, rows


def normal_after(values, factor):
    x, y, z = values
    z /= factor
    magnitude = math.sqrt(x * x + y * y + z * z)
    return (x / magnitude, y / magnitude, z / magnitude)


def verify_returnbloom(record):
    path, factor = record["model"], record["z_scale"]
    before = baseline(path)
    current = (ROOT / path).read_bytes()
    require(sha256(before).hexdigest() == record["original_sha256"], "Returnbloom baseline mismatch")
    require(sha256(current).hexdigest() == record["sha256"], "Returnbloom authored file mismatch")
    old_lines, new_lines = before.decode().splitlines(keepends=True), current.decode().splitlines(keepends=True)
    require(len(old_lines) == len(new_lines), "Returnbloom topology changed")
    vertices = normals = 0
    old_z, new_z = [], []
    for old, new in zip(old_lines, new_lines):
        if old.startswith("v "):
            a, b = old.split(), new.split()
            require(b[:3] == a[:3] and len(b) == 4, "Returnbloom XY changed")
            expected = .5 + (float(a[3]) - .5) * factor
            require(abs(float(b[3]) - expected) < 1e-10, "Returnbloom Z is not the reviewed affine change")
            old_z.append(float(a[3])); new_z.append(float(b[3])); vertices += 1
        elif old.startswith("vn "):
            expected = normal_after(tuple(map(float, old.split()[1:])), factor)
            actual = tuple(map(float, new.split()[1:]))
            require(len(actual) == 3 and all(abs(a - b) < 1e-10 for a, b in zip(actual, expected)),
                    "Returnbloom normals do not use the inverse transpose")
            require(abs(sum(v * v for v in actual) - 1) < 1e-10, "Returnbloom normal not normalized")
            normals += 1
        else:
            require(old == new, "Returnbloom UV, face, material or non-geometry text changed")
    require(vertices == record["vertices"] == 604 and normals == record["normals"] == 1224,
            "Returnbloom vertex/normal inventory changed")
    return {"path": path, "vertices": vertices, "normals": normals, "z_scale": factor,
        "old_depth_model_pixels": (max(old_z) - min(old_z)) * 16,
        "new_depth_model_pixels": (max(new_z) - min(new_z)) * 16,
        "xy_uv_topology_materials_unchanged": True, "inverse_transpose_normals": True}


def verify_model(record, textures):
    path = record["model"]
    old_bytes = baseline(path)
    require(sha256(old_bytes).hexdigest() == record["original_sha256"], "Wrong model baseline: " + path)
    encoded = (ROOT / path).read_bytes()
    require(sha256(encoded).hexdigest() == record["sha256"], "Model differs from authored manifest: " + path)
    before, after = json.loads(old_bytes), json.loads(encoded)
    require(after["loader"] == "uwudimension:blade_relief", "Wrong visual loader: " + path)
    require(after["base"] == {key: value for key, value in before.items() if key != "overrides"},
            "The native model changed: " + path)
    require(set(after) == set(before) | {"base", "relief"}, "Unexpected model keys: " + path)
    for key in before:
        if key != "loader":
            require(after[key] == before[key], "Native property or override changed: " + path + ":" + key)
    relief = after["relief"]
    require(relief["loader"] == "neoforge:elements" and relief["render_type"] == "minecraft:cutout",
            "Relief must use native cutout elements: " + path)
    require(relief["textures"] == before["textures"] | {"particle": "#layer0"}, "Relief materials changed")
    require(relief.get("transform") == before.get("transform"), "Relief root transform differs from native")
    for texture in before["textures"].values():
        if not texture.startswith("uwudimension:"):
            continue
        image_path = ASSETS + "textures/" + texture.split(":", 1)[1] + ".png"
        if image_path not in textures:
            image = (ROOT / image_path).read_bytes()
            require(image == baseline(image_path), "Original PNG modified: " + image_path)
            textures[image_path] = rgba_png(image)
    image_path = ASSETS + "textures/" + before["textures"]["layer0"].split(":", 1)[1] + ".png"
    width, height, pixels = textures[image_path]
    require((width, height) == (32, 32), "Unexpected base sprite dimensions")
    expected_pixels = {tuple(p) for p in record["blade_pixels"]}
    require(len(expected_pixels) == len(record["blade_pixels"]) and expected_pixels, "Invalid blade mask")
    body_caps, z_bounds = set(), []
    for element in relief["elements"]:
        a, b = element["from"], element["to"]
        require(len(a) == len(b) == 3 and all(math.isfinite(v) for v in a + b), "Invalid relief coordinates")
        require(a[2] >= 8.5 or b[2] <= 7.5, "Relief intersects the original central sidewalls")
        require(6.8 <= a[2] <= b[2] <= 9.2, "Relief exceeds the modest reviewed depth range")
        require(a[0] <= b[0] and a[1] <= b[1], "Inverted relief bounds")
        x, y = round(a[0] * 2), 31 - round(a[1] * 2)
        require((x, y) in expected_pixels, "Unreviewed metal pixel")
        require([a[0], a[1], b[0], b[1]] == [x / 2, (31 - y) / 2, (x + 1) / 2, (32 - y) / 2],
                "Relief changed the original pixel's XY footprint")
        require(pixels[y][x * 4 + 3] == 255, "Relief fills an original transparent gap")
        require(len(element["faces"]) == 1, "Relief must omit all interior and duplicate faces")
        direction, face = next(iter(element["faces"].items()))
        layer = face["tintindex"]
        require(face["texture"] == "#layer" + str(layer), "Relief texture mapping changed")
        if direction in ("north", "south"):
            require((direction == "south") == (a[2] >= 8.5), "Relief has an interior cap")
            u0, u1, v0, v1 = x / 2, (x + 1) / 2, y / 2, (y + 1) / 2
            require(face["uv"] == ([u0, v0, u1, v1] if direction == "south" else [u1, v0, u0, v1]),
                    "Relief face no longer samples the same painted pixel")
            if layer == 0:
                body_caps.add((x, y, direction))
            else:
                require(face.get("neoforge_data") == before.get("neoforge_data", {}).get("layers", {}).get(str(layer)),
                        "Relief glow lost the original emission/color/AO data")
        else:
            require(layer == 0 and direction in ("east", "west", "up", "down"), "Unexpected side layer")
            require(face["uv"] == [(x + .1) / 2, (y + .1) / 2, (x + .9) / 2, (y + .9) / 2],
                    "Relief shoulder no longer uses its original metal pixel")
        z_bounds.extend((a[2], b[2]))
    require(body_caps == {(x, y, face) for x, y in expected_pixels for face in ("north", "south")},
            "Missing front/back metal cap")
    require(len(relief["elements"]) == record["element_count"], "Element count changed")
    return {"model": path, "metal_pixels": len(expected_pixels), "elements": len(relief["elements"]),
        "relief_bounds_z_model_pixels": [min(z_bounds), max(z_bounds)],
        "native_base_preserved": True, "native_overrides_transforms_materials_preserved": True,
        "original_silhouette_holes_preserved": True, "no_central_duplicate_sidewalls": True,
        "emissive_layer_face_data_preserved": True}


def main():
    require(os.environ.get("GITHUB_ACTIONS") == "true", "Run geometry validation only on public GitHub Actions")
    require(os.environ.get("GITHUB_REPOSITORY") == "WeepingAngeIs/z-UwU_dm", "Wrong repository")
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
    require(event.get("repository", {}).get("private") is False, "Repository must remain public")
    require(not sys.flags.optimize, "Assertions must remain enabled")
    manifest = json.loads((ROOT / "weapon-audit/relief-manifest.json").read_text())
    require(manifest["baseline"] == BASELINE and manifest["baseline_gallery_run"] == "44956844489", "Wrong reviewed baseline")
    require(len(manifest["models"]) == 174 and len({r["item"] for r in manifest["models"]}) == 102, "Weapon model coverage changed")
    require(len(manifest["production_paths"]) == len(set(manifest["production_paths"])) == 176, "Production scope changed")
    textures = {}
    models = [verify_model(record, textures) for record in manifest["models"]]
    mesh = verify_returnbloom(manifest["returnbloom"])
    report = {"baseline_source_sha": BASELINE, "baseline_gallery_run": "44956844489",
        "models": models, "unchanged_source_pngs": len(textures), "returnbloom": mesh,
        "native_rendering_required": "weapon-check verifies the baked passes, transforms and actual screenshots"}
    destination = ROOT / "build/weapon-release/geometry-source-proof.json"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, indent=2) + "\n")
    print(f"UWU WEAPONS GEOMETRY SOURCE PASS models={len(models)} pngs={len(textures)} "
          f"returnbloom_vertices={mesh['vertices']} normals={mesh['normals']}")


if __name__ == "__main__":
    main()
