package com.pinguine.spiele.rules

import com.pinguine.spiele.model.Game
import com.pinguine.spiele.model.PointsRound
import com.pinguine.spiele.model.Round

internal object BiberbandeRules : GameRules {
    const val MAX_ROUNDS = 20

    override fun isRoundComplete(round: Round): Boolean = round is PointsRound

    override fun roundScores(round: Round, playerIds: List<String>): Map<String, Int> {
        if (round !is PointsRound) return emptyMap()
        return playerIds.associateWith { round.points[it] ?: 0 }
    }

    override fun isOver(game: Game): Boolean {
        val planned = game.plannedRounds ?: return false
        return game.completedRoundCount() >= planned
    }

    /** Official 2002 rule: as many rounds as there are players. */
    override fun defaultPlannedRounds(playerCount: Int): Int = playerCount
}
