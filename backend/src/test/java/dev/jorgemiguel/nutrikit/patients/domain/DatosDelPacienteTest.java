package dev.jorgemiguel.nutrikit.patients.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Reglas de los datos generales del paciente, sin Spring ni base de datos. */
class DatosDelPacienteTest {

	private static final LocalDate HOY = LocalDate.of(2026, 10, 27);

	private static DatosPersonales datos(String nombre, LocalDate nacimiento, String telefono, String email) {
		return DatosPersonales.de(nombre, "Pérez", nacimiento, Sexo.MASCULINO, telefono, email, null, HOY);
	}

	@Test
	void normalizaEspaciosTelefonoYCorreo() {
		var datos = DatosPersonales.de("  José   Luis ", " Pérez  García ", LocalDate.of(1988, 4, 17), Sexo.MASCULINO,
				"(55) 1234-5678", "  Jose.Perez@Correo.MX ", "  ", HOY);

		assertThat(datos.nombre()).isEqualTo("José Luis");
		assertThat(datos.apellidos()).isEqualTo("Pérez García");
		assertThat(datos.telefono().valor()).isEqualTo("5512345678");
		assertThat(datos.email().valor()).isEqualTo("jose.perez@correo.mx");
		assertThat(datos.ocupacion()).isNull();
	}

	@Test
	void telefonoYCorreoSonOpcionales() {
		var datos = datos("Ana", LocalDate.of(1990, 1, 1), null, "");

		assertThat(datos.telefono()).isNull();
		assertThat(datos.email()).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "+52 55 1234 5678", "55.1234.5678", "5512345678" })
	void aceptaTelefonosConSeparadores(String telefono) {
		assertThat(Telefono.deTexto(telefono).valor()).matches("^\\+?\\d{10,15}$");
	}

	@ParameterizedTest
	@ValueSource(strings = { "12345", "55-1234-ABCD", "++525512345678", "1234567890123456" })
	void rechazaTelefonosInvalidos(String telefono) {
		assertThatThrownBy(() -> Telefono.deTexto(telefono))
				.isInstanceOfSatisfying(DatoInvalidoException.class, e -> assertThat(e.getCampo()).isEqualTo("telefono"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "sin-arroba", "a@b", "con espacio@correo.mx", "@correo.mx" })
	void rechazaCorreosInvalidos(String email) {
		assertThatThrownBy(() -> CorreoElectronico.deTexto(email))
				.isInstanceOfSatisfying(DatoInvalidoException.class, e -> assertThat(e.getCampo()).isEqualTo("email"));
	}

	@Test
	void exigeNombreFechaYSexo() {
		assertThatThrownBy(() -> datos("  ", LocalDate.of(1990, 1, 1), null, null))
				.isInstanceOfSatisfying(DatoInvalidoException.class, e -> assertThat(e.getCampo()).isEqualTo("nombre"));
		assertThatThrownBy(() -> datos("Ana", null, null, null))
				.isInstanceOfSatisfying(DatoInvalidoException.class,
						e -> assertThat(e.getCampo()).isEqualTo("fechaNacimiento"));
		assertThatThrownBy(() -> DatosPersonales.de("Ana", "Pérez", LocalDate.of(1990, 1, 1), null, null, null, null, HOY))
				.isInstanceOfSatisfying(DatoInvalidoException.class, e -> assertThat(e.getCampo()).isEqualTo("sexo"));
	}

	@Test
	void rechazaNacimientosFuturosOAnterioresA1900() {
		assertThatThrownBy(() -> datos("Ana", HOY.plusDays(1), null, null)).hasMessageContaining("futura");
		assertThatThrownBy(() -> datos("Ana", LocalDate.of(1899, 12, 31), null, null)).hasMessageContaining("1900");
	}

	@ParameterizedTest(name = "nacido el {0}, el {1} tiene {2} años")
	@CsvSource({
			"1988-04-17, 2026-10-27, 38",
			"1988-10-27, 2026-10-27, 38", // hoy cumple
			"1988-10-28, 2026-10-27, 37", // cumple mañana
			"2024-02-29, 2026-02-28, 1",  // nacido en año bisiesto
			"2026-10-27, 2026-10-27, 0" })
	void laEdadSeCalculaDeLaFechaDeNacimiento(LocalDate nacimiento, LocalDate dia, int edad) {
		var paciente = new Paciente() {
		};
		paciente.actualizar(datos("Ana", nacimiento, null, null));

		assertThat(paciente.edad(dia)).isEqualTo(edad);
	}

	@Test
	void laBusquedaSeparaPalabrasYEscapaComodines() {
		assertThat(BusquedaDePacientes.terminos("  jose   per ")).containsExactly("jose", "per");
		assertThat(BusquedaDePacientes.terminos("a b c d e f g")).hasSize(5);
		assertThat(BusquedaDePacientes.terminos(null)).isEmpty();
		assertThat(BusquedaDePacientes.escapar("100%_a\\b")).isEqualTo("100\\%\\_a\\\\b");
	}

}
