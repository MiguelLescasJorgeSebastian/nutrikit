package dev.jorgemiguel.nutrikit.shared;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita los inicios de sesión fallidos con una ventana deslizante por clave (IP y correo).
 * Cuando una clave llega al máximo de fallos dentro de la ventana, se rechazan los intentos
 * hasta que el fallo más antiguo sale de la ventana, aunque la contraseña sea correcta.
 *
 * <p>Cada módulo crea su propia instancia con su ventana (inicio de sesión, enlaces de pacientes), así
 * los conteos de uno no afectan al otro.
 *
 * <p>Vive en memoria: sirve con una sola instancia. Con varias réplicas, el conteo debe moverse
 * a un almacén compartido (Redis con Bucket4j, por ejemplo).
 */
public class LimitadorDeIntentos {

	/** Con este número de claves se purgan las que ya no tienen fallos en la ventana. */
	private static final int UMBRAL_LIMPIEZA = 10_000;

	private final Map<String, Deque<Instant>> fallos = new ConcurrentHashMap<>();
	private final Duration ventana;
	private final Clock clock;

	public LimitadorDeIntentos(Duration ventana, Clock clock) {
		this.ventana = ventana;
		this.clock = clock;
	}

	/** @throws DemasiadosIntentosException si alguna clave ya llegó a su máximo */
	public void verificar(Clave... claves) {
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

	public void registrarFallo(Clave... claves) {
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
	public void reiniciar(Clave clave) {
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
	public record Clave(String id, int maximo) {

		public static Clave ip(String ip, int maximo) {
			return new Clave("ip:" + ip, maximo);
		}

		public static Clave cuenta(String ambito, String email, int maximo) {
			return new Clave("cuenta:" + ambito + ":" + email, maximo);
		}

		/** Cualquier otra cosa que se limite, por ejemplo un enlace privado. */
		public static Clave de(String tipo, String id, int maximo) {
			return new Clave(tipo + ":" + id, maximo);
		}

	}

}
