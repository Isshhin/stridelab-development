"""Compile pure Java/Kotlin sources and run actual JUnit tests in cloud Java 17.

This does not compile Android/Compose, install an APK, contact providers or mutate accounts.
Build dependencies download from Maven Central and are SHA-256 pinned. --offline forbids downloads.
Run from any working directory: python3 native-android/tools/run_library_jvm_checks.py
"""
import argparse
import hashlib
import json
import os
import pathlib
import re
import subprocess
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
TOOLS = ROOT/'native-android/tools'


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--cache', type=pathlib.Path, default=ROOT/'native-android/build/library-jvm-cache')
    parser.add_argument('--offline', action='store_true')
    args=parser.parse_args()
    cache=args.cache.resolve();cache.mkdir(parents=True,exist_ok=True)
    dependencies=json.loads((TOOLS/'fixtures/jvm-dependencies.json').read_text())
    jars=[]
    for artifact in dependencies:
        path=cache/artifact['file']
        if not path.exists():
            if args.offline:raise RuntimeError('Offline dependency unavailable: '+path.name)
            request=urllib.request.Request(artifact['url'],headers={'User-Agent':'StrideLab-library-validation/2.0'})
            with urllib.request.urlopen(request,timeout=120) as response:path.write_bytes(response.read())
        if hashlib.sha256(path.read_bytes()).hexdigest()!=artifact['sha256']:
            raise RuntimeError('Dependency checksum mismatch: '+path.name)
        jars.append(str(path))
    build=cache/'classes';build.mkdir(exist_ok=True)
    # Clear stale bytecode so removed sources cannot make a later run falsely pass.
    for path in build.rglob('*.class'):path.unlink()
    cp=os.pathsep.join(jars+[str(build)])
    def run(command):
        print('+ '+' '.join(str(c) for c in command),flush=True)
        subprocess.run(list(map(str,command)),cwd=ROOT,check=True)
    legacy=ROOT/'android-app/android/app/src'
    java=[legacy/'main/java/io/stridelab/privatealpha'/f for f in ['RecordPolicy.java','NativeSession.java','BackupCodec.java']]
    java_tests=sorted((legacy/'test/java/io/stridelab/privatealpha').glob('*.java'))
    run(['java','com.sun.tools.javac.Main','-source','17','-target','17','-cp',cp,'-d',build]+java+java_tests)
    native=ROOT/'native-android/app/src'
    # Deliberately named boundary: all pure JVM source and tests in the pinned native baseline.
    names=['ActivityDetail','ActivityIntelligence','ActivityRecord','BackendConfig','DashboardSummary',
           'Engine1','Engine1Engines','EngineContracts','ExerciseAlternatives','ExerciseLibraryV2','IntervalConnection',
           'LibraryShelf','MultiSessionEngine1','PlanBackup','PlanOperations','SessionTemplate','SupabaseGateway',
           'WellnessRecord','WorkoutEntryDraft']
    kotlin=[native/'main/java/io/stridelab/privatealpha'/f'{n}.kt' for n in names]
    # One existing pure helper lives in a Compose file. Compile its exact source, not a stub.
    # Full Android compilation remains a separate CI gate.
    builder=(native/'main/java/io/stridelab/privatealpha/PlanBuilder.kt').read_text()
    helper=re.search(r'^internal fun historyWeeklyKm\(.*?^}',builder,re.M|re.S)
    if not helper:raise RuntimeError('Pinned pure helper boundary changed')
    extracted=cache/'HistoryWeeklyKm.kt'
    extracted.write_text('package io.stridelab.privatealpha\nimport java.time.Instant\nimport java.time.ZoneId\n'+helper.group(0)+'\n')
    kotlin.append(extracted)
    tests=sorted((native/'test/java/io/stridelab/privatealpha').glob('*.kt'))
    fixtures=[TOOLS/'fixtures/Engine2ExerciseSelection-245349c.kt',TOOLS/'jvm/ExerciseLibrarySelectionCompatibilityTest.kt']
    run(['java','-cp',cp,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17','-classpath',cp,'-d',build]+kotlin+tests+fixtures)
    classes=['io.stridelab.privatealpha.'+p.stem for p in java_tests+tests]+['io.stridelab.privatealpha.ExerciseLibrarySelectionCompatibilityTest']
    run(['java','-cp',cp,'org.junit.runner.JUnitCore']+classes)


if __name__=='__main__':main()
