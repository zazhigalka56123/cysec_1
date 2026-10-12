package ru.itmo.lab.notes.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Configuration
internal class JwtConfiguration {

    @Bean
    fun jwtSecretKey(@Value($$"${security.jwt.secret}") secret: String): SecretKey {
        val bytes = Base64.getDecoder().decode(secret)
        require(bytes.size >= 32) { "JWT_SECRET must contain at least 32 random bytes encoded as Base64" }
        return SecretKeySpec(bytes, "HmacSHA256")
    }

    @Bean
    fun jwtEncoder(jwtSecretKey: SecretKey): JwtEncoder {
        return NimbusJwtEncoder.withSecretKey(jwtSecretKey)
            .algorithm(MacAlgorithm.HS256)
            .build()
    }

    @Bean
    fun jwtDecoder(
        jwtSecretKey: SecretKey,
        @Value($$"${security.jwt.issuer}") issuer: String,
    ): JwtDecoder {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build()
            .apply { setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer)) }
    }
}
