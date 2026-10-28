package dev.jorgemiguel.nutrikit.patients.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

/**
 * Pacientes activos del consultorio actual. El filtro de tenant, RLS y {@code @SQLRestriction}
 * se aplican a todas las consultas: no hay forma de pedir pacientes de otro consultorio ni dados
 * de baja por este repositorio.
 */
public interface PacienteRepository extends Repository<Paciente, UUID>, JpaSpecificationExecutor<Paciente> {

	Paciente save(Paciente paciente);

	Optional<Paciente> findById(UUID id);

	@Override
	Page<Paciente> findAll(Specification<Paciente> spec, Pageable pageable);

}
