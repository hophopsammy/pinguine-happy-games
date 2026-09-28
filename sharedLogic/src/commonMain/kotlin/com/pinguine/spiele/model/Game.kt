package com.pinguine.spiele.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One played (or running) game. [playerIds] is the seat order, which is also the column order on the
 * sheet and the dealing order in Wizard. [plannedRounds] is null for open-ended games (Skyjo).
 */
@Serializable
@SerialName("game")
data class Game(
    val id: String,
    val type: GameType,
    val playerIds: List<String>,
    val plannedRounds: Int? = null,
    val rounds: List<Round> = emptyList(),
    val startedAt: Long,
    val updatedAt: Long,
    val endedAt: Long? = null,
    val endedManually: Boolean = false,
) {
    val isFinished: Boolean get() = endedAt != null
}
