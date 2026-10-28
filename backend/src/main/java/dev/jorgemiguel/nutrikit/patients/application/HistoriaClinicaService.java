package dev.jorgemiguel.nutrikit.patients.application;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jorgemiguel.nutrikit.patients.HistoriaClinicaActualizada;
import dev.jorgemiguel.nutrikit.patients.HistoriaClinicaConsultada;
import dev.jorgemiguel.nutrikit.patients.domain.ContenidoClinico;
import dev.jorgemiguel.nutrikit.patients.domain.HistoriaClinica;
import dev.jorgemiguel.nutrikit.patients.domain.HistoriaClinicaRepository;
import dev.jorgemiguel.nutrikit.patients.domain.Paciente;
import dev.jorgemiguel.nutrikit.patients.domain.PacienteRepository;
import dev.jorgemiguel.nutrikit.shared.Origen;

/**
 * Historia clínica de un paciente activo del consultorio. Cada lectura y cada cambio se publica
 * como evento para la bitácora: el expediente debe registrar quién lo consultó.
 */
@Service
@Transactional
public class HistoriaClinicaService {

	private final PacienteRepository pacientes;
	private final HistoriaClinicaRepository historias;
	private final ApplicationEventPublisher eventos;
	private final Clock clock;

	HistoriaClinicaService(PacienteRepository pacientes, HistoriaClinicaRepository historias,
			ApplicationEventPublisher eventos, Clock clock) {
		this.pacientes = pacientes;
		this.historias = historias;
		this.eventos = eventos;
		this.clock = clock;
	}

	/** Vacío si el paciente aún no tiene historia. No es solo lectura: la consulta se audita. */
	public Optional<HistoriaClinica> consultar(UUID pacienteId) {
		Paciente paciente = paciente(pacienteId);
		Optional<HistoriaClinica> historia = historias.findByPacienteId(pacienteId);
		eventos.publishEvent(new HistoriaClinicaConsultada(paciente.getTenantId(), pacienteId,
				historia.map(HistoriaClinica::getId).orElse(null), Origen.actual(), clock.instant()));
		return historia;
	}

	/**
	 * Crea o reemplaza la historia. Al editar una existente se exige la versión leída, igual que
	 * con los datos del paciente.
	 */
	public HistoriaClinica guardar(UUID pacienteId, Long versionLeida, ContenidoClinico contenido) {
		Paciente paciente = paciente(pacienteId);
		HistoriaClinica historia = historias.findByPacienteId(pacienteId).orElse(null);
		if (historia == null) {
			historia = HistoriaClinica.de(paciente);
		}
		else if (versionLeida == null || historia.getVersion() != versionLeida) {
			throw new VersionDesactualizadaException(versionLeida == null ? -1 : versionLeida, historia.getVersion());
		}
		historia.registrar(contenido, clock.instant());
		historia = historias.save(historia);
		eventos.publishEvent(new HistoriaClinicaActualizada(paciente.getTenantId(), pacienteId, historia.getId(),
				Origen.actual(), clock.instant()));
		return historia;
	}

	private Paciente paciente(UUID id) {
		return pacientes.findById(id).orElseThrow(() -> new PacienteNoEncontradoException(id));
	}

}
