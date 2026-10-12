package ru.itmo.lab.notes.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.JdbcUserDetailsManager
import org.springframework.stereotype.Component

@Component
internal class CreateDefaultUser(
    private val userDetailsManager: JdbcUserDetailsManager,
    private val passwordEncoder: PasswordEncoder,
    @Value($$"${security.default-user.username}") private val username: String,
    @Value($$"${security.default-user.password}") private val password: String,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        userDetailsManager.createUser(
            User.withUsername(username)
                .password(passwordEncoder.encode(password))
                .roles("USER")
                .build(),
        )
    }
}
