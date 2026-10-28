package dev.jorgemiguel.nutrikit.shared;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Filtro de Hibernate que agrega {@code tenant_id = <consultorio actual>} a toda consulta de una
 * entidad con datos de consultorio. Es la primera barrera de aislamiento; la segunda es
 * Row-Level Security en la base.
 *
 * <p>Cada entidad con {@code tenant_id} lleva {@code @Filter(name = FiltroDeTenant.NOMBRE)}. La
 * definición ({@code @FilterDef}) está una sola vez en {@code package-info.java} de este paquete,
 * con {@code autoEnabled} para que esté activa en toda sesión y {@code applyToLoadByKey} para que
 * también aplique a {@code findById}.
 */
public final class FiltroDeTenant {

	public static final String NOMBRE = "tenant";
	public static final String PARAMETRO = "tenantId";
	public static final String CONDICION = "tenant_id = :" + PARAMETRO;

	/**
	 * Para catálogos con filas globales ({@code tenant_id} nulo, visibles para todos) y filas propias
	 * del consultorio, como los indicadores. La política de RLS de esas tablas hace lo mismo.
	 */
	public static final String NOMBRE_CON_GLOBALES = "tenantOGlobal";
	public static final String CONDICION_CON_GLOBALES = "(tenant_id IS NULL OR tenant_id = :" + PARAMETRO + ")";

	/**
	 * Sin consultorio en contexto, el filtro compara contra un UUID que no existe: la consulta
	 * no devuelve nada en lugar de devolver todo.
	 */
	public static final UUID NINGUNO = new UUID(0L, 0L);

	private FiltroDeTenant() {
	}

	/** Valor del parámetro del filtro; Hibernate lo instancia y lo consulta al usar el filtro. */
	public static final class TenantActual implements Supplier<UUID> {

		@Override
		public UUID get() {
			return TenantContext.actual().orElse(NINGUNO);
		}

	}

}
