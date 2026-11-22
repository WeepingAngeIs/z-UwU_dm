"""Verify a weapon-only candidate against the exact integrated Playtest 9 JAR.

Run only on the public hosted runner. The reviewed scope lists exact production
paths; every other committed file and packaged entry is preserved from 1f489c5.
"""
from hashlib import sha256
import json
import os
from pathlib import Path
import subprocess
import sys
from zipfile import ZipFile

BASE = '1f489c5308b686a30672ec19b671a64534eb28a4'
BASE_VERSION = '1.5.0-beta.13-combined-rc.2-playtest.9-integrated.1'
VERSION = BASE_VERSION + '-weapons.1'
BASE_JAR_SHA = '0e865bbe4cf695144b70889138d82ad36ed8288a019bcb0e6b8b4851a6f1a04f'
JAVA = 'src/main/java/'
RESOURCES = 'src/main/resources/'
META = RESOURCES + 'META-INF/neoforge.mods.toml'
WORKFLOW = '.github/workflows/uwu-combined-integration.yml'
HELPERS = ('dev/uwu/weaponcheck/', 'dev/uwu/lotusclientcheck/', 'dev/uwu/duskcheck/',
           'dev/uwu/shrinecheck/', 'dev/uwu/waterfallcheck/', 'weapon-check/',
           'weapon-audit/', 'weapon-release/', 'assets/uwu_lotus_client_check/')


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def git(*args):
    return subprocess.check_output(['git', *args])


def blob(ref, path):
    return git('show', f'{ref}:{path}')


def environment():
    require(os.environ.get('GITHUB_ACTIONS') == 'true', 'Only public GitHub Actions may execute validation')
    require(os.environ.get('GITHUB_REPOSITORY') == 'WeepingAngeIs/z-UwU_dm', 'Wrong repository')
    require(not sys.flags.optimize, 'Assertions must remain enabled')
    event = json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text())
    require(event.get('repository', {}).get('private') is False, 'The repository must remain public')
    head = git('rev-parse', 'HEAD').decode().strip()
    require(head == os.environ['GITHUB_SHA'], 'Wrong source commit')
    subprocess.run(['git', 'merge-base', '--is-ancestor', BASE, head], check=True)
    subprocess.run(['git', 'diff', '--exit-code', 'HEAD', '--'], check=True)
    require(not git('ls-files', '--others', '--exclude-standard', '--', 'src/main'),
            'Untracked production source is not allowed')
    return head


def identity(candidate):
    candidate = candidate.resolve()
    require(candidate == Path(os.environ['UWU_JAR']).resolve(), 'Wrong candidate path')
    digest = sha256(candidate.read_bytes()).hexdigest()
    require(digest == os.environ['UWU_EXPECTED_JAR_SHA256'], 'Candidate bytes changed')
    record = Path('build/weapon-release/candidate.sha256').read_text().split()
    require(len(record) == 2 and record[0] == digest and Path(record[1]).resolve() == candidate,
            'Candidate checksum record changed')
    return digest


def source_scope():
    head = environment()
    scope = json.loads(Path('weapon-release/scope.json').read_text())
    require(scope['baseline_source_sha'] == BASE and scope['version'] == VERSION, 'Wrong reviewed scope')
    production = set(scope['production_paths'])
    require(len(production) == len(scope['production_paths']), 'Duplicate reviewed production paths')
    require({'build.gradle', META} <= production, 'Both version files must be reviewed')
    for path in production:
        require(path == 'build.gradle' or path.startswith((JAVA, RESOURCES)), 'Invalid production path: ' + path)
        require(not path.endswith(('.png', '.mcmeta')), 'Original texture pixels and timing must remain unchanged')
        require(Path(path).is_file(), 'Missing reviewed production file: ' + path)
    changed = set(git('diff', '--name-only', BASE, 'HEAD').decode().splitlines())
    infrastructure = ('weapon-check/', 'weapon-audit/', 'weapon-release/')
    require(all(p in production or p == WORKFLOW or p.startswith(infrastructure) for p in changed),
            'Changes exceed the reviewed weapon-only scope: ' + repr(sorted(changed - production)))
    require(production <= changed, 'The reviewed list contains unchanged or missing production files')
    for path in ('build.gradle', META):
        before = blob(BASE, path)
        require(before.count(BASE_VERSION.encode()) == 1, 'Version anchor changed')
        require(Path(path).read_bytes() == before.replace(BASE_VERSION.encode(), VERSION.encode()),
                'Version file changed beyond its version: ' + path)
    adapter = JAVA + 'dev/uwu/dimension/WeaponAnimationAdapter.java'
    before = blob(BASE, adapter)
    old_guard = b'if(shape.equals("chakram")&&!two&&simply)'
    new_guard = b'if(shape.equals("chakram")&&simply)'
    require(before.count(old_guard) == 1 and Path(adapter).read_bytes() == before.replace(old_guard, new_guard),
            'The animation change exceeds removal of the chakram two-handed gate')
    landmarks = JAVA + 'dev/uwu/dimension/traversal/client/TraversalLandmarks.java'
    expected = blob(BASE, landmarks)
    landmark_edits = (
        (b'import dev.uwu.dimension.Arsenal;\r\n',
         b'import dev.uwu.dimension.Arsenal;\r\nimport dev.uwu.dimension.client.WeaponBladeModels;\r\n'),
        (b'|| context == null || stack == null || selected == null || selected.getClass() != CompositeModel.Baked.class) return null;',
         b'|| context == null || stack == null || selected == null) return null;'),
        (b'        if (actual != selected) return null;\r\n',
         b'        if (actual != selected) return null;\r\n'
         b'        // Our blade relief preserves the native layers and transforms. Certify those\r\n'
         b'        // original layers only after matching the actual selected wrapper identity.\r\n'
         b'        // Foreign wrappers and unknown native geometry still fail the same class guard.\r\n'
         b'        BakedModel nativeModel = WeaponBladeModels.landmarkModel(selected);\r\n'
         b'        if (nativeModel.getClass() != CompositeModel.Baked.class) return null;\r\n'),
        (b'                model.getQuads(null, null, RandomSource.create(42), ModelData.EMPTY, null)))).orElse(null);',
         b'                nativeModel.getQuads(null, null, RandomSource.create(42), ModelData.EMPTY, null)))).orElse(null);'),
    )
    for old, new in landmark_edits:
        require(expected.count(old) == 1, 'The reviewed traversal anchor changed')
        expected = expected.replace(old, new)
    require(Path(landmarks).read_bytes() == expected,
            'Traversal changed beyond recognition of the owned wrapper native layers')
    # The full integrated registrations, common/client mixins, caves, Dusk, bow,
    # storybook, damage, use, projectile and return logic remain covered by the
    # exact tree comparison above unless a precise path is explicitly reviewed.
    return head, production


def packaged(candidate, baseline, production):
    require(sha256(baseline.read_bytes()).hexdigest() == BASE_JAR_SHA, 'Wrong integrated baseline artifact')
    allowed = {p.removeprefix(RESOURCES) for p in production if p.startswith(RESOURCES)}
    stems = {p.removeprefix(JAVA).removesuffix('.java') for p in production if p.startswith(JAVA)}
    def java_allowed(name):
        return name.endswith('.class') and any(name == stem + '.class' or name.startswith(stem + '$') for stem in stems)
    with ZipFile(candidate) as current, ZipFile(baseline) as previous:
        names = {name for name in current.namelist() if not name.endswith('/')}
        old_names = {name for name in previous.namelist() if not name.endswith('/')}
        require(len(names) == len([n for n in current.namelist() if not n.endswith('/')]), 'Duplicate JAR entries')
        require(not any(name.startswith(HELPERS) for name in names), 'A test helper was packaged')
        preserved, changed = [], []
        for name in sorted(names | old_names):
            same = name in names and name in old_names and current.read(name) == previous.read(name)
            if same:
                preserved.append(name)
            else:
                require(name in allowed or java_allowed(name), 'Unapproved packaged change: ' + name)
                changed.append(name)
        for file in Path(RESOURCES).rglob('*'):
            if file.is_file():
                name = file.relative_to(RESOURCES).as_posix()
                require(name in names and current.read(name) == file.read_bytes(), 'Packaged resource mismatch: ' + name)
        classes = {p.relative_to('build/classes/java/main').as_posix(): p
                   for p in Path('build/classes/java/main').rglob('*.class')}
        require(classes, 'Main compilation output is missing')
        require({n for n in names if n.endswith('.class')} == set(classes), 'Packaged class inventory differs from main output')
        for name, file in classes.items():
            require(current.read(name) == file.read_bytes(), 'Packaged class bytes differ from main output: ' + name)
    return preserved, changed


def main():
    head, production = source_scope()
    candidate = Path(os.environ['UWU_JAR'])
    digest = identity(candidate)
    preserved, changed = packaged(candidate, Path('build/weapon-baseline.jar'), production)
    report = {'source_sha': head, 'baseline_source_sha': BASE, 'version': VERSION,
              'candidate_jar': str(candidate.resolve()), 'candidate_jar_sha256': digest,
              'baseline_jar_sha256': BASE_JAR_SHA, 'production_paths': sorted(production),
              'preserved_jar_entries': len(preserved), 'changed_jar_entries': changed,
              'texture_pixels_and_animation_metadata_unchanged': True, 'helper_mods_excluded': True}
    target = Path('build/weapon-release/scope.json')
    encoded = (json.dumps(report, indent=2) + '\n').encode()
    if target.exists():
        require(target.read_bytes() == encoded, 'Frozen source/packaged scope changed during validation')
    else:
        target.write_bytes(encoded)
    print('UWU WEAPONS SOURCE AND JAR PASS preserved=' + str(len(preserved)) + ' exact integrated entries; helpers excluded')


if __name__ == '__main__':
    main()
