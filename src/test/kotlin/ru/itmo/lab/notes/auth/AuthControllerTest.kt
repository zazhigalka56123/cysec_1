package ru.itmo.lab.notes.auth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
internal class AuthControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
    private val jdbcClient: JdbcClient,
    private val passwordEncoder: PasswordEncoder,
    private val jwtEncoder: JwtEncoder,
) {

    @Test
    fun `when credentials are valid, should issue a usable token`() {
        val response = mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"student","password":"test-password"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andReturn()
        val token = jsonMapper.readTree(response.response.contentAsString).get("accessToken").asText()
        mockMvc.perform(get("/api/data").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
    }

    @Test
    fun `when credentials contain SQL injection, should reject authentication`() {
        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"' OR '1'='1' --","password":"test-password"}"""),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `when password is wrong, should reject authentication`() {
        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"student","password":"wrong-password"}"""),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `when token is missing or malformed, should deny access`() {
        mockMvc.perform(get("/api/data")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/data").header("Authorization", "Bearer invalid.token.value"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(
            post("/api/data")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Note","content":"Text"}"""),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `when token is expired or issuer is wrong, should deny access`() {
        val now = Instant.now()
        val expiredToken = createToken("secure-notes", now.minusSeconds(3600), now.minusSeconds(1800))
        val wrongIssuerToken = createToken("another-app", now, now.plusSeconds(900))
        listOf(expiredToken, wrongIssuerToken).forEach { token ->
            mockMvc.perform(get("/api/data").header("Authorization", "Bearer $token"))
                .andExpect(status().isUnauthorized)
        }
    }

    @Test
    fun `when token signature is modified, should deny access`() {
        val now = Instant.now()
        val token = createToken("secure-notes", now, now.plusSeconds(900))
        val signatureStart = token.lastIndexOf('.') + 1
        val replacement = if (token[signatureStart] == 'A') "B" else "A"
        val modifiedToken = token.replaceRange(signatureStart, signatureStart + 1, replacement)
        mockMvc.perform(get("/api/data").header("Authorization", "Bearer $modifiedToken"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `when default user is created, should store a salted password hash`() {
        val hash = jdbcClient.sql("SELECT password FROM users WHERE username = :username")
            .param("username", "student")
            .query(String::class.java)
            .single()
        assertThat(hash).isNotEqualTo("test-password")
        assertThat(passwordEncoder.matches("test-password", hash)).isTrue()
        assertThat(passwordEncoder.encode("test-password")).isNotEqualTo(hash)
    }

    private fun createToken(issuer: String, issuedAt: Instant, expiresAt: Instant): String {
        val claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .subject("student")
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .build()
        return jwtEncoder.encode(
            JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims),
        ).tokenValue
    }

    companion object {

        private val secret = Base64.getEncoder().encodeToString(SecureRandom().generateSeed(32))

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("security.jwt.secret") { secret }
            registry.add("spring.datasource.url") { "jdbc:h2:mem:auth-test;DB_CLOSE_DELAY=-1" }
        }
    }
}
