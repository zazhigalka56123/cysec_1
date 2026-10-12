package ru.itmo.lab.notes.note

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

internal data class CreateNoteRequest(
    @field:NotBlank
    @field:Size(max = 120)
    val title: String,
    @field:NotBlank
    @field:Size(max = 2000)
    val content: String,
)
