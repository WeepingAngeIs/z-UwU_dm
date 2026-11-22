# Guardian model review

The Petal and Dusk males share the flower Guardian's six original bone pivots. The jaw and second chin form one continuous ring mesh. A recessed fold separates the two lobes. The chest, sleeves, flowers and crown have their own curved surfaces. Each of the ten tapered lily pads occupies a separate angular sector on the head. Both female variants use the shipped female model, jaw, walk and chase clips. Her Dusk skin follows the same five expression frames.

For a new or revised Guardian mesh, check for overlapping coplanar faces and visible limb, face or crown clipping in idle, walk, warning, grip and held flip. Include the outline from the front and side. Check the actual animation again in the packaged client from both the player and an observer.

## Editable art

- `male-mesh.cjs` defines the male sculpture in the model's original 16-pixels-per-block coordinates. Each profile has a named material and a rig bone.
- `paint.cjs` bakes the native mesh and atlases. The female eyelids and Dusk egg silhouettes derive from the shipped UVs. Node.js and `pngjs` are authoring tools. The mod reads the baked resources directly.
- `review.py` renders design views from the same game mesh, crown UVs and atlases. `check_surfaces.py` checks shading normals, distinct-part coplanarity, intersections between unrelated moving parts, female expression UV coverage, one-eye winks and spawn-egg transparency. Both review tools use Python, Pillow and NumPy. Node.js supplies the shape-to-bone map for the surface check.

Run from the repo root. Choose an output directory outside the checkout.

```sh
node guardian-art/paint.cjs
python guardian-art/check_surfaces.py /path/to/review
python guardian-art/review.py /path/to/review
```

## First prototype

At source baseline `072811cb288996978e7f664a3bda47a570fea079`, the old rigid male torso and lower cuff share a 14-by-1-pixel strip on the front and another on the back. The old flower petals also overlap across their flat front and back faces. The check reconstructs the old rig as a positive control and detects both conditions. Some old coplanar pairs are inside the body.

The first new mesh has SHA-256 `99452e78af5663233c835bf066b4383f98c76211d2e6cffebf189959d3122cef`. Its 22 sampled poses cover idle, a walk cycle, both extremes of the head shake, grip and a complete held backflip. No two distinct authored parts had overlapping coplanar area above 0.0002 square model pixels. The hands and distal forearms cleared the torso, the full arms cleared the head and crown, and the feet stayed clear of each other, the hands and the head. The shoulder joints intentionally seat inside the torso. A backflip applies a common rigid rotation to every held-pose bone and the crown.

The native female Petal wink changes 103 painted eyelid pixels on one side of her original face. The male wink changes 80 atlas pixels. All five Dusk female expression atlases and both Dusk eggs preserve their respective baseline alpha masks.
