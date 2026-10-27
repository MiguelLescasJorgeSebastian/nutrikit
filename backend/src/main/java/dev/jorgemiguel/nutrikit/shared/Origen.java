package dev.jorgemiguel.nutrikit.shared;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Quién hizo algo y desde dónde. Viaja dentro de los eventos de dominio para que la bitácora lo
 * registre.
 *
 * <p>Se captura al publicar el evento, en el hilo de la petición: el listener corre después y en
 * otro hilo, donde ya no hay petición ni usuario autenticado.
 *
 * @param actorId {@code null} si el actor no tiene id (anónimo, sistema, operador)
 * @param ip {@code null} fuera de una petición HTTP
 */
public record Origen(TipoDeActor actorTipo, UUID actorId, String ip, String userAgent) {

	/** Rol del operador de la plataforma en el token (ver iam). */
	public static final String ROL_OPERADOR = "OPERADOR";

	/** Un user agent puede ser arbitrariamente largo; no se guarda más que esto. */
	private static final int USER_AGENT_MAXIMO = 500;

	/** Origen de la petición en curso: el usuario autenticado, su IP y su navegador. */
	public static Origen actual() {
		HttpServletRequest peticion = peticionActual();
		if (peticion == null) {
			return new Origen(TipoDeActor.SISTEMA, null, null, null);
		}
		String userAgent = peticion.getHeader("User-Agent");
		if (userAgent != null && userAgent.length() > USER_AGENT_MAXIMO) {
			userAgent = userAgent.substring(0, USER_AGENT_MAXIMO);
		}
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		Origen origen = new Origen(TipoDeActor.ANONIMO, null, peticion.getRemoteAddr(), userAgent);
		if (autenticacion == null || !autenticacion.isAuthenticated()
				|| autenticacion instanceof AnonymousAuthenticationToken) {
			return origen;
		}
		boolean esOperador = autenticacion.getAuthorities().stream()
				.anyMatch(a -> ("ROLE_" + ROL_OPERADOR).equals(a.getAuthority()));
		return esOperador ? origen.conActor(TipoDeActor.OPERADOR, null)
				: origen.conActor(TipoDeActor.USUARIO, comoUuid(autenticacion.getName()));
	}

	/** El mismo origen con otro actor; por ejemplo, el usuario que acaba de iniciar sesión. */
	public Origen conActor(TipoDeActor tipo, UUID id) {
		return new Origen(tipo, id, ip, userAgent);
	}

	private static HttpServletRequest peticionActual() {
		return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos
				? atributos.getRequest()
				: null;
	}

	private static UUID comoUuid(String valor) {
		try {
			return UUID.fromString(valor);
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}

}
