package ru.itmo.lab.notes.note

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.HtmlUtils

@RestController
@RequestMapping("/api/data")
internal class NoteController(
    private val noteRepository: NoteRepository,
) {

    @GetMapping
    fun getNotes(@AuthenticationPrincipal jwt: Jwt): List<NoteResponse> {
        return noteRepository.getNotes(jwt.subject).map { toResponse(it) }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createNote(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: CreateNoteRequest,
    ): NoteResponse {
        return toResponse(noteRepository.createNote(jwt.subject, request.title, request.content))
    }

    private fun toResponse(note: NoteLocal): NoteResponse {
        return NoteResponse(
            id = note.id,
            title = HtmlUtils.htmlEscape(note.title, "UTF-8"),
            content = HtmlUtils.htmlEscape(note.content, "UTF-8"),
        )
    }
}
