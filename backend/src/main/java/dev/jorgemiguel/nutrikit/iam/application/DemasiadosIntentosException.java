package dev.jorgemiguel.nutrikit.iam.application;

import java.time.Duration;

/** Demasiados inicios de sesión fallidos; se responde 429 con {@code Retry-After}. */
public class DemasiadosIntentosException extends RuntimeException {

	private final Duration reintentarEn;

	public DemasiadosIntentosException(Duration reintentarEn) {
		super("Demasiados intentos fallidos. Intenta de nuevo más tarde.");
		this.reintentarEn = reintentarEn;
	}

	public Duration getReintentarEn() {
		return reintentarEn;
	}

}
