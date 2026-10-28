package dev.jorgemiguel.nutrikit.iam.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración de autenticación ({@code nutrikit.seguridad.*}).
 *
 * @param jwt firma y vida del access token
 * @param refresh vida del refresh token
 * @param intentos límite de inicios de sesión fallidos
 * @param operador cuenta del operador de la plataforma; sin email, no hay operador
 */
@ConfigurationProperties("nutrikit.seguridad")
public record SeguridadProperties(
		@DefaultValue Jwt jwt,
		@DefaultValue Refresh refresh,
		@DefaultValue Intentos intentos,
		@DefaultValue Operador operador) {

	/** Máximo permitido para el access token: si se filtra, sirve por poco tiempo. */
	public static final Duration DURACION_MAXIMA_ACCESO = Duration.ofMinutes(15);

	public SeguridadProperties {
		if (jwt.duracionAcceso().compareTo(DURACION_MAXIMA_ACCESO) > 0) {
			throw new IllegalArgumentException("nutrikit.seguridad.jwt.duracion-acceso no puede pasar de 15 minutos");
		}
	}

	/**
	 * @param secreto clave HMAC en Base64, de al menos 256 bits; viene de NUTRIKIT_JWT_SECRET
	 * @param permitirClaveEfimera sin secreto, generar una clave al arrancar (solo desarrollo:
	 * los tokens dejan de servir al reiniciar)
	 */
	public record Jwt(
			String secreto,
			@DefaultValue("false") boolean permitirClaveEfimera,
			@DefaultValue("15m") Duration duracionAcceso,
			@DefaultValue("nutrikit") String emisor) {
	}

	/**
	 * @param duracion vida del refresh token
	 * @param cookieSegura marca la cookie del refresh token como {@code Secure}; solo se apaga en
	 * desarrollo, donde la API corre en http
	 */
	public record Refresh(@DefaultValue("14d") Duration duracion, @DefaultValue("true") boolean cookieSegura) {
	}

	/**
	 * @param maximoPorEmail fallos permitidos por correo dentro de la ventana
	 * @param maximoPorIp fallos permitidos por IP dentro de la ventana; más alto porque varias
	 * personas pueden compartir IP (un consultorio, una red móvil)
	 */
	public record Intentos(
			@DefaultValue("5") int maximoPorEmail,
			@DefaultValue("20") int maximoPorIp,
			@DefaultValue("15m") Duration ventana) {
	}

	/** @param passwordHash hash con prefijo de algoritmo, por ejemplo {@code {bcrypt}$2a$10$...} */
	public record Operador(String email, String passwordHash) {

		public boolean configurado() {
			return email != null && !email.isBlank() && passwordHash != null && !passwordHash.isBlank();
		}

	}

}
