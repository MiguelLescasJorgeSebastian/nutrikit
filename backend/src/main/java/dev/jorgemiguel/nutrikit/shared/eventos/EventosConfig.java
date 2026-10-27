package dev.jorgemiguel.nutrikit.shared.eventos;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Eventos entre módulos: los listeners corren en otro hilo ({@code @Async}) después del commit, y
 * los que fallan se reintentan.
 *
 * <p>Spring Modulith guarda cada evento en {@code event_publication} en la misma transacción que
 * lo publica (outbox) y lo marca como completado cuando el listener termina bien. Si el listener
 * falla, la publicación queda pendiente; esta tarea las vuelve a enviar.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableScheduling
class EventosConfig {

	private static final Logger log = LoggerFactory.getLogger(EventosConfig.class);

	private final IncompleteEventPublications pendientes;
	private final Duration antiguedadMinima;

	EventosConfig(IncompleteEventPublications pendientes,
			@Value("${nutrikit.eventos.reintento.antiguedad-minima:5m}") Duration antiguedadMinima) {
		this.pendientes = pendientes;
		this.antiguedadMinima = antiguedadMinima;
	}

	/**
	 * Solo reenvía las que llevan un rato pendientes, para no duplicar un listener que todavía está
	 * corriendo.
	 */
	@Scheduled(fixedDelayString = "${nutrikit.eventos.reintento.cada:5m}",
			initialDelayString = "${nutrikit.eventos.reintento.cada:5m}")
	void reintentarPendientes() {
		log.debug("Reenviando publicaciones de eventos pendientes desde hace más de {}", antiguedadMinima);
		pendientes.resubmitIncompletePublicationsOlderThan(antiguedadMinima);
	}

}
