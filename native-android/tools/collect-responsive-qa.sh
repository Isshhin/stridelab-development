#!/usr/bin/env bash
# Runs the Android responsive suite and preserves captured PNGs even on failure.
# The emulator-runner executes each YAML script line separately, so multi-line
# test/capture logic must live in this checked-in helper.
set -u
profile="${1:?Missing responsive viewport profile}"
out="native-android/app/build/qa-screenshots/$profile"
mkdir -p "$out"
test_status=0
gradle --project-dir native-android --no-daemon :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.stridelab.privatealpha.TracksideResponsiveQaTest \
  --console=plain || test_status=$?
count=0
for attempt in 1 2 3; do
  adb pull /sdcard/Pictures/StrideLabBatchB "$out" || true
  count=$(find "$out/StrideLabBatchB" -type f -name '*.png' 2>/dev/null | wc -l | tr -d '[:space:]')
  echo "Profile $profile captured $count PNGs (attempt $attempt)"
  if [ "$count" -ge 5 ]; then break; fi
  sleep 3
done
if [ "$test_status" -ne 0 ]; then
  echo "Instrumentation failed (exit $test_status). Retained $count PNGs for inspection." >&2
  exit "$test_status"
fi
if [ "$count" -lt 5 ]; then
  echo "Missing screenshot evidence for $profile: only $count captured" >&2
  exit 1
fi
echo "Responsive QA passed with $count screenshot files"
