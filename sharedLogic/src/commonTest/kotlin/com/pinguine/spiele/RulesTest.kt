package com.pinguine.spiele

import com.pinguine.spiele.model.GameType
import com.pinguine.spiele.rules.BiberbandeRules
import com.pinguine.spiele.rules.SkyjoRules
import com.pinguine.spiele.rules.WizardRules
import com.pinguine.spiele.rules.totals
import com.pinguine.spiele.rules.winnerIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkyjoRulesTest {
    private val seats = listOf("a", "b", "c")

    @Test
    fun enderWithStrictlyLowestScoreIsNotDoubled() {
        val round = points("a" to 5, "b" to 10, "c" to 6, ender = "a")
        assertEquals(mapOf("a" to 5, "b" to 10, "c" to 6), SkyjoRules.roundScores(round, seats))
    }

    @Test
    fun enderIsDoubledWhenSomeoneHasFewerPoints() {
        val round = points("a" to 10, "b" to 5, "c" to 12, ender = "a")
        assertEquals(20, SkyjoRules.roundScores(round, seats)["a"])
    }

    @Test
    fun enderIsDoubledOnATie() {
        val round = points("a" to 7, "b" to 7, "c" to 12, ender = "a")
        assertEquals(14, SkyjoRules.roundScores(round, seats)["a"])
    }

    @Test
    fun zeroOrNegativeEnderPointsAreNeverDoubled() {
        assertEquals(0, SkyjoRules.roundScores(points("a" to 0, "b" to -2, "c" to 3, ender = "a"), seats)["a"])
        assertEquals(-3, SkyjoRules.roundScores(points("a" to -3, "b" to -5, "c" to 3, ender = "a"), seats)["a"])
    }

    @Test
    fun onlyTheEnderIsDoubled() {
        val round = points("a" to 10, "b" to 5, "c" to 12, ender = "a")
        assertEquals(mapOf("a" to 20, "b" to 5, "c" to 12), SkyjoRules.roundScores(round, seats))
    }

    @Test
    fun gameEndsWhenATotalReachesExactly100() {
        val almost = game("g", GameType.SKYJO, seats, listOf(points("a" to 50, "b" to 1, "c" to 2), points("a" to 49, "b" to 1, "c" to 2)))
        assertFalse(SkyjoRules.isOver(almost))
        val reached = game("g", GameType.SKYJO, seats, listOf(points("a" to 50, "b" to 1, "c" to 2), points("a" to 50, "b" to 1, "c" to 2)))
        assertTrue(SkyjoRules.isOver(reached))
        assertTrue(reached.isFinished)
    }

    @Test
    fun lowestTotalWinsAndTiesShareTheWin() {
        val finished = game("g", GameType.SKYJO, seats, listOf(points("a" to 100, "b" to 40, "c" to 40)))
        assertEquals(setOf("b", "c"), finished.winnerIds())
    }
}

class WizardRulesTest {
    @Test
    fun roundsDependOnPlayerCount() {
        assertEquals(20, WizardRules.roundsFor(3))
        assertEquals(15, WizardRules.roundsFor(4))
        assertEquals(12, WizardRules.roundsFor(5))
        assertEquals(10, WizardRules.roundsFor(6))
    }

    @Test
    fun scoring() {
        assertEquals(20, WizardRules.score(bid = 0, tricks = 0))
        assertEquals(30, WizardRules.score(bid = 1, tricks = 1))
        assertEquals(40, WizardRules.score(bid = 2, tricks = 2))
        assertEquals(-10, WizardRules.score(bid = 0, tricks = 1))
        assertEquals(-20, WizardRules.score(bid = 3, tricks = 1))
        assertEquals(-20, WizardRules.score(bid = 0, tricks = 2))
    }

    @Test
    fun dealerRotatesBySeatAndTheNextSeatBidsFirst() {
        assertEquals(0, WizardRules.dealerIndex(roundNumber = 1, playerCount = 4))
        assertEquals(1, WizardRules.dealerIndex(roundNumber = 2, playerCount = 4))
        assertEquals(0, WizardRules.dealerIndex(roundNumber = 5, playerCount = 4))
        assertEquals(1, WizardRules.firstBidderIndex(roundNumber = 1, playerCount = 4))
        assertEquals(0, WizardRules.firstBidderIndex(roundNumber = 4, playerCount = 4))
    }

    @Test
    fun roundsWithoutTricksDontScore() {
        assertEquals(emptyMap(), WizardRules.roundScores(wizard(mapOf("a" to 1)), listOf("a")))
    }

    @Test
    fun gameEndsAfterTheLastPlannedRoundAndHighestWins() {
        val seats = listOf("a", "b", "c")
        val complete = (1..20).map { wizard(mapOf("a" to 0, "b" to 0, "c" to 0), mapOf("a" to 0, "b" to it % 2, "c" to 0)) }
        val almost = game("w", GameType.WIZARD, seats, complete.dropLast(1) + wizard(mapOf("a" to 0, "b" to 0, "c" to 0)), plannedRounds = 20)
        assertFalse(WizardRules.isOver(almost))
        val finished = game("w", GameType.WIZARD, seats, complete, plannedRounds = 20)
        assertTrue(WizardRules.isOver(finished))
        assertEquals(setOf("a", "c"), finished.winnerIds())
    }
}

class BiberbandeRulesTest {
    @Test
    fun theGameFromThePhoto() {
        val rounds = listOf(5 to 17, 14 to 7, 13 to 7, 16 to 13).map { (b, c) -> points("ben" to b, "carla" to c) }
        val finished = game("b", GameType.BIBERBANDE, listOf("ben", "carla"), rounds, plannedRounds = 4)
        assertEquals(mapOf("ben" to 48, "carla" to 44), finished.totals())
        assertTrue(finished.isFinished)
        assertEquals(setOf("carla"), finished.winnerIds())
    }

    @Test
    fun defaultRoundsEqualThePlayerCount() {
        assertEquals(2, BiberbandeRules.defaultPlannedRounds(2))
        assertEquals(6, BiberbandeRules.defaultPlannedRounds(6))
    }

    @Test
    fun gameIsNotOverBeforeThePlannedRounds() {
        val running = game("b", GameType.BIBERBANDE, listOf("a", "b"), listOf(points("a" to 3, "b" to 4)), plannedRounds = 2)
        assertNull(running.endedAt)
    }
}
