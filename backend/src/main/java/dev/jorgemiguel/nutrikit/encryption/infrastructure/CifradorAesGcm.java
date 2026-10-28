package dev.jorgemiguel.nutrikit.encryption.infrastructure;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import dev.jorgemiguel.nutrikit.encryption.DatoCifradoInvalidoException;

/**
 * AES-256-GCM: cifrado autenticado. El resultado es {@code nonce (12) || texto cifrado || tag (16)}.
 *
 * <ul>
 * <li>Nonce aleatorio de 96 bits en cada llamada: con la misma llave nunca debe repetirse, y con
 * 96 bits aleatorios la probabilidad es despreciable para el volumen de este sistema.</li>
 * <li>Los datos autenticados adicionales (AAD) no se cifran pero quedan protegidos por el tag:
 * si cambian, descifrar falla.</li>
 * </ul>
 */
public final class CifradorAesGcm {

	public static final int BYTES_LLAVE = 32;
	static final int BYTES_NONCE = 12;
	static final int BITS_TAG = 128;
	private static final String TRANSFORMACION = "AES/GCM/NoPadding";
	private static final SecureRandom ALEATORIO = new SecureRandom();

	private CifradorAesGcm() {
	}

	public static SecretKey llaveNueva() {
		return llaveDe(bytesAleatorios(BYTES_LLAVE));
	}

	public static SecretKey llaveDe(byte[] bytes) {
		if (bytes.length != BYTES_LLAVE) {
			throw new IllegalArgumentException("Una llave AES-256 mide 32 bytes");
		}
		return new SecretKeySpec(bytes, "AES");
	}

	public static byte[] cifrar(SecretKey llave, byte[] plano, byte[] aad) {
		byte[] nonce = bytesAleatorios(BYTES_NONCE);
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMACION);
			cipher.init(Cipher.ENCRYPT_MODE, llave, new GCMParameterSpec(BITS_TAG, nonce));
			cipher.updateAAD(aad);
			byte[] cifrado = cipher.doFinal(plano);
			return ByteBuffer.allocate(nonce.length + cifrado.length).put(nonce).put(cifrado).array();
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("No se pudo cifrar con AES-GCM", e);
		}
	}

	/** @throws DatoCifradoInvalidoException si el dato, el tag, la llave o el AAD no coinciden */
	public static byte[] descifrar(SecretKey llave, byte[] nonceYCifrado, byte[] aad) {
		if (nonceYCifrado.length < BYTES_NONCE + BITS_TAG / 8) {
			throw new DatoCifradoInvalidoException("El dato cifrado está truncado");
		}
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMACION);
			cipher.init(Cipher.DECRYPT_MODE, llave,
					new GCMParameterSpec(BITS_TAG, Arrays.copyOfRange(nonceYCifrado, 0, BYTES_NONCE)));
			cipher.updateAAD(aad);
			return cipher.doFinal(nonceYCifrado, BYTES_NONCE, nonceYCifrado.length - BYTES_NONCE);
		}
		catch (javax.crypto.AEADBadTagException e) {
			throw new DatoCifradoInvalidoException("El dato cifrado fue alterado o no corresponde a esta llave");
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("No se pudo descifrar con AES-GCM", e);
		}
	}

	private static byte[] bytesAleatorios(int cantidad) {
		byte[] bytes = new byte[cantidad];
		ALEATORIO.nextBytes(bytes);
		return bytes;
	}

}
