package com.pinguine.spiele.rules

import com.pinguine.spiele.model.Game
import com.pinguine.spiele.model.GameType
import com.pinguine.spiele.model.Round

internal interface GameRules {
    fun isRoundComplete(round: Round): Boolean

    /** Points each player gets for a complete round; empty for an incomplete one. */
    fun roundScores(round: Round, playerIds: List<String>): Map<String, Int>

    fun isOver(game: Game): Boolean

    /** Rounds to plan when a game starts; null means open-ended. */
    fun defaultPlannedRounds(playerCount: Int): Int?
}

internal val GameType.rules: GameRules
    get() = when (this) {
        GameType.SKYJO -> SkyjoRules
        GameType.BIBERBANDE -> BiberbandeRules
        GameType.WIZARD -> WizardRules
    }

internal val Game.rules: GameRules get() = type.rules

internal fun Game.completedRoundCount(): Int = rounds.count { rules.isRoundComplete(it) }

internal fun Game.roundScores(roundIndex: Int): Map<String, Int> =
    rules.roundScores(rounds[roundIndex], playerIds)

internal fun Game.totals(): Map<String, Int> {
    val totals = playerIds.associateWith { 0 }.toMutableMap()
    rounds.forEach { round ->
        rules.roundScores(round, playerIds).forEach { (id, score) -> totals[id] = (totals[id] ?: 0) + score }
    }
    return totals
}

/** Sets [Game.endedAt] when the game is over and clears it when a correction undoes the end. */
internal fun Game.withRecomputedEnd(timestamp: Long): Game {
    if (endedManually) return this
    val over = rules.isOver(this)
    return when {
        over && endedAt == null -> copy(endedAt = timestamp)
        !over && endedAt != null -> copy(endedAt = null)
        else -> this
    }
}
