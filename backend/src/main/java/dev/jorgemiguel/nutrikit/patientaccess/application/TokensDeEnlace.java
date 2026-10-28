package dev.jorgemiguel.nutrikit.patientaccess.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Token del enlace: 256 bits aleatorios en Base64 URL-safe (43 caracteres, sin relleno), que caben
 * en una ruta sin escaparse.
 *
 * <p>En la base se guarda su SHA-256. Un hash rápido basta porque el token tiene 256 bits de
 * entropía: no hay diccionario que probar. Un PIN de 4 dígitos sí se adivina por fuerza bruta, y
 * por eso usa Argon2, que es lento a propósito.
 */
final class TokensDeEnlace {

	private static final SecureRandom ALEATORIO = new SecureRandom();
	private static final int BYTES = 32;

	private TokensDeEnlace() {
	}

	static String nuevo() {
		byte[] bytes = new byte[BYTES];
		ALEATORIO.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String hash(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("La JVM no tiene SHA-256", e);
		}
	}

}
