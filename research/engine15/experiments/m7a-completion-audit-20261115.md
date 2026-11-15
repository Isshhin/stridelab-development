# Engine 1.5 M7a — Read-only plan-to-source completion attribution audit

Date: 2026-11-15. Stacked on draft PR #81. No production activation, no emulator/phone QA, no plan persistence changes.

## Verified repository observations

- \`ActivityRecord\` retains an activity ID, source, type, start time and optional numeric metrics, **but no owner ID once parsed**. Owner/account scope needs a trusted caller before those records can be used to make training decisions.
- \`ActivityPlanLink\` lets the user explicitly link an activity to a saved running session. \`PlanOperations.linkActivity\` changes the saved session status to \`completed\`, which is **not proof of completing the prescribed workout or effort**.
- \`ActivityImportMatcher\` and \`ActivityDisplayConsolidation\` can identify potential repeats, but presentation grouping or source-ID matching is not an authenticated cross-provider identity attestation.
- \`IndependentQualitySourceBinding\` checks candidate source lap durations against linked sessions. It cannot verify that those laps represent threshold work or authorize intensity.

## Implemented M7a

\`IndependentCompletionAttribution.inspect\` takes asserted account-scoped activity rows and saved plans, resolves the **exact manually linked session and source ID**, and returns one of:

- \`Deferred(ruleId,explanation)\`: incompatible accounts, plan/session ambiguity, missing link, invalid/retired activity, mismatched local date, contradictory same-ID content, same original-provider ID repeated under different local IDs, or independent provider IDs with suspiciously similar time/distance.
- \`LinkedObservation\`: a *read-only descriptive observation* containing the existing source distance and elapsed duration (both nullable), whether a manual workout log exists and whether a source identity was asserted. It explicitly reports \`sourceEvidenceVerified=false\`, \`workoutCompletionVerified=false\`, \`authorizedForAdaptiveLoad=false\`, \`authorizedForProduction=false\`.

It cannot authenticate these caller-supplied records; an activity link and a \`completed\` status are insufficient to establish achieved intensity or tolerance. It cannot infer completed running volume for all activities because it only reviews linked sessions. No recorded value is taken from the planned distance; no unknown metric is converted to zero.

19 focused JVM tests cover exact links; claimed vs authenticated identity; owner mismatch; identical vs conflicting ID duplicates; repeated original IDs; plausible cross-provider duplicate; near-but-distinct events; mismatched dates; missing links/metrics; invalid source; retired source; inactive plan; non-run or uncompleted session; double links; duplicate plans/sessions; and determinism.

**Status:** source and tests committed; not yet executed by the GitHub connector. No live athlete data was inspected and no Android code path calls this new component. The previous owner-reported passing Gradle run covers draft PR #79 only; M6a, M6b and M7a all require local tests.

## Next gate

M7b: group per-session audit decisions into a reviewable weekly ledger with explicit missing/ambiguous counts. Never claim complete-week mileage, even if every linked session has a distance, until there is independently verified activity coverage, unambiguous source identity and session attribution.

M8: require trusted M7 provenance and reviewed training intent before proposals can become real adaptive plan decisions. Separate race-specific feasibility from Train Your Way mileage-target planning. All physical-phone QA is deferred to M11 at the end of the roadmap.
