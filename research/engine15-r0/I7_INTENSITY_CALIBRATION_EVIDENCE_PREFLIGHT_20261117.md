Engine 1.5 I7 — calibration evidence intake preflight, 2026-11-17

Stacked on I6 draft PR #126 after the user reported all 20 selected I6 and earlier JVM classes passing in 12s.

Scope: SHADOW ONLY. I6 source-linked HR and pace consistency is not a verified physiological threshold. Existing AthleteHeartRateZoneSettings is for display, not coaching. Do not infer threshold intensity from HR zones, lap pace, titles, weekly mileage, or caller-assigned workout family.

Contract:
1. Rerun I6 from the same independent M4B, M5B, I3 source evidence and I5 envelope. Propagate upstream failure.
2. Require one claimed calibration record matching asserted athlete owner and workout family.
3. Permit only claimed LAB_TEST or STRUCTURED_FIELD_TEST as a candidate for EXTERNAL manual qualification; MANUAL_ENTRY and DEVICE_ESTIMATE are insufficient.
4. Require local date 0..90 days old (administrative freshness only), nonblank bounded subject source and protocol reference, and 64 lowercase hex SHA-256-format artifact fingerprint. Format does not authenticate artifact.
5. Require claimed threshold HR 100..230 and threshold pace 120..1200 sec/km as coarse numeric checks, not training targets; optional resting and max HR must be consistent with claimed threshold HR; if optional display LTHR setting conflicts, defer instead of overriding it.
6. Do NOT force I3 work-lap paces or HR to agree with calibration thresholds because their work-family intensity has not been independently established.
7. A consistent claim produces an ExternalQualificationRequired packet with original I6 unchanged and all authority flags false: ownerSourceAuthenticated, calibrationArtifactVerified, calibrationProtocolVerified, thresholdIntensityVerified, workDoseAuthorized, athleteResponseVerified, futureDistanceVerified, executable and authorizedForProduction.
8. Never update saved plan, imported activity, settings, M4B budget, Engine1/Engine15 production generator, Android UI, or live routing.

Prespecified synthetic tests: valid field and lab declarations return external-review packets only; wrong owner/family/origin, missing fields, malformed fingerprint, future/expired tests, bad HR/pace, impossible resting/max profile, and contradictory optional display LTHR defer. I6 evidence failures and M5B quality holds still defer. Determinism and false authority flags are mandatory.

Validation: new I7 selected JVM class and I6/I5/I4/I3/source binding/I2/I1/M3–M6/Q2/Q3/R0. No athlete-specific intensity or production prescription can be approved by I7.
