# Phase 1 prototype parity inventory — live implementation record

Authoritative reference: accepted Phase 4D Final packaged in Phase 4E, android-app/android/app/src/main/assets/www/index.html. Inspected its executable application on 2026-10-26 (isolated localhost prototype data), not rejected earlier candidates. Current native baseline: e13ce3d, verified CI 40333913342; remote documentation head a85cad5 recovered before edits. Status vocabulary: **Verified / Incomplete / Missing / Replaced / External**. New code stays Incomplete until its exact automated and visual gates pass; human acceptance is separately open.

The actual reference has four primary tabs (Overview, Training, Activities, Library). Settings is a profile modal, not a fifth primary tab. No Progress page is approved. Original demo history, simulated map/readiness values and simulated Garmin controls are not production-data requirements.

| Approved feature / executable reference behavior | Baseline native status | Reconstruction decision / current work |
| --- | --- | --- |
| Overview, Training, Activities, Library roots | Incomplete | Restore four-tab architecture; Settings via profile |
| Root-tab navigation and retained Activities list position | Verified | Preserve regression coverage across new roots |
| Back from Home-opened detail returns to Activities | Verified | Preserve |
| Overview greeting/date/profile | Incomplete | Recover hierarchy and profile entry; no invented athlete name |
| Today workout, session structure, Start/Preview | Missing | Connect to approved Engine 1.0 prescriptions |
| Coach paragraph and recent-run link | Incomplete | Use genuine plans/history; omit sample claims |
| Upcoming workout | Missing | Derive from approved dated plan |
| This-week completion ring/day strip/counts | Missing | Use actual user logs and approved plan, not sample counts |
| Recovery section | Replaced | Authentic source-dated wellness replaces simulated values |
| Seven-day volume, distribution and consistency | Verified | Refine compact design; retain missing-data caveats |
| Historical trend / weekly load chart | Incomplete | Real cache/plan data; no simulated fitness improvement |
| Training weekly strip, date selection, week arrows | Missing | Functional dated native calendar |
| Training month grid / month navigation | Missing | Functional selection and planned/completed indicators |
| Plan status, pause and cancel | Missing | Pause/archive preserves logs and accepted snapshots |
| Choose multiple disciplines | Missing | Engine 1.0 run/strength/calisthenics/conditioning |
| Running goals, experience, current weekly km, longest run | Missing | Editable history-informed inputs; no watch required |
| Runs/week and quality count | Missing | Validate counts/ability and spacing |
| Exactly one long-run day and preferred quality days | Missing | Single enum and explicit conflict explanations |
| Strength goal/focus/experience/frequency/equipment | Missing | Equipment quantities and actual prescriptions |
| Calisthenics goal/current ability/frequency | Missing | Beginner pathways; no calendar-driven skill promotion |
| Shared start date/weeks/minutes/available days | Missing | Validate all selected disciplines rather than dropping them |
| Returning-from-break option | Missing | Conservative bounded baseline policy with explanations |
| Review/edit choices and generated preview | Missing | Durable draft and explicit approval |
| Weekly plan totals, week selection and rationale | Missing | Saved prescription snapshots / versioned policy |
| Move generated workout / recovery conflict warnings | Missing | Validate day, collision and cross-week spacing |
| Workout preview with steps/exercises/sets/reps/rest | Missing | Engine 1.0 structured prescriptions |
| Workout Start, per-set entry and completion | Missing | Blank actual metrics until user records them |
| Swap exercise with appropriate equipment | Missing | Do not transfer old loads or change historical logs |
| Timer/rest and exercise-by-exercise controls | Missing | Native execution workflow; inventory remains open |
| Completion effort/feedback, missed/skipped reason | Missing | User-reported data, explicit statuses and basic suggestions |
| Last performed values and history reopening | Missing | Derive only from saved user logs |
| Activities chronology and discipline cards | Verified | Preserve authentic cache; add filtering/grouping |
| Local JSON import of genuine run records | Missing | Legacy retained; native validation/import parity remains open |
| Activity full-map and sheet positions | Incomplete | Source-coordinate trace exists; native map interaction remains open |
| GPS zoom, fit, highlighted selected split | Incomplete | No simulated routes; provider availability limits apply |
| Pace/HR/cadence/elevation inspection | Incomplete | Existing source graphs; faster pace must be higher |
| Source laps / selectable splits | Incomplete | Source laps retained; interaction remains open |
| Running analysis / expandable detailed paragraph | Incomplete | Cautious source-evidence comparisons exist; expand from genuine evidence |
| Link recorded run to planned workout / unlink | Missing | Explicit unique link, no automatic match assumed |
| Planned versus actual comparison | Missing | Compare compatible recorded values with saved targets |
| HR zones and running dynamics | Missing | Only authentic source values and user-configured zones; honest absence |
| Activity notes, elapsed/moving time, elevation | Incomplete | Preserve source distinctions; notes workflow remains open |
| Library 23 executable entries | Missing | Native adaptation of accepted catalog, with corrected images |
| Library search, discipline, difficulty, pattern/equipment | Missing | Native browsing filters; not generic names-only lists |
| Exercise modal, purpose, technique, mistakes, progression | Missing | Native sheet with actual catalog metadata |
| Image match disclosures, source and license attribution | Missing | Preserve corrected mapping; rasterize without altering originals |
| Easier/harder/related alternatives and tradeoffs | Missing | Use accepted notes; no arbitrary progression prerequisites |
| Saved exercises and collections | Missing | Durable owner-scoped preferences; not cosmetic toggles |
| Templates and saved session browsing | Missing | Connect real prescriptions/logs |
| 21 read-only movement/skill guides | Missing | Preserve reference-only distinction |
| Practice from an exercise | Missing | Connect to actual reviewed prescription and logging |
| Settings units | Replaced | Current native explicitly metric; no misleading inactive toggle |
| Settings Garmin sync (simulated) | Replaced | Real native Intervals source controls; Garmin configuration is external |
| Settings developer engine checks / Reset demo | Replaced | Test-only tools stay out of production Settings |
| Real account/session/security and source synchronization | Verified | Preserve Supabase/Intervals/Keystore and failure handling |
| Equipment, zones, coaching preferences | Missing | Native owner-scoped configuration remains in reconstruction scope |
| Notifications | Missing | Optional permission-aware implementation, never simulated enabled state |
| Legacy backup/restore | Verified | Preserved entry; native plan export/import is additional work |
| Offline activities, source detail and wellness | Verified | Preserve; plan/log persistence must receive new tests |
| Loading/empty/error/offline states | Verified | Extend consistently to new workflows |
| Large fonts / narrow and wide layout | Incomplete | Existing tested foundation; verify new calendar/forms/modals |
| Transitions, focus, Back, touch feedback | Incomplete | Native modal and navigation behavior; final visual acceptance open |

## Current implementation increment (not full parity acceptance)

The working native increment now contains actual Engine 1.0 multi-discipline generation, saved drafts, explicit approval, week/month calendar, pause/archive/move/conflicts, actual sets/reps/hold/load logging, actual workout dates, missed reasons and explicit recovery choices. Library contains the accepted 23 curated entries, 21 educational references, original licensed images/fit disclosures, filters, favorites, collections and a real Sessions view. Owner-scoped offline plan backup export/validated non-destructive restore preserves snapshots/logs/feedback. Manual source-run links enforce one source per session and retain original targets; unlink restores the prior session state. Source charts offer nearest-sample inspection and missing-value disclosures. All these need the exact final checkpoint gate; the baseline-status column above intentionally remains the historical gap inventory.

Local engineering evidence: 64 JVM tests, 14 Python tests and a 19-test selected emulator suite passed before the final builder scroll reset. Earlier CI 40353150650 failed 2/58 emulator tests (builder step scroll position and a library off-screen loading assertion); it is retained, not promoted. Final full CI remains the promotion gate. Automated fixture evidence is not real-athlete/device acceptance or full design signoff.

Still open: full library template/single-movement practice and workout swap parity, full native settings/profile/equipment/zones/preferences and notifications, validated source-activity JSON import, interactive map/lap parity, dashboard integration of manual logs without duplicates, richer baseline coaching and complete phone/wide/TalkBack/visual acceptance. Advanced Engine 2.0 and benchmarking remain deferred.

## Explicit replacements of prototype-only behavior

No demo activity, map or wellness record is copied into production. Native real-data sources supersede simulated Garmin/history values. A plan target is never labeled recorded mileage or pace. Unsupported requests must explain conflicts rather than silently removing selected disciplines. Skill progression requires user-reported control rather than automatic elapsed-week promotion. Holds use seconds, not the prototype preview's generic reps label. Generic automatic 2.5 kg progression is not restored. Historical prescriptions and logs survive engine-policy upgrades as immutable saved snapshots.

## Revised milestone order

Phase 1: complete native reconstruction and functional Engine 1.0, including visual acceptance. Phase 2: Advanced Unified Coaching Engine 2.0. Phase 3: independent coaching benchmarking/validation. Phase 4: sustained private daily-use refinement. This supersedes older roadmap instructions to postpone all features or advance to engine expansion before complete reconstruction. Actual athlete/device acceptance remains distinct from emulator evidence.
