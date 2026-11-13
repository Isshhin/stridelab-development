# P2-R public real-data validation bridge

Research status: first external measurement replay executed. Source admission, file access, schema coverage and completed policy evaluation are separate milestones.

| Track | Current evidence | Open test |
|---|---|---|
| [R0 admission](p2-r-dataset-admission-registry.md) | Seven purpose-specific decisions | Obtain original manifests for Groningen and 4TU |
| [R1 progression](p2-r1-groningen-replay.md) | Identified overlapping history and event-selection hazards from the published design | Verify file rights, reconstruct only auditable unique athlete-days |
| [R2 repeatability](p2-r2-4tu-repeatability.md) | Three distinct training designs cannot form a B0 easy-repeat pair | Inspect archive and quantify descriptive coverage |
| [R3 state](p2-r3-athlete-state-real-data.md) | First 32 Polar intervals for each of 13 participants; two inspected wrist headers | Align Polar intervals with reference ECG and contemporaneous phase information |
| [R4 implementation](p2-r-public-replay-harness.md) | Deterministic participant-specific RR replay and regression tests | Add a second admitted representation and executable source-manifest enforcement |
| R5 policy review | R3-A1 requires contextual evidence; no changes to P2-B/C prescriptions | Compare competing decisions at matched coverage once credible reference outcomes exist |

Private S1 tests acquisition, linkage and independently reconciled development data. Public observational data can expose measurement failures. Synthetic fixtures test software contracts. Prospective StrideLab evidence is necessary for coaching-effectiveness claims. Keep all four denominators separate. Session G remains frozen. Gate 2 is unsigned and P2-B remains protocol-only.