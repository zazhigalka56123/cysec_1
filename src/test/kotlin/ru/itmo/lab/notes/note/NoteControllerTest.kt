package ru.itmo.lab.notes.note

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.core.userdetails.User
import org.springframework.security.provisioning.JdbcUserDetailsManager
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.SecureRandom
import java.util.Base64

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
internal class NoteControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jdbcClient: JdbcClient,
    private val userDetailsManager: JdbcUserDetailsManager,
) {

    @BeforeEach
    fun prepareUsers() {
        jdbcClient.sql("DELETE FROM notes").update()
        if (!userDetailsManager.userExists("another-student")) {
            userDetailsManager.createUser(
                User.withUsername("another-student")
                    .password(requireNotNull(userDetailsManager.loadUserByUsername("student").password))
                    .roles("USER")
                    .build(),
            )
        }
    }

    @Test
    fun `when note contains HTML, should escape output and preserve original data`() {
        mockMvc.perform(
            post("/api/data")
                .with(jwt().jwt { it.subject("student") })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"<script>alert(1)</script>","content":"' OR '1'='1' --"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.title").value("&lt;script&gt;alert(1)&lt;/script&gt;"))
        mockMvc.perform(get("/api/data").with(jwt().jwt { it.subject("student") }))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].title").value("&lt;script&gt;alert(1)&lt;/script&gt;"))
        val content = jdbcClient.sql("SELECT content FROM notes WHERE username = :username")
            .param("username", "student")
            .query(String::class.java)
            .single()
        assertThat(content).isEqualTo("' OR '1'='1' --")
    }

    @Test
    fun `when another user requests notes, should hide notes of their owner`() {
        mockMvc.perform(
            post("/api/data")
                .with(jwt().jwt { it.subject("student") })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Private","content":"Only for the owner"}"""),
        )
            .andExpect(status().isCreated)
        mockMvc.perform(get("/api/data").with(jwt().jwt { it.subject("another-student") }))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isEmpty)
    }

    @Test
    fun `when note is blank or too long, should reject input`() {
        listOf("", "a".repeat(121)).forEach { title ->
            mockMvc.perform(
                post("/api/data")
                    .with(jwt().jwt { it.subject("student") })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"$title","content":"Text"}"""),
            )
                .andExpect(status().isBadRequest)
        }
    }

    companion object {

        private val secret = Base64.getEncoder().encodeToString(SecureRandom().generateSeed(32))

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("security.jwt.secret") { secret }
        }
    }
}
