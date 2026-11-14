# Engine 1.5 volume skeleton: isolated research Batch 1

This implementation is NOT imported into Android, does not prescribe actual workouts and
does not establish athlete safety, physiological capacity or fitness outcomes.

## Usage

From the repository root:

    python -m unittest discover -s research/engine15_skeleton -p 'test_*.py' -v
    python -m research.engine15_skeleton.cli --cases research/engine15_skeleton/fixtures/cases.json --policies research/engine15_skeleton/candidate_policies.json --output reports/engine15-skeleton.json

The full report includes deterministic input/config hashes, confidence, recorded evidence,
a week-by-week workload projection, the exact limiting rule IDs, and explicit constraint
conflicts. Missing distance != zero; declared-only baselines remain low confidence.
A zero/unknown starting baseline cannot be progressed by multiplying a percentage.
Every scenario has an explicit synthetic/manual/real_opt_in provenance label. Real-opt-in
replay must explicitly opt in and real data is not bundled. Do not upload raw athlete
files to CI.

Values in candidate_policies.json are ARITHMETIC RESEARCH EXAMPLES ONLY; they are not
approved training thresholds. Recovery is consistently the fourth week (1-based), not
a fifth-week off-by-one. A later workload uses the last non-deload build as its base.
Target dates are arithmetic forecasts, not physiological guarantees.
An explicit requested minimum long run that conflicts with share/step constraints is
reported rather than silently implemented. This harness checks configured available
days but not a complete session schedule. Feedback signals are deliberately not
converted into adaptations. Illness/pain signals prompt review, not clearance.

The Gate B rubric was separately committed **before** these candidate policies:
docs/qa/engine15-gate-b-rubric-v1.md. The documented software tests are not Gate B
acceptance; a human must inspect exported full scenarios and sign off separately.
Evidence and provenance: docs/engine15-long-term-roadmap-v0.2-20261114.md.
