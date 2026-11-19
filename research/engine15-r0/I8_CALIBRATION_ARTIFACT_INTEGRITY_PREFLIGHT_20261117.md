# I8 — Calibration Artifact Byte Integrity (Preflight)

Frozen 2026-11-17, stacked on I7 PR #127. User's selected 21-class I7/I6/I5/I4/I3/I2/I1/M3–M6/Q2/Q3/R0 Gradle run passed (28 seconds). I8 is SHADOW ONLY, no prescription authority.

## Gap
I7 checks whether a caller-provided SHA-256 string has the right format. It does not read artifact bytes, recompute a digest, or check that the actual artifact contents agree with claimed athlete/date/test family/threshold metadata. A format-valid hash string is not proof of a test.

## I8 Scope and boundaries
1. Re-run I7, which re-runs I6/I5/I4/I3/M4B/M5B/M6B, from original typed inputs. Forward every upstream deferral before inspecting artifact bytes.
2. Take caller-supplied raw calibration artifact ByteArray, bounded to 1..16,384 bytes. Strictly decode UTF-8 with malformed/unmappable input rejected. Inspect **one simple StrideLab internal calibration interchange schema**, not arbitrary lab PDFs, proprietary wearable FIT files or text reports.
3. Require exactly these canonical lines, fixed order, trailing LF, no CR/duplicate/extra fields: stridelab-calibration-record/v1, owner, family, measured_on, origin, source_reference, protocol_reference, threshold_hr_bpm, threshold_pace_seconds_per_km, resting_hr_bpm, maximum_hr_bpm. Nonempty owner/reference/protocol tokens are ASCII [A-Za-z0-9._:/-] (no delimiter/newline). Date ISO-8601; optional HR fields use '-'. Exact byte-for-byte serialization is required so no schema ambiguity is silently normalized.
4. Recompute SHA-256 of the **exact supplied bytes**, compare to I7 claimed hex fingerprint (constant-time byte compare); missing/mismatched digest defers. Even matching SHA-256 supplied by the same caller is NOT an independent provenance/authenticity proof.
5. Compare all structured record fields with I7 claimed metadata exactly, including owner, family, test date, claimed test origin, source/protocol references, HR and pace, resting/max HR. No unverified value may be substituted or used to recalibrate threshold.
6. Result is `ArtifactConsistentForExternalReview` with original I7 packet, calculated digest, schema version and exact field-agreement fact, but all safety/authority flags remain false: sourceOwnerAuthenticated, externallyAuthenticated, protocolVerified, thresholdIntensityVerified, doseAuthorized, athleteResponseVerified, executable, authorizedForProduction. Do not promote I7 `calibrationArtifactVerified` to true: the bytes are self-supplied and their origin is unverified.
7. Do not persist or return raw bytes. Do not access files, network, Android UI, source activity database, saved plan, production Engine1/Engine15 generator, or override any prior defer.

## Frozen tests
- Valid two-recording synthetic history + field/lab I7 packet + correctly SHA-matched canonical source bytes -> byte consistency packet only, every production flag false.
- Bad hash, noncanonical ordering/missing/extra fields, non-UTF8, owner/field/family/date/protocol/threshold conflicts and overly large payload defer. Tests for field mismatch must recompute digest over the modified bytes to distinguish digest mismatch from metadata conflict.
- Upstream I7 invalid origins, unreviewed M5B and I6 failed source data defer unchanged; repeated evaluation is deterministic.

## Validation
Run I8 focused tests and I7/I6/I5/I4/I3/source-binding/I2/I1/M3–M6/Q2/Q3/R0 selected JVM regressions in user Q3 checkout. Next genuine blocker: verify the actual issuer, test protocol, subject consent and source provenance; a matching self-provided digest and canonical record do not do that.
