# Runna marathon long-run guidance — evidence note (2026-11-16)

**Source:** Runna Support, Steph, *How Is My Long Run Distance Calculated in My Marathon Plan?*, dated 2026-08-31, accessed 2026-11-16. https://support.runna.com/en/articles/9144831-how-is-my-long-run-distance-calculated-in-my-marathon-plan

**Evidence status:** Vendor-documented guidance only. NOT an independently observed generated plan, verified implementation rule, scientific prescription, or reconstructed proprietary algorithm. This note is research-only; no production changes authorized.

## Details absent from the canonical 2026-11-02 research document

- Runna states marathon long runs increase **10–15% week over week**, with a **deload every 4–5 weeks**. The article does not specify rounding, exception handling, deload magnitude, whether this is a hard cap, or whether all plans follow it.
- Plans longer than **14 weeks** are said to allow more opportunities for longer runs; plans shorter than **13 weeks** fewer. Exactly 13–14 weeks is unspecified.
- Runna describes **32 km (~20 miles)** once, ideally twice, as a marathon long-run target, with experienced runners sometimes prescribed longer. This is not a universal cap or guaranteed plan output.

## Reproducible illustrative arithmetic (NOT Runna output)

For a hypothetical 20.0-km long run, a 10–15% increase gives 20.0×1.10 = **22.0 km** to 20.0×1.15 = **23.0 km**. Three consecutive hypothetical 10% builds yield 20×1.10³ = **26.62 km**; three 15% builds yield 20×1.15³ = **30.4175 km**. Neither sequence includes deloads, rounding, other training constraints, or adaptation; do not interpret as a vendor schedule.

## Research implication

Test authenticated matched-input marathon plans across 12, 13, 14 and 15 weeks, with fixed ability, mileage and run frequency, to see whether the vendor's duration distinctions are reflected in generated long-run schedules. Capture weekly distances, deload timing, and athlete-specific exceptions. For StrideLab Engine 1.5: **no algorithm changes**. For Engine 2.0: candidate research constraints only, subject to validation and safety review.

**Baseline comparison:** docs/research/runna-garmin-coaching-algorithm.md on research/runna-garmin-coaching-20261102 (blob 4fa8ff42197ebab6a93aca7be8af4fa74d6d611d) covers independent long-run limits and periodization, but does not mention this article URL or its 10–15%, 4–5-week, or 32-km guidance. Garmin Run Coach, Garmin DSW and Garmin Expert Coach remain distinct; this source establishes nothing about Garmin.
