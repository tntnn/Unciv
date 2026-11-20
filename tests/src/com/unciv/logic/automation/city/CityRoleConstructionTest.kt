package com.unciv.logic.automation.city

import com.unciv.logic.automation.civilization.AdvancedAIContext
import com.unciv.logic.automation.civilization.NextTurnAutomation
import com.unciv.models.ruleset.PerpetualConstruction
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class CityRoleConstructionTest {
    private val game = TestGame()

    @Before
    fun setUp() {
        game.makeHexagonalMap(7)
    }

    @Test
    fun `development priorities can change a close legal choice in a science center`() {
        val civ = game.addCiv()
        val city = game.addCity(civ, game.getTile(0, 0), initialPopulation = 12)
        val production = game.createBuilding().apply { cost = 40; this.production = 8.4f }
        val science = game.createBuilding().apply { cost = 40; this.science = 16f }
        val unavailable = game.createBuilding().apply { cost = 1; this.science = 100f; requiredTech = "Unresearched" }
        val queue = city.cityConstructions
        val records = mutableListOf<AdvancedAIContext.Diagnostic>()

        queue.setCurrentConstruction(PerpetualConstruction.Idle.name)
        ConstructionAutomation(queue).chooseNextConstruction()
        assertEquals(production.name, queue.currentConstructionName())

        queue.setCurrentConstruction(PerpetualConstruction.Idle.name)
        NextTurnAutomation.automateCivMoves(civ, false) { records.add(it) }

        assertEquals(science.name, queue.currentConstructionName())
        val ranking = records.single { it.category == "construction-ranking" }
        assertEquals(science.name, ranking.choice)
        val reason = ranking.reason.orEmpty()
        assertTrue(reason.contains("CityRoleWeights") && reason.contains("base=") && reason.contains("final="))
        assertFalse(reason.contains(unavailable.name))
    }

    @Test
    fun `strategic production does not replace an active construction`() {
        val civ = game.addCiv()
        val city = game.addCity(civ, game.getTile(0, 0))
        val ongoing = game.createBuilding().apply { cost = 50; culture = 1f }
        game.createBuilding().apply { cost = 1; science = 100f }
        val queue = city.cityConstructions
        queue.setCurrentConstruction(ongoing.name)
        queue.inProgressConstructions[ongoing.name] = 20

        NextTurnAutomation.automateCivMoves(civ, false)

        assertEquals(ongoing.name, queue.currentConstructionName())
        assertEquals(20, queue.getWorkDone(ongoing.name))
    }
}
