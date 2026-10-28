package dev.jorgemiguel.nutrikit.patients.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

public interface HistoriaClinicaRepository extends Repository<HistoriaClinica, UUID> {

	HistoriaClinica save(HistoriaClinica historia);

	Optional<HistoriaClinica> findByPacienteId(UUID pacienteId);

}
