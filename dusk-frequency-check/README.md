# Hosted Dusk frequency and settings fixture

This fixture runs only on free public GitHub-hosted runners. It is a separate test mod and is never included in the UwU Dimension JAR. It uses the same frozen candidate JAR for dedicated native generation and the real configuration client.

## Runner contract

The workflow builds one frozen UwU candidate, one dedicated fixture JAR and one patched optional companion. It supplies `UWU_JAR`, `UWU_EXPECTED_JAR_SHA256`, `UWU_PROBE_JAR`, `UWU_EXPECTED_PROBE_SHA256`, the exact integrated Playtest 9 `UWU_BASELINE_JAR`, and `UWU_COMPANION_JAR`. The same workflow can run the three candidate seeds in parallel and collect their reports for native placement and UI verification:

```bash
bash dusk-frequency-check/run-server.sh survey "$SEED"
# After all three candidate reports and the exact baseline reports are staged:
bash dusk-frequency-check/run-server.sh placement
bash dusk-frequency-check/run-client.sh
```

`survey` accepts only the three predetermined seeds. `placement` reads all six JSON reports already staged under `build/dusk-frequency-evidence/`, publishes their counters, and enforces the comparison gates before installing its server. A supplied fixture JAR skips Gradle compilation and must match `UWU_EXPECTED_PROBE_SHA256`. Both the candidate and actually loaded dedicated fixture JAR paths and hashes are checked on every process. The original all-in-one invocation with candidate and baseline JAR arguments remains available for reproduction; it reruns all baseline and candidate surveys. All modes boot pinned NeoForge 21.1.249 in explicitly named disposable worlds.

Only the three unchanged baseline survey reports and their individual logs may be reused from completed run `44961077711`, whose fixture source was `cc01a6c942353cbc570b786d9718a58c1438612a`. The CI recovery script checks the original artifact and each recovered file. These reports retain their original source identity and schema without normalization or relabeling. The new comparison includes their original source SHA, baseline JAR SHA and file hashes. Every candidate survey is rerun from the new source with the same frozen candidate and fixture JARs. The client inherits the existing pinned build runtime, loads the candidate and optional companion from their packaged JARs, and registers only the fixture as a local source mod.

## Predetermined frequency sample

The sample is seeds **152045, 20261121 and 8675309**, with the same **21 × 21 lattice-cell window** centered on each seed's original first-grove cell. This is 441 cells per seed, 1,323 cells total. The window has no biome filter. The existing 384-block minimum spacing and 1,536-block lattice remain fixed.

The baseline and candidate surveys independently boot fresh worlds and call their actual native terrain survey methods, with an adaptive wall-clock cooldown after each cell: the greater of 250 milliseconds or half that cell's measured call duration. This allows ordinary server catch-up ticks between the unusually dense synthetic samples; the normal watchdog and stage timeouts remain enabled. Reports record the total and maximum cell-call cost, the intervening ordinary tick count, and actual wall time between calls. These timings describe the hosted survey, not gameplay FPS. Reports separate lattice cells, usable groves, canonically assigned Dusk sites, viable original 32-attempt plans, viable expanded 1,024-attempt plans, and accepted 75-percent plans. The actual budget is also recorded as `surveyAttemptBudget`; expanded fields use generic names. Reports record the Velvet Highlands subset. The biome preview uses Minecraft's fuzzy biome selection against the pure noise source, so it does not load chunks.

Every candidate cell checks the actual 0/75/100 plan results against its deterministic roll, with no preview freezing. All Petal signatures and every successful original 32-attempt signature must match the baseline exactly. Expanded 75-percent acceptance must strictly exceed baseline 100-percent acceptance both over the complete fixed window and within its Velvet Highlands subset. All measured counters are written before those gates are applied, so a rejected candidate remains inspectable without receiving a PASS marker. Survey counts are terrain plans, **not counts of generated settlements**.

## Separate actual generation and persistence checks

Actual native village placement runs on each of the same three seeds. It checks full village footprints, the native plaza, Dusk house blocks, generated Dusk residents and Cupid-shrine exclusion. The detailed lifecycle seed is chosen deterministically from the fixed sample using explicit prerequisites. It must have a village that failed the original 32-attempt survey, succeeds at the expanded budget, passes the 75-percent roll, and actually generates in Velvet Highlands.

On that seed, the fixture additionally checks an accepted 75-percent site, an accepted 100-percent site, a generation-touched 0-percent rejected footprint, and a Petal control. Live edits must leave accepted and rejected touched decisions unchanged, while a previously untouched high-roll site responds to 100. A separate Minecraft process then reloads the same saved world and verifies persisted decisions, selected structural block states, and resident UUID/type/name/trades/inventory/custom identity. The fixture does not claim whole-chunk byte identity.

The original integrated baseline seeds another disposable world with partial native Dusk and Petal settlements, real residents enriched with distinctive test trades and inventory, and a generated empty footprint that would otherwise become viable only with the expanded survey. Candidate upgrade and restart must preserve those partial plans, retain the original 32-attempt/100-percent historical policy, keep the old blank footprint empty, complete new chunks with the same faction and geometry, and leave the sampled existing structural blocks and residents unchanged.

The rejected-0 check exercises real generation and first-touch persistence. It does not isolate the fallback `ChunkEvent.Load` branch from the landscape feature: all eleven default Sakura Reach biomes run the landscape feature, including those without the surface settlement feature.

## Actual graphical settings path

The client opens the real optional Unyielding `/config` screen, selects **Config values**, types the exact Dusk setting key into the existing search field, opens its value row, types a new value through normal keyboard events, and clicks the native **Save** button. It performs **75 → 0 → 75 → 100**. No positive edit invokes the handler directly or changes the UwU config field from the fixture.

Each successful save must return the real server acknowledgement and matching displayed value. The fixture captures the actual edited and saved screens, then checks the UwU getter, persisted server TOML, and subsequent native world generation. A first touch at 0 remains rejected through later GUI edits; 75 generates the recovered Velvet village; 100 generates an untouched high-roll village. Petal planning and outside-dimension exclusion remain separate assertions.

Ten negative checks use the actual companion packet codec and registered server handler separately from the graphical positive path. They cover non-operator, stale expected value, unknown key, range and type failures while asserting unchanged memory and byte-identical TOML. The optional companion is a separately identified hosted test build from pinned public source plus the reviewable coordination patch, not a dependency bundled into UwU.

## Evidence

Logs are under `build/dusk-frequency-check/` and `build/dusk-frequency-client.log`. JSON reports and six native menu PNGs are under `build/dusk-frequency-evidence/`. Required top-level completion markers are `DUSK FREQUENCY SERVER PASS`, `DUSK FREQUENCY COMPARISON PASS` and `DUSK FREQUENCY UI PASS`. The workflow's independent provenance gate checks source and JAR identities before making the test artifact available.

No local compilation, Minecraft execution, profile installation, existing-world access, server access or release is part of this fixture's preparation.
