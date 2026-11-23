#!/usr/bin/env python3
"""Read-only Android APK metadata gate. Never installs, uninstalls, clears or signs an APK."""
import argparse
import dataclasses
import json
import pathlib
import re
import subprocess
import tempfile

APP_ID = 'io.stridelab.privatealpha'

@dataclasses.dataclass(frozen=True)
class ApkInfo:
    package: str
    version: int
    certificates: frozenset[str]

def parse_apk(badging: str, signing: str) -> ApkInfo:
    package = re.search(r"package: name='([^']+)' versionCode='(\d+)'", badging)
    certificates = frozenset(value.lower().replace(':','') for value in re.findall(r'Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F:]+)', signing))
    if package is None or not certificates or any(len(value) != 64 for value in certificates):
        raise ValueError('Complete package/version and signer SHA-256 information is required')
    return ApkInfo(package.group(1), int(package.group(2)), certificates)

def assess(installed: ApkInfo, candidate: ApkInfo) -> dict:
    reasons = []
    if installed.package != APP_ID or candidate.package != APP_ID:
        reasons.append('Application identity does not match io.stridelab.privatealpha')
    if installed.certificates != candidate.certificates:
        reasons.append('Signing certificates differ; keep the installed app and obtain its original signing path')
    if candidate.version <= installed.version:
        reasons.append('Candidate versionCode must be higher; do not downgrade or clear data')
    return dict(metadataChecksPassed=not reasons, physicalUpgradeValidated=False,
                installedVersionCode=installed.version, candidateVersionCode=candidate.version,
                installedSignerSha256=sorted(installed.certificates), candidateSignerSha256=sorted(candidate.certificates),
                blockers=reasons, nextStep='Keep a verified legacy backup. Device update/persistence validation is still required; this tool does not install anything.')

def run_tool(arguments: list[str]) -> str:
    return subprocess.run(arguments, check=True, capture_output=True, text=True, timeout=45).stdout

def inspect(apk: pathlib.Path, aapt: str, apksigner: str) -> ApkInfo:
    return parse_apk(run_tool([aapt,'dump','badging',str(apk)]), run_tool([apksigner,'verify','--print-certs',str(apk)]))

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--candidate', type=pathlib.Path, required=True)
    parser.add_argument('--installed-apk', type=pathlib.Path, help='Use an existing APK copy; its correspondence to the phone remains unverified')
    parser.add_argument('--serial', help='Explicit adb device serial when reading the installed package')
    parser.add_argument('--adb', default='adb'); parser.add_argument('--aapt', default='aapt'); parser.add_argument('--apksigner', default='apksigner')
    args = parser.parse_args()
    try:
        candidate = inspect(args.candidate,args.aapt,args.apksigner)
        with tempfile.TemporaryDirectory(prefix='stridelab-apk-metadata-') as temporary:
            if args.installed_apk:
                installed_path = args.installed_apk; source = 'provided_apk_copy'
            else:
                adb = [args.adb] + (['-s',args.serial] if args.serial else [])
                paths = [line.removeprefix('package:').strip() for line in run_tool(adb + ['shell','pm','path',APP_ID]).splitlines() if line.startswith('package:')]
                base = next((path for path in paths if path.endswith('/base.apk')), paths[0] if len(paths) == 1 else None)
                if not base or not base.startswith('/data/app/'):
                    raise ValueError('Installed base APK could not be identified. No device data was changed')
                installed_path = pathlib.Path(temporary)/'installed-base.apk'
                run_tool(adb + ['pull',base,str(installed_path)]); source = 'device_read_only'
            report = assess(inspect(installed_path,args.aapt,args.apksigner),candidate)
            report['installedApkSource'] = source
            print(json.dumps(report,indent=2))
            return 0 if report['metadataChecksPassed'] else 2
    except (OSError,ValueError,subprocess.SubprocessError):
        print(json.dumps(dict(metadataChecksPassed=False,physicalUpgradeValidated=False,
                             blockers=['APK metadata could not be verified. Check Android build tools, device authorization and file paths. No install or data-clear operation was performed.'])))
        return 2

if __name__ == '__main__':
    raise SystemExit(main())
