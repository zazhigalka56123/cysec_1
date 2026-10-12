package ru.itmo.lab.notes.note

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
internal class NoteRepository(
    private val jdbcClient: JdbcClient,
) {

    fun getNotes(username: String): List<NoteLocal> {
        return jdbcClient.sql("SELECT id, title, content FROM notes WHERE username = :username ORDER BY id")
            .param("username", username)
            .query { row, _ ->
                NoteLocal(
                    id = row.getObject("id", UUID::class.java),
                    title = row.getString("title"),
                    content = row.getString("content"),
                )
            }
            .list()
    }

    fun createNote(username: String, title: String, content: String): NoteLocal {
        val note = NoteLocal(id = UUID.randomUUID(), title = title, content = content)
        jdbcClient.sql("INSERT INTO notes (id, username, title, content) VALUES (:id, :username, :title, :content)")
            .param("id", note.id)
            .param("username", username)
            .param("title", note.title)
            .param("content", note.content)
            .update()
        return note
    }
}
