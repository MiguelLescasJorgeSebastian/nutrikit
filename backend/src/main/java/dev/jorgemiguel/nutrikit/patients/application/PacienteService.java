package dev.jorgemiguel.nutrikit.patients.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jorgemiguel.nutrikit.patients.PacienteActualizado;
import dev.jorgemiguel.nutrikit.patients.PacienteCreado;
import dev.jorgemiguel.nutrikit.patients.PacienteDadoDeBaja;
import dev.jorgemiguel.nutrikit.patients.domain.BusquedaDePacientes;
import dev.jorgemiguel.nutrikit.patients.domain.DatosPersonales;
import dev.jorgemiguel.nutrikit.patients.domain.Paciente;
import dev.jorgemiguel.nutrikit.patients.domain.PacienteRepository;
import dev.jorgemiguel.nutrikit.shared.Origen;

/** Casos de uso de pacientes, siempre dentro del consultorio de la petición. */
@Service
@Transactional
public class PacienteService {

	private final PacienteRepository repository;
	private final ApplicationEventPublisher eventos;
	private final Clock clock;

	PacienteService(PacienteRepository repository, ApplicationEventPublisher eventos, Clock clock) {
		this.repository = repository;
		this.eventos = eventos;
		this.clock = clock;
	}

	public Paciente registrar(DatosPersonales datos) {
		Paciente paciente = repository.save(Paciente.registrar(datos, clock.instant()));
		eventos.publishEvent(new PacienteCreado(paciente.getTenantId(), paciente.getId(), Origen.actual(), clock.instant()));
		return paciente;
	}

	@Transactional(readOnly = true)
	public Paciente obtener(UUID id) {
		return buscar(id);
	}

	@Transactional(readOnly = true)
	public Page<Paciente> buscar(String texto, Pageable pageable) {
		return repository.findAll(BusquedaDePacientes.por(texto), pageable);
	}

	/**
	 * Reemplaza los datos generales si {@code versionLeida} sigue siendo la actual.
	 *
	 * <p>Dos barreras contra ediciones que se pisan: esta comparación detecta a quien editó con
	 * datos viejos, y {@code @Version} detecta a dos ediciones que llegan al mismo tiempo con la
	 * misma versión (la segunda falla al hacer commit).
	 */
	public Paciente actualizar(UUID id, long versionLeida, DatosPersonales datos) {
		Paciente paciente = buscar(id);
		if (paciente.getVersion() != versionLeida) {
			throw new VersionDesactualizadaException(versionLeida, paciente.getVersion());
		}
		paciente.actualizar(datos);
		eventos.publishEvent(new PacienteActualizado(paciente.getTenantId(), id, Origen.actual(), clock.instant()));
		return paciente;
	}

	public void darDeBaja(UUID id) {
		Paciente paciente = buscar(id);
		paciente.darDeBaja(clock.instant());
		eventos.publishEvent(new PacienteDadoDeBaja(paciente.getTenantId(), id, Origen.actual(), clock.instant()));
	}

	/** Fecha de hoy para validar nacimientos y calcular edades. */
	public LocalDate hoy() {
		return LocalDate.now(clock);
	}

	private Paciente buscar(UUID id) {
		return repository.findById(id).orElseThrow(() -> new PacienteNoEncontradoException(id));
	}

}
