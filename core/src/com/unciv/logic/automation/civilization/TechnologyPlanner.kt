package com.unciv.logic.automation.civilization

import com.unciv.logic.automation.Timers.Companion.timeThis
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.MilestoneType
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import kotlin.math.sqrt

/** Evaluates bounded prerequisite closures and returns only the first technology of the best path. */
object TechnologyPlanner {
    private const val MAX_DEPTH = 4
    private const val MAX_TARGETS = 96
    private const val MAX_PREREQUISITES_CHECKED = 768
    private const val BENEFIT_DELAY_TURNS = 12f
    private const val PATH_COST_TURNS = 18f
    private const val EXTRA_STEP_DISCOUNT = 0.35f

    private data class Unlock(val requirements: Set<String>, val value: Float)

    fun choose(civ: Civilization, priorities: StrategicPriorities, context: AdvancedAIContext? = null): Technology? =
        timeThis("TechnologyPlanner.choose") { Search(civ, priorities, context).choose() }

    private class Search(
        val civ: Civilization,
        val priorities: StrategicPriorities,
        val context: AdvancedAIContext?
    ) {
        val ruleset = civ.gameInfo.ruleset
        val tech = civ.tech
        val order = compareBy<Technology> { it.cost }.thenBy { it.name }
        val paidCosts = HashMap<String, Int>()
        val weights = HashMap<String, Float>()
        val unavailable = HashMap<String, Boolean>()
        val legalFirst = HashMap<String, Boolean>()
        val science = civ.stats.statsForNextTurn.science.coerceAtLeast(1f)
        val unlocks by lazy { indexUnlocks() }
        var prerequisiteChecks = 0

        fun choose(): Technology? {
            val targets = ruleset.technologies.values.asSequence()
                .filter { !tech.isResearched(it.name) || it.isContinuallyResearchable() }
                .sortedWith(order)
                .take(MAX_TARGETS)
                .toList()
            var bestPath: List<Technology> = emptyList()
            var bestScore = Float.NEGATIVE_INFINITY
            val diagnostics = if (context?.diagnosticsEnabled == true) mutableListOf<Pair<Float, String>>() else null
            var pathsScored = 0
            for (target in targets) {
                val path = pathTo(target) ?: continue
                var cost = 0f
                var weightedUtility = 0f
                val contributions = diagnostics?.let { ArrayList<String>(path.size) }
                for ((index, step) in path.withIndex()) {
                    cost += paidCost(step)
                    val turns = ((cost - tech.getOverflowScience()).coerceAtLeast(0f)) / science
                    val unlocked = unlocks[step.name].orEmpty().sumOf { unlock ->
                        // Attach the credit to the last required technology completed in this path.
                        if (unlock.requirements.all { name -> (0..index).any { path[it].name == name } })
                            unlock.value.toDouble() else 0.0
                    }.toFloat()
                    val weight = weight(step)
                    val immediate = weight * (1f + unlocked)
                    val delay = 1f + turns / BENEFIT_DELAY_TURNS + index * EXTRA_STEP_DISCOUNT
                    weightedUtility += immediate / delay
                    contributions?.add("${step.name}(weight=$weight unlock=$unlocked science=$cost turns=$turns discounted=${immediate / delay})")
                }
                val researchTurns = (cost - tech.getOverflowScience()).coerceAtLeast(0f) / science
                val score = weightedUtility / (1f + researchTurns / PATH_COST_TURNS)
                pathsScored++
                if (score > bestScore) {
                    bestPath = path
                    bestScore = score
                }
                if (contributions != null) diagnostics.add(score to contributions.joinToString(" -> "))
            }
            val chosen = bestPath.firstOrNull()
            if (diagnostics != null) {
                val top = diagnostics.sortedByDescending { it.first }.take(3)
                context?.record("technology-path", chosen?.name,
                    "targets=${targets.size} scored=$pathsScored prerequisiteChecks=$prerequisiteChecks " +
                        "priorities=$priorities top=$top", bestScore.toDouble().takeIf { it.isFinite() })
            }
            return chosen
        }

        private fun paidCost(step: Technology): Int = paidCosts.getOrPut(step.name) {
            (tech.costOfTech(step.name) - tech.researchOfTech(step.name)).coerceAtLeast(0)
        }

        private fun weight(step: Technology): Float = weights.getOrPut(step.name) {
            step.getWeightForAiDecision(civ.state).coerceAtLeast(0f)
        }

        private fun pathTo(target: Technology): List<Technology>? {
            val remaining = arrayListOf(target)
            var cursor = 0
            while (cursor < remaining.size) {
                val step = remaining[cursor++]
                if (unavailable.getOrPut(step.name) { tech.isUnresearchable(step) }) return null
                if (step.prerequisites.size > MAX_PREREQUISITES_CHECKED - prerequisiteChecks) return null
                prerequisiteChecks += step.prerequisites.size
                for (name in step.prerequisites.sorted()) {
                    if (tech.isResearched(name) || remaining.any { it.name == name }) continue
                    if (remaining.size == MAX_DEPTH) return null
                    remaining.add(ruleset.technologies[name] ?: return null)
                }
            }

            val path = ArrayList<Technology>(remaining.size)
            while (remaining.isNotEmpty()) {
                val next = remaining.asSequence()
                    .filter { step ->
                        if (path.isEmpty()) {
                            legalFirst.getOrPut(step.name) { tech.canBeResearched(step.name) } && weight(step) > 0f
                        } else {
                            step.prerequisites.all { name -> tech.isResearched(name) || path.any { it.name == name } }
                        }
                    }
                    .minWithOrNull(order) ?: return null
                path.add(next)
                remaining.remove(next)
            }
            return path
        }

        private fun indexUnlocks(): Map<String, List<Unlock>> {
            val index = HashMap<String, MutableList<Unlock>>()
            fun add(requirements: Set<String>, value: Float) {
                if (requirements.isEmpty() || requirements.size > MAX_DEPTH || value <= 0f) return
                val unlock = Unlock(requirements, value)
                for (requirement in requirements) index.getOrPut(requirement) { ArrayList() }.add(unlock)
            }

            val cityCount = civ.cities.size.coerceAtLeast(1)
            val population = (civ.cities.sumOf { it.population.population }.toFloat() / cityCount).coerceAtLeast(1f)
            val production = (civ.cities.sumOf { it.cityStats.currentCityStats.production.toDouble() }.toFloat() /
                cityCount).coerceAtLeast(1f)
            val victoryConstructions = HashSet<String>()
            for (victory in civ.victoryManager.getAvailableVictories()) {
                victoryConstructions.addAll(victory.requiredSpaceshipParts)
                victory.milestoneObjects.filter { it.type == MilestoneType.BuiltBuilding }
                    .mapTo(victoryConstructions) { it.params[0] }
            }
            for (building in ruleset.buildings.values) {
                val requirements = building.requiredTechs().filterNot(tech::isResearched).toSet()
                if (requirements.isEmpty() || requirements.size > MAX_DEPTH) continue
                if (building.uniqueTo?.let { !civ.matchesFilter(it) } == true ||
                    (building.uniqueTo == null && civ.getEquivalentBuilding(building) != building) ||
                    building.hasUnique(UniqueType.Unbuildable) || building.isUnavailableBySettings(civ.gameInfo)) continue
                val yields = building.cloneStats()
                val percentages = building.getStatPercentageBonuses(null)
                for (stat in Stat.entries) yields[stat] += percentages[stat] * civ.stats.statsForNextTurn[stat] / cityCount / 100f
                for (unique in building.getMatchingUniques(UniqueType.Stats)) yields.add(unique.stats)
                for (unique in building.getMatchingUniques(UniqueType.StatsPerPopulation)) {
                    yields.add(unique.stats * (population / unique.params[1].toFloat().coerceAtLeast(1f)))
                }
                yields.gold -= building.maintenance
                val military = if (building.cityStrength > 0 || building.cityHealth > 0) priorities.military else 0f
                val victory = if (building.name in victoryConstructions ||
                    building.hasUnique(UniqueType.TriggersVictory)) 4f else 0f
                val timeToBuild = building.cost.coerceAtLeast(0) / production
                val value = (valueOf(yields) + military + victory) / (1f + timeToBuild / 20f)
                add(requirements, if (building.isWonder && victory == 0f) value * 0.35f else value)
            }
            for (unit in ruleset.units.values) {
                val requirements = unit.requiredTechs().filterNot(tech::isResearched).toSet()
                if (requirements.isEmpty() || requirements.size > MAX_DEPTH) continue
                if (unit.uniqueTo?.let { !civ.matchesFilter(it) } == true ||
                    (unit.uniqueTo == null && civ.getEquivalentUnit(unit) != unit) ||
                    unit.hasUnique(UniqueType.Unbuildable) || unit.isUnavailableBySettings(civ.gameInfo) ||
                    tech.isObsolete(unit)) continue
                val strength = maxOf(unit.strength, unit.rangedStrength)
                val military = if (unit.isMilitary) priorities.military * sqrt(strength.coerceAtLeast(0) / 10f) else 0f
                val victory = if (unit.name in victoryConstructions) 4f else 0f
                add(requirements, (military + victory) / (1f + unit.cost.coerceAtLeast(0) / production / 20f))
            }
            for (improvement in ruleset.tileImprovements.values) {
                if (improvement.turnsToBuild <= 0 || improvement.uniqueTo?.let { !civ.matchesFilter(it) } == true) continue
                add(improvement.requiredTechs().filterNot(tech::isResearched).toSet(), priorities.economy + valueOf(improvement))
            }
            for (resource in ruleset.tileResources.values) {
                val revealedBy = resource.revealedBy ?: continue
                if (tech.isResearched(revealedBy)) continue
                val value = when (resource.resourceType) {
                    ResourceType.Strategic -> priorities.military * 0.7f
                    ResourceType.Luxury -> priorities.economy * 1.2f
                    ResourceType.Bonus -> priorities.economy * 0.3f
                }
                add(setOf(revealedBy), value)
            }
            for (technology in ruleset.technologies.values) {
                if (tech.isResearched(technology.name)) continue
                val yields = Stats()
                for (unique in technology.getMatchingUniques(UniqueType.Stats)) yields.add(unique.stats)
                val benefits = valueOf(yields) +
                    (if (technology.hasUnique(UniqueType.LandUnitEmbarkation)) priorities.expansion else 0f) +
                    (if (technology.hasUnique(UniqueType.EnablesResearchAgreements)) priorities.science else 0f)
                add(setOf(technology.name), benefits)
            }
            return index
        }

        private fun valueOf(stats: Stats): Float =
            (stats.science * priorities.science * 1.5f +
                (stats.production + stats.happiness + stats.gold * 0.5f) * priorities.economy +
                stats.culture * priorities.culture + stats.food * priorities.expansion + stats.faith * 0.3f)
                .coerceAtLeast(0f)
    }
}
