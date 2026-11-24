#!/usr/bin/env bash
set -euo pipefail
test "${GITHUB_ACTIONS:-}" = true
test "${GITHUB_REPOSITORY:-}" = WeepingAngeIs/z-UwU_dm
root=$(pwd)
printf '%s  %s\n' "$UWU_EXPECTED_JAR_SHA256" "$UWU_JAR" | sha256sum --check
mkdir -p build/chicken-deps build/chickens/control
curl --retry 3 --retry-all-errors --fail --silent --show-error --location --max-time 120 \
  'https://cdn.modrinth.com/data/EltpO5cN/versions/ST5FHZnD/lootr-neoforge-1.21.1-1.11.38.125.jar' -o build/chicken-deps/lootr.jar
printf '091878598fa5cb51cd304bf9324afe46420d328b6f77a9ba38acc15e26945e9e  build/chicken-deps/lootr.jar\n' | sha256sum --check
for role in host observer; do
  dir="build/chickens/$role"
  mkdir -p "$dir/config" "$dir/tmp" "$dir/lwjgl"
  test ! -d "$dir/saves"
  printf 'version:3955\nonboardAccessibility:false\ntutorialStep:none\nrenderDistance:3\nsimulationDistance:5\nmaxFps:35\nenableVsync:false\npauseOnLostFocus:false\nbobView:false\nentityShadows:true\n' > "$dir/options.txt"
  printf 'earlyWindowControl = false\n' > "$dir/config/fml.toml"
done
bash gradlew --no-daemon --max-workers=2 -I chicken-check/clients.gradle createChickenHostLaunchScript createChickenObserverLaunchScript
export LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null LP_NUM_THREADS=2
pids=()
cleanup() { for pid in "${pids[@]}"; do kill "$pid" 2>/dev/null || true; done; }
trap cleanup EXIT
for UWU_QA_PASS in fresh reload; do
  export UWU_QA_PASS
  for role in host observer; do
    timeout --kill-after=10s 13m xvfb-run -a -s '-screen 0 1024x768x24' \
      bash "$root/build/moddev/runChicken${role^}.sh" > "build/chickens/$UWU_QA_PASS-$role.log" 2>&1 &
    pids+=("$!")
  done
  failed=0
  for pid in "${pids[@]}"; do wait "$pid" || failed=1; done
  pids=()
  for role in host observer; do
    log="build/chickens/$UWU_QA_PASS-$role.log"
    tail -45 "$log"
    grep -F "NATIVE CHICKEN GALLERY PASS $UWU_QA_PASS $role" "$log"
    grep -F "UWU WEAPONS PACKAGED CANDIDATE PASS role=$role" "$log"
  done
  test "$failed" = 0
done
grep -F 'NATIVE WILD CHICKEN PASS' build/chickens/fresh-host.log
python3 chicken-check/evidence.py
printf '%s  %s\n' "$UWU_EXPECTED_JAR_SHA256" "$UWU_JAR" | sha256sum --check
