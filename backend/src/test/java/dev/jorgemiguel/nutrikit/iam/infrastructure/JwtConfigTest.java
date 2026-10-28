package dev.jorgemiguel.nutrikit.iam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import dev.jorgemiguel.nutrikit.iam.application.SeguridadProperties;

/** La clave de firma viene de fuera del repositorio y tiene el tamaño mínimo de HS256. */
class JwtConfigTest {

	private final JwtConfig config = new JwtConfig();

	private static SeguridadProperties conSecreto(String secreto, boolean permitirEfimera) {
		return new SeguridadProperties(
				new SeguridadProperties.Jwt(secreto, permitirEfimera, Duration.ofMinutes(15), "nutrikit"),
				new SeguridadProperties.Refresh(Duration.ofDays(14), true),
				new SeguridadProperties.Intentos(5, 20, Duration.ofMinutes(15)),
				new SeguridadProperties.Operador(null, null));
	}

	@Test
	void sinSecretoLaAppNoArranca() {
		assertThatThrownBy(() -> config.claveJwt(conSecreto("", false)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("NUTRIKIT_JWT_SECRET");
	}

	@Test
	void rechazaUnSecretoDeMenosDe256Bits() {
		String corto = Base64.getEncoder().encodeToString(new byte[16]);

		assertThatThrownBy(() -> config.claveJwt(conSecreto(corto, false)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("32 bytes");
	}

	@Test
	void usaElSecretoConfigurado() {
		byte[] bytes = new byte[32];
		bytes[0] = 7;

		var clave = config.claveJwt(conSecreto(Base64.getEncoder().encodeToString(bytes), false));

		assertThat(clave.getEncoded()).isEqualTo(bytes);
	}

	@Test
	void enDesarrolloPuedeUsarUnaClaveEfimera() {
		assertThat(config.claveJwt(conSecreto(null, true)).getEncoded()).hasSize(32);
	}

	@Test
	void elAccessTokenNoPuedeDurarMasDe15Minutos() {
		assertThatThrownBy(() -> new SeguridadProperties(
				new SeguridadProperties.Jwt(null, true, Duration.ofMinutes(16), "nutrikit"),
				new SeguridadProperties.Refresh(Duration.ofDays(14), true),
				new SeguridadProperties.Intentos(5, 20, Duration.ofMinutes(15)),
				new SeguridadProperties.Operador(null, null)))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
