package dev.jorgemiguel.nutrikit.patients;

import java.time.Instant;
import java.util.UUID;

import dev.jorgemiguel.nutrikit.shared.Origen;

/** Alguien abrió la historia clínica de un paciente. NOM-004 pide saber quién consultó el expediente. Sin contenido clínico: solo identificadores. */
public record HistoriaClinicaConsultada(UUID tenantId, UUID pacienteId, UUID historiaId, Origen origen, Instant ocurridoEn) {
}
