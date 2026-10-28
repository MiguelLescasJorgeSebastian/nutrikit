package dev.jorgemiguel.nutrikit.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import dev.jorgemiguel.nutrikit.shared.LimitadorDeIntentos.Clave;

class LimitadorDeIntentosTest {

	/** Reloj que la prueba avanza a mano. */
	private static final class RelojManual extends Clock {

		private Instant ahora = Instant.parse("2026-10-27T12:00:00Z");

		void avanzar(Duration tiempo) {
			ahora = ahora.plus(tiempo);
		}

		@Override
		public Instant instant() {
			return ahora;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zona) {
			return this;
		}

	}

	private final RelojManual reloj = new RelojManual();
	private final LimitadorDeIntentos limitador = new LimitadorDeIntentos(Duration.ofMinutes(15), reloj);
	private final Clave ana = Clave.cuenta("t1", "ana@ana.mx", 3);

	private void fallar(int veces) {
		for (int i = 0; i < veces; i++) {
			limitador.registrarFallo(ana);
			reloj.avanzar(Duration.ofMinutes(1));
		}
	}

	@Test
	void permiteHastaElMaximoDeFallos() {
		fallar(2);

		assertThatCode(() -> limitador.verificar(ana)).doesNotThrowAnyException();
	}

	@Test
	void bloqueaAlLlegarAlMaximoEIndicaCuantoEsperar() {
		fallar(3); // a las 12:00, 12:01 y 12:02; ahora son las 12:03

		assertThatThrownBy(() -> limitador.verificar(ana))
				.isInstanceOfSatisfying(DemasiadosIntentosException.class,
						e -> assertThat(e.getReintentarEn()).isEqualTo(Duration.ofMinutes(12)));
	}

	@Test
	void laVentanaEsDeslizante() {
		fallar(3);

		reloj.avanzar(Duration.ofMinutes(12)); // el fallo de las 12:00 sale de la ventana

		assertThatCode(() -> limitador.verificar(ana)).doesNotThrowAnyException();
	}

	@Test
	void unAccesoCorrectoReiniciaElConteoDelCorreo() {
		fallar(2);

		limitador.reiniciar(ana);
		fallar(2);

		assertThatCode(() -> limitador.verificar(ana)).doesNotThrowAnyException();
	}

	@Test
	void cadaClaveTieneSuPropioConteo() {
		fallar(3);

		assertThatCode(() -> limitador.verificar(Clave.cuenta("t1", "beto@ana.mx", 3))).doesNotThrowAnyException();
		assertThatCode(() -> limitador.verificar(Clave.cuenta("t2", "ana@ana.mx", 3))).doesNotThrowAnyException();
	}

}
