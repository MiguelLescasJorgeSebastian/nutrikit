package dev.jorgemiguel.nutrikit.patients.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

public interface AvisoDePrivacidadRepository extends Repository<AvisoDePrivacidad, UUID> {

	AvisoDePrivacidad save(AvisoDePrivacidad aviso);

	/** La versión vigente es la última publicada; el id (UUIDv7) desempata. */
	Optional<AvisoDePrivacidad> findFirstByOrderByPublicadoEnDescIdDesc();

	Optional<AvisoDePrivacidad> findByVersion(String version);

	boolean existsByVersion(String version);

	List<AvisoDePrivacidad> findAllByOrderByPublicadoEnDescIdDesc();

}
