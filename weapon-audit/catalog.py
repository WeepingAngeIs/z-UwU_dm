#!/usr/bin/env python3
"""Static inventory of shipped weapon assets; never starts Java or renders a model.

The optional contact sheets copy existing PNG pixels at nearest-neighbour scale.
They are texture-review aids, not substitutes for the hosted in-game gallery.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from hashlib import sha256
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/uwudimension"
BASELINE = "1f489c5308b686a30672ec19b671a64534eb28a4"
RELICS = {
    "cupid_bow": "bow", "ripple_bow": "bow", "ribbonhare_scepter": "wand",
    "blossom_greatsword": "axe", "bellheart_axe": "axe", "lockkeeper_axe": "axe",
    "vesper_scythe": "scythe", "axolotl_whip": "whip", "frost_whip": "whip",
    "rift_whip": "whip", "rose_dagger": "dagger", "satin_whip": "whip",
    "lantern_spear": "spear", "tidelace_trident": "spear", "blue_hour_wand": "wand",
}
SPECIALS = {"glitch_bow": "morph", "heart_launcher": "launcher",
            "bell_hammer": "hammer", "returnbloom_dirk": "dagger", "dawnpetal_axe": "axe"}


def relative(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def digest(path: Path) -> str:
    return sha256(path.read_bytes()).hexdigest()


def walk(value):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from walk(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk(child)


def model_file(ref: str) -> Path | None:
    if not ref.startswith("uwudimension:"):
        return None
    value = ref.split(":", 1)[1]
    return ASSETS / (value if value.endswith(".obj") else "models/" + value + ".json")


def closure(start: Path):
    pending, models, meshes, textures, materials = [start], {}, {}, set(), {}
    while pending:
        path = pending.pop()
        if relative(path) in models or relative(path) in meshes:
            continue
        if path.suffix == ".obj":
            vertices = [list(map(float, line.split()[1:4])) for line in path.read_text().splitlines()
                        if line.startswith("v ")]
            meshes[relative(path)] = {"sha256": digest(path), "vertices": len(vertices),
                "bounds": [[min(v[i] for v in vertices), max(v[i] for v in vertices)] for i in range(3)]}
            for line in path.read_text().splitlines():
                if line.startswith("mtllib "):
                    mtl = path.parent / line[7:].strip()
                    materials[relative(mtl)] = {"sha256": digest(mtl), "text": mtl.read_text()}
            continue
        data = json.loads(path.read_text())
        nodes = list(walk(data))
        elements = [e for node in nodes for e in node.get("elements", [])]
        models[relative(path)] = {
            "sha256": digest(path), "parent": data.get("parent"), "loader": data.get("loader"),
            "nested_loaders": sorted({node["loader"] for node in nodes if "loader" in node}),
            "element_count": len(elements), "textures": data.get("textures", {}),
            "display": data.get("display", {}), "overrides": data.get("overrides", []),
        }
        if elements:
            models[relative(path)]["element_depth_bounds"] = [
                min(e["from"][2] for e in elements), max(e["to"][2] for e in elements)]
        for node in nodes:
            for field in ("parent", "model"):
                ref = node.get(field)
                if isinstance(ref, str) and (target := model_file(ref)) is not None:
                    pending.append(target)
            for value in node.get("textures", {}).values():
                if isinstance(value, str) and value.startswith("uwudimension:"):
                    textures.add(ASSETS / ("textures/" + value.split(":", 1)[1] + ".png"))
    return models, meshes, textures, materials


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--contact-sheets", type=Path)
    args = parser.parse_args()
    from PIL import Image, ImageDraw, ImageFont

    roster = json.loads((ASSETS / "counter_roster.json").read_text())
    items = [{"id": key, "shape": shape, "source": "Arsenal.Relic", "is_weapon": True}
             for key, shape in RELICS.items()]
    items += [{"id": row["id"], "shape": row["shape"], "source": "CounterArsenal.ROSTER",
               "is_weapon": True, "display_name": row["name"], "roster": row,
               "retired_saved_stack_form": row["shape"] == "bow"} for row in roster]
    items += [{"id": key, "shape": shape, "source": "special registry", "is_weapon": True}
              for key, shape in SPECIALS.items()]
    items += [{"id": material + "_" + kind, "shape": kind, "source": "SakuraTools",
               "is_weapon": False, "control": "native material tool"}
              for material in ("silver", "roseglass", "vesper_opal")
              for kind in ("pickaxe", "axe", "shovel", "hoe")]
    items += [{"id": key, "shape": "shield", "source": "HeartShields", "is_weapon": False,
               "control": "native shield"} for key in
              ("heartbriar_shield", "heartwater_window_shield", "petalfold_shield")]

    all_models, all_meshes, all_textures, all_materials = {}, {}, set(), {}
    for item in items:
        path = ASSETS / ("models/item/" + item["id"] + ".json")
        models, meshes, textures, materials = closure(path)
        item.update({"model": relative(path), "model_closure": sorted(models),
                     "meshes": sorted(meshes), "texture_closure": sorted(map(relative, textures))})
        types = {kind for model in models.values() for kind in model["nested_loaders"]}
        item["source_geometry"] = ("OBJ plus JSON elements" if meshes else
            "explicit JSON elements" if any(m["element_count"] for m in models.values()) else
            "NeoForge layered sprite extrusion" if "neoforge:item_layers" in types else
            "vanilla generated/handheld sprite extrusion")
        if item["id"] == "glitch_bow":
            item["source_geometry"] = "stack-dependent overrides to native weapon models"
        all_models.update(models); all_meshes.update(meshes)
        all_textures.update(textures); all_materials.update(materials)

    texture_records, duplicates = {}, defaultdict(list)
    for path in sorted(all_textures):
        with Image.open(path) as image:
            rgba = image.convert("RGBA")
            pixel_hash = sha256(rgba.tobytes()).hexdigest()
            mcmeta = Path(str(path) + ".mcmeta")
            meta = json.loads(mcmeta.read_text()) if mcmeta.exists() else {}
            animation = meta.get("animation")
            frame_width = animation.get("width", min(rgba.size)) if animation is not None else rgba.width
            frame_height = animation.get("height", min(rgba.size)) if animation is not None else rgba.height
            texture_records[relative(path)] = {"sha256": digest(path), "rgba_sha256": pixel_hash,
                "size": list(rgba.size), "frame_size": [frame_width, frame_height],
                "frames": (rgba.width // frame_width) * (rgba.height // frame_height),
                "animation": animation}
            duplicates[(rgba.size, pixel_hash)].append(relative(path))
    forms = list(RELICS) + [row["id"] for row in roster]
    variants = {
        "cupid": {"marker": {"uwudimension:smooth_cupid": True}, "source": "Arsenal.hasSmoothCupidStyle",
            "poses": ["idle", "drawing (<0.4)", "drawing (0.4..0.75)", "drawing (0.75..1)", "drawing (1)"],
            "properties": "drawing: active use item identity; draw: min(1, ticksUsingItem/20)"},
        "ripple": {"properties": "drawing: active use item identity; draw: min(1, ticksUsingItem/20)"},
        "scythe_elements": {"marker": "uwu_element", "values": [0, 1, 2, 3],
            "items": ["vesper_scythe"] + [row["id"] for row in roster if row["shape"] == "scythe"]},
        "vesper_flower_credit": {"marker": "uwu.flower_credit_until", "active": "> level.getGameTime()",
            "elements": [0, 1, 2, 3]},
        "glitch_forms": [{"id": item, "uwu_form": index, "allowed": item != "cupid_bow"}
                         for index, item in enumerate(forms)],
        "glitch_static": {"marker": "uwu_static_until", "active": "> level.getGameTime()"},
        "glitch_notes": "GlitchBow.currentIndex resolves absent, invalid, or Cupid indices to Ripple. "
            "All 147 permitted legacy forms resolve; only active forms enter random choices. "
            "Vesper and counter scythe markers and Ripple use state also apply to the Glitch stack.",
        "heart_launcher": {"property": "uwudimension:recoil", "source": "player item cooldown percent",
            "models": ["heart_launcher_recoil_1", "heart_launcher_recoil_2", "heart_launcher_recoil_3"]},
        "revival": {"marker": "uwu_revival_phase", "values": [0, 1, 2, 3, 4, 5],
            "note": "Property still registered. Root Returnbloom and Dawnpetal models have no overrides; "
            "phase1..5 files only alias the roots, so these are not distinct visible model variants."},
    }
    catalog = {"baseline": BASELINE, "definition": "AcquisitionPolicy.isWeapon + 15 separate tool/shield controls",
        "counts": {"weapons": sum(i["is_weapon"] for i in items), "controls": sum(not i["is_weapon"] for i in items),
            "counter_shapes": dict(sorted(Counter(row["shape"] for row in roster).items())),
            "model_json": len(all_models), "obj_meshes": len(all_meshes), "texture_files": len(all_textures),
            "distinct_rgba_images": len(duplicates)},
        "variants": variants, "items": items, "models": dict(sorted(all_models.items())),
        "meshes": dict(sorted(all_meshes.items())), "materials": dict(sorted(all_materials.items())),
        "textures": texture_records, "exact_texture_duplicates": [v for v in duplicates.values() if len(v) > 1],
        "validation": "Static source/asset inventory only. Actual rendering must be verified by the hosted weapon-check fixture."}
    output = Path(__file__).with_name("catalog.json")
    output.write_text(json.dumps(catalog, indent=2) + "\n")
    print(json.dumps(catalog["counts"]))
    print(relative(output))
    if not args.contact_sheets:
        return
    args.contact_sheets.mkdir(parents=True, exist_ok=True)
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 11)
    representatives = [paths[0] for paths in duplicates.values()]
    # These are labelled raster contact sheets, not lighting or model renderings.
    for page, start in enumerate(range(0, len(representatives), 24), 1):
        sheet = Image.new("RGB", (1200, 1110), "#2d3039")
        draw = ImageDraw.Draw(sheet)
        draw.text((15, 10), f"Shipped PNG pixels — exact duplicate groups represented once — sheet {page}", font=font, fill="white")
        for cell, name in enumerate(representatives[start:start + 24]):
            info = texture_records[name]
            with Image.open(ROOT / name) as texture:
                fw, fh = info["frame_size"]
                frame_indices = sorted(set([0, info["frames"] // 2, info["frames"] - 1])) if info["frames"] > 1 else [0]
                x, y = (cell % 6) * 200, 40 + (cell // 6) * 265
                for frame_no, frame_index in enumerate(frame_indices):
                    columns = texture.width // fw
                    frame = texture.crop(((frame_index % columns) * fw, (frame_index // columns) * fh,
                        (frame_index % columns + 1) * fw, (frame_index // columns + 1) * fh)).convert("RGBA")
                    limit = 160 if len(frame_indices) == 1 else 60
                    scale = min(limit / fw, 160 / fh)
                    rendered = frame.resize((max(1, round(fw * scale)), max(1, round(fh * scale))), Image.Resampling.NEAREST)
                    sheet.paste(rendered, (x + 10 + frame_no * 62, y + 10), rendered)
                    if len(frame_indices) > 1:
                        draw.text((x + 10 + frame_no * 62, y + 80), f"f{frame_index}", font=font, fill="#c5cbd5")
                short = Path(name).stem
                label = re.sub(r"(.{1,26})(?:_|$)", r"\1\n", short).strip().splitlines()
                for row, line in enumerate(label[:3]):
                    draw.text((x + 8, y + 180 + row * 14), line, font=font, fill="white")
                draw.text((x + 8, y + 228), f"{info['size'][0]}×{info['size'][1]}  / {info['frames']} frame(s)", font=font, fill="#b3bdca")
        destination = args.contact_sheets / f"textures-{page:02d}.png"
        sheet.save(destination)
        print(destination)


if __name__ == "__main__":
    main()
