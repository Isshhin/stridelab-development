---
title: "Engine 2.0 — Adaptive Intelligence"
area: architecture
status: verified
last_verified_commit: "HEAD"
key_files:
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine20AdaptivePolicyRegistry.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine20RunningPhysiologyAdapter.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine20StrengthResponseAdapter.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine20FatigueAndDoseResponseEngine.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine20ShadowPromotionEvaluator.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine20AdaptivePlanEngine.kt"
tags: [stridelab, engine20, adaptive, bayesian, critical-speed, durability, kinetics, autonomy]
---

# Engine 2.0 — Adaptive Intelligence

Engine 2.0 (`ENGINE20_VERSION = "2.0.0"`, `ENGINE20_ADAPTIVE_POLICY`, enabled always via `FeatureFlags.USE_ENGINE20_ADAPTIVE_V1 = true`) delivers individualized, evidence-gated training adaptations across Running, Strength, Calisthenics, and Hybrid athletes—scaling an Olympic-coach model safely to beginners, watch-free runners, and high-volume hybrid athletes while keeping `PLAN_SCHEMA = 1`, `validateSaved(plan)`, `TrainingStore.java`, and `ManualDisciplineStore` untouched.

## Core Architecture
- **Adaptive Policy Registry (`Engine20AdaptivePolicyRegistry`):** Centralizes `POL-B01`–`POL-B06` (`MOD-B1..B4`, `MOD-C1`, `E2-01..E2-09`) thresholds, Huber damping (`kappa = 1.5`), Bayesian log-e1RM variance floors (`sigma_floor = 0.035`), fractional set multipliers (`0.5`), `MOD-C1` identifiability floors (`>= 56 d`, `>= 3` anchors), `E2-09` bounded autonomy ceilings (`<= 10%` downward trim, `-1` working set backoff, `>= 28 d` consistent history), and Track 8 §11 shadow-model promotion/demotion bounds.
- **Running Speed-Duration & Durability (`Engine20RunningPhysiologyAdapter`):**
  - `MOD-B1`: Exact scaled Householder/Givens QR factorization (`t_scaled = t / 600 s`) + Joseph $2\times 2$ covariance solving Critical Speed & $D'$ (`cond(H_scl) < 10`), rejecting mismatched surface/shoe, narrow trial spans, non-maximal efforts, and wide-$D'$ SE fits cleanly.
  - `MOD-B2` & `MOD-B3`: $C^0$-continuous endurance extension beyond `t_ref` and SI cardiac cost decoupling (`c_HR = f_HR / v_GAP`), strictly quarantining optical PPG, heat-confounded, fueling-deficit, and `< 60 min` runs.
- **Strength & Recoverable Volume Response (`Engine20StrengthResponseAdapter`):**
  - `E2-03`: Log-space Bayesian RIR-adjusted e1RM (`r + RIR <= 12`, `Halperin 2022` RIR dispersion + Huber outlier damping + temporal diffusion) predicting conservative lower-credible targets.
  - `MOD-B4`: `0.5` fractional indirect-set accounting (`Pelland 2025/2026`) paired with separate Direct/Indirect ledgers and soreness/stall-driven recoverable muscle volume bands (`LOW_TOLERANCE | STANDARD_TOLERANCE | HIGH_TOLERANCE`).
- **Probabilistic Fatigue Filter & Kinetics (`Engine20FatigueAndDoseResponseEngine`):**
  - `E2-05`: 3-state transparent Bayes filter over `{FRESH_ADAPTING, FUNCTIONAL_OVERLOAD, NON_FUNCTIONAL_FATIGUE_RISK}` consuming subjective trends (`Saw 2016`), execution stability, and `MOD-21` standardized `SD(ln rMSSD)` HRV (`>= 3–4 d`), requiring multi-day persistence (`>= 0.65`) for action, never producing an opaque `0–100` score, and never overriding `H1–H3` holds.
  - `MOD-C1`: Identifiability-gated Ridge/MAP Banister/Busso fitness-fatigue impulse kinetics (`Busso 2003`; `Hellard 2006`) falling back explicitly to Track 4 static tapers when sparse (`< 56 d` or `< 3` anchors).
- **Shadow Promotion Gates & Governance (`Engine20ShadowPromotionEvaluator`, `Engine20AdaptivePlanEngine`, `TrainingRepository`):**
  - Enforces Track 8 §11 closed-form Gaussian `CRPS`, `NLL`, 90% interval coverage, and optimistic safety-tail non-inferiority (`PROMOTION_NON_INFERIORITY_DELTA_SAFE = 0.03`), plus deterministic `MOD-B4` decision-usefulness gating.
  - Classifies each athlete transparently (`FOUNDATION_BUILDER`, `FAST_RESPONDER`, `FATIGUE_SENSITIVE_OR_OVERREACHING`, `ENDURANCE_DURABILITY_LIMITED`, `HYBRID_BALANCER`) with jargon-free plain-English briefings audited by `Engine19ExplanationAndNotificationPolicy`.
  - Defaults to `SUPERVISORY_APPROVAL` (emitting Engine 1.9 OCC `PlanRevisionCandidate` payloads) while supporting `OPT_IN_BOUNDED_AUTONOMY` strictly for downward recovery adjustments with immutable 1-tap `undoReceiptId` receipts, persisted locally under `m1-adaptive-profile-*`.

## Related Nodes
- [[00-StrideLab-Index]] · [[Engine-1.5]] · [[Engine-1.6-Strength]] · [[Engine-1.7-Calisthenics]] · [[Engine-1.8-Hybrid]] · [[Engine-1.9-Coaching]] · [[Hard-Guardrails]] · [[Storage-and-FIT-Contracts]]
