package ru.itmo.lab.notes.auth

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

internal data class LoginRequest(
    @field:NotBlank
    @field:Size(max = 64)
    val username: String,
    @field:NotBlank
    val password: String,
)
