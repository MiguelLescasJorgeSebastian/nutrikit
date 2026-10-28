package dev.jorgemiguel.nutrikit.iam.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jorgemiguel.nutrikit.iam.InicioDeSesionExitoso;
import dev.jorgemiguel.nutrikit.iam.InicioDeSesionFallido;
import dev.jorgemiguel.nutrikit.shared.LimitadorDeIntentos;
import dev.jorgemiguel.nutrikit.shared.LimitadorDeIntentos.Clave;
import dev.jorgemiguel.nutrikit.iam.domain.RefreshToken;
import dev.jorgemiguel.nutrikit.iam.domain.RefreshTokenRepository;
import dev.jorgemiguel.nutrikit.iam.domain.Usuario;
import dev.jorgemiguel.nutrikit.iam.domain.UsuarioRepository;
import dev.jorgemiguel.nutrikit.shared.Origen;
import dev.jorgemiguel.nutrikit.shared.TenantContext;
import dev.jorgemiguel.nutrikit.shared.TipoDeActor;

/**
 * Inicio de sesión, renovación y cierre de sesión de los usuarios de un consultorio. Todo ocurre
 * dentro del consultorio de la petición: un usuario o un refresh token de otro consultorio no se
 * encuentra.
 */
@Service
@Transactional
public class AutenticacionService {

	private final UsuarioRepository usuarios;
	private final RefreshTokenRepository refreshTokens;
	private final PasswordEncoder passwordEncoder;
	private final EmisorDeTokens emisor;
	private final LimitadorDeIntentos limitador;
	private final SeguridadProperties properties;
	private final ApplicationEventPublisher eventos;
	private final Clock clock;

	/**
	 * Hash de una contraseña que nadie tiene. Si el correo no existe, se compara contra este para
	 * tardar lo mismo que con un correo real y no revelar cuáles existen por el tiempo de respuesta.
	 */
	private final String hashSenuelo;

	AutenticacionService(UsuarioRepository usuarios, RefreshTokenRepository refreshTokens,
			PasswordEncoder passwordEncoder, EmisorDeTokens emisor, @Qualifier("limitadorDeInicioDeSesion") LimitadorDeIntentos limitador,
			SeguridadProperties properties, ApplicationEventPublisher eventos, Clock clock) {
		this.usuarios = usuarios;
		this.refreshTokens = refreshTokens;
		this.passwordEncoder = passwordEncoder;
		this.emisor = emisor;
		this.limitador = limitador;
		this.properties = properties;
		this.eventos = eventos;
		this.clock = clock;
		this.hashSenuelo = passwordEncoder.encode("contraseña-señuelo-sin-dueño");
	}

	/**
	 * @throws DemasiadosIntentosException si la IP o el correo superaron los fallos permitidos
	 * @throws CredencialesInvalidasException si el correo no existe, la contraseña no coincide o
	 * el usuario está inactivo
	 *
	 * <p>{@code noRollbackFor}: el intento fallido se audita con un evento que solo se entrega si
	 * la transacción se confirma. En ese camino no se escribe nada más.
	 */
	@Transactional(noRollbackFor = CredencialesInvalidasException.class)
	public SesionEmitida iniciarSesion(String email, String password, String ip) {
		String correo = Usuario.normalizarEmail(email);
		var porIp = Clave.ip(ip, properties.intentos().maximoPorIp());
		var porCuenta = Clave.cuenta(TenantContext.requerido().toString(), correo, properties.intentos().maximoPorEmail());
		limitador.verificar(porIp, porCuenta);

		Optional<Usuario> encontrado = usuarios.findByEmail(correo);
		boolean coincide = passwordEncoder.matches(password, encontrado.map(Usuario::getPasswordHash).orElse(hashSenuelo));
		UUID tenantId = TenantContext.requerido();
		if (encontrado.isEmpty() || !coincide || !encontrado.get().isActivo()) {
			limitador.registrarFallo(porIp, porCuenta);
			eventos.publishEvent(new InicioDeSesionFallido(tenantId, encontrado.map(Usuario::getId).orElse(null),
					Origen.actual().conActor(TipoDeActor.ANONIMO, null), clock.instant()));
			throw new CredencialesInvalidasException();
		}

		limitador.reiniciar(porCuenta);
		Usuario usuario = encontrado.get();
		usuario.registrarAcceso(clock.instant());
		eventos.publishEvent(new InicioDeSesionExitoso(tenantId, usuario.getId(),
				Origen.actual().conActor(TipoDeActor.USUARIO, usuario.getId()), clock.instant()));
		return emitirSesion(usuario);
	}

	/**
	 * Cambia un refresh token por un par nuevo y revoca el usado (rotación).
	 *
	 * <p>Si llega un token ya revocado, alguien lo está reutilizando: o lo robaron, o el
	 * legítimo dueño ya lo cambió y el atacante llega después. No se sabe quién es quién, así que
	 * se revocan todas las sesiones del usuario. {@code noRollbackFor} hace que esa revocación se
	 * guarde aunque el método termine con excepción.
	 */
	@Transactional(noRollbackFor = SesionInvalidaException.class)
	public SesionEmitida renovar(String refreshToken) {
		Instant ahora = clock.instant();
		RefreshToken actual = refreshTokens.findByTokenHash(EmisorDeTokens.hash(refreshToken))
				.orElseThrow(SesionInvalidaException::new);

		if (actual.fueRevocado()) {
			refreshTokens.revocarTodosDelUsuario(actual.getUsuarioId(), ahora);
			throw new SesionInvalidaException();
		}
		if (actual.expiro(ahora)) {
			throw new SesionInvalidaException();
		}
		Usuario usuario = usuarios.findById(actual.getUsuarioId())
				.filter(Usuario::isActivo)
				.orElseThrow(SesionInvalidaException::new);

		actual.revocar(ahora);
		return emitirSesion(usuario);
	}

	/** Revoca el refresh token. Idempotente: un token desconocido o ya revocado no es error. */
	public void cerrarSesion(String refreshToken) {
		refreshTokens.findByTokenHash(EmisorDeTokens.hash(refreshToken))
				.ifPresent(token -> token.revocar(clock.instant()));
	}

	private SesionEmitida emitirSesion(Usuario usuario) {
		var acceso = emisor.accesoPara(usuario);
		String refresh = emisor.nuevoRefreshToken();
		Instant refreshExpira = clock.instant().plus(properties.refresh().duracion());
		refreshTokens.save(RefreshToken.emitir(usuario, EmisorDeTokens.hash(refresh), refreshExpira));
		return new SesionEmitida(acceso.valor(), acceso.expiraEn(), refresh, refreshExpira);
	}

}
