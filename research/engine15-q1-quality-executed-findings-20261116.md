# Engine 1.5 Q1 — executed synthetic baseline findings

**Status:** Executed on user's Windows Gradle checkout on 2026-11-16. Test command: `./gradlew.bat -PstrideEngine15Active=true :app:testDebugUnitTest --tests "io.stridelab.privatealpha.Engine15Q1QualityBaselineTest" --no-daemon --rerun-tasks`. User reported `BUILD SUCCESSFUL` with 25 tasks executed. Source: `engine15-q1-results.zip` containing the three Q1-generated exports. This summary is based on inspecting those uploaded exports. Results are synthetic, not athlete-effectiveness evidence.

**Scope:** 11 profiles × 8 weeks = 88 weekly records; 1,006 running step rows; all 11 generated without policy rejection; 91 quality-session instances.

## Executed outputs

| Profile | W1 weekly km | W3 weekly km | W4 weekly km | W1/W2/W3 quality work km | W4/W8 quality | Maximum single-session cool-down |
|---|---:|---:|---:|---|---|---:|
| Beginner, 15 km/week | 12.9 | 14.0 | 9.5 | 0 / 0 / 0 | 0 / 0 | N/A |
| Developing, 30 km/week | 27.0 | 30.0 | 23.8 | 2.00 / 1.98 / 2.00 | 0 / 0 | 4.83 km |
| Established, 60 km/week | 54.0 | 60.0 | 48.0 | 4.00 / 3.96 / 4.00 | 0 / 0 | 7.23 km |
| Experienced, 80 km/week | 72.0 | 80.0 | 64.0 | 4.00 / 3.96 / 4.00 | 0 / 0 | 11.03 km |
| Synthetic 6 km repeated threshold WORK distance ("30-minute proxy") | 54.0 | 60.0 | 48.0 | 9.12 / 9.12 / 9.12 | 0 / 0 | 5.83 km |
| Synthetic 12 km repeated threshold WORK distance ("60-minute proxy") | 72.0 | 80.0 | 64.0 | 13.44 / 13.44 / 12.00 | 0 / 0 | 7.61 km |
| Returning, 40 km/week | 24.2 | 26.3 | 21.0 | 0 / 0 / 0 | 0 / 0 | 1.58 km (later weeks) |
| Fatigued, 60 km/week | 45.2 | 60.0 | 48.0 | 0 / 3.96 / 4.00 | 0 / 0 | 7.23 km |
| Three running days, target 80 km/week | 45.0 | 41.0 | 27.0 | 2.00 / 1.98 / 2.00 | 0 / 0 | 10.23 km |
| Synthetic threshold logs with total workout minutes but missing qualityWorkKm | 54.0 | 60.0 | 48.0 | 4.00 / 3.96 / 4.00 | 0 / 0 | 7.23 km |
| Recent quality frequency unknown | 54.0 | 60.0 | 48.0 | 2.00 / 1.98 / 2.00 | 0 / 0 | 6.43 km |

**Do not interpret work kilometers as measured work minutes.** The current v1 schema records WORK distance in meters. The exported `work_minutes_at_planning_pace_PROXY` is just distance × an administrative planning pace (6:00/km for these fixture profiles), not measured threshold duration or intensity. The 30/60-minute-labeled fixtures are explicitly **distance proxies**: two 6 km / two 12 km completed WORK-distance observations respectively, not independently verified minute-based capacity.

### Direct evidence of oversized cool-down

80 km profile, week 3, *each* of two quality sessions:
- 15.20 km allocated session, planned duration 92 minutes
- WARM_UP = 1,670 m; WORK = 2,000 m; RECOVERY = 500 m; COOL_DOWN = **11,030 m**
- Cool-down is **72.6% of total session distance**; at fixture planning pace it is a **66.18-minute proxy**, not an actual timed cool-down.
- Quality WORK accounts for **13.16% of quality-session distance** and 5% of the 80 km week across both sessions.

The three-day 80 km-target case has a **10.23 km** cool-down in its week 3 quality session and reaches only **45 km** at its peak (week 1); the exported generator explanation explicitly reports `TARGET_NOT_REACHED`. The fixture's high target must not be silently treated as achieved.

### Direct evidence of nonprogression

For the no-admitted-threshold-history profiles, weeks 1–3 and 5–7 repeat nearly identical work distances. All profiles have **zero prescribed quality** in weeks 4 and 8.

For synthetic repeated 6 km-work history, weekly total WORK stays **9.12 km** in weeks 1–3. Individual session work changes significantly: week 1 is 4.56 + 4.56 km; week 2 is 5.91 + 3.21 km; week 3 is 6.00 + 3.12 km.

For synthetic repeated 12 km-work history, the week 2 work split is **8.61 + 4.83 km**, while week 3 is **6.00 + 6.00 km**. This reveals uneven dose allocation between the two sessions even though the synthetic history itself is identical.

### Direct evidence of history and fatigue gating

- The **minutes-only** history case generates the same week-by-week work totals as the established 60 km profile without qualifying threshold history. Existing `qualityWorkKm` evidence is therefore material; total workout minutes alone do not establish completed WORK duration.
- The fatigued 60 km profile generates no quality in week 1, with reduced weekly mileage (45.2 km vs 54.0 km for otherwise comparable established case), then returns to two quality workouts in week 2. Review whether this is a desirable *future policy*; no safety conclusion can be drawn from these synthetic fixtures.
- The returning 40 km profile generates no quality until week 5, then one session with approximately 1.62–1.64 km WORK in weeks 5–7.
- Unknown reported quality frequency yields one quality session per normal week despite requesting two, consistent with the conservative unknown-history policy.

## Root causes: inspected source, NOT individually instrumented binding-constraint traces

- `Engine15RunningProgram.quality`: distance-equivalent nominal work ceiling, short fixed repetition patterns, recovery ratio and session-fit logic.
- `Engine15RunningProgram.compose`: role allocation and distance-first composition; `priorBuildWork` freezes the first positive weekly WORK envelope; `qualityCount` suppresses sessions in every fourth week.
- `Engine15RunningProgram.quality`: final `COOL_DOWN` uses `total - segments.sumOf { meters }`, absorbing the session remainder.
- `Engine15RunHistory.assess`: eligibility requires `qualityWorkKm`, rather than measuring quality-work duration from logs.
- `Engine15.runningCeiling`, role-specific exposure caps and long-run share checks restrict the three-day high-target request.

The executed reports **do not contain internal constraint-level telemetry**, so no single cap is asserted as the proven binding limiter for each individual workout. Add explicit constraint traces if causal attribution is needed before changing policy.

## Q1 verdict and Q2 gate

**Q1 has passed execution and output analysis.** The generator's under-prescription / oversized cool-down / nonprogression are reproducible across the synthetic profiles, and the accepted-history limitations are visible.

**Next:** Q2 should introduce versioned mixed-unit contracts and truthful distance totals while keeping the existing v1 plans intact; Q3 should select evidence-supported main sets; Q4 should implement guarded, approval-based progression. Do **not** raise the 20% weekly work-distance guardrail or change existing approved workouts as an unreviewed side effect.

**Regression gates:** unchanged old-plan encode/decode, no reinterpretation of old meters as minutes, unknown work duration remains unknown, stable approval and owner protections, conservative recovery holds, and no unwanted mileage redistribution.

Raw reports were generated by the JUnit harness and remain local to the executor's machine; this Markdown summary does not purport to commit the full 215 KB fixture-output JSON to the repository.
