"""Require every native view and the preserved integration suites on one JAR."""
from hashlib import sha256
import json
import os
from pathlib import Path
import re
import sys

import final_verify as reviewed

# Reuse the already reviewed regression requirements without weakening or editing
# the integrated Playtest 9 fixtures. Only their result collection is shared.
sys.path.insert(0, str(Path('integration-check').resolve()))
import provenance as integrated


def load(path):
    return json.loads(Path(path).read_text())


def file_record(path):
    raw = Path(path).read_bytes()
    reviewed.require(raw, 'Empty evidence: ' + str(path))
    return {'sha256': sha256(raw).hexdigest(), 'bytes': len(raw)}


def main():
    head = reviewed.environment()
    candidate = Path(os.environ['UWU_JAR']).resolve()
    digest = reviewed.identity(candidate)
    scope_path = Path('build/weapon-release/scope.json')
    scope = load(scope_path)
    reviewed.require(scope['source_sha'] == head and scope['candidate_jar_sha256'] == digest,
                     'Evidence does not match the reviewed frozen candidate')
    geometry_path = Path('build/weapon-release/geometry-source-proof.json')
    geometry = load(geometry_path)
    reviewed.require(geometry['baseline_source_sha'] == reviewed.BASE and len(geometry['models']) == 174
                     and geometry['returnbloom']['xy_uv_topology_materials_unchanged'] is True
                     and geometry['returnbloom']['inverse_transpose_normals'] is True,
                     'The reviewed source geometry proof is missing or incomplete')
    requirements = dict(integrated.REQUIRED_MARKERS)
    requirements.update({
        'build/weapon-check/host.log': ('UWU WEAPONS CLIENT PASS',
            'UWU WEAPONS NATIVE GALLERY PASS', 'UWU BLADE NATIVE MODEL PASS',
            'UWU BLADE HELD VIEWS PASS representatives=12 views=48',
            'UWU VESPER TRAVERSAL CERTIFICATION PASS variants=8 views=32 snowkiss=4 negative_controls=100 native_base_rejections=32',
            'CHAKRAM PROFILE PASS', 'UWU CHAKRAM HOST MATRIX PASS trials=64',
            'UWU CHAKRAM HOST COMBO MATRIX PASS trials=4 native_attacks=8'),
        'build/weapon-check/observer.log': ('UWU WEAPONS OBSERVER PASS',
            'UWU WEAPONS OBSERVER TCP PASS', 'UWU CHAKRAM OBSERVER MATRIX PASS trials=64',
            'UWU CHAKRAM OBSERVER COMBO MATRIX PASS trials=4 native_attacks=8'),
    })
    logs, records = {}, {}
    records[str(geometry_path)] = file_record(geometry_path)
    for name, markers in requirements.items():
        log = Path(name).read_text(errors='replace')
        reviewed.require(integrated.FAILURE.search(log) is None,
                         'A required suite recorded a failure: ' + name)
        for marker in markers:
            reviewed.require(marker in log, 'Missing required result in ' + name + ': ' + marker)
        logs[name] = log
        records[name] = dict(file_record(name), required_markers=list(markers),
                             result_lines=[line for line in log.splitlines()
                                           if ' PASS' in line or 'DUSK LEGACY SEEDED' in line])
    for name in integrated.CLIENT_LOGS:
        expected = f'UWU PACKAGED CANDIDATE PASS sha256={digest} path={candidate}'
        matches = [line for line in logs[name].splitlines() if 'UWU PACKAGED CANDIDATE PASS' in line]
        reviewed.require(len(matches) == 1 and expected in matches[0], 'Wrong regression client JAR: ' + name)
    for role in ('host', 'observer'):
        name = f'build/weapon-check/{role}.log'
        expected = f'UWU WEAPONS PACKAGED CANDIDATE PASS role={role} sha256={digest} path={candidate}'
        matches = [line for line in logs[name].splitlines() if 'UWU WEAPONS PACKAGED CANDIDATE PASS' in line]
        reviewed.require(len(matches) == 1 and expected in matches[0], 'Wrong weapon client JAR: ' + role)

    plans = []
    for order in ('forward', 'reverse'):
        marker = 'DUSK FRESH PLAN '
        rows = [line.split(marker, 1)[1] for line in logs[f'build/dusk-check/fresh-{order}.log'].splitlines() if marker in line]
        reviewed.require(len(rows) == 1, 'Missing native Dusk plan: ' + order)
        plans.append(rows[0])
    reviewed.require(plans[0] == plans[1], 'Dusk plans differ between chunk orders')
    anchors = []
    for order in ('shoulder', 'village'):
        marker = f'PETAL SHRINE SERVER PASS order={order} anchor='
        rows = [line.split(marker, 1)[1] for line in logs[f'build/shrine-check/{order}.log'].splitlines() if marker in line]
        reviewed.require(len(rows) == 1, 'Missing native shrine anchor: ' + order)
        anchors.append(rows[0])
    reviewed.require(anchors[0] == anchors[1], 'Shrine anchors differ between chunk orders')
    images = {str(path): integrated.image_record(path) for path in integrated.REQUIRED_IMAGES}

    base = Path('build/weapon-check/evidence')
    gallery = load(base / 'host/gallery-index.json')
    reviewed.require(gallery['weapons'] == 153 and gallery['controls'] == 15 and gallery['held_views'] == 169,
                     'The full native weapon inventory was not exercised')
    reviewed.require(gallery['angles'] == [0, 60, 90, 120], 'Native side angles changed')
    reviewed.require(gallery['hud_hidden'] is True, 'Vanilla HUD can contaminate gallery silhouette observations')
    entries = gallery['entries']
    reviewed.require(gallery['variant_views'] == len(entries), 'Variant gallery is incomplete')
    names = {entry['name'] for entry in entries}
    reviewed.require(len(names) == len(entries), 'Duplicate native gallery entry')
    silhouettes = (base / 'host/native-silhouettes.tsv').read_text().splitlines()
    observed = {(row.split('\t')[0], int(float(row.split('\t')[1]))) for row in silhouettes}
    reviewed.require(observed == {(name, angle) for name in names for angle in gallery['angles']},
                     'Native face/side framebuffer observations are incomplete')
    models = load(base / 'host/native-models.json')
    reviewed.require({row['name'] for row in models} == names, 'Native baked-model inventory is incomplete')
    relief_names = {row['name'] for row in models
                    if row['model_class'] == 'dev.uwu.dimension.client.WeaponBladeModels$ReliefModel'}
    blade_models = load(base / 'host/blade-native-models.json')
    reviewed.require(blade_models['baseline'] is False and len(relief_names) == 314
                     and {row['name'] for row in blade_models['models']} == relief_names,
                     'Native blade pass/transform proof omitted a resolved variant')
    for row in blade_models['models']:
        reviewed.require(row['transforms_checked'] == 18 and row['gui_returns_original'] is True
                         and row['original_pass_identity_preserved'] is True,
                         'Native blade display or original passes changed: ' + row['name'])
        before, after = row['native_bounds'], row['actual_bounds']
        reviewed.require(all(abs(before[i] - after[i]) < 1e-6 for i in (0, 1, 3, 4))
                         and after[2] < before[2] and after[5] > before[5],
                         'Native blade geometry did not preserve XY while adding depth: ' + row['name'])
    gui_pairs = load(base / 'host/blade-gui-identity.json')
    reviewed.require(len(gui_pairs) == len(relief_names) + 1
                     and relief_names <= {row['name'] for row in gui_pairs}, 'Incomplete native GUI preservation pairs')
    reviewed.require(all(row['different_pixels'] == 0 and row['occupied_pixels'] > 20 for row in gui_pairs),
                     'A final native GUI image changed or rendered empty')
    reviewed.require(any(row['foil'] and row['fixed_glint_calls'] > 0 for row in gui_pairs),
                     'Native enchantment glint preservation was not exercised')
    reviewed.require(len(list((base / 'host').glob('gallery-*.png'))) == (len(entries) + 4) // 5,
                     'Missing native gallery pages')
    reviewed.require(len(list((base / 'host').glob('held-side-*.png'))) == 169,
                     'Missing actual held side views')
    held_blades = load(base / 'host/blade-held-views.json')
    reviewed.require(len(held_blades) == 48
                     and {row['view']['index'] for row in held_blades} == set(range(48)),
                     'Missing changed blade views in both physical hands')
    for row in held_blades:
        view = row['view']
        context = ('FIRST_PERSON' if view['camera'] == 'first' else 'THIRD_PERSON') + '_' + view['arm'].upper() + '_HAND'
        reviewed.require(row['native_context'] == context and context in row['observed_contexts']
                         and (base / 'host' / row['screenshot']).is_file(), 'Wrong actual held blade view')

    traversal_path = base / 'host/vesper-traversal.json'
    traversal = load(traversal_path)
    approved_grip = [10.5 / 32, 1 - 22.5 / 32, .5]
    reviewed.require(traversal['baseline'] is False and traversal['variants'] == 8
                     and traversal['approved_grip_source'] == 'TraversalRenderPass.WRAP'
                     and traversal['approved_grip'] == approved_grip,
                     'The model-dependent traversal proof has the wrong source or approved grip')
    reviewed.require(traversal['negative_controls'] == 100
                     and traversal['negative_control_counts'] == {'off_hand': 36, 'gui': 32, 'foreign_model': 32}
                     and traversal['unselected_native_base_rejections'] == 32,
                     'Vesper/Snowkiss lost native identity, hand or GUI rejection controls')
    native_composite = 'net.neoforged.neoforge.client.model.CompositeModel$Baked'
    vesper_views = traversal['views']
    expected_vesper = {(element, credit, arm, view + '_' + arm + '_HAND')
                       for element in range(4) for credit in (False, True)
                       for arm in ('LEFT', 'RIGHT') for view in ('FIRST_PERSON', 'THIRD_PERSON')}
    reviewed.require(len(vesper_views) == 32 and {
        (row['element'], row['flower_credit'], row['arm'], row['context']) for row in vesper_views
    } == expected_vesper, 'Vesper certification omitted an element, flower-credit state, view or arm')
    for row in vesper_views:
        element = row['element']
        model_id = 'vesper_scythe_credit_' + str(element) if row['flower_credit'] else (
            'vesper_scythe' if element == 0 else 'vesper_scythe_' + str(element))
        reviewed.require(row['selected_model_class'] == 'dev.uwu.dimension.client.WeaponBladeModels$ReliefModel'
                         and row['native_model_class'] == native_composite
                         and row['certified_model_id'] == 'uwudimension:item/' + model_id
                         and row['tip'] == [.5 / 32, 1 - 6.5 / 32, .5]
                         and row['grip'] == approved_grip,
                         'Vesper selected model, certified silhouette tip or approved grip changed')
    snowkiss_views = traversal['snowkiss_controls']
    reviewed.require(len(snowkiss_views) == 4 and {(row['arm'], row['context']) for row in snowkiss_views}
                     == {(arm, view + '_' + arm + '_HAND') for arm in ('LEFT', 'RIGHT')
                         for view in ('FIRST_PERSON', 'THIRD_PERSON')},
                     'Snowkiss certification omitted a view or physical arm')
    reviewed.require(all(row['selected_model_class'] == native_composite
                         and row['tip'] == [26.5 / 32, 1 - 20.5 / 32, .5] for row in snowkiss_views),
                     'Snowkiss no longer uses its unchanged native model and certified tip')
    records[str(traversal_path)] = file_record(traversal_path)

    trials = load(base / 'host/trial-index.json')
    reviewed.require(len(trials) == 64 and {t['index'] for t in trials} == set(range(64)),
                     'Incomplete native chakram trial matrix')
    combo_trials = load(base / 'host/combo-trial-index.json')
    reviewed.require(len(combo_trials) == 4 and {t['index'] for t in combo_trials} == set(range(4))
                     and {(t['item'], t['camera'], t['arm']) for t in combo_trials}
                         == {(item, camera, 'right') for item in ('counter_iron_twinblade', 'counter_iron_katana')
                             for camera in ('first', 'third')},
                     'Incomplete native second-attack combo matrix')
    network = load(base / 'observer/network-identity.json')
    reviewed.require(network['memory_connection'] is False and network['owner'] != network['observer']
                     and '25591' in network['remote'], 'Observer did not use a separate native TCP identity')
    profile = base / 'host/chakram-profiles.json'
    profile_result = load(profile)
    reviewed.require(profile_result['baseline'] is False and profile_result['roster_chakrams'] == 20
                     and profile_result['native_profiles'] == 15 and profile_result['vanilla_profiles_preserved'] == 5
                     and profile_result['unconditional_throw_clips'] > 0
                     and profile_result['unconditional_hand_only_clips'] == 0
                     and profile_result['conditional_controls'] >= 2, 'Native profile preservation evidence is incomplete')
    records[str(profile)] = file_record(profile)
    motion_by_role = {}
    for role in ('host', 'observer'):
        identity = load(base / role / 'identity.json')
        reviewed.require(identity['sha256'] == digest and identity['baseline'] is False and identity['role'] == role,
                         'Native role did not use the final candidate: ' + role)
        options = load(base / role / 'client-options.json')
        reviewed.require(options['fov_degrees'] == 70 and options['simulation_distance'] == 5,
                         'Unexpected native test camera settings: ' + role)
        results = load(base / role / 'motion-results.json')
        motion_by_role[role] = {row['trial']['index']: row for row in results}
        reviewed.require(len(results) == 64 and {r['trial']['index'] for r in results} == set(range(64)),
                         'Incomplete native motion results: ' + role)
        for result in results:
            trial = result['trial']
            reviewed.require(result['role'] == role and trial == trials[trial['index']], 'Mismatched role/trial')
            reviewed.require(result['samples'] >= 5 and len(result['captures']) >= 5, 'Insufficient rendered motion samples')
            view = 'FIRST_PERSON' if role == 'host' and trial['camera'] == 'first' else 'THIRD_PERSON'
            context = view + '_' + trial['arm'].upper() + '_HAND'
            reviewed.require(context in result['native_contexts'], 'Missing native held view: ' + context)
            if trial['physical']:
                reviewed.require(result['physical_seen'] and result['physical_return_seen']
                    and result['physical_outbound_distance'] > 2 and len(result['physical_entity_uuids']) == 1
                    and result['returned_stack'] and result['returned_count'] == 1,
                    'Physical chakram outbound/return failed: ' + str(trial))
            elif result['native_melee_profile']:
                reviewed.require(result['animation_events'] > 0 and any('chakram' in clip for clip in result['clips'])
                    and result['maximum_draw_displacement'] > 2.0 and result['final_draw_displacement'] < .45,
                    'Native animation did not leave and return to the grip: ' + str(trial))
            else:
                reviewed.require(trial['item'].endswith('_sai') and result['vanilla_swing_seen'],
                                 'The native no-profile melee control changed')
        combos = load(base / role / 'combo-results.json')
        reviewed.require(len(combos) == 4 and {r['trial']['index'] for r in combos} == set(range(4)),
                         'Incomplete native second-attack results: ' + role)
        for result in combos:
            trial = result['trial']
            reviewed.require(result['role'] == role and trial == combo_trials[trial['index']],
                             'Mismatched second-attack role/trial')
            reviewed.require(result['animation_events'] == 2 and result['clips'] == [
                'simplyswords:one_handed_chakram_throw_left', 'simplyswords:one_handed_throw_chakram_right'],
                'Native combo did not dispatch both preserved left/right throw clips: ' + str(trial))
            attacks = result['attack_results']
            reviewed.require(len(attacks) == 2 and [r['ordinal'] for r in attacks] == [1, 2],
                             'A native combo attack was omitted or reordered')
            view = 'FIRST_PERSON' if role == 'host' and trial['camera'] == 'first' else 'THIRD_PERSON'
            context = view + '_RIGHT_HAND'
            for attack in attacks:
                reviewed.require(attack['samples'] >= 5 and len(attack['captures']) >= 3
                                 and attack['maximum_draw_displacement'] > 2.0
                                 and attack['final_draw_displacement'] < .45
                                 and context in attack['native_contexts'],
                                 'A combo attack did not visibly leave and return to the native grip in the required view')
            reviewed.require(result['final_draw_displacement'] < .45,
                             'The final combo attack did not return to the native grip')
        for path in (base / role).glob('*'):
            if path.suffix == '.png':
                images[str(path)] = integrated.image_record(path)
            elif path.is_file():
                records[str(path)] = file_record(path)

    physical_trials = [trial for trial in trials if trial['physical']]
    reviewed.require(len(physical_trials) == 16, 'Physical throw coverage changed')
    for trial in physical_trials:
        host = motion_by_role['host'][trial['index']]
        observer = motion_by_role['observer'][trial['index']]
        reviewed.require(host['physical_entity_uuids'] == observer['physical_entity_uuids']
                         and host['returned_damage'] == 1,
                         'Clients disagree on the native projectile or its baseline durability: ' + str(trial))

    report = {'source_sha': head, 'baseline_source_sha': reviewed.BASE, 'version': reviewed.VERSION,
              'jar_filename': candidate.name, 'jar_sha256': digest,
              'baseline_jar_sha256': reviewed.BASE_JAR_SHA,
              'scope_record_sha256': file_record(scope_path)['sha256'],
              'workflow_run_url': f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}",
              'validation_environment': 'Public GitHub-hosted Actions, Java 21, NeoForge 21.1.249',
              'network_observer': 'Separate Minecraft JVM with a native TCP RemotePlayer',
              'matching_physical_projectile_trials': len(physical_trials),
              'weapons': 153, 'tool_and_shield_controls': 15, 'held_side_views': 169,
              'native_variant_views': len(entries), 'chakram_trials_per_client': 64,
              'native_combo_trials_per_client': len(combo_trials), 'native_combo_attacks_per_client': 8,
              'native_blade_variants_verified': len(relief_names), 'identical_gui_pairs': len(gui_pairs),
              'changed_blade_views_both_physical_hands': len(held_blades),
              'vesper_certified_variants': 8, 'vesper_certified_views': len(vesper_views),
              'snowkiss_certified_views': len(snowkiss_views), 'traversal_rejection_controls': 132,
              'native_profile_chakrams': 15, 'preserved_native_vanilla_melee_controls': 5,
              'evidence': records, 'actual_in_game_images': images,
              'fixture_scope': 'Fresh disposable hosted worlds only; no user world or live server accessed'}
    target = Path('build/weapon-release')
    (target / 'provenance.json').write_text(json.dumps(report, indent=2) + '\n')
    (target / 'weapons.patch').write_bytes(reviewed.git('diff', '--binary', reviewed.BASE, 'HEAD'))
    with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as summary:
        summary.write(f'## Verified weapon candidate\n\nSource: `{head}`\n\nJAR SHA-256: `{digest}`\n\n')
        summary.write(f'153 weapons, 15 controls, {len(entries)} native variants, 169 held side views.\n\n')
        summary.write('64 native chakram trials observed independently by owner and TCP observer.\n\n')
        summary.write('Cave berry/vanilla controls, Cupid/book, Dusk fresh/legacy, shrine and waterfall suites passed on the same frozen JAR.\n')
    print('UWU WEAPONS PROVENANCE PASS native views, both real clients, preserved integration suites')
    print('UWU WEAPONS VERIFIED CANDIDATE SHA256 ' + digest)


if __name__ == '__main__':
    main()
