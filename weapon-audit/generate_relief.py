#!/usr/bin/env python3
"""Author reviewed blade geometry from the shipped PNGs; never compile or render.

The source selection follows actual hosted baseline run 44956844489.
The native sprite model is retained intact; only reviewed metal pixels receive
front/back extension slabs. PNGs, native central sidewalls and GUI art are kept.
"""
from __future__ import annotations

import argparse
from collections import deque
from copy import deepcopy
from hashlib import sha256
import json
import math
from pathlib import Path
import subprocess

from PIL import Image

BASELINE = "1f489c5308b686a30672ec19b671a64534eb28a4"
BASELINE_GALLERY_RUN = "44956844489"
RETURNBLOOM_Z_SCALE = 3.0
PREFIX = "src/main/resources/assets/uwudimension/"
NATIVE = {
    "blossom_greatsword": "axe", "bellheart_axe": "axe", "lockkeeper_axe": "axe",
    "vesper_scythe": "scythe", "rose_dagger": "dagger", "lantern_spear": "spear",
    "tidelace_trident": "spear",
}
BLADES = {"axe", "chakram", "dagger", "scythe", "spear"}
# Pixel-space masks, origin at the top-left of the shipped 32px face art.
# They are intentionally narrower than the weapon silhouette: flower hubs,
# tassels, ribbons, grips and the black contour retain their native extrusion.
NATIVE_POLYGONS = {
    "blossom_greatsword": [
        [(8, 12), (9, 7), (14, 3), (23, 0), (19, 6), (15, 11)],
        [(23, 9), (28, 7), (31, 9), (30, 16), (26, 21), (23, 21)]],
    "bellheart_axe": [
        [(7, 14), (6, 8), (8, 4), (17, 0), (20, 1), (17, 6), (15, 12), (10, 13)],
        [(24, 6), (29, 7), (31, 12), (29, 18), (23, 20), (20, 18), (23, 12)]],
    "lockkeeper_axe": [
        [(7, 12), (7, 7), (13, 1), (20, 0), (18, 5), (15, 11), (11, 14)],
        [(23, 4), (28, 5), (30, 10), (28, 15), (24, 19), (22, 16), (24, 11)]],
    "rose_dagger": [[(14, 15), (14, 10), (18, 6), (29, 2), (27, 11), (24, 16), (18, 16)]],
    "lantern_spear": [[(21, 9), (21, 5), (30, 0), (30, 4), (25, 10)]],
    "tidelace_trident": [
        [(16, 11), (15, 8), (17, 4), (22, 1), (24, 2), (21, 6), (19, 10)],
        [(19, 10), (21, 5), (26, 1), (28, 1), (24, 7), (22, 10)],
        [(21, 15), (25, 13), (29, 7), (31, 7), (29, 14), (24, 18)]],
}
SHAPE_POLYGONS = {
    "scythe": [[(0, 10), (0, 5), (7, 2), (15, 1), (22, 3), (20, 9), (12, 9), (5, 11)]],
    "dagger": [[(15, 18), (15, 10), (21, 5), (29, 0), (29, 8), (24, 14), (18, 18)]],
    "spear": [[(19, 14), (18, 7), (24, 3), (29, 0), (30, 5), (26, 11), (22, 14)]],
}
DEPTH = {"axe": (.80, .15, 1.10), "dagger": (.80, .15, 1.10),
         "spear": (.76, .14, 1.04), "scythe": (.74, .14, 1.02),
         "chakram": (.74, .12, .98)}


def original(repo, relative):
    return subprocess.check_output(["git", "show", f"{BASELINE}:{relative}"], cwd=repo)


def inside(x, y, polygon):
    result = False
    j = len(polygon) - 1
    for i, (px, py) in enumerate(polygon):
        qx, qy = polygon[j]
        if (py > y) != (qy > y) and x < (qx - px) * (y - py) / (qy - py) + px:
            result = not result
        j = i
    return result


def in_rectangle(x, y, rect):
    return rect[0] <= x < rect[2] and rect[1] <= y < rect[3]


def metal_region(item, shape, x, y):
    if item in NATIVE_POLYGONS:
        return any(inside(x, y, polygon) for polygon in NATIVE_POLYGONS[item])
    if shape in SHAPE_POLYGONS:
        return any(inside(x, y, polygon) for polygon in SHAPE_POLYGONS[shape])
    if shape == "axe":
        left = [(7, 18), (7, 9), (10, 4), (18, 1), (17, 6), (15, 13), (11, 17)]
        right = [(22, 5), (26, 3), (29, 8), (29, 14), (25, 19), (22, 17), (24, 11)]
        return inside(x, y, left) or item.endswith("_greataxe") and inside(x, y, right)
    if shape == "chakram":
        pair = any(item.startswith("counter_" + material + "_") for material in ("iron", "runic")) \
            and item.endswith(("_twinblade", "_rapier"))
        if pair:
            in_ring = any(3.7 ** 2 <= (x - cx) ** 2 + (y - cy) ** 2 <= 8 ** 2
                          for cx, cy in ((10, 21), (21, 11)))
            ornaments = ([(19, 3, 24, 8), (25, 17, 31, 23), (14, 25, 21, 32), (2, 12, 8, 17)]
                         if item.endswith("_rapier") else [(22, 17, 30, 23), (0, 27, 6, 32)])
        else:
            in_ring = 6.5 ** 2 <= (x - 16.5) ** 2 + (y - 15.5) ** 2 <= 13.5 ** 2
            ornaments = [(22, 3, 32, 19), (7, 21, 15, 29)]
        return in_ring and not any(in_rectangle(x, y, rect) for rect in ornaments)
    return False


def depths(item, shape, image):
    assert image.size == (32, 32)
    mask = set()
    for y in range(32):
        for x in range(32):
            r, g, b, alpha = image.getpixel((x, y))
            # Keep the dark painted outline at its original depth. Its adjacent
            # metal pixels create a visible shoulder with their own painted color.
            metal = .2126 * r + .7152 * g + .0722 * b >= 45 and max(r, g, b) >= 70
            if alpha == 255 and metal and metal_region(item, shape, x + .5, y + .5):
                mask.add((x, y))
    assert mask, f"Empty reviewed blade region: {item}"
    distance, queue = {}, deque()
    for x, y in mask:
        if any((x + dx, y + dy) not in mask for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            distance[x, y] = 1
            queue.append((x, y))
    while queue:
        x, y = queue.popleft()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = x + dx, y + dy
            if p in mask and p not in distance:
                distance[p] = distance[x, y] + 1
                queue.append(p)
    edge, step, peak = DEPTH[shape]
    return {p: round(min(peak, edge + step * (distance[p] - 1)), 4) for p in mask}


def face(texture, uv, layer, data=None):
    result = {"texture": texture, "uv": uv, "tintindex": layer}
    if data:
        result["neoforge_data"] = deepcopy(data)
    return result


def cap(x, y, half, texture, layer, front, data=None, offset=0):
    x0, x1, y0, y1 = x / 2, (x + 1) / 2, (31 - y) / 2, (32 - y) / 2
    depth = round(8 + half + offset if front else 8 - half - offset, 6)
    u0, u1, v0, v1 = x / 2, (x + 1) / 2, y / 2, (y + 1) / 2
    return {"name": f"blade_{'front' if front else 'back'}_{layer}_{x}_{y}",
        "from": [x0, y0, 8.5 if front else depth],
        "to": [x1, y1, depth if front else 7.5],
        "shade": not bool(data),
        "faces": {"south" if front else "north":
            face(texture, [u0, v0, u1, v1] if front else [u1, v0, u0, v1], layer, data)}}


def walls(x, y, half, depth_map):
    x0, x1, y0, y1 = x / 2, (x + 1) / 2, (31 - y) / 2, (32 - y) / 2
    # A side face samples only its original metal pixel, with no new color or PNG.
    uv = [(x + .1) / 2, (y + .1) / 2, (x + .9) / 2, (y + .9) / 2]
    result = []
    for direction, dx, dy in (("west", -1, 0), ("east", 1, 0), ("up", 0, -1), ("down", 0, 1)):
        adjacent = depth_map.get((x + dx, y + dy), .5)
        if adjacent >= half:
            continue
        for front in (False, True):
            z0, z1 = (8 + adjacent, 8 + half) if front else (8 - half, 8 - adjacent)
            result.append({"name": f"blade_shoulder_{direction}_{'front' if front else 'back'}_{x}_{y}",
                "from": [x0, y0, round(z0, 6)], "to": [x1, y1, round(z1, 6)], "shade": True,
                "faces": {direction: face("#layer0", uv, 0)}})
    return result


def alpha_union(repo, texture):
    path = repo / PREFIX / ("textures/" + texture.split(":", 1)[1] + ".png")
    image = Image.open(path).convert("RGBA")
    assert image.width == 32 and image.height % 32 == 0
    return {(x, y) for y in range(32) for x in range(32)
            if any(image.getpixel((x, y + frame * 32))[3] > 0 for frame in range(image.height // 32))}


def build(repo, item, shape, path):
    before = json.loads(original(repo, path))
    assert before["loader"] == "neoforge:item_layers", path
    texture = before["textures"]["layer0"]
    image_path = repo / PREFIX / ("textures/" + texture.split(":", 1)[1] + ".png")
    image = Image.open(image_path).convert("RGBA")
    depth_map = depths(item, shape, image)
    elements = []
    for (x, y), half in sorted(depth_map.items()):
        elements.extend((cap(x, y, half, "#layer0", 0, False), cap(x, y, half, "#layer0", 0, True)))
        elements.extend(walls(x, y, half, depth_map))
    for key, texture in before["textures"].items():
        if not key.startswith("layer") or key == "layer0":
            continue
        layer = int(key[5:])
        opaque = alpha_union(repo, texture)
        data = before.get("neoforge_data", {}).get("layers", {}).get(str(layer))
        for (x, y), half in sorted(depth_map.items()):
            if (x, y) in opaque:
                for front in (False, True):
                    elements.append(cap(x, y, half, "#" + key, layer, front, data, .002 * layer))
    # No generated face enters or duplicates the original central sidewall range.
    for element in elements:
        assert element["from"][2] >= 8.5 or element["to"][2] <= 7.5, element
    nested_base = deepcopy(before)
    nested_base.pop("overrides", None)
    materials = deepcopy(before["textures"])
    materials["particle"] = "#layer0"
    relief = {"loader": "neoforge:elements", "textures": materials,
              "render_type": "minecraft:cutout", "elements": elements}
    if "transform" in before:
        relief["transform"] = deepcopy(before["transform"])
    after = deepcopy(before)
    after.update({"loader": "uwudimension:blade_relief", "base": nested_base, "relief": relief})
    for key, value in before.items():
        if key != "loader":
            assert after[key] == value
    assert nested_base == {key: value for key, value in before.items() if key != "overrides"}
    return after, {"model": path, "item": item, "shape": shape,
        "original_sha256": sha256(original(repo, path)).hexdigest(), "texture": before["textures"]["layer0"],
        "blade_pixels": [list(p) for p in sorted(depth_map)],
        "half_depths": sorted(set(depth_map.values())), "element_count": len(elements),
        "native_center_depth": [7.5, 8.5], "extra_faces_inside_original_opaque_pixels": True,
        "original_base_verbatim_except_overrides": True, "gui_uses_original_model": True}


def selected_models(repo, only):
    roster = json.loads(original(repo, PREFIX + "counter_roster.json"))
    roots = dict(NATIVE)
    roots.update({row["id"]: row["shape"] for row in roster if row["shape"] in BLADES})
    if only:
        assert set(only) <= roots.keys(), set(only) - roots.keys()
        roots = {key: roots[key] for key in only}
    result = []
    for item, shape in roots.items():
        names = [item]
        if shape == "scythe":
            names.extend(item + "_" + str(i) for i in range(4))
            if item == "vesper_scythe":
                names.extend(item + "_credit_" + str(i) for i in range(4))
        for name in names:
            result.append((item, shape, PREFIX + "models/item/" + name + ".json"))
    return result


def write_model(path, model):
    # Keep one generated element per line so the actual authored geometry can be
    # reviewed without pages of one-number JSON array lines.
    output = deepcopy(model)
    elements = output["relief"]["elements"]
    output["relief"]["elements"] = "__GENERATED_BLADE_ELEMENTS__"
    serialized = json.dumps(output, indent=2)
    replacement = "[\n" + ",\n".join("      " + json.dumps(element, separators=(",", ":"))
        for element in elements) + "\n    ]"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(serialized.replace('"__GENERATED_BLADE_ELEMENTS__"', replacement) + "\n")


def returnbloom(repo, output):
    path = PREFIX + "models/item/returnbloom_blade.obj"
    before = original(repo, path).decode()
    lines, vertices, normals = [], 0, 0
    for line in before.splitlines(keepends=True):
        if line.startswith("v "):
            values = line.split()
            z = .5 + (float(values[3]) - .5) * RETURNBLOOM_Z_SCALE
            lines.append(f"v {values[1]} {values[2]} {z:.12f}\n")
            vertices += 1
        elif line.startswith("vn "):
            x, y, z = map(float, line.split()[1:])
            z /= RETURNBLOOM_Z_SCALE
            magnitude = math.sqrt(x * x + y * y + z * z)
            lines.append(f"vn {x / magnitude:.12f} {y / magnitude:.12f} {z / magnitude:.12f}\n")
            normals += 1
        else:
            lines.append(line)
    target = output / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text("".join(lines))
    return {"model": path, "z_scale": RETURNBLOOM_Z_SCALE, "z_origin": .5,
        "vertices": vertices, "normals": normals, "xy_uv_topology_materials_unchanged": True,
        "normal_transform": "inverse transpose: normalize(nx, ny, nz / z_scale)",
        "original_sha256": sha256(before.encode()).hexdigest(),
        "sha256": sha256(target.read_bytes()).hexdigest()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--output", type=Path, help="Default: write reviewed source into the checkout")
    parser.add_argument("--items", nargs="*")
    args = parser.parse_args()
    output = args.output or args.repo
    manifest = {"baseline": BASELINE, "baseline_gallery_run": BASELINE_GALLERY_RUN,
        "baseline_gallery_url": "https://github.com/WeepingAngeIs/z-UwU_dm/actions/runs/" + BASELINE_GALLERY_RUN,
        "baseline_gallery_status": "All 427 model variants and 169 held captures completed; later observer login failed",
        "candidate_status": "Source authored; final hosted rendering must pass before delivery", "models": []}
    for item, shape, path in selected_models(args.repo, args.items):
        model, record = build(args.repo, item, shape, path)
        target = output / path
        write_model(target, model)
        record["sha256"] = sha256(target.read_bytes()).hexdigest()
        manifest["models"].append(record)
    manifest["returnbloom"] = returnbloom(args.repo, output)
    manifest["production_paths"] = sorted([record["model"] for record in manifest["models"]] + [
        manifest["returnbloom"]["model"], "src/main/java/dev/uwu/dimension/client/WeaponBladeModels.java"])
    target = output / "weapon-audit/relief-manifest.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps({"models": len(manifest["models"]), "manifest": str(target)}))


if __name__ == "__main__":
    main()
