package dev.jorgemiguel.nutrikit.iam.infrastructure;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import dev.jorgemiguel.nutrikit.iam.application.SeguridadProperties;

/**
 * Firma y validación de los access tokens con HMAC-SHA256 (HS256) y una clave simétrica.
 *
 * <p>La clave viene de {@code NUTRIKIT_JWT_SECRET}, nunca del repositorio. Sin ella la app no
 * arranca, salvo que se permita una clave efímera (perfil dev).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SeguridadProperties.class)
class JwtConfig {

	private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

	/** HS256 exige una clave de al menos 256 bits. */
	private static final int BYTES_MINIMOS = 32;

	@Bean
	SecretKey claveJwt(SeguridadProperties properties) {
		String secreto = properties.jwt().secreto();
		byte[] bytes;
		if (secreto == null || secreto.isBlank()) {
			if (!properties.jwt().permitirClaveEfimera()) {
				throw new IllegalStateException("Falta la clave de firma de JWT: define la variable de entorno "
						+ "NUTRIKIT_JWT_SECRET con al menos 32 bytes en Base64 (por ejemplo: openssl rand -base64 32)");
			}
			log.warn("Sin NUTRIKIT_JWT_SECRET: se usa una clave efímera. Los tokens dejan de servir al reiniciar.");
			bytes = new byte[BYTES_MINIMOS];
			new SecureRandom().nextBytes(bytes);
		}
		else {
			bytes = Base64.getDecoder().decode(secreto.strip());
			if (bytes.length < BYTES_MINIMOS) {
				throw new IllegalStateException("NUTRIKIT_JWT_SECRET debe tener al menos 32 bytes (256 bits)");
			}
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey claveJwt) {
		return NimbusJwtEncoder.withSecretKey(claveJwt).build();
	}

	/** Valida firma, algoritmo, expiración y emisor. */
	@Bean
	JwtDecoder jwtDecoder(SecretKey claveJwt, SeguridadProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(claveJwt).macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().emisor()));
		return decoder;
	}

	/**
	 * BCrypt hoy, con el prefijo del algoritmo en cada hash ({@code {bcrypt}...}): si mañana se
	 * cambia a Argon2, los hashes viejos siguen funcionando y se pueden migrar al iniciar sesión.
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	@ConditionalOnMissingBean
	Clock clock() {
		return Clock.systemUTC();
	}

}
