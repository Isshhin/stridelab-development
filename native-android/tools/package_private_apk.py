#!/usr/bin/env python3
"""Package a verified debug APK with public integrity metadata; never install or alter device data."""
import argparse
import hashlib
import json
import pathlib
import shutil
from check_apk_update import APP_ID, inspect

WARNING = '''PRIVATE TEST BUILD — SIGNING COMPATIBILITY IS NOT VERIFIED FOR YOUR PHONE.
This APK uses this CI job's ephemeral debug signing key, not a verified original installation key.
Do not uninstall StrideLab or clear its data if Android rejects the update.
Preserve the installed app and export its original backup before considering an update.
Compare the installed APK with tools/check_apk_update.py (read-only) first.
Matching CI legacy/native certificates prove only the disposable emulator path.
Future CI debug builds may have different certificates and may not update this APK.
Use a separate emulator for testing until phone compatibility is established.
No genuine-account or physical-device validation is claimed.
'''

def package(apk, output, info, commit, run_url):
    if info.package != APP_ID:
        raise ValueError('Unexpected application identity; refusing to package')
    output.mkdir(parents=True, exist_ok=False)
    destination = output / 'StrideLab-native-private-test.apk'
    shutil.copyfile(apk, destination)
    digest = hashlib.sha256(destination.read_bytes()).hexdigest()
    metadata = dict(applicationId=info.package, versionCode=info.version,
                    apkSha256=digest, signingCertificateSha256=sorted(info.certificates),
                    buildType='debug', signingKey='ephemeral CI debug key',
                    sourceCommit=commit, workflowRun=run_url,
                    installedDeviceCertificateCompared=False, physicalUpgradeValidated=False,
                    updateCompatibility='UNKNOWN — do not uninstall or clear the existing app')
    (output / 'apk-metadata.json').write_text(json.dumps(metadata, indent=2)+'\n')
    (output / 'SHA256SUMS').write_text(f'{digest}  {destination.name}\n')
    (output / 'READ-BEFORE-INSTALL.txt').write_text(WARNING)
    return metadata

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=pathlib.Path, required=True)
    parser.add_argument('--output', type=pathlib.Path, required=True)
    parser.add_argument('--commit', required=True)
    parser.add_argument('--run-url', required=True)
    parser.add_argument('--aapt', required=True)
    parser.add_argument('--apksigner', required=True)
    args = parser.parse_args()
    print(json.dumps(package(args.apk, args.output, inspect(args.apk, args.aapt, args.apksigner),
                             args.commit, args.run_url), indent=2))
