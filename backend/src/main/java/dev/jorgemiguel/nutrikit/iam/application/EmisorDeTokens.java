package dev.jorgemiguel.nutrikit.iam.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import dev.jorgemiguel.nutrikit.iam.domain.Usuario;

/** Genera access tokens (JWT firmados) y refresh tokens (aleatorios, opacos). */
@Component
class EmisorDeTokens {

	/** 256 bits: imposible de adivinar por fuerza bruta. */
	private static final int BYTES_REFRESH = 32;

	private final JwtEncoder encoder;
	private final SeguridadProperties properties;
	private final Clock clock;
	private final SecureRandom aleatorio = new SecureRandom();

	EmisorDeTokens(JwtEncoder encoder, SeguridadProperties properties, Clock clock) {
		this.encoder = encoder;
		this.properties = properties;
		this.clock = clock;
	}

	TokenDeAcceso accesoPara(Usuario usuario) {
		return firmar(usuario.getId().toString(), usuario.getRol().name(), usuario.getTenantId());
	}

	TokenDeAcceso accesoParaOperador() {
		return firmar("operador", ClaimsJwt.ROL_OPERADOR, null);
	}

	private TokenDeAcceso firmar(String sujeto, String rol, UUID tenantId) {
		Instant ahora = clock.instant();
		Instant expira = ahora.plus(properties.jwt().duracionAcceso());
		var claims = JwtClaimsSet.builder()
				.issuer(properties.jwt().emisor())
				.subject(sujeto)
				.id(UUID.randomUUID().toString())
				.issuedAt(ahora)
				.expiresAt(expira)
				.claim(ClaimsJwt.ROL, rol);
		if (tenantId != null) {
			claims.claim(ClaimsJwt.TENANT_ID, tenantId.toString());
		}
		var header = JwsHeader.with(MacAlgorithm.HS256).build();
		String valor = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
		return new TokenDeAcceso(valor, expira);
	}

	/** Valor en claro para el cliente; en la base solo se guarda {@link #hash}. */
	String nuevoRefreshToken() {
		byte[] bytes = new byte[BYTES_REFRESH];
		aleatorio.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** SHA-256 en hexadecimal (64 caracteres). Basta un hash rápido: el token ya es aleatorio. */
	static String hash(String refreshToken) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(refreshToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("La JVM no tiene SHA-256", e);
		}
	}

	record TokenDeAcceso(String valor, Instant expiraEn) {
	}

}
