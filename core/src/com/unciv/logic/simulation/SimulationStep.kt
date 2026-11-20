package com.unciv.logic.simulation

import com.unciv.logic.GameInfo
import kotlin.math.roundToInt

class SimulationStep (gameInfo: GameInfo, statTurns: List<Int> = listOf()) {
    private val civilizationNames = gameInfo.civilizations.filter { !it.isSpectator && it.isMajorCiv }.map { it.civID }
    private val reportingTurns = statTurns + -1
    var turns = gameInfo.turns
    var victoryType = gameInfo.getCurrentPlayerCivilization().victoryManager.getVictoryTypeAchieved()
    var winner: String? = null
    var currentPlayer = gameInfo.currentPlayer
    val turnStatsPop = mutableMapOf<String, MutableMap<Int, MutableInt>>() // [civ][turn][stat]
    val turnStatsProd = mutableMapOf<String, MutableMap<Int, MutableInt>>() // [civ][turn][stat]
    val turnStatsCities = mutableMapOf<String, MutableMap<Int, MutableInt>>() // [civ][turn][stat]
    
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
        }
    }

    internal fun statisticsText(): String = reportingTurns.joinToString(" ") { turn ->
        val label = if (turn == -1) "END" else turn.toString()
        val samples = civilizationNames.mapNotNull { civ ->
            val population = turnStatsPop.getValue(civ).getValue(turn).value
            if (population == -1) return@mapNotNull null
            val production = turnStatsProd.getValue(civ).getValue(turn).value
            val cities = turnStatsCities.getValue(civ).getValue(turn).value
            "$civ(population=$population production=$production cities=$cities)"
        }
        "$label=[${samples.joinToString()}]"
    }
    
    fun update(gameInfo: GameInfo) {
        turns = gameInfo.turns
        victoryType = gameInfo.getCurrentPlayerCivilization().victoryManager.getVictoryTypeAchieved()
        currentPlayer = gameInfo.currentPlayer
    }
}


