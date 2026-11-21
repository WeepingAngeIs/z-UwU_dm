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

## Actual hosted baseline review and decisions

Use the native `ItemRenderer` with each actual item stack at face, 60°, 90° and
120° model-space yaw, plus real in-world third-person side captures. Compare both
hands and the first-person held view for any changed family. Native GUI rendering
must remain a separate check, particularly for Cupid's separate GUI transform.

The source inventory was completed before any production asset edit. The later
review used [hosted run 44956844489](https://github.com/WeepingAngeIs/z-UwU_dm/actions/runs/44956844489),
artifact `14728036526`: all 427 native model variants and 169 actual held side
captures completed. That run subsequently failed when the second fixture client's
username exceeded Minecraft's limit; it is completed visual baseline evidence,
not a passing multiplayer test. `baseline-review.json` maps every inspected row and
held capture to its original filename, SHA-256, source family and decision.

The completed two-client baseline is
[run 44957916244](https://github.com/WeepingAngeIs/z-UwU_dm/actions/runs/44957916244),
artifact `14728756840`, fixture commit `def4e4fb9169b7771c4044c949608b16f9a4d188`.
Production remained the exact integrated Playtest 9 JAR; that run's final integration
job was deliberately skipped. `motion-baseline.json` records the downloaded artifact
digest, actual frame references, TCP identities and both clients' observations.
Twinblade/katana profiles selected hand-slash clips while the existing rapier throws
already rendered outbound and returning motion remotely. All sixteen physical throw
trials returned one stack and shared the same projectile identity across clients.
This is existing baseline evidence, not validation of the candidate below.

The audit inspected the 102 sprite blade weapons, Returnbloom Dirk, and all 212
related elemental, credit and Glitch rows. An independent review covered the other
66 held captures and 46 non-blade Glitch rows. At exact model-space 90 degrees, the
sprite blades collapsed to narrow, mostly uniform strips. Their face and oblique
views retained the intended art. Real held side views also showed that Vesper and
Magiscythe can present their narrow edge to the camera. Returnbloom's curved face
looked good while its blade edge was particularly thin. Dawnpetal and the shields
already retained deliberate depth, and the two Cupid styles remained distinct.

The resulting authored scope is 174 JSON models covering all 102 sprite blade
roots and their elemental/credit states, one client-only opt-in geometry loader,
and Returnbloom's existing blade OBJ. Non-blades, tools, shields, Dawnpetal, both
Cupid styles, all PNGs and all animation metadata remain unchanged. Root transforms
and override predicates are preserved. Native GUI rendering returns the original
model, so sprite hotbar and inventory art is unchanged.

The relief adds metal shoulders and a stepped ridge over reviewed blade pixels.
Flowers, ribbons, guards, grips, dark contours and transparent gaps retain the
native model. Every added face lies in front of or behind the original central
extrusion, avoiding duplicate coplanar sidewalls. Original native render passes are
retained; raised glint and flower-credit faces use their original texture and light
data. Returnbloom's blade depth is scaled threefold around its existing midplane
(about 0.346 to 1.037 model pixels); its explicit normals use the inverse transpose,
while XY coordinates, UVs, faces, topology and material remain unchanged.

Some fifth-row baseline gallery metrics include overlapping HUD pixels and must
not be used for quantitative comparisons. Final native gallery capture hides the
HUD. The authored candidate still requires the final hosted native proof, visual
review and all requested integration checks before it can be delivered as verified.

### Geometry invariants

The implementation uses the existing NeoForge `elements` loader and the shipped
texture coordinates, combined with the unchanged native item-layer render passes.
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

Returnbloom was reviewed separately because its curved OBJ blade was only about
0.346 model pixels deep before its held Z scale. Its topology and UVs remain intact
in the authored depth refinement. Dawnpetal's existing model is about 2.03 model
pixels deep and remains unchanged after actual side-view review.

Implementation references: NeoForge's version-specific documentation for
[built-in model loaders](https://docs.neoforged.net/docs/1.21.1/resources/client/models/modelloaders/)
and [per-face light data](https://docs.neoforged.net/docs/1.21.1/resources/client/models/).

## Reproducing the static inventory

`catalog.json` is the frozen source inventory from the integrated baseline. Run
`python weapon-audit/catalog.py` only against those baseline assets to reproduce
that snapshot. It does not build the mod, launch Java or render models. Optional
`--contact-sheets /absolute/scratch/path` creates labelled copies of the shipped
PNG pixels for human inspection. Compilation and all in-game verification belong
to the hosted Actions workflow only.

`python weapon-audit/generate_relief.py` authors the reviewed geometry from the
baseline git objects and unchanged source PNGs. It writes compact JSON elements,
the Returnbloom depth/normal change and `relief-manifest.json`, whose
`production_paths` lists exactly 176 visual production files.

`python3 weapon-audit/verify_geometry.py` is the hosted-only source gate. It uses
only Python's standard library and writes
`build/weapon-release/geometry-source-proof.json`. The separate native
`BladeModelProbe` checks render-pass identity, emission data, native transforms,
the central extrusion boundary, and same-frame GUI pixel identity after Minecraft
bakes the packaged models. Source authoring or a successful static proof is not an
in-game rendering pass.
