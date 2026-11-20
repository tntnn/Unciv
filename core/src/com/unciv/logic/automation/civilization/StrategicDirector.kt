package com.unciv.logic.automation.civilization

import com.unciv.logic.automation.Timers.Companion.timeThis
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.models.ruleset.nation.PersonalityValue

data class StrategicPriorities(
    val science: Float,
    val economy: Float,
    val military: Float,
    val culture: Float,
    val expansion: Float
)

/** Analyzes the civilization's own position and its side of diplomatic relationships. */
object StrategicDirector {
    fun analyze(civ: Civilization, context: AdvancedAIContext? = null): StrategicPriorities =
        timeThis("StrategicDirector.analyze") {
            val cities = civ.cities.size.coerceAtLeast(1)
            val yields = civ.stats.statsForNextTurn
            val atWar = civ.diplomacy.values.any { it.diplomaticStatus == DiplomaticStatus.War }
            val damagedCity = civ.cities.any { it.health < it.getMaxHealth() }
            val militaryUnits = civ.units.getCivUnits().count { it.baseUnit.isMilitary }
            val runningOutOfGold = yields.gold < 0f && civ.gold + yields.gold * 8 < 0
            val happiness = civ.getHappiness()
            val sciencePerCity = yields.science / cities
            val culturePerCity = yields.culture / cities
            val personality = civ.getPersonality()

            fun preference(value: PersonalityValue) =
                (personality.scaledFocus(value) - 1f).coerceIn(-1f, 1f) * 0.10f

            val priorities = StrategicPriorities(
                science = 1.25f + 0.25f / (1f + sciencePerCity.coerceAtLeast(0f) / 5f) +
                    preference(PersonalityValue.Science),
                economy = 1.15f + (if (runningOutOfGold) 0.55f else 0f) +
                    (if (happiness < 0) 0.25f else 0f) + preference(PersonalityValue.Gold),
                military = 1f + (if (atWar) 0.4f else 0f) + (if (damagedCity) 0.35f else 0f) +
                    (if (militaryUnits < cities) 0.2f else 0f) + preference(PersonalityValue.Military),
                culture = 1f + 0.2f / (1f + culturePerCity.coerceAtLeast(0f)) +
                    preference(PersonalityValue.Culture),
                expansion = 1f + (if (happiness < 0 || runningOutOfGold) -0.4f else 0.25f / cities) +
                    preference(PersonalityValue.Expansion)
            )
            if (context?.diagnosticsEnabled == true) {
                context.record("strategic-priorities",
                    reason = "$priorities war=$atWar damagedCity=$damagedCity militaryUnits=$militaryUnits " +
                        "gold=${civ.gold} goldPerTurn=${yields.gold} happiness=$happiness " +
                        "sciencePerCity=$sciencePerCity culturePerCity=$culturePerCity")
            }
            priorities
        }
}
