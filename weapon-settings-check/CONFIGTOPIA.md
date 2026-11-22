# Live UwU weapon settings

A server operator (level 2) or the dedicated console can read a setting by omitting its value. Tab completes weapons and keys. The same registered NeoForge SERVER values are available through Configtopia’s optional UwU Dimension entry. Standalone commands work with UwU Dimension alone.

```mcfunction
/uwu cupid charmseconds 90
/uwu weapon returnbloom_dirk turnBackCooldownSeconds 2.5
/uwu weapon returnbloom_dirk baseHitDamage 18
/uwu weapon returnbloom_dirk throwDamage 7
/uwu weapon blossom_revival rechargeSeconds 30
```

Use the loaded UwU SERVER spec (`AcquisitionPolicy.SPEC`, registered under `uwudimension`). Its file is `config/uwudimension-server.toml` unless the world already supplies `<world>/serverconfig/uwudimension-server.toml`. Resolve `ModConfig.getFullPath()` for that spec to find the file actually in use. Write through the registered ConfigValue and save it on the server thread. Include the weapon section in the menu label so two settings called “Throw Damage” are distinguishable.

## Cupid Bow

The root integer `cupidBowCharmSeconds` is also exposed as `AcquisitionPolicy.CUPID_BOW_CHARM_SECONDS`. Default 40, inclusive range 1–107374182 whole seconds. Both art variants use the same item. The value applies when a new eligible enemy or PvP opponent is struck, including by a previously fired arrow. Existing charms retain their expiry. Owned pets keep their existing 40-second wish marker. A successful charm still deals zero arrow damage. Players can read the current duration in `/sakura cupid help`.

## Weapon keys

Every row below is under `weapons.<weapon id>.<key>` in that same file. Command spelling is `/uwu weapon <weapon id> <key> <value>`. All durations here use seconds. Decimal seconds round to the nearest game tick (0.05 s). Timers accept 0.05–3600 s, except the Cupid Bow’s optional shot cooldown, which also permits zero. Damage uses 1–256 health points before ordinary defenses and modifiers. The Dirk’s multiplier accepts 1–8. Existing cooldowns, revival charges and timed status effects keep the deadline set when they started. Damage is read at the hit. Held base damage refreshes during normal equipment ticking and uses the normal attribute and equipment synchronization.

| Weapon ID | Keys and shipped defaults |
| --- | --- |
| `returnbloom_dirk` | `baseHitDamage` 15, `throwDamage` 10, `throwCooldownSeconds` 5, `turnBackCooldownSeconds` 6, `backstabMultiplier` 2 for melee |
| `dawnpetal_axe` | `baseHitDamage` 22, `throwDamage` 10, `throwCooldownSeconds` 5, `spinDamage` 8, `spinCooldownSeconds` 3.5, `heartSpinDamage` 8, `heartSpinCooldownSeconds` 6 |
| `blossom_revival` | `rechargeSeconds` 60, shared by the two blossom weapons with the existing owner and item lockouts |
| `heart_launcher` | `castCooldownSeconds` 0.6, `abilityDamage` 8 for its blast |
| `petalfold_shield` | `blockCharmSeconds` 6 |
| `cupid_bow` | Base hit 14, full-draw fallback arrow 14, shot cooldown 0 |
| `ripple_bow` | Base hit 17, arrow 17, cast 1.2 s, `slowSeconds` 3.75, `glowSeconds` 7 |
| `ribbonhare_scepter` | Base hit 14, cast hit 14, cast 10 s, `hareLifetimeSeconds` 8 |
| `blossom_greatsword` (Blossom Great Axe) | Base hit 30, thrown hit 30, cast 2.2 s |
| `bellheart_axe` | Base hit 22, thrown hit 22, cast 5.25 s |
| `lockkeeper_axe` | Base hit 21, thrown hit 21, cast 4.25 s |
| `vesper_scythe` | Base hit 20, direct ability hit 20, `throwCooldownSeconds` 3, `waveCooldownSeconds` 0.7 |
| `axolotl_whip` | Base hit 16, lash 16, cast 8 s, `companionLifetimeSeconds` 30, `companionHitDamage` 4 |
| `frost_whip` | Base hit 17, lash 17, cast 3.5 s, `slowSeconds` 3 |
| `rift_whip` | Base hit 18, lash 18, cast 4 s, `markDurationSeconds` 8 |
| `rose_dagger` | Base hit 15, thrown hit 15, cast 1.25 s |
| `satin_whip` | Base hit 15, lash 15, cast 2 s |
| `lantern_spear` | Base hit 19, thrown hit 19, cast 2.25 s |
| `tidelace_trident` | Base hit 23, thrown hit 23, cast 3.75 s |
| `blue_hour_wand` | Base hit 16, ray 16, cast 3.25 s, `slowSeconds` 4, `glowSeconds` 7 |
| Each selectable `counter_*` | `baseHitDamage` and `abilityDamage` both start at that exact entry’s `damage` in `assets/uwudimension/counter_roster.json`. `castCooldownSeconds` 3, plus `waveCooldownSeconds` 0.7 for scythe forms. FEINT and SHATTER use ability damage on the delayed echo. SHATTER keeps its separate two-point opener. |

For the fifteen Arsenal relics the standard keys are `baseHitDamage`, `abilityDamage` and `castCooldownSeconds`, with Vesper’s two action-specific timers as shown. Glitch follows the currently displayed supported form. Retired counter-bow IDs retain their original values. The Bell Hammer’s special action is its existing area-mining toggle. Attack speed, charge-up time, armor, enchantments, target restrictions, flight, visuals, and per-form ward rules retain their existing behavior.

## Existing server settings for optional discovery

| Registered file and section | Existing keys (default, inclusive bounds) |
| --- | --- |
| `uwudimension-server.toml` root, live | `duskVillageChancePercent` 75 (0–100), future untouched eligible Dusk sites. `bellHammerAreaMiningEnabled` true, governed by existing harvest/claim checks. |
| Same root, marked world restart | `nativeVillageSpacingBlocks` 384 (192–2048), chosen and saved per world. `heartLocketCraftingEnabled` true, `weaponAcquisitionEnabled` true, `useLootrForNewPersonalChests` false, all for their existing acquisition or new-assignment paths. |
| `cupidWishes` | `enabled` true, `playerWitnesses` true, `pairRadius` 8 (2–24 blocks), `permissionLevel` 0 (0–4). Player witness consent remains individual. |
| `guardianFamily` | `enabled` true, `cooldownTicks` 6000 (1–240000), `growthTicks` 24000 (1–240000), `readyTicks` 600 (20–24000), `pairRadius` 8 (2–16 blocks), `populationRadius` 32 (8–48 blocks), `populationCap` 6 (2–64), `permissionLevel` 0 (0–4). These existing keys retain tick units. |
| `uwudimension-launcher.toml` | `terrainExcavation` false, `blastRadius` 3 (1–4 blocks). The saved world excavation override takes precedence over the file default. The existing operator command reports its effective value. |

Guardian creeper targeting is separately governed by the `uwuGuardianCreeperAggro` game rule (default false). Personal witness and appearance preferences have their own scope.
