#!/usr/bin/env bash
set -euo pipefail
test "$GITHUB_ACTIONS" = true
export UWU_JAR=$(realpath "$(find build/libs -name 'UwU-Dimension-*.jar' -print -quit)")
export UWU_EXPECTED_JAR_SHA256=$(sha256sum "$UWU_JAR" | cut -d' ' -f1)
dir=build/escort-gallery
mkdir -p build/escort-deps/gallery "$dir/config"
curl --retry 3 --retry-all-errors -fsSL 'https://cdn.modrinth.com/data/EltpO5cN/versions/ST5FHZnD/lootr-neoforge-1.21.1-1.11.38.125.jar' -o build/escort-deps/gallery/lootr.jar
printf '%s  %s\n' 091878598fa5cb51cd304bf9324afe46420d328b6f77a9ba38acc15e26945e9e build/escort-deps/gallery/lootr.jar | sha256sum -c
printf 'version:3955\nonboardAccessibility:false\ntutorialStep:none\nrenderDistance:4\nsimulationDistance:4\nmaxFps:60\nenableVsync:false\npauseOnLostFocus:false\nguiScale:2\n' > "$dir/options.txt"
printf 'earlyWindowControl = false\n' > "$dir/config/fml.toml"
export LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null
timeout --kill-after=20s 12m xvfb-run -a -s '-screen 0 1280x720x24' bash gradlew --no-daemon --max-workers=2 -I village-profession-check/client.gradle runEscortGallery 2>&1 | tee build/escort-gallery.log
grep -F 'ESCORT PICK NATIVE GALLERY PASS' build/escort-gallery.log
test "$(find build/profession-evidence/gallery -name '*.png' | wc -l)" = 12
grep -F 'NATIVE HARVEST GALLERY PASS' build/escort-gallery.log
test "$(find build/profession-evidence/harvest -name '*.png' | wc -l)" = 7
