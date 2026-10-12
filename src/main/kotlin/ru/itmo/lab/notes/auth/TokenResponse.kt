package ru.itmo.lab.notes.auth

internal data class TokenResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
)
