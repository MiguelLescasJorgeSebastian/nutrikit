package dev.jorgemiguel.nutrikit.encryption.infrastructure;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import dev.jorgemiguel.nutrikit.shared.TenantContext;
import dev.jorgemiguel.nutrikit.tenants.Consultorios;

/**
 * Al arrancar, re-envuelve con la llave maestra activa las llaves de datos que dependen de otra.
 *
 * <p>Así se rota la llave maestra sin re-cifrar datos: se agrega la nueva a
 * {@code NUTRIKIT_LLAVES_MAESTRAS}, se marca activa, se reinicia, y cuando el log confirma que no
 * queda ninguna pendiente se retira la anterior. Cada consultorio se procesa en su propio contexto
 * y transacción, porque {@code llave_de_datos} tiene RLS.
 */
@Component
class RotacionDeLlaveMaestra {

	private static final Logger log = LoggerFactory.getLogger(RotacionDeLlaveMaestra.class);

	private final Consultorios consultorios;
	private final LlavesDeDatos llaves;
	private final LlavesMaestras maestras;
	private final TransactionTemplate tx;

	RotacionDeLlaveMaestra(Consultorios consultorios, LlavesDeDatos llaves, LlavesMaestras maestras,
			TransactionTemplate tx) {
		this.consultorios = consultorios;
		this.llaves = llaves;
		this.maestras = maestras;
		this.tx = tx;
	}

	@EventListener(ApplicationReadyEvent.class)
	void alArrancar() {
		if (maestras.ids().size() > 1) {
			int total = reenvolverTodas();
			log.info("Rotación de llave maestra: {} llave(s) de datos re-envuelta(s) con '{}'", total, maestras.activa());
		}
	}

	int reenvolverTodas() {
		int total = 0;
		for (UUID tenantId : consultorios.ids()) {
			total += TenantContext.ejecutarComo(tenantId, () -> tx.execute(estado -> llaves.reenvolver(tenantId)));
		}
		return total;
	}

}
