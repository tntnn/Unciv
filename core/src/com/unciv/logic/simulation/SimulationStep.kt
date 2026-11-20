package com.unciv.logic.simulation

import com.unciv.logic.GameInfo
import kotlin.math.roundToInt

class SimulationStep (gameInfo: GameInfo, statTurns: List<Int> = listOf()) {
    private data class ResearchSample(val completed: Int, val sciencePerTurn: Float)

    private val civilizationNames = gameInfo.civilizations.filter { !it.isSpectator && it.isMajorCiv }.map { it.civID }
    private val reportingTurns = statTurns + -1
    var turns = gameInfo.turns
    var victoryType = gameInfo.getCurrentPlayerCivilization().victoryManager.getVictoryTypeAchieved()
    var winner: String? = null
    var currentPlayer = gameInfo.currentPlayer
    val turnStatsPop = mutableMapOf<String, MutableMap<Int, MutableInt>>() // [civ][turn][stat]
    val turnStatsProd = mutableMapOf<String, MutableMap<Int, MutableInt>>() // [civ][turn][stat]
    val turnStatsCities = mutableMapOf<String, MutableMap<Int, MutableInt>>() // [civ][turn][stat]
    private val turnResearch = mutableMapOf<String, MutableMap<Int, ResearchSample>>()
    
    init {
        for (civ in civilizationNames) {
            for (turn in statTurns) {
                this.turnStatsPop.getOrPut(civ) { mutableMapOf() }[turn] = MutableInt(-1)
                this.turnStatsProd.getOrPut(civ) { mutableMapOf() }[turn] = MutableInt(-1)
                this.turnStatsCities.getOrPut(civ) { mutableMapOf() }[turn] = MutableInt(-1)
            }
            this.turnStatsPop.getOrPut(civ) { mutableMapOf() }[-1] = MutableInt(-1) // end of game
            this.turnStatsProd.getOrPut(civ) { mutableMapOf() }[-1] = MutableInt(-1) // end of game
            this.turnStatsCities.getOrPut(civ) { mutableMapOf() }[-1] = MutableInt(-1) // end of game
        }
        
    }
    
    fun saveTurnStats(gameInfo: GameInfo, turn: Int = gameInfo.turns) {
        for (civ in gameInfo.civilizations.filter { !it.isSpectator && it.isMajorCiv }) {
            val popsum = civ.cities.sumOf { it.population.population }
            //println("$civ $turn $popsum")
            turnStatsPop[civ.civID]!![turn]!!.set(popsum)
            val prodsum = civ.cities.sumOf { it.cityStats.currentCityStats.production.roundToInt() }
            //println("$civ $prodsum")
            turnStatsProd[civ.civID]!![turn]!!.set(prodsum)
            val cityCnt = civ.cities.count()
            //println("$civ $cityCnt")
            turnStatsCities[civ.civID]!![turn]!!.set(cityCnt)
            turnResearch.getOrPut(civ.civID) { mutableMapOf() }[turn] =
                ResearchSample(civ.tech.getNumberOfTechsResearched(), civ.stats.statsForNextTurn.science)
        }
    }

    internal fun statisticsText(): String = reportingTurns.joinToString(" ") { turn ->
        val label = if (turn == -1) "END" else turn.toString()
        val samples = civilizationNames.mapNotNull { civ ->
            val population = turnStatsPop.getValue(civ).getValue(turn).value
            if (population == -1) return@mapNotNull null
            val production = turnStatsProd.getValue(civ).getValue(turn).value
            val cities = turnStatsCities.getValue(civ).getValue(turn).value
            val research = turnResearch.getValue(civ).getValue(turn)
            "$civ(population=$population production=$production cities=$cities " +
                "techs=${research.completed} science=${research.sciencePerTurn})"
        }
        "$label=[${samples.joinToString()}]"
    }
    
    fun update(gameInfo: GameInfo) {
        turns = gameInfo.turns
        victoryType = gameInfo.getCurrentPlayerCivilization().victoryManager.getVictoryTypeAchieved()
        currentPlayer = gameInfo.currentPlayer
    }
}


