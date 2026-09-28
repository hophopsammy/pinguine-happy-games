package com.pinguine.spiele.rules

import com.pinguine.spiele.model.Game
import com.pinguine.spiele.model.Round
import com.pinguine.spiele.model.WizardRound
import kotlin.math.abs

internal object WizardRules : GameRules {
    private const val DECK_SIZE = 60

    fun roundsFor(playerCount: Int): Int = DECK_SIZE / playerCount

    fun score(bid: Int, tricks: Int): Int =
        if (bid == tricks) 20 + 10 * tricks else -10 * abs(bid - tricks)

    /** Seat index of the dealer; the first seat deals round 1. */
    fun dealerIndex(roundNumber: Int, playerCount: Int): Int = (roundNumber - 1) % playerCount

    fun firstBidderIndex(roundNumber: Int, playerCount: Int): Int =
        (dealerIndex(roundNumber, playerCount) + 1) % playerCount

    override fun isRoundComplete(round: Round): Boolean = round is WizardRound && round.tricks != null

    override fun roundScores(round: Round, playerIds: List<String>): Map<String, Int> {
        if (round !is WizardRound) return emptyMap()
        val tricks = round.tricks ?: return emptyMap()
        return playerIds.associateWith { score(round.bids[it] ?: 0, tricks[it] ?: 0) }
    }

    override fun isOver(game: Game): Boolean {
        val planned = game.plannedRounds ?: roundsFor(game.playerIds.size)
        return game.completedRoundCount() >= planned
    }

    override fun defaultPlannedRounds(playerCount: Int): Int = roundsFor(playerCount)
}
