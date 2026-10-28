package dev.jorgemiguel.nutrikit.patientaccess.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jorgemiguel.nutrikit.consultations.ConsultasDelPaciente;
import dev.jorgemiguel.nutrikit.patientaccess.AccesoPorEnlace;
import dev.jorgemiguel.nutrikit.patientaccess.AccesoPorEnlaceRechazado;
import dev.jorgemiguel.nutrikit.patientaccess.domain.EnlaceDeAcceso;
import dev.jorgemiguel.nutrikit.patientaccess.domain.EnlaceDeAccesoRepository;
import dev.jorgemiguel.nutrikit.patients.DatosGenerales;
import dev.jorgemiguel.nutrikit.patients.Pacientes;
import dev.jorgemiguel.nutrikit.shared.LimitadorDeIntentos;
import dev.jorgemiguel.nutrikit.shared.LimitadorDeIntentos.Clave;
import dev.jorgemiguel.nutrikit.shared.Origen;
import dev.jorgemiguel.nutrikit.shared.TipoDeActor;

/**
 * La página del paciente, a la que se entra solo con el enlace (y el PIN, si lo tiene). Solo
 * lectura y solo de ese paciente: el enlace se busca por hash dentro del consultorio del
 * subdominio, así que no hay forma de llegar a otro paciente ni a otro consultorio.
 *
 * <p>{@code noRollbackFor}: un PIN incorrecto se audita con un evento que solo se entrega si la
 * transacción se confirma; en ese camino no se escribe nada más.
 */
@Service
@Transactional(noRollbackFor = AccesoDenegadoException.class)
public class AccesoDelPacienteService {

	/** Enlaces o PIN fallidos por IP antes de bloquear: frena a quien prueba tokens o PINs al azar. */
	static final int FALLOS_POR_IP = 20;

	/** PIN incorrectos por enlace antes de bloquearlo un rato (un PIN de 4 dígitos son 10,000 opciones). */
	static final int FALLOS_DE_PIN_POR_ENLACE = 5;

	private final EnlaceDeAccesoRepository enlaces;
	private final Pacientes pacientes;
	private final ConsultasDelPaciente consultas;
	private final PasswordEncoder pinEncoder;
	private final LimitadorDeIntentos limitador;
	private final ApplicationEventPublisher eventos;
	private final Clock clock;

	AccesoDelPacienteService(EnlaceDeAccesoRepository enlaces, Pacientes pacientes, ConsultasDelPaciente consultas,
			PinEncoder pinEncoder, @Qualifier("limitadorDeEnlaces") LimitadorDeIntentos limitador,
			ApplicationEventPublisher eventos, Clock clock) {
		this.enlaces = enlaces;
		this.pacientes = pacientes;
		this.consultas = consultas;
		this.pinEncoder = pinEncoder.encoder();
		this.limitador = limitador;
		this.eventos = eventos;
		this.clock = clock;
	}

	/** Lo que ve el paciente. La visibilidad por indicador llega en la fase 2. */
	public record PaginaDelPaciente(DatosGenerales paciente, Optional<ConsultasDelPaciente.ResumenDeConsulta> ultimaConsulta,
			ConsultasDelPaciente.SerieDeEvolucion peso) {
	}

	/**
	 * @throws AccesoDenegadoException 404 si el enlace no existe o está revocado, 410 si caducó,
	 * 401 si falta o falla el PIN
	 * @throws dev.jorgemiguel.nutrikit.shared.DemasiadosIntentosException 429 tras varios fallos
	 */
	public PaginaDelPaciente abrir(String token, String pin, String ip) {
		Clave porIp = Clave.ip(ip, FALLOS_POR_IP);
		limitador.verificar(porIp);
		Instant ahora = clock.instant();

		EnlaceDeAcceso enlace = enlaces.findByTokenHash(TokensDeEnlace.hash(token))
				.filter(e -> !e.estaRevocado())
				.orElse(null);
		if (enlace == null) {
			limitador.registrarFallo(porIp);
			throw AccesoDenegadoException.noDisponible();
		}
		if (enlace.expiro(ahora)) {
			throw AccesoDenegadoException.caducado();
		}
		Origen origen = Origen.actual().conActor(TipoDeActor.PACIENTE, enlace.getPacienteId());
		if (enlace.tienePin()) {
			Clave porEnlace = Clave.de("enlace", enlace.getId().toString(), FALLOS_DE_PIN_POR_ENLACE);
			limitador.verificar(porIp, porEnlace);
			if (pin == null || pin.isBlank() || !pinEncoder.matches(pin, enlace.getPinHash())) {
				limitador.registrarFallo(porIp, porEnlace);
				eventos.publishEvent(new AccesoPorEnlaceRechazado(enlace.getTenantId(), enlace.getPacienteId(),
						enlace.getId(), origen.conActor(TipoDeActor.ANONIMO, null), ahora));
				throw AccesoDenegadoException.pinIncorrecto();
			}
			limitador.reiniciar(porEnlace);
		}
		DatosGenerales paciente = pacientes.datosGenerales(enlace.getPacienteId())
				.orElseThrow(AccesoDenegadoException::noDisponible);

		enlace.registrarUso(ahora);
		eventos.publishEvent(new AccesoPorEnlace(enlace.getTenantId(), enlace.getPacienteId(), enlace.getId(), origen, ahora));
		return new PaginaDelPaciente(paciente, consultas.ultima(enlace.getPacienteId()),
				consultas.serie(enlace.getPacienteId(), "peso"));
	}

}
