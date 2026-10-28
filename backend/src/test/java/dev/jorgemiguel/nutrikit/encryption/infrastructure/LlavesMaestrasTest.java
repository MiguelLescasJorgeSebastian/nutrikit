package dev.jorgemiguel.nutrikit.encryption.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LlavesMaestrasTest {

	private static String llave() {
		return Base64.getEncoder().encodeToString(CifradorAesGcm.llaveNueva().getEncoded());
	}

	@Test
	void sinLlaveMaestraLaAppNoArranca() {
		assertThatThrownBy(() -> LlavesMaestras.desde(new CifradoProperties("", null, null)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("NUTRIKIT_LLAVES_MAESTRAS");
	}

	@Test
	void unaSolaLlaveEsLaActiva() {
		var maestras = LlavesMaestras.desde(new CifradoProperties("v1:" + llave(), null, null));

		assertThat(maestras.activa()).isEqualTo("v1");
	}

	@Test
	void conVariasHayQueIndicarLaActiva() {
		String varias = "v1:" + llave() + ", v2:" + llave();

		assertThatThrownBy(() -> LlavesMaestras.desde(new CifradoProperties(varias, null, null)))
				.hasMessageContaining("NUTRIKIT_LLAVE_MAESTRA_ACTIVA");
		assertThat(LlavesMaestras.desde(new CifradoProperties(varias, "v2", null)).activa()).isEqualTo("v2");
		assertThatThrownBy(() -> LlavesMaestras.desde(new CifradoProperties(varias, "v9", null)))
				.hasMessageContaining("v9");
	}

	@Test
	void rechazaLlavesQueNoMiden256Bits() {
		String corta = Base64.getEncoder().encodeToString(new byte[16]);

		assertThatThrownBy(() -> LlavesMaestras.desde(new CifradoProperties("v1:" + corta, null, null)))
				.hasMessageContaining("32 bytes");
	}

	@Test
	void elTextoDeLasLlavesNoIncluyeLasLlaves() {
		String secreta = llave();

		assertThat(LlavesMaestras.desde(new CifradoProperties("v1:" + secreta, null, null)).toString())
				.doesNotContain(secreta).contains("v1");
	}

	@Test
	void enDesarrolloCreaUnaLlaveLocalYLaConservaEntreReinicios(@TempDir Path carpeta) throws Exception {
		Path archivo = carpeta.resolve("nutrikit/llave-maestra-dev");

		var primera = LlavesMaestras.desde(new CifradoProperties(null, null, archivo));
		var segunda = LlavesMaestras.desde(new CifradoProperties(null, null, archivo));

		assertThat(primera.llaveActiva().getEncoded()).isEqualTo(segunda.llaveActiva().getEncoded());
		assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(archivo))).isEqualTo("rw-------");
	}

}
