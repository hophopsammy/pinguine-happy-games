package com.pinguine.spiele.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Round

/** Skyjo and Biberbande: the points each player scored. [roundEnderId] is only used by Skyjo. */
@Serializable
@SerialName("points")
data class PointsRound(
    val points: Map<String, Int>,
    val roundEnderId: String? = null,
) : Round

/** Wizard: bids are stored as soon as they are made; [tricks] stays null while the round is played. */
@Serializable
@SerialName("wizard")
data class WizardRound(
    val bids: Map<String, Int>,
    val tricks: Map<String, Int>? = null,
) : Round
