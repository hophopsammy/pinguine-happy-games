package com.pinguine.spiele.rules

import com.pinguine.spiele.model.Game

internal data class Standing(val playerId: String, val total: Int, val rank: Int)

/** Players ordered from best to worst; tied players share a rank (1, 1, 3). */
internal fun Game.standings(): List<Standing> {
    val totals = totals()
    val sign = if (type.lowestTotalWins) 1 else -1
    val ordered = playerIds.sortedWith(compareBy { sign * totals.getValue(it) })
    var previousTotal: Int? = null
    var previousRank = 0
    return ordered.mapIndexed { index, id ->
        val total = totals.getValue(id)
        val rank = if (total == previousTotal) previousRank else index + 1
        previousTotal = total
        previousRank = rank
        Standing(id, total, rank)
    }
}

/** Everyone tied for best; empty before the first complete round. */
internal fun Game.leaderIds(): Set<String> =
    if (completedRoundCount() == 0) emptySet()
    else standings().filter { it.rank == 1 }.map { it.playerId }.toSet()

/** A finished game counts for statistics once at least one round was completed. */
internal fun Game.countsForStatistics(): Boolean = isFinished && completedRoundCount() > 0

internal fun Game.winnerIds(): Set<String> = if (isFinished) leaderIds() else emptySet()
