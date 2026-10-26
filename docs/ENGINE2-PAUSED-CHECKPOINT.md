# Engine 2.0 paused checkpoint — 26 October 2026

User changed priorities during implementation: real data/backend reliability, full application usability, then private daily-use Android, then Engine 2.0. Do not continue expanding the engine until those prerequisites are ready.

Preserved implementation: portable deterministic athlete model/planner; separate abilities; primary/secondary discipline priorities; unified lower-body spacing; time/equipment constraints; illness/injury holds; recovery reductions; technique-gated exercise progression; draft/approval/revision handling; offline calendar adapter; optional builder and feedback UI.

Actual evidence at pause: 35/35 Node tests passed on current source. 14/14 browser tests (including axe, approval, persistence and failure atomicity) passed on HTML built at SHA256 5797816e1fb8330534a99be1dfb9d45accc03d3d9cdcefd13f5f580c1fc475c3. A later weighted running-duration source adjustment passed Node tests but is not yet represented in those browser results. Do not call the current full source browser-verified until rebuilt and tested.

First inherited run: 272 passed, 1 failed at legacy file-origin reload (GPD null). The failing case passed 3 focused repetitions. A second full run uses the p3c helper's existing documented reload-safe HTTP origin and durable seed flag applied to older helpers, without changing assertions. Record its final result separately. Do not hide the first failure or claim the original unmodified harness passed.

Unfinished: production Next.js adapter; cloud/wearable sync; full periodization and progressive load prescriptions; Android packaging/device validation; complete master requirements; source review and final regression of latest weighted-duration change. The original Phase 4D engine and Android sources were not overwritten. No APK or public release was created.

Recover: install pinned dependencies with `npm ci --prefix engine2`; retrieve the accepted private phase4d-final-candidate archive, SHA256 915c9c37e3816291bfc9ab3ead1f37b400711886056a43fd1af101b602cdaa6b; run `python3 engine2/build.py /path/to/StrideLab-Phase4D-Final-candidate.zip`, `npm test --prefix engine2`, and `npm run test:browser --prefix engine2`. Then run all 273 inherited assertions against generated engine2/dist/index.html. Do not replace or merge the daily application with this prototype.
