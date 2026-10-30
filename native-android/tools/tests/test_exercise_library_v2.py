import copy
import hashlib
import importlib.util
import json
import pathlib
import re
import unittest
from jsonschema.exceptions import ValidationError
from unittest.mock import patch

ROOT = pathlib.Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('library_validator', ROOT/'tools/validate_exercise_library.py')
v = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v)


class ExerciseLibraryV2Test(unittest.TestCase):
    def setUp(self):
        self.library = v.read(v.ASSETS/'exercise-library-v2.json')
        self.media = v.read(v.ASSETS/'exercise-media-v2.json')

    def reject(self, change):
        change(self.library)
        with self.assertRaises((ValueError, ValidationError)):
            v.validate(self.library, self.media)

    def test_offline_schema_graph_baseline_and_coverage(self):
        with patch('socket.socket', side_effect=AssertionError('Network forbidden')):
            self.assertEqual(v.read(v.FIXTURES/'library-v2-coverage.json'), v.validate())

    def test_duplicate_id_rejected(self):
        self.reject(lambda lib: lib['records'].append(copy.deepcopy(lib['records'][0])))

    def test_normalized_duplicate_title_rejected(self):
        self.reject(lambda lib: lib['records'][1].update(name=lib['records'][0]['name'].upper()+'!'))

    def test_missing_required_field_and_unknown_equipment_rejected(self):
        for mutate in [lambda r:r.pop('caveats'), lambda r:r.update(equipmentOptions=[['magic_machine']])]:
            library=copy.deepcopy(self.library);mutate(library['records'][0])
            with self.assertRaises(ValidationError):v.validate(library,self.media)

    def test_dangling_and_self_reference_rejected(self):
        for target in ['nonexistent', self.library['records'][0]['id']]:
            library=copy.deepcopy(self.library);library['records'][0]['alternatives']=[target]
            with self.assertRaises(ValueError):v.validate(library,self.media)

    def test_prerequisite_and_progression_cycles_rejected(self):
        for field in ['prerequisites','progressions']:
            library=copy.deepcopy(self.library);a,b=library['records'][:2]
            for r in library['records']:
                r[field]=[]
                if field=='progressions':r['regressions']=[]
            a[field]=[b['id']];b[field]=[a['id']]
            if field=='progressions':a['regressions']=[b['id']];b['regressions']=[a['id']]
            with self.assertRaisesRegex(ValueError,'cycle'):v.validate(library,self.media)

    def test_missing_reciprocal_regression_rejected(self):
        by={r['id']:r for r in self.library['records']};by['e1']['regressions']=[]
        with self.assertRaisesRegex(ValueError,'reciprocal'):v.validate(self.library,self.media)

    def test_guide_never_promoted_and_legacy_payload_preserved(self):
        by={r['id']:r for r in self.library['records']};by['core-tuck']['kind']='exercise'
        with self.assertRaisesRegex(ValueError,'guide status'):v.validate(self.library,self.media)
        catalog=v.read(v.ASSETS/'catalog.json');catalog['entries'][0]['tech']=['Changed']
        with self.assertRaisesRegex(ValueError,'Legacy catalog'):v.validate(media=self.media,catalog=catalog)

    def test_unknown_source_and_missing_media_rejected(self):
        self.library['records'][0]['provenance'][0]['sourceId']='invented'
        with self.assertRaisesRegex(ValueError,'provenance'):v.validate(self.library,self.media)
        self.media['assets'].pop()
        with self.assertRaisesRegex(ValueError,'coverage'):v.validate(media=self.media)

    def test_unverified_image_cannot_be_claimed_verified(self):
        self.media['assets'][0]['status']='verified'
        with self.assertRaisesRegex(ValueError,'Incomplete verified'):v.validate(media=self.media)

    def test_path_traversal_rejected(self):
        a=next(a for a in self.media['assets'] if a['bundledFile']);a['bundledFile']='native-library/../../user-data'
        with self.assertRaisesRegex(ValueError,'Unsafe'):v.validate(media=self.media)

    def test_bundled_image_hash_and_inherited_license_are_protected(self):
        for key, value, message in [('bundledSha256','0'*64,'hash mismatch'), ('license','Imagined permission','attribution changed')]:
            media=copy.deepcopy(self.media);asset=next(a for a in media['assets'] if a['bundledFile']);asset[key]=value
            with self.assertRaisesRegex(ValueError,message):v.validate(media=media)

    def test_licensed_candidates_remain_explicitly_unbundled_placeholders(self):
        candidates=[a for a in self.media['assets'] if a.get('licensedCandidates')]
        self.assertEqual(4,len(candidates))
        self.assertTrue(all(a['status']=='placeholder' for a in candidates))
        self.assertTrue(all(not c['packaged'] for a in candidates for c in a['licensedCandidates']))
        candidates[0]['licensedCandidates'][0]['packaged']=True
        with self.assertRaisesRegex(ValueError,'unpackaged placeholder'):v.validate(media=self.media)

    def test_licensed_candidates_remain_explicitly_unbundled_placeholders(self):
        candidates=[a for a in self.media['assets'] if a.get('licensedCandidates')]
        self.assertEqual(4,len(candidates))
        self.assertTrue(all(a['status']=='placeholder' for a in candidates))
        self.assertTrue(all(not c['packaged'] for a in candidates for c in a['licensedCandidates']))
        candidates[0]['licensedCandidates'][0]['packaged']=True
        with self.assertRaisesRegex(ValueError,'unpackaged placeholder'):v.validate(media=self.media)

    def test_deterministic_order_required(self):
        self.library['records'].reverse()
        with self.assertRaisesRegex(ValueError,'ordered'):v.validate(self.library,self.media)

    def test_latest_engine2_selector_identity_guide_and_equipment_contract(self):
        # Read-only fixture from PR #25 head; no Engine2 code is compiled into this branch.
        source=(v.FIXTURES/'Engine2ExerciseSelection-245349c.kt').read_text()
        by={r['id']:r for r in self.library['records']}
        ids=re.findall(r'Engine2ExerciseOption\("([^"]+)"',source)
        self.assertEqual(18,len(ids));self.assertEqual(len(ids),len(set(ids)))
        for segment in source.split('Engine2ExerciseOption(')[1:]:
            match=re.match(r'"([^"]+)"',segment)
            if not match:continue
            record=by[match[1]]
            if record['kind']=='guide':self.assertIn('executable = false',segment)
        # Retain explicit selector behavior and its order; v2 is not a replacement whitelist.
        self.assertIn('option.executable &&',source)
        self.assertIn('equipment.containsAll(option.needs)',source)
        self.assertIn('confirmedSkills.containsAll(option.confirmedPrerequisites)',source)
        self.assertIn('thenBy { it.catalogId }',source)
        self.assertIn(['band','anchor'],by['br']['equipmentOptions'])
        self.assertIn(['pullup_bar','band'],by['ap']['equipmentOptions'])
        self.assertEqual('guide',by['hand-wall']['kind'])

    def test_existing_engine_and_ui_sources_unchanged(self):
        fixture=v.read(v.FIXTURES/'baseline-protected-files.json')
        for relative,expected in fixture['sha256'].items():
            self.assertEqual(expected,hashlib.sha256((ROOT.parent/relative).read_bytes()).hexdigest(),relative)

    def test_duplicate_json_keys_rejected(self):
        import tempfile
        with tempfile.TemporaryDirectory() as folder:
            path=pathlib.Path(folder)/'bad.json';path.write_text('{"id":"a","id":"b"}')
            with self.assertRaisesRegex(ValueError,'Duplicate JSON key'):v.read(path)


if __name__=='__main__':unittest.main()
