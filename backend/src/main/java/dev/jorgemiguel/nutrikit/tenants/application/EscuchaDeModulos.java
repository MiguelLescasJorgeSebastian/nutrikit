package dev.jorgemiguel.nutrikit.tenants.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import dev.jorgemiguel.nutrikit.tenants.ModuloDeConsultorioCambiado;
import dev.jorgemiguel.nutrikit.tenants.TenantCreado;

/**
 * Reacciones del propio módulo a sus eventos, después del commit y en el mismo hilo: cuando la
 * respuesta llega al cliente, el consultorio ya tiene sus módulos y la caché ya está al día.
 *
 * <p>Si algo falla aquí, Spring Modulith deja el evento pendiente y se reintenta.
 */
@Component
class EscuchaDeModulos {

	private final ModulosService modulos;
	private final ModulosEncendidos encendidos;

	EscuchaDeModulos(ModulosService modulos, ModulosEncendidos encendidos) {
		this.modulos = modulos;
		this.encendidos = encendidos;
	}

	@TransactionalEventListener
	public void on(TenantCreado evento) {
		modulos.registrarModulosPorDefecto(evento.tenantId());
		encendidos.olvidar(evento.tenantId());
	}

	/** Después del commit: si se olvidara antes, otra petición podría volver a guardar el valor viejo. */
	@TransactionalEventListener
	public void on(ModuloDeConsultorioCambiado evento) {
		encendidos.olvidar(evento.tenantId());
	}

}
