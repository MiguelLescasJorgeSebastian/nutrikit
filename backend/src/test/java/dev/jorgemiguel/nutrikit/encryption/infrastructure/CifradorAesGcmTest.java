package dev.jorgemiguel.nutrikit.encryption.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import dev.jorgemiguel.nutrikit.encryption.DatoCifradoInvalidoException;

class CifradorAesGcmTest {

	private final SecretKey llave = CifradorAesGcm.llaveNueva();
	private final byte[] aad = "consultorio-a".getBytes(StandardCharsets.UTF_8);
	private final byte[] plano = "Alergia a la penicilina".getBytes(StandardCharsets.UTF_8);

	@Test
	void descifraLoQueCifro() {
		assertThat(CifradorAesGcm.descifrar(llave, CifradorAesGcm.cifrar(llave, plano, aad), aad)).isEqualTo(plano);
	}

	@Test
	void elMismoTextoCifradoDosVecesDaResultadosDistintos() {
		byte[] uno = CifradorAesGcm.cifrar(llave, plano, aad);
		byte[] otro = CifradorAesGcm.cifrar(llave, plano, aad);

		assertThat(uno).isNotEqualTo(otro);
		assertThat(Arrays.copyOf(uno, 12)).as("nonce").isNotEqualTo(Arrays.copyOf(otro, 12));
	}

	@Test
	void elResultadoNoContieneElTextoEnClaro() {
		String cifrado = new String(CifradorAesGcm.cifrar(llave, plano, aad), StandardCharsets.ISO_8859_1);

		assertThat(cifrado).doesNotContain("penicilina");
	}

	@Test
	void unByteAlteradoFallaEnLugarDeDevolverBasura() {
		byte[] cifrado = CifradorAesGcm.cifrar(llave, plano, aad);
		for (int i : new int[] { 0, 12, cifrado.length / 2, cifrado.length - 1 }) {
			byte[] alterado = cifrado.clone();
			alterado[i] ^= 0x01;

			assertThatThrownBy(() -> CifradorAesGcm.descifrar(llave, alterado, aad))
					.as("byte %d alterado", i)
					.isInstanceOf(DatoCifradoInvalidoException.class);
		}
	}

	@Test
	void otraLlaveUOtrosDatosAutenticadosNoDescifran() {
		byte[] cifrado = CifradorAesGcm.cifrar(llave, plano, aad);

		assertThatThrownBy(() -> CifradorAesGcm.descifrar(CifradorAesGcm.llaveNueva(), cifrado, aad))
				.isInstanceOf(DatoCifradoInvalidoException.class);
		assertThatThrownBy(() -> CifradorAesGcm.descifrar(llave, cifrado, "consultorio-b".getBytes(StandardCharsets.UTF_8)))
				.isInstanceOf(DatoCifradoInvalidoException.class);
	}

	@Test
	void unDatoTruncadoFalla() {
		assertThatThrownBy(() -> CifradorAesGcm.descifrar(llave, new byte[10], aad))
				.isInstanceOf(DatoCifradoInvalidoException.class);
	}

}
