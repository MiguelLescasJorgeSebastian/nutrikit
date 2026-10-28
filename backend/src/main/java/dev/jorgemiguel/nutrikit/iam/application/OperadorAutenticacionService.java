package dev.jorgemiguel.nutrikit.iam.application;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import dev.jorgemiguel.nutrikit.shared.LimitadorDeIntentos;
import dev.jorgemiguel.nutrikit.shared.LimitadorDeIntentos.Clave;
import dev.jorgemiguel.nutrikit.iam.domain.Usuario;

/**
 * Inicio de sesión del operador de la plataforma, que da de alta los consultorios.
 *
 * <p>No es un usuario de la tabla {@code usuario}: no pertenece a ningún consultorio y sus
 * credenciales vienen de variables de entorno. Recibe solo un access token, sin refresh token:
 * al expirar, vuelve a iniciar sesión. Ver docs/adr/0005-autenticacion-jwt.md.
 */
@Service
public class OperadorAutenticacionService {

	private static final String AMBITO = "operador";

	private final SeguridadProperties properties;
	private final PasswordEncoder passwordEncoder;
	private final EmisorDeTokens emisor;
	private final LimitadorDeIntentos limitador;
	private final String hashSenuelo;

	OperadorAutenticacionService(SeguridadProperties properties, PasswordEncoder passwordEncoder,
			EmisorDeTokens emisor, @Qualifier("limitadorDeInicioDeSesion") LimitadorDeIntentos limitador) {
		this.properties = properties;
		this.passwordEncoder = passwordEncoder;
		this.emisor = emisor;
		this.limitador = limitador;
		this.hashSenuelo = passwordEncoder.encode("contraseña-señuelo-sin-dueño");
	}

	public SesionEmitida iniciarSesion(String email, String password, String ip) {
		String correo = Usuario.normalizarEmail(email);
		var porIp = Clave.ip(ip, properties.intentos().maximoPorIp());
		var porCuenta = Clave.cuenta(AMBITO, correo, properties.intentos().maximoPorEmail());
		limitador.verificar(porIp, porCuenta);

		var operador = properties.operador();
		boolean configurado = operador.configurado();
		boolean correoCoincide = configurado && iguales(Usuario.normalizarEmail(operador.email()), correo);
		boolean passwordCoincide = passwordEncoder.matches(password, configurado ? operador.passwordHash() : hashSenuelo);
		if (!correoCoincide || !passwordCoincide) {
			limitador.registrarFallo(porIp, porCuenta);
			throw new CredencialesInvalidasException();
		}

		limitador.reiniciar(porCuenta);
		var acceso = emisor.accesoParaOperador();
		return new SesionEmitida(acceso.valor(), acceso.expiraEn(), null, null);
	}

	/** Comparación en tiempo constante. */
	private static boolean iguales(String a, String b) {
		return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
	}

}
