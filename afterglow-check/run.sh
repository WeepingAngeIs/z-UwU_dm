#!/usr/bin/env bash
set -euo pipefail
test "${GITHUB_ACTIONS:-}" = true
test "${GITHUB_REPOSITORY:-}" = WeepingAngeIs/z-UwU_dm
game=build/afterglow/client
mkdir -p "$game/config" "$game/mods" "$game/shaderpacks" build/afterglow/{evidence,deps}
test ! -d "$game/saves"
fetch() {
    curl --retry 3 --retry-all-errors --fail --silent --show-error --location --max-time 140 "$1" -o "build/afterglow/deps/$2.jar"
    printf '%s  %s\n' "$3" "build/afterglow/deps/$2.jar" | sha256sum --check
}
fetch 'https://cdn.modrinth.com/data/YL57xq9U/versions/KduFYu4t/iris-neoforge-1.8.14-beta.1%2Bmc1.21.1.jar' iris 60d5f8bf52f25e9986440f4a4270a6bd986d9cff994510e1108ac1547063b314
fetch 'https://cdn.modrinth.com/data/AANobbMI/versions/uMOpc5uV/sodium-neoforge-0.8.13%2Bmc1.21.1.jar' sodium 575d187b15328d7bddd8a9b1bee64333c22c14735f5780ec49eac0cd5503c08d
# Sodium's distribution is an early service that discovers its immutable nested mod.
# Putting the wrapper on ModDev's runtime classpath selects it as the mod instead.
mv build/afterglow/deps/sodium.jar "$game/mods/sodium-neoforge-0.8.13+mc1.21.1.jar"
fetch 'https://cdn.modrinth.com/data/EltpO5cN/versions/ST5FHZnD/lootr-neoforge-1.21.1-1.11.38.125.jar' lootr 091878598fa5cb51cd304bf9324afe46420d328b6f77a9ba38acc15e26945e9e
cp 'build/afterglow/dist/UwU Afterglow.zip' "$game/shaderpacks/"
(cd "$game/shaderpacks" && sha256sum -c ../../dist/'UwU Afterglow.sha256')
printf 'enableShaders=false\nshaderPack=UwU Afterglow.zip\n' > "$game/config/iris.properties"
printf 'earlyWindowControl = false\n' > "$game/config/fml.toml"
printf 'version:3955\nonboardAccessibility:false\ntutorialStep:none\nrenderDistance:4\nsimulationDistance:5\nmaxFps:260\nenableVsync:false\npauseOnLostFocus:false\nfov:0.0\nbobView:false\nrenderClouds:false\ngamma:0.5\n' > "$game/options.txt"
export LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null LP_NUM_THREADS=3
screen=960x540x24
if [[ "${AFTERGLOW_BENCH:-}" == true ]]; then screen=1920x1080x24; fi
timeout --kill-after=30s 33m xvfb-run -a -s "-screen 0 $screen" bash gradlew --no-daemon --max-workers=2 -I afterglow-check/client.gradle runAfterglowClient 2>&1 | tee build/afterglow/launch.log
grep -F 'UWU PACKAGED CANDIDATE PASS' build/afterglow/launch.log
grep -F 'AFTERGLOW CLIENT FINISHED' build/afterglow/launch.log
if grep -Ei 'AFTERGLOW CHECK FAILED|shader compilation failed|failed to create shader rendering pipeline|OutOfMemoryError' build/afterglow/launch.log; then exit 1; fi
/usr/bin/python3 afterglow-check/analyze.py build/afterglow/evidence
