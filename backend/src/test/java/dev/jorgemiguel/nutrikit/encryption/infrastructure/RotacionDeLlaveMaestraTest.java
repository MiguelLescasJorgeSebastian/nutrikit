package dev.jorgemiguel.nutrikit.encryption.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import dev.jorgemiguel.nutrikit.TestcontainersConfiguration;
import dev.jorgemiguel.nutrikit.shared.TenantContext;

/**
 * Rotar la llave maestra re-envuelve las llaves de datos sin re-cifrar los datos: lo cifrado con la
 * maestra vieja se sigue leyendo cuando ya solo existe la nueva.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RotacionDeLlaveMaestraTest {

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private TransactionTemplate tx;

	private final SecretKey vieja = CifradorAesGcm.llaveNueva();
	private final SecretKey nueva = CifradorAesGcm.llaveNueva();

	private <T> T enConsultorio(UUID tenantId, Supplier<T> accion) {
		return TenantContext.ejecutarComo(tenantId, () -> tx.execute(estado -> accion.get()));
	}

	@Test
	void rotarLaLlaveMaestraNoRequiereReCifrarLosDatos() {
		UUID tenantId = UUID.randomUUID();
		jdbc.sql("INSERT INTO tenant (id, slug, nombre) VALUES (?, ?, 'Rotación')")
				.params(tenantId, "rotacion-" + tenantId.toString().substring(0, 8)).update();

		// 1. Hoy: solo existe la maestra vieja. Se cifra un dato.
		var llavesHoy = new LlavesDeDatos(jdbc, new LlavesMaestras(Map.of("vieja", vieja), "vieja"));
		byte[] dato = enConsultorio(tenantId, () -> new CifradoDeCampos(llavesHoy).cifrar("Diabetes tipo 2"));

		// 2. Rotación: se agrega la nueva, se marca activa y se re-envuelve.
		var llavesRotando = new LlavesDeDatos(jdbc,
				new LlavesMaestras(Map.of("vieja", vieja, "nueva", nueva), "nueva"));
		assertThat(enConsultorio(tenantId, () -> llavesRotando.reenvolver(tenantId))).isEqualTo(1);
		assertThat(enConsultorio(tenantId, () -> jdbc.sql("SELECT llave_maestra_id FROM llave_de_datos")
				.query(String.class).single())).isEqualTo("nueva");

		// 3. Se retira la vieja: el dato, sin re-cifrar, se sigue leyendo.
		var llavesDespues = new LlavesDeDatos(jdbc, new LlavesMaestras(Map.of("nueva", nueva), "nueva"));
		assertThat(enConsultorio(tenantId, () -> new CifradoDeCampos(llavesDespues).descifrar(dato)))
				.isEqualTo("Diabetes tipo 2");

		// Y re-envolver de nuevo no tiene nada pendiente.
		assertThat(enConsultorio(tenantId, () -> llavesRotando.reenvolver(tenantId))).isZero();
	}

	@Test
	void sinLaLlaveMaestraConQueSeEnvolvioNoSePuedeLeer() {
		UUID tenantId = UUID.randomUUID();
		jdbc.sql("INSERT INTO tenant (id, slug, nombre) VALUES (?, ?, 'Sin maestra')")
				.params(tenantId, "sin-maestra-" + tenantId.toString().substring(0, 8)).update();
		var conVieja = new LlavesDeDatos(jdbc, new LlavesMaestras(Map.of("vieja", vieja), "vieja"));
		byte[] dato = enConsultorio(tenantId, () -> new CifradoDeCampos(conVieja).cifrar("dato"));

		var soloNueva = new LlavesDeDatos(jdbc, new LlavesMaestras(Map.of("nueva", nueva), "nueva"));

		assertThatThrownBy(() -> enConsultorio(tenantId, () -> new CifradoDeCampos(soloNueva).descifrar(dato)))
				.hasMessageContaining("Falta la llave maestra 'vieja'");
	}

}
