package dev.jorgemiguel.nutrikit.tenants.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SlugTest {

	@ParameterizedTest
	@ValueSource(strings = { "ana", "nutri-ana", "consultorio-2026", "a1b", "x-y-z" })
	void aceptaSlugsValidos(String valor) {
		assertThat(Slug.of(valor).valor()).isEqualTo(valor);
	}

	@Test
	void aceptaLaLongitudMaxima() {
		String sesentaCaracteres = "a".repeat(Slug.LONGITUD_MAXIMA);

		assertThat(Slug.of(sesentaCaracteres).valor()).hasSize(60);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {
			"ab",                // muy corto
			"Nutri-Ana",         // mayúsculas
			"nutri ana",         // espacio
			"nutri_ana",         // guion bajo
			"nutrióloga",        // acento
			"-ana",              // guion al inicio
			"ana-",              // guion al final
			"nutri--ana",        // guiones seguidos
			"",                  // vacío
	})
	void rechazaSlugsInvalidos(String valor) {
		assertThatThrownBy(() -> Slug.of(valor)).isInstanceOf(SlugInvalidoException.class);
	}

	@Test
	void rechazaSlugsDeMasDeSesentaCaracteres() {
		String sesentaYUno = "a".repeat(Slug.LONGITUD_MAXIMA + 1);

		assertThatThrownBy(() -> Slug.of(sesentaYUno))
				.isInstanceOf(SlugInvalidoException.class)
				.hasMessageContaining("entre 3 y 60");
	}

	@Test
	void dosSlugsConElMismoValorSonIguales() {
		assertThat(Slug.of("nutri-ana")).isEqualTo(Slug.of("nutri-ana"));
	}

}
