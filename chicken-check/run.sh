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
if [[ -n "${UWU_SHADER_ZIP:-}" ]]; then
  printf '120edd7bdb18294e8f0e7e07e366dc5694057aa06091cd8fb3e5baf18a3e4c06  %s\n' "$UWU_SHADER_ZIP" | sha256sum --check
  for dep in iris sodium; do
    if [[ "$dep" = iris ]]; then
      url='https://cdn.modrinth.com/data/YL57xq9U/versions/KduFYu4t/iris-neoforge-1.8.14-beta.1%2Bmc1.21.1.jar'
      hash='60d5f8bf52f25e9986440f4a4270a6bd986d9cff994510e1108ac1547063b314'
    else
      url='https://cdn.modrinth.com/data/AANobbMI/versions/uMOpc5uV/sodium-neoforge-0.8.13%2Bmc1.21.1.jar'
      hash='575d187b15328d7bddd8a9b1bee64333c22c14735f5780ec49eac0cd5503c08d'
    fi
    curl --retry 3 --retry-all-errors --fail --silent --show-error --location --max-time 140 "$url" -o "build/chicken-deps/$dep.jar"
    printf '%s  %s\n' "$hash" "build/chicken-deps/$dep.jar" | sha256sum --check
  done
fi
for role in host observer; do
  dir="build/chickens/$role"
  mkdir -p "$dir/config" "$dir/tmp" "$dir/lwjgl"
  if [[ -n "${UWU_SHADER_ZIP:-}" ]]; then
    mkdir -p "$dir/mods"
    cp build/chicken-deps/sodium.jar "$dir/mods/sodium-neoforge-0.8.13+mc1.21.1.jar"
  fi
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
    if [[ -n "${UWU_SHADER_ZIP:-}" ]]; then
      dir="build/chickens/$role"
      mkdir -p "$dir/shaderpacks"
      cp "$UWU_SHADER_ZIP" "$dir/shaderpacks/UwU Afterglow.zip"
      active=false
      if [[ "$role:$UWU_QA_PASS" = observer:fresh || "$role:$UWU_QA_PASS" = host:reload ]]; then active=true; fi
      printf 'enableShaders=%s\nshaderPack=UwU Afterglow.zip\n' "$active" > "$dir/config/iris.properties"
    fi
    timeout --kill-after=10s 13m xvfb-run -a -s '-screen 0 1024x768x24' \
      bash "$root/build/moddev/runChicken${role^}.sh" > "build/chickens/$UWU_QA_PASS-$role.log" 2>&1 &
    pids+=("$!")
  done
  failed=0
  first=
  wait -n -p first "${pids[@]}" || failed=1
  first_role=host
  if [[ "$first" = "${pids[1]}" ]]; then first_role=observer; fi
  if [[ "$failed" != 0 ]] || ! grep -q "NATIVE CHICKEN GALLERY PASS $UWU_QA_PASS $first_role" "build/chickens/$UWU_QA_PASS-$first_role.log"; then
    failed=1
    cleanup
  fi
  for pid in "${pids[@]}"; do
    if [[ "$pid" != "$first" ]]; then wait "$pid" || failed=1; fi
  done
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
