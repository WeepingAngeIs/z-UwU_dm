# Dusk settlement frequency workstream

This branch starts at integrated Playtest 9
`1f489c5308b686a30672ec19b671a64534eb28a4` in the public
`WeepingAngeIs/z-UwU_dm` repository. It is independent of the village-dialogue and
active weapon branches. No release, installation or user-world migration is performed
by this workflow.

## What the percentage means

`uwudimension-server.toml` gains the integer `duskVillageChancePercent`, default
**75**, allowed **0–100**. Its denominator is a newly considered, habitable Dusk
settlement site in Sakura Reach that passes the existing lattice, faction, spacing,
legacy-neighbor and terrain rules. It is not a percentage of chunks, biomes, players
or already generated villages. A separately salted seed/grid hash produces one
stable roll from 0 through 99; a site is admitted when its roll is below the setting.

The baseline already admitted 100% of modern sites that passed every gate. Its
historical `3/8` Dusk expression is part of old-world reconstruction. Changing that
expression or adding a 75% roll alone would not solve the current scarcity.

This candidate searches up to 1,024 Dusk layouts within the original footprint,
instead of stopping after 32. The first 32 attempts have the identical random
sequence, and all terrain, road slope, house-lot and biome requirements remain.
Petal, arrival selection and historical reconstruction retain their original
32-attempt survey. An extended Dusk survey temporarily memoizes the existing pure
height queries and exact three-dimensional world-quart biome queries, with a
32,768-entry cap per map and uncached fallback. The cache lives only for that call;
it does not alter random calls, terrain predicates or returned plans.

The existing saved spacing is unchanged (default 384 minimum
footprint separation; the corresponding default lattice stride is 1,536 blocks).

The hosted measurement compares baseline 32-attempt/100% results and candidate
1,024-attempt/75% results over the same fixed fresh seeds and lattice cells. A survey
count alone is not a generated-village count: the evidence records the bounded
native placement sample separately, including Velvet Highlands and expanded-only
Dusk sites. Consult the exact passing run's results before claiming a measured gain.

## Existing and partially generated worlds

The first load records an immutable index of existing region-file chunk headers in
`uwudimension_before_dusk_frequency_v1.dat`. It does not load or rewrite those
chunks or their residents. If any saved chunk intersects a conservative site
footprint, that site keeps the old 32-attempt/100% policy.

For new sites, actual generation freezes the site's percentage when it first
touches the footprint. Both accepted and rejected decisions are persisted as
`DuskSiteChancesV1`; later edits cannot remove half a village or enable the other
half of a rejected site. The native feature hook runs before village placement,
and the existing new-full-chunk hook covers chunks whose biome did not invoke the
native surface feature. A pure preview or locator query never freezes a site.

Authored structure and giant-tree reservations use the possible village envelope
before the chance roll. This keeps their caches valid after live edits to future
site settings. Village architecture, residents, Cupid exclusion, waterfalls and
other biome assets remain unchanged.

## Unyielding coordination is required

The existing Unyielding editor has an explicit catalog; it does not discover new
UwU options automatically. The companion patch is
[`companion/Server-Jars-config.patch`](companion/Server-Jars-config.patch), against
public `WeepingAngeIs/Server-Jars` commit
`0b01111213c6d6aaf63a955a5c010bc1a63ee04c`, the settings source pinned by
`WeepingAngeIs/unyielding-mod` at
`d561cd5423815cf88da5c0ad46914b2adfc73ab4`.

With that companion applied, operators use **`/config` → Config values → search
`dusk` → Dusk village generation chance (%)**. The row uses the actual public
`AcquisitionPolicy.DUSK_SITE_CHANCE` value and the existing editor's permission,
optimistic-concurrency, validation, persistence and rollback path. The optional
adapter safely omits the row when UwU is absent or predates the capability.

Neither external repository is changed or published by this work. The workflow
applies the patch to a disposable checkout of the pinned public source and builds
only the quests module for the integration fixture. That separately labeled
companion test JAR is not a combined Unyielding release and must not be installed
alongside another JAR supplying the same `unyieldingquests` mod ID. Delivery to the
existing combined Unyielding distribution requires coordination by its maintainer.

## Hosted checks and artifacts

`.github/workflows/dusk-frequency.yml` uses only the public repository's ordinary
GitHub-hosted Ubuntu runner, pinned Java 21, and NeoForge 21.1.249. It builds one
UwU candidate, freezes its SHA-256, and uses that exact JAR throughout the checks.

The build job compiles one candidate, one dedicated fixture and the explicit
companion. A hashed input manifest carries those exact files to three parallel
GitHub-hosted seed-survey jobs, then to the native-placement and menu job.
Intermediate artifacts named `uwu-dusk-ci-inputs-*` contain inputs awaiting checks;
the final test JAR is uploaded only after all required checks pass.

The unchanged baseline controls are recovered from the completed baseline phases
of [run 44961077711](https://github.com/WeepingAngeIs/z-UwU_dm/actions/runs/44961077711),
fixture source `cc01a6c942353cbc570b786d9718a58c1438612a`. The original source/JAR
identities and report bytes are retained. That run's first candidate failed the
aggregate gain requirement (150 accepted sites versus 156 baseline sites); its
candidate reports are not reused as current evidence. The recovery helper verifies
the complete archive, exact baseline files and successful phase markers and records
the earlier run explicitly. Every revised candidate, placement and UI check runs
again on the new source.

The check entry points are for the hosted workflow only:

```bash
bash integration-check/bootstrap.sh
bash dusk-frequency-ci/recover-baseline.sh
bash dusk-frequency-ci/recover-baseline-surveys.sh
bash ./gradlew --no-daemon --no-build-cache --max-workers=2 jar
bash ./gradlew --no-daemon --no-build-cache --max-workers=2 -I dusk-frequency-check/probe.gradle duskFrequencyProbeJar
python3 dusk-frequency-ci/verify.py "$UWU_JAR" "$UWU_BASELINE_JAR"
bash dusk-frequency-ci/build-companion.sh
python3 dusk-frequency-ci/inputs.py seal
# Each of the three survey jobs restores and verifies the frozen inputs first.
python3 dusk-frequency-ci/inputs.py verify
bash dusk-frequency-check/run-server.sh survey "$UWU_SURVEY_SEED"
# The final job collects all three new candidate reports before these checks.
bash dusk-frequency-check/run-server.sh placement
bash dusk-frequency-check/run-client.sh
python3 dusk-frequency-ci/provenance.py
```

Required environment includes `UWU_JAR`, `UWU_BASELINE_JAR`,
`UWU_EXPECTED_JAR_SHA256`, `UWU_PROBE_JAR`, `UWU_EXPECTED_PROBE_SHA256`, and,
for the menu fixture, `UWU_COMPANION_JAR`. The input verifier exports the frozen
identities for every subsequent job.
The companion build records its upstream source, patch, public dependencies,
Java/Gradle/NeoForge versions and output hashes. Its upstream NeoForge default is
21.1.255; the disposable fixture build explicitly uses 21.1.249 to match UwU.

Only an all-pass run uploads the UwU test JAR and the separate companion test
artifact. Logs, JSON measurements and native menu captures are retained as
evidence even when a check fails. The final scope gate compares every JAR entry
against the passing integrated baseline, preserving all unrelated classes and
resources and rejecting bundled fixture or Unyielding code.
