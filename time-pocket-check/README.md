# Combined UwUzi acceptance

This disposable observer is based on the isolated UwUzi handoff at
`687071e25616fbf6d9f2107c151b8827c2ba3ef7`. It does not compile or load the product
source output. The product JAR must already be sealed by the single combined
package job.

## Workflow commands

Use separate matrix jobs with `POCKET_SIDE: client` and `POCKET_SIDE: server` on
the existing public repository's standard `ubuntu-24.04` runner. Set a 40-minute
job timeout. Download the candidate artifact to `build/combined-candidate`, then
set `UWU_JAR` to its one absolute JAR path and `UWU_EXPECTED_JAR_SHA256` to the
package job's sealed hash. Run the normal combined candidate verification step
before this entry point:

```bash
bash combined-check/run-pocket.sh
```

The runner bootstraps the existing Gradle wrapper, verifies and downloads the
same seven pinned runtime dependencies as `combined-check/run-client.sh`, and
compiles only the disposable `timePocketCheck` source set. The QA task graph
rejects `:compileJava`, `:processResources`, `:classes` and `:jar`.

After its GitHub Actions and public-repository guards pass, the client job checks
for Xvfb/xauth, loadable GL/OpenAL libraries and system Python with NumPy/Pillow.
If prerequisites are missing, it conditionally installs the needed packages on
that disposable hosted runner (`xvfb`, `xauth`, `libgl1`, `libgl1-mesa-dri`,
`libopenal1`, `python3-numpy`, `python3-pil`) and checks again. Do not invoke this
runner locally. The server job runs write/restart stages in the same disposable
world, bounded to 12 and 9 minutes; the client invocation is bounded to 22 minutes.
The 40-minute workflow timeout also bounds prerequisite setup and bootstrapping.

Always upload `build/combined-evidence/pocket-${POCKET_SIDE}` with
`if: always()`. It contains candidate provenance copies, before/after JAR hash
checks, dependency hashes, package checks, native logs and configuration. The
client additionally emits actual Minecraft screenshots, pixel measurements,
`audio.txt` and `uwuzi-models.json`; server evidence includes both startup logs
and saved settings. An EXIT trap collects evidence and checks the JAR even if
an assertion or process fails.

## Mandatory checks

- Exactly the three approved embedded opening/loop/thaw cues by SHA-256, both
  in the product ZIP and through Minecraft's runtime resource manager. No
  private resource pack is accepted. The original MP3/WAV formats are rejected.
- The two earlier public pocket tick/tock resources retain their Git blob hashes.
- The actual loaded product's ModList path and SHA-256 match the sealed JAR,
  with no product source-output entry in `fml.modFolders`.
- Both UwUzi variants render the approved GUI sprite, retain the vanilla clock
  sprite in first/third person, and are observed in native GUI draws. The initial
  hotbar contains both variants for screenshot review.
- Existing native attack/use checks, five-chunk boundaries, 4-second standard
  cast and 12-second cooldown, 20-second no-cooldown cast and immediate recast,
  next-cast duration changes, spectator cancellation, particles, simulation,
  walking/floor/wall collision, native audio and dedicated settings persistence.
- Better Combat and Spell Engine are loaded for the client input checks.

The completion markers are `TIME POCKET CLIENT PASS`,
`UWUZI BOTH GUI ICONS PASS`, `UWUZI DEDICATED WRITE PASS`,
`UWUZI DEDICATED RESTART PASS` and `UWUZI DISK SETTINGS PASS`.
The runner also requires all four native cast markers and exact-JAR markers.

## Interpretation

Audio evidence observes real native OpenAL buffers, pitch, loop cadence, source
starts, cancellation and ducking on a hosted null output device. It does not
prove a listener's subjective sound quality. GUI assertions prove resource and
baked-model selection plus native draw calls; screenshots still need inspection.
The gallery covers clock/star front, side and rear at 2.6 and 5.5 blocks with
small camera nudges. Multi-client simultaneous casting and every cancellation
path are outside this bounded check. Disposable practice-block breaking belongs
to the observer; it is not a product Clock Star feature.
