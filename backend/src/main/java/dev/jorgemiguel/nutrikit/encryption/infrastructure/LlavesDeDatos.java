package dev.jorgemiguel.nutrikit.encryption.infrastructure;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKey;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import dev.jorgemiguel.nutrikit.encryption.DatoCifradoInvalidoException;

/**
 * Llaves de datos (DEK) por consultorio: se crean la primera vez que hacen falta, se guardan
 * envueltas con la llave maestra activa y se desenvuelven al usarlas.
 *
 * <p>Las desenvueltas se guardan en memoria, pero solo después del commit de la transacción que
 * las leyó o creó: si una llave nueva terminara en rollback y quedara en memoria, se cifrarían
 * datos con una llave que no existe en la base.
 *
 * <p>Requiere una transacción del consultorio: la tabla tiene RLS.
 */
@Component
public class LlavesDeDatos {

	/** Versión de llave con la que se cifran los datos nuevos. */
	public record LlaveVersionada(int version, SecretKey llave) {

		@Override
		public String toString() {
			return "LlaveVersionada[version=%d]".formatted(version);
		}

	}

	private record FilaDeLlave(int version, byte[] llaveEnvuelta, String llaveMaestraId) {
	}

	private final JdbcClient jdbc;
	private final LlavesMaestras maestras;
	private final Map<String, SecretKey> enMemoria = new ConcurrentHashMap<>();
	private final Map<UUID, Integer> versionActiva = new ConcurrentHashMap<>();

	LlavesDeDatos(JdbcClient jdbc, LlavesMaestras maestras) {
		this.jdbc = jdbc;
		this.maestras = maestras;
	}

	public LlaveVersionada activa(UUID tenantId) {
		Integer version = versionActiva.get(tenantId);
		if (version != null && enMemoria.containsKey(clave(tenantId, version))) {
			return new LlaveVersionada(version, enMemoria.get(clave(tenantId, version)));
		}
		exigirTransaccion();
		FilaDeLlave fila = ultima(tenantId).orElseGet(() -> crear(tenantId));
		SecretKey llave = desenvolver(tenantId, fila);
		recordarAlConfirmar(tenantId, fila.version(), llave, true);
		return new LlaveVersionada(fila.version(), llave);
	}

	public SecretKey version(UUID tenantId, int version) {
		SecretKey enCache = enMemoria.get(clave(tenantId, version));
		if (enCache != null) {
			return enCache;
		}
		exigirTransaccion();
		FilaDeLlave fila = jdbc.sql("SELECT version, llave_envuelta, llave_maestra_id FROM llave_de_datos WHERE tenant_id = ? AND version = ?")
				.params(tenantId, version)
				.query(FilaDeLlave.class)
				.optional()
				.orElseThrow(() -> new DatoCifradoInvalidoException("No existe la llave de datos versión " + version));
		SecretKey llave = desenvolver(tenantId, fila);
		recordarAlConfirmar(tenantId, version, llave, false);
		return llave;
	}

	/**
	 * Re-envuelve con la llave maestra activa las llaves de datos del consultorio que dependen de
	 * otra. No toca los datos cifrados: la llave de datos es la misma.
	 *
	 * @return cuántas llaves se re-envolvieron
	 */
	int reenvolver(UUID tenantId) {
		exigirTransaccion();
		var pendientes = jdbc.sql("SELECT version, llave_envuelta, llave_maestra_id FROM llave_de_datos WHERE tenant_id = ? AND llave_maestra_id <> ?")
				.params(tenantId, maestras.activa())
				.query(FilaDeLlave.class)
				.list();
		for (FilaDeLlave fila : pendientes) {
			SecretKey llave = desenvolver(tenantId, fila);
			jdbc.sql("UPDATE llave_de_datos SET llave_envuelta = ?, llave_maestra_id = ? WHERE tenant_id = ? AND version = ?")
					.params(envolver(tenantId, fila.version(), llave), maestras.activa(), tenantId, fila.version())
					.update();
		}
		return pendientes.size();
	}

	private Optional<FilaDeLlave> ultima(UUID tenantId) {
		return jdbc.sql("SELECT version, llave_envuelta, llave_maestra_id FROM llave_de_datos WHERE tenant_id = ? ORDER BY version DESC LIMIT 1")
				.param(tenantId)
				.query(FilaDeLlave.class)
				.optional();
	}

	/**
	 * Primera llave del consultorio. Si dos transacciones la crean a la vez, {@code ON CONFLICT}
	 * deja la de quien llegó primero y ambas usan esa.
	 */
	private FilaDeLlave crear(UUID tenantId) {
		int version = 1;
		jdbc.sql("""
				INSERT INTO llave_de_datos (tenant_id, version, llave_envuelta, llave_maestra_id)
				VALUES (?, ?, ?, ?) ON CONFLICT (tenant_id, version) DO NOTHING
				""")
				.params(tenantId, version, envolver(tenantId, version, CifradorAesGcm.llaveNueva()), maestras.activa())
				.update();
		return ultima(tenantId).orElseThrow();
	}

	private byte[] envolver(UUID tenantId, int version, SecretKey llave) {
		return CifradorAesGcm.cifrar(maestras.llaveActiva(), llave.getEncoded(), aad(tenantId, version));
	}

	private SecretKey desenvolver(UUID tenantId, FilaDeLlave fila) {
		SecretKey maestra = maestras.llave(fila.llaveMaestraId())
				.orElseThrow(() -> new IllegalStateException(
						"Falta la llave maestra '%s' para abrir una llave de datos; agrégala a NUTRIKIT_LLAVES_MAESTRAS"
								.formatted(fila.llaveMaestraId())));
		return CifradorAesGcm.llaveDe(CifradorAesGcm.descifrar(maestra, fila.llaveEnvuelta(), aad(tenantId, fila.version())));
	}

	/** Una llave envuelta solo se abre para su consultorio y su versión. */
	private static byte[] aad(UUID tenantId, int version) {
		byte[] etiqueta = "nutrikit:dek".getBytes(StandardCharsets.US_ASCII);
		return ByteBuffer.allocate(etiqueta.length + 16 + 4).put(etiqueta)
				.putLong(tenantId.getMostSignificantBits()).putLong(tenantId.getLeastSignificantBits())
				.putInt(version).array();
	}

	private void recordarAlConfirmar(UUID tenantId, int version, SecretKey llave, boolean esActiva) {
		Runnable recordar = () -> {
			enMemoria.put(clave(tenantId, version), llave);
			if (esActiva) {
				versionActiva.merge(tenantId, version, Math::max);
			}
		};
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					recordar.run();
				}
			});
		}
		else {
			recordar.run();
		}
	}

	private static void exigirTransaccion() {
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("Las llaves de datos se leen dentro de una transacción del consultorio (RLS)");
		}
	}

	private static String clave(UUID tenantId, int version) {
		return tenantId + ":" + version;
	}

}
