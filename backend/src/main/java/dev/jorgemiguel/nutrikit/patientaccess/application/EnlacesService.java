package dev.jorgemiguel.nutrikit.patientaccess.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jorgemiguel.nutrikit.patientaccess.EnlaceConfigurado;
import dev.jorgemiguel.nutrikit.patientaccess.EnlaceGenerado;
import dev.jorgemiguel.nutrikit.patientaccess.EnlaceRevocado;
import dev.jorgemiguel.nutrikit.patientaccess.domain.EnlaceDeAcceso;
import dev.jorgemiguel.nutrikit.patientaccess.domain.EnlaceDeAccesoRepository;
import dev.jorgemiguel.nutrikit.patientaccess.domain.EnlaceInvalidoException;
import dev.jorgemiguel.nutrikit.patients.Pacientes;
import dev.jorgemiguel.nutrikit.shared.Origen;

/** Enlaces de los pacientes, desde el panel de la nutrióloga. */
@Service
@Transactional
public class EnlacesService {

	private static final Pattern FORMATO_PIN = Pattern.compile("^\\d{4,8}$");

	private final EnlaceDeAccesoRepository enlaces;
	private final Pacientes pacientes;
	private final PasswordEncoder pinEncoder;
	private final ApplicationEventPublisher eventos;
	private final Clock clock;

	EnlacesService(EnlaceDeAccesoRepository enlaces, Pacientes pacientes, PinEncoder pinEncoder,
			ApplicationEventPublisher eventos, Clock clock) {
		this.enlaces = enlaces;
		this.pacientes = pacientes;
		this.pinEncoder = pinEncoder.encoder();
		this.eventos = eventos;
		this.clock = clock;
	}

	/** El enlace recién emitido con su token en claro: se muestra una sola vez y no se guarda. */
	public record EnlaceEmitido(EnlaceDeAcceso enlace, String token) {

		@Override
		public String toString() {
			return "EnlaceEmitido[%s]".formatted(enlace);
		}

	}

	/** Emite un enlace nuevo y revoca el anterior en la misma transacción: el viejo deja de servir al instante. */
	public EnlaceEmitido generar(UUID pacienteId, String pin, Instant expiraEn) {
		exigirPacienteActivo(pacienteId);
		Instant ahora = clock.instant();
		enlaces.findByPacienteIdAndRevocadoEnIsNull(pacienteId).ifPresent(anterior -> {
			anterior.revocar(ahora);
			enlaces.saveAndFlush(anterior);
		});
		String token = TokensDeEnlace.nuevo();
		EnlaceDeAcceso enlace = enlaces.save(
				EnlaceDeAcceso.emitir(pacienteId, TokensDeEnlace.hash(token), hashDePin(pin), expiraEn, ahora));
		eventos.publishEvent(new EnlaceGenerado(enlace.getTenantId(), pacienteId, enlace.getId(), Origen.actual(), ahora));
		return new EnlaceEmitido(enlace, token);
	}

	@Transactional(readOnly = true)
	public Optional<EnlaceDeAcceso> vigente(UUID pacienteId) {
		exigirPacienteActivo(pacienteId);
		return enlaces.findByPacienteIdAndRevocadoEnIsNull(pacienteId);
	}

	/** Idempotente: sin enlace vigente no hay nada que revocar. */
	public void revocar(UUID pacienteId) {
		exigirPacienteActivo(pacienteId);
		enlaces.findByPacienteIdAndRevocadoEnIsNull(pacienteId).ifPresent(enlace -> {
			enlace.revocar(clock.instant());
			eventos.publishEvent(new EnlaceRevocado(enlace.getTenantId(), pacienteId, enlace.getId(), Origen.actual(),
					clock.instant()));
		});
	}

	/** Cambia PIN y caducidad sin cambiar el token; {@code null} quita cada uno. */
	public EnlaceDeAcceso configurar(UUID pacienteId, String pin, Instant expiraEn) {
		exigirPacienteActivo(pacienteId);
		EnlaceDeAcceso enlace = enlaces.findByPacienteIdAndRevocadoEnIsNull(pacienteId)
				.orElseThrow(AccesoDenegadoException::noDisponible);
		enlace.configurar(hashDePin(pin), expiraEn, clock.instant());
		eventos.publishEvent(new EnlaceConfigurado(enlace.getTenantId(), pacienteId, enlace.getId(), Origen.actual(),
				clock.instant()));
		return enlace;
	}

	private String hashDePin(String pin) {
		if (pin == null || pin.isBlank()) {
			return null;
		}
		if (!FORMATO_PIN.matcher(pin).matches()) {
			throw new EnlaceInvalidoException("pin", "de 4 a 8 dígitos");
		}
		return pinEncoder.encode(pin);
	}

	private void exigirPacienteActivo(UUID pacienteId) {
		if (!pacientes.estaActivo(pacienteId)) {
			throw AccesoDenegadoException.noDisponible();
		}
	}

}
