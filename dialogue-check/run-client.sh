#!/usr/bin/env bash
set -euo pipefail
test "${GITHUB_ACTIONS:-false}" = true
export UWU_DIALOGUE_MODE=${1:?Supply without-reference or with-reference}
case "$UWU_DIALOGUE_MODE" in without-reference|with-reference) ;; *) exit 2 ;; esac
test -f "${UWU_JAR:?Supply the frozen candidate JAR}"
printf '%s  %s\n' "${UWU_EXPECTED_JAR_SHA256:?Supply candidate SHA-256}" "$UWU_JAR" | sha256sum --check
game_dir="build/dialogue-$UWU_DIALOGUE_MODE"
deps="build/dialogue-deps/$UWU_DIALOGUE_MODE"
log="build/dialogue-$UWU_DIALOGUE_MODE.log"
mkdir -p "$deps" "$game_dir/config" "build/dialogue-evidence/$UWU_DIALOGUE_MODE"
fetch() {
    curl --retry 3 --retry-all-errors --fail --silent --show-error --location --max-time 120 "$1" --output "$deps/$2.jar"
    printf '%s  %s\n' "$3" "$deps/$2.jar" | sha256sum --check
}
fetch 'https://cdn.modrinth.com/data/EltpO5cN/versions/ST5FHZnD/lootr-neoforge-1.21.1-1.11.38.125.jar' lootr 091878598fa5cb51cd304bf9324afe46420d328b6f77a9ba38acc15e26945e9e
if [[ "$UWU_DIALOGUE_MODE" == with-reference ]]; then
    fetch 'https://mediafilez.forgecdn.net/files/8470/425/chatbubbles-1.1.1%2B1.21.1.jar' chatbubbles 9cfc0de144d71462626063440b13b35a55147c3f9881589b93d7ef0792921986
else
    test ! -e "$deps/chatbubbles.jar"
fi
printf 'version:3955\nonboardAccessibility:false\ntutorialStep:none\nrenderDistance:3\nsimulationDistance:5\nmaxFps:60\nenableVsync:false\npauseOnLostFocus:false\nguiScale:2\nchatVisibility:0\nchatOpacity:1.0\nchatScale:1.0\n' > "$game_dir/options.txt"
printf 'earlyWindowControl = false\n' > "$game_dir/config/fml.toml"
export LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null
timeout --kill-after=20s 12m xvfb-run -a -s '-screen 0 1280x720x24' bash gradlew --no-daemon --max-workers=2 -I dialogue-check/client.gradle runDialogueClient 2>&1 | tee "$log"
if grep -Ei 'OutOfMemoryError|CHECK FAILED|single server tick has taken' "$log"; then exit 1; fi
for marker in 'UWU PACKAGED CANDIDATE PASS' 'KEEPER SPEECH RECIPIENTS PASS' 'KEEPER SPEECH CLIENT PASS' 'KEEPER SPEECH RENDER PASS'; do
    grep -F "$marker" "$log"
done
grep -F "KEEPER SPEECH COMPATIBILITY PASS mode=$UWU_DIALOGUE_MODE" "$log"
for name in petal-speech dusk-speech dusk-conversation dusk-reply expired; do
    test -s "build/dialogue-evidence/$UWU_DIALOGUE_MODE/$name.png"
done
printf '%s  %s\n' "$UWU_EXPECTED_JAR_SHA256" "$UWU_JAR" | sha256sum --check
