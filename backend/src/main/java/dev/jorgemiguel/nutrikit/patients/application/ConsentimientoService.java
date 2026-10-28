package dev.jorgemiguel.nutrikit.patients.application;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jorgemiguel.nutrikit.patients.AvisoDePrivacidadPublicado;
import dev.jorgemiguel.nutrikit.patients.ConsentimientoOtorgado;
import dev.jorgemiguel.nutrikit.patients.ConsentimientoRetirado;
import dev.jorgemiguel.nutrikit.patients.domain.AvisoDePrivacidad;
import dev.jorgemiguel.nutrikit.patients.domain.AvisoDePrivacidadRepository;
import dev.jorgemiguel.nutrikit.patients.domain.Consentimiento;
import dev.jorgemiguel.nutrikit.patients.domain.ConsentimientoRepository;
import dev.jorgemiguel.nutrikit.patients.domain.Paciente;
import dev.jorgemiguel.nutrikit.patients.domain.PacienteRepository;
import dev.jorgemiguel.nutrikit.shared.Origen;
import dev.jorgemiguel.nutrikit.shared.TenantContext;

/**
 * Aviso de privacidad del consultorio y consentimiento de cada paciente (LFPDPPP).
 *
 * <p>Los datos de salud son sensibles y requieren consentimiento expreso. Otros casos de uso que
 * guardan datos de salud llaman a {@link #exigirVigente} antes de escribir: la regla vive aquí, en
 * un solo lugar, y se valida en la misma transacción que la escritura.
 */
@Service
@Transactional
public class ConsentimientoService {

	private final PacienteRepository pacientes;
	private final AvisoDePrivacidadRepository avisos;
	private final ConsentimientoRepository consentimientos;
	private final ApplicationEventPublisher eventos;
	private final Clock clock;

	ConsentimientoService(PacienteRepository pacientes, AvisoDePrivacidadRepository avisos,
			ConsentimientoRepository consentimientos, ApplicationEventPublisher eventos, Clock clock) {
		this.pacientes = pacientes;
		this.avisos = avisos;
		this.consentimientos = consentimientos;
		this.eventos = eventos;
		this.clock = clock;
	}

	// --- aviso de privacidad ------------------------------------------------------------------

	public AvisoDePrivacidad publicarAviso(String version, String contenido) {
		AvisoDePrivacidad aviso = AvisoDePrivacidad.publicar(version, contenido, clock.instant());
		if (avisos.existsByVersion(aviso.getVersion())) {
			throw ConflictoDeConsentimientoException.versionDuplicada(aviso.getVersion());
		}
		aviso = avisos.save(aviso);
		eventos.publishEvent(new AvisoDePrivacidadPublicado(TenantContext.requerido(),
				aviso.getId(), aviso.getVersion(), Origen.actual(), clock.instant()));
		return aviso;
	}

	@Transactional(readOnly = true)
	public Optional<AvisoDePrivacidad> avisoVigente() {
		return avisos.findFirstByOrderByPublicadoEnDescIdDesc();
	}

	@Transactional(readOnly = true)
	public List<AvisoDePrivacidad> avisos() {
		return avisos.findAllByOrderByPublicadoEnDescIdDesc();
	}

	// --- consentimiento -----------------------------------------------------------------------

	/** El paciente acepta la versión vigente del aviso. El cliente no elige la versión. */
	public Consentimiento otorgar(UUID pacienteId, Consentimiento.Medio medio) {
		Paciente paciente = paciente(pacienteId);
		AvisoDePrivacidad aviso = avisoVigente().orElseThrow(ConflictoDeConsentimientoException::sinAviso);
		Optional<Consentimiento> actual = consentimientos.findFirstByPacienteIdOrderByIdDesc(pacienteId);
		if (actual.filter(Consentimiento::estaOtorgado).map(Consentimiento::getVersionAviso)
				.filter(aviso.getVersion()::equals).isPresent()) {
			throw ConflictoDeConsentimientoException.yaVigente(aviso.getVersion());
		}
		Origen origen = Origen.actual();
		Consentimiento nuevo = consentimientos.save(
				Consentimiento.otorgar(paciente, aviso, medio, clock.instant(), origen.actorId()));
		eventos.publishEvent(new ConsentimientoOtorgado(paciente.getTenantId(), pacienteId, nuevo.getId(),
				nuevo.getVersionAviso(), origen, clock.instant()));
		return nuevo;
	}

	/** Agrega un retiro al historial; el otorgamiento anterior se conserva. */
	public Consentimiento retirar(UUID pacienteId, Consentimiento.Medio medio) {
		Paciente paciente = paciente(pacienteId);
		Consentimiento vigente = consentimientos.findFirstByPacienteIdOrderByIdDesc(pacienteId)
				.filter(Consentimiento::estaOtorgado)
				.orElseThrow(ConflictoDeConsentimientoException::nadaQueRetirar);
		Origen origen = Origen.actual();
		Consentimiento retiro = consentimientos.save(
				Consentimiento.retirar(paciente, vigente, medio, clock.instant(), origen.actorId()));
		eventos.publishEvent(new ConsentimientoRetirado(paciente.getTenantId(), pacienteId, retiro.getId(),
				retiro.getVersionAviso(), origen, clock.instant()));
		return retiro;
	}

	@Transactional(readOnly = true)
	public List<Consentimiento> historial(UUID pacienteId) {
		paciente(pacienteId);
		return consentimientos.findAllByPacienteIdOrderByIdAsc(pacienteId);
	}

	/** Consentimientos vigentes de una versión anterior del aviso: pacientes a quienes pedir la nueva. */
	@Transactional(readOnly = true)
	public List<Consentimiento> desactualizados() {
		return avisoVigente()
				.map(aviso -> consentimientos.findVigentesDeOtraVersion(aviso.getVersion()))
				.orElse(List.of());
	}

	/**
	 * Invariante: no se guardan datos de salud de un paciente sin consentimiento vigente. Un
	 * consentimiento de una versión anterior del aviso sigue vigente hasta que el paciente lo
	 * retire; se reporta en {@link #desactualizados} para pedirle que acepte la nueva.
	 *
	 * @throws ConflictoDeConsentimientoException si no tiene o lo retiró
	 */
	@Transactional(readOnly = true)
	public void exigirVigente(UUID pacienteId) {
		consentimientos.findFirstByPacienteIdOrderByIdDesc(pacienteId)
				.filter(Consentimiento::estaOtorgado)
				.orElseThrow(ConflictoDeConsentimientoException::requerido);
	}

	private Paciente paciente(UUID id) {
		return pacientes.findById(id).orElseThrow(() -> new PacienteNoEncontradoException(id));
	}

}
