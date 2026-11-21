# Native weapon views and two-client chakram fixture

This disposable fixture runs only on the public repository's GitHub-hosted Actions runner.
It requires `UWU_JAR` and `UWU_EXPECTED_JAR_SHA256`; both independently launched clients verify
the actual owning NeoForge mod file and digest. The main source output is compile-only for the fixture.

`UWU_WEAPON_BASELINE=true` records the exact integrated Playtest 9 behavior, including existing
hand-only or missing Better Combat profiles, without claiming those observations fix the report.
The final candidate mode requires native thrown-item motion and return for adapted melee clips.
Both modes exercise the unchanged physical right-click projectile and inventory return path.
Entries with no native Better Combat melee profile are explicit vanilla-swing controls, including
the remote client's native swing packet. The fixture does not invent a melee profile for those items;
their native physical right-click throw still has to travel out, return, and preserve the stack.

`bash weapon-check/run.sh` fetches hash-pinned public animation dependencies, prepares both native
launch scripts once, then launches a host JVM (`-Xmx2G`) and a separate observer JVM (`-Xmx1536M`).
The observer uses a distinct username and a real TCP connection to the host's disposable integrated
server on `127.0.0.1:25591`. No user profile, installed game, existing world, or external server is used.

The host captures native ItemRenderer pages at 0/60/90/120 degrees for all 153 registered weapons,
15 tool/shield controls, and the marked Cupid, scythe and legal Glitch forms. Each base stack and the
detailed Cupid also receives a native held third-person side capture. Inventory enumeration is based
on `AcquisitionPolicy.isWeapon` and exact tool controls rather than item filename guesses.

The motion matrix contains 64 trials: all 20 actual chakram entries in first and third person;
left-handed representatives of all four identifier families; and native physical throws in both views
and both physical hands. A fixture-only mixin observes Better Combat's native animation dispatch and
the final ItemRenderer matrix after PlayerAnimator and model transforms. It does not invoke animations
or synthesize attack packets. The owner uses normal `Minecraft.startAttack`/`startUseItem` input paths;
the observer renders a real network `RemotePlayer`. Screenshots sample rest, outbound and return stages.

Evidence is under `build/weapon-check/evidence/{host,observer}`: `gallery-index.json`, native image
pages, held side images, animation events, native matrix samples, motion result summaries, identity
records, and frame metadata. Process logs are `build/weapon-check/{prepare,host,observer}.log`.
Game directories are `build/weapon-check/{host,observer}`; only crash reports should be retained from
those folders. Do not upload generated worlds, dependency jars, or unrelated runner files.
