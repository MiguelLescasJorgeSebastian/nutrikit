package dev.jorgemiguel.nutrikit.audit.application;

import java.time.Clock;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mantiene creadas las particiones de {@code auditoria} del mes actual y los dos siguientes. Corre
 * al arrancar y cada día; la función de la base es idempotente.
 *
 * <p>Si esta tarea dejara de correr, los registros de un mes sin partición fallarían, quedarían
 * pendientes en {@code event_publication} y se reintentarían cuando la partición exista: no se
 * pierde nada.
 */
@Component
class MantenimientoDeParticiones {

	private static final Logger log = LoggerFactory.getLogger(MantenimientoDeParticiones.class);
	private static final int MESES_ADELANTE = 2;

	private final JdbcClient jdbc;
	private final Clock clock;

	MantenimientoDeParticiones(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@EventListener(ApplicationReadyEvent.class)
	@Scheduled(cron = "${nutrikit.auditoria.particiones.cron:0 0 3 * * *}")
	void asegurarParticiones() {
		LocalDate mes = LocalDate.now(clock).withDayOfMonth(1);
		for (int i = 0; i <= MESES_ADELANTE; i++) {
			String particion = asegurar(mes.plusMonths(i));
			log.debug("Partición de auditoría lista: {}", particion);
		}
	}

	String asegurar(LocalDate mes) {
		return jdbc.sql("SELECT asegurar_particion_auditoria(?)").param(mes).query(String.class).single();
	}

}
