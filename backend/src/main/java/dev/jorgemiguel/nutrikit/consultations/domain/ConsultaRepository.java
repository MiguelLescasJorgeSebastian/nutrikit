package dev.jorgemiguel.nutrikit.consultations.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

public interface ConsultaRepository extends Repository<Consulta, UUID> {

	Consulta save(Consulta consulta);

	/** Con sus mediciones en la misma consulta SQL. */
	@EntityGraph(attributePaths = "mediciones")
	Optional<Consulta> findById(UUID id);

	/** Línea de tiempo: el orden lo define quien llama (fecha y id descendentes). */
	Page<Consulta> findByPacienteId(UUID pacienteId, Pageable pageable);

}
