package dev.jorgemiguel.nutrikit.patients.application;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.jorgemiguel.nutrikit.patients.Pacientes;
import dev.jorgemiguel.nutrikit.patients.domain.Consentimiento;
import dev.jorgemiguel.nutrikit.patients.domain.ConsentimientoRepository;
import dev.jorgemiguel.nutrikit.patients.domain.PacienteRepository;

@Component
@Transactional(readOnly = true)
class PacientesAdapter implements Pacientes {

	private final PacienteRepository pacientes;
	private final ConsentimientoRepository consentimientos;

	PacientesAdapter(PacienteRepository pacientes, ConsentimientoRepository consentimientos) {
		this.pacientes = pacientes;
		this.consentimientos = consentimientos;
	}

	@Override
	public boolean estaActivo(UUID pacienteId) {
		return pacientes.findById(pacienteId).isPresent();
	}

	@Override
	public boolean tieneConsentimientoVigente(UUID pacienteId) {
		return consentimientos.findFirstByPacienteIdOrderByIdDesc(pacienteId)
				.filter(Consentimiento::estaOtorgado)
				.isPresent();
	}

}
