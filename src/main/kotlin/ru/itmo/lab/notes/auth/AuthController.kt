package ru.itmo.lab.notes.auth

import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant

@RestController
@RequestMapping("/auth")
internal class AuthController(
    private val authenticationManager: AuthenticationManager,
    private val jwtEncoder: JwtEncoder,
    @Value($$"${security.jwt.issuer}") private val issuer: String,
) {

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<TokenResponse> {
        if (request.password.toByteArray(Charsets.UTF_8).size > 72) {
            throw BadCredentialsException("Invalid credentials")
        }
        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(
                request.username,
                request.password,
            ),
        )
        val now = Instant.now()
        val lifetime = Duration.ofMinutes(15)
        val claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(authentication.name)
            .issuedAt(now)
            .expiresAt(now.plus(lifetime))
            .build()

        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        val token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims))
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(
                TokenResponse(
                    accessToken = token.tokenValue,
                    tokenType = "Bearer",
                    expiresIn = lifetime.seconds,
                ),
            )
    }
}
