package com.unciv.logic.automation.civilization

import com.unciv.logic.civilization.Civilization
import com.unciv.utils.Log

/** Snapshot of a civilization at the start of its automation pass. */
class AdvancedAIContext internal constructor(
    val turn: Int,
    val civId: String,
    val ownCityCount: Int,
    private val diagnostics: ((Diagnostic) -> Unit)?
) {
    internal var strategicPriorities: StrategicPriorities? = null
        private set

    internal val diagnosticsEnabled: Boolean get() = diagnostics != null

    internal fun prioritiesFor(civ: Civilization): StrategicPriorities? {
        if (!civ.isAI()) return null
        return strategicPriorities ?: StrategicDirector.analyze(civ, this).also { strategicPriorities = it }
    }

    companion object {
        /** Includes construction decisions from turn preparation and cleanup. */
        internal inline fun <T> duringTurn(civ: Civilization, action: () -> T): T {
            if (!civ.isAI() || civ.isBarbarian || civ.isSpectator) return action()
            val previous = civ.cache.activeAdvancedAIContext
            civ.cache.activeAdvancedAIContext = AdvancedAIContext(civ.gameInfo.turns, civ.civID, civ.cities.size, null)
            try {
                return action()
            } finally {
                civ.cache.activeAdvancedAIContext = previous
            }
        }
    }

    data class Diagnostic(
        val turn: Int,
        val civId: String,
        val ownCityCount: Int,
        val category: String,
        val choice: String?,
        val reason: String?,
        val score: Double?,
        val elapsedNanos: Long?
    )

    fun record(
        category: String,
        choice: String? = null,
        reason: String? = null,
        score: Double? = null,
        elapsedNanos: Long? = null
    ) {
        val sink = diagnostics ?: return
        try {
            sink(Diagnostic(turn, civId, ownCityCount, category, choice, reason, score, elapsedNanos))
        } catch (exception: Exception) {
            Log.error("Failed to publish an Advanced AI diagnostic", exception)
        }
    }
}
