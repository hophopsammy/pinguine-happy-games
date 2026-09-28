package com.pinguine.spiele.persistence

import com.pinguine.spiele.model.Game
import com.pinguine.spiele.model.GameType
import com.pinguine.spiele.model.Player
import com.pinguine.spiele.model.PointsRound
import com.pinguine.spiele.model.WizardRound
import com.pinguine.spiele.rules.WizardRules
import com.pinguine.spiele.rules.withRecomputedEnd

/** Players and games for SwiftUI previews. */
internal object SampleData {
    private const val DAY = 24L * 60 * 60 * 1000
    private const val START = 1_788_000_000_000L // September 2026

    fun build(): SavedData {
        val names = listOf("Anna", "Ben", "Carla", "David")
        val players = names.mapIndexed { index, name ->
            Player(id = name.lowercase(), name = name, createdAt = START + index, updatedAt = START + index)
        }
        val (anna, ben, carla, david) = players.map { it.id }

        // The Biberbande sheet from the photo: 48 vs 44 over four rounds.
        val biberbande = Game(
            id = "sample-biberbande",
            type = GameType.BIBERBANDE,
            playerIds = listOf(ben, carla),
            plannedRounds = 4,
            rounds = listOf(5 to 17, 14 to 7, 13 to 7, 16 to 13).map { (b, c) -> PointsRound(mapOf(ben to b, carla to c)) },
            startedAt = START + DAY,
            updatedAt = START + DAY + 3_600_000,
        ).withRecomputedEnd(START + DAY + 3_600_000)

        val wizardSeats = listOf(anna, ben, carla, david)
        val wizardRounds = (1..WizardRules.roundsFor(wizardSeats.size)).map { round ->
            val bids = wizardSeats.mapIndexed { seat, id -> id to ((round + seat) % (round + 1)) }.toMap()
            val tricks = wizardSeats.mapIndexed { seat, id ->
                val bid = bids.getValue(id)
                id to if ((round + seat) % 3 == 0) (bid + 1).coerceAtMost(round) else bid
            }.toMap()
            WizardRound(bids = bids, tricks = tricks)
        }
        val wizard = Game(
            id = "sample-wizard",
            type = GameType.WIZARD,
            playerIds = wizardSeats,
            plannedRounds = WizardRules.roundsFor(wizardSeats.size),
            rounds = wizardRounds,
            startedAt = START + 2 * DAY,
            updatedAt = START + 2 * DAY + 7_200_000,
        ).withRecomputedEnd(START + 2 * DAY + 7_200_000)

        val skyjo = Game(
            id = "sample-skyjo",
            type = GameType.SKYJO,
            playerIds = listOf(anna, ben, carla, david),
            rounds = listOf(
                PointsRound(mapOf(anna to 12, ben to 25, carla to -3, david to 18), roundEnderId = carla),
                PointsRound(mapOf(anna to 20, ben to 8, carla to 15, david to 9), roundEnderId = anna),
                PointsRound(mapOf(anna to 4, ben to 30, carla to 11, david to 2), roundEnderId = david),
            ),
            startedAt = START + 3 * DAY,
            updatedAt = START + 3 * DAY + 1_800_000,
        )

        return SavedData(
            players = players.inCanonicalOrder(),
            games = listOf(biberbande, wizard, skyjo).inCanonicalOrder(),
        )
    }
}
