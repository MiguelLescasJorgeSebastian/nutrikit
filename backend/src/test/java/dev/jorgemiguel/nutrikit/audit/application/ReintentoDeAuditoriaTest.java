package dev.jorgemiguel.nutrikit.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import dev.jorgemiguel.nutrikit.TestcontainersConfiguration;
import dev.jorgemiguel.nutrikit.shared.Origen;
import dev.jorgemiguel.nutrikit.shared.TenantContext;
import dev.jorgemiguel.nutrikit.shared.TipoDeActor;
import dev.jorgemiguel.nutrikit.tenants.TenantCreado;

/**
 * Si el listener falla, el evento no se pierde: queda pendiente en {@code event_publication} y al
 * reenviarlo se escribe el registro.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReintentoDeAuditoriaTest {

	@MockitoSpyBean
	private Bitacora bitacora;

	@Autowired
	private ApplicationEventPublisher eventos;

	@Autowired
	private TransactionTemplate tx;

	@Autowired
	private EventPublicationRegistry registro;

	@Autowired
	private IncompleteEventPublications pendientes;

	@Autowired
	private JdbcClient jdbc;

	private boolean pendiente(UUID tenantId) {
		return registro.findIncompletePublications().stream()
				.anyMatch(p -> p.getEvent() instanceof TenantCreado evento && evento.tenantId().equals(tenantId));
	}

	private int registrosDe(UUID tenantId) {
		return TenantContext.ejecutarComo(tenantId, () -> tx.execute(estado ->
				jdbc.sql("SELECT count(*) FROM auditoria").query(Integer.class).single()));
	}

	@Test
	void unEventoCuyoListenerFallaQuedaPendienteYSePuedeReintentar() {
		// La primera escritura falla (por ejemplo, la base no responde); la segunda funciona.
		doThrow(new IllegalStateException("falla simulada de la bitácora"))
				.doCallRealMethod()
				.when(bitacora).registrar(any());
		UUID tenant = UUID.randomUUID();
		var evento = new TenantCreado(tenant, "audit-reintento", new Origen(TipoDeActor.SISTEMA, null, null, null),
				Instant.now());

		tx.executeWithoutResult(estado -> eventos.publishEvent(evento));

		// El listener falló: no hay registro y la publicación sigue pendiente.
		await().atMost(Duration.ofSeconds(10)).until(() -> pendiente(tenant));
		assertThat(registrosDe(tenant)).isZero();

		pendientes.resubmitIncompletePublications(p -> p.getEvent().equals(evento));

		await().atMost(Duration.ofSeconds(10)).until(() -> registrosDe(tenant) == 1);
		await().atMost(Duration.ofSeconds(10)).until(() -> !pendiente(tenant));
	}

}
