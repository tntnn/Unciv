package com.unciv.logic.simulation

import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import com.unciv.utils.DebugUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@RunWith(BaseTestRunner::class)
class SimulationReportingTest {
    @Test
    fun `a draw reports statistics for both civs and distinguishes missing samples`() {
        val game = TestGame()
        game.makeHexagonalMap(7)
        val control = game.addCiv()
        val experiment = game.addCiv()
        game.addCity(control, game.getTile(0, 0))
        game.addCity(experiment, game.getTile(3, 0))
        val gameInfo = game.gameInfo
        gameInfo.currentPlayerCiv = control
        gameInfo.currentPlayer = control.civID
        val statTurns = listOf(1, 3)
        val previousExperimentGroup = DebugUtils.CIV_IDS_IN_EXPERIMENT_GROUP
        try {
            val simulation = Simulation(gameInfo, statTurns = statTurns, civIdsInExperimentGroup = setOf(experiment.civID))
            val step = SimulationStep(gameInfo, statTurns)
            gameInfo.turns = 1
            step.saveTurnStats(gameInfo)
            step.update(gameInfo)
            step.saveTurnStats(gameInfo, turn = -1)
            simulation.add(step)

            assertTrue(simulation.numWins.values.all { it.value == 0 })
            val summary = simulation.text()
            assertTrue(summary.contains("Experiment group (${experiment.civID})"))
            assertTrue(summary.contains("Control group (${control.civID})"))
            assertEquals(2, summary.lineSequence().count { it == "@1: popSum avg=1.0 cnt=1" })
            assertEquals(2, summary.lineSequence().count { it == "@3: popSum avg=n/a cnt=0" })
            assertEquals(2, summary.lineSequence().count { it == "@END: popSum avg=1.0 cnt=1" })
        } finally {
            DebugUtils.CIV_IDS_IN_EXPERIMENT_GROUP = previousExperimentGroup
        }
    }
}
