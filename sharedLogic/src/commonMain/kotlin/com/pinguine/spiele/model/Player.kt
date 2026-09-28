package com.pinguine.spiele.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A person that can join any game.
 *
 * [id] is the normalized username (see [Usernames.normalize]) and is how devices recognise the same
 * person when data is merged. [aliases] keeps earlier usernames so renames and merged duplicates
 * carry over to other devices.
 */
@Serializable
@SerialName("player")
data class Player(
    val id: String,
    val name: String,
    val aliases: Set<String> = emptySet(),
    val archived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
