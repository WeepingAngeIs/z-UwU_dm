#!/usr/bin/env bash
set -euo pipefail
test "$GITHUB_ACTIONS" = true
seed=$1
root=$PWD
input=$root/build/village-input
out=$root/build/village-evidence
mkdir -p "$out"
(cd "$input" && sha256sum -c checksums.txt)
server=$(mktemp -d "$RUNNER_TEMP/uwu-villages.XXXXXXXX")
cd "$server"
mkdir mods
cp "$input/probe.jar" mods/probe.jar
fetch() {
    curl --retry 3 --retry-all-errors -fsSL --max-time 150 "$1" -o "$2"
    printf '%s  %s\n' "$3" "$2" | sha256sum -c
}
fetch https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.249/neoforge-21.1.249-installer.jar installer.jar d88b448eab73cd65bdf1720844a4828262de30a15fc71bd04dd81acc61c5399a
fetch https://cdn.modrinth.com/data/EltpO5cN/versions/ST5FHZnD/lootr-neoforge-1.21.1-1.11.38.125.jar mods/lootr.jar 091878598fa5cb51cd304bf9324afe46420d328b6f77a9ba38acc15e26945e9e
fetch https://cdn.modrinth.com/data/ulloLmqG/versions/Q29JlZfU/another_furniture-neoforge-4.0.2.jar mods/another_furniture.jar c61a66c73bcc5147d9983dc72c485493a612d2dbc078fcb86e2024155e34b7dd
timeout --kill-after=15s 6m java -Xmx1G -jar installer.jar --installServer
printf 'eula=true\n' >eula.txt
stages=(fresh)
if [[ "$seed" == 152045 ]]; then stages+=(restart oldpartial upgrade); fi
for stage in "${stages[@]}"; do
    source=candidate
    world=freshworld
    if [[ "$stage" == oldpartial || "$stage" == upgrade ]]; then world=legacyworld; fi
    if [[ "$stage" == oldpartial ]]; then source=baseline; fi
    cp "$input/$source.jar" mods/uwu-dimension.jar
    sha=$(sha256sum "$input/$source.jar" | cut -d' ' -f1)
    printf 'server-ip=127.0.0.1\nserver-port=25598\nonline-mode=false\nlevel-type=minecraft:flat\nlevel-name=%s\nlevel-seed=%s\ndifficulty=normal\nspawn-monsters=false\nspawn-protection=0\nview-distance=3\nsimulation-distance=3\nmax-tick-time=240000\n' "$world" "$seed" >server.properties
    log="$out/$stage-$seed.log"
    timeout --kill-after=20s 20m java -Xms512m -Xmx4G -XX:+ExitOnOutOfMemoryError \
        "-Duwu.village.seed=$seed" "-Duwu.village.stage=$stage" "-Duwu.village.out=$out" \
        "-Duwu.check.candidateJar=$server/mods/uwu-dimension.jar" "-Duwu.check.candidateSha256=$sha" \
        -Duwu.check.role=village @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee "$log"
    grep -F "UWU VILLAGE SIZES PASS $stage $seed" "$log"
    if grep -F 'UWU VILLAGE SIZES FAILED' "$log"; then exit 1; fi
done
