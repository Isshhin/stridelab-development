"""Offline release validation. No URL resolution, network access or user-data reads.

python3 native-android/tools/validate_exercise_library.py
Requires jsonschema==4.26.0 in the development/CI environment only.
"""
import collections
import hashlib
import json
import pathlib
import re
import unicodedata
from jsonschema import Draft202012Validator, FormatChecker

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets/native-library'
FIXTURES = ROOT / 'tools/fixtures'


def read(path):
    # Reject duplicate JSON keys rather than silently accepting the last value.
    def pairs(items):
        result = {}
        for k, v in items:
            if k in result:
                raise ValueError('Duplicate JSON key: ' + k)
            result[k] = v
        return result
    return json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=pairs)


def acyclic(records, field):
    by = {r['id']: r for r in records}
    visiting, done = set(), set()
    def visit(id_):
        if id_ in visiting:
            raise ValueError(f'{field} cycle at {id_}')
        if id_ in done:
            return
        visiting.add(id_)
        for target in by[id_][field]:
            visit(target)
        visiting.remove(id_)
        done.add(id_)
    for id_ in by:
        visit(id_)


def unique(values, description):
    if len(values) != len(set(values)):
        raise ValueError('Duplicate ' + description)


def validate(library=None, media=None, catalog=None):
    library = read(ASSETS / 'exercise-library-v2.json') if library is None else library
    media = read(ASSETS / 'exercise-media-v2.json') if media is None else media
    catalog = read(ASSETS / 'catalog.json') if catalog is None else catalog
    schema = read(ASSETS / 'exercise-library-v2.schema.json')
    Draft202012Validator.check_schema(schema)
    Draft202012Validator(schema, format_checker=FormatChecker()).validate(library)
    baseline = read(FIXTURES / 'exercise-library-v1-baseline.json')
    if catalog['schema'] != 1 or catalog['entries'] != baseline['entries']:
        raise ValueError('Legacy catalog records or ordering changed')
    digest = hashlib.sha256((ASSETS / 'catalog.json').read_bytes()).hexdigest()
    if digest != baseline['catalogSha256'] or digest != library['baselineCatalogSha256']:
        raise ValueError('Legacy catalog bytes changed')
    records = library['records']
    ids = [r['id'] for r in records]
    unique(ids, 'exercise ID')
    if ids != sorted(ids):
        raise ValueError('Records must be ordered by stable ID')
    def normalized(name):
        return re.sub(r'[^a-z0-9]', '', unicodedata.normalize('NFKD', name).casefold())
    unique([normalized(r['name']) for r in records], 'normalized name')
    by = {r['id']: r for r in records}
    sources = [s['id'] for s in library['sources']]
    unique(sources, 'source ID')
    for old in baseline['entries']:
        if old['id'] not in by:
            raise ValueError('Missing baseline ID ' + old['id'])
        r = by[old['id']]
        if r['name'] != old['n'] or (r['kind'] == 'guide') != old.get('guide', False):
            raise ValueError('Baseline identity/guide status changed: ' + old['id'])
        if r['technique'] != old['tech'] or r['easier'] != old['reg'] or r['harder'] != old['prog']:
            raise ValueError('Inherited instructions changed: ' + old['id'])
    for r in records:
        if set(r['primaryMuscles']) & set(r['secondaryMuscles']):
            raise ValueError('Overlapping muscle roles: ' + r['id'])
        for p in r['provenance']:
            if p['sourceId'] not in sources:
                raise ValueError('Unknown provenance source ' + p['sourceId'])
        for field in ['prerequisites', 'regressions', 'progressions', 'alternatives']:
            for target in r[field]:
                if target not in by or target == r['id']:
                    raise ValueError(f'Invalid {field}: {r["id"]} -> {target}')
        for high in r['progressions']:
            if r['id'] not in by[high]['regressions']:
                raise ValueError('Missing reciprocal regression')
        for low in r['regressions']:
            if r['id'] not in by[low]['progressions']:
                raise ValueError('Missing reciprocal progression')
    for field in ['prerequisites', 'progressions', 'regressions']:
        acyclic(records, field)
    if media.get('schemaVersion') != 2:
        raise ValueError('Unsupported media manifest version')
    assets = media['assets']
    unique([a['id'] for a in assets], 'media ID')
    unique([a['exerciseId'] for a in assets], 'media exercise ID')
    if {a['exerciseId'] for a in assets} != set(ids):
        raise ValueError('Media coverage mismatch')
    mb = {a['id']: a for a in assets}
    for r in records:
        a = mb.get(r['mediaId'])
        if not a or a['exerciseId'] != r['id']:
            raise ValueError('Media reference mismatch')
        if a['status'] not in ['placeholder', 'inherited-unverified', 'verified']:
            raise ValueError('Unknown rights status')
        if not a['reason'] or not a['changes']:
            raise ValueError('Missing rights explanation')
        for url in a['verificationUrls'] + a['candidateUrls']:
            if not url.startswith('https://'):
                raise ValueError('Invalid verification URL')
        if a['status'] == 'placeholder':
            if a['bundledFile'] is not None or a['license'] is not None:
                raise ValueError('Placeholder must not imply asset or license')
        elif a['status'] == 'verified':
            review = a.get('rightsReview', {})
            if not all(a.get(k) for k in ['author', 'license', 'licenseUrl', 'verificationUrls', 'bundledFile', 'bundledSha256']) or not all(review.get(k) for k in ['reviewedBy', 'reviewedAt', 'scope', 'fitApproved']):
                raise ValueError('Incomplete verified asset attribution')
        if a['bundledFile']:
            path = pathlib.PurePosixPath(a['bundledFile'])
            if path.is_absolute() or '..' in path.parts or path.parts[0] != 'native-library':
                raise ValueError('Unsafe bundled media path')
            file = ASSETS.parent / path
            if a.get('bundledSha256') != hashlib.sha256(file.read_bytes()).hexdigest():
                raise ValueError('Bundled image hash mismatch')
            old = next((r.get('image') for r in baseline['entries'] if r['id'] == a['exerciseId']), None)
            if a['status'] == 'inherited-unverified' and (not old or
                    any(a[k] != old[legacy] for k, legacy in [('author','author'), ('license','license'), ('licenseUrl','licenseUrl'), ('bundledFile','nativeFile')])):
                raise ValueError('Inherited image attribution changed')
    return dict(total=len(records), preserved=len(baseline['entries']), added=len(records)-len(baseline['entries']),
                kind=dict(collections.Counter(r['kind'] for r in records)),
                categories=dict(collections.Counter(c for r in records for c in r['categories'])),
                media=dict(collections.Counter(a['status'] for a in assets)))


if __name__ == '__main__':
    print(json.dumps(validate(), indent=2))
