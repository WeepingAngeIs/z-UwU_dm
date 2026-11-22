#!/usr/bin/env bash
set -euo pipefail
test "${GITHUB_ACTIONS:-false}" = true
test -f "${UWU_JAR:?Supply frozen candidate}"
test -f "${UWU_COMPANION_JAR:?Supply hosted patched UnyieldingQuests module}"
printf '%s  %s\n' "${UWU_EXPECTED_JAR_SHA256:?Supply frozen hash}" "$UWU_JAR" | sha256sum --check
test -s build/dusk-frequency-evidence/selected-seed.txt
deps=build/dusk-frequency-deps
game_dir=build/dusk-frequency-client
mkdir -p "$deps" "$game_dir/config" build/dusk-frequency-evidence
fetch(){
    curl --retry 3 --retry-all-errors --fail --silent --show-error --location --max-time 120 "$1" --output "$deps/$2.jar"
    printf '%s  %s\n' "$3" "$deps/$2.jar" | sha256sum --check
}
fetch 'https://cdn.modrinth.com/data/EltpO5cN/versions/ST5FHZnD/lootr-neoforge-1.21.1-1.11.38.125.jar' lootr 091878598fa5cb51cd304bf9324afe46420d328b6f77a9ba38acc15e26945e9e
printf 'version:3955\nonboardAccessibility:false\ntutorialStep:none\nrenderDistance:3\nsimulationDistance:5\nmaxFps:60\nenableVsync:false\npauseOnLostFocus:false\nguiScale:2\n' > "$game_dir/options.txt"
printf 'earlyWindowControl = false\n' > "$game_dir/config/fml.toml"
export LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null
timeout --kill-after=20s 18m xvfb-run -a -s '-screen 0 1280x720x24' bash gradlew --no-daemon --max-workers=2 -I dusk-frequency-check/client.gradle runDuskFrequencyClient 2>&1 | tee build/dusk-frequency-client.log
if grep -Ei 'OutOfMemoryError|CHECK FAILED|single server tick has taken' build/dusk-frequency-client.log; then exit 1; fi
for marker in 'UWU PACKAGED CANDIDATE PASS' 'DUSK FREQUENCY CONFIG GUARDS PASS' 'DUSK FREQUENCY UI PASS'; do
    grep -F "$marker" build/dusk-frequency-client.log
done
for value in 0 75 100; do
    grep -F "DUSK FREQUENCY UI EDIT PASS percent=$value " build/dusk-frequency-client.log
    test -s "build/dusk-frequency-evidence/ui-edited-$value.png"
    test -s "build/dusk-frequency-evidence/ui-saved-$value.png"
done
test -s build/dusk-frequency-evidence/ui.json
printf '%s  %s\n' "$UWU_EXPECTED_JAR_SHA256" "$UWU_JAR" | sha256sum --check
