# Experiment 007 — Session-level reconciliation audit

**Registered extension:** 2026-11-04. **Status:** REGISTERED; zero Runna sessions captured. **Research only.** This is a *secondary descriptive measurement* and does not modify the original weekly-progression primary endpoint.

## Why weekly totals need an independent capture audit

A correctly transcribed weekly total can still conceal errors in the prescribed session distances, long-run designation, or number of distinct running days. A six-day schedule can contain seven workouts with a double day; **sessions and running days are different denominators**.

The secondary capture file is [experiment-007-runna-frequency-sessions.csv](experiment-007-runna-frequency-sessions.csv). Each row is one *planned run*, not one hard-work step, and must have a case ID, week, scheduled date/day, within-week session index, full prescribed distance, session category, `true`/`false` long-run flag and redacted evidence reference. Keep scheduled hard-work minutes **blank** if the app does not explicitly expose them or they cannot be calculated from complete prescribed workout steps. Empty is not equivalent to zero.

Use the research-only [session integrity analyzer](../../../research/validation/runna_session_integrity.py) to reconcile the session rows against the existing [case matrix](experiment-007-runna-frequency-cases.csv) and [weekly totals](experiment-007-runna-frequency-weeks.csv).

```bash
python research/validation/runna_session_integrity.py \
  --cases docs/research/experiments/experiment-007-runna-frequency-cases.csv \
  --weeks docs/research/experiments/experiment-007-runna-frequency-weeks.csv \
  --sessions docs/research/experiments/experiment-007-runna-frequency-sessions.csv \
  --output research-runna-session-readiness.json
python -m unittest discover -s research/validation -p 'test_runna_session_integrity.py' -v
```

## Admission and reconciliation rules

1. **No sessions captured:** `BLOCKED_NO_SESSION_OUTPUT`. It is not a zero-mileage result.
2. **Incomplete or unmatched plans:** require six distinct generated study cases with the registered 65 km/18 km/12-week athlete state, matching non-frequency settings, compatible app version and all 72 week captures. Otherwise block.
3. **Calendar consistency:** week 1 starts 2026-11-09; a recorded scheduled date must be within its corresponding Monday–Sunday week and have the claimed weekday. Session index must be unique within that case/week.
4. **Day count:** count distinct dates with prescribed runs, not number of sessions. Compare with weekly captured `run_days`; allow multiple sessions on a single date when the app actually prescribes them.
5. **Weekly distance:** sum all captured prescribed run distances, including warmups/cooldowns within workouts. Compare against the week card total; allow `max(0.1 km, 0.05 km per session)` for one-decimal rounding. Mismatches block until the source is reviewed; **do not silently add missing runs**.
6. **Long run:** when the week has a positive long-run distance, exactly one session should carry the long-run label and match the week-card long distance within 0.11 km. Report mismatches; a race week or unusual multi-long-run plan requires an explicit separate study revision instead of quietly changing rules.
7. **Valid fully reconciled capture:** `DESCRIPTIVE_ONLY_PENDING_CAPTURE_REVIEW`. The parser cannot authenticate provenance or determine proprietary Runna logic.

The output includes per-week largest prescribed session, its share of all prescribed weekly run distance, recorded session categories, run days and completeness of duration-based hard-work detail. It does **not** estimate actual workout intensity, future injury probability, or adaptation efficacy.

## Interpretation boundaries

The registered primary Runna frequency experiment still compares **BUILD→BUILD weekly progression** and independently checks the actual scheduled run-day exposure. This session-level check is a separate descriptive lane for investigating whether the weekly mileage is redistributed into smaller or larger individual workout doses at five, six and seven days.

Scientific associations involving **completed single-session distances and previous 30-day completed long runs** ([independent evidence addendum](../2026-11-04-running-frequency-and-session-spike-evidence.md)) must not be treated as a calibrated rule for *planned session distances*. No consented real athlete activity or injury outcome is analyzed in this audit.

**Source category:** Session CSV and weekly plan CSV are initially blank Runna observational templates. The tests use synthetic schedule fixtures. A future matched Runna plan set must be captured through authorized mobile-app access and reviewed before any result is labeled directly observed app behavior.
