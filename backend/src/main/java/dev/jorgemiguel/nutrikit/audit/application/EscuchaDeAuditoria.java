package dev.jorgemiguel.nutrikit.audit.application;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import dev.jorgemiguel.nutrikit.audit.domain.Acciones;
import dev.jorgemiguel.nutrikit.audit.domain.RegistroDeAuditoria;
import dev.jorgemiguel.nutrikit.iam.InicioDeSesionExitoso;
import dev.jorgemiguel.nutrikit.iam.InicioDeSesionFallido;
import dev.jorgemiguel.nutrikit.patients.PacienteActualizado;
import dev.jorgemiguel.nutrikit.patients.PacienteCreado;
import dev.jorgemiguel.nutrikit.patients.PacienteDadoDeBaja;
import dev.jorgemiguel.nutrikit.shared.TenantContext;
import dev.jorgemiguel.nutrikit.tenants.EstadoDeTenantCambiado;
import dev.jorgemiguel.nutrikit.tenants.ModuloDeConsultorioCambiado;
import dev.jorgemiguel.nutrikit.tenants.TenantCreado;

/**
 * Convierte eventos de otros módulos en entradas de la bitácora. Solo conoce los eventos de su
 * API pública, nunca sus clases internas.
 *
 * <p>Por qué no {@code @ApplicationModuleListener}: esa anotación abre la transacción antes de
 * entrar al método, y aquí hay que entrar primero al consultorio del evento
 * ({@link TenantContext#ejecutarComo}) para que la transacción fije {@code app.tenant_id} y RLS
 * permita el {@code INSERT}. Por eso se combinan a mano sus otras dos piezas:
 * <ul>
 * <li>{@code @TransactionalEventListener}: corre después del commit de quien publicó, así no se
 * audita algo que terminó en rollback. Spring Modulith registra cada evento en
 * {@code event_publication} y lo marca completo solo si este método termina sin error.</li>
 * <li>{@code @Async}: en otro hilo, para no hacer esperar a la petición. El hilo nuevo no
 * hereda el consultorio; por eso cada evento lleva su {@code tenantId}.</li>
 * </ul>
 */
@Component
class EscuchaDeAuditoria {

	private final Bitacora bitacora;

	EscuchaDeAuditoria(Bitacora bitacora) {
		this.bitacora = bitacora;
	}

	@Async
	@TransactionalEventListener
	public void on(TenantCreado evento) {
		registrar(new RegistroDeAuditoria(evento.tenantId(), evento.origen(), Acciones.TENANT_CREADO,
				Acciones.RECURSO_TENANT, evento.tenantId(), evento.ocurridoEn()));
	}

	@Async
	@TransactionalEventListener
	public void on(EstadoDeTenantCambiado evento) {
		registrar(new RegistroDeAuditoria(evento.tenantId(), evento.origen(), Acciones.TENANT_ESTADO_CAMBIADO,
				Acciones.RECURSO_TENANT, evento.tenantId(), evento.ocurridoEn()));
	}

	@Async
	@TransactionalEventListener
	public void on(InicioDeSesionExitoso evento) {
		registrar(new RegistroDeAuditoria(evento.tenantId(), evento.origen(), Acciones.LOGIN_EXITOSO,
				Acciones.RECURSO_USUARIO, evento.usuarioId(), evento.ocurridoEn()));
	}

	@Async
	@TransactionalEventListener
	public void on(InicioDeSesionFallido evento) {
		registrar(new RegistroDeAuditoria(evento.tenantId(), evento.origen(), Acciones.LOGIN_FALLIDO,
				Acciones.RECURSO_USUARIO, evento.usuarioId(), evento.ocurridoEn()));
	}

	@Async
	@TransactionalEventListener
	public void on(ModuloDeConsultorioCambiado evento) {
		registrar(new RegistroDeAuditoria(evento.tenantId(), evento.origen(),
				evento.habilitado() ? Acciones.MODULO_ENCENDIDO : Acciones.MODULO_APAGADO,
				Acciones.recursoModulo(evento.modulo().codigo()), null, evento.ocurridoEn()));
	}

	@Async
	@TransactionalEventListener
	public void on(PacienteCreado evento) {
		registrar(new RegistroDeAuditoria(evento.tenantId(), evento.origen(), Acciones.PACIENTE_CREADO,
				Acciones.RECURSO_PACIENTE, evento.pacienteId(), evento.ocurridoEn()));
	}

	@Async
	@TransactionalEventListener
	public void on(PacienteActualizado evento) {
		registrar(new RegistroDeAuditoria(evento.tenantId(), evento.origen(), Acciones.PACIENTE_ACTUALIZADO,
				Acciones.RECURSO_PACIENTE, evento.pacienteId(), evento.ocurridoEn()));
	}

	@Async
	@TransactionalEventListener
	public void on(PacienteDadoDeBaja evento) {
		registrar(new RegistroDeAuditoria(evento.tenantId(), evento.origen(), Acciones.PACIENTE_DADO_DE_BAJA,
				Acciones.RECURSO_PACIENTE, evento.pacienteId(), evento.ocurridoEn()));
	}

	private void registrar(RegistroDeAuditoria registro) {
		TenantContext.ejecutarComo(registro.tenantId(), () -> bitacora.registrar(registro));
	}

}
