package dev.jorgemiguel.nutrikit.patientaccess.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

public interface EnlaceDeAccesoRepository extends Repository<EnlaceDeAcceso, UUID> {

	EnlaceDeAcceso save(EnlaceDeAcceso enlace);

	/**
	 * Al regenerar, la revocación del enlace anterior debe llegar a la base antes del alta del
	 * nuevo: el índice único de "un enlace vigente por paciente" lo exige, y Hibernate ejecuta los
	 * INSERT antes que los UPDATE si se deja al final de la transacción.
	 */
	EnlaceDeAcceso saveAndFlush(EnlaceDeAcceso enlace);

	/** Búsqueda por hash: el token en claro nunca toca la base. Dentro del consultorio (RLS). */
	Optional<EnlaceDeAcceso> findByTokenHash(String tokenHash);

	Optional<EnlaceDeAcceso> findByPacienteIdAndRevocadoEnIsNull(UUID pacienteId);

}
