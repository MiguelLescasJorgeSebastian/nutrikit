package dev.jorgemiguel.nutrikit.shared;

import java.util.Optional;
import java.util.UUID;

/**
 * Consultorio de la petición en curso.
 *
 * <p>Usa {@link ScopedValue} (Java 25) en lugar de {@code ThreadLocal}: el valor solo existe
 * dentro de {@link #ejecutarComo} y desaparece al salir, aunque haya una excepción. No hay que
 * limpiarlo a mano, así que un hilo reutilizado por el servidor (o un hilo virtual) nunca
 * arrastra el consultorio de una petición anterior.
 *
 * <p>Los hilos que se crean dentro del alcance no heredan el valor (por ejemplo, un listener
 * asíncrono de eventos): quien cambia de hilo debe pasar el id y volver a llamar a
 * {@link #ejecutarComo}.
 */
public final class TenantContext {

	private static final ScopedValue<UUID> ACTUAL = ScopedValue.newInstance();

	private TenantContext() {
	}

	/** El consultorio actual, o vacío si el código corre fuera de una petición de consultorio. */
	public static Optional<UUID> actual() {
		return ACTUAL.isBound() ? Optional.of(ACTUAL.get()) : Optional.empty();
	}

	/** El consultorio actual; falla si no hay uno, para que ningún dato quede sin dueño. */
	public static UUID requerido() {
		return actual().orElseThrow(ConsultorioNoIdentificadoException::new);
	}

	/** Ejecuta {@code operacion} con {@code tenantId} como consultorio actual y devuelve su resultado. */
	public static <R, X extends Throwable> R ejecutarComo(UUID tenantId, ScopedValue.CallableOp<R, X> operacion)
			throws X {
		return ScopedValue.where(ACTUAL, requerirId(tenantId)).call(operacion);
	}

	/** Ejecuta {@code operacion} con {@code tenantId} como consultorio actual. */
	public static void ejecutarComo(UUID tenantId, Runnable operacion) {
		ScopedValue.where(ACTUAL, requerirId(tenantId)).run(operacion);
	}

	private static UUID requerirId(UUID tenantId) {
		if (tenantId == null) {
			throw new IllegalArgumentException("El id del consultorio es obligatorio");
		}
		return tenantId;
	}

}
