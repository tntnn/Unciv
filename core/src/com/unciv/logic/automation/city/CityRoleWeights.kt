package com.unciv.logic.automation.city

import com.unciv.logic.automation.civilization.StrategicPriorities
import com.unciv.logic.city.City
import com.unciv.models.stats.Stats
import com.unciv.view.GameView
import com.unciv.view.isMilitary

internal data class CityRoleWeights(
    val production: Float,
    val science: Float,
    val growth: Float,
    val culture: Float,
    val militaryProduction: Float,
    val defense: Float
) {
    data class Adjustment(val strategic: Float = 0f, val cityRole: Float = 0f) {
        companion object {
            val None = Adjustment()
        }
    }

    fun adjustment(priorities: StrategicPriorities, yields: Stats?, military: Float = 0f, defense: Float = 0f): Adjustment {
        val productionValue = yields?.production?.coerceAtLeast(0f) ?: 0f
        val scienceValue = yields?.science?.coerceAtLeast(0f) ?: 0f
        val growthValue = yields?.food?.coerceAtLeast(0f) ?: 0f
        val cultureValue = yields?.culture?.coerceAtLeast(0f) ?: 0f
        val economyValue = (yields?.gold?.coerceAtLeast(0f) ?: 0f) + (yields?.happiness?.coerceAtLeast(0f) ?: 0f)
        val total = productionValue + scienceValue + growthValue + cultureValue + economyValue + military + defense
        if (total <= 0f) return Adjustment.None

        val strategy = (productionValue + economyValue) * (priorities.economy - 1f) +
            scienceValue * (priorities.science - 1f) + growthValue * (priorities.expansion - 1f) +
            cultureValue * (priorities.culture - 1f) + (military + defense) * (priorities.military - 1f)
        val fit = productionValue * (production - 0.5f) + scienceValue * (science - 0.5f) +
            growthValue * (growth - 0.5f) + cultureValue * (culture - 0.5f) +
            military * (militaryProduction - 0.5f) + defense * (this.defense - 0.5f)
        return Adjustment(
            strategic = (0.4f * strategy / total).coerceIn(-0.16f, 0.16f),
            cityRole = (0.24f * fit / total).coerceIn(-0.12f, 0.12f)
        )
    }

    companion object {
        private const val NEARBY_THREAT_RADIUS = 3

        fun analyze(city: City, averageProduction: Float, atWar: Boolean): CityRoleWeights {
            val yields = city.cityStats.currentCityStats
            val population = city.population.population.coerceAtLeast(1)
            val relativeProduction = yields.production / averageProduction.coerceAtLeast(1f)
            val production = (relativeProduction * 0.6f).coerceIn(0f, 1f)
            val damaged = city.health < city.getMaxHealth()
            val nearbyEnemy = atWar && !damaged && hasVisibleEnemy(city)
            return CityRoleWeights(
                production = production,
                science = (0.15f + population / 20f + yields.science / population * 0.15f +
                    if (city.isCapital()) 0.15f else 0f).coerceIn(0f, 1f),
                growth = if (city.civ.getHappiness() < 0) 0.2f else (1f - population / 20f).coerceIn(0f, 1f),
                culture = (0.35f + yields.culture / population * 0.3f).coerceIn(0f, 1f),
                militaryProduction = (production * 0.75f + if (atWar) 0.25f else 0f).coerceAtMost(1f),
                defense = if (damaged || nearbyEnemy) 1f else if (atWar) 0.5f else 0.15f
            )
        }

        private fun hasVisibleEnemy(city: City): Boolean {
            val gameView = GameView(city.civ.gameInfo, city.civ)
            val center = gameView.getCityView(city).getCenterTile()
            return center.getExploredTilesInDistance(NEARBY_THREAT_RADIUS).any { tile ->
                tile.isVisible() && tile.getVisibleUnits().any { unit ->
                    unit.isMilitary() && gameView.civView.isAtWarWith(unit.civ())
                }
            }
        }
    }
}
