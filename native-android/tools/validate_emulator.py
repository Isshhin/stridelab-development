#!/usr/bin/env python3
"""Private CI orchestration. Requires a disposable emulator; never targets a physical device."""
import os
import pathlib
import subprocess
import sys

ROOT=pathlib.Path(__file__).resolve().parents[2]
REPORTS=ROOT/'native-android/app/build/reports'
ADB=['adb']

def run(arguments,cwd=ROOT,capture=False,timeout=1200):
    result=subprocess.run(arguments,cwd=cwd,check=True,text=True,stdout=subprocess.PIPE if capture else None,stderr=subprocess.STDOUT if capture else None,timeout=timeout)
    return result.stdout or ''

def instrument(class_name,arguments,output):
    text=run(ADB+['shell','am','instrument','-w','-e','class',class_name]+arguments+['io.stridelab.privatealpha.test/androidx.test.runner.AndroidJUnitRunner'],capture=True,timeout=180)
    output.parent.mkdir(parents=True,exist_ok=True);output.write_text(text)
    print(text,flush=True)
    if 'OK (1 test)' not in text:raise RuntimeError('Instrumentation did not report a successful test: '+class_name)

def main():
    global ADB
    if os.environ.get('GITHUB_ACTIONS')!='true':raise RuntimeError('Only run on the disposable private CI emulator')
    devices=[line.split()[0] for line in run(['adb','devices'],capture=True).splitlines() if line.endswith('\tdevice')]
    if len(devices)!=1 or not devices[0].startswith('emulator-'):raise RuntimeError('Expected exactly one isolated emulator; refusing device operations')
    ADB=['adb','-s',devices[0]]
    REPORTS.mkdir(parents=True,exist_ok=True)
    try:
        run(ADB+['install','-r',str(ROOT/'android-app/android/app/build/outputs/apk/debug/app-debug.apk')])
        run(ADB+['install','-r',str(ROOT/'android-app/android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')])
        instrument('io.stridelab.privatealpha.LegacyUpgradeSeedTest',[],REPORTS/'legacy-seed.txt')
        run(['gradle','--no-daemon','--stacktrace',':app:connectedDebugAndroidTest','-Pandroid.testInstrumentationRunnerArguments.expectLegacyUpgrade=true'],cwd=ROOT/'native-android')
        run(['gradle','--no-daemon',':app:assembleDebugAndroidTest','-PstrideProcessProbe=true'],cwd=ROOT/'native-android')
        run(ADB+['install','-r',str(ROOT/'native-android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')])
        cls='io.stridelab.privatealpha.NativeProcessProbeTest'
        for phase in ['reachability','seed']:
            instrument(cls,['-e','processPhase',phase],REPORTS/f'process-probe/{phase}.txt')
        run(ADB+['shell','am','force-stop','io.stridelab.privatealpha'])
        run(ADB+['shell','settings','put','system','font_scale','1.3'])
        run(ADB+['shell','cmd','connectivity','airplane-mode','enable'])
        run(ADB+['shell','svc','wifi','disable']);run(ADB+['shell','svc','data','disable'])
        instrument(cls,['-e','processPhase','offline'],REPORTS/'process-probe/offline.txt')
        run(ADB+['shell','am','force-stop','io.stridelab.privatealpha'])
        run(ADB+['shell','cmd','connectivity','airplane-mode','disable'])
        run(ADB+['shell','svc','wifi','enable']);run(ADB+['shell','svc','data','enable'])
        instrument(cls,['-e','processPhase','reconnected'],REPORTS/'process-probe/reconnected.txt')
    finally:
        # Collect before emulator-runner shutdown, including diagnostics when a test fails.
        try:(REPORTS/'emulator-logcat.txt').write_text(run(ADB+['logcat','-d','-t','4000'],capture=True,timeout=30))
        except subprocess.SubprocessError:pass
        for args in [['shell','settings','put','system','font_scale','1.0'],['shell','cmd','connectivity','airplane-mode','disable'],['shell','svc','wifi','enable'],['shell','svc','data','enable']]:
            try:run(ADB+args,timeout=30)
            except subprocess.SubprocessError:pass
        try:run(ADB+['pull','/sdcard/Android/data/io.stridelab.privatealpha/files/native-ui-evidence',str(REPORTS)],timeout=30)
        except subprocess.SubprocessError:pass
    required=['home','activities','settings','signin','offline-after-process-restart','activity-detail-offline']
    if any(not (REPORTS/f'native-ui-evidence/{name}.png').is_file() for name in required):raise RuntimeError('Required native UI screenshots were not exported')

if __name__=='__main__':main()
