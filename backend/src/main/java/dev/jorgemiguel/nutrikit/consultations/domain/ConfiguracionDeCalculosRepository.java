package dev.jorgemiguel.nutrikit.consultations.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

public interface ConfiguracionDeCalculosRepository extends Repository<ConfiguracionDeCalculos, UUID> {

	ConfiguracionDeCalculos save(ConfiguracionDeCalculos configuracion);

	Optional<ConfiguracionDeCalculos> findById(UUID tenantId);

}
