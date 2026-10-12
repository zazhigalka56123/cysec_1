package ru.itmo.lab.notes.note

import java.util.UUID

internal data class NoteResponse(
    val id: UUID,
    val title: String,
    val content: String,
)
