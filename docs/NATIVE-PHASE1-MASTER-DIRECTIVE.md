# StrideLab — Complete Native UI/UX & Feature Reconstruction

## Master Implementation Directive — Phase 1

### 1. Primary objective

Continue developing StrideLab from its latest verified native Android checkpoint.

**Our immediate objective is to completely reconstruct the accepted Phase 4E prototype's UI/UX, navigation, screens, interactions, and functional features in native Kotlin/Jetpack Compose.**

The current native application has reliable foundations, but its appearance and feature completeness remain substantially below the accepted prototype.

I prefer the original Phase 4E design. I want that visual quality restored while retaining the technical improvements of the native application.

**This is a complete application reconstruction milestone, not merely a visual redesign or Training tab implementation.**

Our development sequence is now:

1. **Phase 1 — Complete Native UI/UX and Feature Reconstruction**
2. **Phase 2 — Advanced Unified Coaching Engine 2.0 Improvements**
3. **Phase 3 — Independent Coaching Benchmarking and Validation**
4. **Phase 4 — Private Daily-Use Testing and Continuous Refinement**

Do not begin substantial Engine 2.0 expansion or competitor benchmarking until Phase 1 reaches functional and visual acceptance.

Do not restart the project or rebuild verified foundations.

---

## 2. Repository recovery and preservation

Repository:

`Isshhin/stridelab-development`

Current development branch:

`feature/native-reconstruction-1-20261026`

Latest verified executable checkpoint:

`checkpoint/native-reconstruction-s2a-final-e13ce3d`

Verified implementation commit:

`e13ce3d54f5b442ed19581b6d80b77e2467780b5`

Successful CI:

`40333913342`

Read the existing project documentation, particularly:

- `docs/NATIVE-FEATURE-RECONSTRUCTION.md`
- `docs/NATIVE-RECONSTRUCTION-CONTINUE.md`
- `docs/NATIVE-UI-RECONSTRUCTION-20261026.md`
- `docs/ROADMAP.md`
- Existing Phase 4D/4E acceptance and checkpoint documentation

Fetch and inspect the latest remote branch before making changes. Preserve newer work and documentation commits.

Recover the accepted Phase 4E prototype's actual source code, styling, assets, screenshots, navigation, feature behavior, and existing functionality.

**The accepted Phase 4E prototype is our authoritative UI/UX reference. Do not use older rejected design candidates.**

Preserve all previous verified branches and checkpoints.

Do not:

- Restart the project.
- Replace the native Kotlin/Jetpack Compose architecture.
- Reintroduce WebView for primary application screens.
- Overwrite accepted assets or existing checkpoints.
- Regress authentication, synchronization, or offline caching.
- Change `io.stridelab.privatealpha`.
- Merge into `main`.
- Create a public release.
- Fabricate training data or metrics.

---

## 3. Establish a complete prototype parity inventory

Before restructuring screens, inspect the actual Phase 4E application and create a comprehensive feature-parity matrix.

Inventory every approved:

- Primary navigation tab.
- Screen and nested page.
- Dashboard section.
- Modal and dialog.
- Chart and visualization.
- Settings option.
- Workout-related workflow.
- Training plan feature.
- Calendar interaction.
- Exercise library feature.
- Coaching analysis feature.
- Data source and synchronization feature.
- Offline interaction.
- User-configurable preference.

For each feature, classify its current native status:

**Implemented and verified / Implemented but incomplete / Missing / Intentionally replaced / Blocked by external dependency.**

Inspect actual executable prototype behavior instead of relying exclusively on old documentation.

Distinguish functional features from prototype-only simulations. Reconstruct genuine functionality using available data instead of recreating misleading demonstrations.

Keep the parity inventory updated throughout implementation.

**No approved feature should disappear silently during migration.**

Previously rejected UI elements, such as unnecessary Progress pages or redundant progress sections, should not be reintroduced merely because an older source snapshot contains them.

---

## 4. Reconstruct the complete visual design

I am not satisfied with the current native application's visual quality compared with Phase 4E.

Reconstruct the accepted prototype's visual identity as closely as practical while maintaining high-quality native Android interaction.

### Requirements

Restore and refine:

- Typography and text hierarchy.
- Layout composition.
- Dashboard information density.
- Card dimensions, proportions, and grouping.
- Navigation structure.
- Spacing and alignment.
- Accent-color usage.
- Charts and visualizations.
- Workout cards.
- Calendar design.
- Exercise library modals.
- Loading, empty, error, and offline states.
- Animations and screen transitions.
- Button styling and interactive feedback.
- Visual consistency between tabs.

Preserve the established palette:

- Near-black: `#050506`
- Warm white: `#F4F1EA`
- Signal lime: `#D6FF4F`
- Appropriate discipline-specific accent colors.

Do not interpret visual parity as simply using these colors with generic Material 3 components.

The entire application should feel like a cohesive, premium fitness application.

Experiment with better native implementations where appropriate, but do not sacrifice the distinctive character of the approved prototype.

### Mandatory visual verification

Render and inspect the original Phase 4E prototype alongside the reconstructed native screens.

Compare equivalent screen sizes, content states, typography scales, and navigation scenarios.

Capture actual screenshots and inspect:

- Component proportions.
- Vertical spacing.
- Information hierarchy.
- Chart presentation.
- Modal layouts.
- Navigation.
- Empty and populated states.
- Large-font accessibility.
- Different screen dimensions.

Iteratively resolve substantial visual discrepancies.

Automated screenshot capture without reviewing the resulting images is insufficient.

**Do not consider visual reconstruction complete until the actual rendered native screens meet the approved design quality.**

---

## 5. Reconstruct every approved application tab

The following describes the expected functional coverage. Match the actual accepted prototype's information architecture; do not create unnecessary new bottom tabs for features that originally belonged inside existing screens or modals.

### A. Home / Overview

Reconstruct the complete dashboard experience.

Include:

- Wellness snapshot when genuine records exist.
- Training volume.
- Weekly and historical trends.
- Running, strength, and other discipline distributions.
- Training consistency.
- Recent workouts.
- Useful activity summaries.
- Relevant upcoming scheduled training.
- Meaningful comparison metrics when supported.

Maintain accurate dates, time zones, recorded values, and units.

Avoid unnecessary cards, redundant sections, excessive empty space, and fabricated readiness assessments.

Charts should be compact, visually polished, interactive where useful, and properly labeled.

### B. Activities

Reconstruct the complete activity-history experience.

Include:

- Accurate chronological activity history.
- Discipline categorization.
- Useful filters and grouping.
- Real activity details.
- Pace, heart-rate, elevation, and other available recorded metrics.
- Source-recorded laps and splits.
- GPS maps and routes when genuine coordinates exist.
- Meaningful historical comparisons.
- Activity Intelligence analysis.
- Offline access to cached details.

Improve the existing basic GPS trace toward the prototype's map experience using an appropriate low-cost map implementation where technically feasible.

Do not fabricate routes when the provider does not supply coordinates.

For pace graphs, retain intuitive presentation, with faster paces visually higher.

Preserve missing-data handling and honest comparisons.

### C. Training — highest functional priority

Implement the full native Training experience.

**The Training tab must be present, visually complete, and genuinely capable of creating personalized training plans.**

Reconstruct the accepted plan builder and improve usability where appropriate.

Support:

- Running.
- Strength and gym training.
- Calisthenics.
- Conditioning.
- Coordinated multi-discipline training.

The user should be able to specify:

- Primary goal.
- Secondary goals.
- Fitness experience and current ability.
- Training days per week.
- Preferred training days.
- Preferred quality running days.
- Exactly one preferred long-run day when applicable.
- Training plan duration.
- Available workout time.
- Equipment and quantities.
- Running mileage, estimated from actual recent history and editable.
- Exercise preferences and limitations.
- Optional recovery information.

The system must generate actual workouts with meaningful prescriptions, not merely place generic activity names into calendar slots.

Provide:

- Plan generation.
- Plan preview.
- Explanation of recommendations.
- Explicit plan approval.
- Plan editing.
- A functional weekly/monthly calendar.
- Workout detail screens.
- Planned versus suggested versus completed distinctions.
- Scheduling conflicts.
- Missed-workout reasons.
- Appropriate basic adaptations.
- Offline persistence.

The engine must account for interactions between different exercises and disciplines.

For example, strength workouts involving demanding lower-body exercises should be considered when scheduling running intensity.

Preserve recovery-oriented safeguards and avoid unsupported recommendations.

**Wearable data must remain optional.** A user without a watch must still receive a useful training plan based on their ability, goals, training history, reported effort, and available information.

### D. Exercise Library

Reconstruct the approved Explore Workouts experience as a polished native modal or equivalent appropriate native presentation.

Do not make it a generic empty list of exercise names.

Include appropriate running, gym, strength, calisthenics, and conditioning exercises.

Each exercise should have:

- Purpose and description.
- Real, properly licensed instructional images where available.
- Equipment requirements.
- Difficulty or progression level.
- Technique instructions.
- Suggested sets, repetitions, or duration.
- Easier variations.
- Harder progressions.
- Suitable alternatives.

For calisthenics, include progression pathways that support beginners who cannot yet perform the final movement.

Do not create arbitrary or misleading progression requirements.

Preserve available licensed assets and attribution.

### E. Workout Execution and Logging

A generated plan must connect to actual workout execution.

Users should be able to:

- Open a planned session.
- View prescribed exercises.
- Record sets and repetitions.
- Record loads where applicable.
- Record duration or distance when relevant.
- Record effort and completion feedback.
- Mark workouts completed, missed, or skipped.
- See completed sessions in their training history.
- Reopen previously recorded workouts offline.

Use account-isolated, durable storage.

Avoid duplicate workout creation during synchronization.

Do not mark workout logging as complete if only visual controls exist.

### F. Coaching / Intelligence

Restore the approved coaching-analysis experience.

Analysis should interpret genuine training information instead of merely repeating statistics already displayed.

Use concise, well-written paragraphs.

Prioritize:

1. What the training data suggests.
2. Meaningful practical recommendations.
3. Relevant historical comparisons.
4. Limitations or uncertainty where necessary.

Do not add unnecessary repetitive analysis boxes.

This phase should restore a useful baseline coaching experience.

**Do not expand or claim completion of Advanced Unified Coaching Engine 2.0 during this reconstruction.**

The later advanced engine will improve personalization, athlete-response learning, periodization, and recovery-aware adaptation after the complete application is functional.

### G. Settings, Equipment and Data Sources

Restore the approved settings functionality.

Include applicable:

- Account management.
- Data source configuration.
- Intervals.icu synchronization.
- Workout and activity preferences.
- Training zones.
- Equipment profile.
- Coaching configuration.
- Relevant notifications.
- Offline status.
- Backup/export capabilities where supported.
- Appropriate app and version information.

Preserve existing security and credential-storage practices.

Do not expose debugging controls or permanent diagnostic messages in normal production UI.

---

## 6. Maintain the existing navigation fixes

The following behaviors are mandatory and must not regress:

- Switching to a primary tab opens its root destination.
- Activities → Detail → Home → Activities returns to the Activities list.
- Activities retains list position without restoring an abandoned detail screen.
- Android Back from an activity detail returns to the appropriate Activities list, including when opened from Home.
- Successful synchronization does not leave persistent "History refreshed" banners.
- Errors and offline conditions remain discoverable.

Use appropriate native Navigation Compose behavior.

---

## 7. Preserve real-data integrity

Retain the working:

- Supabase authentication.
- Intervals.icu integration.
- Owner-filtered synchronization.
- Account-scoped SQLite.
- Android Keystore security.
- Offline activity history.
- Source samples and laps.
- Authentic wellness records.
- Existing migration and preservation behavior.

Never replace genuine data with demonstration records to make the UI appear populated.

When a feature lacks the required source data, provide an honest, well-designed unavailable state.

Synthetic data may be used only in isolated tests and development fixtures.

Do not introduce destructive schema changes without thoroughly verified migrations.

---

## 8. Implementation sequencing

Execute this as **one coordinated native reconstruction project**, but with incremental code and verification checkpoints.

### Increment 1 — Prototype parity and visual foundation

Inspect all accepted screens, create the feature matrix, recover the actual visual design, and establish reusable Compose components.

Verify representative screens against the prototype.

### Increment 2 — Complete Training and Plan Generation

Implement a functional Training tab, multi-discipline plan builder, generated workout prescriptions, review and approval.

This is the highest-priority missing functional feature.

### Increment 3 — Calendar and Workout Library

Connect plans to a functional calendar, workout details, exercise library, and relevant progression information.

### Increment 4 — Workout Logging and Shared Integration

Implement completed workout recording, persistence, history integration, offline reopening, and basic coordination with existing activity data.

### Increment 5 — Full Prototype Feature Parity

Complete outstanding approved features across Home, Activities, Settings, Intelligence, and other screens.

Resolve visual and functional differences discovered in the parity inventory.

### Increment 6 — Final Quality and Acceptance

Run complete functional, visual, integration, offline, and regression verification.

Document remaining genuine external blockers rather than disguising them as completed features.

These increments belong to the same reconstruction milestone, not six unrelated projects.

---

## 9. Mandatory acceptance tests

At minimum, verify that:

1. The application launches into the correct native interface.
2. Its visual appearance closely matches the accepted Phase 4E design.
3. All approved tabs and navigation workflows function.
4. A beginner can generate an appropriate plan without a smartwatch.
5. An experienced runner can generate a history-informed running plan.
6. A user can generate a coordinated running and strength plan.
7. A calisthenics beginner receives understandable exercises and progressions.
8. Preferred quality days and the single preferred long-run day work.
9. Scheduling conflicts produce understandable explanations.
10. A draft plan can be reviewed and explicitly approved.
11. The accepted plan appears in the calendar.
12. Workouts can be opened and their prescriptions inspected.
13. Users can record completed workouts.
14. Workout logs and approved plans persist after application closure and offline reopening.
15. Existing activity synchronization and authentic wellness data remain functional.
16. Recorded activity details and graphs remain available offline.
17. Existing navigation and sync-feedback corrections remain intact.
18. Large fonts, different screen dimensions, empty states, and error states remain usable.
19. No fabricated metrics, routes, workouts, or wellness data are introduced into production.
20. The final APK passes the required Android and repository regression tests.

Use realistic synthetic fixtures for automated tests and separate genuine-athlete/device acceptance from emulator verification.

---

## 10. Delivery, checkpoints and recovery

Preserve all verified checkpoints.

After each substantial, passing implementation increment:

- Commit verified source changes.
- Run applicable unit and emulator tests.
- Inspect rendered UI screenshots.
- Create a uniquely named GitHub recovery checkpoint.
- Preserve the source and integrity manifest.
- Record the exact commit and CI results.
- Document completed versus remaining parity requirements.

Provide private Android APK artifacts when useful for evaluation.

Preserve the package identity and follow the established signing process.

Do not assume a new APK can update the currently installed app if its signing certificate differs.

Do not publish public releases or automatically uninstall, clear, or overwrite device data.

If execution is interrupted, resume from the latest verified checkpoint in the same master project.

Do not restart completed development or repeat already verified work.

---

## 11. Revised roadmap after reconstruction

### Phase 2 — Advanced Unified Coaching Engine 2.0

Once the native application is complete, extend the existing modular coaching foundation.

Our intended engine improvements include:

- Athlete-response learning.
- Individualized progression.
- Guided exercise progression for beginners.
- Multi-discipline training coordination.
- Goal-aware periodization.
- Intelligent workout compatibility.
- Recovery assessment without requiring wearable devices.
- Optional wearable-enhanced coaching.
- Subjective fatigue, soreness, and effort feedback.
- Planned-versus-actual learning.
- Workout substitutions.
- Recovery and interruption handling.
- Data confidence and recommendation confidence.
- Explainable coaching decisions.
- Stable, versioned coaching policies.

Preserve the principle of **one unified coaching experience supported by modular specialist systems**.

Do not discard the existing Engine 2.0 foundation.

### Phase 3 — Coaching Benchmarking and Validation

Evaluate the improved engine against:

- Runna's publicly documented running coaching principles.
- Freeletics' publicly documented exercise programming and adaptive workout principles.
- Relevant independent exercise-science guidance.
- Realistic beginner and advanced athlete scenarios.
- Cross-discipline scheduling and recovery scenarios.
- Missing wearable data scenarios.
- Interrupted training and missed workout scenarios.

Do not assume access to competitors' proprietary algorithms.

Measure plan quality, constraint satisfaction, progression appropriateness, training consistency, adaptability, explainability, and safety-related behavior.

Passing software tests alone does not validate coaching effectiveness.

### Phase 4 — Private Testing and Continuous Improvement

Conduct sustained real-device and genuine-athlete testing.

Evaluate user experience, adherence, reliability, training progression, and coaching usefulness.

StrideLab remains a private development application unless I explicitly authorize wider distribution.

---

## 12. Final definition of Phase 1 completion

Do not declare reconstruction complete simply because the application builds, screenshots look attractive, or the Training tab exists.

**Phase 1 is complete only when the native Android application reproduces the approved Phase 4E experience to a high visual and functional standard.**

I should be able to:

- Open an application that looks and feels like the prototype I approved.
- Navigate every approved feature.
- Synchronize my genuine activities.
- Understand my recorded training.
- Generate personalized fitness plans.
- Manage those plans in a real calendar.
- Explore exercises and their progressions.
- Record completed workouts.
- Receive useful basic coaching analysis.
- Access supported records and plans offline.
- Use the application without requiring a smartwatch.

Every approved feature must be implemented and verified, intentionally superseded with documented justification, or explicitly identified as blocked.

Do not silently defer missing features while claiming complete parity.

**Execute the full reconstruction with quality as the primary requirement. Preserve verified work, test incrementally, and continue until the complete native application experience is reconstructed or a genuine blocker prevents further implementation.**

The next major development focus after this is Advanced Unified Coaching Engine 2.0, followed by benchmarking.

Proceed with actual implementation, not another planning-only response.