package dev.jorgemiguel.nutrikit.encryption.infrastructure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Llaves maestras (KEK) en memoria. Solo envuelven y desenvuelven llaves de datos; nunca cifran
 * datos directamente. Nada de esta clase escribe una llave en un log.
 */
public final class LlavesMaestras {

	private static final Logger log = LoggerFactory.getLogger(LlavesMaestras.class);
	static final String ID_LOCAL = "local-dev";

	private final Map<String, SecretKey> porId;
	private final String activa;

	LlavesMaestras(Map<String, SecretKey> porId, String activa) {
		if (!porId.containsKey(activa)) {
			throw new IllegalStateException("La llave maestra activa '%s' no está entre las configuradas".formatted(activa));
		}
		this.porId = Map.copyOf(porId);
		this.activa = activa;
	}

	static LlavesMaestras desde(CifradoProperties properties) {
		String texto = properties.llavesMaestras();
		if (texto == null || texto.isBlank()) {
			if (properties.archivoLlaveLocal() == null) {
				throw new IllegalStateException("Falta la llave maestra de cifrado: define NUTRIKIT_LLAVES_MAESTRAS como "
						+ "id:base64 (32 bytes; por ejemplo: v1:$(openssl rand -base64 32))");
			}
			return new LlavesMaestras(Map.of(ID_LOCAL, deArchivoLocal(properties.archivoLlaveLocal())), ID_LOCAL);
		}
		Map<String, SecretKey> llaves = new LinkedHashMap<>();
		for (String entrada : texto.split(",")) {
			String[] partes = entrada.strip().split(":", 2);
			if (partes.length != 2 || partes[0].isBlank()) {
				throw new IllegalStateException("NUTRIKIT_LLAVES_MAESTRAS debe tener la forma id:base64[,id:base64]");
			}
			byte[] bytes = Base64.getDecoder().decode(partes[1].strip());
			if (bytes.length != CifradorAesGcm.BYTES_LLAVE) {
				throw new IllegalStateException("La llave maestra '%s' debe medir 32 bytes (256 bits)".formatted(partes[0]));
			}
			llaves.put(partes[0].strip(), CifradorAesGcm.llaveDe(bytes));
		}
		String activa = properties.llaveMaestraActiva();
		if (activa == null || activa.isBlank()) {
			if (llaves.size() > 1) {
				throw new IllegalStateException("Hay varias llaves maestras: define NUTRIKIT_LLAVE_MAESTRA_ACTIVA");
			}
			activa = llaves.keySet().iterator().next();
		}
		return new LlavesMaestras(llaves, activa.strip());
	}

	/**
	 * Desarrollo: la llave vive en un archivo de la cuenta del usuario, no en el repositorio, y se
	 * conserva entre reinicios para que los datos cifrados sigan legibles.
	 */
	private static SecretKey deArchivoLocal(Path archivo) {
		try {
			if (Files.notExists(archivo)) {
				Files.createDirectories(archivo.getParent());
				Files.writeString(archivo, Base64.getEncoder().encodeToString(CifradorAesGcm.llaveNueva().getEncoded()));
				try {
					Files.setPosixFilePermissions(archivo, PosixFilePermissions.fromString("rw-------"));
				}
				catch (UnsupportedOperationException e) {
					// Sistema de archivos sin permisos POSIX (Windows).
				}
				log.warn("Se creó una llave maestra local de desarrollo en {}", archivo);
			}
			return CifradorAesGcm.llaveDe(Base64.getDecoder().decode(Files.readString(archivo).strip()));
		}
		catch (IOException e) {
			throw new IllegalStateException("No se pudo leer ni crear la llave maestra local " + archivo, e);
		}
	}

	public String activa() {
		return activa;
	}

	public SecretKey llaveActiva() {
		return porId.get(activa);
	}

	public Optional<SecretKey> llave(String id) {
		return Optional.ofNullable(porId.get(id));
	}

	public Set<String> ids() {
		return porId.keySet();
	}

	/** Sin las llaves. */
	@Override
	public String toString() {
		return "LlavesMaestras[ids=%s, activa=%s]".formatted(porId.keySet(), activa);
	}

}
