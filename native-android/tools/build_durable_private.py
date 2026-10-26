"""Build a durable signed APK using injected secrets; never prints credentials."""
import base64
import os
import pathlib
import subprocess
import tempfile
from check_apk_update import inspect
from package_private_apk import package


def build(root, version, commit, run_url, expected):
    if int(version) < 11:
        raise ValueError('Signed versionCode must be at least 11 and newer than the installed build')
    required = ['STRIDELAB_KEYSTORE_BASE64','STRIDELAB_STORE_PASSWORD','STRIDELAB_KEY_ALIAS','STRIDELAB_KEY_PASSWORD']
    if any(not os.environ.get(name) for name in required):
        raise ValueError('Durable signing secrets are not configured; no signed build produced')
    if len(expected) != 64 or any(c not in '0123456789abcdef' for c in expected):
        raise ValueError('Expected public signer SHA-256 must be configured')
    sdk = pathlib.Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0'
    with tempfile.TemporaryDirectory(prefix='stride-signing-') as temporary:
        key = pathlib.Path(temporary)/'private.jks'
        key.write_bytes(base64.b64decode(os.environ['STRIDELAB_KEYSTORE_BASE64'],validate=True))
        key.chmod(0o600)
        env = dict(os.environ,STRIDELAB_KEYSTORE_FILE=str(key))
        # Passwords are environment-only. Suppress tool logs; signed artifact contains public metadata only.
        result = subprocess.run(['gradle','--no-daemon','-p',str(root/'native-android'),':app:assembleRelease',f'-PstrideVersionCode={int(version)}'],env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
        if result.returncode:
            raise RuntimeError('Durable signed build failed; secret-bearing build output was withheld')
        apk=root/'native-android/app/build/outputs/apk/release/app-release.apk'
        info=inspect(apk,str(sdk/'aapt'),str(sdk/'apksigner'))
        return package(apk,root/'native-android/app/build/durable-private-apk',info,commit,run_url,durable_signer=expected)

if __name__ == '__main__':
    import argparse
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version',required=True)
    args=parser.parse_args()
    build(pathlib.Path(__file__).resolve().parents[2],args.version,os.environ['GITHUB_SHA'],os.environ['GITHUB_SERVER_URL']+'/'+os.environ['GITHUB_REPOSITORY']+'/actions/runs/'+os.environ['GITHUB_RUN_ID'],os.environ.get('STRIDELAB_EXPECTED_SIGNER_SHA256','').lower().replace(':',''))
    print('Durable signed private artifact packaged. Installed-device update compatibility remains unverified.')
