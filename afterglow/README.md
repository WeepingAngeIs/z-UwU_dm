# UwU Afterglow

This optional Iris shader pack is a separate ZIP for Minecraft 1.21.1. The test target is NeoForge 21.1.249, Iris 1.8.14-beta.1 and Sodium 0.8.13. The test workflow records which combinations actually pass.

The shader branch starts at `502d123b419fd5c05e77d202f8eb17d16a687794`. The native QA fixture uses the cumulative JAR from `c71c9afe351db06400a0f5def2c7386a7276b748`, with SHA-256 `2384b5e7edc6e2f992c851f0b4b5a82a5581e6dbe502c5d522d973302252c83f`.

The End has a persistent black hole due north. The approved black disc and local six-second spiral share a 24-second loop with the surrounding eight-second inward flow. The northern textures come from the approved player-view still and film. The extraction removes the foreground islands and uses cloud samples from that view for side, rear and polar coverage. Minecraft draws its own terrain.

In Sakura Reach the shader offers Low and Default presets, a 40–100% Liquid Brightness setting, rain halos around mapped lamps and a moon selector. Game sky is the initial moon setting. Petal Moon and Dusk Moon are manual previews of the approved artwork. The shader uses the native moon draw. The cumulative mod owns the Hollow Solar Eclipse phase, authoritative world light, spawn checks and any shader-off custom sky. This ZIP supplies no event schedule.

The approved northern still has SHA-256 `48d04f407faaf383f22506df00f950fbd1c419bf1f859d218476bf93d5bc06c4`. The approved 24-second motion master has SHA-256 `9906249db5309c7797cb27b1d16b43654db5b097efb19904587adff6d5df957f`. The archive contains six derived or approved textures. The largest is 1456 × 1456 pixels.

Run `python3 afterglow/pack.py build/afterglow/dist` from the repository root to reproduce the ZIP. The digest in `afterglow-check/expected.sha256` pins the bytes passed to Iris. The hosted test creates its own worlds. Its images are QA captures.
