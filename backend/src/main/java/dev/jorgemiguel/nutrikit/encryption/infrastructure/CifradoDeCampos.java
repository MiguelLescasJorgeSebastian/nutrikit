package dev.jorgemiguel.nutrikit.encryption.infrastructure;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import org.springframework.stereotype.Component;

import dev.jorgemiguel.nutrikit.encryption.DatoCifradoInvalidoException;
import dev.jorgemiguel.nutrikit.shared.TenantContext;

/**
 * Cifra y descifra valores con la llave de datos del consultorio actual.
 *
 * <p>Formato de cada valor ({@code bytea}):
 *
 * <pre>
 * [formato: 1 byte = 1][versión de llave: 4 bytes][nonce: 12 bytes][texto cifrado + tag: n + 16 bytes]
 * </pre>
 *
 * El formato y la versión van en claro (hacen falta para saber cómo descifrar), pero forman parte
 * de los datos autenticados junto con el {@code tenant_id}: si alguien los cambia o copia el valor
 * a otro consultorio, descifrar falla.
 */
@Component
public class CifradoDeCampos {

	static final byte FORMATO = 1;
	private static final int BYTES_ENCABEZADO = 1 + 4;

	private final LlavesDeDatos llaves;

	CifradoDeCampos(LlavesDeDatos llaves) {
		this.llaves = llaves;
	}

	public byte[] cifrar(String texto) {
		if (texto == null) {
			return null;
		}
		UUID tenantId = TenantContext.requerido();
		var llave = llaves.activa(tenantId);
		byte[] cuerpo = CifradorAesGcm.cifrar(llave.llave(), texto.getBytes(StandardCharsets.UTF_8),
				aad(tenantId, llave.version()));
		return ByteBuffer.allocate(BYTES_ENCABEZADO + cuerpo.length).put(FORMATO).putInt(llave.version()).put(cuerpo).array();
	}

	public String descifrar(byte[] dato) {
		if (dato == null) {
			return null;
		}
		if (dato.length < BYTES_ENCABEZADO || dato[0] != FORMATO) {
			throw new DatoCifradoInvalidoException("Formato de dato cifrado desconocido");
		}
		UUID tenantId = TenantContext.requerido();
		int version = versionDe(dato);
		byte[] plano = CifradorAesGcm.descifrar(llaves.version(tenantId, version),
				Arrays.copyOfRange(dato, BYTES_ENCABEZADO, dato.length), aad(tenantId, version));
		return new String(plano, StandardCharsets.UTF_8);
	}

	/** Versión de la llave de datos con la que se cifró un valor. */
	public static int versionDe(byte[] dato) {
		return ByteBuffer.wrap(dato, 1, 4).getInt();
	}

	private static byte[] aad(UUID tenantId, int version) {
		return ByteBuffer.allocate(1 + 4 + 16).put(FORMATO).putInt(version)
				.putLong(tenantId.getMostSignificantBits()).putLong(tenantId.getLeastSignificantBits()).array();
	}

}
