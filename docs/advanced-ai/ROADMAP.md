# Unciv Advanced AI — Living Roadmap

- Version: v1.2
- SHA-256: 384b667079e6f03f26efaa02b0a5bd8e59940c893efcf6a9a3ed000719e2c4e7
- Status: Living document / Last Known Good
- Baseline project: Unciv official
- Baseline release: 5.0.1
- Baseline commit: `82edba7216ae98319be36f09acc3661ede23c6c4`
- Target platforms: Desktop + Android
- Direction: Maximum AI improvement without resource or information cheating
- Last updated: 2026-11-20

## 1. Purpose

This file is the working roadmap for the Unciv Advanced AI project. It is intentionally a living document: architecture, priorities, milestones, and implementation details may be revised when code inspection, benchmarks, testing, or integration work produces better evidence.

The document should remain useful in two roles:

1. A stable record of decisions that have already been made.
2. A controlled place to revise assumptions and implementation direction as the project evolves.

The roadmap is not meant to freeze technical details too early. Items marked as provisional, experimental, or open may change without implying that the project has failed or changed its core goal.

## 2. Core goal

Build a substantially stronger AI for official Unciv that is difficult because it makes better decisions, not because it receives hidden economic, production, science, military, diplomatic, or information advantages.

The target behavior is an AI that can:

- develop an empire with a coherent long-term plan;
- adapt research and production to current strategic needs;
- coordinate military units instead of treating them as mostly independent actors;
- conduct more effective offense and defense;
- use diplomacy and war timing more deliberately;
- react to opponents and changing victory conditions;
- remain playable offline on both Desktop and Android.

## 3. Non-negotiable principles

### 3.1. No resource cheating

Advanced AI must not receive project-specific bonuses such as free gold, science, culture, production, units, reduced costs, or other hidden material advantages.

Existing Unciv difficulty mechanics remain part of the base game and are not redefined by this project, but the Advanced AI itself must not introduce additional resource advantages as a substitute for intelligence.

### 3.2. No information cheating

The AI should make decisions from information legitimately available to it through explored territory, visible units, known cities, diplomacy, remembered observations, and other information exposed by fair game-state abstractions.

Direct access to hidden opponent state should not be used to improve decision quality.

### 3.3. Personality is not competence

Leader personality may affect strategic preferences and style, but it should not intentionally make a civilization incompetent in unrelated areas.

A militaristic leader and a scientific leader may prioritize differently, while both should still manage cities, defend themselves, and use units competently.

### 3.4. Improve incrementally instead of rewriting everything

The project should preserve and reuse existing Unciv automation when it is already effective.

The preferred strategy is:

> inspect → measure → improve or replace only where justified → benchmark again

A parallel AI engine should not be created unless later evidence shows that the existing architecture cannot support the required behavior.

### 3.5. Performance matters

The AI must remain practical on Android as well as Desktop. Expensive search should therefore be bounded and applied selectively instead of globally.

## 4. Baseline and repository strategy

The initial baseline is official Unciv **5.0.1**, pinned to commit:

`82edba7216ae98319be36f09acc3661ede23c6c4`

M0 additionally pinned the corresponding Git tree:

`6412accbc5ffcd960bbfce7d4af0cbacd7b87bb7`

The verified official 5.0.1 `Unciv.jar` used for the M0 executable baseline has SHA-256:

`e99dff28144a3585791019cb3b6f85161890f3b86404ab41c033739a8726321d`

The project should not develop directly against a moving `master` branch because changing upstream code during benchmarking would make comparisons harder to reproduce.

Recommended repository model:

```text
Unciv upstream
     │
     ├── pinned baseline: 5.0.1
     │
     └── Advanced AI development branch/fork
              │
              ├── AI changes
              ├── tests
              ├── benchmark tooling
              └── periodic upstream merges
```

Upstream changes may be merged later, but only after regression testing against the current Advanced AI baseline.

## 5. Current understanding of Unciv AI architecture

The existing AI is already modular. The main turn-level entry point is:

```text
NextTurnAutomation.automateCivMoves()
```

Its responsibilities include, among other things:

```text
Diplomacy / war / trade
        ↓
Technology choice
        ↓
City bombardment
        ↓
Gold spending
        ↓
Unit automation
        ↓
City automation
        ↓
Settler / voting / other turn actions
```

Existing code already contains dedicated automation for areas such as:

- diplomacy;
- war declaration evaluation;
- threat analysis;
- trade;
- religion;
- city construction;
- civilian units;
- workers;
- air units;
- battle decisions;
- city placement;
- simulation and A/B testing.

This means the Advanced AI should initially be implemented as an evolutionary extension of `logic/automation`, not as a separate replacement engine.

## 6. Target architecture

### 6.1. High-level structure

```text
                 StrategicDirector
               "What are we trying to do?"
                         │
                         ▼
                  AdvancedAIContext
             Shared strategic turn context
                         │
        ┌────────────────┼────────────────┐
        ▼                ▼                ▼
     Economy          Expansion         Military
        │                │                │
        ├─────── Tech / City ─────────────┤
        │                                 │
        └──────── Diplomacy / War ─────────┘
                         │
                         ▼
                Existing Unciv APIs
```

The architecture should add stronger shared planning above existing decision modules rather than forcing each module to rediscover the whole game state independently.

### 6.2. AdvancedAIContext

`AutomationCache` already acts as a per-turn cache. The proposed direction is to extend or compose it with an `AdvancedAIContext` containing reusable analysis for that turn.

Provisional contents:

```text
AdvancedAIContext
├── strategicState
├── economicSituation
├── militarySituation
├── opponentModels
├── cityRoles
├── objectives
└── tacticalReservations
```

The context should be computed once where practical and reused by downstream automation.

### 6.3. StrategicDirector

The `StrategicDirector` should describe priorities instead of directly executing actions.

Example conceptual state:

```text
Science priority      0.85
Economy priority      0.72
Military priority     0.58
Culture priority      0.30
Expansion priority    0.21

Military posture      Defensive
Primary objective     Stabilize frontier
Victory direction     Science
```

Priorities should generally be soft weights rather than rigid mutually exclusive modes.

### 6.4. Personality integration

Current Unciv personality values include economic and behavioral preferences such as Production, Food, Gold, Science, Culture, Military, Aggressive, DeclareWar, Diplomacy, Expansion, and others.

The intended relationship is:

```text
Personality = preferred style
Strategy    = response to the current game state
Competence  = quality of execution
```

Strategic urgency should be allowed to override personality preference when necessary for survival or victory prevention.

## 7. Priority improvement areas

### 7.1. Technology planning

Current technology selection is largely local and weighted around researchable technologies and cost groups.

The Advanced AI should move toward short bounded research-path evaluation, for example two to five technologies ahead when useful.

Candidate scoring factors:

```text
Tech score =
    immediate value
  + unlock value
  + strategic alignment
  + military urgency
  + resource access
  + victory progress
  - research cost
  - opportunity cost
```

This is a planned direction, not a final formula.

### 7.2. City specialization and construction

Existing `ConstructionAutomation` should be retained unless testing shows a strong reason to replace it.

The preferred improvement is to feed it stronger strategic context and soft city roles such as:

```text
Production hub
Science hub
Military production
Border fortress
Growth city
Culture city
Mixed role
```

A city may have several weighted roles rather than exactly one fixed label.

### 7.3. Military operations

Military improvement is expected to be one of the highest-impact areas.

The intended hierarchy is:

```text
MilitarySituationAnalyzer
          ↓
OperationalPlanner
          ↓
TacticalPlanner
          ↓
Existing UnitAutomation / BattleHelper
```

The main conceptual change is to shift from only asking:

> What should this unit do?

and also ask:

> What is this force trying to accomplish this turn and over the next few turns?

Potential operational objectives include:

- defend a threatened city;
- assemble for an attack;
- capture a specific enemy city;
- reinforce a front;
- protect civilian units;
- withdraw damaged forces;
- maintain a reserve.

### 7.4. Tactical coordination

Early tactical improvements should focus on coordination before expensive search.

Priority order:

1. target prioritization;
2. focus fire;
3. attack ordering;
4. tile reservation and blocking avoidance;
5. ranged and siege positioning;
6. retreat and survival behavior;
7. local tactical search where useful.

Global beam search over an entire army should not be the initial implementation because of combinatorial cost.

If search is introduced, it should initially be bounded to local combat hotspots with a limited number of participating units and candidate actions.

### 7.5. Diplomacy and opponent modeling

Later phases may add stronger opponent modeling based on legitimately observed information, such as:

- military buildup near borders;
- historical aggression;
- prior war outcomes;
- visible strategic direction;
- repeated diplomatic behavior.

Persistent strategic memory across save/load is explicitly deferred until the save-format impact has been evaluated.

## 8. Fair-information layer

A dedicated fair-information abstraction should be considered if existing view classes are not sufficient to guarantee information discipline.

Provisional name:

```text
AIWorldView
```

It should expose only information that the AI may legitimately use, preferably through existing abstractions such as:

```text
CivView
ForeignCivView
MapUnitView
TileView
```

This section is a design constraint and may be revised after a full code audit of information access patterns.

## 9. Compute-budget model

AI strength should not be tied to extra resources. If multiple AI thinking levels are introduced, they should differ primarily in computational effort.

Provisional concept:

```text
AI_FAST
    heuristic decisions only or minimal local search

AI_STANDARD
    moderate tactical analysis

AI_MAXIMUM
    larger candidate set and deeper bounded search
```

This is a proposed feature, not yet a committed implementation detail.

Desktop and Android should share the same core logic. Platform-specific tuning may change search budgets, but should not change the fundamental rules or fairness guarantees.

## 10. Testing and benchmark strategy

Unciv already provides simulation support and A/B testing hooks for AI development. The project should build on this instead of creating an unrelated benchmark framework from scratch.

### 10.1. Test ladder

Every significant AI change should move through the following ladder:

```text
Unit tests
    ↓
Scenario tests
    ↓
20–50 smoke simulations
    ↓
200–400 A/B simulations
    ↓
Performance regression checks
```

The 200–400 simulation range is the current target for major A/B comparisons because Unciv's own developer documentation identifies that range as a useful baseline.

M0 established **statistical within-game A/B testing** as the authoritative strength test for the current project baseline. The default operational batch sizes are now **32 games for smoke** and **256 games for a main A/B comparison**, with larger runs allowed when variance or a small effect size requires them.

Exact replay is a separate debugging/regression mode. M0 found that bit-for-bit replay determinism is not established in Unciv 5.0.1 even when starting from cloned game state, so exact replay must not be treated as an oracle until that issue is isolated. Detailed evidence and the benchmark protocol are recorded in `docs/advanced-ai/M0_BASELINE.md`.

### 10.2. Core metrics

Initial metrics should include:

- win rate;
- victory type;
- victory turn;
- population over time;
- production over time;
- city count over time;
- average city population;
- AI turn processing time.

Additional metrics should be added as implementation reaches the relevant systems, especially:

- units killed and lost;
- military value destroyed versus lost;
- cities captured and lost;
- idle military turns;
- idle settler/worker turns;
- strategic objective completion;
- damage dealt and received;
- production and research efficiency.

Not every metric must exist in the first benchmark implementation.

## 11. Milestone roadmap

### M0 — Baseline and AI map

**Status: Completed on 2026-11-20.** Detailed evidence is recorded in `docs/advanced-ai/M0_BASELINE.md`. Exact replay determinism remains an open benchmark-engineering issue, but it does not block statistical A/B comparison.

Goals:

- pin Unciv 5.0.1;
- map the complete AI call graph relevant to normal civilization turns;
- identify major decision points;
- classify existing logic as keep / improve / replace / investigate;
- run baseline simulations;
- establish reproducible benchmark settings.

Exit condition:

A documented baseline exists and future changes can be compared against it. **Met through the pinned 5.0.1 control and Unciv's statistical experiment/control mechanism.**

### M1 — Shared context and diagnostics

**Status: Completed on 2026-11-20.**

`NextTurnAutomation` creates the turn-local `AutomationCache` just after constructing the civilization view. The cache holds an `AdvancedAIContext` with the turn, civilization ID, and own city count. Existing cache data is still populated on demand during unit automation. The optional diagnostics sink receives a structured completion record when enabled.

Simulation records the build revision, configuration, and group assignments for the run, plus the map seed, game ID, outcome, duration, and requested statistics for each game. It samples checkpoints at turn boundaries and reports final statistics for both wins and draws.

Build the desktop JAR with `./gradlew :desktop:dist`. From `android/assets`, launch `com.unciv.app.desktop.ConsoleLauncher --smoke` with that JAR on the classpath. Set `-Dunciv.benchmark.revision` to the build's Git revision and use `-DonlyLog=Timers` for timing output. The preset runs 32 games across four threads on the mirrored M0 scenario, with a 100-turn cap and checkpoints at turns 25, 50, and 75.

Validation on Windows with OpenJDK 21 recorded 1,002 passing JVM tests and 25 skipped tests. All 32 smoke games reached turn 100 as draws and produced final statistics for both civilizations. The run recorded 32 game IDs and 31 distinct map seeds. `automateCivMoves` averaged 27.90 ms across 6,400 calls, with a p95 of 82.18 ms. M0's four-game reference averaged 28.12 ms, with a p95 of 68.44 ms. The sample sizes and run conditions limit direct performance comparisons. Exact replay determinism remains open.

Goals:

- introduce the minimum shared Advanced AI context needed for later work;
- avoid changing behavior more than necessary;
- add decision diagnostics where useful;
- measure performance overhead.

Exit condition:

The architecture can carry shared strategic information without causing significant regression or instability.

### M2 — Strategy, technology, and city planning

Goals:

- implement the first `StrategicDirector` behavior;
- improve short-horizon technology planning;
- introduce city-role weighting;
- feed strategic priorities into production decisions.

Exit condition:

Economic and development behavior improves measurably without relying on extra resources.

### M3 — Military operational coordination

Goals:

- analyze fronts and threats;
- create operational objectives;
- assign units to objectives;
- improve reinforcement, defense, attack concentration, and retreat behavior.

Exit condition:

Military units act more coherently as groups and waste fewer turns or units.

### M4 — Tactical coordination and local search

Goals:

- improve focus fire and attack order;
- add movement/tile reservation where needed;
- improve ranged and siege positioning;
- introduce bounded local tactical search only where benchmarks justify it.

Exit condition:

Combat efficiency improves while Android performance remains acceptable.

### M5 — Diplomacy and adaptation

Goals:

- improve long-term diplomacy evaluation;
- add opponent modeling based on fair observations;
- adapt strategic priorities to game developments and opponent progress.

Exit condition:

AI reacts more effectively to changing strategic situations instead of following mostly static preferences.

### M6 — Integration, optimization, and release preparation

Goals:

- regression-test completed systems together;
- profile Desktop and Android;
- tune compute budgets;
- validate save compatibility decisions;
- document build and installation paths;
- prepare release artifacts when the project is sufficiently stable.

Exit condition:

A stable Advanced AI build can be tested and used on both target platforms.

## 12. Current decision register

### Confirmed

- Base game: official Unciv.
- Direction: maximum AI improvement.
- Platforms: Desktop and Android.
- Initial baseline: Unciv 5.0.1.
- Development model: source fork/branch, not a JSON-only mod.
- AI must not gain project-specific resource advantages.
- AI must not gain hidden-information advantages.
- Existing Unciv automation should be reused where appropriate.
- Benchmarking should be continuous, not postponed until the end.
- Military coordination is a major priority.
- M0 architectural/statistical baseline is complete and documented in `docs/advanced-ai/M0_BASELINE.md`.
- `NextTurnAutomation` remains the primary integration/orchestration point; the existing automation stack will not be wholesale replaced.
- Existing `AutomationCache` functionality should be preserved and composed with later shared strategic context rather than replaced.
- Technology planning is an early high-value improvement target; current technology choice is primarily local/cost-group weighted.
- Existing `UnitAutomation`/`BattleHelper` should remain the execution/local-evaluation layer while M3/M4 add force-level coordination above them.
- Statistical within-game A/B is the authoritative AI-strength test until exact replay determinism is established.
- Default benchmark batch sizes are 32 games for smoke and 256 games for a main A/B comparison.

### Provisional

- `AdvancedAIContext` as the shared turn-level planning structure.
- `StrategicDirector` as the strategic layer.
- short bounded technology-path planning;
- soft city-role weights;
- operational objectives for groups of units;
- bounded local tactical search;
- AI_FAST / AI_STANDARD / AI_MAXIMUM compute-budget profiles;
- an explicit `AIWorldView` fairness abstraction if existing view classes are insufficient.

### Deferred

- persistent strategic memory across save/load;
- major save-format changes;
- any machine-learning or LLM dependency;
- large-scale global tactical search;
- replacement of the entire existing Unciv automation stack.

## 13. Open questions to resolve during implementation

These are intentionally not decided yet:

1. What causes the replay nondeterminism observed in M0, and what minimum harness change is required for reliable exact replay?
2. What is the smallest clean composition boundary between existing `AutomationCache` and the later shared `AdvancedAIContext`?
3. How much strategic state can remain transient before persistent memory becomes necessary?
4. What exact information boundaries are already enforced by current `*View` abstractions, and are there concrete hidden-state leaks that justify a separate `AIWorldView`?
5. What tactical search depth and candidate count remain practical on representative Android hardware?
6. Which new benchmark metrics provide enough signal to justify their instrumentation cost?
7. What benchmark metadata/logging changes are needed before M1 experiments so results remain auditable across builds?
8. How frequently should upstream changes be merged after development begins?

Answers should be based on code inspection and measurements, not assumptions.

## 14. Change policy for this roadmap

This document is expected to change.

When updated:

- preserve confirmed decisions unless there is evidence and an explicit reason to revise them;
- move items between Confirmed, Provisional, Deferred, or Rejected as evidence develops;
- record architecture changes instead of silently replacing earlier direction;
- keep milestones aligned with the actual implementation state;
- update the baseline if and only if the project deliberately rebases to a newer Unciv version;
- increase the document version for each completed revision session;
- update the changelog below.

A roadmap change does not automatically imply a code change, and a code experiment does not automatically become roadmap policy until it has been reviewed.

## 15. Changelog

### v1.2 — 2026-11-20

Recorded the M1 context lifecycle, optional diagnostics, simulation metadata, draw reporting, and validation results. Updated M0 references to the repository path.

### v1.1 — 2026-11-20

M0 completed and reconciled into the living roadmap.

Key updates:

- pinned the 5.0.1 Git tree and verified official JAR identity;
- recorded M0 as completed through a statistical A/B baseline;
- confirmed `NextTurnAutomation`/existing automation as the evolutionary integration path;
- promoted technology planning and military coordination findings from source audit;
- established 32-game smoke and 256-game main A/B defaults;
- separated statistical A/B from exact replay testing;
- recorded replay nondeterminism as an explicit open engineering issue;
- linked the detailed `docs/advanced-ai/M0_BASELINE.md` evidence report.

### v1 — 2026-11-20

Initial living roadmap created from the project decisions and source-code investigation completed before implementation began.

Key contents established:

- project goal and fairness constraints;
- official Unciv 5.0.1 baseline;
- evolutionary architecture around existing automation;
- shared strategic context and `StrategicDirector` direction;
- technology, city, military, tactical, and diplomacy priorities;
- benchmark ladder;
- milestone sequence M0–M6;
- confirmed, provisional, deferred, and open decision registers.
