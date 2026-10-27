/**
 * Piezas transversales que todos los módulos pueden usar: formato de errores de la API,
 * configuración de OpenAPI y contexto de consultorio.
 *
 * <p>Cualquier módulo puede depender de la API pública (la raíz de este paquete), pero este módulo no
 * depende de ninguno. Si algo aquí empieza a saber de pacientes o consultas, va en otro módulo.
 *
 * <p>Aquí se define, una sola vez, el filtro de Hibernate por consultorio (ver {@link FiltroDeTenant}).
 * Cada entidad con {@code tenant_id} solo declara {@code @Filter(name = FiltroDeTenant.NOMBRE)}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Compartido")
@FilterDef(name = FiltroDeTenant.NOMBRE, defaultCondition = FiltroDeTenant.CONDICION,
		parameters = @ParamDef(name = FiltroDeTenant.PARAMETRO, type = UUID.class,
				resolver = FiltroDeTenant.TenantActual.class),
		autoEnabled = true, applyToLoadByKey = true)
package dev.jorgemiguel.nutrikit.shared;

import java.util.UUID;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
