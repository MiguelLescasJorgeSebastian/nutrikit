package dev.jorgemiguel.nutrikit.patients;

import java.time.Instant;
import java.util.UUID;

import dev.jorgemiguel.nutrikit.shared.Origen;

/** Se modificó la historia clínica de un paciente. Sin contenido clínico: solo identificadores. */
public record HistoriaClinicaActualizada(UUID tenantId, UUID pacienteId, UUID historiaId, Origen origen, Instant ocurridoEn) {
}
