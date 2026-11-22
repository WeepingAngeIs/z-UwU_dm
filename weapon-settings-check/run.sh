#!/usr/bin/env bash
set -euo pipefail
test "${GITHUB_ACTIONS:-false}" = true
root=$PWD
candidate=$(realpath build/libs/*-weapon-settings.1.jar)
probe=$(realpath build/weapon-settings-check/weapon-settings-probe.jar)
out="$root/build/weapon-settings-check"
mkdir -p "$out/evidence"
sha=$(sha256sum "$candidate" | cut -d' ' -f1)
printf '%s  %s\n' "$sha" "${candidate##*/}" >"$out/evidence/SHA256SUMS.txt"
printf '%s\n' "$GITHUB_SHA" >"$out/evidence/source.txt"
server=$(mktemp -d "$RUNNER_TEMP/uwu-balance.XXXXXXXX")
cd "$server"
mkdir mods
cp "$candidate" mods/uwu-dimension.jar
cp "$probe" mods/probe.jar
fetch() {
    curl --retry 3 --retry-all-errors -fsSL --max-time 150 "$1" -o "$2"
    printf '%s  %s\n' "$3" "$2" | sha256sum -c
}
fetch 'https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.249/neoforge-21.1.249-installer.jar' installer.jar d88b448eab73cd65bdf1720844a4828262de30a15fc71bd04dd81acc61c5399a
fetch 'https://cdn.modrinth.com/data/EltpO5cN/versions/ST5FHZnD/lootr-neoforge-1.21.1-1.11.38.125.jar' mods/lootr.jar 091878598fa5cb51cd304bf9324afe46420d328b6f77a9ba38acc15e26945e9e
fetch 'https://cdn.modrinth.com/data/ulloLmqG/versions/Q29JlZfU/another_furniture-neoforge-4.0.2.jar' mods/another_furniture.jar c61a66c73bcc5147d9983dc72c485493a612d2dbc078fcb86e2024155e34b7dd
timeout --kill-after=15s 6m java -Xmx1G -jar installer.jar --installServer
printf 'eula=true\n' >eula.txt
printf 'server-ip=127.0.0.1\nserver-port=25598\nonline-mode=false\nlevel-type=minecraft:flat\nlevel-name=fixture\nlevel-seed=927062\ndifficulty=normal\nspawn-protection=0\nspawn-monsters=false\nview-distance=3\nsimulation-distance=3\n' >server.properties
for stage in fresh restart; do
    timeout --kill-after=15s 8m java -Xms512m -Xmx3g -XX:+ExitOnOutOfMemoryError \
        "-Duwu.check.candidateJar=$server/mods/uwu-dimension.jar" "-Duwu.check.candidateSha256=$sha" \
        "-Duwu.check.stage=$stage" "-Duwu.check.out=$out/evidence" \
        @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee "$out/$stage.log"
    grep -F "UWU WEAPON SETTINGS PASS $stage" "$out/$stage.log"
    if grep -F 'UWU WEAPON SETTINGS FAILED' "$out/$stage.log"; then exit 1; fi
done
