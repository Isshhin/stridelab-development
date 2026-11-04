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
    # Explicit pure-JVM source boundary. Keep additions synchronized with Kotlin's
    # actual dependencies; NEVER use placeholder implementations of production code.
    # FIT SDK files are built and tested separately by the full Android Gradle job.
    names=['ActivityDetail','ActivityImportMatcher','ActivityIntelligence','ActivityRecord',
           'BackendConfig','DashboardSummary','Engine1','Engine15','Engine15Feedback',
           'Engine1Engines','Engine15InterferenceScheduling','Engine15SessionLocks','Engine15Availability','Engine15SessionPresentation','EngineContracts','ExerciseAlternatives','ExerciseLibraryV2',
           'FitContainerReader','IntervalConnection','LibraryShelf','MultiSessionEngine1',
           'PlanBackup','PlanOperations','PlanScheduleValidation','SessionTemplate',
           'SupabaseGateway','WearableProviderContracts','WellnessRecord',
           'WorkoutEntryDraft','WorkoutLogValidation']
    kotlin=[native/'main/java/io/stridelab/privatealpha'/f'{n}.kt' for n in names]
    # One existing pure helper lives in a Compose file. Compile its exact source, not a stub.
    # Full Android compilation remains a separate CI gate.
    builder=(native/'main/java/io/stridelab/privatealpha/PlanBuilder.kt').read_text(encoding='utf-8')
    helper=re.search(r'^internal fun historyWeeklyKm\(.*?^}',builder,re.M|re.S)
    if not helper:raise RuntimeError('Pinned pure helper boundary changed')
    extracted=cache/'HistoryWeeklyKm.kt'
    extracted.write_text('package io.stridelab.privatealpha\nimport java.time.Instant\nimport java.time.ZoneId\n'+helper.group(0)+'\n')
    kotlin.append(extracted)
    all_tests=sorted((native/'test/java/io/stridelab/privatealpha').glob('*.kt'))
    # FIT SDK and Trackside Compose-bound tests run with the Android Gradle
    # classpath. They MUST run in :app:testDebugUnitTest (the independent CI job).
    # This pinned, SDK-free harness checks every other native JVM test as well as
    # the legacy Java/library compatibility suite, even when new tests appear.
    gradle_only={'FitActivityDecoderTest.kt','FitContainerReaderTest.kt','TracksideTrainingCalendarTest.kt','TracksideParityPlanStateTest.kt'}
    if not gradle_only.issubset({p.name for p in all_tests}):
        raise RuntimeError('FIT SDK test boundary changed; review both CI suites')
    tests=[p for p in all_tests if p.name not in gradle_only]
    if len(tests)<20:
        raise RuntimeError('Pure-JVM regression test coverage unexpectedly shrank')
    fixtures=[TOOLS/'fixtures/Engine2ExerciseSelection-245349c.kt',TOOLS/'jvm/ExerciseLibrarySelectionCompatibilityTest.kt']
    run(['java','-cp',cp,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17','-classpath',cp,'-d',build]+kotlin+tests+fixtures)
    classes=['io.stridelab.privatealpha.'+p.stem for p in java_tests+tests]+['io.stridelab.privatealpha.ExerciseLibrarySelectionCompatibilityTest']
    run(['java','-cp',cp,'org.junit.runner.JUnitCore']+classes)


if __name__=='__main__':main()
