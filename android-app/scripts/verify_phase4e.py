#!/usr/bin/env python3
"""Offline, reproducible verification of Phase 4E against accepted Phase 4D."""
import hashlib, json, pathlib, re, sys, xml.etree.ElementTree as ET, zipfile
R=pathlib.Path(__file__).resolve().parent.parent
APP=R/'android/app/src/main'
BASE=APP/'assets/www/index.html'
ORIGINAL='915c9c37e3816291bfc9ab3ead1f37b400711886056a43fd1af101b602cdaa6b'
HTML_SHA='d43ae2539d2c4d0da2e289ab6fec270366d7351e5d973d6495cc57d7627e4466'
BASELINE='cffaad8e9856d988f864cb09143a63aeac573b453aa0978abcded3dfb054bd9d'
ENGINE='3ae9226a19718925d5a0c59771d13ed85860b4f4cd694490f0cbf668cfb3a3f2'
FONT='<link href="https://fonts.googleapis.com/css2?family=Bricolage+Grotesque:opsz,wght@12..96,500;12..96,800&display=swap" rel="stylesheet">'
REPLACEMENT='<!-- Android pre-alpha: network font disabled; local/system fonts are used offline. -->'

def hashb(b):return hashlib.sha256(b).hexdigest()
def verify(upstream):
    if not upstream.is_file():raise RuntimeError('Accepted Phase 4D ZIP required for provenance validation')
    if hashb(upstream.read_bytes())!=ORIGINAL:raise RuntimeError('Upstream ZIP is not accepted Phase 4D final')
    with zipfile.ZipFile(upstream) as z:
        source=z.read('StrideLab-WS5/stridelab-afterglow-phase4d2-wip.html')
        if hashb(source)!=HTML_SHA:raise RuntimeError('Source HTML hash mismatch')
        android=BASE.read_bytes()
        if android.count(REPLACEMENT.encode())!=1:raise RuntimeError('Offline font replacement missing')
        recovered=android.replace(REPLACEMENT.encode(),FONT.encode())
        if recovered!=source:raise RuntimeError('Unexpected Android HTML modification to accepted source')
        for n in (a for a in z.namelist() if a.startswith('StrideLab-WS5/assets/') and not a.endswith('/')):
            rel=n.removeprefix('StrideLab-WS5/assets/')
            if (APP/'assets/www/assets'/rel).read_bytes()!=z.read(n):raise RuntimeError('Modified licensed asset: '+rel)
        protected=z.read('StrideLab-WS5/baseline-4d1/stridelab-afterglow-phase4d1-final.html')
        if hashb(protected)!=BASELINE:raise RuntimeError('Phase 4D.1 baseline changed')
        import re
        matches=re.findall(rb'<script\s+id="eng"[^>]*>(.*?)</script>',source,re.DOTALL)
        if len(matches)!=1 or hashb(matches[0])!=ENGINE:raise RuntimeError('Protected engine hash mismatch')
    manifest=ET.parse(APP/'AndroidManifest.xml').getroot()
    app=manifest.find('application'); ns='{http://schemas.android.com/apk/res/android}'
    assert app.get(ns+'allowBackup')=='false','Cloud backup is not disabled'
    assert app.get(ns+'usesCleartextTraffic')=='false','Cleartext not denied'
    assert manifest.findall('uses-permission')==[],'Permission added: manual review required'
    java=(APP/'java/io/stridelab/privatealpha/MainActivity.java').read_text()
    codec=(APP/'java/io/stridelab/privatealpha/BackupCodec.java').read_text()
    assert 'addPathHandler("/assets/"' in java and 'setAllowFileAccess(false)' in java
    assert 'setDomStorageEnabled(true)' in java and 'setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW)' in java
    assert 'ACTION_CREATE_DOCUMENT' in java and 'ACTION_OPEN_DOCUMENT' in java
    assert 'android.permission.INTERNET' not in (APP/'AndroidManifest.xml').read_text()
    assert 'afterglow2' in codec and 'schemaVersion' in codec and 'MAX_BYTES' in codec
    assert 'io.stridelab.privatealpha' in (R/'android/app/build.gradle').read_text()
    assert not list(R.rglob('*.jks')) and not list(R.rglob('*.keystore'))
    result={'phase':'4E','upstream_sha256':ORIGINAL,'upstream_html_sha256':HTML_SHA,'offline_html_sha256':hashb(android),'asset_count':len(list((APP/'assets/www/assets').iterdir())),'protected_engine_sha256':ENGINE,'protected_baseline_sha256':BASELINE,'permissions':'none','offline_assertions':'passed','outcome':'pass'}
    print(json.dumps(result,indent=2));return result

if __name__=='__main__':
    p=pathlib.Path(sys.argv[1]) if len(sys.argv)>1 else R.parent/'StrideLab-Phase4D-Final-candidate.zip'
    try:verify(p)
    except Exception as e:print('ERROR:',e,file=sys.stderr);sys.exit(1)
