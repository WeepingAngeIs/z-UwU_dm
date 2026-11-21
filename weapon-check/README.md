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
The final gallery hides the vanilla HUD before measuring pixels and records `hud_hidden=true`.
Older baseline galleries with the HUD visible have contaminated last-row measurements on each
five-row page; those rows must be excluded from numerical comparisons. Their baked-model bounds
and actual screenshots remain valid observations.

For every resolved blade-relief model, the candidate fixture inspects the actual native original
and added models. It verifies original pass identity/order and complete quad/light data for both
render modes, all display pose and normal matrices in both hands, unchanged X/Y bounds, and relief
outside both sides of the original depth slab. It then captures candidate/original native GUI
pairs in the same frame and requires pixel equality. A fixture-only redirect fixes vanilla's
glint clock during these GUI pairs, with one forced-foil copy proving the hook executes; all
other native rendering uses the live clock. This changes no product option or animation.
The GUI uses the identical original model; held views use the relief wrapper. This proof covers
every resolved gallery variant that selects the relief model, not just representative roots.
Results are `blade-native-models.json`, `blade-gui-identity.json`, and `gui-identity-*.png`.
The final marker is `UWU BLADE NATIVE MODEL PASS models=N transforms=T gui_pairs=N+1 glint_calls=G`.
There is one GUI pair per resolved relief variant plus the single native foil control.
The exact expected coverage is 314 resolved relief variants, 5,652 transform comparisons,
315 GUI pairs on 79 native screenshot pages, and a positive fixed-glint call count.

An additional 48 actual held captures cover 12 blade representatives in first/third person
and both physical hands: the native axe, scythe, dagger, spear and trident families; each
actual counter blade shape; and the independently refined Returnbloom OBJ. The native item
draw observer must see the expected hand/view context before capture. The result inventory is
`blade-held-views.json`, its screenshots are `blade-held-*.png`, and the required marker is
`UWU BLADE HELD VIEWS PASS representatives=12 views=48 first_person=24 third_person=24`.

While the native Vesper is held, the fixture resolves all eight element/credit models and
checks the existing traversal model, silhouette CRC and UV certification in both views/hands.
The production recognizer first verifies the actual selected-model identity, then reads
the unchanged native composite inside only its own relief wrapper. It never unwraps an
arbitrary model wrapper or sends the added relief faces through the native UV/CRC guard.
It preserves rejection of off-hand, GUI and unselected models, including a native base passed
in place of the actual selected wrapper. Four Snowkiss view/hand controls confirm the unchanged
ordinary composite path. The actual approved grip comes from `TraversalRenderPass.WRAP`.
`vesper-traversal.json` records 32 Vesper positives, four Snowkiss controls, 100 negative controls,
and 32 extra native-base identity rejections in candidate mode. This checks the model-dependent
traversal guard; it does not activate a new movement ability or change combat mechanics.
The final collector requires the full element/credit/view/arm matrix, exact shipped tip and
approved grip coordinates, native composite identity, and every rejection-control count.

Four additional consecutive-combo trials cover twinblade and katana in both owner
views, with the independent observer watching all eight native attacks. Both existing
throw clips must travel more than two blocks in the recorded native draw coordinates
and return within 0.45 before the next attack or completion. These are ordinary
consecutive attack inputs; the fixture never sets combo state or dispatches clips.
`combo-trial-index.json`, `combo-results.json` and `chakram-combo-*.png` record the
actual two-client evidence. All sixteen physical throws must also share the same
projectile UUID across clients and retain baseline return durability.

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
