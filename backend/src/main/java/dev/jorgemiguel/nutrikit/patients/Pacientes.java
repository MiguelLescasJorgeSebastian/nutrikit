package dev.jorgemiguel.nutrikit.patients;

import java.util.UUID;

/**
 * API pública del módulo de pacientes para otros módulos. Responde preguntas sobre un paciente del
 * consultorio actual sin exponer la entidad ni sus repositorios: otro módulo guarda solo el
 * {@code paciente_id} y pregunta aquí.
 */
public interface Pacientes {

	/** Si el paciente existe en el consultorio actual y no está dado de baja. */
	boolean estaActivo(UUID pacienteId);

	/**
	 * Si hoy se pueden guardar sus datos de salud: tiene un consentimiento vigente (ADR-0010).
	 * Cualquier módulo que guarde datos de salud debe preguntarlo antes de escribir.
	 */
	boolean tieneConsentimientoVigente(UUID pacienteId);

}
