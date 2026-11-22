#!/usr/bin/env bash
set -euo pipefail
test "${GITHUB_ACTIONS:-false}" = true
test "${GITHUB_REPOSITORY:-}" = WeepingAngeIs/z-UwU_dm
root=$(pwd)
export UWU_JAR=$(realpath "${UWU_JAR:?Packaged candidate is required}")
test -n "${UWU_EXPECTED_JAR_SHA256:?Candidate digest is required}"
printf '%s  %s\n' "$UWU_EXPECTED_JAR_SHA256" "$UWU_JAR" | sha256sum --check
bash weapon-check/fetch-deps.sh
mkdir -p build/weapon-check/control build/weapon-check/evidence/{host,observer}
for role in host observer; do
    test ! -d "build/weapon-check/$role/saves"
    mkdir -p "build/weapon-check/$role/config/bettercombat" "build/weapon-check/$role/tmp" "build/weapon-check/$role/lwjgl"
    # 1.21.1 serializes FOV as (degrees - 70) / 40; simulation distance starts at five.
    printf 'version:3955\nonboardAccessibility:false\ntutorialStep:none\nrenderDistance:3\nsimulationDistance:5\nmaxFps:45\nenableVsync:false\npauseOnLostFocus:false\nguiScale:1\nfov:0.0\n' > "build/weapon-check/$role/options.txt"
    printf '{"firstPersonAnimations":"AUTO"}\n' > "build/weapon-check/$role/config/bettercombat/client.json5"
    printf 'earlyWindowControl = false\n' > "build/weapon-check/$role/config/fml.toml"
done
# MDG prepares both classpaths and compiles the fixture once before either native JVM starts.
# Its generated launch scripts run the unchanged packaged product, not main.output.
bash ./gradlew --no-daemon --no-build-cache --max-workers=2 -I weapon-check/client.gradle \
    createWeaponHostLaunchScript createWeaponObserverLaunchScript 2>&1 | tee build/weapon-check/prepare.log
export LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null LP_NUM_THREADS=2
host_pid= observer_pid=
cleanup() {
    if [[ -n "$host_pid" ]]; then kill "$host_pid" 2>/dev/null || true; fi
    if [[ -n "$observer_pid" ]]; then kill "$observer_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT
timeout --kill-after=20s 18m xvfb-run -a -s '-screen 0 1024x768x24' \
    bash "$root/build/moddev/runWeaponHost.sh" > build/weapon-check/host.log 2>&1 &
host_pid=$!
timeout --kill-after=20s 18m xvfb-run -a -s '-screen 0 1024x768x24' \
    bash "$root/build/moddev/runWeaponObserver.sh" > build/weapon-check/observer.log 2>&1 &
observer_pid=$!
host_result=0 observer_result=0
first_result=0
first_pid=
wait -n -p first_pid "$host_pid" "$observer_pid" || first_result=$?
if [[ "$first_result" != 0 ]]; then
    cleanup
fi
if [[ "$first_pid" == "$host_pid" ]]; then host_result=$first_result; else wait "$host_pid" || host_result=$?; fi
host_pid=
if [[ "$first_pid" == "$observer_pid" ]]; then observer_result=$first_result; else wait "$observer_pid" || observer_result=$?; fi
observer_pid=
cat build/weapon-check/host.log
cat build/weapon-check/observer.log
test "$first_result" = 0 && test "$host_result" = 0 && test "$observer_result" = 0
if grep -Ei 'UWU WEAPONS CHECK FAILED|OutOfMemoryError|single server tick has taken' build/weapon-check/{host,observer}.log; then exit 1; fi
grep -F 'UWU WEAPONS PACKAGED CANDIDATE PASS role=host' build/weapon-check/host.log
grep -F 'UWU WEAPONS PACKAGED CANDIDATE PASS role=observer' build/weapon-check/observer.log
grep -F 'UWU WEAPONS NATIVE GALLERY PASS' build/weapon-check/host.log
grep -F 'UWU BLADE HELD VIEWS PASS representatives=12 views=48' build/weapon-check/host.log
grep -F 'UWU VESPER TRAVERSAL CERTIFICATION PASS variants=8 views=32 snowkiss=4 negative_controls=100' build/weapon-check/host.log
grep -F 'UWU WEAPONS OBSERVER TCP PASS' build/weapon-check/observer.log
grep -F 'UWU CHAKRAM HOST MATRIX PASS trials=64' build/weapon-check/host.log
grep -F 'UWU CHAKRAM OBSERVER MATRIX PASS trials=64' build/weapon-check/observer.log
if [[ ${UWU_WEAPON_BASELINE:-false} == true ]]; then
    grep -F 'UWU WEAPONS BASELINE OBSERVED' build/weapon-check/host.log
    grep -F 'UWU WEAPONS OBSERVER BASELINE OBSERVED' build/weapon-check/observer.log
else
    grep -F 'UWU CHAKRAM HOST COMBO MATRIX PASS trials=4 native_attacks=8' build/weapon-check/host.log
    grep -F 'UWU CHAKRAM OBSERVER COMBO MATRIX PASS trials=4 native_attacks=8' build/weapon-check/observer.log
    grep -F 'UWU BLADE NATIVE MODEL PASS' build/weapon-check/host.log
    grep -F 'UWU WEAPONS CLIENT PASS' build/weapon-check/host.log
    grep -F 'UWU WEAPONS OBSERVER PASS' build/weapon-check/observer.log
fi
printf '%s  %s\n' "$UWU_EXPECTED_JAR_SHA256" "$UWU_JAR" | sha256sum --check
