package ru.itmo.lab.notes

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
internal class NotesApplication

fun main(args: Array<String>) {
    runApplication<NotesApplication>(*args)
}
