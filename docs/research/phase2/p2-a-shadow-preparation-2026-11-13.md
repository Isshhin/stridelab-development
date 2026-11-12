# P2-A synthetic shadow preparation — 2026-11-13

This additive helper is isolated from the frozen Session G baseline and the B0/H1 comparison runner. It accepts invented observations only; it is not a calibrated athlete-state model, does not estimate readiness or coaching benefit, and emits no training action.

`research/validation/p2a_shadow.py` filters observations by `available_at <= decision_at`, validates timezone-aware ordering and unique IDs, rejects mixed visible units, and reports included IDs, excluded quality/value count, support, newest-input age and model version. With fewer than two valid history values it abstains without a descriptive median. It compares history-only with history-plus-check-in as separate views. A check-in is included only if reported and available at decision time; because its scale is not calibrated to history, that view explicitly abstains. Missing or delayed check-ins do not alter the history-only result.

Synthetic regressions cover delayed arrival despite earlier observation, check-in ablation, sparse/invalid history, duplicate identities and incompatible units. These prove only the code contract. They do not establish real linkage, meaningful uncertainty calibration, an appropriate sample minimum, predictive value, or effectiveness. P2-A remains shadow research pending authorized decision-time inputs and independent follow-up; P2-B repeatability remains required for any matched-efficiency feature.

Validation: Python 3.12.10 focused tests passed (4 tests); full local research test discovery passed (448 tests, one pre-existing skip); the synthetic contract runner passed all 58 registered checks (E003 18, E004 22, E005 18), with all 58 real-engine checks blocked. No human data, Engine 1.5, collector or target was used.
