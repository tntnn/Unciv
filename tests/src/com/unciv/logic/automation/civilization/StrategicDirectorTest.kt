package com.unciv.logic.automation.civilization

import com.unciv.models.ruleset.nation.Personality
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class StrategicDirectorTest {
    private val game = TestGame()

    @Before
    fun setUp() {
        game.makeHexagonalMap(7)
    }

    @Test
    fun `unobserved changes to another civilization do not change priorities`() {
        val own = game.addCiv()
        game.addCity(own, game.getTile(-5, 0))
        val other = game.addCiv()
        val original = StrategicDirector.analyze(own)

        val foreignCity = game.addCity(other, game.getTile(5, 0))
        other.addGold(100)
        game.addUnit("Warrior", other, game.getTile(4, 0))

        assertTrue(!foreignCity.getCenterTile().isExplored(own))
        assertEquals(original, StrategicDirector.analyze(own))
        assertEquals(original, StrategicDirector.analyze(own))
    }

    @Test
    fun `damaged city outweighs peaceful military preference`() {
        val civ = game.addCiv()
        val city = game.addCity(civ, game.getTile(0, 0))
        val personality = Personality().apply { name = "Test focus"; military = 10f }
        game.ruleset.personalities[personality.name] = personality
        civ.nation.personality = personality.name
        val militaryPreference = StrategicDirector.analyze(civ).military

        personality.military = 0f
        assertTrue(StrategicDirector.analyze(civ).military < militaryPreference)
        city.health -= 25

        assertTrue(StrategicDirector.analyze(civ).military > militaryPreference)
    }

    @Test
    fun `cash shortage prioritizes economy and curbs expansion while science pressure responds to yields`() {
        val civ = game.addCiv()
        game.addCity(civ, game.getTile(0, 0))
        civ.stats.happiness = 5
        civ.addGold(80 - civ.gold)
        civ.stats.statsForNextTurn.gold = -4f
        civ.stats.statsForNextTurn.science = 15f
        val stable = StrategicDirector.analyze(civ)

        civ.addGold(-civ.gold)
        civ.stats.statsForNextTurn.science = 2f
        val urgent = StrategicDirector.analyze(civ)

        assertTrue(urgent.economy > stable.economy)
        assertTrue(urgent.expansion < stable.expansion)
        assertTrue(urgent.science > stable.science)
    }
}
