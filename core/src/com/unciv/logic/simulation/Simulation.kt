package com.unciv.logic.simulation

import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameStarter
import com.unciv.logic.automation.Timers
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.utils.DebugUtils
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

@ExperimentalTime
class Simulation(
    private val newGameInfo: GameInfo,
    val simulationsPerThread: Int = 1,
    private val threadsNumber: Int = 1,
    private val maxTurns: Int = 500,
    private val statTurns: List<Int> = listOf(),
    private val civIdsInExperimentGroup: Set<String> = emptySet(),
    private val configurationId: String = "custom",
    private val buildRevision: String? = null
) {
    private val maxSimulations = threadsNumber * simulationsPerThread
    private val majorCivs = newGameInfo.civilizations.filter { !it.isSpectator && it.isMajorCiv }.map { it.civID }
    private val numMajorCivs = newGameInfo.civilizations.filter { !it.isSpectator && it.isMajorCiv  }.size
    private val reportingTurns = statTurns + -1
    private val checkpointTurns = statTurns.toSet()
    private val victoryTypes = newGameInfo.ruleset.victories.keys
    private var startTime: Long = 0
    private var talliedGames = 0
    var numWins = mutableMapOf<String, MutableInt>()
    private var summaryStatsPop = HashMap<String, HashMap<Int, HashMap<Stat, MutableInt>>>() // [civ][turn][stat]=value
    private var summaryStatsProd = HashMap<String, HashMap<Int, HashMap<Stat, MutableInt>>>() // [civ][turn][stat]=value
    private var summaryStatsCities = HashMap<String, HashMap<Int, HashMap<Stat, MutableInt>>>() // [civ][turn][stat]=value
    private var summaryStatsAvgPop = HashMap<String, HashMap<Int, HashMap<Stat, MutableInt>>>() // [civ][turn][stat]=value
    private var winRateByVictory = HashMap<String, MutableMap<String, MutableInt>>()
    private var winTurnByVictory = HashMap<String, MutableMap<String, MutableInt>>()
    private var avgSpeed = 0f
    private var avgDuration: Duration = Duration.ZERO
    private var totalTurns = 0
    private var totalDuration: Duration = Duration.ZERO
    private var stepCounter: Int = 0
    enum class Stat {
        SUM,
        NUM
    }
    // print flags
    private val printPop = true
    private val printProd = false
    private val printCityCnt = false
    private val printAvgCityPop = false

    init{
        DebugUtils.CIV_IDS_IN_EXPERIMENT_GROUP = civIdsInExperimentGroup
        for (civ in majorCivs) {
            this.numWins[civ] = MutableInt(0)
            winRateByVictory[civ] = mutableMapOf()
            for (victory in victoryTypes)
                winRateByVictory[civ]!![victory] = MutableInt(0)
            winTurnByVictory[civ] = mutableMapOf()
            for (victory in victoryTypes)
                winTurnByVictory[civ]!![victory] = MutableInt(0)
        }
        initHash(summaryStatsPop)
        initHash(summaryStatsProd)
        initHash(summaryStatsCities)
        initHash(summaryStatsAvgPop)
    }
    
    // Need to initialize the values
    // Later will iterate with flatMap
    private fun initHash(summary: HashMap<String, HashMap<Int, HashMap<Stat, MutableInt>>>) {
        for (civ in majorCivs) {
            for (turn in reportingTurns) {
                summary.getOrPut(civ) { hashMapOf() }.getOrPut(turn){hashMapOf()}[Stat.SUM] = MutableInt(0)
                summary.getOrPut(civ) { hashMapOf() }.getOrPut(turn){hashMapOf()}[Stat.NUM] = MutableInt(0)
            }
        }
    }

    fun start() = runBlocking {
        startTime = System.currentTimeMillis()

        println(
            "Starting new game with major civs: " +
                newGameInfo.civilizations.filter { it.isMajorCiv }.joinToString { it.civID } +
                " and minor civs: " +
                newGameInfo.civilizations.filter { it.isCityState }.joinToString { it.civID }
        )

        newGameInfo.gameParameters.shufflePlayerOrder = true
        val map = newGameInfo.tileMap.mapParameters
        println("Simulation run: version=${UncivGame.VERSION.toSerializeString()} revision=${buildRevision ?: "unspecified"} " +
            "configuration=$configurationId games=$maxSimulations threads=$threadsNumber maxTurns=$maxTurns statTurns=$statTurns " +
            "experiment=${majorCivs.filter { it in civIdsInExperimentGroup }} control=${majorCivs.filter { it !in civIdsInExperimentGroup }} " +
            "shufflePlayerOrder=${newGameInfo.gameParameters.shufflePlayerOrder} game=${newGameInfo.gameParameters} " +
            "map=$map mapType=${map.type} mirroring=${map.mirroring} noRuins=${map.noRuins} noNaturalWonders=${map.noNaturalWonders} " +
            "waterThreshold=${map.waterThreshold}")

        Timers.singleton.startTiming()
        val jobs = (1..threadsNumber).map { threadId ->
            launch(Dispatchers.Default + CoroutineName("simulation-$threadId")) {
                repeat(simulationsPerThread) { gameOffset ->
                    val gameIndex = (threadId - 1) * simulationsPerThread + gameOffset + 1
                    val started = TimeSource.Monotonic.markNow()
                    val step = SimulationStep(newGameInfo, statTurns)
                    val gameSetupInfo = GameSetupInfo(newGameInfo)
                    gameSetupInfo.mapParameters.seed = 0
                    val gameInfo = GameStarter.startNewGame(gameSetupInfo)

                    gameInfo.simulateUntilWin = true
                    gameInfo.simulateMaxTurns = maxTurns
                    if (gameInfo.turns in checkpointTurns) step.saveTurnStats(gameInfo)
                    gameInfo.nextTurn(onTurnStarted = { turn ->
                        if (turn in checkpointTurns) step.saveTurnStats(gameInfo, turn)
                    })
                    step.update(gameInfo)

                    step.saveTurnStats(gameInfo, turn = -1)
                    if (step.victoryType != null) {
                        step.winner = step.currentPlayer
                        println("${step.winner} won ${step.victoryType} victory on turn ${step.turns}")
                    } else if (gameInfo.getAliveMajorCivs().isEmpty()) {
                        println("No major civ left on turn ${step.turns}: Draw")
                    } else {
                        println("Max simulation ${step.turns} turns reached: Draw")
                    }

                    println("Simulation game: index=$gameIndex seed=${gameInfo.tileMap.mapParameters.seed} " +
                        "gameId=${gameInfo.gameId} endingTurn=${step.turns} winner=${step.winner ?: "none"} " +
                        "victory=${step.victoryType ?: "draw"} elapsedMs=${started.elapsedNow().inWholeMilliseconds} " +
                        "stats=${step.statisticsText()}")

                    // ⚠️ these need to be thread-safe
                    updateCounter(threadId)
                    add(step)
                    print()
                }
            }
        }

        jobs.joinAll()
        Timers.singleton.endTiming()
    }

    @Suppress("UNUSED_PARAMETER")   // used when activating debug output
    @Synchronized fun add(step: SimulationStep, threadId: Int = 1) {
        talliedGames++
        totalTurns += step.turns
        if (step.winner != null) {
            numWins[step.winner!!]!!.inc()
            winRateByVictory[step.winner!!]!![step.victoryType]!!.inc()
            winTurnByVictory[step.winner!!]!![step.victoryType]!!.add(step.turns)
        }
        for (civ in majorCivs) {
            for (turn in reportingTurns) {
                summaryStatSet(summaryStatsPop, civ, turn, step.turnStatsPop)
                summaryStatSet(summaryStatsProd, civ, turn, step.turnStatsProd)
                summaryStatSet(summaryStatsCities, civ, turn, step.turnStatsCities)
                if (step.turnStatsPop[civ]!![turn]!!.value != -1 && step.turnStatsCities[civ]!![turn]!!.value != -1) {
                    if (step.turnStatsCities[civ]!![turn]!!.value != 0) // if no cities, avgpop=0
                        summaryStatsAvgPop[civ]!![turn]!![Stat.SUM]!!.add(step.turnStatsPop[civ]!![turn]!!.value/step.turnStatsCities[civ]!![turn]!!.value)
                    summaryStatsAvgPop[civ]!![turn]!![Stat.NUM]!!.inc()
                }
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")   // used when activating debug output
    @Synchronized fun updateCounter(threadId: Int = 1) {
        stepCounter++
        println("Simulation step ($stepCounter/$maxSimulations)")
    }

    @Synchronized
    fun print(){
        totalDuration = (System.currentTimeMillis() - startTime).milliseconds
        avgSpeed = totalTurns.toFloat() / totalDuration.inWholeSeconds
        avgDuration = totalDuration / max(talliedGames, 1)
        println(text())
    }
    
    private fun summaryStatSet(summaryHash: HashMap<String, HashMap<Int, HashMap<Stat, MutableInt>>>,
                    civ: String, turn: Int, stat:  MutableMap<String, MutableMap<Int, MutableInt>>) {
        if (stat[civ]!![turn]!!.value != -1) {
            summaryHash[civ]!![turn]!![Stat.SUM]!!.add(stat[civ]!![turn]!!.value)
            summaryHash[civ]!![turn]!![Stat.NUM]!!.inc()
            //println("civ ${civ} @ ${turn} value ${stat[civ]!![turn]!!.value}")
        }
    }

    // Helper text formatter
    private fun summaryStatsText(summaryStats: HashMap<Stat, MutableInt>,
                                 turn: Int, statStr: String): String {
        val turnStr = if(turn == -1) "END" else turn
        val count = summaryStats[Stat.NUM]!!.value
        val average = if (count == 0) "n/a" else (summaryStats[Stat.SUM]!!.value.toFloat() / count).toString()
        return "@$turnStr: $statStr avg=$average cnt=$count\n"
    }

    /** Win rate and p-value for the group as a whole */
    private fun groupText(groupName: String, groupCivs: List<String>): String {
        if (groupCivs.isEmpty()) return ""
        val numSteps = max(talliedGames, 1)
        val groupWins = groupCivs.sumOf { numWins[it]!!.value }
        val expWinRate = groupCivs.size.toFloat() / numMajorCivs
        val winRate = String.format("%.1f", groupWins * 100f / numSteps)
        val expected = String.format("%.1f", expWinRate * 100f)

        var outString = "\n$groupName (${groupCivs.joinToString()}):\n"
        outString += "$winRate% total win rate (expected $expected% if no effect)\n"
        if (numSteps * expWinRate >= 10 && numSteps * (1 - expWinRate) >= 10) {
            val pval = binomialTest(groupWins.toDouble(), numSteps.toDouble(), expWinRate.toDouble(), "greater")
            outString += "one-tail binomial pval = $pval\n"
        }
        for (victory in victoryTypes) {
            val winsVictory = groupCivs.sumOf { winRateByVictory[it]!![victory]!!.value } * 100 / max(groupWins, 1)
            outString += "$victory: $winsVictory%    "
        }
        outString += "\n"
        return outString
    }

    fun text(): String {
        var outString = ""
        if (civIdsInExperimentGroup.isNotEmpty()) {
            outString += groupText("Experiment group", majorCivs.filter { it in civIdsInExperimentGroup })
            outString += groupText("Control group", majorCivs.filter { it !in civIdsInExperimentGroup })
        }
        for (civ in majorCivs) {

            val numSteps = max(talliedGames, 1)
            val expWinRate = 1f / numMajorCivs
            val wins = numWins[civ]!!.value
            val winRate = String.format("%.1f", wins * 100f / numSteps)

            outString += "\n$civ:\n"
            outString += "$winRate% total win rate \n"
            if (numSteps * expWinRate >= 10 && numSteps * (1 - expWinRate) >= 10) {
                // large enough sample size, binomial distribution approximates the Normal Curve
                val pval = binomialTest(
                    wins.toDouble(),
                    numSteps.toDouble(),
                    expWinRate.toDouble(),
                    "greater"
                )
                outString += "one-tail binomial pval = $pval\n"
            }
            if (wins > 0) {
                for (victory in victoryTypes) {
                    val winsVictory = winRateByVictory[civ]!![victory]!!.value * 100 / wins
                    outString += "$victory: $winsVictory%    "
                }
                outString += "\n"
                for (victory in victoryTypes) {
                    val winsTurns =
                        winTurnByVictory[civ]!![victory]!!.value / max(winRateByVictory[civ]!![victory]!!.value, 1)
                    outString += "$victory: $winsTurns    "
                }
                outString += "avg turns\n"
            }
            for (turn in reportingTurns) {
                if(printPop)
                    outString += summaryStatsText(summaryStatsPop[civ]!![turn]!!, turn, "popSum")
                if(printProd)
                    outString += summaryStatsText(summaryStatsProd[civ]!![turn]!!, turn, "prodSum")
                if(printCityCnt)
                    outString += summaryStatsText(summaryStatsCities[civ]!![turn]!!, turn, "cityCount")
                if(printAvgCityPop)
                    outString += summaryStatsText(summaryStatsAvgPop[civ]!![turn]!!, turn, "avgCityPop")
            }
        }
        outString += "\nAverage speed: %.1f turns/s \n".format(avgSpeed)
        outString += "Average game duration: $avgDuration\n"
        outString += "Total time: $totalDuration\n"

        return outString
    }

    private fun binomialTest(successes: Double, trials: Double, p: Double, alternative: String): Double {
        val q = 1 - p
        val mean = trials * p
        val variance = trials * p * q
        val stdDev = sqrt(variance)
        val z = (successes - mean) / stdDev
        val pValue = 1 - normalCdf(z)
        return when (alternative) {
            "greater" -> pValue
            "less" -> 1 - pValue
            else -> throw IllegalArgumentException("Alternative must be 'greater' or 'less'")
        }
    }

    private fun normalCdf(z: Double): Double {
        return 0.5 * (1 + erf(z / sqrt(2.0)))
    }

    // Approximation of the error function
    private fun erf(x: Double): Double {
        val t = 1.0 / (1.0 + 0.5 * kotlin.math.abs(x))
        val tau =
            t * kotlin.math.exp(
                -x * x - 1.26551223 +
                    t * (1.00002368 + t * (0.37409196 + t * (0.09678418
                    + t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398 +
                    t * (1.48851587 + t * (-0.82215223 + t * 0.17087277))))))))
            )
        return if (x >= 0) 1 - tau else tau - 1
    }
}
