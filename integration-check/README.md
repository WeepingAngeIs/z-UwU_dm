# Combined Playtest 9 integration candidate

This isolated integration combines the complete changes from these two verified source commits:

- Cupid Bow and A Lantern for Both Shores: `9b1d9fdffd5e385ae4f18f8e49e05559679ec306`.
- Cave flora, UwU Berry harvesting, and Dusk placement/preservation: `c7f3026cec0f673c7b9eb45a39d26f880283f912`.

Their exact common baseline is `e16af6a2a1a00cb68291b6c9611d4604a0b20171` (Playtest 9).
The branch is `codex/integration/playtest9-combined-805cfd`; the combined version is
`1.5.0-beta.13-combined-rc.2-playtest.9-integrated.1`.

## Reconciliation

Both runtime registrations are retained: the Cupid client visual setting and the cave berry registry.
The mixin list retains both Cupid/book client hooks, adds the cave-vine harvesting hook, and removes
the superseded villager factory hook together with its deleted source. Production files changed by
only one worker are required to match that worker's exact source commit. No implementation from either
workstream is replaced by the other worker's version.

The shared client fixture retains independent cave, Cupid/book, shrine and Dirk routes. The integration
workflow runs the cave and Cupid/book routes separately against the same frozen production JAR, and
the runtime fixture checks the actual loaded mod file and its SHA-256. The book probe also holds the
first manually selected page 2 until its screenshot has been saved, then checks normal backward navigation.

The earlier standalone scope gates remain unchanged as historical workstream checks. The integration
gate replaces their mutually exclusive source allowlists with the exact combined scope, retains the
bow mesh/core checks, and compares all packaged content outside that scope with the original Playtest 9 JAR.

## Hosted validation and delivery

Only `.github/workflows/uwu-combined-integration.yml` is used for this candidate. It runs on the public
repository's standard GitHub-hosted Ubuntu runner, builds once, freezes the JAR and records its hash.
The hosted bootstrap seeds the unmodified Gradle wrapper from Gradle's official download endpoint,
using the repository's pinned distribution checksum, and adds an official Maven Central endpoint
before settings-plugin resolution. Product dependencies and their versions remain unchanged.
Every suite checks that same file's hash before and after execution:

1. Actual ripe tip/body berry harvest, regrowth, light, edible item, Jade captions, cave palette and vanilla controls.
2. Rendered bow silhouettes, both hands, draw/release, visual held scale, book opening to page 1 and the first manual page turn.
3. Fresh Dusk placement in both chunk orders and restart, then a genuine Playtest 9 historical site/resident fixture through upgrade and restart.
4. Native shrine generation in both chunk orders, persistence and Dusk exclusion.
5. All three fixed waterfalls on fresh native terrain.

The passing JAR is uploaded only after all suites and the combined provenance gate succeed. Logs,
rendered screenshots, source provenance and a baseline-to-integration patch are retained as evidence.
Prior independent runs are context, not acceptance evidence for this combined candidate.

Do not compile or launch these fixtures locally for this assignment. All generated worlds and server
processes belong to disposable GitHub Actions fixtures. No existing user worlds, live server, installation,
default-branch merge, public release or private-asset upload is part of this work.
