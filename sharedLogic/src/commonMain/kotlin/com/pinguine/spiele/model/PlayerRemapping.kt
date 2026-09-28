package com.pinguine.spiele.model

/** Rewrites player references (seats, round entries, Skyjo round ender) through [mapping]. */
internal fun Game.remapPlayers(mapping: Map<String, String>): Game {
    if (playerIds.none { it in mapping }) return this
    fun id(value: String) = mapping[value] ?: value
    fun <V> Map<String, V>.remapKeys() = entries.associate { (key, value) -> id(key) to value }
    return copy(
        playerIds = playerIds.map(::id),
        rounds = rounds.map { round ->
            when (round) {
                is PointsRound -> round.copy(points = round.points.remapKeys(), roundEnderId = round.roundEnderId?.let(::id))
                is WizardRound -> round.copy(bids = round.bids.remapKeys(), tricks = round.tricks?.remapKeys())
            }
        },
    )
}
