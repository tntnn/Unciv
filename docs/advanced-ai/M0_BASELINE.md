# Unciv Advanced AI — M0 Baseline and AI Map

- Version: v1
- SHA-256: f9c08068763cc64c5c4df251437c11f4eeb5166956b8fef87aa6004741d7b5b4
- Status: Last Known Good
- Baseline project: Unciv official
- Baseline release: 5.0.1
- Baseline commit: `82edba7216ae98319be36f09acc3661ede23c6c4`
- Baseline tree: `6412accbc5ffcd960bbfce7d4af0cbacd7b87bb7`
- Date: 2026-11-20

## 1. M0 scope and result

M0 establishes the reference point from which later Advanced AI changes will be judged. It does not attempt to improve AI behavior yet.

The milestone work covered:

- pinning the exact official source/release baseline;
- verifying the baseline build and upstream test status;
- mapping the normal civilization AI turn call graph;
- inventorying the major decision points;
- classifying existing logic as KEEP / IMPROVE / REPLACE / INVESTIGATE;
- auditing the current fairness boundary where it is visible in the automation code;
- executing the official simulation path from the 5.0.1 release artifact;
- measuring a small smoke baseline;
- auditing the official A/B harness and its reproducibility properties;
- defining the benchmark protocol that later milestones should use.

### M0 status

**M0 is complete as an architectural and statistical baseline.** Future AI changes can be compared against the pinned 5.0.1 control through Unciv's existing within-game A/B mechanism.

One limitation remains explicitly open: **bit-for-bit replay determinism is not established in 5.0.1**. This does not block statistical A/B testing, but it does block treating a single replay as an exact regression oracle. Until that is fixed, exact replay and statistical A/B must be treated as different test modes.

## 2. Pinned baseline

### 2.1. Source identity

| Item | Baseline |
|---|---|
| Repository | `yairm210/Unciv` |
| Release/tag | `5.0.1` |
| Commit | `82edba7216ae98319be36f09acc3661ede23c6c4` |
| Git tree | `6412accbc5ffcd960bbfce7d4af0cbacd7b87bb7` |
| Release date | 2026-11-17 |
| Project baseline date | 2026-11-20 |

The project should continue to use this commit as the control until an explicit rebase decision is made.

### 2.2. Verified official artifact

The official 5.0.1 GitHub Actions `Deploy` run produced an `UncivJar` artifact. It was downloaded and executed headlessly for M0.

| Artifact | SHA-256 |
|---|---|
| `Unciv.jar` | `e99dff28144a3585791019cb3b6f85161890f3b86404ab41c033739a8726321d` |
| GitHub Actions `UncivJar` ZIP | `beea58f259ce77909944fe2974ba6b8f0be6874ebe896891a31c90b619133d65` |

The JAR contains the desktop launcher, `ConsoleLauncher`, simulation classes, automation classes, and the bundled Civ V ruleset assets needed for headless simulation.

### 2.3. Upstream validation state

For baseline commit `82edba7...`, GitHub Actions reports:

- `Build and test`: success;
- job `Check code and run unit tests`: success;
- `Detekt`: success;
- release `Deploy`: success;
- Android build: success;
- multiplatform build: success.

This does not replace our own regression testing, but it establishes that the pinned baseline itself was green upstream.

## 3. Normal AI turn call graph

### 3.1. Turn entry

The normal AI path starts in `GameInfo.nextTurn()` and flows through `TurnManager`:

```text
GameInfo.nextTurn()
    │
    ├─ TurnManager.startTurn()
    │
    ├─ TurnManager.automateTurn()
    │      │
    │      └─ NextTurnAutomation.automateCivMoves()
    │
    ├─ victory / simulation stop checks
    │
    └─ TurnManager.endTurn()
```

Source anchors at 5.0.1:

- `core/src/com/unciv/logic/GameInfo.kt` — `nextTurn()` around line 384;
- `core/src/com/unciv/logic/civilization/managers/TurnManager.kt` — `automateTurn()` around line 376;
- `core/src/com/unciv/logic/automation/civilization/NextTurnAutomation.kt` — `automateCivMoves()` around line 47.

### 3.2. Civilization-level orchestration

The 5.0.1 `NextTurnAutomation.automateCivMoves()` order is:

```text
respondToPopupAlerts
    ↓
TradeAutomation.respondToTradeRequests
    ↓
major-civ diplomacy / war / religion / trade actions
    ├─ declareWar
    ├─ offerPeaceTreaty
    ├─ askForHelp
    ├─ offerDeclarationOfFriendship
    ├─ spendFaithOnReligion
    ├─ denounce
    ├─ embassy / open borders / research agreement / defensive pact
    ├─ border military-presence check
    ├─ exchangeLuxuries
    ├─ issueRequests
    ├─ adoptPolicy
    └─ freeUpSpaceResources
    ↓
chooseTechToResearch
    ↓
automateCityBombardment
    ↓
UseGoldAutomation.useGold
    ↓
city-state protect / bully logic
    ↓
create AutomationCache
    ↓
automateUnits              ← source comment: most expensive part
    ↓
religious belief choice / espionage
    ↓
automateCities             ← source comment: second most expensive
    ↓
trainSettler
    ↓
tryVoteForDiplomaticVictory
```

This confirms that Unciv already has a useful top-level orchestration point. Advanced AI should initially extend it rather than create a parallel turn engine.

## 4. Major decision-point inventory

### 4.1. Strategy, diplomacy, and war

Existing modules already separate a meaningful amount of strategic logic:

- `DiplomacyAutomation`
  - friendship;
  - embassy;
  - open borders;
  - research agreements;
  - defensive pacts;
  - declare war;
  - peace offers;
  - requests for help;
  - border-pressure reaction;
  - denouncement.
- `MotivationToAttackAutomation`
  - force comparison;
  - allied/protector strength;
  - relationships;
  - production/score ratios;
  - defensive-pact effects;
  - attack-path feasibility;
  - city attackability.
- `DeclareWarPlanEvaluator`
  - direct war;
  - preparation for war;
  - team war;
  - join war;
  - ask another civilization to join.
- `DeclareWarTargetAutomation`
  - target and war-plan selection.
- `ThreatManager`
  - nearest enemy distance;
  - local enemy tiles;
  - dangerous tiles;
  - neighboring civilization/city relations.

M0 conclusion: **the current war/diplomacy layer is more mature than a simple aggression heuristic**. Replacing it early would discard useful behavior and increase regression risk.

### 4.2. Technology choice

`NextTurnAutomation.chooseTechToResearch()` is comparatively local:

- researchable technologies are grouped by cost;
- free technology selection favors the most expensive eligible cost group;
- normal research generally considers the cheapest group and sometimes the second-cheapest group;
- selection inside the candidate group is weighted by `getWeightForAiDecision()`.

This is efficient and ruleset-aware, but it is not a research-path planner. It cannot deliberately accept a weak immediate technology because it unlocks a strategically critical technology several steps later.

M0 conclusion: **high-value IMPROVE target for M2**.

### 4.3. Policy choice

`adoptPolicy()` already uses branch priorities and tends to finish incomplete high-priority branches. It is not obviously broken from source inspection alone.

The source contains a performance TODO near the call site (`adoptPolicy` can reportedly take a second), so this remains an **INVESTIGATE** item rather than a rewrite target.

### 4.4. City construction

`ConstructionAutomation` is already a generic scoring engine. It evaluates, among other things:

- buildings and wonders;
- military units;
- workers;
- work boats;
- spaceship parts;
- city production level;
- war state;
- unit supply;
- gold state;
- personality modifiers;
- building stat deltas;
- military/victory effects;
- remaining production work.

The final non-perpetual choice is effectively selected by relative value over remaining work.

M0 conclusion: **KEEP the evaluator core, IMPROVE its strategic inputs in M2**. The first improvement should be city-role/strategic weighting, not a rewrite.

### 4.5. Settlers and expansion

`SpecificUnitAutomation.automateSettlerActions()` delegates location scoring to `CityLocationTileRanker` and already includes:

- a bounded search radius;
- first-city special handling;
- dangerous-tile avoidance;
- reachability checks;
- distance penalties;
- frontier/exploration fallback.

`CityLocationTileRanker` explicitly uses explored tiles/cities and estimates unexplored value rather than reading hidden terrain directly.

M0 conclusion: **KEEP initially; INVESTIGATE later as part of strategic expansion planning**.

### 4.6. Workers

`WorkerAutomation` is a large dedicated subsystem with tile selection, improvement ranking, roads, resource handling, and safety checks. Existing tests exercise many worker behaviors across multiple turns.

M0 conclusion: **KEEP** unless later benchmarks identify a concrete failure mode. It is not a priority rewrite target.

### 4.7. Unit execution pipeline

`UnitAutomation.automateUnitMoves()` is an ordered heuristic chain. For a normal land military unit, the broad sequence is:

```text
escape terrain damage
    ↓
civilian/special handling
    ↓
nuclear / air specialization
    ↓
escort settler or great person
    ↓
ruin handling
    ↓
retreat / heal if badly damaged
    ↓
defend damaged own city
    ↓
disembark into attack position
    ↓
attack nearby target
    ↓
retake captured city
    ↓
head toward enemy city
    ↓
barbarian encampment
    ↓
garrison
    ↓
heal
    ↓
advance toward close enemy
    ↓
prepare against hostile border
    ↓
explore / fog bust
```

This is a strong execution layer for individual units. The weakness is architectural: each unit is still mostly answering **“what should I do now?”** rather than consuming a shared operational plan.

M0 conclusion: **KEEP the executor; IMPROVE coordination above it in M3/M4**.

### 4.8. Local battle choice

`BattleHelper` already scores attacks using projected damage, enemy health, capture possibility, city health, siege/ranged roles, and some nearby-unit context.

This is useful local tactical logic. The main missing capability is cross-unit coordination:

- target reservation;
- focus fire;
- attack order;
- movement/tile reservation;
- coordinated ranged/siege positioning;
- force-level retreat or assault intent.

M0 conclusion: **KEEP local combat valuation initially; add a TacticalPlanner above it**.

### 4.9. Existing partial coordination

`NextTurnAutomation.automateCityConquer()` is important because it shows that Unciv already has a local special-case form of coordination:

- detects enemy cities with a concentration of friendly units;
- sends air units first;
- directs siege toward the city;
- orders melee by force;
- biases attacks toward surrounded enemies to exploit flanking and concentrated hits.

This is not a general tactical planner, but it is evidence that coordinated behavior fits naturally into the existing architecture.

M0 conclusion: **IMPROVE first, then likely subsume/replace this localized special case once M3/M4 tactical planning is mature**.

## 5. KEEP / IMPROVE / REPLACE / INVESTIGATE classification

| Area / component | M0 classification | Rationale | Earliest target |
|---|---|---|---|
| `GameInfo.nextTurn` / `TurnManager` AI entry | KEEP | Stable game-turn orchestration | none |
| `NextTurnAutomation` top-level role | KEEP + EXTEND | Correct central integration point | M1 |
| `AutomationCache` existing caches | KEEP + COMPOSE | Already scoped per civilization turn | M1 |
| `ThreatManager` | KEEP | Useful localized threat/cache primitives | M1/M3 |
| `chooseTechToResearch` | IMPROVE | Local cost-group choice lacks path planning | M2 |
| `adoptPolicy` | INVESTIGATE | Existing branch logic is nontrivial; performance TODO exists | M2 or later |
| `ConstructionAutomation` evaluator | KEEP + IMPROVE INPUTS | Good generic scoring; lacks shared strategy/city role | M2 |
| `CityLocationTileRanker` | KEEP | Mature, reachability-aware and fair-info conscious | later |
| `WorkerAutomation` | KEEP | Large mature subsystem with direct tests | later |
| `UnitAutomation` execution chain | KEEP + EXTEND | Good per-unit executor, weak force coordination | M3 |
| `BattleHelper` local attack score | KEEP + EXTEND | Useful local valuation, no multi-unit plan | M3/M4 |
| `HeadTowardsEnemyCityAutomation` | IMPROVE | Useful pathing, target intent should come from operations layer | M3 |
| `automateCityConquer` | IMPROVE → LOCAL REPLACE CANDIDATE | Partial coordination should eventually be generalized | M3/M4 |
| `AirUnitAutomation` | KEEP | Specialized domain logic; integrate with objectives later | M3/M4 |
| `DiplomacyAutomation` | KEEP + INVESTIGATE | Already broad; strategic integration should precede rewrite | M5 |
| war motivation/plan evaluators | KEEP + INVESTIGATE | Existing force/path/diplomacy analysis is valuable | M5 |
| `TradeAutomation` | KEEP | No M0 evidence justifying replacement | later |
| `UseGoldAutomation` | KEEP + INVESTIGATE | Functional budget use; strategy-aware budgeting may improve it later | M2/M5 |
| `ReligionAutomation` | KEEP | Dedicated ruleset-aware logic; not core early bottleneck | later |
| `EspionageAutomation` | KEEP | Specialized subsystem; not early priority | later |
| whole automation stack | DO NOT REPLACE | No evidence supports a wholesale rewrite | n/a |

**M0 does not justify replacing any complete major module.** The first “replace” candidate is only a localized tactical special case after a general coordinator exists.

## 6. Fair-information audit

The current codebase already contains several explicit information-discipline choices:

- settler ranking operates on explored information;
- workboat/path logic contains an explicit anti-cheating check against routing through unexplored terrain;
- `UnitAutomation.tryPrepare()` explicitly states that only our side of diplomacy flags is known and avoids reading the opponent's hidden flags;
- gold-based tile purchasing checks visibility and resource visibility;
- view abstractions such as `CivView`, `ForeignCivView`, `MapUnitView`, and `TileView` are already used heavily by automation.

### M0 fairness decision

The provisional roadmap idea of creating an `AIWorldView` remains **provisional, not confirmed**.

Before adding another abstraction, M1/M2 should audit actual hidden-state access. If the existing `*View` boundaries plus disciplined call sites are sufficient, a new layer would add complexity without improving fairness. If concrete leaks are found, a dedicated fair-information facade becomes justified.

## 7. Benchmark harness audit

### 7.1. Official simulation configuration

The 5.0.1 `desktop/.../ConsoleLauncher.kt` AI simulation setup uses:

- base ruleset: Civ V — Gods & Kings;
- two generic civilizations with no uniques (`SimulationCiv1`, `SimulationCiv2`);
- difficulty: King;
- map size: Tiny;
- no city-states;
- no barbarians;
- no ruins;
- no natural wonders;
- legendary start;
- strategic balance;
- mirrored map around the center tile;
- slightly reduced water threshold;
- default speed;
- spectator human player;
- all available victory types;
- randomized player order in `Simulation`.

This is a sensible control scenario because it suppresses civilization uniques and several major sources of variance while preserving a real game.

### 7.2. Existing A/B mechanism

Unciv's developer documentation provides a direct experiment mechanism:

```text
Simulation(..., civIdsInExperimentGroup = setOf(...))
```

Changed code can be gated by membership in:

```text
DebugUtils.CIV_IDS_IN_EXPERIMENT_GROUP
```

The simulation output already includes experiment/control group win rate and a one-tail binomial p-value when an experiment group is configured.

The official developer documentation recommends approximately **200–400 simulations** as a useful baseline for detecting AI changes.

### 7.3. Existing metrics

`Simulation` / `SimulationStep` already track:

- wins;
- victory type;
- average victory turn by victory type;
- population at requested turns and game end;
- production at requested turns and game end;
- city count;
- average city population;
- turns per second;
- elapsed duration.

Several intermediate metrics exist but are hidden by print flags in the current implementation.

### 7.4. Harness limitation: draw-only reporting

In 5.0.1, per-civilization detail printing is skipped when a civilization has zero wins. In a short smoke run where every game draws, population/production/city detail is therefore not printed even though the structures are present.

This is a benchmark-output limitation, not an AI behavior issue. It should be corrected when benchmark tooling is patched.

## 8. M0 executed smoke baseline

### 8.1. Environment

The official 5.0.1 JAR was executed headlessly with the bundled assets. A custom launcher reproduced the official `ConsoleLauncher` scenario while reducing the run size for a smoke test.

Smoke configuration:

```text
simulations            4
simulation threads     4
maximum turns          100
stat turns             25, 50, 75, 100
ruleset                 Civ V - Gods & Kings
civilizations           2 generic civs, no uniques
map                     Tiny, mirrored
barbarians              off
city-states             0
ruins                    off
natural wonders         off
difficulty              King
```

### 8.2. Smoke results

- Completed simulations: 4 / 4.
- Result: 4 draws at the 100-turn cap.
- Harness-reported aggregate speed after completion: **30.8 turns/s**.
- Total wall time reported by the harness: **13.348 s**.
- Harness-reported average game duration: **3.337 s**; because simulations ran concurrently, this figure is elapsed wall time divided by tallied games and should not be interpreted as single-game CPU duration.

Conclusion: 100 turns is useful for smoke/performance checks but is too short to provide a meaningful victory-rate baseline in this scenario.

### 8.3. AI timing sample

From the same smoke batch, the built-in `Timers` instrumentation reported for `automateCivMoves`:

| Metric | Value |
|---|---:|
| calls | 804 |
| mean | ~28.12 ms |
| p50 | ~21.51 ms |
| p90 | ~54.19 ms |
| p95 | ~68.44 ms |
| p99 | ~98.47 ms |
| max | ~265.87 ms |

`automateWorkerAction` in the same batch reported:

| Metric | Value |
|---|---:|
| calls | 263 |
| mean | ~3.14 ms |
| p50 | ~1.09 ms |
| p95 | ~14.73 ms |
| max | ~27.92 ms |

These numbers are **environment-specific smoke references**, not universal performance targets. Their main value is that later M1 changes can be checked for obvious order-of-magnitude regressions under the same local setup.

### 8.4. Longer pilot

A separate 150-turn exploratory batch began to produce domination outcomes (one observed domination victory at turn 121), but the complete batch did not finish within the execution envelope used for this M0 session. Its partial results are therefore **not** accepted as a statistical baseline.

This reinforces the rule that smoke runs and statistical A/B runs serve different purposes.

## 9. Reproducibility audit

### 9.1. Map seed alone is not enough

`Simulation.kt` sets:

```text
gameSetupInfo.mapParameters.seed = 0
```

and `MapGenerator` replaces seed `0` with `System.currentTimeMillis()`.

Therefore the stock simulation deliberately samples fresh maps rather than replaying a fixed map corpus.

Additionally, `GameStarter` documents that terrain generation is deterministic from the map seed, while other setup details use `GameContext.stateBasedRandom`, which depends on the randomly generated `gameId`.

So a fixed map seed alone cannot recreate the entire initial game.

### 9.2. Determinism probes

Two probes were run:

1. Generate two games using map seed `424242` and normalize `gameId` only after `GameStarter` returns.
   - Result: state diverged by turn 50.
   - This is expected to be insufficient because setup randomness already used the original random `gameId`.

2. Generate one turn-0 game, clone that exact `GameInfo` into two branches, restore transients, and run both branches from the same initial state.
   - Result: final checksums differed.
   - In a 50-turn probe, population also diverged between branches.
   - In a shorter pre-cloned 30-turn probe, population matched but checksums still differed.

M0 therefore does **not** certify exact replay determinism for 5.0.1.

The root cause has not been isolated. Possible categories include shared/global mutable state, iteration-order effects, randomness outside the inspected automation paths, or transient data not fully represented by the clone. M0 deliberately records the failure instead of guessing a cause.

### 9.3. Automation random audit

A targeted scan of the principal civilization/city/unit automation files did not find obvious no-argument `.random()` / `.shuffled()` calls in the inspected decision modules. The randomness found in `DiplomacyAutomation` and `EspionageAutomation` is explicitly seeded.

This narrows but does not resolve the nondeterminism question, because relevant state transitions also occur outside `logic/automation`.

## 10. Benchmark protocol adopted after M0

### 10.1. Two separate benchmark modes

The project should maintain two distinct concepts:

**A. Statistical A/B mode — required for AI strength decisions**

- control and experiment civilizations coexist in the same simulation where practical;
- use generic civilizations without uniques;
- use the mirrored official test-map configuration;
- use `CIV_IDS_IN_EXPERIMENT_GROUP` to gate only the behavior under test;
- randomize or balance player order;
- use 20–50 games for smoke;
- use 200–400 games for a major A/B decision;
- report effect size and p-value, not only whether one side happened to win more games.

**B. Exact replay mode — regression/debugging tool, currently unresolved**

- intended to replay a frozen turn-0 state exactly;
- must not be used as an oracle until the clone/replay nondeterminism is isolated;
- once fixed, preserve seed, gameId, starting state, configuration, and relevant build identity.

### 10.2. Project default sample sizes

For consistency, the provisional operational defaults after M0 are:

```text
smoke batch       32 games
main A/B batch    256 games
```

These values sit inside the ranges already established in the roadmap and official developer guidance. They are defaults, not a rule against running more games when the effect is small or variance is high.

### 10.3. Main A/B configuration

Unless a feature requires a specialized scenario, the default main comparison should preserve the official configuration:

```text
baseline            Unciv 5.0.1 / 82edba7...
ruleset             Civ V - Gods & Kings
civs                 generic, no uniques
difficulty           King
map                  Tiny, mirrored around center
city-states          0
barbarians           off
ruins                off
natural wonders      off
legendary start      on
strategic balance    on
speed                default
max turns            500
victories            all enabled
```

Specialized scenario tests should be added for behavior that global win rate cannot measure well, especially tactical movement, siege, retreat, focus fire, city defense, worker behavior, and settler behavior.

### 10.4. Run metadata that must be captured

Before M1 behavior changes are accepted, benchmark output should be extended to record at least:

- baseline/experiment build identity;
- benchmark configuration version;
- experiment-group assignment;
- game number;
- map seed actually used;
- gameId;
- ending turn;
- winner/victory or draw;
- elapsed time;
- requested intermediate metrics.

Recording these values does not itself guarantee deterministic replay, but it makes results auditable and helps isolate nondeterminism.

## 11. M0 architecture decisions

The following are promoted from provisional assumptions to M0-supported decisions:

1. **Do not rewrite the complete AI stack.** The existing automation contains substantial domain logic worth preserving.
2. **Use `NextTurnAutomation` as the integration point.** It is already the normal civilization AI orchestrator.
3. **Compose with `AutomationCache` rather than replace it.** Existing per-turn caches are useful and correctly scoped.
4. **Add strategy above execution.** The strongest architectural gap is shared intent across technology, cities, diplomacy, and units.
5. **Military improvement should begin with coordination, not deeper per-unit heuristics.** The local unit/battle layer is already nontrivial.
6. **Technology planning is an early high-value target.** Current selection is local enough that bounded path planning has a clear role.
7. **Construction should receive better context before being rewritten.** Its generic evaluator is reusable.
8. **A separate `AIWorldView` is not yet justified.** Existing views and explicit fair-info checks must be audited first.
9. **Statistical A/B is the authoritative strength test for now.** Exact replay determinism remains an engineering investigation.
10. **Performance telemetry must remain enabled during AI development.** The cost of stronger reasoning is part of correctness for Android/Desktop targets.

## 12. M1 handoff

M1 should begin with infrastructure that changes behavior as little as possible.

Recommended first implementation slice:

```text
NextTurnAutomation
      │
      ├─ create AutomationCache
      │
      └─ attach/compose minimal AdvancedAIContext
             ├─ immutable turn identity
             ├─ civ-level summary already derivable from fair views
             ├─ lightweight strategic diagnostics
             └─ decision logging hooks
```

M1 should **not** yet add deep tactical search or rewrite technology/city decisions. Its purpose is to create a safe shared context and measurement path for M2/M3.

Before M1 is accepted:

- existing unit tests must remain green;
- the 32-game smoke batch should complete without correctness regressions;
- `automateCivMoves` performance should be compared against the M0 smoke reference;
- no project-specific resource or hidden-information bonus may be introduced;
- benchmark metadata logging should be improved enough to audit runs;
- the replay nondeterminism issue should remain visible until actually resolved.

## 13. M0 exit checklist

| Requirement | Result |
|---|---|
| Pin exact official baseline | PASS |
| Verify baseline artifact identity | PASS |
| Verify upstream build/test status | PASS |
| Map normal AI turn call graph | PASS |
| Inventory major decision points | PASS |
| Classify KEEP / IMPROVE / REPLACE / INVESTIGATE | PASS |
| Fair-information source audit | PASS for initial audit; ongoing |
| Execute official simulation path | PASS |
| Establish smoke performance reference | PASS |
| Audit official A/B harness | PASS |
| Define statistical benchmark settings | PASS |
| Establish exact deterministic replay | NOT ESTABLISHED; tracked separately |
| Provide a baseline usable for future comparison | PASS via statistical A/B |

## 14. Evidence and working artifacts

Local M0 execution artifacts retained during this session include:

- official `Unciv.jar` extracted from the 5.0.1 GitHub Actions artifact;
- `M0Baseline4x100.log` — completed 4-game/100-turn smoke log;
- `M0CloneDeterminism.log` — cloned-state replay probe;
- `M0CloneDeterminismPreclone.log` — pre-cloned-state replay probe;
- `M0FixedSeedProbe.log` — fixed-map-seed probe;
- small Kotlin launchers used only to drive the unmodified official JAR.

These runner files are M0 test scaffolding, not production Advanced AI code.

## 15. Change log

### v1 — 2026-11-20

Initial M0 baseline report. Establishes the pinned source/artifact identity, AI call graph, decision-point classification, fairness audit, executable smoke baseline, benchmark protocol, determinism findings, and M1 handoff.
