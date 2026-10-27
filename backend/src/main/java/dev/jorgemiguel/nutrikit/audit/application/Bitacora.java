package dev.jorgemiguel.nutrikit.audit.application;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.jorgemiguel.nutrikit.audit.domain.RegistroDeAuditoria;

/**
 * Escribe en {@code auditoria}. Usa SQL y no JPA: la tabla solo recibe {@code INSERT}, no hay
 * entidad que cargar ni modificar, y la columna {@code ip} es de tipo {@code inet}.
 *
 * <p>Debe llamarse dentro del consultorio del registro ({@code TenantContext}): RLS rechaza el
 * {@code INSERT} si {@code tenant_id} no coincide con el de la transacción.
 */
@Component
class Bitacora {

	private final JdbcClient jdbc;

	Bitacora(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional
	public void registrar(RegistroDeAuditoria registro) {
		var origen = registro.origen();
		jdbc.sql("""
				INSERT INTO auditoria (tenant_id, actor_tipo, actor_id, accion, recurso_tipo, recurso_id, ip, user_agent,
				                       ocurrido_en)
				VALUES (:tenantId, :actorTipo, :actorId, :accion, :recursoTipo, :recursoId, CAST(:ip AS inet),
				        :userAgent, :ocurridoEn)
				""")
				.param("tenantId", registro.tenantId())
				.param("actorTipo", origen.actorTipo().name())
				.param("actorId", origen.actorId())
				.param("accion", registro.accion())
				.param("recursoTipo", registro.recursoTipo())
				.param("recursoId", registro.recursoId())
				.param("ip", origen.ip())
				.param("userAgent", origen.userAgent())
				.param("ocurridoEn", java.sql.Timestamp.from(registro.ocurridoEn()))
				.update();
	}

}
