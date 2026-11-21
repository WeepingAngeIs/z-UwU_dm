#!/usr/bin/env bash
# Build the reviewable Unyielding adapter only on the public hosted CI runner.
set -euo pipefail
if [[ "${GITHUB_ACTIONS:-}" != true || "${RUNNER_ENVIRONMENT:-}" != github-hosted || \
      "${GITHUB_REPOSITORY:-}" != WeepingAngeIs/z-UwU_dm ]]; then
    echo 'Run this companion build only in the public UwU repository on GitHub-hosted Actions.' >&2
    exit 1
fi

uwu_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
uwu_output=${1:-"$uwu_root/build/dusk-frequency-ci/companion"}
mkdir -p -- "$uwu_output"
uwu_output=$(cd -- "$uwu_output" && pwd)
exec > >(tee "$uwu_output/companion-build.log") 2>&1
uwu_source_sha=0b01111213c6d6aaf63a955a5c010bc1a63ee04c
uwu_source=$(mktemp -d "${RUNNER_TEMP:?}/uwu-unyielding-source.XXXXXXXX")
uwu_patch="$uwu_root/dusk-frequency-ci/companion/Server-Jars-config.patch"
uwu_gradle_home=${GRADLE_USER_HOME:-${HOME:?}/.gradle}
uwu_gradle_sha=bd71102213493060956ec229d946beee57158dbd89d0e62b91bca0fa2c5f3531
uwu_wrapper_sha=7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172
# Official checksums: https://gradle.org/release-checksums/#8.14.3
python3 - <<'PY'
import json
import os
from pathlib import Path

event = json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text())
repository = event.get('repository', {})
if repository.get('private') is not False or repository.get('full_name') != 'WeepingAngeIs/z-UwU_dm':
    raise SystemExit('The companion build requires the public UwU repository event.')
PY
uwu_java_version=$(java -XshowSettings:properties -version 2>&1 | awk '/java.specification.version =/ {print $3}')
if [[ "$uwu_java_version" != 21 ]]; then
    echo "Java 21 is required; found $uwu_java_version" >&2
    exit 1
fi

# A sparse, detached checkout avoids downloading unrelated release JARs or assets.
git init "$uwu_source"
git -C "$uwu_source" config core.autocrlf false
git -C "$uwu_source" remote add origin https://github.com/WeepingAngeIs/Server-Jars.git
git -C "$uwu_source" sparse-checkout init --no-cone
git -C "$uwu_source" sparse-checkout set '/unyielding-quests/' '/.github/scripts/fetch-build-dependencies.py'
git -C "$uwu_source" fetch --filter=blob:none --depth=1 origin "$uwu_source_sha"
git -C "$uwu_source" checkout --detach FETCH_HEAD
test "$(git -C "$uwu_source" rev-parse HEAD)" = "$uwu_source_sha"

python3 - "$uwu_source" "$uwu_root" "$uwu_patch" "$uwu_output" <<'PY'
import hashlib
import json
from pathlib import Path
import subprocess
import sys

source, root, patch, output = map(Path, sys.argv[1:])
paths = [
    'unyielding-quests/src/main/java/dev/unyielding/quests/settings/BalanceSettings.java',
    'unyielding-quests/src/main/java/dev/unyielding/quests/settings/ConfigEditor.java',
    'unyielding-quests/build.gradle',
    'unyielding-quests/gradle.properties',
    'unyielding-quests/gradle/wrapper/gradle-wrapper.properties',
    '.github/scripts/fetch-build-dependencies.py',
]
def git(where, argument):
    return subprocess.check_output(['git', '-C', str(where), 'rev-parse', argument], text=True).strip()
data = {
    'upstream_repository': 'https://github.com/WeepingAngeIs/Server-Jars',
    'upstream_commit': git(source, 'HEAD'),
    'upstream_tree': git(source, 'HEAD^{tree}'),
    'uwu_commit': git(root, 'HEAD'),
    'coordination_patch_sha256': hashlib.sha256(patch.read_bytes()).hexdigest(),
    'original_source_sha256': {path: hashlib.sha256((source / path).read_bytes()).hexdigest() for path in paths},
}
(output / 'companion-provenance.json').write_text(json.dumps(data, indent=2) + '\n')
PY
git -C "$uwu_source" apply --check "$uwu_patch"
git -C "$uwu_source" apply "$uwu_patch"
printf '%s  %s\n' "$uwu_wrapper_sha" "$uwu_source/unyielding-quests/gradle/wrapper/gradle-wrapper.jar" | sha256sum --check

# Like integration-check/bootstrap.sh, seed the unmodified wrapper cache from
# an official HTTPS endpoint and verify the distribution before executing it.
uwu_cache_dir=$(python3 - "$uwu_source" "$uwu_gradle_home" <<'PY'
import hashlib
from pathlib import Path
import sys

source, home = map(Path, sys.argv[1:])
properties = {}
for line in (source / 'unyielding-quests/gradle/wrapper/gradle-wrapper.properties').read_text().splitlines():
    if line.strip() and not line.lstrip().startswith(('#', '!')):
        key, value = line.split('=', 1)
        properties[key.strip()] = value.strip().replace(r'\:', ':')
url = 'https://services.gradle.org/distributions/gradle-8.14.3-bin.zip'
expected = {'distributionUrl': url, 'distributionBase': 'GRADLE_USER_HOME',
            'distributionPath': 'wrapper/dists', 'zipStoreBase': 'GRADLE_USER_HOME', 'zipStorePath': 'wrapper/dists'}
for key, value in expected.items():
    if properties.get(key) != value:
        raise SystemExit(f'Unexpected companion wrapper input {key}: {properties.get(key)!r}')
# Unsigned URL MD5 in base 36 is the wrapper's directory key, not a security hash.
number = int.from_bytes(hashlib.md5(url.encode(), usedforsecurity=False).digest(), 'big')
digits, key = '0123456789abcdefghijklmnopqrstuvwxyz', ''
while number:
    number, remainder = divmod(number, 36)
    key = digits[remainder] + key
print(home.resolve() / 'wrapper/dists/gradle-8.14.3-bin' / (key or '0'))
PY
)
mkdir -p -- "$uwu_cache_dir"
uwu_download=$(mktemp "$uwu_cache_dir/uwu-companion-gradle.XXXXXXXX")
trap 'rm -f -- "$uwu_download"' EXIT
if ! curl --fail --location --silent --show-error --proto '=https' --proto-redir '=https' \
        --connect-timeout 20 --max-time 180 --retry 4 --retry-all-errors --retry-delay 3 --retry-max-time 240 \
        https://downloads.gradle.org/distributions/gradle-8.14.3-bin.zip --output "$uwu_download"; then
    curl --fail --location --silent --show-error --proto '=https' --proto-redir '=https' \
        --connect-timeout 20 --max-time 180 --retry 4 --retry-all-errors --retry-delay 3 --retry-max-time 240 \
        https://services.gradle.org/distributions/gradle-8.14.3-bin.zip --output "$uwu_download"
fi
printf '%s  %s\n' "$uwu_gradle_sha" "$uwu_download" | sha256sum --check
uwu_zip="$uwu_cache_dir/gradle-8.14.3-bin.zip"
mv -- "$uwu_download" "$uwu_zip"
rm -f -- "$uwu_zip.ok"

(
    cd -- "$uwu_source"
    python3 .github/scripts/fetch-build-dependencies.py
    bash unyielding-quests/gradlew -p unyielding-quests --no-daemon --build-cache \
        --init-script "$uwu_root/integration-check/official-central.init.gradle" -Pneo_version=21.1.249 jar
)
cp -- "$uwu_source/unyielding-quests/build/libs/UnyieldingQuests-1.6.9.jar" "$uwu_output/UnyieldingQuests-1.6.9.jar"

python3 - "$uwu_source" "$uwu_output" "$uwu_gradle_sha" "$uwu_wrapper_sha" <<'PY'
import ast
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

source, output = map(Path, sys.argv[1:3])
distribution_sha, wrapper_sha = sys.argv[3:]
def digest(path, algorithm='sha256'):
    with path.open('rb') as handle:
        return hashlib.file_digest(handle, algorithm).hexdigest()
fetcher = source / '.github/scripts/fetch-build-dependencies.py'
tree = ast.parse(fetcher.read_text())
dependencies = next(ast.literal_eval(node.value) for node in tree.body
                    if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'DEPENDENCIES' for t in node.targets))
inputs = []
for name, url, algorithm, expected in dependencies:
    path = source / 'unyielding-quests/libs' / name
    actual = digest(path, algorithm)
    if actual != expected:
        raise SystemExit(f'Companion dependency changed after fetching: {name}')
    inputs.append({'filename': name, 'url': url, 'verification_algorithm': algorithm,
                   'published_checksum': expected, 'sha256': digest(path), 'bytes': path.stat().st_size})
data = json.loads((output / 'companion-provenance.json').read_text())
jar = output / 'UnyieldingQuests-1.6.9.jar'
data.update({
    'built_at_utc': datetime.now(timezone.utc).isoformat(),
    'github_run': f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}",
    'github_run_attempt': os.environ.get('GITHUB_RUN_ATTEMPT', '1'),
    'java': subprocess.check_output(['java', '--version'], text=True).strip(),
    'gradle_version': '8.14.3', 'gradle_distribution_sha256': distribution_sha, 'gradle_wrapper_sha256': wrapper_sha,
    'moddev_version': '2.0.148', 'neoforge_upstream_default': '21.1.255', 'neoforge_build_override': '21.1.249',
    'build_task': 'jar', 'module': 'unyieldingquests', 'module_version': '1.6.9',
    'compiled_source_sha256': {path: digest(source / path) for path in data['original_source_sha256']},
    'dependencies': inputs,
    'artifact': {'filename': jar.name, 'sha256': digest(jar), 'bytes': jar.stat().st_size},
    'scope': 'quests-only integration companion; no combined release assembled or published',
})
(output / 'companion-provenance.json').write_text(json.dumps(data, indent=2) + '\n')
(output / 'SHA256SUMS').write_text(''.join(f'{digest(output / name)}  {name}\n'
                                          for name in [jar.name, 'companion-provenance.json']))
print(f'UWU COMPANION BUILD PASS jar={jar.name} sha256={digest(jar)}')
PY
if [[ -n "${GITHUB_ENV:-}" ]]; then
    printf 'UWU_COMPANION_JAR=%s\n' "$uwu_output/UnyieldingQuests-1.6.9.jar" >> "$GITHUB_ENV"
fi
