#!/usr/bin/env bash
# Keep the reviewed integration bootstrap and all committed build inputs intact.
set -euo pipefail
if [[ "${GITHUB_ACTIONS:-}" != true || "${GITHUB_REPOSITORY:-}" != WeepingAngeIs/z-UwU_dm ]]; then
    echo 'Run the weapon bootstrap only in the public repository on GitHub Actions.' >&2
    exit 1
fi

uwu_weapon_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
uwu_weapon_gradle_home=${GRADLE_USER_HOME:-${HOME:?}/.gradle}
# This also validates the public repository event and the exact wrapper checksum.
bash "$uwu_weapon_root/integration-check/bootstrap.sh"
# Replace only the init script that the preceding bootstrap just installed in
# this disposable runner. Repository source and dependency versions stay intact.
cp -- "$uwu_weapon_root/weapon-release/central-mirror.init.gradle" \
    "$uwu_weapon_gradle_home/init.d/uwu-official-central.init.gradle"

python3 - "$uwu_weapon_root" <<'PY'
from hashlib import sha256
import json
from pathlib import Path
import subprocess
import sys

target = Path(sys.argv[1]) / 'build/weapon-release'
downloads = target / 'central-mirror-checks'
downloads.mkdir(parents=True, exist_ok=True)
mirror = 'https://maven-central.storage-download.googleapis.com/maven2/'
# Compared byte-for-byte against repo.maven.apache.org and maven.neoforged.net
# before adoption. These are the exact 2.10.1 artifacts requested by idea-ext 1.2.
artifacts = (
    ('com/google/code/gson/gson/2.10.1/gson-2.10.1.jar',
     '4241c14a7727c34feea6507ec801318a3d4a90f070e4525681079fb94ee4c593'),
    ('com/google/code/gson/gson/2.10.1/gson-2.10.1.pom',
     'd2b115634f5c085db4b9c9ffc2658e89e231fdbfbe2242121a1cd95d4d948dd7'),
    ('com/google/code/gson/gson-parent/2.10.1/gson-parent-2.10.1.pom',
     '4248e0882426c615182385d6086c3ef3262e769957189e29306280b85482b833'),
)
records = []
for path, expected in artifacts:
    destination = downloads / Path(path).name
    url = mirror + path
    subprocess.run([
        'curl', '--fail', '--location', '--silent', '--show-error',
        '--proto', '=https', '--proto-redir', '=https',
        '--connect-timeout', '15', '--max-time', '45',
        '--retry', '2', '--retry-all-errors', '--retry-delay', '2',
        '--retry-max-time', '100', url, '--output', str(destination),
    ], check=True, timeout=150)
    data = destination.read_bytes()
    digest = sha256(data).hexdigest()
    if digest != expected:
        raise SystemExit('Maven mirror checksum mismatch: ' + path)
    records.append({'source_url': url, 'sha256': digest, 'bytes': len(data)})
report = {
    'mirror': mirror,
    'documentation': 'https://docs.cloud.google.com/docs/buildpacks/java',
    'dependency_versions_unchanged': True,
    'verified_artifacts': records,
}
(target / 'central-mirror-provenance.json').write_text(json.dumps(report, indent=2) + '\n')
print('UWU CI CENTRAL MIRROR PASS gson=2.10.1 artifacts=3 source=' + mirror)
PY
