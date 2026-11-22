#!/usr/bin/env bash
set -euo pipefail
test "${GITHUB_ACTIONS:-false}" = true
root=$(pwd)
case "${1:-all}" in
    survey)
        mode=survey
        survey_seed=${2:?Supply one declared survey seed}
        case "$survey_seed" in 152045|20261121|8675309) ;; *) echo 'Unknown fixed survey seed' >&2; exit 2 ;; esac
        candidate_path=${UWU_JAR:?Supply frozen candidate JAR}
        ;;
    placement|all)
        mode=${1:-all}
        candidate_path=${UWU_JAR:?Supply frozen candidate JAR}
        ;;
    *)
        # Retain the original all-in-one hosted invocation for reproducibility.
        mode=all
        candidate_path=${1:?Supply frozen candidate JAR}
        baseline_path=${2:?Supply exact integrated Playtest9 JAR}
        ;;
esac
candidate=$(realpath "$candidate_path")
test -f "$candidate"
candidate_sha=$(sha256sum "$candidate" | cut -d' ' -f1)
test "$candidate_sha" = "${UWU_EXPECTED_JAR_SHA256:?Supply frozen candidate hash}"
if test "$mode" != survey; then
    baseline=$(realpath "${baseline_path:-${UWU_BASELINE_JAR:?Supply exact integrated Playtest9 JAR}}")
    test -f "$baseline"
    baseline_sha=$(sha256sum "$baseline" | cut -d' ' -f1)
    test "$baseline_sha" = "${UWU_EXPECTED_BASELINE_JAR_SHA256:-0e865bbe4cf695144b70889138d82ad36ed8288a019bcb0e6b8b4851a6f1a04f}"
fi
mkdir -p build/dusk-frequency-evidence build/dusk-frequency-check
if test "$mode" = placement; then
    # Publish all counters and enforce both gain gates before installing a server.
    python dusk-frequency-check/compare.py | tee build/dusk-frequency-check/comparison.log
fi
if test -n "${UWU_PROBE_JAR:-}"; then
    probe=$(realpath "$UWU_PROBE_JAR")
    probe_sha=${UWU_EXPECTED_PROBE_SHA256:?Supply the independently built fixture hash}
    printf '%s  %s\n' "$probe_sha" "$probe" | sha256sum --check
else
    bash ./gradlew --no-daemon --no-build-cache --max-workers=2 -I dusk-frequency-check/probe.gradle duskFrequencyProbeJar
    probe=$(realpath build/dusk-frequency-check/dusk-frequency-probe.jar)
    probe_sha=$(sha256sum "$probe" | cut -d' ' -f1)
fi
server=$(mktemp -d "${RUNNER_TEMP:?}/uwu-dusk-frequency.XXXXXXXX")
active_stage=setup
preserve_crashes() {
    local result=$?
    trap - EXIT
    if test -d "$server/crash-reports"; then
        cp -R "$server/crash-reports" "$root/build/dusk-frequency-check/crash-reports-$active_stage" || true
    fi
    exit "$result"
}
trap preserve_crashes EXIT
cd "$server"
mkdir mods
cp "$probe" mods/dusk-frequency-probe.jar
printf '%s  %s\n' "$probe_sha" mods/dusk-frequency-probe.jar | sha256sum --check
fetch() {
    curl --retry 3 --retry-all-errors --connect-timeout 20 --max-time 150 --fail --silent --show-error --location "$1" --output "$2"
    printf '%s  %s\n' "$3" "$2" | sha256sum --check
}
fetch 'https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.249/neoforge-21.1.249-installer.jar' installer.jar d88b448eab73cd65bdf1720844a4828262de30a15fc71bd04dd81acc61c5399a
fetch 'https://cdn.modrinth.com/data/EltpO5cN/versions/ST5FHZnD/lootr-neoforge-1.21.1-1.11.38.125.jar' mods/lootr.jar 091878598fa5cb51cd304bf9324afe46420d328b6f77a9ba38acc15e26945e9e
fetch 'https://cdn.modrinth.com/data/ulloLmqG/versions/Q29JlZfU/another_furniture-neoforge-4.0.2.jar' mods/another_furniture.jar c61a66c73bcc5147d9983dc72c485493a612d2dbc078fcb86e2024155e34b7dd
timeout --kill-after=15s 6m java -Xmx1G -jar installer.jar --installServer
printf 'eula=true\n' >eula.txt
run_stage() {
    local stage=$1 jar=$2 hash=$3 world=$4 seed=$5 marker=$6
    active_stage="$stage-$seed"
    local log="$root/build/dusk-frequency-check/$stage-$seed.log"
    cp "$jar" mods/uwu-dimension.jar
    printf '%s  %s\n' "$hash" mods/uwu-dimension.jar | sha256sum --check
    printf 'server-ip=127.0.0.1\nserver-port=25598\nonline-mode=false\nlevel-type=minecraft:flat\nlevel-seed=%s\nlevel-name=%s\nview-distance=3\nsimulation-distance=3\nmax-tick-time=120000\nspawn-monsters=false\n' "$seed" "$world" >server.properties
    timeout --kill-after=20s 16m java -Xms512m -Xmx3g -XX:+ExitOnOutOfMemoryError \
        "-Duwu.check.frequencyStage=$stage" "-Duwu.check.seed=$seed" \
        "-Duwu.check.evidence=$root/build/dusk-frequency-evidence" \
        "-Duwu.check.candidateJar=$server/mods/uwu-dimension.jar" "-Duwu.check.candidateSha256=$hash" \
        "-Duwu.check.fixtureJar=$server/mods/dusk-frequency-probe.jar" "-Duwu.check.fixtureSha256=$probe_sha" \
        @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee "$log"
    if grep -Ei 'OutOfMemoryError|DUSK FREQUENCY CHECK FAILED|single server tick has taken' "$log"; then exit 1; fi
    grep -F 'UWU PACKAGED CANDIDATE PASS' "$log"
    grep -F 'DUSK PACKAGED FIXTURE PASS' "$log"
    grep -F "$marker" "$log"
}
if test "$mode" = survey; then
    run_stage survey-candidate "$candidate" "$candidate_sha" "candidate-survey-$survey_seed" "$survey_seed" 'DUSK FREQUENCY SURVEY PASS stage=survey-candidate'
    printf '%s  %s\n' "$candidate_sha" "$candidate" | sha256sum --check
    printf '%s  %s\n' "$probe_sha" "$probe" | sha256sum --check
    printf 'DUSK FREQUENCY SURVEY JOB PASS seed=%s\n' "$survey_seed" | tee "$root/build/dusk-frequency-check/survey-pass-$survey_seed.log"
    exit 0
fi
if test "$mode" = all; then
    for seed in 152045 20261121 8675309; do
        run_stage survey-baseline "$baseline" "$baseline_sha" "baseline-survey-$seed" "$seed" 'DUSK FREQUENCY SURVEY PASS stage=survey-baseline'
        run_stage survey-candidate "$candidate" "$candidate_sha" "candidate-survey-$seed" "$seed" 'DUSK FREQUENCY SURVEY PASS stage=survey-candidate'
    done
fi
cd "$root"
if test "$mode" = all; then python dusk-frequency-check/compare.py | tee build/dusk-frequency-check/comparison.log; fi
seed=$(cat build/dusk-frequency-evidence/selected-seed.txt)
cd "$server"
for sample_seed in 152045 20261121 8675309; do
    if test "$sample_seed" != "$seed"; then
        run_stage native-sample "$candidate" "$candidate_sha" "native-sample-$sample_seed" "$sample_seed" 'DUSK FREQUENCY NATIVE SEED PASS'
    fi
done
run_stage fresh "$candidate" "$candidate_sha" native-fresh "$seed" 'DUSK FREQUENCY PLACEMENT PASS'
run_stage restart "$candidate" "$candidate_sha" native-fresh "$seed" 'DUSK FREQUENCY RESTART PASS'
run_stage legacy-seed "$baseline" "$baseline_sha" native-legacy "$seed" 'DUSK FREQUENCY LEGACY SEED PASS'
run_stage legacy-upgrade "$candidate" "$candidate_sha" native-legacy "$seed" 'DUSK FREQUENCY LEGACY PASS stage=legacy-upgrade'
run_stage legacy-restart "$candidate" "$candidate_sha" native-legacy "$seed" 'DUSK FREQUENCY LEGACY PASS stage=legacy-restart'
printf '%s  %s\n' "$candidate_sha" "$candidate" | sha256sum --check
printf '%s  %s\n' "$baseline_sha" "$baseline" | sha256sum --check
printf '%s  %s\n' "$probe_sha" "$probe" | sha256sum --check
printf 'DUSK FREQUENCY SERVER PASS surveySeeds=3 nativeSeed=%s\n' "$seed" | tee "$root/build/dusk-frequency-check/server-pass.log"
