package com.pinguine.spiele.model

internal object Usernames {
    private val whitespace = Regex("\\s+")

    /** The username as it should be displayed: trimmed, inner whitespace collapsed. */
    fun clean(raw: String): String = raw.trim().replace(whitespace, " ")

    /** The identity of a username: "  Anna  Maria " and "anna maria" are the same person. */
    fun normalize(raw: String): String = clean(raw).lowercase()
}
