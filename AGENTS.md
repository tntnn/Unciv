# Unciv Advanced AI — Agent Instructions

- Version: v1
- SHA-256: bde2b69593bda61748ed9d5b5f320f26704dfae6546ab2f0b937b5fa1de4f96f

## 1. Purpose

This file defines how an AI coding agent should work on the **Unciv Advanced AI** project.

The project goal is to make the official Unciv AI substantially stronger through better decision-making, planning, coordination, and evaluation — **not** through extra resources, hidden information, special combat rules, or other unfair advantages.

This is a long-running project. Prefer incremental, measurable, reviewable improvements over broad rewrites.

## 2. Project baseline

The current pinned upstream baseline is:

- Upstream repository: `yairm210/Unciv`
- Official tag: `5.0.1`
- Commit: `82edba7216ae98319be36f09acc3661ede23c6c4`
- Git tree: `6412accbc5ffcd960bbfce7d4af0cbacd7b87bb7`
- Target platforms: Desktop and Android
- Primary implementation language: Kotlin
- Shared AI implementation location: `core`

Do not silently move the project to a newer upstream revision. Upstream updates must be deliberate and followed by regression testing against the current Advanced AI baseline.

When starting work from a fresh checkout, verify the baseline before making project changes:

```bash
git rev-parse HEAD
```

For work that intentionally targets the pinned baseline, the result should be:

```text
82edba7216ae98319be36f09acc3661ede23c6c4
```

## 3. Project documentation and source of truth

The repository is expected to contain project documentation under `docs/advanced-ai/` as the project is migrated into the working repo.

Expected documents include:

- `docs/advanced-ai/ROADMAP.md` — living project roadmap;
- `docs/advanced-ai/M0_BASELINE.md` — M0 architecture and benchmark findings;
- `docs/advanced-ai/BENCHMARKING.md` — benchmark protocol when created;
- `docs/advanced-ai/DECISIONS.md` — architecture/engineering decisions when created;
- `docs/advanced-ai/milestones/M*.md` — milestone-specific scope when created.

Before modifying AI behavior, read the relevant project documents that exist in the checkout.

If project documents disagree, do **not** silently reconcile them. Prefer, in order:

1. the user's current explicit instruction;
2. this `AGENTS.md` for agent working rules;
3. the current milestone document for milestone scope;
4. the living roadmap for project direction;
5. older baseline/reference documents for historical evidence.

Report any material conflict that cannot be resolved from that precedence.

## 4. Non-negotiable project principles

### 4.1. No resource cheating

Do not make the Advanced AI stronger by giving it project-specific bonuses such as:

- extra Gold;
- extra Science;
- extra Culture;
- extra Faith;
- extra Production;
- free units;
- free buildings;
- reduced construction/research costs unavailable to the player;
- hidden economic multipliers;
- special combat bonuses;
- artificial maintenance or supply exemptions.

Existing Unciv difficulty mechanics remain part of the base game. This project must not add new resource advantages as a substitute for intelligence.

### 4.2. No information cheating

The AI must not knowingly use hidden information unavailable through legitimate game knowledge.

Examples of prohibited project behavior include:

- reading unexplored terrain to choose a settlement or movement target;
- reading invisible enemy units as if they were visible;
- reading hidden diplomatic state that the civilization should not know;
- using exact hidden enemy economic or military information without a legitimate game mechanism exposing it;
- routing through unknown information solely because the full game state is accessible internally.

Prefer existing view abstractions such as `CivView`, `ForeignCivView`, `MapUnitView`, and `TileView` where they correctly enforce information boundaries.

Do **not** create a new `AIWorldView` abstraction merely because it was once proposed. M0 did not establish that it is necessary. Introduce a dedicated fair-information facade only if a concrete hidden-state leak is demonstrated and the existing view boundaries cannot reasonably solve it.

### 4.3. Personality is not competence

Leader personality may influence strategic preference, but it must not intentionally make an AI incompetent.

A military-oriented civilization may prefer military options more often, and a science-oriented civilization may prefer science, but both should still perform basic economy, defense, expansion, and tactical play competently.

### 4.4. Improve the existing AI before replacing it

The current Unciv automation stack already contains substantial ruleset-aware behavior.

The default development model is:

```text
inspect → measure → improve or extend → benchmark → replace only if justified
```

Do not build a parallel AI engine unless evidence shows the existing architecture cannot support the required behavior.

Do not treat an upstream `TODO` comment as evidence that a subsystem should be replaced. Inspect the actual implementation, tests, and observed behavior first.

## 5. Current architecture assumptions from M0

Treat these as the current working baseline unless newer project evidence changes them:

- `GameInfo.nextTurn()` and `TurnManager` are stable turn orchestration and should remain intact unless a concrete need appears.
- `NextTurnAutomation.automateCivMoves()` is the main civilization AI orchestration point and should be **extended**, not replaced, in early milestones.
- `AutomationCache` is already correctly scoped per civilization turn and should be **composed with**, not discarded.
- `ThreatManager` provides useful local threat primitives.
- `ConstructionAutomation` is a useful scoring engine; improve its strategic inputs before considering replacement.
- `WorkerAutomation` is mature and test-covered; do not rewrite it without a demonstrated failure mode.
- `CityLocationTileRanker` already has reachability and fair-information awareness; do not rewrite it early without evidence.
- `UnitAutomation` is a useful per-unit execution layer; later military work should improve coordination above it.
- `BattleHelper` contains useful local combat valuation; later tactical work should add cross-unit coordination rather than immediately replacing local scoring.
- `chooseTechToResearch()` is a high-value future improvement target because it is relatively local and does not provide multi-step research-path planning.
- `automateCityConquer()` is evidence that localized unit coordination already fits the architecture, but it may eventually be subsumed by a more general tactical planner.

A complete-module rewrite requires evidence from source inspection, tests, scenarios, or benchmark results. Architectural preference alone is insufficient.

## 6. Milestone discipline

Work milestone by milestone.

Do not opportunistically implement later-milestone features just because nearby code makes them convenient.

Before editing, identify the active milestone and its acceptance criteria. If the requested change belongs to a later milestone, keep it out unless the user explicitly changes scope.

### Current milestone: M1 — Shared context and diagnostics

The current project milestone is **M1**.

M1 should:

- add or prepare shared per-turn AI context;
- compose with existing `AutomationCache` rather than replace it;
- add diagnostics and benchmark metadata needed for later work;
- improve observability of decisions and performance;
- preserve existing game/save behavior where practical;
- keep behavioral changes minimal or zero unless a change is required to support instrumentation correctly.

M1 should **not**:

- redesign combat AI;
- implement multi-step technology planning;
- add the full `StrategicDirector` behavior planned for later work;
- add persistent strategic memory to save files;
- introduce tactical search;
- redesign diplomacy;
- introduce project-specific difficulty/resource bonuses;
- perform unrelated refactors.

If M1 infrastructure would alter AI decisions, call that out explicitly before treating it as acceptable.

## 7. Before modifying code

Before editing AI-related code:

1. Read this `AGENTS.md`.
2. Read the current roadmap and milestone documentation available in the repo.
3. Inspect the existing implementation before proposing a replacement.
4. Identify direct callers and downstream consumers of the code being changed.
5. Identify tests already touching the relevant behavior.
6. Determine whether the proposed change is intended to change AI behavior or only infrastructure/diagnostics.
7. Identify the relevant fairness boundary: what information is legitimately available to the AI at that point?
8. Identify the expected performance impact, especially if work touches unit automation, pathfinding, city evaluation, tactical evaluation, or turn-level orchestration.

Do not begin with a rewrite solely because the current code is complex or heuristic-heavy.

## 8. Change discipline

Prefer changes that are small enough to understand and review.

- Minimize unrelated refactors.
- Do not mass-format the repository.
- Do not rename broad APIs merely for style while implementing an AI feature.
- Reuse existing abstractions when they fit.
- Keep behavior-preserving infrastructure changes separate from behavior-changing changes when practical.
- Avoid adding persistent save-state fields before they are actually required.
- Avoid introducing a new subsystem when a small extension point is sufficient.
- Preserve Desktop/Android shared behavior by keeping AI logic in `core` unless platform-specific code is genuinely required.

When adding a heuristic, make its purpose and tradeoff understandable from code structure, naming, or a concise comment. Avoid large unexplained numeric constants where a named parameter or documented rationale would be clearer.

## 9. Testing ladder

Use the cheapest reliable test that can answer the question, then move upward only when needed.

Default order:

```text
targeted unit test
    ↓
focused scenario/regression test
    ↓
headless/simulation smoke test
    ↓
statistical A/B benchmark
    ↓
minimal interactive/manual integration test when necessary
```

Unit and scenario tests should cover deterministic local behavior where possible. Global win rate should not be used to test a behavior that can be expressed as a focused scenario.

Examples of behaviors that deserve focused scenario tests include:

- retreat decisions;
- focus fire;
- siege positioning;
- city defense;
- unit blocking/reservation;
- worker choices;
- settler choices;
- technology path selection;
- production selection.

## 10. Manual / interactive testing policy

**Do not use normal full-game play as the default form of manual testing.**

Playing Unciv interactively from the start of a normal match is expensive in both wall-clock time and agent/tool resources, and it produces weak, noisy evidence for most AI changes.

When practical, replace normal play with one of these targeted methods:

- a focused unit test;
- a purpose-built scenario test;
- a small test map;
- a preconstructed save positioned immediately before the behavior under test;
- a dev-console setup;
- a scripted turn sequence;
- headless `ConsoleLauncher`/simulation execution;
- autoplay/fast-forward where interactive rendering is not the thing being tested;
- a reproducible minimal reproduction created specifically for the behavior.

If an interactive game test is genuinely necessary, keep it narrow:

1. start as close as possible to the target state;
2. load a prepared save or scenario instead of playing many setup turns;
3. observe only the behavior needed to answer the test question;
4. avoid repeated full matches through the GUI;
5. record the exact setup and result so the test can later be automated if valuable.

Normal manual play is appropriate only when the behavior cannot be validated reliably through a cheaper targeted method, such as certain UI/integration issues or emergent multi-system behavior that cannot yet be reproduced in a harness.

If a proposed manual test would require playing many ordinary turns before reaching the relevant state, first attempt to build or reuse a scenario, save, console setup, or headless harness that reaches that state directly.

## 11. Benchmark policy

Unciv's existing simulation/A-B infrastructure is the primary base for strength testing. Do not create an unrelated benchmark framework unless the existing one is shown to be insufficient.

### 11.1. Statistical A/B is currently authoritative

M0 established **statistical within-game A/B testing** as the authoritative measure of AI strength for the current baseline.

Default project batch sizes are:

```text
smoke batch       32 games
main A/B batch    256 games
```

Larger samples are allowed when variance is high or the expected effect is small. Smaller ad-hoc runs may be used for debugging, but they must not be presented as evidence of strength improvement.

A short smoke run answers questions such as:

- does the simulation complete;
- does the change crash;
- is there an obvious performance regression;
- are results structurally sane.

It does **not** establish that one AI is stronger than another.

### 11.2. Exact replay is not yet an oracle

M0 found that Unciv 5.0.1 is not established to be bit-for-bit deterministic under the tested replay conditions, even when starting from cloned game state.

Therefore:

- do not claim exact replay determinism unless it has been independently established;
- do not use a single replay mismatch as proof of an AI regression without further analysis;
- keep statistical A/B and exact replay/debugging as separate test modes;
- preserve the replay nondeterminism issue as an open engineering item until actually resolved.

### 11.3. Benchmark fairness

When comparing control and experiment AI:

- keep game rules and difficulty conditions identical;
- gate only the behavior under test into the experiment group;
- avoid changing multiple unrelated AI systems in the same A/B comparison;
- record which build/commit and benchmark configuration produced the result;
- use mirrored/fair map settings consistent with the project benchmark protocol;
- do not introduce an economic or information advantage to make the experiment group win more often.

## 12. Performance discipline

AI quality is not useful if turn processing becomes impractical, especially on Android.

Pay particular attention to code executed:

- for every unit;
- for every tile;
- for every candidate action;
- inside pathfinding loops;
- inside nested city/unit evaluations;
- once per civilization turn but with expensive world scans.

M0 confirmed that `automateUnits()` is the most expensive major part of `NextTurnAutomation`, with city automation also expensive. Treat new per-unit tactical work as performance-sensitive by default.

Prefer:

- per-turn caching;
- bounded candidate sets;
- local search instead of global exhaustive search;
- deterministic operation/count budgets over unbounded computation;
- reuse of computed threat/path/evaluation data.

For changes that may materially affect turn cost, compare against the M0 performance reference under the same or a documented equivalent setup.

Do not optimize by weakening fairness constraints or reading hidden state as a shortcut.

## 13. Randomness and reproducibility

Prefer state-based or otherwise explicitly controlled randomness when AI behavior needs randomness.

Do not introduce casual uses of ambient/random-default state into AI decisions without a clear reason.

When randomness is part of an AI choice:

- make the seed source understandable;
- avoid making benchmark results depend on incidental iteration order where practical;
- preserve the ability to diagnose why two runs diverged;
- do not assume that a fixed map seed alone makes the whole game deterministic.

## 14. Validation after changes

After editing:

1. run the narrowest relevant unit/scenario tests;
2. run broader tests when the change surface justifies it;
3. run a smoke simulation for AI infrastructure or behavior changes that can affect full turns;
4. run a main A/B benchmark when claiming a meaningful strength improvement;
5. compare performance when the change may affect turn cost;
6. verify that no project-specific resource or hidden-information advantage was introduced;
7. update project documentation when an architectural decision, benchmark protocol, milestone state, or accepted limitation changes.

Do not claim success based only on compilation if behavior changed.

Do not claim AI improvement from anecdotal gameplay alone.

## 15. Reporting requirements

When completing a coding task, report at least:

- files changed;
- purpose of each material change;
- whether AI behavior should change;
- tests/scenarios executed and their results;
- benchmark executed and sample size, if any;
- performance impact, if measured or relevant;
- fairness implications, if any;
- known limitations or unresolved questions;
- documentation updated, if applicable.

Be explicit when something was **not** tested.

Do not describe a smoke run as a statistically meaningful A/B result.

## 16. Git and branch discipline

Prefer dedicated project branches rather than developing directly on upstream/default branches.

A typical milestone branch may look like:

```text
advanced-ai/m1-context-diagnostics
advanced-ai/m2-strategy-economy
advanced-ai/m3-military-coordination
```

Keep commits conceptually focused where practical. Do not combine broad formatting/refactoring, benchmark-tool changes, and major AI behavior changes into one opaque commit.

Do not rewrite upstream history.

When integrating newer upstream changes later, treat that as an explicit integration event and rerun regression tests/benchmarks appropriate to the affected systems.

## 17. Actions requiring strong justification

Do not do any of the following merely because they appear cleaner or more sophisticated:

- replace the whole automation stack;
- create a parallel AI engine;
- introduce machine-learning/LLM runtime dependencies into normal AI turns;
- add persistent strategic memory to save files early;
- replace `AutomationCache` wholesale;
- bypass existing `*View` information boundaries;
- add unbounded tactical search;
- add project-specific difficulty bonuses;
- rewrite mature Worker/Settler logic without a concrete failure case;
- perform long normal-play manual tests when a targeted harness can answer the same question.

If one of these becomes necessary, document the evidence and rationale first.

## 18. Definition of done for M1 changes

An M1 change is not complete merely because it compiles.

For M1, completion should normally mean:

- relevant existing tests are green;
- new infrastructure has focused tests where appropriate;
- the 32-game smoke batch completes without correctness regressions for changes affecting AI turns;
- `automateCivMoves` performance is compared against the M0 reference when the change can affect runtime materially;
- no project-specific resource or hidden-information advantage is introduced;
- benchmark/diagnostic metadata is improved where the task touches observability;
- AI behavior remains unchanged or any behavioral difference is explicitly identified and justified;
- exact replay nondeterminism remains visible unless it has actually been resolved;
- relevant project documentation is updated if the architecture or accepted project state changed.

## 19. Guiding rule

When in doubt, prefer the change that is:

**fairer, smaller, measurable, testable, benchmarkable, and easier to compare against upstream.**

The project is not trying to make Unciv AI look sophisticated. It is trying to make the AI demonstrably stronger while still playing by the same rules.
