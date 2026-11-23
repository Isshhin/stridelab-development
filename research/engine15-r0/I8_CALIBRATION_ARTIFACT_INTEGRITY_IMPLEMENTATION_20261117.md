# I8 — Calibration Artifact Byte Integrity Implementation

2026-11-17. Branch `feature/engine15-i8-calibration-artifact-integrity-20261117`, stacked on I7 PR #127. Scope frozen in I8_CALIBRATION_ARTIFACT_INTEGRITY_PREFLIGHT_20261117.md.

New Engine15I8CalibrationArtifactIntegrity.inspect recomputes I7's entire original independent M4B/M5B/M6B/I3/I4/I5/I6 chain before reviewing raw calibration artifact bytes. I7 deferrals propagate; I8 never allows a payload to bypass source/dose safety checks.

An I8 record must be at most 16,384 bytes, strict UTF-8 without BOM or CR, and exactly the ten fixed ordered `key=value` fields under `stridelab-calibration-record/v1`. I8 recomputes SHA-256 on the actual supplied bytes and compares it to I7's caller-claimed digest; fixed format and field values must match the I7 calibration claim, including test owner, family, origin, date, subject source reference, protocol reference, HR, pace, optional resting and maximum HR.

I8's raw bytes are never persisted or included in the returned packet. A positive ArtifactConsistentForExternalReview contains only a calculated SHA-256 digest, original I7 packet, schema marker, and explicit outstanding approval limitations. This is *byte consistency* only. A caller can forge a matching record and hash, so it does NOT authenticate a lab, issuer, physiological test, athlete identity or HR/pace calibration. I7's `calibrationArtifactVerified` stays false, and I8's ownerSourceAuthenticated, externallyAuthenticated, protocolVerified, thresholdIntensityVerified, doseAuthorized, athleteResponseVerified, executable and authorizedForProduction remain false.

This internal interchange format is not a parser for arbitrary lab PDFs or wearable FIT test files. Real-world source and protocol attestation requires a separate issuer-specific verified integration and explicit review authority. I8 does not alter Engine1/Engine15 live generation, Android, saved plans or activity data.

Ten focused synthetic I8 tests added, including exact bytes/hash agreement without permission; missing/oversized bytes; changed bytes with old hash; malformed UTF-8; CRLF or no final LF; missing/extra/reordered/duplicate fields; rehashed metadata conflict; ambiguous reference token; upstream I7/I6 source and dose holds; and deterministic original planning chain.

**Not yet locally compiled or executed.** Acceptance requires I8 + I7/I6/I5/I4/I3/source binding/I2/I1/M3–M6/Q2/Q3/R0 selected Gradle tests in the independent Q3 checkout. Passing is not a release-ready intensity prescription or Android admission.
