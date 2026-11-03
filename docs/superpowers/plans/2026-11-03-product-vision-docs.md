# Product Vision Documentation Update Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Bring StrideLab's canonical documentation in line with the current athlete-performance vision, November dogfood milestone, Trackside 3.0 reconstruction, Engine 1.5 reliability work, and Engine 2.0 research direction.

**Architecture:** Keep product identity, execution roadmap, and session handoff separate. Historical phase/status documents remain unchanged and continue to serve as evidence. Canonical docs should point readers to the current branches and pull requests without pretending unreleased work is production-ready.

**Tech Stack:** Markdown, GitHub branches and pull requests.

**Spec:** docs/PRODUCT_VISION.md

## Global Constraints

- Keep the repository private/developer-oriented.
- Do not claim Engine 1.5 or Engine 2.0 is production-enabled.
- Do not rewrite historical Phase 4D/4E status files.
- Avoid em dashes in user-facing project copy.
- Keep Trackside 3.0 faithful to the approved Figma during the current reconstruction phase.
- Keep Engine 2.0 research separate from the release engine.

## Review Focus

- Current and future capabilities must be clearly separated.
- The November milestone must remain a dogfood milestone, not a public-launch claim.
- Active branch and PR references must not imply merge or release status.
- The whole-athlete vision must not turn into uncontrolled feature creep.
- Historical recovery evidence must remain discoverable.

---

### Task 1: Product vision

**Files:**
- Create: `docs/PRODUCT_VISION.md`

- [ ] Define the durable product promise, mission, athlete model, scope, principles, and long-term direction.
- [ ] Separate today's product from the long-term adaptive performance system.
- [ ] Commit.

### Task 2: README

**Files:**
- Modify: `README.md`

- [ ] Update the tagline to `Train the whole athlete.`
- [ ] Explain the current product honestly.
- [ ] Link to product vision and roadmap.
- [ ] Keep development status and technology information concise.
- [ ] Commit.

### Task 3: Roadmap

**Files:**
- Modify: `docs/ROADMAP.md`

- [ ] Replace the stale Phase 4D/4E roadmap with the November dogfood roadmap.
- [ ] Keep Trackside, Engine 1.5, integration, dogfooding, and Engine 2.0 as distinct tracks.
- [ ] Preserve links to historical status documents.
- [ ] Commit.

### Task 4: Handoff

**Files:**
- Modify: `docs/HANDOFF.md`

- [ ] Replace stacked historical banners with a current concise recovery handoff.
- [ ] Record active branches, current goals, non-goals, and verification rules.
- [ ] Point to historical documents instead of duplicating their contents.
- [ ] Commit.

### Task 5: Review

- [ ] Read all four canonical docs together.
- [ ] Check for contradictory current-status claims.
- [ ] Confirm no historical status file was changed.
- [ ] Open a draft documentation PR for review.
