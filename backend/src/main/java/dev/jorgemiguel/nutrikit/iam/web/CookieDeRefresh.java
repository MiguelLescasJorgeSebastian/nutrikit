package dev.jorgemiguel.nutrikit.iam.web;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import dev.jorgemiguel.nutrikit.iam.application.SeguridadProperties;

/**
 * Cookie que lleva el refresh token (ver docs/adr/0015-tokens-en-el-navegador.md):
 * <ul>
 * <li>{@code HttpOnly}: el JavaScript de la página no la puede leer, así que un XSS no se la
 * lleva.</li>
 * <li>{@code SameSite=Strict}: el navegador no la envía en peticiones que empiezan en otro sitio,
 * lo que cubre el CSRF contra renovar y cerrar sesión.</li>
 * <li>{@code Path=/api/auth}: solo viaja a renovar y cerrar sesión, no en cada llamada a la API.</li>
 * <li>Sin {@code Domain}: queda atada al subdominio del consultorio que la emitió.</li>
 * </ul>
 */
@Component
class CookieDeRefresh {

	static final String NOMBRE = "nutrikit_refresh";
	private static final String RUTA = "/api/auth";

	private final boolean segura;

	CookieDeRefresh(SeguridadProperties propiedades) {
		this.segura = propiedades.refresh().cookieSegura();
	}

	ResponseCookie con(String refreshToken, Instant expiraEn, Instant ahora) {
		return base(refreshToken).maxAge(Duration.between(ahora, expiraEn)).build();
	}

	/** Le pide al navegador borrarla: mismo nombre y ruta, vida cero. */
	ResponseCookie borrada() {
		return base("").maxAge(Duration.ZERO).build();
	}

	private ResponseCookie.ResponseCookieBuilder base(String valor) {
		return ResponseCookie.from(NOMBRE, valor).httpOnly(true).secure(segura).sameSite("Strict").path(RUTA);
	}

}
