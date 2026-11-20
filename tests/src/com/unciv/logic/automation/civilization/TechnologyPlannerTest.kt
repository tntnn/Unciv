package com.unciv.logic.automation.civilization

import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.tech.TechColumn
import com.unciv.models.ruleset.tech.Technology
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class TechnologyPlannerTest {
    private val game = TestGame()
    private lateinit var civ: Civilization
    private lateinit var column: TechColumn
    private val neutral = StrategicPriorities(1f, 1f, 1f, 1f, 1f)

    @Before
    fun setUp() {
        game.makeHexagonalMap(4)
        civ = game.addCiv()
        game.addCity(civ, game.getTile(0, 0))
        column = game.ruleset.technologies.values.first().column!!
        game.ruleset.technologies.clear()
        civ.stats.statsForNextTurn.science = 10f
    }

    private fun tech(name: String, cost: Int, vararg requires: String) =
        Technology().apply {
            this.name = name
            this.cost = cost
            column = this@TechnologyPlannerTest.column
            prerequisites.addAll(requires)
            game.ruleset.technologies[name] = this
        }

    private fun unlock(technology: Technology, science: Float = 0f, production: Float = 0f) {
        game.createBuilding().apply {
            cost = 20
            requiredTech = technology.name
            this.science = science
            this.production = production
        }
    }

    private fun choose(priorities: StrategicPriorities = neutral) = TechnologyPlanner.choose(civ, priorities)?.name

    @Test
    fun `immediate development value beats a comparably priced alternative`() {
        tech("Stone Seals", 30)
        unlock(tech("Celestial Measurements", 35), science = 5f)
        assertEquals("Celestial Measurements", choose())
    }

    @Test
    fun `a weak prerequisite leads to a valuable downstream branch`() {
        unlock(tech("Clay Firing", 25), production = 1f)
        tech("Number Marks", 26)
        tech("Portable Records", 30, "Number Marks")
        unlock(tech("Celestial Measurements", 35, "Portable Records"), science = 15f)

        assertEquals("Number Marks", choose())
    }

    @Test
    fun `an expensive delayed payoff loses to a practical immediate unlock`() {
        unlock(tech("Clay Firing", 25), production = 2f)
        tech("Number Marks", 26)
        unlock(tech("Celestial Measurements", 1500, "Number Marks"), science = 15f)

        assertEquals("Clay Firing", choose())
    }

    @Test
    fun `different priorities change the value of equally affordable branches`() {
        unlock(tech("Learning", 30), science = 4f)
        unlock(tech("Tools", 30), production = 6f)

        assertEquals("Learning", choose(neutral.copy(science = 1.5f)))
        assertEquals("Tools", choose(neutral.copy(economy = 1.5f)))
    }

    @Test
    fun `a path cannot profit from a missing or unavailable prerequisite or a cycle`() {
        tech("Unavailable", 20).uniques.add("Unavailable")
        tech("Loop A", 20, "Loop B")
        tech("Loop B", 20, "Loop A")
        unlock(tech("Unreachable S", 20, "Unavailable"), science = 100f)
        unlock(tech("Unreachable M", 20, "Missing"), science = 100f)
        unlock(tech("Unreachable L", 20, "Loop A"), science = 100f)

        assertNull(choose())
        tech("Legal", 25)
        assertEquals("Legal", choose())
    }

    @Test
    fun `a path includes every distinct prerequisite of a joined branch`() {
        tech("First", 25)
        tech("Second", 26)
        tech("Branches", 29, "First", "Second")
        unlock(tech("Observatory", 32, "Branches", "First"), science = 20f)
        unlock(tech("Alternative", 29), production = 1f)

        assertEquals("First", choose())
        civ.tech.techsResearched.add("First")
        assertEquals("Second", choose())
    }

    @Test
    fun `equivalent paths use stable order regardless of ruleset insertion order`() {
        val second = tech("Tools B", 30)
        val first = tech("Tools A", 30)
        assertEquals("Tools A", choose())
        game.ruleset.technologies.clear()
        game.ruleset.technologies[first.name] = first
        game.ruleset.technologies[second.name] = second
        assertEquals("Tools A", choose())
    }

    @Test
    fun `lookahead stops after four distinct technologies`() {
        unlock(tech("Local Development", 20), production = 3f)
        var previous = tech("Branch 1", 20)
        for (step in 2..5) previous = tech("Branch $step", 20, previous.name)
        unlock(previous, science = 100f)

        assertEquals("Local Development", choose())
    }

    @Test
    fun `large research frontier respects the diagnostic target budget`() {
        for (index in 1..120) tech("Root $index", 10)
        unlock(tech("Outside budget", 20), science = 100f)
        val diagnostics = mutableListOf<AdvancedAIContext.Diagnostic>()

        NextTurnAutomation.automateCivMoves(civ, false) { diagnostics.add(it) }

        val planned = diagnostics.single { it.category == "technology-path" }
        assertTrue(planned.reason.orEmpty().contains("targets=96 scored=96 prerequisiteChecks=0"))
        assertEquals("Root 1", planned.choice)
        assertEquals(planned.choice, civ.tech.currentTechnologyName())
    }
}
