package dev.jorgemiguel.nutrikit.tenants.application;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import dev.jorgemiguel.nutrikit.shared.Origen;
import dev.jorgemiguel.nutrikit.shared.TenantContext;
import dev.jorgemiguel.nutrikit.tenants.ModuloDeConsultorioCambiado;
import dev.jorgemiguel.nutrikit.tenants.ModuloDelConsultorio;
import dev.jorgemiguel.nutrikit.tenants.domain.TenantModulo;
import dev.jorgemiguel.nutrikit.tenants.domain.TenantModuloRepository;
import dev.jorgemiguel.nutrikit.tenants.domain.TenantRepository;

/**
 * Módulos de un consultorio desde el panel de operador.
 *
 * <p>El operador no pertenece a ningún consultorio: cada operación entra al consultorio destino
 * ({@link TenantContext#ejecutarComo}) antes de abrir su transacción, así RLS aplica igual que
 * para cualquier usuario (ADR-0004).
 */
@Service
public class ModulosService {

	private static final Logger log = LoggerFactory.getLogger(ModulosService.class);

	private final TenantRepository tenants;
	private final TenantModuloRepository modulos;
	private final TransactionTemplate tx;
	private final TransactionTemplate txNueva;
	private final ApplicationEventPublisher eventos;
	private final Clock clock;

	ModulosService(TenantRepository tenants, TenantModuloRepository modulos, TransactionTemplate tx,
			ApplicationEventPublisher eventos, Clock clock) {
		this.tenants = tenants;
		this.modulos = modulos;
		this.tx = tx;
		this.txNueva = new TransactionTemplate(tx.getTransactionManager());
		this.txNueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.eventos = eventos;
		this.clock = clock;
	}

	public List<TenantModulo> deConsultorio(UUID tenantId) {
		exigirConsultorio(tenantId);
		return TenantContext.ejecutarComo(tenantId, () -> tx.execute(estado -> modulos.findAllByOrderByModuloAsc()));
	}

	/**
	 * Enciende o apaga un módulo. Si ya estaba así, no cambia nada ni publica evento.
	 *
	 * @throws TenantNoEncontradoException si el consultorio no existe
	 * @throws ModuloDesconocidoException si el código no es del catálogo
	 * @throws dev.jorgemiguel.nutrikit.tenants.domain.ModuloDeNucleoException si se intenta apagar el núcleo
	 */
	public TenantModulo cambiar(UUID tenantId, String codigo, boolean encender) {
		ModuloDelConsultorio modulo = ModuloDelConsultorio.deCodigo(codigo)
				.orElseThrow(() -> new ModuloDesconocidoException(codigo));
		exigirConsultorio(tenantId);
		Origen origen = Origen.actual();
		return TenantContext.ejecutarComo(tenantId, () -> tx.execute(estado -> {
			TenantModulo registro = modulos.findById(new TenantModulo.Llave(tenantId, modulo.codigo()))
					.orElseGet(() -> modulos.save(TenantModulo.registrar(modulo, modulo.encendidoPorDefecto())));
			if (registro.cambiar(encender)) {
				eventos.publishEvent(new ModuloDeConsultorioCambiado(tenantId, modulo, encender, origen, clock.instant()));
			}
			return registro;
		}));
	}

	/**
	 * Registra los módulos que le falten al consultorio, con su valor por defecto. Idempotente.
	 * Usa una transacción nueva porque se llama después del commit del alta.
	 *
	 * <p>Si el consultorio ya no existe (se borró antes de que corriera el listener), no hace nada:
	 * fallar dejaría el evento pendiente y se reintentaría para siempre.
	 */
	void registrarModulosPorDefecto(UUID tenantId) {
		if (tenants.findById(tenantId).isEmpty()) {
			log.warn("No se registran módulos: el consultorio {} ya no existe", tenantId);
			return;
		}
		TenantContext.ejecutarComo(tenantId, () -> txNueva.executeWithoutResult(estado -> {
			Set<String> registrados = modulos.findAllByOrderByModuloAsc().stream()
					.map(TenantModulo::getModulo)
					.collect(Collectors.toSet());
			Arrays.stream(ModuloDelConsultorio.values())
					.filter(modulo -> !registrados.contains(modulo.codigo()))
					.forEach(modulo -> modulos.save(TenantModulo.registrar(modulo, modulo.encendidoPorDefecto())));
		}));
	}

	private void exigirConsultorio(UUID tenantId) {
		if (tenants.findById(tenantId).isEmpty()) {
			throw new TenantNoEncontradoException(tenantId);
		}
	}

}
