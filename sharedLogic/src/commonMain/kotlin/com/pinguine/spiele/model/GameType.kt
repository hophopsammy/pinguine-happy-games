package com.pinguine.spiele.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class GameType(
    val minPlayers: Int,
    val maxPlayers: Int,
    val lowestTotalWins: Boolean,
) {
    @SerialName("skyjo")
    SKYJO(minPlayers = 2, maxPlayers = 8, lowestTotalWins = true),

    @SerialName("biberbande")
    BIBERBANDE(minPlayers = 2, maxPlayers = 6, lowestTotalWins = true),

    @SerialName("wizard")
    WIZARD(minPlayers = 3, maxPlayers = 6, lowestTotalWins = false),
}
