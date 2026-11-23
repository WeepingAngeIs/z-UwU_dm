#!/usr/bin/env bash
set -euo pipefail
test "$GITHUB_ACTIONS" = true
seed=$1
root=$PWD
out=$root/build/profession-evidence
mkdir -p "$out"
cd "$(mktemp -d "$RUNNER_TEMP/uwu-professions.XXXXXXXX")"
mkdir mods
cp "$root"/build/libs/UwU-Dimension-*.jar mods/uwu.jar
cp "$root"/build/village-profession-check/profession-probe.jar mods/probe.jar
fetch() {
    curl --retry 3 --retry-all-errors -fsSL --max-time 150 "$1" -o "$2"
    printf '%s  %s\n' "$3" "$2" | sha256sum -c
}
fetch https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.249/neoforge-21.1.249-installer.jar installer.jar d88b448eab73cd65bdf1720844a4828262de30a15fc71bd04dd81acc61c5399a
fetch https://cdn.modrinth.com/data/EltpO5cN/versions/ST5FHZnD/lootr-neoforge-1.21.1-1.11.38.125.jar mods/lootr.jar 091878598fa5cb51cd304bf9324afe46420d328b6f77a9ba38acc15e26945e9e
fetch https://cdn.modrinth.com/data/ulloLmqG/versions/Q29JlZfU/another_furniture-neoforge-4.0.2.jar mods/furniture.jar c61a66c73bcc5147d9983dc72c485493a612d2dbc078fcb86e2024155e34b7dd
fetch 'https://mediafilez.forgecdn.net/files/7675/823/Spice%20of%20Life%20Onion_NEOFORGE_v1.5.6_mc1.21.1.jar' mods/onion.jar 11605cfec2ac2e6385fc119d01bb6633b8a9ce8fdf0614cd272254630d56c0a7
fetch 'https://mediafilez.forgecdn.net/files/8975/261/CreativeCore_NEOFORGE_v2.13.49_mc1.21.1.jar' mods/creativecore.jar 39506208ad0c7efb2c9ac8128766425faebf50c408ec869d5f5043c0742c231b
timeout --kill-after=15s 6m java -Xmx1G -jar installer.jar --installServer
printf 'eula=true\n' >eula.txt
printf 'server-ip=127.0.0.1\nonline-mode=false\nlevel-type=minecraft:flat\nlevel-seed=%s\ndifficulty=peaceful\nspawn-protection=0\nview-distance=3\nsimulation-distance=3\nmax-tick-time=360000\n' "$seed" >server.properties
sha=$(sha256sum mods/uwu.jar | cut -d' ' -f1)
timeout --kill-after=20s 25m java -Xms512m -Xmx4G -XX:+ExitOnOutOfMemoryError \
    "-Duwu.profession.out=$out" "-Duwu.check.candidateJar=$PWD/mods/uwu.jar" "-Duwu.check.candidateSha256=$sha" \
    -Duwu.check.role=village @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee "$out/native.log"
grep -F "UWU PROFESSION AND ESCORT PASS $seed" "$out/native.log"
