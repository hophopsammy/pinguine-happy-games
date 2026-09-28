package com.pinguine.spiele.rules

import com.pinguine.spiele.model.Game
import com.pinguine.spiele.model.PointsRound
import com.pinguine.spiele.model.Round

internal object SkyjoRules : GameRules {
    const val END_SCORE = 100

    override fun isRoundComplete(round: Round): Boolean = round is PointsRound

    override fun roundScores(round: Round, playerIds: List<String>): Map<String, Int> {
        if (round !is PointsRound) return emptyMap()
        return playerIds.associateWith { id ->
            val points = round.points[id] ?: 0
            if (isDoubled(round, id)) points * 2 else points
        }
    }

    /**
     * The player who ended the round has their points doubled when they are positive and another
     * player scored the same or fewer points.
     */
    fun isDoubled(round: PointsRound, playerId: String): Boolean {
        if (round.roundEnderId != playerId) return false
        val enderPoints = round.points[playerId] ?: return false
        if (enderPoints <= 0) return false
        return round.points.any { (id, points) -> id != playerId && points <= enderPoints }
    }

    override fun isOver(game: Game): Boolean =
        game.completedRoundCount() > 0 && game.totals().values.any { it >= END_SCORE }

    override fun defaultPlannedRounds(playerCount: Int): Int? = null
}
