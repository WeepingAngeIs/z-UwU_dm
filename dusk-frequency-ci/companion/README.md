# Unyielding settings adapter for the UwU Dusk site chance

This directory contains a coordination patch for Unyielding's existing configuration editor. The patch belongs to the public `WeepingAngeIs/Server-Jars` repository and is applied only to a disposable checkout by the hosted verification job. It is not part of the UwU Dimension JAR. No external repository is changed by keeping this patch here.

## Verified source targets

| Purpose | Repository and immutable revision |
| --- | --- |
| UwU production baseline | `WeepingAngeIs/z-UwU_dm` at `1f489c5308b686a30672ec19b671a64534eb28a4` |
| Public Unyielding distribution repository inspected | `WeepingAngeIs/unyielding-mod` at `d561cd5423815cf88da5c0ad46914b2adfc73ab4` |
| Actual settings module source to which this patch applies | `WeepingAngeIs/Server-Jars` at `0b01111213c6d6aaf63a955a5c010bc1a63ee04c` |

The Unyielding distribution's [settings workflow](https://github.com/WeepingAngeIs/unyielding-mod/blob/d561cd5423815cf88da5c0ad46914b2adfc73ab4/.github/workflows/settings-release.yml) checks out that exact Server-Jars revision. The [quests module](https://github.com/WeepingAngeIs/Server-Jars/tree/0b01111213c6d6aaf63a955a5c010bc1a63ee04c/unyielding-quests) owns the `/config` and `/quests settings` interface.

## Public connection contract

| Contract | Exact value |
| --- | --- |
| UwU mod ID | `uwudimension` |
| Configuration owner | `dev.uwu.dimension.AcquisitionPolicy` |
| Public field | `DUSK_SITE_CHANCE`, a `ModConfigSpec.IntValue` |
| UwU configuration path | `duskVillageChancePercent` |
| UwU configuration type and filename | `SERVER`, `uwudimension-server.toml` |
| Default and bounds | Integer `75`, inclusive range `0..100` |
| Generator getter | `AcquisitionPolicy.duskVillageChancePercent()` |
| Unyielding editor row key | `uwudimension.duskVillageChancePercent` |
| Row title | `Dusk village generation chance (%)` |
| User navigation | `/config` or `/quests settings` → **Config values** → search `dusk` |

The setting controls the deterministic roll for eligible new Dusk settlement sites. It is not a per-chunk or per-biome probability. Zero rejects new, unfrozen sites; 100 admits every eligible new, unfrozen site; 75 admits rolls below 75. Terrain suitability, faction assignment, minimum spacing and historical protection remain separate gates. The UwU field's own comment is displayed as help, so the production source remains the authority for the precise eligibility and persistence contract.

The generator reads the current value for unfrozen candidates. Once actual generation first touches a site's village footprint, UwU preserves that site's decision across edits and restarts. The setting does not require a restart for future, unfrozen sites. Unyielding needs no cache callback: admission and persisted generation decisions belong to UwU, and the terrain geometry cache does not depend on the percentage.

## Why the adapter is required

Unyielding's `ConfigEditor.values()` creates a private, immutable catalog containing its own config classes plus explicit optional adapters in `BalanceSettings`. It does not discover arbitrary NeoForge server configurations. Adding a UwU TOML field alone therefore does not make that field editable through the current Unyielding menu.

`Server-Jars-config.patch` changes only two existing files:

- `unyielding-quests/src/main/java/dev/unyielding/quests/settings/BalanceSettings.java` optionally reads UwU's public `DUSK_SITE_CHANCE` field and adds that exact config value to the existing catalog. If UwU is absent, or an older UwU does not expose the field, no row is added. An incompatible optional capability logs a warning and does not prevent the rest of the catalog from loading.
- `unyielding-quests/src/main/java/dev/unyielding/quests/settings/ConfigEditor.java` supplies the readable row title. The existing generic integer editor renders it; no new screen or packet is introduced.

There is no compile dependency on UwU, no additional runtime dependency, no replacement config file, and no reflective write to an immutable catalog. Installing a future Unyielding distribution containing this adapter is a separate coordinator action. This task does not install or publish that distribution.

## Existing packet and validation path

The actual client path is `ConfigValuesScreen.ValueScreen.save()` → `Net.sendToServer(new ConfigEditor.Action(key, expected, value))` → `ConfigEditor.register(...)` → `ConfigEditor.handle(...)`.

| Packet | Exact contract |
| --- | --- |
| Serverbound edit | `unyieldingquests:config_edit` |
| Fields | `key` (UTF-8, 200 chars), `expected` (JSON, 30,000 chars), `value` (JSON, 30,000 chars) |
| Clientbound result and rows | `unyieldingquests:config_values` with NBT `data` and Boolean `open` |
| Open request | `ConfigEditor.Action("__open", "", "")` |
| Edit example | `ConfigEditor.Action("uwudimension.duskVillageChancePercent", "75", "100")` |

The production handler requires operator permission level 2, rejects unknown keys, compares the expected JSON against the current raw value, parses an exact integer, and runs the UwU `ConfigValue` specification's range validator. It then calls `set()` and `save()`, restores the previous value if saving fails, and returns the current row snapshot. The adapter retains those checks unchanged. A direct test invocation of `ConfigEditor.handle` exercises the actual registered server handler, but does not by itself prove a graphical client opened the screen; report those forms of evidence separately.

## Hosted build instructions

Run these commands only on a GitHub-hosted runner with Java 21. They prepare and build the optional quests module for isolated integration verification. They do not build or publish a combined Unyielding release.

The repository helper is the recommended entry point:

```bash
bash dusk-frequency-ci/build-companion.sh
```

It requires the public UwU GitHub event and a GitHub-hosted runner. Its default output is `build/dusk-frequency-ci/companion`; an optional first argument overrides that directory. It writes `UnyieldingQuests-1.6.9.jar`, `companion-provenance.json`, `SHA256SUMS` and `companion-build.log`, then exports the absolute `UWU_COMPANION_JAR` through `GITHUB_ENV` for subsequent fixture steps. The helper checks out only the pinned source, verifies the official Gradle 8.14.3 distribution and wrapper hashes, and records input and artifact provenance. It reuses the repository's official Maven Central fallback convention. No build result is implied by the presence of this script.

The equivalent source checkout and module commands are shown below for the coordinator; the helper also performs the transport and provenance checks:

```bash
set -euo pipefail
UWU_COMPANION_SHA=0b01111213c6d6aaf63a955a5c010bc1a63ee04c
UWU_COMPANION_SOURCE="$RUNNER_TEMP/uwu-unyielding-source"
UWU_COMPANION_PATCH="$GITHUB_WORKSPACE/dusk-frequency-ci/companion/Server-Jars-config.patch"
git init "$UWU_COMPANION_SOURCE"
git -C "$UWU_COMPANION_SOURCE" remote add origin https://github.com/WeepingAngeIs/Server-Jars.git
git -C "$UWU_COMPANION_SOURCE" fetch --depth=1 origin "$UWU_COMPANION_SHA"
git -C "$UWU_COMPANION_SOURCE" checkout --detach FETCH_HEAD
test "$(git -C "$UWU_COMPANION_SOURCE" rev-parse HEAD)" = "$UWU_COMPANION_SHA"
git -C "$UWU_COMPANION_SOURCE" apply --check "$UWU_COMPANION_PATCH"
git -C "$UWU_COMPANION_SOURCE" apply "$UWU_COMPANION_PATCH"
cd "$UWU_COMPANION_SOURCE"
python .github/scripts/fetch-build-dependencies.py
bash unyielding-quests/gradlew -p unyielding-quests --no-daemon --build-cache -Pneo_version=21.1.249 jar
sha256sum unyielding-quests/build/libs/UnyieldingQuests-1.6.9.jar
```

The source's wrapper pins Gradle `8.14.3`, its build uses moddev `2.0.148`, and its Java toolchain is 21. The upstream quests properties default to NeoForge `21.1.255`. The explicit `-Pneo_version=21.1.249` aligns this disposable integration build with the UwU baseline's pinned NeoForge runtime; it does not modify the upstream properties file. The resulting test module is `unyielding-quests/build/libs/UnyieldingQuests-1.6.9.jar`. Do not load it alongside a combined Unyielding JAR containing the same `unyieldingquests` mod ID.

The pinned source's [dependency fetcher](https://github.com/WeepingAngeIs/Server-Jars/blob/0b01111213c6d6aaf63a955a5c010bc1a63ee04c/.github/scripts/fetch-build-dependencies.py) downloads and verifies these public, compile-only inputs:

| Dependency | Immutable source | Published checksum |
| --- | --- | --- |
| Cobblemon NeoForge `1.8.4+1.21.1` | [Modrinth version `xsVHrAsY`](https://cdn.modrinth.com/data/MdwFAVRL/versions/xsVHrAsY/Cobblemon-neoforge-1.8.4%2B1.21.1.jar) | SHA-512 `060579fb8ac67ddd131c9cc9076ef512cda9e66e7a584c7c82326be9ef0cbb69ce6c99ff772aa78f990dc1b771bac02f3bbba68a9c7ff68c557009c64f733d04` |
| EMI `1.1.24+1.21.1+neoforge` | [Modrinth version `5sIPA1To`](https://cdn.modrinth.com/data/fRiHVvU7/versions/5sIPA1To/emi-1.1.24%2B1.21.1%2Bneoforge.jar) | SHA-512 `f4c45ed686fe617311d6debadcb6ace5963e66f8cfe0cfed398231f081f87407070efc7a73c8b695d158f1d87a97180737b9107a46771e77df2bc9a7dc715ebd` |
| Kotlin stdlib `2.4.0` | [Maven Central](https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.4.0/kotlin-stdlib-2.4.0.jar) | SHA-1 `4e9f4b531dce5c7dee58ed2de9a8b9a763c8dc6c` |

The quests module's metadata requires Minecraft and NeoForge; Cobblemon is optional. Whether this isolated module boots and edits the new row with the chosen runtime is a hosted integration check, not a result established by source inspection.

## Focused hosted verification requirements

Use the packaged UwU candidate with the patched quests module on a disposable server. The public upstream [SettingsSmoke.java](https://github.com/WeepingAngeIs/Server-Jars/blob/0b01111213c6d6aaf63a955a5c010bc1a63ee04c/settings-update/tests/SettingsSmoke.java) already demonstrates its fake-player outbound-packet recorder and the real `ConfigEditor.handle` invocation pattern.

- Open the actual editor handler as an operator and verify the new row key, title, integer value and help. Round-trip `ConfigEditor.Action.CODEC` before invoking the production handler to cover wire representation as well as application behavior.
- Apply 0, 75 and 100 through that handler. Check `AcquisitionPolicy.duskVillageChancePercent()`, the persisted UwU server TOML and actual new-site generator decisions. The fixture must distinguish previewed candidates from generation-frozen sites.
- Reject non-operator, stale, unknown-key, out-of-range and non-integer edits without changing the value or the config file. Include `-1`, `101`, `74.5`, `true`, `null`, `[]` and `"75"`.
- Demonstrate that previously accepted and previously rejected generation-touched sites retain their decisions after edits and a restart, while untouched eligible sites respond to the new percentage. Check Petal and historical settlement invariants separately.
- Record the exact UwU source SHA, pinned external source SHA, coordination patch hash, packaged JAR hashes, handler results and generation counts in the hosted evidence artifact. Report any graphical menu evidence separately from a server packet-handler test.

No compilation or Minecraft execution was performed locally to prepare this coordination patch. Hosted results must be attached by the integrating workflow before describing the adapter as verified.
