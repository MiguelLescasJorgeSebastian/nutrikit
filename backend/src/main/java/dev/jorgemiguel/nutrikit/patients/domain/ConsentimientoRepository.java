package dev.jorgemiguel.nutrikit.patients.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Historial de consentimientos. El orden es por id: UUIDv7 crece con el tiempo y, a diferencia de
 * la fecha, nunca empata.
 */
public interface ConsentimientoRepository extends Repository<Consentimiento, UUID> {

	Consentimiento save(Consentimiento consentimiento);

	/** El último hecho del paciente: su estado actual. */
	Optional<Consentimiento> findFirstByPacienteIdOrderByIdDesc(UUID pacienteId);

	List<Consentimiento> findAllByPacienteIdOrderByIdAsc(UUID pacienteId);

	/**
	 * Pacientes activos cuyo consentimiento vigente es de una versión del aviso distinta de la
	 * actual: hay que pedirles que acepten la nueva.
	 */
	@Query("""
			SELECT c FROM Consentimiento c
			WHERE c.accion = dev.jorgemiguel.nutrikit.patients.domain.Consentimiento.Accion.OTORGADO
			  AND c.versionAviso <> :versionActual
			  AND c.id = (SELECT max(c2.id) FROM Consentimiento c2 WHERE c2.pacienteId = c.pacienteId)
			  AND c.pacienteId IN (SELECT p.id FROM Paciente p)
			ORDER BY c.id
			""")
	List<Consentimiento> findVigentesDeOtraVersion(@Param("versionActual") String versionActual);

}
