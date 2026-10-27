package dev.jorgemiguel.nutrikit.iam.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Limita los inicios de sesión fallidos con una ventana deslizante por clave (IP y correo).
 * Cuando una clave llega al máximo de fallos dentro de la ventana, se rechazan los intentos
 * hasta que el fallo más antiguo sale de la ventana, aunque la contraseña sea correcta.
 *
 * <p>Vive en memoria: sirve con una sola instancia. Con varias réplicas, el conteo debe moverse
 * a un almacén compartido (Redis con Bucket4j, por ejemplo).
 */
@Component
class LimitadorDeIntentos {

	/** Con este número de claves se purgan las que ya no tienen fallos en la ventana. */
	private static final int UMBRAL_LIMPIEZA = 10_000;

	private final Map<String, Deque<Instant>> fallos = new ConcurrentHashMap<>();
	private final Duration ventana;
	private final Clock clock;

	LimitadorDeIntentos(SeguridadProperties properties, Clock clock) {
		this.ventana = properties.intentos().ventana();
		this.clock = clock;
	}

	/** @throws DemasiadosIntentosException si alguna clave ya llegó a su máximo */
	void verificar(Clave... claves) {
		Instant ahora = clock.instant();
		Duration espera = Duration.ZERO;
		for (Clave clave : claves) {
			Deque<Instant> registro = fallos.get(clave.id());
			if (registro == null) {
				continue;
			}
			synchronized (registro) {
				descartarViejos(registro, ahora);
				if (registro.size() >= clave.maximo()) {
					Duration restante = Duration.between(ahora, registro.peekFirst().plus(ventana));
					espera = restante.compareTo(espera) > 0 ? restante : espera;
				}
			}
		}
		if (!espera.isZero()) {
			throw new DemasiadosIntentosException(espera);
		}
	}

	void registrarFallo(Clave... claves) {
		Instant ahora = clock.instant();
		if (fallos.size() > UMBRAL_LIMPIEZA) {
			limpiar(ahora);
		}
		for (Clave clave : claves) {
			Deque<Instant> registro = fallos.computeIfAbsent(clave.id(), id -> new ArrayDeque<>());
			synchronized (registro) {
				descartarViejos(registro, ahora);
				registro.addLast(ahora);
			}
		}
	}

	/** Tras un acceso correcto se olvidan los fallos del correo (no los de la IP). */
	void reiniciar(Clave clave) {
		fallos.remove(clave.id());
	}

	private void descartarViejos(Deque<Instant> registro, Instant ahora) {
		Instant limite = ahora.minus(ventana);
		while (!registro.isEmpty() && !registro.peekFirst().isAfter(limite)) {
			registro.pollFirst();
		}
	}

	private void limpiar(Instant ahora) {
		fallos.entrySet().removeIf(entrada -> {
			synchronized (entrada.getValue()) {
				descartarViejos(entrada.getValue(), ahora);
				return entrada.getValue().isEmpty();
			}
		});
	}

	/** Algo que se limita (una IP, un correo en un consultorio) y cuántos fallos se le permiten. */
	record Clave(String id, int maximo) {

		static Clave ip(String ip, int maximo) {
			return new Clave("ip:" + ip, maximo);
		}

		static Clave cuenta(String ambito, String email, int maximo) {
			return new Clave("cuenta:" + ambito + ":" + email, maximo);
		}

	}

}
