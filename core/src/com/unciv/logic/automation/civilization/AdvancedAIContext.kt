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

    internal fun analyzeStrategy(civ: Civilization) {
        strategicPriorities = StrategicDirector.analyze(civ, this)
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
