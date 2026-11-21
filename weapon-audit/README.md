# Weapon model and texture audit

Source baseline: `1f489c5308b686a30672ec19b671a64534eb28a4`.

`catalog.json` records the actual registry boundary, complete local model dependency
closure, model display transforms, override predicates, OBJ bounds, material files,
texture sizes and SHA-256 hashes. The inventory uses `AcquisitionPolicy.isWeapon`:
153 weapon items, with 12 native material tools and three shields listed separately
as unchanged visual controls.

## Coverage

| Family | Base items | Authoritative source |
| --- | ---: | --- |
| Native relics | 15 | `Arsenal.Relic` |
| Counter weapons, including saved-stack forms | 133 | `assets/uwudimension/counter_roster.json` |
| Glitch Bow, Heart Launcher, Bell Hammer, Returnbloom Dirk, Dawnpetal Axe | 5 | Respective item registries; `AcquisitionPolicy.isWeapon` |
| Native material tools (controls) | 12 | `SakuraTools` material × kind |
| Native shields (controls) | 3 | `HeartShields` |

The complete closure contains 268 JSON models, nine OBJ meshes and 431 texture
paths. Exact pixel duplicates reduce those textures to 323 distinct RGBA images.
The original PNG pixels were reviewed in labelled contact sheets, including the
first, middle and final frames of animated strips. These contact sheets are texture
inspection aids; they are not model renderings or evidence of in-game appearance.

Counter shape is read from the roster, not inferred from a filename. In particular,
the five material `counter_*_chakram` IDs are retired bow-shaped saved-stack forms.
The 20 actual chakrams are the material `*_twinblade`, `*_rapier`, `*_katana` and
`*_sai` entries. All 15 retired counter bows remain in the audit because existing
saved stacks can still render them.

## Active visual states

The exact markers, source methods, form indices and model predicates are in the
catalog. The hosted fixture should exercise them through real stack components
and actual held-use state.

| Item family | States |
| --- | --- |
| Cupid Bow | Original and `uwudimension:smooth_cupid` style; idle, nock, half, deep and full draw |
| Rippleglass Bow | Idle, nock, half, deep and full draw |
| Vesper Scythe | `uwu_element` 0–3, each with and without active `uwu.flower_credit_until` |
| Counter scythes | `uwu_element` 0–3 on all 16 actual scythe entries |
| Glitch Bow | All 147 legal saved form indices, appropriate scythe and Ripple states, and static overlay |
| Heart Launcher | Base model and three recoil models, selected by actual item cooldown |
| Returnbloom / Dawnpetal | Current root models; phase1–5 files are aliases and are not separate active visual models |

Invalid and Cupid Glitch form indices resolve to Rippleglass Bow. The random form
choice has a narrower active selection than saved-stack resolution; this does not
remove the saved forms from visual coverage.

## Source observations before hosted rendering

Most native and counter weapons use `neoforge:item_layers` over 32-pixel face art.
Their depth comes from generated sprite extrusion. Several held transforms reduce
that depth further (for example, the iron rapier/chakram has Z scales 0.57 in first
person and 0.65 in third person). Those numbers identify a review target, but do not
by themselves prove how a blade looks in a side camera.

The painted axe, dagger, spear, scythe and chakram faces already contain deliberate
light edges, darker interior metal, flowers, ribbons and material-specific colors.
Any refinement should preserve those pixels and the face silhouette. There are
102 blade-bearing sprite weapons to assess: seven native relics and 95 counter
weapons. Hammers, bows, wands and whips are still included in the full gallery;
their different silhouettes should not be forced into blade geometry.

Original Cupid, detailed Cupid, Returnbloom Dirk and Dawnpetal Axe already contain
explicit geometry. Their model-dependent hand poses and previous approved art
need preservation checks, not automatic replacement. Traversal blade landmarks
also depend on Vesper's existing XY silhouette and texture mapping.

## Hosted comparison and decision gate

Use the native `ItemRenderer` with each actual item stack at face, 60°, 90° and
120° model-space yaw, plus real in-world third-person side captures. Compare both
hands and the first-person held view for any changed family. Native GUI rendering
must remain a separate check, particularly for Cupid's separate GUI transform.

No production asset was changed for this initial inventory. Refinement decisions
are deferred until the public GitHub Actions baseline gallery has been inspected.
Only actual hosted Minecraft frames can establish the side-view problem and the
effect of a refinement.

### Proposed geometry invariants

If the side captures justify changing a sprite blade, the proposed implementation
uses the existing NeoForge `elements` loader and the shipped texture coordinates.
The geometry generator must preserve the following properties:

- Every existing PNG and animation metadata file remains byte-for-byte unchanged.
- The front and back XY silhouette stays equal to the original opaque pixels;
  additional depth is confined to an explicitly reviewed blade mask.
- Grip, ribbon, flower and charm depth stays unchanged outside that mask.
- Model display transforms, root override predicates and their order stay fixed.
- Animated glint and flower-credit layers retain their original textures, frame
  timing, full-bright face data and layer ordering. Raised blade faces require
  corresponding glint placement so the new geometry does not hide the effect.
- No registry, stack component, reach, damage, cooldown, flight or return logic is
  part of a geometry edit.
- Native face and GUI views are compared for identity; native edge and oblique
  views are compared for readable blade depth. Source bounds alone are not a pass.

Returnbloom is reviewed separately: its existing curved OBJ blade is only about
0.346 model pixels deep before its held Z scale, even though it is a true mesh.
If its real side capture needs a refinement, the existing mesh topology and UVs
should be retained, with any depth change isolated to the blade. Dawnpetal's
existing model is about 2.03 model pixels deep. Neither item is automatically
exempted merely because it already has explicit geometry.

Implementation references: NeoForge's version-specific documentation for
[built-in model loaders](https://docs.neoforged.net/docs/1.21.1/resources/client/models/modelloaders/)
and [per-face light data](https://docs.neoforged.net/docs/1.21.1/resources/client/models/).

## Reproducing the static inventory

`python weapon-audit/catalog.py` reads source assets and writes `catalog.json`.
It does not build the mod, launch Java or render models. Optional
`--contact-sheets /absolute/scratch/path` creates labelled copies of the shipped
PNG pixels for human inspection. Compilation and all in-game verification belong
to the hosted Actions workflow only.
