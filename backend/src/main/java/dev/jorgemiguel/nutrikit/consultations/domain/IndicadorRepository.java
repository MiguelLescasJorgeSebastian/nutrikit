package dev.jorgemiguel.nutrikit.consultations.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Indicadores visibles para el consultorio actual: los globales más los propios. */
public interface IndicadorRepository extends Repository<Indicador, UUID> {

	Indicador save(Indicador indicador);

	/** Globales primero, después los propios; cada grupo por código. */
	@Query("SELECT i FROM Indicador i ORDER BY CASE WHEN i.tenantId IS NULL THEN 0 ELSE 1 END, i.codigo")
	List<Indicador> findAllVisibles();

	List<Indicador> findByCodigoIn(Collection<String> codigos);

	List<Indicador> findByIdIn(Collection<UUID> ids);

	boolean existsByCodigo(String codigo);

}
